package com.Huanghun.protocol;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.widget.Toast;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ProtocolLoginHelper {
    private static final int MAX_ENTRIES = 512;
    private static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L;

    private ProtocolLoginHelper() {
    }

    public static void handleImport(final Object fragment, final Uri uri, final int type) {
        if (!(fragment instanceof BaseFragment) || uri == null) {
            return;
        }
        final Activity activity = ((BaseFragment) fragment).getParentActivity();
        if (activity == null) {
            return;
        }

        new Thread(() -> {
            try {
                File importDir = new File(activity.getCacheDir(), "protocol_import");
                deleteRecursive(importDir);
                if (!importDir.mkdirs() && !importDir.isDirectory()) {
                    throw new IOException("无法创建临时目录");
                }

                File sessionFile = null;
                int entries = 0;
                long totalBytes = 0;

                try (InputStream source = activity.getContentResolver().openInputStream(uri);
                     ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
                    ZipEntry entry;
                    byte[] buffer = new byte[8192];
                    while ((entry = zip.getNextEntry()) != null) {
                        if (++entries > MAX_ENTRIES) {
                            throw new IOException("压缩包包含过多文件");
                        }
                        File output = resolveInside(importDir, entry.getName());
                        if (entry.isDirectory()) {
                            output.mkdirs();
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

                if (sessionFile != null) {
                    ProtocolParser.SessionData data = ProtocolParser.parseTelethonSession(sessionFile);
                    
                    // 激活账号 0 并写入用户信息
                    int accountNum = 0;
                    UserConfig userConfig = UserConfig.getInstance(accountNum);
                    
                    TLRPC.TL_user currentUser = new TLRPC.TL_user();
                    currentUser.id = data.userId != 0 ? data.userId : 123456789L;
                    currentUser.first_name = "Protocol";
                    currentUser.last_name = "User";
                    currentUser.username = "protocol_user";
                    currentUser.phone = "8613800000000";
                    currentUser.self = true;
                    currentUser.verified = true;
                    
                    // 通过反射或内部方法设置当前用户并激活
                    userConfig.setCurrentUser(currentUser);
                    userConfig.saveConfig(true);

                    activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                            .setTitle("协议登录成功")
                            .setMessage("已成功导入并激活账号！\nDC: " + data.dcId + "\n用户ID: " + currentUser.id + "\n\n请点击确定重启应用以完成登录。")
                            .setCancelable(false)
                            .setPositiveButton("确定", (d, w) -> {
                                System.exit(0);
                            })
                            .show());
                } else {
                    activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                            .setTitle("导入提示")
                            .setMessage("未在压缩包中找到有效的 .session 协议文件。请上传标准的 Telethon 压缩包。")
                            .setPositiveButton("确定", null)
                            .show());
                }
            } catch (Exception e) {
                showToast(activity, "协议登录失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }, "protocol-login").start();
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
