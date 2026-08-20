package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
    private final LinearLayout framingControls;

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
    // 用户在圆形窗口中调整的附加取景变换；缩小不会低于完整覆盖圆窗所需的最小比例。
    private float framingScale = 1f;
    private float framingOffsetX;
    private float framingOffsetY;
    private String recordingPath;
    private long recordingStartedAt;
    private int recordingStartPosition;
    private int recordingSourceDuration;
    private int recordingSourceWidth;
    private int recordingSourceHeight;
    private boolean recordingRequested;
    // 部分设备的 MediaPlayer 不会稳定派发 onCompletion；使用同一会话号的时长后备任务保证列表继续推进。
    private long fallbackCompletionSession = -1;
    private long pendingTransitionSession = -1;
    private final Runnable completionFallbackRunnable = this::onCompletionFallback;

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

        framingControls = createFramingControls(context);
        addView(framingControls, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 14, 66));

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

    private LinearLayout createFramingControls(Context context) {
        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        controls.setBackground(createControlBackground(0x9A101820, AndroidUtilities.dp(18)));

        LinearLayout zoomRow = createControlRow(context);
        TextView zoomIn = createControlButton(context, "＋");
        zoomIn.setContentDescription("放大视频");
        zoomIn.setOnClickListener(v -> changeFramingScale(0.15f));
        TextView zoomOut = createControlButton(context, "－");
        zoomOut.setContentDescription("缩小视频");
        zoomOut.setOnClickListener(v -> changeFramingScale(-0.15f));
        zoomRow.addView(zoomIn, LayoutHelper.createLinear(40, 36));
        zoomRow.addView(zoomOut, LayoutHelper.createLinear(40, 36));
        controls.addView(zoomRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38, Gravity.CENTER));

        LinearLayout upRow = createControlRow(context);
        TextView up = createControlButton(context, "▲");
        up.setContentDescription("向上移动视频");
        up.setOnClickListener(v -> moveFraming(0f, -AndroidUtilities.dp(18)));
        upRow.addView(up, LayoutHelper.createLinear(40, 36));
        controls.addView(upRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38, Gravity.CENTER));

        LinearLayout middleRow = createControlRow(context);
        TextView left = createControlButton(context, "◀");
        left.setContentDescription("向左移动视频");
        left.setOnClickListener(v -> moveFraming(-AndroidUtilities.dp(18), 0f));
        TextView right = createControlButton(context, "▶");
        right.setContentDescription("向右移动视频");
        right.setOnClickListener(v -> moveFraming(AndroidUtilities.dp(18), 0f));
        middleRow.addView(left, LayoutHelper.createLinear(40, 36));
        middleRow.addView(right, LayoutHelper.createLinear(40, 36));
        controls.addView(middleRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38, Gravity.CENTER));

        LinearLayout downRow = createControlRow(context);
        TextView down = createControlButton(context, "▼");
        down.setContentDescription("向下移动视频");
        down.setOnClickListener(v -> moveFraming(0f, AndroidUtilities.dp(18)));
        downRow.addView(down, LayoutHelper.createLinear(40, 36));
        controls.addView(downRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38, Gravity.CENTER));
        return controls;
    }

    private LinearLayout createControlRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private TextView createControlButton(Context context, String label) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(18);
        button.setTextColor(0xFFFFFFFF);
        button.setGravity(Gravity.CENTER);
        button.setBackground(createControlBackground(0x6EFFFFFF, AndroidUtilities.dp(12)));
        return button;
    }

    private GradientDrawable createControlBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
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

    /** 开始记录本次预置视频录制的源片段与播放位置。 */
    public boolean beginRecording() {
        if (released) {
            return false;
        }
        recordingRequested = true;
        captureRecordingStartIfReady();
        return true;
    }

    private void captureRecordingStartIfReady() {
        if (!recordingRequested || recordingPath != null || !prepared || mediaPlayer == null) {
            return;
        }
        String path = getCurrentVideoPath();
        if (path == null) {
            return;
        }
        recordingPath = path;
        recordingStartedAt = SystemClock.elapsedRealtime();
        recordingStartPosition = Math.max(0, mediaPlayer.getCurrentPosition());
        recordingSourceDuration = Math.max(recordingStartPosition + 1, mediaPlayer.getDuration());
        recordingSourceWidth = videoWidth;
        recordingSourceHeight = videoHeight;
    }

    /**
     * 结束录制并返回固定于本次按住开始时的源片段快照。
     * 该快照不会引用预览结束后可能已经切换到的下一段视频。
     */
    public RecordingSnapshot finishRecording() {
        if (recordingPath == null || recordingStartedAt <= 0) {
            return null;
        }
        long elapsed = Math.max(1L, SystemClock.elapsedRealtime() - recordingStartedAt);
        long start = recordingStartPosition;
        long end = Math.min(recordingSourceDuration, start + elapsed);
        RecordingSnapshot snapshot = end > start ? new RecordingSnapshot(
                recordingPath,
                start,
                end,
                recordingSourceDuration,
                recordingSourceWidth,
                recordingSourceHeight,
                framingScale,
                framingOffsetX,
                framingOffsetY,
                Math.max(1, textureView.getWidth()),
                Math.max(1, textureView.getHeight())
        ) : null;
        clearRecordingSnapshot();
        return snapshot;
    }

    private void clearRecordingSnapshot() {
        recordingRequested = false;
        recordingPath = null;
        recordingStartedAt = 0;
        recordingStartPosition = 0;
        recordingSourceDuration = 0;
        recordingSourceWidth = 0;
        recordingSourceHeight = 0;
    }

    public static final class RecordingSnapshot {
        public final String path;
        public final long startTime;
        public final long endTime;
        public final long originalDuration;
        public final int originalWidth;
        public final int originalHeight;
        public final float framingScale;
        public final float framingOffsetX;
        public final float framingOffsetY;
        public final int viewportWidth;
        public final int viewportHeight;

        private RecordingSnapshot(String path, long startTime, long endTime, long originalDuration, int originalWidth, int originalHeight, float framingScale, float framingOffsetX, float framingOffsetY, int viewportWidth, int viewportHeight) {
            this.path = path;
            this.startTime = startTime;
            this.endTime = endTime;
            this.originalDuration = originalDuration;
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
            this.framingScale = framingScale;
            this.framingOffsetX = framingOffsetX;
            this.framingOffsetY = framingOffsetY;
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
        }
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
        cancelCompletionFallback();
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
                hintView.setText(videoPaths.size() > 1 ? "内置视频 " + (currentIndex + 1) + "/" + videoPaths.size() : "内置视频预览");
                player.start();
                captureRecordingStartIfReady();
                scheduleCompletionFallback(session, player.getDuration());
            });
            mediaPlayer.setOnCompletionListener(player -> {
                cancelCompletionFallback();
                scheduleNextVideo(session, false);
            });
            mediaPlayer.setOnErrorListener((player, what, extra) -> {
                FileLog.e("Builtin video preview playback failed: " + what + "/" + extra);
                cancelCompletionFallback();
                scheduleNextVideo(session, true);
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Throwable e) {
            FileLog.e(e);
            cancelCompletionFallback();
            scheduleNextVideo(session, true);
        }
    }

    private void scheduleCompletionFallback(long session, int durationMs) {
        cancelCompletionFallback();
        if (durationMs <= 0 || released) {
            return;
        }
        fallbackCompletionSession = session;
        // 完成事件偶发丢失时，以视频时长加极短余量继续下一个，避免卡在第一段循环。
        textureView.postDelayed(completionFallbackRunnable, Math.max(250L, durationMs + 80L));
    }

    private void onCompletionFallback() {
        long session = fallbackCompletionSession;
        fallbackCompletionSession = -1;
        if (!released && prepared && mediaPlayer != null && session == playbackSession) {
            scheduleNextVideo(session, false);
        }
    }

    private void cancelCompletionFallback() {
        fallbackCompletionSession = -1;
        textureView.removeCallbacks(completionFallbackRunnable);
    }

    /**
     * MediaPlayer 完成回调必须回到视图主线程后才释放并替换 Surface，避免某些设备只停留在第一段视频。
     */
    private void scheduleNextVideo(long completedSession, boolean failed) {
        if (pendingTransitionSession == completedSession) {
            return;
        }
        pendingTransitionSession = completedSession;
        textureView.post(() -> {
            if (released || completedSession != playbackSession || videoPaths.isEmpty()) {
                return;
            }
            pendingTransitionSession = -1;
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

    private void changeFramingScale(float delta) {
        framingScale = Math.max(1f, Math.min(3f, framingScale + delta));
        applyCenterCrop();
    }

    private void moveFraming(float dx, float dy) {
        framingOffsetX += dx;
        framingOffsetY += dy;
        applyCenterCrop();
    }

    public float getFramingScale() {
        return framingScale;
    }

    public float getFramingOffsetX() {
        return framingOffsetX;
    }

    public float getFramingOffsetY() {
        return framingOffsetY;
    }

    private void applyCenterCrop() {
        if (released || videoWidth <= 0 || videoHeight <= 0 || textureView.getWidth() <= 0 || textureView.getHeight() <= 0) {
            return;
        }
        float baseScale = Math.max(textureView.getWidth() / (float) videoWidth, textureView.getHeight() / (float) videoHeight);
        float scale = baseScale * framingScale;
        float scaledWidth = videoWidth * scale;
        float scaledHeight = videoHeight * scale;
        float maxOffsetX = Math.max(0f, (scaledWidth - textureView.getWidth()) / 2f);
        float maxOffsetY = Math.max(0f, (scaledHeight - textureView.getHeight()) / 2f);
        framingOffsetX = Math.max(-maxOffsetX, Math.min(maxOffsetX, framingOffsetX));
        framingOffsetY = Math.max(-maxOffsetY, Math.min(maxOffsetY, framingOffsetY));
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((textureView.getWidth() - scaledWidth) / 2f + framingOffsetX, (textureView.getHeight() - scaledHeight) / 2f + framingOffsetY);
        textureView.setTransform(matrix);
    }

    /** 取消、发送或离开聊天时必须调用；此方法可重复调用。 */
    public void release() {
        if (released) {
            return;
        }
        released = true;
        prepared = false;
        clearRecordingSnapshot();
        playbackSession++;
        pendingTransitionSession = -1;
        cancelCompletionFallback();
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
        pendingTransitionSession = -1;
        prepared = false;
        hasRenderedFrame = false;
        cancelCompletionFallback();
        releasePlayer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }
}
