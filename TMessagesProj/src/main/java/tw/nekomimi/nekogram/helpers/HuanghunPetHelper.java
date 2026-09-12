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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final int MAX_FILES = 1024;
    private static final String[] ALLOWED = {".png", ".webp", ".jpg", ".jpeg", ".ogg", ".mp3", ".wav", ".json", ".txt", ".md", ".zip"};
    private static final String[] FORBIDDEN = {".exe", ".apk", ".aab", ".dll", ".jar", ".so", ".bat", ".cmd", ".sh", ".ps1", ".js", ".lua", ".py", ".class"};

    public static final class PetInfo {
        public final String id, name, version, author, description;
        public final File directory;
        PetInfo(File directory, JSONObject manifest) {
            this.directory = directory;
            id = directory.getName();
            name = manifest.optString("name", manifest.optString("character_name", id));
            version = manifest.optString("version", "未知版本");
            author = manifest.optString("author", "");
            description = manifest.optString("description", "");
        }
        public File preview() { return new File(directory, "preview.png"); }
        public long installedAt() { return readInstalledAt(directory); }
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
        File unpackRoot = new File(root(context), "temp_" + UUID.randomUUID());
        unpackRoot.mkdirs();
        File packageRoot = unpackRoot;
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new Exception("无法读取所选文件");
            unpackArchive(raw, unpackRoot, true);
        } catch (Exception e) {
            deleteRecursive(unpackRoot);
            throw e;
        }
        try {
            // Support the documented outer wrapper: README + one inner pet_pack.zip.
            File manifestFile = new File(packageRoot, "manifest.json");
            File[] nestedArchives = packageRoot.listFiles((dir, filename) -> filename.toLowerCase(Locale.ROOT).endsWith(".zip"));
            if (!manifestFile.isFile() && nestedArchives != null && nestedArchives.length == 1) {
                File nestedRoot = new File(root(context), "temp_nested_" + UUID.randomUUID());
                nestedRoot.mkdirs();
                try (InputStream nested = new FileInputStream(nestedArchives[0])) {
                    unpackArchive(nested, nestedRoot, false);
                }
                deleteRecursive(unpackRoot);
                packageRoot = nestedRoot;
            }

            // Support packages whose actual manifest is under one top-level pet_pack directory.
            packageRoot = normalizePackageRoot(packageRoot);
            manifestFile = new File(packageRoot, "manifest.json");
            if (!manifestFile.isFile()) throw new Exception("缺少 manifest.json");
            JSONObject manifest = new JSONObject(readText(manifestFile));
            boolean modernFormat = !manifest.optString("character_name", "").trim().isEmpty() && manifest.optJSONObject("animations") != null;
            String format = manifest.optString("format", "");
            if (!modernFormat && !"huanghun_pet_pack".equals(format)) throw new Exception("不是有效的桌宠包");
            validateRequiredAnimations(packageRoot, manifest);

            String name = manifest.optString("name", manifest.optString("character_name", "")).trim();
            if (name.isEmpty() || name.length() > 64) throw new Exception("桌宠名称无效");
            String version = manifest.optString("version", "1.0").trim();
            if (version.isEmpty()) version = "1.0";
            manifest.put("format", "huanghun_pet_pack");
            manifest.put("name", name);
            manifest.put("version", version);

            File previewFile = safeChild(packageRoot, manifest.optString("preview", ""));
            if (previewFile == null || !previewFile.isFile()) {
                previewFile = findFirstFrame(packageRoot, "idle");
                if (previewFile == null) throw new Exception("缺少有效的预览图或 idle 动画");
                copyFile(previewFile, new File(packageRoot, "preview.png"));
                previewFile = new File(packageRoot, "preview.png");
                manifest.put("preview", "preview.png");
            }
            if (BitmapFactory.decodeFile(previewFile.getAbsolutePath()) == null) throw new Exception("预览图损坏");

            String id = manifest.optString("id", "").trim();
            if (!id.matches("[A-Za-z0-9_-]{1,64}")) {
                String sanitized = name.replaceAll("[^A-Za-z0-9_-]", "_").replaceAll("_+", "_");
                id = sanitized.length() >= 2 ? "pet_" + sanitized : "pet_" + UUID.randomUUID().toString().replace("-", "");
            }
            manifest.put("id", id);
            if (!manifest.has("installed_at")) manifest.put("installed_at", System.currentTimeMillis());
            try (FileOutputStream out = new FileOutputStream(manifestFile)) {
                out.write(manifest.toString(2).getBytes("UTF-8"));
            }
            File target = new File(installed(context), id);
            deleteRecursive(target);
            if (!packageRoot.renameTo(target)) throw new Exception("无法保存桌宠资源");
            if (packageRoot != unpackRoot) deleteRecursive(unpackRoot);
            return id;
        } catch (Exception e) {
            deleteRecursive(packageRoot);
            if (packageRoot != unpackRoot) deleteRecursive(unpackRoot);
            throw e;
        }
    }

    private static File normalizePackageRoot(File directory) {
        File manifest = new File(directory, "manifest.json");
        if (manifest.isFile()) return directory;
        File[] children = directory.listFiles(File::isDirectory);
        if (children != null && children.length == 1 && new File(children[0], "manifest.json").isFile()) {
            return children[0];
        }
        return directory;
    }

    private static void validateRequiredAnimations(File directory, JSONObject manifest) throws Exception {
        if (manifest.optBoolean("partial_body", false)) return;
        String[] required = {"idle", "walk", "jump", "spin", "peek", "crouch", "angry_stomp"};
        for (String name : required) {
            boolean present = hasAnimation(manifest, name);
            if (!present && "idle".equals(name)) present = hasAnimation(manifest, "idle_breath");
            if (!present && "crouch".equals(name)) present = hasAnimation(manifest, "squat_rest");
            if (!present && "angry_stomp".equals(name)) present = hasAnimation(manifest, "stamp_angry");
            if (!present) throw new Exception("全身桌宠缺少必备动画资源: " + name);
        }
    }

    private static boolean hasAnimation(JSONObject manifest, String name) {
        JSONObject object = manifest.optJSONObject("animations");
        if (object != null && object.has(name)) return true;
        org.json.JSONArray array = manifest.optJSONArray("animations");
        if (array != null) for (int i = 0; i < array.length(); i++) if (name.equals(array.optString(i, ""))) return true;
        return false;
    }

    private static File safeChild(File directory, String relativePath) throws Exception {
        if (relativePath == null || relativePath.trim().isEmpty()) return null;
        File file = new File(directory, relativePath.replace('\\', '/'));
        String base = directory.getCanonicalPath() + File.separator;
        return file.getCanonicalPath().startsWith(base) ? file : null;
    }

    private static File findFirstFrame(File directory, String animation) {
        String[] names = "idle".equals(animation) ? new String[]{"idle", "idle_breath"} : new String[]{animation};
        File[] candidates = new File[names.length * 2];
        for (int i = 0; i < names.length; i++) {
            candidates[i * 2] = new File(directory, "frames/" + names[i]);
            candidates[i * 2 + 1] = new File(directory, "images/" + names[i]);
        }
        for (File candidate : candidates) {
            File[] files = candidate.listFiles((dir, filename) -> {
                String lower = filename.toLowerCase(Locale.ROOT);
                return lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            });
            if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                return files[0];
            }
        }
        return null;
    }

    private static void copyFile(File source, File target) throws Exception {
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
    }

    private static void unpackArchive(InputStream raw, File destination, boolean allowWrapperZip) throws Exception {
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
    private static long readInstalledAt(File dir) {
        try {
            long value = new JSONObject(readText(new File(dir, "manifest.json"))).optLong("installed_at", 0L);
            return value > 0L ? value : dir.lastModified();
        } catch (Exception ignored) {
            return dir.lastModified();
        }
    }
    private static String readText(File file) throws Exception {
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] b = new byte[4096]; int n; while ((n = in.read(b)) != -1) { if (out.size() + n > 1024 * 1024) throw new Exception("配置文件过大"); out.write(b, 0, n); } return out.toString("UTF-8"); }
    }
    private static void deleteRecursive(File file) { if (file == null || !file.exists()) return; if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteRecursive(child); } file.delete(); }

}
