package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置视频录制模式的预览层。
 *
 * 此视图不会访问摄像头、麦克风或即时相机类；它仅顺序播放已经导入到应用私有目录的视频，
 * 由 ChatActivity 在录制状态机进入和退出时创建、发送当前文件并释放。
 */
public final class HuanghunBuiltinVideoPreview extends FrameLayout implements TextureView.SurfaceTextureListener {

    private final ArrayList<String> videoPaths = new ArrayList<>();
    private final TextureView textureView;
    private final TextView hintView;
    private MediaPlayer mediaPlayer;
    private Surface surface;
    private SurfaceTexture surfaceTexture;
    private int currentIndex;
    private int videoWidth;
    private int videoHeight;
    private boolean released;
    private boolean prepared;

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

        FrameLayout circle = new FrameLayout(context);
        GradientDrawable circleBackground = new GradientDrawable();
        circleBackground.setColor(0xE5161B23);
        circleBackground.setShape(GradientDrawable.OVAL);
        circle.setBackground(circleBackground);
        circle.setClipChildren(true);
        circle.setClipToOutline(true);
        addView(circle, LayoutHelper.createFrame(260, 260, Gravity.CENTER));

        textureView = new TextureView(context);
        textureView.setOpaque(false);
        textureView.setAlpha(0f);
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

    /**
     * 在 TextureView 可用后开始循环播放第一段有效视频。
     */
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

    private void playCurrent(SurfaceTexture texture) {
        if (released || texture == null || videoPaths.isEmpty()) {
            return;
        }
        int attempts = 0;
        while (attempts < videoPaths.size() && !new File(videoPaths.get(currentIndex)).isFile()) {
            currentIndex = (currentIndex + 1) % videoPaths.size();
            attempts++;
        }
        if (attempts >= videoPaths.size()) {
            hintView.setText("未找到可用内置视频");
            return;
        }
        releasePlayer();
        try {
            prepared = false;
            videoWidth = 0;
            videoHeight = 0;
            surfaceTexture = texture;
            surface = new Surface(texture);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(videoPaths.get(currentIndex));
            mediaPlayer.setSurface(surface);
            mediaPlayer.setVolume(0f, 0f);
            mediaPlayer.setOnVideoSizeChangedListener((player, width, height) -> {
                videoWidth = width;
                videoHeight = height;
                configureBuffer();
                applyCenterCrop();
            });
            mediaPlayer.setOnPreparedListener(player -> {
                if (released || player != mediaPlayer) {
                    return;
                }
                prepared = true;
                videoWidth = player.getVideoWidth();
                videoHeight = player.getVideoHeight();
                configureBuffer();
                applyCenterCrop();
                textureView.animate().alpha(1f).setDuration(160L).start();
                player.start();
            });
            mediaPlayer.setOnCompletionListener(player -> {
                if (released || player != mediaPlayer || videoPaths.isEmpty()) {
                    return;
                }
                currentIndex = (currentIndex + 1) % videoPaths.size();
                playCurrent(surfaceTexture);
            });
            mediaPlayer.setOnErrorListener((player, what, extra) -> {
                FileLog.e("Builtin video preview playback failed: " + what + "/" + extra);
                if (!released && !videoPaths.isEmpty()) {
                    currentIndex = (currentIndex + 1) % videoPaths.size();
                    textureView.post(() -> playCurrent(surfaceTexture));
                }
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Throwable e) {
            FileLog.e(e);
            currentIndex = (currentIndex + 1) % videoPaths.size();
            if (attempts + 1 < videoPaths.size()) {
                textureView.post(() -> playCurrent(surfaceTexture));
            } else {
                hintView.setText("内置视频无法播放");
                releasePlayer();
            }
        }
    }

    private void configureBuffer() {
        if (surfaceTexture == null || videoWidth <= 0 || videoHeight <= 0) {
            return;
        }
        try {
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

    /**
     * 取消、发送或离开聊天时必须调用；此方法可重复调用。
     */
    public void release() {
        if (released) {
            return;
        }
        released = true;
        prepared = false;
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
        releasePlayer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }
}
