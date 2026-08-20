package tw.nekomimi.nekogram.helpers;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.SystemClock;
import android.view.TextureView;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.video.InputSurface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 实时圆形视频录像器。
 *
 * 它从内置预览 TextureView 的已渲染画面持续采集帧，并始终写入同一个 H.264 编码器和
 * 同一个 MP4 文件。播放器切换下一个内置视频只会改变 TextureView 的内容，不会停止录像
 * 编码器，因此最终文件和手机录像一样是一条连续时间轴，不需要停录后再拆段合成。
 */
final class HuanghunRealtimeRoundVideoRecorder {

    private static final int OUTPUT_SIZE = 360;
    private static final int BITRATE = 850_000;
    private static final int FRAME_RATE = 20;
    private static final long FRAME_INTERVAL_MS = 1000L / FRAME_RATE;
    private static final long DEQUEUE_TIMEOUT_US = 10_000L;

    private final File outputFile;
    private final long startedAtMs;
    private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    private MediaCodec encoder;
    private InputSurface inputSurface;
    private MediaMuxer muxer;
    private BitmapFrameRenderer frameRenderer;
    private int videoTrack = -1;
    private boolean encoderStarted;
    private boolean muxerStarted;
    private boolean finished;
    private boolean failed;
    private long lastCaptureAtMs = -1L;
    private long lastPresentationUs = -1L;
    private int capturedFrameCount;
    // 复用固定大小的帧缓冲，避免 TextureView 每帧新建位图触发 GC 卡顿。
    private Bitmap captureBitmap;

    static HuanghunRealtimeRoundVideoRecorder create() {
        File output = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "huanghun_round_realtime_" + System.currentTimeMillis() + ".mp4");
        if (output.exists() && !output.delete()) {
            return null;
        }
        try {
            return new HuanghunRealtimeRoundVideoRecorder(output);
        } catch (Throwable error) {
            FileLog.e(error);
            //noinspection ResultOfMethodCallIgnored
            output.delete();
            return null;
        }
    }

    private HuanghunRealtimeRoundVideoRecorder(File outputFile) throws IOException {
        this.outputFile = outputFile;
        this.startedAtMs = SystemClock.elapsedRealtime();
        encoder = MediaCodec.createEncoderByType(MediaController.VIDEO_MIME_TYPE);
        MediaFormat format = MediaFormat.createVideoFormat(MediaController.VIDEO_MIME_TYPE, OUTPUT_SIZE, OUTPUT_SIZE);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = new InputSurface(encoder.createInputSurface());
        inputSurface.makeCurrent();
        frameRenderer = new BitmapFrameRenderer();
        encoder.start();
        encoderStarted = true;
        muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    /**
     * 由 TextureView 的 onSurfaceTextureUpdated 主线程回调调用。帧率上限确保 360px
     * 读回不会占满界面线程；播放器正常切换后下一帧会自然进入同一编码时间轴。
     */
    void captureFrame(TextureView previewView) {
        if (finished || failed || previewView == null || !previewView.isAvailable()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (lastCaptureAtMs >= 0L && now - lastCaptureAtMs < FRAME_INTERVAL_MS) {
            return;
        }
        try {
            if (captureBitmap == null || captureBitmap.isRecycled()) {
                captureBitmap = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888);
            }
            Bitmap bitmap = previewView.getBitmap(captureBitmap);
            if (bitmap == null) {
                return;
            }
            inputSurface.makeCurrent();
            frameRenderer.draw(bitmap);
            long presentationUs = Math.max(0L, now - startedAtMs) * 1000L;
            if (presentationUs <= lastPresentationUs) {
                presentationUs = lastPresentationUs + 1_000L;
            }
            inputSurface.setPresentationTime(presentationUs * 1000L);
            if (!inputSurface.swapBuffers()) {
                throw new IOException("Unable to submit realtime round-video frame");
            }
            lastCaptureAtMs = now;
            lastPresentationUs = presentationUs;
            capturedFrameCount++;
            drainEncoder(false);
        } catch (Throwable error) {
            failed = true;
            FileLog.e(error);
        }
    }

    /** 完成同一条实时文件；失败或未捕获有效帧时返回 null，调用方可安全走旧的兼容路径。 */
    File finish() {
        if (finished) {
            return null;
        }
        finished = true;
        boolean success = false;
        try {
            if (!failed && capturedFrameCount > 0 && encoder != null) {
                encoder.signalEndOfInputStream();
                drainEncoder(true);
                success = muxerStarted && outputFile.isFile() && outputFile.length() > 0;
            }
        } catch (Throwable error) {
            FileLog.e(error);
        } finally {
            releaseResources();
        }
        if (!success) {
            //noinspection ResultOfMethodCallIgnored
            outputFile.delete();
            return null;
        }
        return outputFile;
    }

    /** 取消预览或不使用实时结果时释放资源，不向发送队列暴露临时文件。 */
    void cancel() {
        if (finished) {
            return;
        }
        finished = true;
        releaseResources();
        //noinspection ResultOfMethodCallIgnored
        outputFile.delete();
    }

    private void drainEncoder(boolean endOfStream) throws IOException {
        int idleAttempts = 0;
        while (encoder != null) {
            int status = encoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US);
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    return;
                }
                if (++idleAttempts > 100) {
                    throw new IOException("Timed out waiting for realtime round-video encoder");
                }
                continue;
            }
            if (status == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                continue;
            }
            if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerStarted) {
                    throw new IOException("Realtime round-video encoder changed format twice");
                }
                videoTrack = muxer.addTrack(encoder.getOutputFormat());
                muxer.start();
                muxerStarted = true;
                continue;
            }
            if (status < 0) {
                throw new IOException("Unexpected realtime encoder status " + status);
            }
            ByteBuffer encodedData = encoder.getOutputBuffer(status);
            if (encodedData == null) {
                throw new IOException("Realtime encoder output buffer is unavailable");
            }
            boolean end = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                bufferInfo.size = 0;
            }
            if (bufferInfo.size > 0) {
                if (!muxerStarted || videoTrack < 0) {
                    throw new IOException("Realtime round-video muxer has not started");
                }
                encodedData.position(bufferInfo.offset);
                encodedData.limit(bufferInfo.offset + bufferInfo.size);
                muxer.writeSampleData(videoTrack, encodedData, bufferInfo);
            }
            encoder.releaseOutputBuffer(status, false);
            if (end) {
                return;
            }
        }
    }

    private void releaseResources() {
        if (frameRenderer != null) {
            try {
                if (inputSurface != null) {
                    inputSurface.makeCurrent();
                }
                frameRenderer.release();
            } catch (Throwable error) {
                FileLog.e(error);
            }
            frameRenderer = null;
        }
        // 与官方转换器保持相同顺序：先解除 EGL/编码输入 Surface，再停止编码器，
        // 避免部分设备在编码器已释放后再次释放其输入 Surface 时抛出异常。
        if (inputSurface != null) {
            try {
                inputSurface.release();
            } catch (Throwable error) {
                FileLog.e(error);
            }
            inputSurface = null;
        }
        if (encoder != null && encoderStarted) {
            try {
                encoder.stop();
            } catch (Throwable error) {
                FileLog.e(error);
            }
        }
        encoderStarted = false;
        if (encoder != null) {
            try {
                encoder.release();
            } catch (Throwable error) {
                FileLog.e(error);
            }
            encoder = null;
        }
        if (muxer != null) {
            try {
                if (muxerStarted) {
                    muxer.stop();
                }
            } catch (Throwable error) {
                FileLog.e(error);
            }
            try {
                muxer.release();
            } catch (Throwable error) {
                FileLog.e(error);
            }
            muxer = null;
        }
        if (captureBitmap != null && !captureBitmap.isRecycled()) {
            captureBitmap.recycle();
        }
        captureBitmap = null;
    }

    /** 最小 GLES2 纹理绘制器：将实时取得的 360×360 预览位图绘制到编码器输入 Surface。 */
    private static final class BitmapFrameRenderer {
        private static final String VERTEX_SHADER =
                "attribute vec4 aPosition;\n" +
                "attribute vec2 aTexCoord;\n" +
                "varying vec2 vTexCoord;\n" +
                "void main() {\n" +
                "  gl_Position = aPosition;\n" +
                "  vTexCoord = aTexCoord;\n" +
                "}\n";
        private static final String FRAGMENT_SHADER =
                "precision mediump float;\n" +
                "varying vec2 vTexCoord;\n" +
                "uniform sampler2D uTexture;\n" +
                "void main() {\n" +
                "  gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
                "}\n";
        private static final float[] VERTICES = {
                -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f
        };
        private static final float[] TEXTURE_COORDS = {
                0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f
        };

        private final FloatBuffer vertices = createBuffer(VERTICES);
        private final FloatBuffer textureCoords = createBuffer(TEXTURE_COORDS);
        private final int program;
        private final int positionHandle;
        private final int textureCoordHandle;
        private final int textureHandle;
        private final int textureId;

        BitmapFrameRenderer() throws IOException {
            int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);
            int[] status = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            if (status[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                throw new IOException("Cannot link realtime round-video shader: " + log);
            }
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            textureCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord");
            textureHandle = GLES20.glGetUniformLocation(program, "uTexture");
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            textureId = textures[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        }

        void draw(Bitmap bitmap) {
            GLES20.glViewport(0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            GLES20.glUniform1i(textureHandle, 0);
            vertices.position(0);
            textureCoords.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertices);
            GLES20.glEnableVertexAttribArray(textureCoordHandle);
            GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureCoords);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(textureCoordHandle);
        }

        void release() {
            GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
            GLES20.glDeleteProgram(program);
        }

        private static int compileShader(int type, String source) throws IOException {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] status = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new IOException("Cannot compile realtime round-video shader: " + log);
            }
            return shader;
        }

        private static FloatBuffer createBuffer(float[] values) {
            FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            buffer.put(values).position(0);
            return buffer;
        }
    }
}
