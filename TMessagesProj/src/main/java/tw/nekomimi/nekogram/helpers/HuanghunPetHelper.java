package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Safe, resource-only desktop pet package importer. Package files are never executed. */
public final class HuanghunPetHelper {
    private static final String ROOT = "huanghun_pets";
    private static final String INSTALLED = "installed";
    private static final String PREFS = "huanghun_pets";
    private static final long MAX_ZIP_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_UNPACKED_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_FILES = 512;
    private static final String[] ALLOWED = {".png", ".webp", ".jpg", ".jpeg", ".ogg", ".mp3", ".wav", ".json", ".txt", ".md", ".zip"};
    private static final String[] FORBIDDEN = {".exe", ".apk", ".aab", ".dll", ".jar", ".so", ".bat", ".cmd", ".sh", ".ps1", ".js", ".lua", ".py", ".class"};

    public static final class PetInfo {
        public final String id, name, version, author, description;
        public final File directory;
        PetInfo(File directory, JSONObject manifest) {
            this.directory = directory;
            id = directory.getName();
            name = manifest.optString("name", id);
            version = manifest.optString("version", "未知版本");
            author = manifest.optString("author", "");
            description = manifest.optString("description", "");
        }
        public File preview() { return new File(directory, "preview.png"); }
    }

    private HuanghunPetHelper() {}
    private static File root(Context c) { return new File(c.getFilesDir(), ROOT); }
    private static File installed(Context c) { return new File(root(c), INSTALLED); }
    private static void mkdirs(Context c) { installed(c).mkdirs(); }
    public static String activeId(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("active_id", ""); }
    public static void setActive(Context c, String id) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("active_id", id == null ? "" : id).apply(); }

    public static ArrayList<PetInfo> list(Context c) {
        mkdirs(c);
        ArrayList<PetInfo> result = new ArrayList<>();
        File[] dirs = installed(c).listFiles(File::isDirectory);
        if (dirs != null) for (File dir : dirs) {
            try { result.add(new PetInfo(dir, readManifest(dir))); } catch (Exception ignored) {}
        }
        result.sort(Comparator.comparing(p -> p.name.toLowerCase(Locale.ROOT)));
        return result;
    }

    public static String importZip(Context context, Uri uri) throws Exception {
        mkdirs(context);
        File temp = new File(root(context), "temp_" + UUID.randomUUID());
        temp.mkdirs();
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new Exception("无法读取所选文件");
            unpackArchive(new LimitedInputStream(raw, MAX_ZIP_BYTES), temp, true);
        } catch (Exception e) { deleteRecursive(temp); throw e; }
        try {
            File manifestFile = new File(temp, "manifest.json");
            File[] nestedArchives = temp.listFiles((dir, filename) -> filename.toLowerCase(Locale.ROOT).endsWith(".zip"));
            if (!manifestFile.isFile() && nestedArchives != null && nestedArchives.length == 1) {
                File nestedTemp = new File(root(context), "temp_nested_" + UUID.randomUUID());
                nestedTemp.mkdirs();
                try (InputStream nested = new FileInputStream(nestedArchives[0])) {
                    unpackArchive(new LimitedInputStream(nested, MAX_ZIP_BYTES), nestedTemp, false);
                }
                deleteRecursive(temp);
                temp = nestedTemp;
                manifestFile = new File(temp, "manifest.json");
            }
            if (!manifestFile.isFile()) throw new Exception("缺少 manifest.json");
            JSONObject manifest = new JSONObject(readText(manifestFile));
            String format = manifest.optString("format", "huanghun_pet_pack");
            if (!"huanghun_pet_pack".equals(format)) throw new Exception("不是有效的桌宠包");
            String name = manifest.optString("name", "").trim();
            if (name.isEmpty() || name.length() > 64) throw new Exception("桌宠名称无效");
            if (manifest.optString("version", "").trim().isEmpty()) throw new Exception("桌宠版本缺失");
            String preview = manifest.optString("preview", "preview.png");
            if (preview.contains("..") || !new File(temp, preview).getCanonicalPath().startsWith(temp.getCanonicalPath() + File.separator) || !new File(temp, preview).isFile()) throw new Exception("缺少有效的预览图");
            if (BitmapFactory.decodeFile(new File(temp, preview).getAbsolutePath()) == null) throw new Exception("预览图损坏");
            String id = manifest.optString("id", "").trim();
            if (!id.matches("[A-Za-z0-9_-]{1,64}")) id = "pet_" + name.replaceAll("[^A-Za-z0-9_-]", "_").replaceAll("_+", "_");
            if (!id.matches("[A-Za-z0-9_-]{1,64}")) id = "pet_" + UUID.randomUUID().toString().replace("-", "");
            manifest.put("id", id);
            manifest.put("format", "huanghun_pet_pack");
            try (FileOutputStream out = new FileOutputStream(manifestFile)) { out.write(manifest.toString(2).getBytes("UTF-8")); }
            File target = new File(installed(context), id);
            deleteRecursive(target);
            if (!temp.renameTo(target)) throw new Exception("无法保存桌宠资源");
            return id;
        } catch (Exception e) { deleteRecursive(temp); throw e; }
    }

    private static void unpackArchive(InputStream raw, File destination, boolean allowWrapperZip) throws Exception {
        long total = 0;
        int files = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory()) continue;
                if (++files > MAX_FILES) throw new Exception("桌宠包文件数量过多");
                if (name.isEmpty() || name.startsWith("/") || name.contains("../") || name.contains("..\\")) throw new Exception("压缩包包含不安全路径");
                String lower = name.toLowerCase(Locale.ROOT);
                for (String bad : FORBIDDEN) if (lower.endsWith(bad)) throw new Exception("压缩包包含不允许的文件");
                boolean isZip = lower.endsWith(".zip");
                if (isZip && !allowWrapperZip) throw new Exception("桌宠包不允许嵌套压缩包");
                boolean allowed = false;
                for (String ext : ALLOWED) if (lower.endsWith(ext)) { allowed = true; break; }
                if (!allowed) throw new Exception("压缩包包含不支持的文件类型");
                File out = new File(destination, name);
                String base = destination.getCanonicalPath() + File.separator;
                if (!out.getCanonicalPath().startsWith(base)) throw new Exception("压缩包包含不安全路径");
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        total += n;
                        if (total > MAX_UNPACKED_BYTES) throw new Exception("桌宠包解压后过大");
                        os.write(buffer, 0, n);
                    }
                }
            }
        }
    }

    public static PetInfo get(Context c, String id) throws Exception {
        File dir = new File(installed(c), id);
        if (!dir.isDirectory()) throw new Exception("桌宠不存在");
        return new PetInfo(dir, readManifest(dir));
    }
    public static void delete(Context c, String id) { if (id != null && id.matches("[A-Za-z0-9_-]{1,64}")) { deleteRecursive(new File(installed(c), id)); if (id.equals(activeId(c))) setActive(c, ""); } }
    private static JSONObject readManifest(File dir) throws Exception { return new JSONObject(readText(new File(dir, "manifest.json"))); }
    private static String readText(File file) throws Exception {
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] b = new byte[4096]; int n; while ((n = in.read(b)) != -1) { if (out.size() + n > 1024 * 1024) throw new Exception("配置文件过大"); out.write(b, 0, n); } return out.toString("UTF-8"); }
    }
    private static void deleteRecursive(File file) { if (file == null || !file.exists()) return; if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteRecursive(child); } file.delete(); }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long count;
        LimitedInputStream(InputStream input, long limit) { super(input); this.limit = limit; }
        @Override public int read() throws java.io.IOException {
            int value = super.read();
            if (value >= 0 && ++count > limit) throw new java.io.IOException("桌宠包文件过大");
            return value;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
            int value = super.read(buffer, offset, length);
            if (value > 0 && (count += value) > limit) throw new java.io.IOException("桌宠包文件过大");
            return value;
        }
    }
}
