package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONTokener;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 黄昏定制版内置视频库。
 *
 * 所选视频会复制到应用私有目录，仅保存在当前设备，并按 Telegram 账号隔离保存。
 * 使用私有副本而非 Content URI，避免系统文档提供方回收 URI 授权后导致录制预览或发送失败。
 */
public final class HuanghunVideoLibraryHelper {

    private static final String PREFERENCES = "huanghun_builtin_video_library";
    private static final String DIRECTORY = "huanghun_builtin_videos";
    private static final String KEY_PREFIX = "videos_";
    private static final long MAX_VIDEO_SIZE_BYTES = 500L * 1024L * 1024L;

    private HuanghunVideoLibraryHelper() {
    }

    public static final class ImportResult {
        public final int importedCount;
        public final ArrayList<String> errors;

        public ImportResult(int importedCount, ArrayList<String> errors) {
            this.importedCount = importedCount;
            this.errors = errors;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    public static final class VideoItem {
        public final String path;
        public final String fileName;
        public final long durationMs;
        public final long size;

        private VideoItem(String path, String fileName, long durationMs, long size) {
            this.path = path;
            this.fileName = fileName;
            this.durationMs = durationMs;
            this.size = size;
        }
    }

    private static String key(int account) {
        return KEY_PREFIX + account;
    }

    /**
     * 返回顺序稳定且仍存在的本地视频路径；损坏或已被系统清理的路径会自动移出保存列表。
     */
    public static ArrayList<String> getVideoPaths(Context context, int account) {
        ArrayList<String> stored = readPaths(context, account);
        ArrayList<String> valid = new ArrayList<>(stored.size());
        boolean changed = false;
        for (String path : stored) {
            if (isOwnedVideoFile(context, path) && new File(path).isFile() && new File(path).length() > 0) {
                valid.add(path);
            } else {
                changed = true;
            }
        }
        if (changed) {
            writePaths(context, account, valid, true);
        }
        return valid;
    }

    public static ArrayList<VideoItem> getVideoItems(Context context, int account) {
        ArrayList<String> paths = getVideoPaths(context, account);
        ArrayList<VideoItem> result = new ArrayList<>(paths.size());
        ArrayList<String> unavailable = new ArrayList<>();
        for (String path : paths) {
            long duration = readDuration(path);
            if (duration <= 0) {
                unavailable.add(path);
                continue;
            }
            File file = new File(path);
            result.add(new VideoItem(path, file.getName(), duration, file.length()));
        }
        if (!unavailable.isEmpty()) {
            deleteVideos(context, account, unavailable);
        }
        return result;
    }

    public static int getVideoCount(Context context, int account) {
        return getVideoPaths(context, account).size();
    }

    /**
     * 将文档选择器返回的视频复制到应用私有目录并追加到现有顺序末尾。
     * 单个文件失败不会影响同批次其它合法视频。
     */
    public static ImportResult importVideos(Context context, int account, List<Uri> sources) {
        ArrayList<String> paths = getVideoPaths(context, account);
        ArrayList<String> errors = new ArrayList<>();
        int imported = 0;
        if (sources == null || sources.isEmpty()) {
            errors.add("未读取到所选视频。");
            return new ImportResult(0, errors);
        }
        for (Uri source : sources) {
            try {
                String path = importVideo(context, source, paths.size() + imported);
                paths.add(path);
                imported++;
            } catch (Throwable e) {
                FileLog.e(e);
                String message = e.getMessage();
                errors.add(message == null || message.length() == 0 ? "有一个视频无法导入。" : message);
            }
        }
        if (imported > 0) {
            writePaths(context, account, paths, true);
        }
        return new ImportResult(imported, errors);
    }

    public static void deleteVideos(Context context, int account, Collection<String> pathsToDelete) {
        if (pathsToDelete == null || pathsToDelete.isEmpty()) {
            return;
        }
        Set<String> deleteSet = new HashSet<>(pathsToDelete);
        ArrayList<String> kept = new ArrayList<>();
        for (String path : getVideoPaths(context, account)) {
            if (deleteSet.contains(path)) {
                deleteOwnedFile(context, path);
            } else {
                kept.add(path);
            }
        }
        writePaths(context, account, kept, true);
    }

    public static void clearVideos(Context context, int account) {
        ArrayList<String> paths = getVideoPaths(context, account);
        for (String path : paths) {
            deleteOwnedFile(context, path);
        }
        writePaths(context, account, new ArrayList<>(), true);
    }

    private static ArrayList<String> readPaths(Context context, int account) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String value = preferences.getString(key(account), null);
        ArrayList<String> paths = new ArrayList<>();
        if (value == null || value.length() == 0) {
            return paths;
        }
        try {
            JSONArray array = new JSONArray(new JSONTokener(value));
            for (int i = 0; i < array.length(); i++) {
                String path = array.optString(i, null);
                if (path != null && path.length() > 0 && !paths.contains(path)) {
                    paths.add(path);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
            preferences.edit().remove(key(account)).apply();
        }
        return paths;
    }

    private static void writePaths(Context context, int account, List<String> paths, boolean commit) {
        JSONArray array = new JSONArray();
        if (paths != null) {
            for (String path : paths) {
                if (path != null && path.length() > 0) {
                    array.put(path);
                }
            }
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(key(account), array.toString());
        if (commit) {
            editor.commit();
        } else {
            editor.apply();
        }
    }

    private static String importVideo(Context context, Uri source, int index) throws IOException {
        if (source == null) {
            throw new IOException("未读取到所选视频。");
        }
        File directory = getDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建内置视频存储目录。");
        }
        File target = new File(directory, "video_" + System.currentTimeMillis() + "_" + index + ".mp4");
        long copied = 0;
        try (InputStream input = context.getContentResolver().openInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) {
                throw new IOException("无法读取所选视频。");
            }
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                copied += count;
                if (copied > MAX_VIDEO_SIZE_BYTES) {
                    throw new IOException("所选视频超过 500 MB，无法导入。");
                }
                output.write(buffer, 0, count);
            }
            output.flush();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw e;
        }
        if (copied <= 0 || !target.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw new IOException("所选视频为空或无法读取。");
        }
        if (readDuration(target.getAbsolutePath()) <= 0) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw new IOException("所选文件不是可播放的视频。");
        }
        return target.getAbsolutePath();
    }

    private static long readDuration(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return duration == null ? 0 : Long.parseLong(duration);
        } catch (Throwable e) {
            FileLog.e(e);
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignore) {
            }
        }
    }

    private static File getDirectory(Context context) {
        return new File(context.getFilesDir(), DIRECTORY);
    }

    private static boolean isOwnedVideoFile(Context context, String path) {
        if (path == null || path.length() == 0) {
            return false;
        }
        try {
            File directory = getDirectory(context).getCanonicalFile();
            File file = new File(path).getCanonicalFile();
            return file.getPath().startsWith(directory.getPath() + File.separator);
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    private static void deleteOwnedFile(Context context, String path) {
        if (!isOwnedVideoFile(context, path)) {
            return;
        }
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
