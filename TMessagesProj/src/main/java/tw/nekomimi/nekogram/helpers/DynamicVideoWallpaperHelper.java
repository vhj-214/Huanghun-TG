package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

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
    private static final Object PLAYBACK_STATE_LOCK = new Object();
    private static final java.util.LinkedHashMap<String, PlaybackState> PLAYBACK_STATES = new java.util.LinkedHashMap<String, PlaybackState>(8, .75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, PlaybackState> eldest) {
            return size() > 24;
        }
    };

    private static final class PlaybackState {
        final String path;
        final int playlistIndex;
        final int positionMs;

        PlaybackState(String path, int playlistIndex, int positionMs) {
            this.path = path;
            this.playlistIndex = playlistIndex;
            this.positionMs = Math.max(0, positionMs);
        }
    }

    private DynamicVideoWallpaperHelper() {
    }

    private static String key(int account, long dialogId) {
        return account + "_" + dialogId;
    }

    private static String singlePlaybackKey(int account, String path) {
        return "single:" + account + ":" + path;
    }

    private static String playlistPlaybackKey(int account, ArrayList<String> paths, int mode) {
        StringBuilder builder = new StringBuilder("playlist:").append(account).append(':').append(mode);
        for (String path : paths) {
            builder.append('|').append(path);
        }
        return builder.toString();
    }

    private static PlaybackState readPlaybackState(String playbackKey) {
        synchronized (PLAYBACK_STATE_LOCK) {
            return PLAYBACK_STATES.get(playbackKey);
        }
    }

    private static void writePlaybackState(String playbackKey, String path, int playlistIndex, int positionMs) {
        if (playbackKey == null || path == null) {
            return;
        }
        synchronized (PLAYBACK_STATE_LOCK) {
            PLAYBACK_STATES.put(playbackKey, new PlaybackState(path, playlistIndex, positionMs));
        }
    }

    /** 静态壁纸覆盖动态视频时的会话级开关；重新选择视频会自动清除。 */
    private static String disabledKey(int account, long dialogId) {
        return "disabled_" + key(account, dialogId);
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
        // 两种动态壁纸模式互斥：重新设置单视频时关闭多轮模式。
        MultiDynamicVideoWallpaperHelper.setEnabled(context, account, false);
        // 使用同步提交确保通知当前聊天页刷新时，新路径已经可被立即读取。
        // 用户重新选择视频时，明确重新启用该会话的动态壁纸。
        preferences.edit()
                .putString(key(account, dialogId), path)
                .remove(disabledKey(account, dialogId))
                .commit();
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

    public static void notifyWallpaperChanged(int account, long dialogId) {
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
        // 本会话改用静态壁纸后，不能再回退显示全局默认动态视频。
        if (preferences.getBoolean(disabledKey(account, dialogId), false)) {
            return null;
        }
        if (MultiDynamicVideoWallpaperHelper.isAnyEnabled(context, account)) {
            return null;
        }
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

    /**
     * 当前会话选择静态壁纸时停用视频层。保留视频文件，便于用户随后重新选择动态壁纸。
     */
    public static void disableVideoForStaticWallpaper(Context context, int account, long dialogId) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        preferences.edit().putBoolean(disabledKey(account, dialogId), true).commit();
        notifyWallpaperChanged(account, dialogId);
    }

    public static void clearVideo(Context context, int account, long dialogId) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String path = preferences.getString(key(account, dialogId), null);
        preferences.edit()
                .remove(key(account, dialogId))
                .remove(disabledKey(account, dialogId))
                .commit();
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

    /** 多轮循环模式的播放器入口；启用后单视频模式不会被读取。 */
    public static Player attachMulti(ViewGroup parent, View contentAnchor, Context context, int account, long dialogId) {
        ArrayList<String> paths = MultiDynamicVideoWallpaperHelper.getVideoPaths(context, account);
        if (!MultiDynamicVideoWallpaperHelper.isAnyEnabled(context, account) || paths.isEmpty() || parent == null) return null;
        FrameLayout layer = new FrameLayout(context);
        layer.setClipChildren(true);
        layer.setClipToPadding(true);
        layer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        TextureView textureView = new TextureView(context);
        textureView.setClickable(false);
        textureView.setFocusable(false);
        textureView.setOpaque(false);
        textureView.setAlpha(0f);
        layer.addView(textureView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int insertIndex = 0;
        if (contentAnchor != null) {
            int anchorIndex = parent.indexOfChild(contentAnchor);
            insertIndex = anchorIndex < 0 ? 0 : anchorIndex;
        } else if (parent instanceof SizeNotifierFrameLayout) {
            View backgroundView = ((SizeNotifierFrameLayout) parent).backgroundView;
            int backgroundIndex = backgroundView == null ? -1 : parent.indexOfChild(backgroundView);
            insertIndex = backgroundIndex < 0 ? 0 : backgroundIndex + 1;
        }
        parent.addView(layer, Math.min(insertIndex, parent.getChildCount()), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int mode = MultiDynamicVideoWallpaperHelper.getMode(context, account);
        Player player = new Player(textureView, paths, layer, mode, playlistPlaybackKey(account, paths, mode));
        if (contentAnchor instanceof SizeNotifierFrameLayout) {
            View background = ((SizeNotifierFrameLayout) contentAnchor).backgroundView;
            if (background != null) { background.setAlpha(0f); player.setSuppressedContentBackground(background); }
        }
        player.start();
        return player;
    }

    /**
     * 将动态壁纸插入指定聊天根容器，并可通过 contentAnchor 保证视频位于聊天片段与顶部栏共同下方。
     * 这样视频不会只局限在消息列表，顶部导航、翻译栏与底部输入区也能看到同一段背景。
     */
    public static Player attach(ViewGroup parent, View contentAnchor, Context context, int account, long dialogId) {
        if (MultiDynamicVideoWallpaperHelper.isAnyEnabled(context, account)) {
            return attachMulti(parent, contentAnchor, context, account, dialogId);
        }
        String path = getVideoPath(context, account, dialogId);
        if (path == null || parent == null) {
            return null;
        }
        // 资料页空媒体区域必须只显示一个视频画面。不得再使用同一视频首帧做柔焦后景，
        // 否则完整比例的前景 TextureView 会与后景叠加，视觉上形成上下两段重复视频。
        FrameLayout videoLayer = new FrameLayout(context);
        videoLayer.setClipChildren(true);
        videoLayer.setClipToPadding(true);
        // 视频比例与页面不一致时由背景填满模式裁切多余区域；图层保持透明，不能露出固定深色底。
        videoLayer.setBackgroundColor(android.graphics.Color.TRANSPARENT);

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
        Player player = new Player(textureView, path, videoLayer, singlePlaybackKey(account, path));
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


    public static final class Player implements TextureView.SurfaceTextureListener {
        private final TextureView textureView;
        private String path;
        private final ArrayList<String> playlist;
        private final View layerView;
        private final boolean playlistMode;
        private final int playlistModeValue;
        private final String playbackKey;
        private int playlistIndex;
        private int resumePositionMs;
        private MediaPlayer mediaPlayer;
        private Surface surface;
        private SurfaceTexture surfaceTexture;
        private View suppressedContentBackground;
        private final ArrayList<SuppressedBackground> suppressedBackgrounds = new ArrayList<>();
        private final ArrayList<SuppressedAlpha> suppressedAlphas = new ArrayList<>();
        private boolean released;
        private final View.OnLayoutChangeListener videoLayoutListener = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> applyFitCenter();
        private int videoWidth;
        private int videoHeight;

        private Player(TextureView textureView, String path, View layerView, String playbackKey) {
            this(textureView, null, layerView, MultiDynamicVideoWallpaperHelper.MODE_ORDER, playbackKey);
            this.path = path;
        }

        private Player(TextureView textureView, ArrayList<String> playlist, View layerView, int mode, String playbackKey) {
            this.textureView = textureView;
            this.playlist = playlist;
            this.path = playlist == null || playlist.isEmpty() ? null : playlist.get(0);
            this.layerView = layerView;
            this.playlistMode = playlist != null && !playlist.isEmpty();
            this.playlistModeValue = mode;
            this.playbackKey = playbackKey;
            PlaybackState state = readPlaybackState(playbackKey);
            if (state != null) {
                resumePositionMs = state.positionMs;
                if (this.playlistMode) {
                    int restoredIndex = state.playlistIndex;
                    if (state.path != null) {
                        int pathIndex = playlist.indexOf(state.path);
                        if (pathIndex >= 0) {
                            restoredIndex = pathIndex;
                        }
                    }
                    if (restoredIndex >= 0 && restoredIndex < playlist.size()) {
                        playlistIndex = restoredIndex;
                        path = playlist.get(playlistIndex);
                    }
                }
            }
        }

        private void start() {
            textureView.addOnLayoutChangeListener(videoLayoutListener);
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
            PlaybackState state = readPlaybackState(playbackKey);
            if (state != null) {
                resumePositionMs = state.positionMs;
                if (playlistMode && playlist != null && !playlist.isEmpty()) {
                    int restoredIndex = state.playlistIndex;
                    if (state.path != null) {
                        int pathIndex = playlist.indexOf(state.path);
                        if (pathIndex >= 0) {
                            restoredIndex = pathIndex;
                        }
                    }
                    if (restoredIndex >= 0 && restoredIndex < playlist.size()) {
                        playlistIndex = restoredIndex;
                        path = playlist.get(playlistIndex);
                    }
                }
            }
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
                mediaPlayer.setLooping(!playlistMode);
                mediaPlayer.setVolume(0f, 0f);
                if (playlistMode) {
                    mediaPlayer.setOnCompletionListener(player -> playNextVideo());
                }
                mediaPlayer.setOnVideoSizeChangedListener((player, width, height) -> {
                    videoWidth = width;
                    videoHeight = height;
                    configureVideoBuffer();
                    textureView.post(this::applyFitCenter);
                });
                mediaPlayer.setOnPreparedListener(player -> {
                    // prepareAsync 可能在页面释放后才回调；旧播放器不得重新显示或启动。
                    if (!released && player == mediaPlayer && surface != null && surface.isValid()) {
                        videoWidth = player.getVideoWidth();
                        videoHeight = player.getVideoHeight();
                        configureVideoBuffer();
                        applyFitCenter();
                        textureView.animate().alpha(.96f).setDuration(220L).start();
                        try {
                            int position = resumePositionMs;
                            if (position > 0 && player.getDuration() > position + 300) {
                                player.seekTo(position);
                            }
                            resumePositionMs = 0;
                            player.start();
                        } catch (Throwable e) {
                            FileLog.e(e);
                            release();
                        }
                    }
                });
                mediaPlayer.setOnErrorListener((player, what, extra) -> {
                    FileLog.e("Dynamic video wallpaper playback failed: " + what + "/" + extra);
                    // 某些机型遇到损坏视频或不支持的编解码器时，MediaPlayer 会进入不可恢复状态。
                    // 立即在主线程回收图层并恢复原始背景，避免残留 Surface 持续占用解码器，
                    // 也避免页面在下一次进入时因遗留播放器而闪退。
                    textureView.post(this::release);
                    return true;
                });
                mediaPlayer.prepareAsync();
            } catch (Throwable e) {
                FileLog.e(e);
                // 不能只释放 MediaPlayer：此时透明 TextureView 仍会遮挡原壁纸，必须完整回滚图层。
                release();
            }
        }

        /**
         * 在首帧准备完成前保留页面自己的稳定承接色，避免视频图层默认深色在资料页入场时短暂闪现。
         * 只改变图层后景，不影响 TextureView、缩放矩阵或播放器生命周期。
         */
        public void setFallbackBackgroundColor(int color) {
            if (!released) {
                layerView.setBackgroundColor(color);
            }
        }

        public void pause() {
            try {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
                savePlaybackPosition();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        public void resume() {
            try {
                if (released) {
                    return;
                }
                if (mediaPlayer == null && textureView.isAvailable()) {
                    prepare(textureView.getSurfaceTexture());
                } else if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
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
            savePlaybackPosition();
            released = true;
            textureView.animate().cancel();
            textureView.removeOnLayoutChangeListener(videoLayoutListener);
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

        private void playNextVideo() {
            if (released || !playlistMode || playlist == null || playlist.isEmpty()) return;
            if (playlistModeValue == MultiDynamicVideoWallpaperHelper.MODE_RANDOM && playlist.size() > 1) {
                int next;
                do { next = (int) (Math.random() * playlist.size()); } while (next == playlistIndex);
                playlistIndex = next;
            } else {
                playlistIndex = (playlistIndex + 1) % playlist.size();
            }
            String nextPath = playlist.get(playlistIndex);
            if (nextPath == null || !new File(nextPath).isFile()) {
                FileLog.e("Dynamic video wallpaper playlist contains an unavailable file");
                textureView.post(this::release);
                return;
            }
            try {
                if (mediaPlayer != null) {
                    path = nextPath;
                    resumePositionMs = 0;
                    writePlaybackState(playbackKey, path, playlistIndex, 0);
                    mediaPlayer.reset();
                    mediaPlayer.setDataSource(nextPath);
                    mediaPlayer.setSurface(surface);
                    mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                    mediaPlayer.setLooping(false);
                    mediaPlayer.setOnCompletionListener(player -> playNextVideo());
                    mediaPlayer.prepareAsync();
                }
            } catch (Throwable e) { FileLog.e(e); }
        }

        private void savePlaybackPosition() {
            if (mediaPlayer == null || released || path == null) {
                return;
            }
            try {
                int position = mediaPlayer.getCurrentPosition();
                int duration = mediaPlayer.getDuration();
                if (duration > 0 && position >= duration - 250) {
                    position = 0;
                }
                writePlaybackState(playbackKey, path, playlistIndex, position);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        private void releaseMediaPlayer() {
            savePlaybackPosition();
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
         * 将视频作为页面背景填满可见区域。每次顶部导航、底部输入区或设备窗口尺寸变化后都会重新计算；
         * 比例多出的部分由容器裁切，避免出现深色边缘、黑条或第二层视频背景。
         */
        private void applyFitCenter() {
            if (released || videoWidth <= 0 || videoHeight <= 0 || textureView.getWidth() <= 0 || textureView.getHeight() <= 0) {
                return;
            }
            // 背景采用较大比例覆盖整个可见区；父容器负责裁掉超出的边缘，绝不留下空白承接区。
            float scale = Math.max(textureView.getWidth() / (float) videoWidth, textureView.getHeight() / (float) videoHeight);
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
