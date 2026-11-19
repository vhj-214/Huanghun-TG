package com.Huanghun.protocol.maker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** 打包 / 解包工具。所有协议产物统一以 zip 交付。 */
public final class ZipUtil {

    private static final long MAX_UNCOMPRESSED = 128L * 1024L * 1024L;
    private static final long MAX_COMPRESSED = 256L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_PATH_DEPTH = 16;

    private ZipUtil() {
    }

    /** 把目录内容打包到 zipFile，条目路径相对于 sourceDir。 */
    public static void zipDirectory(File sourceDir, File zipFile) throws IOException {
        if (sourceDir == null || !sourceDir.isDirectory() || zipFile == null) {
            throw new IOException("压缩源目录或目标文件无效");
        }
        try {
            SessionCodec.ensureParent(zipFile);
            try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zipFile))) {
                zipInto(output, sourceDir, "");
            }
        } catch (IOException error) {
            //noinspection ResultOfMethodCallIgnored
            zipFile.delete();
            throw error;
        }
    }

    private static void zipInto(ZipOutputStream output, File file, String prefix) throws IOException {
        File[] children = file.listFiles();
        if (children == null) {
            throw new IOException("无法读取压缩目录：" + file.getName());
        }
        java.util.Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        byte[] buffer = new byte[8192];
        for (File child : children) {
            String entryName = prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName();
            if (!shouldInclude(child, prefix)) {
                continue;
            }
            if (child.isDirectory()) {
                zipInto(output, child, entryName);
            } else {
                output.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream input = new FileInputStream(child)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
                output.closeEntry();
            }
        }
    }

    /** Keep only stable protocol artifacts; SQLite journal files are temporary. */
    private static boolean shouldInclude(File child, String prefix) {
        String name = child.getName();
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains(".session-journal")) {
            return false;
        }
        if (!prefix.isEmpty()) {
            return true;
        }
        if (child.isDirectory()) {
            return "tdata".equalsIgnoreCase(name);
        }
        return lower.endsWith(".session") || lower.endsWith(".json");
    }

    /**
     * 解压到目标目录，拒绝 ../ 这类越界条目，并限制解压总量。
     */
    public static void unzip(File zipFile, File targetDir) throws IOException {
        if (zipFile == null || !zipFile.isFile() || zipFile.length() > MAX_COMPRESSED) {
            throw new IOException("压缩包不存在或体积超过 256 MiB");
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("无法创建解压目录");
        }
        String canonicalTarget = targetDir.getCanonicalPath() + File.separator;
        long total = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("压缩包内文件过多（最多 4096 项）");
                }
                String name = entry.getName();
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (name.split("/", -1).length > MAX_PATH_DEPTH) {
                    throw new IOException("压缩包目录层级过深：" + name);
                }
                File output = new File(targetDir, name);
                String canonical = output.getCanonicalPath();
                if (!canonical.startsWith(canonicalTarget)) {
                    throw new IOException("压缩包内含有越界路径：" + name);
                }
                if (entry.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    output.mkdirs();
                    continue;
                }
                File parent = output.getParentFile();
                if (parent != null && !parent.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    parent.mkdirs();
                }
                try (FileOutputStream fileOutput = new FileOutputStream(output)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_UNCOMPRESSED) {
                            throw new IOException("压缩包解压后体积过大");
                        }
                        fileOutput.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    static boolean isZip(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        try (InputStream input = new FileInputStream(file)) {
            byte[] header = new byte[4];
            if (input.read(header) != 4) {
                return false;
            }
            return header[0] == 'P' && header[1] == 'K';
        } catch (IOException ignore) {
            return false;
        }
    }

    static byte[] readAll(File file) throws IOException {
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(file.length(), 1 << 20))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
