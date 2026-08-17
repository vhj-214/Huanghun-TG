package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.Matrix;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

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
        return attach(parent, null, context, account, dialogId);
    }

    /**
     * 将动态壁纸插入指定聊天根容器，并可通过 contentAnchor 保证视频位于聊天片段与顶部栏共同下方。
     * 这样视频不会只局限在消息列表，顶部导航、翻译栏与底部输入区也能看到同一段背景。
     */
    public static Player attach(ViewGroup parent, View contentAnchor, Context context, int account, long dialogId) {
        String path = getVideoPath(context, account, dialogId);
        if (path == null || parent == null) {
            return null;
        }
        // 前景严格按完整比例显示视频；后景使用同一视频的柔焦首帧铺满，
        // 因此纵横比不一致时不会露出原始静态壁纸，也不会裁切前景视频。
        FrameLayout videoLayer = new FrameLayout(context);
        videoLayer.setClipChildren(true);
        videoLayer.setClipToPadding(true);
        ImageView softBackdrop = createSoftVideoBackdrop(context, path);
        videoLayer.addView(softBackdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextureView textureView = new TextureView(context);
        textureView.setClickable(false);
        textureView.setFocusable(false);
        textureView.setOpaque(false);
        // 仅在播放器成功准备后显示，避免进入聊天时短暂出现黑色首帧。
        textureView.setAlpha(0f);
        videoLayer.addView(textureView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // SizeNotifierFrameLayout 会把 Telegram 原始壁纸放在 backgroundView（索引 0）。
        // 旧实现把 TextureView 也插到索引 0，导致官方静态壁纸被推到视频上层而完全遮住视频。
        // 将视频准确放在 backgroundView 之后，随后创建或已有的消息列表仍位于视频之上。
        int insertIndex = 0;
        if (contentAnchor != null) {
            int anchorIndex = parent.indexOfChild(contentAnchor);
            // 完整根容器中，视频要在 ChatActivity 内容与 ActionBar 之前，才能成为整页背景。
            insertIndex = anchorIndex < 0 ? 0 : anchorIndex;
        } else if (parent instanceof SizeNotifierFrameLayout) {
            View backgroundView = ((SizeNotifierFrameLayout) parent).backgroundView;
            if (backgroundView != null) {
                int backgroundIndex = parent.indexOfChild(backgroundView);
                insertIndex = backgroundIndex < 0 ? 0 : backgroundIndex + 1;
            }
        }
        parent.addView(videoLayer, Math.min(insertIndex, parent.getChildCount()),
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Player player = new Player(textureView, path, videoLayer);
        // 视频已提升到导航容器后，聊天内容内的官方静态背景会成为遮挡层；
        // 将它暂时透明化，释放播放器时再恢复，保证视频作为完整页面背景可见。
        if (contentAnchor instanceof SizeNotifierFrameLayout) {
            View originalBackground = ((SizeNotifierFrameLayout) contentAnchor).backgroundView;
            if (originalBackground != null) {
                originalBackground.setAlpha(0f);
                player.setSuppressedContentBackground(originalBackground);
            }
        }
        player.start();
        return player;
    }

    /** 作为等比前景视频的连续柔焦底图，避免顶部或底部露出旧主题壁纸。 */
    private static ImageView createSoftVideoBackdrop(Context context, String path) {
        ImageView backdrop = new ImageView(context);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setBackgroundColor(0xFF1E2632);
        backdrop.setAlpha(.62f);
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(path);
                Bitmap frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame != null) {
                    backdrop.setImageBitmap(frame);
                }
            } finally {
                retriever.release();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                backdrop.setRenderEffect(RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return backdrop;
    }

    public static final class Player implements TextureView.SurfaceTextureListener {
        private final TextureView textureView;
        private final String path;
        private final View layerView;
        private MediaPlayer mediaPlayer;
        private Surface surface;
        private SurfaceTexture surfaceTexture;
        private View suppressedContentBackground;
        private final ArrayList<SuppressedBackground> suppressedBackgrounds = new ArrayList<>();
        private final ArrayList<SuppressedAlpha> suppressedAlphas = new ArrayList<>();
        private boolean released;
        private int videoWidth;
        private int videoHeight;

        private Player(TextureView textureView, String path, View layerView) {
            this.textureView = textureView;
            this.path = path;
            this.layerView = layerView;
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
                this.surfaceTexture = surfaceTexture;
                // 清除旧视频遗留的矩阵，避免换视频后继续沿用旧比例。
                textureView.setTransform(new Matrix());
                surface = new Surface(surfaceTexture);
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(path);
                mediaPlayer.setSurface(surface);
                // 明确要求播放器缩放以完整呈现内容，绝不使用裁切填满模式。
                mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(0f, 0f);
                mediaPlayer.setOnVideoSizeChangedListener((player, width, height) -> {
                    videoWidth = width;
                    videoHeight = height;
                    configureVideoBuffer();
                    textureView.post(this::applyFitCenter);
                });
                mediaPlayer.setOnPreparedListener(player -> {
                    if (!released) {
                        videoWidth = player.getVideoWidth();
                        videoHeight = player.getVideoHeight();
                        configureVideoBuffer();
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

        private void setSuppressedContentBackground(View background) {
            suppressedContentBackground = background;
        }

        /**
         * 暂时移除顶部导航、翻译栏或外层容器的实色背景，但不隐藏其中的文字和按钮。
         * 视频播放器释放时会逐一恢复原始 Drawable。
         */
        public void suppressViewBackground(View view) {
            if (view == null) {
                return;
            }
            for (SuppressedBackground state : suppressedBackgrounds) {
                if (state.view == view) {
                    return;
                }
            }
            suppressedBackgrounds.add(new SuppressedBackground(view, view.getBackground()));
            view.setBackground(null);
        }

        /** 暂时隐藏原始静态壁纸视图，释放播放器时精确恢复原透明度。 */
        public void suppressViewAlpha(View view) {
            if (view == null) {
                return;
            }
            for (SuppressedAlpha state : suppressedAlphas) {
                if (state.view == view) {
                    return;
                }
            }
            suppressedAlphas.add(new SuppressedAlpha(view, view.getAlpha()));
            view.setAlpha(0f);
        }

        public void release() {
            released = true;
            textureView.animate().cancel();
            textureView.setSurfaceTextureListener(null);
            releaseMediaPlayer();
            if (suppressedContentBackground != null) {
                suppressedContentBackground.setAlpha(1f);
                suppressedContentBackground = null;
            }
            for (SuppressedBackground state : suppressedBackgrounds) {
                state.view.setBackground(state.background);
            }
            suppressedBackgrounds.clear();
            for (SuppressedAlpha state : suppressedAlphas) {
                state.view.setAlpha(state.alpha);
            }
            suppressedAlphas.clear();
            if (layerView.getParent() instanceof ViewGroup) {
                ((ViewGroup) layerView.getParent()).removeView(layerView);
            }
        }

        /**
         * 让 SurfaceTexture 使用视频自身的缓冲尺寸。若保留 MATCH_PARENT 的默认缓冲区，
         * 部分设备会在 Surface 层先执行 cover 缩放，随后再由 Matrix 缩放一次，最终只剩视频局部。
         */
        private static final class SuppressedBackground {
            final View view;
            final Drawable background;

            SuppressedBackground(View view, Drawable background) {
                this.view = view;
                this.background = background;
            }
        }

        private static final class SuppressedAlpha {
            final View view;
            final float alpha;

            SuppressedAlpha(View view, float alpha) {
                this.view = view;
                this.alpha = alpha;
            }
        }

        private void configureVideoBuffer() {
            if (surfaceTexture == null || videoWidth <= 0 || videoHeight <= 0) {
                return;
            }
            try {
                surfaceTexture.setDefaultBufferSize(videoWidth, videoHeight);
            } catch (Throwable e) {
                FileLog.e(e);
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
            surfaceTexture = null;
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
            // 此矩阵严格采用较小比例：视频任一边都不会超过聊天背景可见区，四周留给原壁纸补足。
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
