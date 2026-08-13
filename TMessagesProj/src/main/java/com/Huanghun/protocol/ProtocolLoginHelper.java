package com.Huanghun.protocol;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.ui.LoginActivity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Imports owner-provided credential archives. Every candidate is counted as successful
 * only after Telegram accepts it and returns a real account; no synthetic login state is
 * ever generated. Individual invalid or revoked files never interrupt later candidates.
 */
public final class ProtocolLoginHelper {
    private static final int TYPE_SESSION = 0;
    private static final int TYPE_TDATA = 1;
    private static final int TYPE_PASSKEY = 2;
    private static final int MAX_ENTRIES = 512;
    private static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L;
    private static final long VERIFY_TIMEOUT_MS = 20_000L;

    private ProtocolLoginHelper() {
    }

    public static void handleImport(final Object fragment, final Uri uri, final int type) {
        if (!(fragment instanceof LoginActivity) || uri == null) {
            return;
        }
        final LoginActivity loginActivity = (LoginActivity) fragment;
        final Activity activity = loginActivity.getParentActivity();
        if (activity == null) {
            return;
        }

        showToast(activity, "正在导入会话…");
        new Thread(() -> {
            try {
                File importDir = new File(activity.getCacheDir(), "protocol_import");
                deleteRecursive(importDir);
                if (!importDir.mkdirs() && !importDir.isDirectory()) {
                    throw new IOException("无法创建临时目录");
                }
                extractArchive(activity, uri, importDir);

                CandidateCollection collection = collectCandidates(importDir, type);
                BatchState batch = new BatchState(collection.scannedCount, loginActivity.getCurrentAccount());
                batch.failedCount = collection.invalidCount;
                if (collection.candidates.isEmpty()) {
                    AndroidUtilities.runOnUIThread(() -> showBatchSummary(activity, loginActivity, batch));
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> startNextImport(loginActivity, activity, collection.candidates, batch, 0));
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> showArchiveError(activity, e));
            }
        }, "protocol-login-scan").start();
    }

    private static CandidateCollection collectCandidates(File importDir, int type) {
        CandidateCollection result = new CandidateCollection();
        if (type == TYPE_TDATA) {
            ArrayList<File> tdataDirectories = findTdataDirectories(importDir);
            for (File tdataDirectory : tdataDirectories) {
                try {
                    ArrayList<ProtocolParser.SessionData> sessions = TdataParser.parse(tdataDirectory);
                    if (sessions.isEmpty()) {
                        result.scannedCount++;
                        result.invalidCount++;
                    } else {
                        for (ProtocolParser.SessionData session : sessions) {
                            result.candidates.add(ImportCandidate.forSession(session));
                            result.scannedCount++;
                        }
                    }
                } catch (Exception ignore) {
                    result.scannedCount++;
                    result.invalidCount++;
                }
            }
            return result;
        }

        String extension = type == TYPE_PASSKEY ? ".passkey" : ".session";
        for (File file : findFilesByExtension(importDir, extension)) {
            result.candidates.add(type == TYPE_PASSKEY ? ImportCandidate.forPasskey(file) : ImportCandidate.forSession(file));
            result.scannedCount++;
        }
        return result;
    }

    private static void startNextImport(LoginActivity loginActivity, Activity activity,
                                        ArrayList<ImportCandidate> candidates, BatchState batch, int index) {
        new Thread(() -> importNext(loginActivity, activity, candidates, batch, index), "protocol-login-import").start();
    }

    private static void importNext(LoginActivity loginActivity, Activity activity,
                                   ArrayList<ImportCandidate> candidates, BatchState batch, int index) {
        if (index >= candidates.size()) {
            AndroidUtilities.runOnUIThread(() -> showBatchSummary(activity, loginActivity, batch));
            return;
        }

        ImportCandidate candidate = candidates.get(index);

        if (candidate.kind == TYPE_PASSKEY) {
            int accountNum = reserveNextFreeAccountSlot(loginActivity, activity, candidates, batch, index);
            if (accountNum >= 0) {
                importPasskey(loginActivity, activity, candidates, batch, index, accountNum, candidate.file);
            }
            return;
        }

        int accountNum = -1;
        try {
            ProtocolParser.SessionData data = candidate.sessionData != null
                    ? candidate.sessionData
                    : ProtocolParser.parseTelethonSession(candidate.file);
            long authKeyId = getAuthKeyId(data.authKey);
            if (batch.importedAuthKeyIds.contains(authKeyId) || findExistingAccountForAuthKey(authKeyId) >= 0) {
                batch.alreadyImportedCount++;
                startNextImport(loginActivity, activity, candidates, batch, index + 1);
                return;
            }
            batch.importedAuthKeyIds.add(authKeyId);
            accountNum = reserveNextFreeAccountSlot(loginActivity, activity, candidates, batch, index);
            if (accountNum < 0) {
                return;
            }
            final int assignedAccountNum = accountNum;
            ConnectionsManager.native_importAuthKey(assignedAccountNum, data.dcId, data.address, data.port, data.authKey);
            verifySession(assignedAccountNum, data.dcId, new VerificationCallback() {
                @Override
                public void onVerified(TLRPC.User user) {
                    AndroidUtilities.runOnUIThread(() -> recordVerifiedAccount(
                            loginActivity, activity, candidates, batch, index, assignedAccountNum, data.dcId, user));
                }

                @Override
                public void onFailed() {
                    clearUnusedImportedKey(assignedAccountNum);
                    AndroidUtilities.runOnUIThread(() -> recordFailure(
                            loginActivity, activity, candidates, batch, index));
                }
            });
        } catch (Exception ignore) {
            if (accountNum >= 0) {
                clearUnusedImportedKey(accountNum);
            }
            AndroidUtilities.runOnUIThread(() -> recordFailure(loginActivity, activity, candidates, batch, index));
        }
    }

    private static void importPasskey(LoginActivity loginActivity, Activity activity,
                                      ArrayList<ImportCandidate> candidates, BatchState batch,
                                      int index, int accountNum, File passkeyFile) {
        try {
            PasskeyParser.PasskeyData passkey = PasskeyParser.parse(passkeyFile);
            PasskeyLoginHelper.login(accountNum, passkey, new PasskeyLoginHelper.Callback() {
                @Override
                public void onSuccess(TLRPC.TL_auth_authorization authorization) {
                    if (authorization.user == null || authorization.user.id == 0) {
                        onFailed();
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> recordVerifiedAccount(
                            loginActivity, activity, candidates, batch, index, accountNum,
                            passkey.datacenterId, authorization.user));
                }

                @Override
                public void onFailed() {
                    clearUnusedImportedKey(accountNum);
                    AndroidUtilities.runOnUIThread(() -> recordFailure(
                            loginActivity, activity, candidates, batch, index));
                }
            });
        } catch (Exception ignore) {
            clearUnusedImportedKey(accountNum);
            AndroidUtilities.runOnUIThread(() -> recordFailure(loginActivity, activity, candidates, batch, index));
        }
    }

    private static void recordVerifiedAccount(LoginActivity loginActivity, Activity activity,
                                              ArrayList<ImportCandidate> candidates, BatchState batch,
                                              int index, int accountNum, int datacenterId, TLRPC.User user) {
        if (batch.importedUserIds.contains(user.id)) {
            clearUnusedImportedKey(accountNum);
            recordFailure(loginActivity, activity, candidates, batch, index);
            return;
        }

        try {
            if (!LoginActivity.saveProtocolLoginForAdditionalAccount(accountNum, user)) {
                clearUnusedImportedKey(accountNum);
                batch.failedCount++;
            } else {
                batch.importedUserIds.add(user.id);
                batch.successCount++;
                if (batch.firstSuccess == null) {
                    batch.firstSuccess = new ImportedAccount(accountNum, datacenterId, user);
                }
            }
        } catch (Throwable ignore) {
            clearUnusedImportedKey(accountNum);
            batch.failedCount++;
        }
        startNextImport(loginActivity, activity, candidates, batch, index + 1);
    }

    private static void recordFailure(LoginActivity loginActivity, Activity activity,
                                      ArrayList<ImportCandidate> candidates, BatchState batch, int index) {
        batch.failedCount++;
        startNextImport(loginActivity, activity, candidates, batch, index + 1);
    }

    private static void extractArchive(Activity activity, Uri uri, File importDir) throws Exception {
        int entries = 0;
        long totalBytes = 0;
        try (InputStream source = activity.getContentResolver().openInputStream(uri)) {
            if (source == null) {
                throw new IOException("无法读取所选压缩包");
            }
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > MAX_ENTRIES) {
                        throw new IOException("压缩包包含过多文件");
                    }
                    File output = resolveInside(importDir, entry.getName());
                    if (entry.isDirectory()) {
                        if (!output.mkdirs() && !output.isDirectory()) {
                            throw new IOException("无法创建解压目录");
                        }
                    } else {
                        File parent = output.getParentFile();
                        if (parent != null && !parent.exists() && !parent.mkdirs()) {
                            throw new IOException("无法创建解压目录");
                        }
                        try (FileOutputStream file = new FileOutputStream(output)) {
                            int read;
                            while ((read = zip.read(buffer)) != -1) {
                                totalBytes += read;
                                if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                    throw new IOException("压缩包解压后超过 64 MB 限制");
                                }
                                file.write(buffer, 0, read);
                            }
                        }
                    }
                    zip.closeEntry();
                }
            }
        }
    }

    private static ArrayList<File> findFilesByExtension(File root, String extension) {
        ArrayList<File> result = new ArrayList<>();
        collectFiles(root, extension, result);
        Collections.sort(result, Comparator.comparing(File::getAbsolutePath));
        return result;
    }

    private static void collectFiles(File root, String extension, ArrayList<File> result) {
        File[] files = root.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectFiles(file, extension, result);
            } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(extension)) {
                result.add(file);
            }
        }
    }

    private static ArrayList<File> findTdataDirectories(File root) {
        ArrayList<File> result = new ArrayList<>();
        collectTdataDirectories(root, result);
        Collections.sort(result, Comparator.comparing(File::getAbsolutePath));
        return result;
    }

    private static void collectTdataDirectories(File root, ArrayList<File> result) {
        File[] files = root.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isDirectory()) {
                continue;
            }
            if ("tdata".equalsIgnoreCase(file.getName()) && new File(file, "key_datas").isFile()) {
                result.add(file);
            } else {
                collectTdataDirectories(file, result);
            }
        }
    }

    /**
     * Returns Telegram's auth-key identifier using the MTProto SHA-1 convention.
     * This allows duplicate archives to be recognized before a second connection
     * reuses the same server-side authorization key.
     */
    private static long getAuthKeyId(byte[] authKey) throws IOException {
        if (authKey == null || authKey.length != 256) {
            throw new IOException("授权密钥长度无效");
        }
        byte[] digest = Utilities.computeSHA1(authKey);
        if (digest == null || digest.length < 20) {
            throw new IOException("无法计算授权密钥标识");
        }
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result |= ((long) (digest[12 + i] & 0xff)) << (8 * i);
        }
        return result;
    }

    /** Finds a live local account using the exact same server authorization key. */
    private static int findExistingAccountForAuthKey(long authKeyId) {
        if (authKeyId == 0) {
            return -1;
        }
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            try {
                if (UserConfig.getInstance(i).isClientActivated()
                        && ConnectionsManager.getInstance(i).getCurrentAuthKeyId() == authKeyId) {
                    return i;
                }
            } catch (Throwable ignore) {
                // A not-yet-initialized slot simply cannot match an active account.
            }
        }
        return -1;
    }

    private static int reserveNextFreeAccountSlot(LoginActivity loginActivity, Activity activity,
                                                   ArrayList<ImportCandidate> candidates, BatchState batch, int index) {
        int accountNum = findNextFreeAccountSlot(batch);
        if (accountNum < 0) {
            batch.failedCount += candidates.size() - index;
            batch.noFreeSlot = true;
            AndroidUtilities.runOnUIThread(() -> showBatchSummary(activity, loginActivity, batch));
            return -1;
        }
        batch.reservedSlots.add(accountNum);
        return accountNum;
    }

    private static int findNextFreeAccountSlot(BatchState batch) {
        if (batch.preferredAccount >= 0
                && batch.preferredAccount < UserConfig.MAX_ACCOUNT_COUNT
                && !batch.reservedSlots.contains(batch.preferredAccount)
                && !UserConfig.getInstance(batch.preferredAccount).isClientActivated()) {
            return batch.preferredAccount;
        }
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            if (!batch.reservedSlots.contains(i) && !UserConfig.getInstance(i).isClientActivated()) {
                return i;
            }
        }
        return -1;
    }

    private static void verifySession(int accountNum, int dcId, VerificationCallback callback) {
        final ConnectionsManager manager = ConnectionsManager.getInstance(accountNum);
        manager.resumeNetworkMaybe();
        final AtomicBoolean finished = new AtomicBoolean(false);
        TLRPC.TL_users_getUsers request = new TLRPC.TL_users_getUsers();
        request.id.add(new TLRPC.TL_inputUserSelf());
        final int requestToken = manager.sendRequest(request, (response, error) -> {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            if (error != null || !(response instanceof Vector)) {
                callback.onFailed();
                return;
            }
            TLRPC.User self = null;
            for (Object object : ((Vector) response).objects) {
                if (object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    if (user.self || self == null) {
                        self = user;
                    }
                }
            }
            if (self == null || self.id == 0) {
                callback.onFailed();
                return;
            }
            callback.onVerified(self);
        }, null, null, ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagEnableUnauthorized,
                dcId, ConnectionsManager.ConnectionTypeGeneric, true);

        AndroidUtilities.runOnUIThread(() -> {
            if (finished.compareAndSet(false, true)) {
                manager.cancelRequest(requestToken, true);
                callback.onFailed();
            }
        }, VERIFY_TIMEOUT_MS);
    }

    private static void clearUnusedImportedKey(int accountNum) {
        try {
            ConnectionsManager.getInstance(accountNum).cleanup(true);
        } catch (Throwable ignore) {
        }
    }

    private static void showBatchSummary(Activity activity, LoginActivity loginActivity, BatchState batch) {
        if (activity.isFinishing()) {
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("本次已扫描到 ").append(batch.scannedCount).append(" 个账户");
        message.append("\n有效账户：").append(batch.successCount).append(" 个");
        message.append("\n失败账户：").append(batch.failedCount).append(" 个");
        if (batch.alreadyImportedCount > 0) {
            message.append("\n已存在账户：").append(batch.alreadyImportedCount).append(" 个（未重复导入）");
        }
        if (batch.noFreeSlot) {
            message.append("\n\n账户槽位已满，未继续导入剩余会话。");
        } else if (batch.scannedCount == 0) {
            message.append("\n\n未找到对应的会话文件。");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("协议登录完成")
                .setMessage(message.toString())
                .setCancelable(false);
        if (batch.firstSuccess != null) {
            builder.setPositiveButton("进入账户", (dialog, which) ->
                    loginActivity.completeProtocolLogin(batch.firstSuccess.accountNum,
                            batch.firstSuccess.user, batch.firstSuccess.datacenterId);
        } else {
            builder.setPositiveButton("确定", null);
        }
        builder.show();
    }

    private static void showArchiveError(Activity activity, Exception error) {
        if (activity.isFinishing()) {
            return;
        }
        String reason = error.getMessage() != null ? error.getMessage() : "无法读取所选压缩包";
        new AlertDialog.Builder(activity)
                .setTitle("协议登录失败")
                .setMessage("无法扫描压缩包：\n" + reason)
                .setPositiveButton("确定", null)
                .show();
    }

    private static File resolveInside(File root, String name) throws IOException {
        if (name == null || name.indexOf('\0') >= 0) {
            throw new IOException("压缩包路径无效");
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new IOException("压缩包包含绝对路径");
        }
        File rootCanonical = root.getCanonicalFile();
        File output = new File(root, normalized).getCanonicalFile();
        String rootPath = rootCanonical.getPath() + File.separator;
        if (!output.getPath().startsWith(rootPath)) {
            throw new IOException("压缩包包含越界路径");
        }
        return output;
    }

    private static void showToast(Activity activity, String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    private interface VerificationCallback {
        void onVerified(TLRPC.User user);

        void onFailed();
    }

    private static final class ImportCandidate {
        final int kind;
        final File file;
        final ProtocolParser.SessionData sessionData;

        private ImportCandidate(int kind, File file, ProtocolParser.SessionData sessionData) {
            this.kind = kind;
            this.file = file;
            this.sessionData = sessionData;
        }

        static ImportCandidate forSession(File file) {
            return new ImportCandidate(TYPE_SESSION, file, null);
        }

        static ImportCandidate forSession(ProtocolParser.SessionData data) {
            return new ImportCandidate(TYPE_TDATA, null, data);
        }

        static ImportCandidate forPasskey(File file) {
            return new ImportCandidate(TYPE_PASSKEY, file, null);
        }
    }

    private static final class CandidateCollection {
        final ArrayList<ImportCandidate> candidates = new ArrayList<>();
        int scannedCount;
        int invalidCount;
    }

    private static final class ImportedAccount {
        final int accountNum;
        final int datacenterId;
        final TLRPC.User user;

        ImportedAccount(int accountNum, int datacenterId, TLRPC.User user) {
            this.accountNum = accountNum;
            this.datacenterId = datacenterId;
            this.user = user;
        }
    }

    private static final class BatchState {
        final int scannedCount;
        final int preferredAccount;
        final Set<Integer> reservedSlots = new HashSet<>();
        final Set<Long> importedUserIds = new HashSet<>();
        final Set<Long> importedAuthKeyIds = new HashSet<>();
        int successCount;
        int failedCount;
        int alreadyImportedCount;
        boolean noFreeSlot;
        ImportedAccount firstSuccess;

        BatchState(int scannedCount, int preferredAccount) {
            this.scannedCount = scannedCount;
            this.preferredAccount = preferredAccount;
        }
    }
}
