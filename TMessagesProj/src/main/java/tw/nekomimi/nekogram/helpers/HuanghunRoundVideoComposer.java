package tw.nekomimi.nekogram.helpers;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.video.InputSurface;
import org.telegram.messenger.video.MediaCodecVideoConvertor;
import org.telegram.messenger.video.OutputSurface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 将跨越多个内置源视频的录制快照连续编码为一条 360×360 H.264 视频时间轴。
 *
 * 不再把每一段先写成独立 MP4 后再拼接轨道。该旧路径在部分设备上会在第二段的
 * 硬件编解码器切换后丢失后续回调，表现为只出现“正在合成”提示而没有发送结果。
 * 这里始终只创建一个输出编码器和一个 MediaMuxer；每个源片段仅替换解码器及其
 * 裁切渲染面，输出时间戳持续递增，因此最终文件本身就是连续的一条圆形视频。
 */
public final class HuanghunRoundVideoComposer {

    private static final int OUTPUT_SIZE = 360;
    private static final int OUTPUT_BITRATE = 850_000;
    private static final int OUTPUT_FPS = 25;
    private static final long DEQUEUE_TIMEOUT_US = 10_000L;
    private static final long MIN_OUTPUT_FRAME_INTERVAL_US = 1_000L;

    private HuanghunRoundVideoComposer() {
    }

    public static File composeVideoOnly(List<HuanghunBuiltinVideoPreview.RecordingSnapshot> recordings) {
        if (recordings == null || recordings.isEmpty()) {
            return null;
        }
        ArrayList<HuanghunBuiltinVideoPreview.RecordingSnapshot> validRecordings = new ArrayList<>();
        for (HuanghunBuiltinVideoPreview.RecordingSnapshot recording : recordings) {
            if (isValid(recording)) {
                validRecordings.add(recording);
            }
        }
        if (validRecordings.isEmpty()) {
            return null;
        }

        File cacheDirectory = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
        File output = new File(cacheDirectory, "huanghun_round_continuous_" + System.currentTimeMillis() + ".mp4");
        if (output.exists() && !output.delete()) {
            return null;
        }
        if (!encodeContinuousVideo(validRecordings, output)) {
            //noinspection ResultOfMethodCallIgnored
            output.delete();
            return null;
        }
        return output;
    }

    private static boolean isValid(HuanghunBuiltinVideoPreview.RecordingSnapshot recording) {
        return recording != null
                && recording.path != null
                && recording.endTime > recording.startTime
                && new File(recording.path).isFile();
    }

    private static boolean encodeContinuousVideo(List<HuanghunBuiltinVideoPreview.RecordingSnapshot> recordings, File output) {
        MediaCodec encoder = null;
        InputSurface inputSurface = null;
        MediaMuxer muxer = null;
        boolean encoderStarted = false;
        boolean muxerStarted = false;
        try {
            encoder = MediaCodec.createEncoderByType(MediaController.VIDEO_MIME_TYPE);
            MediaFormat encoderFormat = MediaFormat.createVideoFormat(MediaController.VIDEO_MIME_TYPE, OUTPUT_SIZE, OUTPUT_SIZE);
            encoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_BITRATE);
            encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, OUTPUT_FPS);
            encoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
            encoder.start();
            encoderStarted = true;
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            EncoderOutput encoderOutput = new EncoderOutput(muxer);

            long timelineUs = 0L;
            for (HuanghunBuiltinVideoPreview.RecordingSnapshot recording : recordings) {
                long producedDurationUs = appendSourceSegment(recording, timelineUs, encoder, inputSurface, encoderOutput, output);
                if (producedDurationUs <= 0) {
                    throw new IOException("No encodable frames in built-in recording segment");
                }
                timelineUs += producedDurationUs;
            }

            encoder.signalEndOfInputStream();
            drainEncoder(encoder, encoderOutput, true);
            muxerStarted = encoderOutput.started;
            return muxerStarted && output.isFile() && output.length() > 0;
        } catch (Throwable error) {
            FileLog.e(error);
            return false;
        } finally {
            if (encoder != null && encoderStarted) {
                try {
                    encoder.stop();
                } catch (Throwable error) {
                    FileLog.e(error);
                }
            }
            if (encoder != null) {
                try {
                    encoder.release();
                } catch (Throwable error) {
                    FileLog.e(error);
                }
            }
            if (inputSurface != null) {
                try {
                    inputSurface.release();
                } catch (Throwable error) {
                    FileLog.e(error);
                }
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
            }
        }
    }

    /**
     * 将一个源片段解码、裁切后直接交给共享的编码器。返回值是本段占用的连续输出时间，
     * 使用快照范围作下限，避免不同视频帧率造成下一段时间戳倒退或重叠。
     */
    private static long appendSourceSegment(HuanghunBuiltinVideoPreview.RecordingSnapshot recording, long timelineUs, MediaCodec encoder, InputSurface inputSurface, EncoderOutput encoderOutput, File output) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        OutputSurface outputSurface = null;
        boolean decoderStarted = false;
        try {
            extractor.setDataSource(recording.path);
            int videoTrack = MediaController.findTrack(extractor, false);
            if (videoTrack < 0) {
                return 0L;
            }
            MediaFormat videoFormat = extractor.getTrackFormat(videoTrack);
            String mime = videoFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null || !mime.startsWith("video/")) {
                return 0L;
            }

            int originalWidth = recording.originalWidth > 0 ? recording.originalWidth : videoFormat.getInteger(MediaFormat.KEY_WIDTH);
            int originalHeight = recording.originalHeight > 0 ? recording.originalHeight : videoFormat.getInteger(MediaFormat.KEY_HEIGHT);
            MediaController.CropState cropState = createCropState(recording, originalWidth, originalHeight);
            VideoEditedInfo renderInfo = new VideoEditedInfo();
            renderInfo.cropState = cropState;
            MediaCodecVideoConvertor.ConvertVideoParams renderParams = MediaCodecVideoConvertor.ConvertVideoParams.of(
                    recording.path,
                    output,
                    0,
                    0,
                    false,
                    originalWidth,
                    originalHeight,
                    OUTPUT_SIZE,
                    OUTPUT_SIZE,
                    OUTPUT_FPS,
                    OUTPUT_BITRATE,
                    OUTPUT_BITRATE,
                    recording.startTime * 1000L,
                    recording.endTime * 1000L,
                    -1,
                    true,
                    Math.max(1L, recording.endTime - recording.startTime) * 1000L,
                    null,
                    renderInfo
            );

            // InputSurface 的 EGL 上下文必须在创建 OutputSurface 前处于当前状态。
            inputSurface.makeCurrent();
            outputSurface = new OutputSurface(null, null, null, null, null, cropState,
                    OUTPUT_SIZE, OUTPUT_SIZE, originalWidth, originalHeight, 0, OUTPUT_FPS,
                    false, null, null, null, renderParams);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(videoFormat, outputSurface.getSurface(), null, 0);
            decoder.start();
            decoderStarted = true;

            long startUs = Math.max(0L, recording.startTime) * 1000L;
            long endUs = Math.max(startUs + 1L, recording.endTime * 1000L);
            long expectedDurationUs = Math.max(MIN_OUTPUT_FRAME_INTERVAL_US, endUs - startUs);
            extractor.selectTrack(videoTrack);
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            MediaCodec.BufferInfo decoderInfo = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean decoderDone = false;
            long lastPresentationUs = timelineUs - MIN_OUTPUT_FRAME_INTERVAL_US;
            int renderedFrames = 0;

            while (!decoderDone) {
                if (!inputDone) {
                    int inputBufferIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                        if (inputBuffer == null) {
                            throw new IOException("Decoder input buffer is unavailable");
                        }
                        inputBuffer.clear();
                        long sampleTimeUs = extractor.getSampleTime();
                        int sampleSize = sampleTimeUs < 0 || sampleTimeUs >= endUs ? -1 : extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTimeUs, 0);
                            extractor.advance();
                        }
                    }
                }

                int decoderStatus = decoder.dequeueOutputBuffer(decoderInfo, DEQUEUE_TIMEOUT_US);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER || decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED || decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    continue;
                }
                if (decoderStatus < 0) {
                    throw new IOException("Unexpected decoder status " + decoderStatus);
                }

                boolean endOfStream = (decoderInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                boolean renderFrame = decoderInfo.size > 0 && decoderInfo.presentationTimeUs >= startUs && decoderInfo.presentationTimeUs < endUs;
                decoder.releaseOutputBuffer(decoderStatus, renderFrame);
                if (renderFrame) {
                    outputSurface.awaitNewImage();
                    long relativeUs = Math.max(0L, decoderInfo.presentationTimeUs - startUs);
                    long presentationUs = timelineUs + relativeUs;
                    if (presentationUs <= lastPresentationUs) {
                        presentationUs = lastPresentationUs + MIN_OUTPUT_FRAME_INTERVAL_US;
                    }
                    outputSurface.drawImage(presentationUs * 1000L);
                    inputSurface.setPresentationTime(presentationUs * 1000L);
                    if (!inputSurface.swapBuffers()) {
                        throw new IOException("Unable to submit continuous round-video frame");
                    }
                    lastPresentationUs = presentationUs;
                    renderedFrames++;
                    drainEncoder(encoder, encoderOutput, false);
                }
                if (endOfStream) {
                    decoderDone = true;
                }
            }

            if (renderedFrames == 0) {
                return 0L;
            }
            long actualDurationUs = Math.max(MIN_OUTPUT_FRAME_INTERVAL_US, lastPresentationUs - timelineUs + MIN_OUTPUT_FRAME_INTERVAL_US);
            return Math.max(expectedDurationUs, actualDurationUs);
        } finally {
            if (decoder != null && decoderStarted) {
                try {
                    decoder.stop();
                } catch (Throwable error) {
                    FileLog.e(error);
                }
            }
            if (decoder != null) {
                try {
                    decoder.release();
                } catch (Throwable error) {
                    FileLog.e(error);
                }
            }
            if (outputSurface != null) {
                try {
                    outputSurface.release();
                } catch (Throwable error) {
                    FileLog.e(error);
                }
            }
            extractor.release();
            // 替换解码器与 OutputSurface 后，继续回到同一编码器的 EGL 上下文。
            inputSurface.makeCurrent();
        }
    }

    private static void drainEncoder(MediaCodec encoder, EncoderOutput output, boolean endOfStream) throws Exception {
        int idleAttempts = 0;
        while (true) {
            int encoderStatus = encoder.dequeueOutputBuffer(output.bufferInfo, DEQUEUE_TIMEOUT_US);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    return;
                }
                if (++idleAttempts > 100) {
                    throw new IOException("Timed out waiting for continuous round-video encoder");
                }
                continue;
            }
            if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                continue;
            }
            if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (output.started) {
                    throw new IOException("Continuous round-video encoder changed format twice");
                }
                output.track = output.muxer.addTrack(encoder.getOutputFormat());
                output.muxer.start();
                output.started = true;
                continue;
            }
            if (encoderStatus < 0) {
                throw new IOException("Unexpected encoder status " + encoderStatus);
            }

            ByteBuffer encodedData = encoder.getOutputBuffer(encoderStatus);
            if (encodedData == null) {
                throw new IOException("Encoder output buffer is unavailable");
            }
            boolean end = (output.bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            if ((output.bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                output.bufferInfo.size = 0;
            }
            if (output.bufferInfo.size > 0) {
                if (!output.started || output.track < 0) {
                    throw new IOException("Continuous round-video muxer has not started");
                }
                encodedData.position(output.bufferInfo.offset);
                encodedData.limit(output.bufferInfo.offset + output.bufferInfo.size);
                output.muxer.writeSampleData(output.track, encodedData, output.bufferInfo);
            }
            encoder.releaseOutputBuffer(encoderStatus, false);
            if (end) {
                return;
            }
        }
    }

    private static MediaController.CropState createCropState(HuanghunBuiltinVideoPreview.RecordingSnapshot recording, int originalWidth, int originalHeight) {
        MediaController.CropState crop = new MediaController.CropState();
        float sourceAspect = originalWidth / (float) Math.max(1, originalHeight);
        crop.cropPw = sourceAspect > 1f ? 1f / sourceAspect : 1f;
        crop.cropPh = sourceAspect < 1f ? sourceAspect : 1f;
        crop.cropScale = Math.max(1f, recording.framingScale);
        float previewBaseScale = Math.max(
                recording.viewportWidth / (float) Math.max(1, originalWidth),
                recording.viewportHeight / (float) Math.max(1, originalHeight)
        );
        float previewScale = Math.max(0.0001f, previewBaseScale * crop.cropScale);
        crop.cropPx = recording.framingOffsetX / (Math.max(1, originalWidth) * previewScale);
        // CropState 使用 Android 屏幕坐标；TextureRenderer 会在 OpenGL 绘制时完成 Y 轴转换。
        crop.cropPy = recording.framingOffsetY / (Math.max(1, originalHeight) * previewScale);
        crop.transformWidth = OUTPUT_SIZE;
        crop.transformHeight = OUTPUT_SIZE;
        return crop;
    }

    private static final class EncoderOutput {
        final MediaMuxer muxer;
        final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int track = -1;
        boolean started;

        EncoderOutput(MediaMuxer muxer) {
            this.muxer = muxer;
        }
    }
}
