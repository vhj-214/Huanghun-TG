package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 黄昏定制版的本地动态聊天壁纸。
 *
 * 视频仅保存和播放在当前设备；不会上传至 Telegram，也不会同步给聊天对方。
 */
public final class DynamicVideoWallpaperHelper {

    private static final String PREFERENCES = "huanghun_dynamic_video_wallpapers";
    private static final String DIRECTORY = "huanghun_dynamic_video_wallpapers";
    private static final long MAX_VIDEO_SIZE_BYTES = 100L * 1024L * 1024L;

    public interface WallpaperChangeListener {
        void onDynamicWallpaperChanged(int account, long dialogId);
    }

    private static final java.util.concurrent.CopyOnWriteArrayList<WallpaperChangeListener> CHANGE_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private DynamicVideoWallpaperHelper() {
    }

    private static String key(int account, long dialogId) {
        return account + "_" + dialogId;
    }

    public static String importVideo(Context context, Uri source) throws IOException {
        if (source == null) {
            throw new IOException("未读取到所选视频。");
        }
        File directory = new File(context.getFilesDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建动态壁纸存储目录。");
        }

        File target = new File(directory, "wallpaper_" + System.currentTimeMillis() + ".mp4");
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
                    throw new IOException("所选视频超过 100 MB，无法设置为动态壁纸。");
                }
                output.write(buffer, 0, count);
            }
            output.flush();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw e;
        }

        if (copied == 0 || !target.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw new IOException("所选视频为空或无法读取。");
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(target.getAbsolutePath());
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration == null || Long.parseLong(duration) <= 0) {
                throw new IOException("所选文件不是可播放的视频。");
            }
        } catch (RuntimeException e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw new IOException("所选文件不是可播放的视频。", e);
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignore) {
            }
        }
        return target.getAbsolutePath();
    }

    public static void saveVideo(Context context, int account, long dialogId, String path) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String oldPath = preferences.getString(key(account, dialogId), null);
        // 使用同步提交确保通知当前聊天页刷新时，新路径已经可被立即读取。
        preferences.edit().putString(key(account, dialogId), path).commit();
        notifyWallpaperChanged(account, dialogId);
        if (oldPath != null && !oldPath.equals(path)) {
            try {
                // 旧播放器会在监听回调中先被释放，再异步删除旧文件，避免替换瞬间仍解码旧视频。
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        //noinspection ResultOfMethodCallIgnored
                        new File(oldPath).delete();
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }, 350L);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    public static void addChangeListener(WallpaperChangeListener listener) {
        if (listener != null && !CHANGE_LISTENERS.contains(listener)) {
            CHANGE_LISTENERS.add(listener);
        }
    }

    public static void removeChangeListener(WallpaperChangeListener listener) {
        if (listener != null) {
            CHANGE_LISTENERS.remove(listener);
        }
    }

    private static void notifyWallpaperChanged(int account, long dialogId) {
        for (WallpaperChangeListener listener : CHANGE_LISTENERS) {
            try {
                listener.onDynamicWallpaperChanged(account, dialogId);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    public static String getVideoPath(Context context, int account, long dialogId) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String selectedKey = key(account, dialogId);
        String path = preferences.getString(selectedKey, null);
        // 聊天设置页保存的全局默认视频使用 dialogId = 0。具体聊天没有单独设置时，
        // 一律回退到该默认视频，因此切换 Telegram 主题不会影响本地动态背景。
        if (path == null && dialogId != 0L) {
            selectedKey = key(account, 0L);
            path = preferences.getString(selectedKey, null);
        }
        if (path == null) {
            return null;
        }
        if (!new File(path).isFile()) {
            preferences.edit().remove(selectedKey).apply();
            return null;
        }
        return path;
    }

    public static void clearVideo(Context context, int account, long dialogId) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String path = preferences.getString(key(account, dialogId), null);
        preferences.edit().remove(key(account, dialogId)).commit();
        notifyWallpaperChanged(account, dialogId);
        if (path != null) {
            try {
                //noinspection ResultOfMethodCallIgnored
                new File(path).delete();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    public static Player attach(ViewGroup parent, Context context, int account, long dialogId) {
        String path = getVideoPath(context, account, dialogId);
        if (path == null || parent == null) {
            return null;
        }
        TextureView textureView = new TextureView(context);
        textureView.setClickable(false);
        textureView.setFocusable(false);
        textureView.setOpaque(false);
        // 仅在播放器成功准备后显示，避免进入聊天时短暂出现黑色首帧。
        textureView.setAlpha(0f);

        // SizeNotifierFrameLayout 会把 Telegram 原始壁纸放在 backgroundView（索引 0）。
        // 旧实现把 TextureView 也插到索引 0，导致官方静态壁纸被推到视频上层而完全遮住视频。
        // 将视频准确放在 backgroundView 之后，随后创建或已有的消息列表仍位于视频之上。
        int insertIndex = 0;
        if (parent instanceof SizeNotifierFrameLayout) {
            View backgroundView = ((SizeNotifierFrameLayout) parent).backgroundView;
            if (backgroundView != null) {
                int backgroundIndex = parent.indexOfChild(backgroundView);
                insertIndex = backgroundIndex < 0 ? 0 : backgroundIndex + 1;
            }
        }
        parent.addView(textureView, Math.min(insertIndex, parent.getChildCount()),
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Player player = new Player(textureView, path);
        player.start();
        return player;
    }

    public static final class Player implements TextureView.SurfaceTextureListener {
        private final TextureView textureView;
        private final String path;
        private MediaPlayer mediaPlayer;
        private Surface surface;
        private boolean released;
        private int videoWidth;
        private int videoHeight;

        private Player(TextureView textureView, String path) {
            this.textureView = textureView;
            this.path = path;
        }

        private void start() {
            textureView.setSurfaceTextureListener(this);
            if (textureView.isAvailable()) {
                prepare(textureView.getSurfaceTexture());
            }
        }

        private void prepare(SurfaceTexture surfaceTexture) {
            if (released || surfaceTexture == null) {
                return;
            }
            releaseMediaPlayer();
            try {
                surface = new Surface(surfaceTexture);
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(path);
                mediaPlayer.setSurface(surface);
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(0f, 0f);
                mediaPlayer.setOnVideoSizeChangedListener((player, width, height) -> {
                    videoWidth = width;
                    videoHeight = height;
                    textureView.post(this::applyFitCenter);
                });
                mediaPlayer.setOnPreparedListener(player -> {
                    if (!released) {
                        videoWidth = player.getVideoWidth();
                        videoHeight = player.getVideoHeight();
                        applyFitCenter();
                        textureView.animate().alpha(.96f).setDuration(220L).start();
                        player.start();
                    }
                });
                mediaPlayer.setOnErrorListener((player, what, extra) -> {
                    FileLog.e("Dynamic video wallpaper playback failed: " + what + "/" + extra);
                    return true;
                });
                mediaPlayer.prepareAsync();
            } catch (Throwable e) {
                FileLog.e(e);
                releaseMediaPlayer();
            }
        }

        public void pause() {
            try {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        public void resume() {
            try {
                if (!released && mediaPlayer != null && !mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        public void release() {
            released = true;
            textureView.animate().cancel();
            textureView.setSurfaceTextureListener(null);
            releaseMediaPlayer();
            if (textureView.getParent() instanceof ViewGroup) {
                ((ViewGroup) textureView.getParent()).removeView(textureView);
            }
        }

        private void releaseMediaPlayer() {
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.reset();
                    mediaPlayer.release();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                mediaPlayer = null;
            }
            if (surface != null) {
                try {
                    surface.release();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                surface = null;
            }
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            prepare(surface);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            applyFitCenter();
        }

        /**
         * 以完整比例显示原视频，不裁切、不放大局部。视频比例与聊天区不一致时保留完整画面，
         * 周围通过底层原主题壁纸自然补足，而不是截掉视频边缘。
         */
        private void applyFitCenter() {
            if (released || videoWidth <= 0 || videoHeight <= 0 || textureView.getWidth() <= 0 || textureView.getHeight() <= 0) {
                return;
            }
            float scale = Math.min(textureView.getWidth() / (float) videoWidth, textureView.getHeight() / (float) videoHeight);
            float scaledWidth = videoWidth * scale;
            float scaledHeight = videoHeight * scale;
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate((textureView.getWidth() - scaledWidth) / 2f, (textureView.getHeight() - scaledHeight) / 2f);
            textureView.setTransform(matrix);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            releaseMediaPlayer();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    }
}
