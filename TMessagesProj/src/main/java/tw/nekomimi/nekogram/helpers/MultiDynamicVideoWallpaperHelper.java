package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 独立于单个动态壁纸的多视频循环库。所有视频仅保存于本机。 */
public final class MultiDynamicVideoWallpaperHelper {
    private static final String PREFS = "huanghun_multi_dynamic_wallpapers";
    private static final String DIRECTORY = "huanghun_multi_dynamic_wallpapers";
    private static final String KEY_VIDEOS = "videos_";
    private static final String KEY_MODE = "mode_";
    private static final String KEY_ENABLED = "enabled_";
    private static final String KEY_APIS = "apis";
    public static final int MODE_ORDER = 0;
    public static final int MODE_RANDOM = 1;
    public static final String DEFAULT_API = "https://api.yujn.cn/api/zzxjj.php";

    public static final class VideoItem {
        public final String path;
        public final long durationMs;
        public final long size;
        public VideoItem(String path, long durationMs, long size) { this.path = path; this.durationMs = durationMs; this.size = size; }
    }
    public static final class FetchResult {
        public final int imported;
        public final int skippedLandscape;
        public final ArrayList<String> errors;
        public FetchResult(int imported, int skippedLandscape, ArrayList<String> errors) { this.imported = imported; this.skippedLandscape = skippedLandscape; this.errors = errors; }
    }
    private MultiDynamicVideoWallpaperHelper() {}
    private static String key(String prefix, int account) { return prefix + account; }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static ArrayList<VideoItem> getVideos(Context c, int account) {
        ArrayList<VideoItem> result = new ArrayList<>();
        ArrayList<String> valid = new ArrayList<>();
        for (String path : readPaths(c, account)) {
            File f = new File(path);
            long duration = readDuration(path);
            if (f.isFile() && f.length() > 0 && duration > 0) { valid.add(path); result.add(new VideoItem(path, duration, f.length())); }
        }
        if (valid.size() != readPaths(c, account).size()) writePaths(c, account, valid);
        return result;
    }
    public static ArrayList<String> getVideoPaths(Context c, int account) {
        ArrayList<String> result = new ArrayList<>(); for (VideoItem i : getVideos(c, account)) result.add(i.path); return result;
    }
    public static int getVideoCount(Context c, int account) { return getVideos(c, account).size(); }
    public static boolean isEnabled(Context c, int account) { return prefs(c).getBoolean(key(KEY_ENABLED, account), false) && getVideoCount(c, account) > 0; }
    public static void setEnabled(Context c, int account, boolean enabled) { prefs(c).edit().putBoolean(key(KEY_ENABLED, account), enabled).commit(); }
    public static int getMode(Context c, int account) { return prefs(c).getInt(key(KEY_MODE, account), MODE_ORDER) == MODE_RANDOM ? MODE_RANDOM : MODE_ORDER; }
    public static void setMode(Context c, int account, int mode) { prefs(c).edit().putInt(key(KEY_MODE, account), mode == MODE_RANDOM ? MODE_RANDOM : MODE_ORDER).apply(); }
    public static String getApiText(Context c) { return prefs(c).getString(KEY_APIS, DEFAULT_API); }
    public static void setApiText(Context c, String text) { prefs(c).edit().putString(KEY_APIS, text == null ? DEFAULT_API : text).apply(); }
    public static void deleteVideos(Context c, int account, Collection<String> paths) {
        Set<String> set = new HashSet<>(paths == null ? new ArrayList<>() : paths); ArrayList<String> keep = new ArrayList<>();
        for (String p : readPaths(c, account)) { if (set.contains(p)) { try { new File(p).delete(); } catch (Throwable e) { FileLog.e(e); } } else keep.add(p); }
        writePaths(c, account, keep); if (keep.isEmpty()) setEnabled(c, account, false);
    }
    public static void clear(Context c, int account) { deleteVideos(c, account, readPaths(c, account)); }
    public static FetchResult importLocalVideos(Context c, int account, List<Uri> sources) {
        ArrayList<String> errors = new ArrayList<>(); int imported = 0; int landscape = 0; int index = getVideoCount(c, account);
        if (sources != null) for (Uri source : sources) {
            File file = null;
            try { file = copyLocal(c, source, index++); if (readOrientation(file.getAbsolutePath()) <= 0) { landscape++; file.delete(); } else { addPath(c, account, file.getAbsolutePath()); imported++; } }
            catch (Throwable e) { if (file != null) file.delete(); FileLog.e(e); errors.add("有一个视频无法播放"); }
        }
        if (imported > 0) setEnabled(c, account, true); return new FetchResult(imported, landscape, errors);
    }
    public static FetchResult fetchFromApis(Context c, int account, String apiText) {
        ArrayList<String> errors = new ArrayList<>(); int imported = 0; int landscape = 0;
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        String source = apiText == null ? "" : apiText;
        for (String line : source.split("[\\r\\n,;]+")) { String api = line.trim(); if (api.length() == 0) continue; try { collectUrls(readText(api), urls); } catch (Throwable e) { FileLog.e(e); errors.add(api + "：获取失败"); } }
        int index = getVideoCount(c, account);
        for (String url : urls) { File file = null; try { file = download(c, url, index++); int orientation = readOrientation(file.getAbsolutePath()); if (orientation <= 0) { landscape++; file.delete(); continue; } addPath(c, account, file.getAbsolutePath()); imported++; } catch (Throwable e) { if (file != null) file.delete(); FileLog.e(e); errors.add("视频无法播放：" + url); } }
        if (imported > 0) setEnabled(c, account, true); return new FetchResult(imported, landscape, errors);
    }
    private static String readText(String address) throws Exception { HttpURLConnection h = (HttpURLConnection) new URL(address).openConnection(); h.setConnectTimeout(15000); h.setReadTimeout(30000); h.setRequestProperty("User-Agent", "Mozilla/5.0"); try (InputStream in = h.getInputStream()) { java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); return out.toString("UTF-8"); } finally { h.disconnect(); } }
    private static void collectUrls(String body, Set<String> urls) { String trimmed = body.trim(); try { Object object = new org.json.JSONTokener(trimmed).nextValue(); collectJson(object, urls); } catch (Throwable ignore) { for (String p : trimmed.split("[\\s\"']+")) if (isVideoUrl(p)) urls.add(p); } if (isVideoUrl(trimmed)) urls.add(trimmed); }
    private static void collectJson(Object value, Set<String> urls) { if (value instanceof String) { String s=(String)value; if (isVideoUrl(s)) urls.add(s); } else if (value instanceof JSONObject) { JSONObject o=(JSONObject)value; JSONArray names=o.names(); if(names!=null) for(int i=0;i<names.length();i++) collectJson(o.opt(names.optString(i)), urls); } else if (value instanceof JSONArray) { JSONArray a=(JSONArray)value; for(int i=0;i<a.length();i++) collectJson(a.opt(i), urls); } }
    private static boolean isVideoUrl(String s) { String x=s.toLowerCase(); return (x.startsWith("http://") || x.startsWith("https://")) && (x.contains(".mp4") || x.contains(".m3u8") || x.contains("video") || x.contains("play")); }
    private static File copyLocal(Context c, Uri source, int index) throws Exception {
        if (source == null) throw new Exception("未读取视频"); File dir = new File(c.getFilesDir(), DIRECTORY); if (!dir.exists() && !dir.mkdirs()) throw new Exception("无法创建视频目录");
        File f = new File(dir, "local_" + System.currentTimeMillis() + "_" + index + ".mp4"); long total = 0;
        try (InputStream in = c.getContentResolver().openInputStream(source); FileOutputStream out = new FileOutputStream(f)) { if (in == null) throw new Exception("无法读取视频"); byte[] b = new byte[32768]; int n; while ((n = in.read(b)) != -1) { total += n; if (total > 200L * 1024L * 1024L) throw new Exception("视频超过 200 MB"); out.write(b, 0, n); } }
        if (total <= 0 || readDuration(f.getAbsolutePath()) <= 0) throw new Exception("不是可播放视频"); return f;
    }
    private static File download(Context c, String address, int index) throws Exception { File dir=new File(c.getFilesDir(), DIRECTORY); if(!dir.exists()&&!dir.mkdirs()) throw new Exception("无法创建视频目录"); HttpURLConnection h=(HttpURLConnection)new URL(address).openConnection(); h.setConnectTimeout(15000); h.setReadTimeout(60000); h.setRequestProperty("User-Agent","Mozilla/5.0"); File f=new File(dir,"video_"+System.currentTimeMillis()+"_"+index+".mp4"); long total=0; try(InputStream in=h.getInputStream(); FileOutputStream out=new FileOutputStream(f)){ byte[] b=new byte[32768]; int n; while((n=in.read(b))!=-1){ total+=n; if(total>200L*1024L*1024L) throw new Exception("视频超过 200 MB"); out.write(b,0,n); } } finally { h.disconnect(); } if(total<=0 || readDuration(f.getAbsolutePath())<=0) throw new Exception("不是可播放视频"); return f; }
    private static int readOrientation(String path) { MediaMetadataRetriever r=new MediaMetadataRetriever(); try { r.setDataSource(path); int w=Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)); int h=Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)); String rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION); int angle = rotation == null ? 0 : Integer.parseInt(rotation); if (angle == 90 || angle == 270) { int t = w; w = h; h = t; } return h > w ? 1 : 0; } catch(Throwable e) { return 0; } finally { try { r.release(); } catch(Throwable ignore) {} } }
    private static long readDuration(String path) { MediaMetadataRetriever r=new MediaMetadataRetriever(); try { r.setDataSource(path); return Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); } catch(Throwable e) { return 0; } finally { try { r.release(); } catch(Throwable ignore) {} } }
    private static ArrayList<String> readPaths(Context c,int account) { ArrayList<String> r=new ArrayList<>(); String raw=prefs(c).getString(key(KEY_VIDEOS,account),""); try { JSONArray a=new JSONArray(raw); for(int i=0;i<a.length();i++){String p=a.optString(i,"");if(p.length()>0&&!r.contains(p))r.add(p);}}catch(Throwable ignore){} return r; }
    private static void addPath(Context c,int account,String path){ArrayList<String> r=readPaths(c,account);if(!r.contains(path)){r.add(path);writePaths(c,account,r);}}
    private static void writePaths(Context c,int account,List<String> paths){JSONArray a=new JSONArray();for(String p:paths)a.put(p);prefs(c).edit().putString(key(KEY_VIDEOS,account),a.toString()).commit();}
}
