package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置视频录制模式的圆形预览层。
 *
 * 此视图不会访问摄像头、麦克风或即时相机类；它只播放已经导入应用私有目录的视频。
 * 播放器始终在主线程按保存顺序切换，最后一个视频结束后回到第一个视频。
 */
public final class HuanghunBuiltinVideoPreview extends FrameLayout implements TextureView.SurfaceTextureListener {

    private final ArrayList<String> videoPaths = new ArrayList<>();
    private final FrameLayout circle;
    private final TextureView textureView;
    private final TextView hintView;

    private MediaPlayer mediaPlayer;
    private Surface surface;
    private SurfaceTexture surfaceTexture;
    private int currentIndex;
    private int videoWidth;
    private int videoHeight;
    private int circleSize;
    private int consecutivePlaybackFailures;
    private long playbackSession;
    private boolean released;
    private boolean prepared;
    private boolean hasRenderedFrame;

    public HuanghunBuiltinVideoPreview(Context context, List<String> paths) {
        super(context);
        if (paths != null) {
            for (String path : paths) {
                if (path != null && new File(path).isFile()) {
                    videoPaths.add(path);
                }
            }
        }
        setClickable(false);
        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);

        circle = new FrameLayout(context);
        GradientDrawable circleBackground = new GradientDrawable();
        circleBackground.setColor(0xE5161B23);
        circleBackground.setShape(GradientDrawable.OVAL);
        circle.setBackground(circleBackground);
        circle.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        circle.setClipChildren(true);
        circle.setClipToOutline(true);
        addView(circle, LayoutHelper.createFrame(AndroidUtilities.roundPlayingMessageSize, AndroidUtilities.roundPlayingMessageSize, Gravity.CENTER));

        textureView = new TextureView(context);
        textureView.setOpaque(false);
        textureView.setAlpha(0f);
        // TextureView 自身也使用圆形 outline：部分机型不会可靠继承父容器的裁切。
        textureView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        textureView.setClipToOutline(true);
        textureView.setSurfaceTextureListener(this);
        circle.addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        hintView = new TextView(context);
        hintView.setText("内置视频预览");
        hintView.setTextColor(0xE6FFFFFF);
        hintView.setTextSize(12);
        hintView.setGravity(Gravity.CENTER);
        GradientDrawable hintBackground = new GradientDrawable();
        hintBackground.setColor(0x7A000000);
        hintBackground.setCornerRadius(AndroidUtilities.dp(10));
        hintView.setBackground(hintBackground);
        hintView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4));
        addView(hintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 60));
    }

    /** 在 TextureView 可用后开始播放第一段有效视频。 */
    public boolean start() {
        if (released || videoPaths.isEmpty()) {
            return false;
        }
        if (textureView.isAvailable()) {
            playCurrent(textureView.getSurfaceTexture());
        }
        return true;
    }

    public boolean isPrepared() {
        return prepared;
    }

    public String getCurrentVideoPath() {
        if (currentIndex < 0 || currentIndex >= videoPaths.size()) {
            return null;
        }
        String path = videoPaths.get(currentIndex);
        return new File(path).isFile() ? path : null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateCircleSize(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void updateCircleSize(int availableWidth, int availableHeight) {
        if (availableWidth <= 0 || availableHeight <= 0) {
            return;
        }
        int targetSize = availableHeight - getPaddingBottom() > availableWidth * 1.3f
                ? AndroidUtilities.roundPlayingMessageSize
                : AndroidUtilities.roundMessageSize;
        targetSize = Math.min(targetSize, Math.min(availableWidth, availableHeight));
        if (targetSize <= 0 || targetSize == circleSize) {
            return;
        }
        circleSize = targetSize;
        ViewGroup.LayoutParams params = circle.getLayoutParams();
        params.width = targetSize;
        params.height = targetSize;
        circle.setLayoutParams(params);
    }

    private void playCurrent(SurfaceTexture texture) {
        if (released || texture == null || videoPaths.isEmpty()) {
            return;
        }
        int validIndex = findValidIndex(currentIndex);
        if (validIndex < 0) {
            prepared = false;
            hintView.setText("未找到可用内置视频");
            return;
        }
        currentIndex = validIndex;
        releasePlayer();

        final long session = ++playbackSession;
        try {
            prepared = false;
            videoWidth = 0;
            videoHeight = 0;
            surfaceTexture = texture;
            surface = new Surface(texture);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setLooping(false);
            mediaPlayer.setDataSource(videoPaths.get(currentIndex));
            mediaPlayer.setSurface(surface);
            mediaPlayer.setVolume(0f, 0f);
            mediaPlayer.setOnVideoSizeChangedListener((player, width, height) -> {
                if (!released && session == playbackSession && player == mediaPlayer) {
                    videoWidth = width;
                    videoHeight = height;
                    configureBuffer();
                    applyCenterCrop();
                }
            });
            mediaPlayer.setOnPreparedListener(player -> {
                if (released || session != playbackSession || player != mediaPlayer) {
                    return;
                }
                prepared = true;
                consecutivePlaybackFailures = 0;
                videoWidth = player.getVideoWidth();
                videoHeight = player.getVideoHeight();
                configureBuffer();
                applyCenterCrop();
                if (!hasRenderedFrame) {
                    hasRenderedFrame = true;
                    textureView.animate().alpha(1f).setDuration(160L).start();
                } else {
                    textureView.setAlpha(1f);
                }
                player.start();
            });
            mediaPlayer.setOnCompletionListener(player -> scheduleNextVideo(session, false));
            mediaPlayer.setOnErrorListener((player, what, extra) -> {
                FileLog.e("Builtin video preview playback failed: " + what + "/" + extra);
                scheduleNextVideo(session, true);
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Throwable e) {
            FileLog.e(e);
            scheduleNextVideo(session, true);
        }
    }

    /**
     * MediaPlayer 完成回调必须回到视图主线程后才释放并替换 Surface，避免某些设备只停留在第一段视频。
     */
    private void scheduleNextVideo(long completedSession, boolean failed) {
        textureView.post(() -> {
            if (released || completedSession != playbackSession || videoPaths.isEmpty()) {
                return;
            }
            if (failed) {
                consecutivePlaybackFailures++;
                if (consecutivePlaybackFailures >= videoPaths.size()) {
                    prepared = false;
                    hintView.setText("内置视频无法播放");
                    releasePlayer();
                    return;
                }
            }
            currentIndex = (currentIndex + 1) % videoPaths.size();
            SurfaceTexture nextTexture = textureView.isAvailable() ? textureView.getSurfaceTexture() : surfaceTexture;
            playCurrent(nextTexture);
        });
    }

    private int findValidIndex(int startIndex) {
        if (videoPaths.isEmpty()) {
            return -1;
        }
        int size = videoPaths.size();
        for (int offset = 0; offset < size; offset++) {
            int index = (Math.max(0, startIndex) + offset) % size;
            String path = videoPaths.get(index);
            if (path != null && new File(path).isFile() && new File(path).length() > 0) {
                return index;
            }
        }
        return -1;
    }

    private void configureBuffer() {
        if (surfaceTexture == null || videoWidth <= 0 || videoHeight <= 0) {
            return;
        }
        try {
            // 使用视频自身缓冲尺寸，避免部分设备先 cover 再 Matrix 缩放导致只显示局部。
            surfaceTexture.setDefaultBufferSize(videoWidth, videoHeight);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void applyCenterCrop() {
        if (released || videoWidth <= 0 || videoHeight <= 0 || textureView.getWidth() <= 0 || textureView.getHeight() <= 0) {
            return;
        }
        float scale = Math.max(textureView.getWidth() / (float) videoWidth, textureView.getHeight() / (float) videoHeight);
        float scaledWidth = videoWidth * scale;
        float scaledHeight = videoHeight * scale;
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((textureView.getWidth() - scaledWidth) / 2f, (textureView.getHeight() - scaledHeight) / 2f);
        textureView.setTransform(matrix);
    }

    /** 取消、发送或离开聊天时必须调用；此方法可重复调用。 */
    public void release() {
        if (released) {
            return;
        }
        released = true;
        prepared = false;
        playbackSession++;
        textureView.animate().cancel();
        textureView.setSurfaceTextureListener(null);
        releasePlayer();
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }

    private void releasePlayer() {
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
        playCurrent(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        applyCenterCrop();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        playbackSession++;
        prepared = false;
        hasRenderedFrame = false;
        releasePlayer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }
}
