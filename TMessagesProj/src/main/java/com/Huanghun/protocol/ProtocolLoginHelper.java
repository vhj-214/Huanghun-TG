package com.Huanghun.protocol;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.ui.LoginActivity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Imports an owner-provided Telethon session archive into the app's native tgnet store.
 * Import success is reported only after Telegram accepts the restored auth key and returns
 * the authenticated self user. No placeholder user or synthetic auth key is ever created.
 */
public final class ProtocolLoginHelper {
    private static final int MAX_ENTRIES = 512;
    private static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L;

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

        // All three menu entries use the same ZIP scanner. The archive is accepted
        // whenever it contains a valid .session file; optional JSON files are ignored.
        showToast(activity, "正在扫描压缩包中的 .session 会话文件…");

        new Thread(() -> {
            try {
                File importDir = new File(activity.getCacheDir(), "protocol_import");
                deleteRecursive(importDir);
                if (!importDir.mkdirs() && !importDir.isDirectory()) {
                    throw new IOException("无法创建临时目录");
                }

                File sessionFile = extractFirstSessionFile(activity, uri, importDir);
                if (sessionFile == null) {
                    throw new IOException("压缩包中没有找到 .session 会话文件");
                }

                showToast(activity, "已找到 .session，正在验证 Telegram 授权…");
                ProtocolParser.SessionData data = ProtocolParser.parseTelethonSession(sessionFile);
                int accountNum = loginActivity.getCurrentAccount();

                // The native layer persists the real 256-byte MTProto auth key before the
                // verification RPC is scheduled. The key is never displayed or logged.
                ConnectionsManager.native_importAuthKey(accountNum, data.dcId, data.address, data.port, data.authKey);
                verifySession(loginActivity, activity, accountNum, data.dcId);
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                showAlert(activity, "协议登录失败", "无法导入压缩包中的 .session 会话文件：\n" + reason);
            }
        }, "protocol-login").start();
    }

    private static File extractFirstSessionFile(Activity activity, Uri uri, File importDir) throws Exception {
        File sessionFile = null;
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
                        String lower = entry.getName().toLowerCase(Locale.ROOT);
                        if (lower.endsWith(".session") && sessionFile == null) {
                            sessionFile = output;
                        }
                    }
                    zip.closeEntry();
                }
            }
        }
        return sessionFile;
    }

    private static void verifySession(LoginActivity loginActivity, Activity activity, int accountNum, int dcId) {
        showToast(activity, "正在连接 Telegram 并验证会话，请稍候…");
        final ConnectionsManager manager = ConnectionsManager.getInstance(accountNum);
        // Importing an auth key replaces a suspended DC connection. Explicitly resume it,
        // then route the verification RPC to the imported DC instead of the old default DC.
        manager.resumeNetworkMaybe();

        final AtomicBoolean finished = new AtomicBoolean(false);
        TLRPC.TL_users_getUsers request = new TLRPC.TL_users_getUsers();
        request.id.add(new TLRPC.TL_inputUserSelf());
        final int requestToken = manager.sendRequest(request, (response, error) -> {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            if (error != null) {
                String reason = error.text != null ? error.text : "UNKNOWN_ERROR";
                showAlert(activity, "协议登录失败", "Telegram 未接受该会话授权：" + reason + "\n\n请确认压缩包中的 .session 仍有效，且属于您本人可使用的账号。");
                return;
            }
            if (!(response instanceof Vector)) {
                showAlert(activity, "协议登录失败", "服务器未返回有效的当前账号信息。");
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
                showAlert(activity, "协议登录失败", "服务器没有返回可用的当前账号信息。");
                return;
            }
            final TLRPC.User authenticatedUser = self;
            activity.runOnUiThread(() -> loginActivity.completeProtocolLogin(authenticatedUser, dcId));
        }, null, null, 0, dcId, ConnectionsManager.ConnectionTypeGeneric, true);

        AndroidUtilities.runOnUIThread(() -> {
            if (finished.compareAndSet(false, true)) {
                manager.cancelRequest(requestToken, true);
                showAlert(activity, "协议登录超时", "20 秒内未收到 Telegram 的验证结果。会话文件已被识别，但客户端未能建立验证连接；请检查网络或重试。\n\n此提示不表示导入成功。 ");
            }
        }, 20_000);
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
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    private static void showAlert(Activity activity, String title, String message) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing()) {
                new AlertDialog.Builder(activity)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("确定", null)
                        .show();
            }
        });
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
}
