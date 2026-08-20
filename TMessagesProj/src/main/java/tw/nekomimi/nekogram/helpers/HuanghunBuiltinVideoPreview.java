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
    private final TextView confirmRecordingButton;
    private final OnRecordingConfirmedListener recordingConfirmedListener;

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
    private boolean recordingConfirmed;
    // 手机录像式实时输出：从确认开始到停止始终只有一个编码器和一个文件。
    private HuanghunRealtimeRoundVideoRecorder realtimeRoundVideoRecorder;
    // 录制跨越播放列表边界时，已结束的视频片段按真实时间顺序保留在此处。
    private final ArrayList<RecordingSnapshot> completedRecordingSegments = new ArrayList<>();
    // 部分设备的 MediaPlayer 不会稳定派发 onCompletion；使用同一会话号的时长后备任务保证列表继续推进。
    // 切入下一段的首个画面与 MediaPlayer 的 prepared 回调可能相隔极短时间；
    // 小于该阈值的尾段无法稳定进入硬件编码，保留它会让整条合成消息失败。
    private static final long MIN_ENCODABLE_TAIL_SEGMENT_MS = 120L;
    private long fallbackCompletionSession = -1;
    private long pendingTransitionSession = -1;
    private final Runnable completionFallbackRunnable = this::onCompletionFallback;

    public interface OnRecordingConfirmedListener {
        void onRecordingConfirmed();
    }

    public HuanghunBuiltinVideoPreview(Context context, List<String> paths, OnRecordingConfirmedListener recordingConfirmedListener) {
        super(context);
        this.recordingConfirmedListener = recordingConfirmedListener;
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
        // 控制面板固定在左下区域，避开右侧的官方发送键与录制状态按钮。
        addView(framingControls, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 0, 112));

        confirmRecordingButton = new TextView(context);
        confirmRecordingButton.setText("确认开始");
        confirmRecordingButton.setTextSize(17);
        confirmRecordingButton.setTextColor(0xFFFFFFFF);
        confirmRecordingButton.setGravity(Gravity.CENTER);
        confirmRecordingButton.setClickable(true);
        confirmRecordingButton.setContentDescription("确认开始内置视频录制");
        confirmRecordingButton.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        confirmRecordingButton.setBackground(createControlBackground(0xE82B8A5B, AndroidUtilities.dp(22)));
        confirmRecordingButton.setOnClickListener(v -> confirmRecording());
        addView(confirmRecordingButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 118));

        hintView = new TextView(context);
        hintView.setText("调整取景后，点击确认开始");
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
        controls.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7));
        controls.setBackground(createControlBackground(0xAA101820, AndroidUtilities.dp(22)));

        LinearLayout zoomRow = createControlRow(context);
        TextView zoomIn = createControlButton(context, "＋");
        zoomIn.setContentDescription("放大视频");
        zoomIn.setOnClickListener(v -> changeFramingScale(0.15f));
        TextView zoomOut = createControlButton(context, "－");
        zoomOut.setContentDescription("缩小视频");
        zoomOut.setOnClickListener(v -> changeFramingScale(-0.15f));
        zoomRow.addView(zoomIn, LayoutHelper.createLinear(58, 50));
        zoomRow.addView(zoomOut, LayoutHelper.createLinear(58, 50));
        controls.addView(zoomRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 54, Gravity.CENTER));

        LinearLayout upRow = createControlRow(context);
        TextView up = createControlButton(context, "▲");
        up.setContentDescription("向上移动视频");
        up.setOnClickListener(v -> moveFraming(0f, -AndroidUtilities.dp(18)));
        upRow.addView(up, LayoutHelper.createLinear(58, 50));
        controls.addView(upRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 54, Gravity.CENTER));

        LinearLayout middleRow = createControlRow(context);
        TextView left = createControlButton(context, "◀");
        left.setContentDescription("向左移动视频");
        left.setOnClickListener(v -> moveFraming(-AndroidUtilities.dp(18), 0f));
        TextView right = createControlButton(context, "▶");
        right.setContentDescription("向右移动视频");
        right.setOnClickListener(v -> moveFraming(AndroidUtilities.dp(18), 0f));
        middleRow.addView(left, LayoutHelper.createLinear(58, 50));
        middleRow.addView(right, LayoutHelper.createLinear(58, 50));
        controls.addView(middleRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 54, Gravity.CENTER));

        LinearLayout downRow = createControlRow(context);
        TextView down = createControlButton(context, "▼");
        down.setContentDescription("向下移动视频");
        down.setOnClickListener(v -> moveFraming(0f, AndroidUtilities.dp(18)));
        downRow.addView(down, LayoutHelper.createLinear(58, 50));
        controls.addView(downRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 54, Gravity.CENTER));
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
        button.setTextSize(23);
        button.setTextColor(0xFFFFFFFF);
        button.setGravity(Gravity.CENTER);
        button.setBackground(createControlBackground(0x8AFFFFFF, AndroidUtilities.dp(16)));
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

    /**
     * 用户完成取景调整后才开始记录源片段。这样首次点击录制仅进入预览，
     * 不会把调整取景的时间误计入最终发送的视频。
     */
    public boolean beginRecording() {
        if (released || !prepared || mediaPlayer == null || recordingConfirmed) {
            return false;
        }
        recordingConfirmed = true;
        recordingRequested = true;
        completedRecordingSegments.clear();
        captureRecordingStartIfReady();
        realtimeRoundVideoRecorder = HuanghunRealtimeRoundVideoRecorder.create();
        if (realtimeRoundVideoRecorder != null) {
            // 立即收录当前已显示画面；之后的每一帧都由 onSurfaceTextureUpdated 写入同一文件。
            realtimeRoundVideoRecorder.captureFrame(textureView);
        }
        framingControls.setVisibility(GONE);
        confirmRecordingButton.setVisibility(GONE);
        hintView.setText("正在录制，点击发送完成");
        return recordingPath != null;
    }

    public boolean isRecordingConfirmed() {
        return recordingConfirmed;
    }

    private void confirmRecording() {
        if (released || recordingConfirmed) {
            return;
        }
        if (!prepared || mediaPlayer == null) {
            hintView.setText("视频正在加载，请稍后确认");
            return;
        }
        if (beginRecording() && recordingConfirmedListener != null) {
            recordingConfirmedListener.onRecordingConfirmed();
        }
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
     * 结束录制并返回按实际播放顺序组成的全部片段。跨越第一个视频后进入第二个
     * 视频时，两段都会保留，绝不再只引用开始录制时的源文件。
     */
    /**
     * 完成手机录像式的实时单文件输出。必须在释放预览前调用；返回 null 时调用方可
     * 安全回退到已有快照合成路径，避免设备不支持编码器时丢失录制内容。
     */
    public File finishRealtimeRoundVideoRecording() {
        HuanghunRealtimeRoundVideoRecorder recorder = realtimeRoundVideoRecorder;
        realtimeRoundVideoRecorder = null;
        return recorder == null ? null : recorder.finish();
    }

    public ArrayList<RecordingSnapshot> finishRecording() {
        // 若用户在下一段刚显示后立即停止，先以当前已准备播放器补获该段，
        // 再封存，避免 recordingPath 尚未写入而返回空列表并丢失发送。
        captureRecordingStartIfReady();
        appendCurrentRecordingSegment(SystemClock.elapsedRealtime(), false);
        ArrayList<RecordingSnapshot> snapshots = new ArrayList<>(completedRecordingSegments);
        clearRecordingSnapshot();
        return snapshots;
    }

    /** 将当前播放的视频封存为一个录制片段；到片尾时使用完整的剩余时长。 */
    private void appendCurrentRecordingSegment(long now, boolean completedByPlayback) {
        if (!recordingRequested) {
            return;
        }
        // 跨视频切换时，停止事件可能早于下一段的常规捕获入口；这里再次补获，
        // 使“已经播放到第 2 段后停止”始终至少能生成可发送的录制结果。
        captureRecordingStartIfReady();
        if (recordingPath == null || recordingStartedAt <= 0) {
            return;
        }
        long start = recordingStartPosition;
        long end = completedByPlayback
                ? recordingSourceDuration
                : Math.min(recordingSourceDuration, start + Math.max(1L, now - recordingStartedAt));
        long segmentDuration = end - start;
        // 仅丢弃在已完成前段之后出现的极短切换尾段；该段没有有效可编码画面，
        // 不应阻止此前已完整录制的内容进入发送队列。
        boolean discardTransientTail = !completedByPlayback
                && !completedRecordingSegments.isEmpty()
                && segmentDuration < MIN_ENCODABLE_TAIL_SEGMENT_MS;
        if (segmentDuration > 0 && !discardTransientTail) {
            completedRecordingSegments.add(new RecordingSnapshot(
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
            ));
        }
        recordingPath = null;
        recordingStartedAt = 0;
        recordingStartPosition = 0;
        recordingSourceDuration = 0;
        recordingSourceWidth = 0;
        recordingSourceHeight = 0;
    }

    private void clearRecordingSnapshot() {
        if (realtimeRoundVideoRecorder != null) {
            realtimeRoundVideoRecorder.cancel();
            realtimeRoundVideoRecorder = null;
        }
        recordingRequested = false;
        recordingConfirmed = false;
        completedRecordingSegments.clear();
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
                if (!recordingConfirmed) {
                    hintView.setText(videoPaths.size() > 1 ? "内置视频 " + (currentIndex + 1) + "/" + videoPaths.size() + "，调整后确认开始" : "调整取景后，点击确认开始");
                }
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
            if (!failed && recordingConfirmed) {
                // 当前段已播放到结尾；先封存其完整剩余部分，再切换并开始记录下一段。
                appendCurrentRecordingSegment(SystemClock.elapsedRealtime(), true);
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
        if (!released && recordingConfirmed && realtimeRoundVideoRecorder != null) {
            realtimeRoundVideoRecorder.captureFrame(textureView);
        }
    }
}
