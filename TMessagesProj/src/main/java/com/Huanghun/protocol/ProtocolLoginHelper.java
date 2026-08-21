package com.Huanghun.protocol;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
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
    private static final int TYPE_PASSKEY_BRIDGE = 3;
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
        if (!isZipArchive(activity, uri)) {
            showZipFormatError(activity);
            return;
        }

        final ImportProgressDialog progress = new ImportProgressDialog(activity);
        progress.showScanning();
        new Thread(() -> {
            try {
                File importDir = new File(activity.getCacheDir(), "protocol_import");
                deleteRecursive(importDir);
                if (!importDir.mkdirs() && !importDir.isDirectory()) {
                    throw new IOException("无法创建临时目录");
                }
                extractArchive(activity, uri, importDir);

                CandidateCollection collection = collectCandidates(importDir, type);
                BatchState batch = new BatchState(collection.scannedCount, loginActivity.getCurrentAccount(), progress);
                batch.failedCount = collection.invalidCount;
                batch.processedCount = collection.invalidCount;
                AndroidUtilities.runOnUIThread(() -> progress.showImporting(batch, batch.processedCount));
                if (collection.candidates.isEmpty()) {
                    AndroidUtilities.runOnUIThread(() -> showBatchSummary(activity, loginActivity, batch));
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> startNextImport(loginActivity, activity, collection.candidates, batch, 0));
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> {
                    progress.dismiss();
                    showArchiveError(activity, e);
                });
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

        boolean isPasskeyType = type == TYPE_PASSKEY || type == TYPE_PASSKEY_BRIDGE;
        String extension = isPasskeyType ? ".passkey" : ".session";
        for (File file : findFilesByExtension(importDir, extension)) {
            result.candidates.add(isPasskeyType ? ImportCandidate.forPasskey(file, type) : ImportCandidate.forSession(file));
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
        AndroidUtilities.runOnUIThread(() -> batch.progress.showImporting(batch, Math.min(batch.scannedCount, batch.processedCount + 1)));

        if (candidate.kind == TYPE_PASSKEY || candidate.kind == TYPE_PASSKEY_BRIDGE) {
            int accountNum = reserveNextFreeAccountSlot(loginActivity, activity, candidates, batch, index);
            if (accountNum >= 0) {
                importPasskey(loginActivity, activity, candidates, batch, index, accountNum, candidate.file,
                        candidate.kind == TYPE_PASSKEY_BRIDGE);
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
                batch.processedCount++;
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

                @Override
                public void onDeferred(String reason) {
                    // 离线或连接超时不能证明授权失效。保留已导入的授权信息，并明确标记为待联网验证。
                    AndroidUtilities.runOnUIThread(() -> recordDeferred(
                            loginActivity, activity, candidates, batch, index, reason));
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
                                      int index, int accountNum, File passkeyFile, boolean useBridge) {
        try {
            PasskeyParser.PasskeyData passkey = PasskeyParser.parse(passkeyFile);
            PasskeyLoginHelper.Callback callback = new PasskeyLoginHelper.Callback() {
                @Override
                public void onSuccess(TLRPC.TL_auth_authorization authorization) {
                    if (authorization.user == null || authorization.user.id == 0) {
                        onFailed("未获取到授权账号信息");
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> recordVerifiedAccount(
                            loginActivity, activity, candidates, batch, index, accountNum,
                            passkey.datacenterId, authorization.user));
                }

                @Override
                public void onFailed(String reason) {
                    if (isWaitingForNetworkVerification(reason)) {
                        AndroidUtilities.runOnUIThread(() -> recordDeferred(
                                loginActivity, activity, candidates, batch, index, reason));
                        return;
                    }
                    clearUnusedImportedKey(accountNum);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!useBridge && !batch.passkeyFallbackHintShown) {
                            batch.passkeyFallbackHintShown = true;
                            showPasskeyFallbackHint(activity, reason);
                        }
                        recordFailure(loginActivity, activity, candidates, batch, index);
                    });
                }
            };
            if (useBridge) {
                PasskeyLoginHelper.loginWithProtocolBridge(accountNum, passkey, callback);
            } else {
                PasskeyLoginHelper.login(accountNum, passkey, callback);
            }
        } catch (Exception error) {
            clearUnusedImportedKey(accountNum);
            AndroidUtilities.runOnUIThread(() -> {
                if (!useBridge && !batch.passkeyFallbackHintShown) {
                    batch.passkeyFallbackHintShown = true;
                    showPasskeyFallbackHint(activity, "通行密钥文件无法解析");
                }
                recordFailure(loginActivity, activity, candidates, batch, index);
            });
        }
    }

    private static void recordVerifiedAccount(LoginActivity loginActivity, Activity activity,
                                              ArrayList<ImportCandidate> candidates, BatchState batch,
                                              int index, int accountNum, int datacenterId, TLRPC.User user) {
        if (batch.importedUserIds.contains(user.id) || findExistingAccountForUserId(user.id, accountNum) >= 0) {
            clearUnusedImportedKey(accountNum);
            batch.alreadyImportedCount++;
            batch.processedCount++;
            startNextImport(loginActivity, activity, candidates, batch, index + 1);
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
        batch.processedCount++;
        startNextImport(loginActivity, activity, candidates, batch, index + 1);
    }

    private static void recordFailure(LoginActivity loginActivity, Activity activity,
                                      ArrayList<ImportCandidate> candidates, BatchState batch, int index) {
        batch.failedCount++;
        batch.processedCount++;
        startNextImport(loginActivity, activity, candidates, batch, index + 1);
    }

    private static boolean isWaitingForNetworkVerification(String reason) {
        if (reason == null) {
            return false;
        }
        return reason.startsWith("网络不可用") || reason.startsWith("网络连接超时");
    }

    private static void recordDeferred(LoginActivity loginActivity, Activity activity,
                                       ArrayList<ImportCandidate> candidates, BatchState batch, int index,
                                       String reason) {
        batch.deferredCount++;
        if (batch.firstDeferredReason == null && reason != null && !reason.isEmpty()) {
            batch.firstDeferredReason = reason;
        }
        batch.processedCount++;
        startNextImport(loginActivity, activity, candidates, batch, index + 1);
    }

    private static void showPasskeyFallbackHint(Activity activity, String reason) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("外部通行密钥（第一接口）失败")
                .setMessage("本次通行密钥未完成 Telegram 授权：" + reason
                        + "\n\n请返回“协议登录”，选择“外部通信密钥（第二接口）”后重新导入同一压缩包。第二接口会为挑战过期自动重新获取挑战并递增签名计数后重试。")
                .setPositiveButton("确定", null)
                .show();
    }

    private static boolean isZipArchive(Activity activity, Uri uri) {
        String displayName = null;
        try (Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                displayName = cursor.getString(0);
            }
        } catch (Exception ignore) {
        }
        return displayName != null && displayName.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private static void showZipFormatError(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("文件格式不支持")
                .setMessage("请上传 .zip 格式压缩包，其他格式无法读取其中内容。")
                .setPositiveButton("确定", null)
                .show();
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

    /** Finds an already active local slot for the same Telegram user, regardless of login method. */
    private static int findExistingAccountForUserId(long userId, int excludedAccount) {
        if (userId == 0) {
            return -1;
        }
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            if (i != excludedAccount && UserConfig.getInstance(i).isClientActivated()
                    && UserConfig.getInstance(i).getClientUserId() == userId) {
                return i;
            }
        }
        return -1;
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
            batch.processedCount += candidates.size() - index;
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
            if (error != null) {
                if (isNetworkUnavailable(manager, error)) {
                    callback.onDeferred("网络不可用，已保留本地授权，联网后请重新验证");
                } else {
                    callback.onFailed();
                }
                return;
            }
            if (!(response instanceof Vector)) {
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
                callback.onDeferred("网络连接超时，已保留本地授权，联网后请重新验证");
            }
        }, VERIFY_TIMEOUT_MS);
    }

    private static boolean isNetworkUnavailable(ConnectionsManager manager, TLRPC.TL_error error) {
        try {
            if (manager != null && manager.getConnectionState() == ConnectionsManager.ConnectionStateWaitingForNetwork) {
                return true;
            }
        } catch (Throwable ignore) {
        }
        if (error == null || error.text == null) {
            return false;
        }
        String text = error.text.toUpperCase(Locale.ROOT);
        return text.contains("NETWORK") || text.contains("CONNECTION") || text.contains("TIMEOUT")
                || text.contains("TIMED_OUT") || text.contains("RPC_CALL_FAIL") || text.contains("MSG_WAIT_FAILED");
    }

    private static void clearUnusedImportedKey(int accountNum) {
        try {
            ConnectionsManager.getInstance(accountNum).cleanup(true);
        } catch (Throwable ignore) {
        }
    }

    private static TextView createTextView(Activity activity, String text, int sizeSp, int color) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private static GradientDrawable createRoundedBackground(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    private static View createMetricCard(Activity activity, String label, int value, int accentColor) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8));
        card.setBackground(createRoundedBackground(Color.rgb(247, 249, 253), AndroidUtilities.dp(14)));
        TextView valueView = createTextView(activity, String.valueOf(value), 25, accentColor);
        valueView.setGravity(Gravity.CENTER);
        TextView labelView = createTextView(activity, label, 13, Color.rgb(86, 100, 124));
        labelView.setGravity(Gravity.CENTER);
        card.addView(valueView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(labelView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private static final class ImportProgressDialog {
        private final Activity activity;
        private AlertDialog dialog;
        private TextView titleView;
        private TextView detailView;
        private TextView percentView;
        private ProgressBar progressBar;

        ImportProgressDialog(Activity activity) {
            this.activity = activity;
        }

        void showScanning() {
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) {
                    return;
                }
                if (dialog != null && dialog.isShowing()) {
                    return;
                }
                int padding = AndroidUtilities.dp(22);
                LinearLayout content = new LinearLayout(activity);
                content.setOrientation(LinearLayout.VERTICAL);
                content.setPadding(padding, AndroidUtilities.dp(15), padding, AndroidUtilities.dp(10));
                content.setBackgroundColor(Color.WHITE);

                titleView = createTextView(activity, "当前账号正在加载中", 21, Color.rgb(22, 35, 63));
                content.addView(titleView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                detailView = createTextView(activity, "正在扫描压缩包并验证文件格式…", 14, Color.rgb(91, 104, 128));
                LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                detailParams.topMargin = AndroidUtilities.dp(7);
                content.addView(detailView, detailParams);

                progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
                progressBar.setMax(100);
                progressBar.setIndeterminate(true);
                LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(7));
                progressParams.topMargin = AndroidUtilities.dp(20);
                content.addView(progressBar, progressParams);

                percentView = createTextView(activity, "正在准备…", 14, Color.rgb(32, 105, 197));
                percentView.setGravity(Gravity.CENTER_HORIZONTAL);
                LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                percentParams.topMargin = AndroidUtilities.dp(9);
                content.addView(percentView, percentParams);

                dialog = new AlertDialog.Builder(activity)
                        .setView(content)
                        .setCancelable(false)
                        .create();
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
            });
        }

        void showImporting(BatchState batch, int currentAccount) {
            if (dialog == null || !dialog.isShowing()) {
                showScanning();
            }
            int total = Math.max(1, batch.scannedCount);
            int completed = Math.max(0, Math.min(batch.processedCount, total));
            int current = Math.max(1, Math.min(currentAccount, total));
            int percent = Math.max(0, Math.min(100, Math.round((completed * 100f) / total)));
            if (titleView != null) {
                titleView.setText("当前账号正在加载中");
            }
            if (detailView != null) {
                detailView.setText("正在校验第 " + current + " 个账号，共 " + total + " 个");
            }
            if (progressBar != null) {
                progressBar.setIndeterminate(false);
                progressBar.setProgress(percent);
            }
            if (percentView != null) {
                percentView.setText("加载进度  " + percent + "%  ·  已完成 " + completed + " / " + total);
            }
        }

        void dismiss() {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }

    private static void showBatchSummary(Activity activity, LoginActivity loginActivity, BatchState batch) {
        if (activity.isFinishing() || batch.summaryShown) {
            return;
        }
        batch.summaryShown = true;
        batch.progress.showImporting(batch, batch.scannedCount);
        AndroidUtilities.runOnUIThread(() -> {
            if (activity.isFinishing()) {
                return;
            }
            batch.progress.dismiss();
            showBatchSummaryContent(activity, loginActivity, batch);
        }, 180);
    }

    private static void showBatchSummaryContent(Activity activity, LoginActivity loginActivity, BatchState batch) {
        if (activity.isFinishing()) {
            return;
        }
        int padding = AndroidUtilities.dp(22);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, AndroidUtilities.dp(12), padding, AndroidUtilities.dp(4));

        TextView title = createTextView(activity, "协议登录完成", 22, Color.rgb(22, 35, 63));
        content.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = createTextView(activity, "导入校验已完成，以下为真实验证结果", 14, Color.rgb(91, 104, 128));
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = AndroidUtilities.dp(5);
        content.addView(subtitle, subtitleParams);

        LinearLayout metrics = new LinearLayout(activity);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams metricsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metricsParams.topMargin = AndroidUtilities.dp(18);
        content.addView(metrics, metricsParams);
        metrics.addView(createMetricCard(activity, "有效", batch.successCount, Color.rgb(20, 166, 106)), new LinearLayout.LayoutParams(0, AndroidUtilities.dp(86), 1f));
        LinearLayout.LayoutParams metricInvalidParams = new LinearLayout.LayoutParams(0, AndroidUtilities.dp(86), 1f);
        metricInvalidParams.leftMargin = AndroidUtilities.dp(6);
        metrics.addView(createMetricCard(activity, "无效", batch.failedCount, Color.rgb(220, 74, 74)), metricInvalidParams);
        LinearLayout.LayoutParams metricDeferredParams = new LinearLayout.LayoutParams(0, AndroidUtilities.dp(86), 1f);
        metricDeferredParams.leftMargin = AndroidUtilities.dp(6);
        metrics.addView(createMetricCard(activity, "待联网", batch.deferredCount, Color.rgb(226, 150, 37)), metricDeferredParams);
        LinearLayout.LayoutParams metricLastParams = new LinearLayout.LayoutParams(0, AndroidUtilities.dp(86), 1f);
        metricLastParams.leftMargin = AndroidUtilities.dp(6);
        metrics.addView(createMetricCard(activity, "已存在", batch.alreadyImportedCount, Color.rgb(85, 105, 155)), metricLastParams);

        String detail = "共扫描 " + batch.scannedCount + " 个账户文件";
        if (batch.noFreeSlot) {
            detail += "\n账户槽位已满，未继续导入剩余会话。";
        } else if (batch.scannedCount == 0) {
            detail += "\n未找到符合该登录方式的有效文件。";
        } else if (batch.alreadyImportedCount > 0) {
            detail += "\n重复账户已自动跳过，不会重复创建。";
        }
        if (batch.deferredCount > 0) {
            detail += "\n有 " + batch.deferredCount + " 个账号因无网络或连接超时暂未验证，未计为无效。会话与 tdata 已保留本地授权；通行密钥请在联网后重新导入验证。";
            if (batch.firstDeferredReason != null) {
                detail += "\n原因：" + batch.firstDeferredReason;
            }
        }
        TextView detailView = createTextView(activity, detail, 15, Color.rgb(53, 65, 86));
        detailView.setBackground(createRoundedBackground(Color.rgb(241, 245, 252), AndroidUtilities.dp(14)));
        detailView.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(13), AndroidUtilities.dp(15), AndroidUtilities.dp(13));
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = AndroidUtilities.dp(14);
        content.addView(detailView, detailParams);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setView(content)
                .setCancelable(false);
        if (batch.firstSuccess != null) {
            builder.setPositiveButton("进入账户", (dialog, which) ->
                    loginActivity.completeProtocolLogin(batch.firstSuccess.accountNum,
                            batch.firstSuccess.user, batch.firstSuccess.datacenterId));
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

        void onDeferred(String reason);
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

        static ImportCandidate forPasskey(File file, int kind) {
            return new ImportCandidate(kind, file, null);
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
        final ImportProgressDialog progress;
        final Set<Integer> reservedSlots = new HashSet<>();
        final Set<Long> importedUserIds = new HashSet<>();
        final Set<Long> importedAuthKeyIds = new HashSet<>();
        int successCount;
        int failedCount;
        int processedCount;
        int alreadyImportedCount;
        int deferredCount;
        String firstDeferredReason;
        boolean noFreeSlot;
        boolean passkeyFallbackHintShown;
        boolean summaryShown;
        ImportedAccount firstSuccess;

        BatchState(int scannedCount, int preferredAccount, ImportProgressDialog progress) {
            this.scannedCount = scannedCount;
            this.preferredAccount = preferredAccount;
            this.progress = progress;
        }
    }
}
