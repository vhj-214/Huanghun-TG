package org.telegram.messenger.voip;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.view.Surface;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.webrtc.CapturerObserver;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import java.io.File;
import java.util.ArrayList;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.HuanghunCallVideoLibraryHelper;

/**
 * 将通话专区中导入的本地 MP4 视频作为 WebRTC 摄像头帧源。
 *
 * 视频由 MediaPlayer 解码到 WebRTC 提供的 SurfaceTexture 中，再经 SurfaceTextureHelper
 * 以原始宽高比例的纹理帧交给 CapturerObserver。该实现不裁切为正方形，并在每个视频结束后
 * 自动按已选顺序播放下一个视频，最后一个播放完成后重新从第一个开始。
 */
public final class HuanghunVirtualCameraCapturer implements VideoCapturer {

    private static final String TAG = "HuanghunVirtualCamera";
    private static final int FALLBACK_WIDTH = 1280;
    private static final int FALLBACK_HEIGHT = 720;

    private static volatile HuanghunVirtualCameraCapturer activeCapturer;

    private final int account;
    private final Object playerLock = new Object();
    private final HuanghunVirtualCameraAudioSource audioSource = new HuanghunVirtualCameraAudioSource();

    private SurfaceTextureHelper surfaceTextureHelper;
    private CapturerObserver capturerObserver;
    private MediaPlayer mediaPlayer;
    private Surface outputSurface;
    private ArrayList<String> videoPaths = new ArrayList<>();
    private int videoIndex;
    private int requestedWidth = FALLBACK_WIDTH;
    private int requestedHeight = FALLBACK_HEIGHT;
    private int requestedFps = 30;
    private volatile boolean capturing;
    private volatile boolean paused;
    private boolean disposed;
    private boolean listenerStarted;
    private boolean firstFrameDelivered;

    public HuanghunVirtualCameraCapturer(int account) {
        this.account = account;
    }

    public static boolean isActive() {
        HuanghunVirtualCameraCapturer capturer = activeCapturer;
        return capturer != null && capturer.capturing && !capturer.disposed;
    }

    public static boolean isPaused() {
        HuanghunVirtualCameraCapturer capturer = activeCapturer;
        return capturer != null && capturer.paused;
    }

    /** 由 WebRtcAudioRecord 在实时录音线程中读取；无活跃虚拟摄像头时返回 null。 */
    public static HuanghunVirtualCameraAudioSource getActiveAudioSource() {
        HuanghunVirtualCameraCapturer capturer = activeCapturer;
        return capturer != null && capturer.capturing && !capturer.disposed ? capturer.audioSource : null;
    }

    public static void play() {
        HuanghunVirtualCameraCapturer capturer = activeCapturer;
        if (capturer != null) {
            capturer.setPaused(false);
        }
    }

    /** 设置页修改“视频声音”后立即更新正在播放的本地监控音量；上行 PCM 同时读取该配置。 */
    public static void refreshSoundState() {
        HuanghunVirtualCameraCapturer capturer = activeCapturer;
        if (capturer != null) {
            capturer.updateLocalSoundState();
        }
    }

    public static void pause() {
        HuanghunVirtualCameraCapturer capturer = activeCapturer;
        if (capturer != null) {
            capturer.setPaused(true);
        }
    }

    public static void previousVideo() {
        HuanghunVirtualCameraCapturer capturer = activeCapturer;
        if (capturer != null) {
            capturer.selectVideo(-1);
        }
    }

    public static void nextVideo() {
        HuanghunVirtualCameraCapturer capturer = activeCapturer;
        if (capturer != null) {
            capturer.selectVideo(1);
        }
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver capturerObserver) {
        this.surfaceTextureHelper = surfaceTextureHelper;
        this.capturerObserver = capturerObserver;
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
        requestedWidth = ensureEven(width > 0 ? width : FALLBACK_WIDTH);
        requestedHeight = ensureEven(height > 0 ? height : FALLBACK_HEIGHT);
        requestedFps = Math.max(1, framerate);
        if (surfaceTextureHelper == null || capturerObserver == null || disposed) {
            if (capturerObserver != null) {
                capturerObserver.onCapturerStarted(false);
            }
            return;
        }
        surfaceTextureHelper.getHandler().post(() -> startOnCaptureThread());
    }

    private void startOnCaptureThread() {
        if (disposed || capturing) {
            return;
        }
        videoPaths = HuanghunCallVideoLibraryHelper.getVideoPaths(ApplicationLoader.applicationContext, account);
        if (videoPaths.isEmpty()) {
            FileLog.d(TAG + ": no configured virtual-camera video");
            capturerObserver.onCapturerStarted(false);
            return;
        }
        capturing = true;
        paused = false;
        firstFrameDelivered = false;
        activeCapturer = this;
        audioSource.start(videoPaths, videoIndex);
        releaseOutputSurface();
        outputSurface = new Surface(surfaceTextureHelper.getSurfaceTexture());
        surfaceTextureHelper.setTextureSize(requestedWidth, requestedHeight);
        if (!listenerStarted) {
            listenerStarted = true;
            surfaceTextureHelper.startListening(this::onTextureFrame);
        }
        capturerObserver.onCapturerStarted(true);
        openCurrentVideo();
    }

    private void onTextureFrame(VideoFrame frame) {
        CapturerObserver observer = capturerObserver;
        if (capturing && !disposed && observer != null) {
            observer.onFrameCaptured(frame);
            if (!firstFrameDelivered) {
                firstFrameDelivered = true;
                // 与官方相机相同地通知 UI：预览可以显示，虚拟视频控制面板也在此时出现。
                AndroidUtilities.runOnUIThread(() -> {
                    VoIPService service = VoIPService.getSharedInstance();
                    if (service != null) {
                        service.onCameraFirstFrameAvailable();
                    }
                });
            }
        }
    }

    private void openCurrentVideo() {
        if (!capturing || disposed || videoPaths.isEmpty()) {
            return;
        }
        releasePlayer();
        if (videoIndex < 0 || videoIndex >= videoPaths.size()) {
            videoIndex = 0;
        }
        String path = videoPaths.get(videoIndex);
        if (path == null || !new File(path).isFile()) {
            skipUnavailableVideo(1);
            return;
        }
        try {
            MediaPlayer player = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build());
            } else {
                player.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
            }
            player.setDataSource(path);
            player.setSurface(outputSurface);
            // 让发起方也能听到已启用的视频原声；远端声音仍由独立 PCM 队列注入上行，
            // 不依赖手机扬声器回录，因此关闭开关后本地与远端会同时静音。
            float localVolume = NekoConfig.huanghunCallVirtualVideoSound.Bool() ? 1f : 0f;
            player.setVolume(localVolume, localVolume);
            player.setLooping(false);
            player.setOnPreparedListener(preparedPlayer -> {
                if (preparedPlayer != mediaPlayer || !capturing || disposed) {
                    return;
                }
                applyVideoTextureSize(preparedPlayer);
                if (!paused) {
                    try {
                        preparedPlayer.start();
                        audioSource.setPlaybackEnabled(true);
                    } catch (Throwable e) {
                        FileLog.e(e);
                        skipUnavailableVideo(1);
                    }
                }
            });
            player.setOnCompletionListener(completedPlayer -> {
                if (completedPlayer == mediaPlayer && capturing && !disposed) {
                    videoIndex = (videoIndex + 1) % Math.max(1, videoPaths.size());
                    audioSource.selectVideo(videoIndex);
                    openCurrentVideo();
                }
            });
            player.setOnErrorListener((failedPlayer, what, extra) -> {
                FileLog.e(TAG + ": player error what=" + what + " extra=" + extra);
                if (failedPlayer == mediaPlayer && capturing && !disposed) {
                    skipUnavailableVideo(1);
                }
                return true;
            });
            mediaPlayer = player;
            player.prepareAsync();
        } catch (Throwable e) {
            FileLog.e(e);
            skipUnavailableVideo(1);
        }
    }

    private void skipUnavailableVideo(int direction) {
        if (videoPaths.isEmpty()) {
            return;
        }
        if (videoPaths.size() == 1) {
            releasePlayer();
            return;
        }
        videoIndex = normalizeIndex(videoIndex + direction);
        audioSource.selectVideo(videoIndex);
        openCurrentVideo();
    }

    private void applyVideoTextureSize(MediaPlayer player) {
        int videoWidth = player.getVideoWidth();
        int videoHeight = player.getVideoHeight();
        if (videoWidth <= 0 || videoHeight <= 0 || surfaceTextureHelper == null) {
            surfaceTextureHelper.setTextureSize(requestedWidth, requestedHeight);
            return;
        }
        float scale = Math.min(requestedWidth / (float) videoWidth, requestedHeight / (float) videoHeight);
        if (!(scale > 0f)) {
            scale = 1f;
        }
        int targetWidth = ensureEven(Math.round(videoWidth * scale));
        int targetHeight = ensureEven(Math.round(videoHeight * scale));
        surfaceTextureHelper.setTextureSize(targetWidth, targetHeight);
    }

    private void updateLocalSoundState() {
        SurfaceTextureHelper helper = surfaceTextureHelper;
        if (helper == null) {
            return;
        }
        helper.getHandler().post(() -> {
            MediaPlayer player = mediaPlayer;
            if (capturing && !disposed && player != null) {
                try {
                    float volume = NekoConfig.huanghunCallVirtualVideoSound.Bool() ? 1f : 0f;
                    player.setVolume(volume, volume);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private void setPaused(boolean pause) {
        SurfaceTextureHelper helper = surfaceTextureHelper;
        if (helper == null) {
            return;
        }
        helper.getHandler().post(() -> {
            if (!capturing || disposed || paused == pause) {
                return;
            }
            paused = pause;
            MediaPlayer player = mediaPlayer;
            if (player == null) {
                return;
            }
            try {
                if (pause) {
                    if (player.isPlaying()) {
                        player.pause();
                    }
                    audioSource.setPlaybackEnabled(false);
                } else {
                    player.start();
                    audioSource.setPlaybackEnabled(true);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    private void selectVideo(int delta) {
        SurfaceTextureHelper helper = surfaceTextureHelper;
        if (helper == null) {
            return;
        }
        helper.getHandler().post(() -> {
            if (!capturing || disposed || videoPaths.isEmpty()) {
                return;
            }
            videoIndex = normalizeIndex(videoIndex + delta);
            openCurrentVideo();
        });
    }

    @Override
    public void stopCapture() throws InterruptedException {
        SurfaceTextureHelper helper = surfaceTextureHelper;
        if (helper == null) {
            stopOnCaptureThread();
            return;
        }
        if (Thread.currentThread() == helper.getHandler().getLooper().getThread()) {
            stopOnCaptureThread();
        } else {
            // VideoCapturer 的契约要求 stopCapture 返回后不再推送帧；同步切换到纹理线程。
            ThreadUtils.invokeAtFrontUninterruptibly(helper.getHandler(), this::stopOnCaptureThread);
        }
    }

    private void stopOnCaptureThread() {
        if (!capturing) {
            return;
        }
        capturing = false;
        paused = false;
        firstFrameDelivered = false;
        if (activeCapturer == this) {
            activeCapturer = null;
        }
        releasePlayer();
        audioSource.stop();
        if (listenerStarted && surfaceTextureHelper != null) {
            surfaceTextureHelper.stopListening();
            listenerStarted = false;
        }
        CapturerObserver observer = capturerObserver;
        if (observer != null) {
            observer.onCapturerStopped();
        }
    }

    @Override
    public void changeCaptureFormat(int width, int height, int framerate) {
        requestedWidth = ensureEven(width > 0 ? width : FALLBACK_WIDTH);
        requestedHeight = ensureEven(height > 0 ? height : FALLBACK_HEIGHT);
        requestedFps = Math.max(1, framerate);
        SurfaceTextureHelper helper = surfaceTextureHelper;
        if (helper != null) {
            helper.getHandler().post(() -> {
                MediaPlayer player = mediaPlayer;
                if (capturing && player != null) {
                    applyVideoTextureSize(player);
                }
            });
        }
    }

    @Override
    public void dispose() {
        try {
            stopCapture();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        disposed = true;
        releaseOutputSurface();
        videoPaths.clear();
        audioSource.stop();
        capturerObserver = null;
        surfaceTextureHelper = null;
    }

    @Override
    public boolean isScreencast() {
        return false;
    }

    private void releaseOutputSurface() {
        if (outputSurface != null) {
            try {
                outputSurface.release();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            outputSurface = null;
        }
    }

    private void releasePlayer() {
        synchronized (playerLock) {
            if (mediaPlayer == null) {
                return;
            }
            try {
                mediaPlayer.setOnPreparedListener(null);
                mediaPlayer.setOnCompletionListener(null);
                mediaPlayer.setOnErrorListener(null);
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            mediaPlayer = null;
        }
    }

    private int normalizeIndex(int index) {
        int size = videoPaths.size();
        if (size <= 0) {
            return 0;
        }
        int normalized = index % size;
        return normalized < 0 ? normalized + size : normalized;
    }

    private static int ensureEven(int value) {
        return Math.max(2, value & ~1);
    }
}
