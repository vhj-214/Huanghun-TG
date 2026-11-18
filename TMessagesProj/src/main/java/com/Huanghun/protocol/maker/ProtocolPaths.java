package com.Huanghun.protocol.maker;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 协议文件的落盘位置与命名规则。
 *
 * <p>目标目录固定为公共存储的 {@code /storage/emulated/0/黄昏/协议制作/}；
 * 在 Android 11+ 上如果没有拿到全盘读写权限，则退到应用外部私有目录，
 * 并把这个真实路径回报给界面显示，避免用户以为文件没生成。</p>
 */
public final class ProtocolPaths {

    public static final String FOLDER_ROOT = "黄昏";
    public static final String FOLDER_BUILD = "协议制作";

    private ProtocolPaths() {
    }

    public static final class Resolved {
        public final File directory;
        public final boolean isPublic;

        Resolved(File directory, boolean isPublic) {
            this.directory = directory;
            this.isPublic = isPublic;
        }
    }

    public static Resolved resolveOutputDirectory(Context context) {
        File publicRoot = new File(Environment.getExternalStorageDirectory(),
                FOLDER_ROOT + File.separator + FOLDER_BUILD);
        if (ensureWritable(publicRoot)) {
            return new Resolved(publicRoot, true);
        }
        File externalFiles = context.getExternalFilesDir(null);
        if (externalFiles != null) {
            File privateRoot = new File(externalFiles,
                    FOLDER_ROOT + File.separator + FOLDER_BUILD);
            if (ensureWritable(privateRoot)) {
                return new Resolved(privateRoot, false);
            }
        }
        File cacheRoot = new File(context.getFilesDir(),
                FOLDER_ROOT + File.separator + FOLDER_BUILD);
        ensureWritable(cacheRoot);
        return new Resolved(cacheRoot, false);
    }

    private static boolean ensureWritable(File directory) {
        if (directory == null) {
            return false;
        }
        if (!directory.exists() && !directory.mkdirs()) {
            return false;
        }
        File probe = new File(directory, ".zaka_write_probe");
        try {
            if (probe.exists() && !probe.delete()) {
                return false;
            }
            if (!probe.createNewFile()) {
                return false;
            }
            return probe.length() == 0;
        } catch (Throwable ignore) {
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            probe.delete();
        }
    }

    public static File uniqueTarget(File directory, String baseName, String extension) throws java.io.IOException {
        if (directory == null || (!directory.exists() && !directory.mkdirs()) || !directory.isDirectory()) {
            throw new java.io.IOException("输出目录不可用");
        }
        for (int index = 1; index < 10000; index++) {
            String suffix = index == 1 ? "" : "(" + index + ")";
            File candidate = new File(directory, baseName + suffix + extension);
            try {
                if (candidate.createNewFile()) {
                    return candidate;
                }
            } catch (java.io.IOException error) {
                if (!candidate.exists()) {
                    throw error;
                }
            }
        }
        throw new java.io.IOException("无法分配不重复的输出文件名");
    }

    public static File uniqueDirectory(File directory, String baseName) {
        if (!directory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
        }
        File candidate = new File(directory, baseName);
        int index = 2;
        while (candidate.exists()) {
            candidate = new File(directory, baseName + "(" + index + ")");
            index++;
        }
        return candidate;
    }

    public static boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT < 30) {
            return true;
        }
        try {
            return Environment.isExternalStorageManager();
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static File workDir(Context context) {
        File dir = new File(context.getCacheDir(), "protocol_build");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** Create a collision-resistant private work directory; ownership is granted only after mkdir succeeds. */
    public static File createTemporaryDirectory(Context context, String prefix) throws IOException {
        File parent = workDir(context);
        if ((!parent.exists() && !parent.mkdirs()) || !parent.isDirectory()) {
            throw new IOException("无法创建协议临时目录");
        }
        for (int attempt = 0; attempt < 16; attempt++) {
            File candidate = new File(parent, prefix + UUID.randomUUID());
            if (candidate.mkdir()) {
                return candidate;
            }
        }
        throw new IOException("无法分配唯一协议临时目录");
    }

    /** Create an owned temporary file without ever truncating a path reserved by another operation. */
    public static File createTemporaryFile(Context context, String prefix, String suffix) throws IOException {
        File parent = workDir(context);
        if ((!parent.exists() && !parent.mkdirs()) || !parent.isDirectory()) {
            throw new IOException("无法创建协议临时目录");
        }
        for (int attempt = 0; attempt < 16; attempt++) {
            File candidate = new File(parent, prefix + UUID.randomUUID() + suffix);
            if (candidate.createNewFile()) {
                return candidate;
            }
        }
        throw new IOException("无法分配唯一协议临时文件");
    }

    public static void deleteRecursive(File file) {
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
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
