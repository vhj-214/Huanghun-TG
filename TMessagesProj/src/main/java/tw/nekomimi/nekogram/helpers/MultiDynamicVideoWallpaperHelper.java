package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import org.json.JSONArray;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 独立于单个动态壁纸的多视频循环库。所有视频仅保存于本机。 */
public final class MultiDynamicVideoWallpaperHelper {
    private static final String PREFS = "huanghun_multi_dynamic_wallpapers";
    private static final String DIRECTORY = "huanghun_multi_dynamic_wallpapers";
    private static final String KEY_VIDEOS = "videos_";
    private static final String KEY_MODE = "mode_";
    private static final String KEY_ENABLED = "enabled_";
    public static final int MODE_ORDER = 0;
    public static final int MODE_RANDOM = 1;

    public static final class VideoItem {
        public final String path;
        public final long durationMs;
        public final long size;
        public VideoItem(String path, long durationMs, long size) {
            this.path = path;
            this.durationMs = durationMs;
            this.size = size;
        }
    }

    public static final class FetchResult {
        public final int imported;
        public final int skippedLandscape;
        public final ArrayList<String> errors;
        public FetchResult(int imported, int skippedLandscape, ArrayList<String> errors) {
            this.imported = imported;
            this.skippedLandscape = skippedLandscape;
            this.errors = errors;
        }
    }

    private MultiDynamicVideoWallpaperHelper() {}

    private static String key(String prefix, int account) {
        return prefix + account;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static ArrayList<VideoItem> getVideos(Context c, int account) {
        ArrayList<VideoItem> result = new ArrayList<>();
        ArrayList<String> valid = new ArrayList<>();
        ArrayList<String> paths = readPaths(c, account);
        for (String path : paths) {
            File f = new File(path);
            long duration = readDuration(path);
            if (f.isFile() && f.length() > 0 && duration > 0) {
                valid.add(path);
                result.add(new VideoItem(path, duration, f.length()));
            }
        }
        if (valid.size() != paths.size()) {
            writePaths(c, account, valid);
        }
        return result;
    }

    public static ArrayList<String> getVideoPaths(Context c, int account) {
        ArrayList<String> result = new ArrayList<>();
        for (VideoItem item : getVideos(c, account)) {
            result.add(item.path);
        }
        return result;
    }

    public static int getVideoCount(Context c, int account) {
        return getVideos(c, account).size();
    }

    public static boolean isEnabled(Context c, int account) {
        return prefs(c).getBoolean(key(KEY_ENABLED, account), false) && getVideoCount(c, account) > 0;
    }

    public static boolean isAnyEnabled(Context c, int account) {
        return isEnabled(c, account);
    }

    public static void setEnabled(Context c, int account, boolean enabled) {
        prefs(c).edit().putBoolean(key(KEY_ENABLED, account), enabled).commit();
    }

    public static int getMode(Context c, int account) {
        return prefs(c).getInt(key(KEY_MODE, account), MODE_ORDER) == MODE_RANDOM ? MODE_RANDOM : MODE_ORDER;
    }

    public static void setMode(Context c, int account, int mode) {
        prefs(c).edit().putInt(key(KEY_MODE, account), mode == MODE_RANDOM ? MODE_RANDOM : MODE_ORDER).apply();
    }

    public static void deleteVideos(Context c, int account, Collection<String> paths) {
        Set<String> set = new HashSet<>(paths == null ? new ArrayList<>() : paths);
        ArrayList<String> keep = new ArrayList<>();
        for (String path : readPaths(c, account)) {
            if (set.contains(path)) {
                try {
                    new File(path).delete();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            } else {
                keep.add(path);
            }
        }
        writePaths(c, account, keep);
        if (keep.isEmpty()) {
            setEnabled(c, account, false);
        }
    }

    public static void clear(Context c, int account) {
        deleteVideos(c, account, readPaths(c, account));
    }

    public static FetchResult importLocalVideos(Context c, int account, List<Uri> sources) {
        ArrayList<String> errors = new ArrayList<>();
        int imported = 0;
        int landscape = 0;
        int index = getVideoCount(c, account);
        if (sources != null) {
            for (Uri source : sources) {
                File file = null;
                try {
                    file = copyLocal(c, source, index++);
                    if (readOrientation(file.getAbsolutePath()) <= 0) {
                        landscape++;
                        file.delete();
                    } else {
                        addPath(c, account, file.getAbsolutePath());
                        imported++;
                    }
                } catch (Throwable e) {
                    if (file != null) {
                        file.delete();
                    }
                    FileLog.e(e);
                    errors.add("有一个视频无法播放");
                }
            }
        }
        if (imported > 0) {
            setEnabled(c, account, true);
        }
        return new FetchResult(imported, landscape, errors);
    }

    private static File copyLocal(Context c, Uri source, int index) throws Exception {
        if (source == null) {
            throw new Exception("未读取视频");
        }
        File dir = new File(c.getFilesDir(), DIRECTORY);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("无法创建视频目录");
        }
        File file = new File(dir, "local_" + System.currentTimeMillis() + "_" + index + ".mp4");
        long total = 0;
        try (InputStream in = c.getContentResolver().openInputStream(source);
             FileOutputStream out = new FileOutputStream(file)) {
            if (in == null) {
                throw new Exception("无法读取视频");
            }
            byte[] buffer = new byte[32768];
            int count;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > 200L * 1024L * 1024L) {
                    throw new Exception("视频超过 200 MB");
                }
                out.write(buffer, 0, count);
            }
            if (total <= 0 || readDuration(file.getAbsolutePath()) <= 0) {
                throw new Exception("不是可播放视频");
            }
            return file;
        } catch (Throwable e) {
            // 调用方在 copyLocal 抛异常前无法取得 file 引用；必须在这里自行清理半成品。
            if (file.exists() && !file.delete()) {
                FileLog.e("Unable to delete invalid dynamic wallpaper video: " + file.getAbsolutePath());
            }
            throw e;
        }
    }

    private static int readOrientation(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            int width = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            int angle = rotation == null ? 0 : Integer.parseInt(rotation);
            if (angle == 90 || angle == 270) {
                int temp = width;
                width = height;
                height = temp;
            }
            return height > width ? 1 : 0;
        } catch (Throwable e) {
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignore) {
            }
        }
    }

    private static long readDuration(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            return Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (Throwable e) {
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignore) {
            }
        }
    }

    private static ArrayList<String> readPaths(Context c, int account) {
        ArrayList<String> result = new ArrayList<>();
        String raw = prefs(c).getString(key(KEY_VIDEOS, account), "");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String path = array.optString(i, "");
                if (path.length() > 0 && !result.contains(path)) {
                    result.add(path);
                }
            }
        } catch (Throwable ignore) {
        }
        return result;
    }

    private static void addPath(Context c, int account, String path) {
        ArrayList<String> paths = readPaths(c, account);
        if (!paths.contains(path)) {
            paths.add(path);
            writePaths(c, account, paths);
        }
    }

    private static void writePaths(Context c, int account, List<String> paths) {
        JSONArray array = new JSONArray();
        for (String path : paths) {
            array.put(path);
        }
        prefs(c).edit().putString(key(KEY_VIDEOS, account), array.toString()).commit();
    }
}
