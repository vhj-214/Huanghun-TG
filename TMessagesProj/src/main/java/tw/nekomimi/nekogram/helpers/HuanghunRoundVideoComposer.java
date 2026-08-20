package tw.nekomimi.nekogram.helpers;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.video.MediaCodecVideoConvertor;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 将内置视频录制跨越的多个源片段合成为一个统一的 360×360 H.264 时间轴。
 * 每段先单独走 Telegram 的视频转换器，因此不同的源尺寸、方向、编码和用户取景
 * 均会先标准化；随后仅拼接标准化视频轨道。声音由最终发送转换阶段重建，避免不同
 * 源文件的 AAC 参数不一致时造成合成文件无法播放。
 */
public final class HuanghunRoundVideoComposer {

    private static final int OUTPUT_SIZE = 360;
    private static final int OUTPUT_BITRATE = 850_000;
    private static final int OUTPUT_FPS = 25;

    private HuanghunRoundVideoComposer() {
    }

    public static File composeVideoOnly(List<HuanghunBuiltinVideoPreview.RecordingSnapshot> recordings) {
        if (recordings == null || recordings.isEmpty()) {
            return null;
        }
        ArrayList<File> segments = new ArrayList<>();
        ArrayList<HuanghunBuiltinVideoPreview.RecordingSnapshot> validRecordings = new ArrayList<>();
        File cacheDirectory = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
        long token = System.currentTimeMillis();
        try {
            for (int i = 0; i < recordings.size(); i++) {
                HuanghunBuiltinVideoPreview.RecordingSnapshot recording = recordings.get(i);
                if (!isValid(recording)) {
                    continue;
                }
                File segment = new File(cacheDirectory, "huanghun_round_segment_" + token + "_" + i + ".mp4");
                if (segment.exists() && !segment.delete()) {
                    throw new IllegalStateException("Cannot replace temporary round segment");
                }
                if (!convertSegment(recording, segment)) {
                    throw new IllegalStateException("Failed to convert built-in recording segment " + i);
                }
                segments.add(segment);
                validRecordings.add(recording);
            }
            if (segments.isEmpty()) {
                return null;
            }
            if (segments.size() == 1) {
                return segments.get(0);
            }
            File combined = new File(cacheDirectory, "huanghun_round_combined_" + token + ".mp4");
            if (combined.exists() && !combined.delete()) {
                throw new IllegalStateException("Cannot replace combined round video");
            }
            if (!appendStandardizedVideoTracks(segments, validRecordings, combined)) {
                throw new IllegalStateException("Failed to append converted recording segments");
            }
            for (File segment : segments) {
                if (!segment.equals(combined)) {
                    segment.delete();
                }
            }
            return combined;
        } catch (Throwable error) {
            FileLog.e(error);
            for (File segment : segments) {
                segment.delete();
            }
            return null;
        }
    }

    private static boolean isValid(HuanghunBuiltinVideoPreview.RecordingSnapshot recording) {
        return recording != null && recording.path != null && recording.endTime > recording.startTime
                && new File(recording.path).isFile();
    }

    private static boolean convertSegment(HuanghunBuiltinVideoPreview.RecordingSnapshot recording, File output) {
        int originalWidth = recording.originalWidth > 0 ? recording.originalWidth : OUTPUT_SIZE;
        int originalHeight = recording.originalHeight > 0 ? recording.originalHeight : OUTPUT_SIZE;
        int sourceBitrate = MediaController.getVideoBitrate(recording.path);
        if (sourceBitrate <= 0) {
            sourceBitrate = 1_500_000;
        }
        // 所有临时片段使用严格一致的编码规格；这样最终封装器只需写入一份
        // AVC 配置，即可在同一条时间轴内连续播放来自不同源文件的片段。
        int targetBitrate = OUTPUT_BITRATE;

        VideoEditedInfo info = new VideoEditedInfo();
        info.originalPath = recording.path;
        info.roundVideo = true;
        info.startTime = recording.startTime * 1000L;
        info.endTime = recording.endTime * 1000L;
        info.originalDuration = recording.originalDuration * 1000L;
        info.originalWidth = originalWidth;
        info.originalHeight = originalHeight;
        info.resultWidth = OUTPUT_SIZE;
        info.resultHeight = OUTPUT_SIZE;
        info.originalBitrate = sourceBitrate;
        info.bitrate = targetBitrate;
        info.framerate = OUTPUT_FPS;
        // 音频将在最终的单文件发送转换中按录制顺序统一重建，避免各段音频格式不同。
        info.muted = true;
        info.cropState = createCropState(recording, originalWidth, originalHeight);

        long durationUs = Math.max(1L, recording.endTime - recording.startTime) * 1000L;
        MediaCodecVideoConvertor.ConvertVideoParams params = MediaCodecVideoConvertor.ConvertVideoParams.of(
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
                targetBitrate,
                sourceBitrate,
                info.startTime,
                info.endTime,
                -1,
                true,
                durationUs,
                new MediaController.VideoConvertorListener() {
                    @Override
                    public boolean checkConversionCanceled() {
                        return false;
                    }

                    @Override
                    public void didWriteData(long availableSize, float progress) {
                        // 临时片段不属于消息发送队列，不发布中间进度事件。
                    }
                },
                info
        );
        boolean error = new MediaCodecVideoConvertor().convertVideo(params);
        return !error && output.isFile() && output.length() > 0;
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
        crop.cropPy = -recording.framingOffsetY / (Math.max(1, originalHeight) * previewScale);
        crop.transformWidth = OUTPUT_SIZE;
        crop.transformHeight = OUTPUT_SIZE;
        return crop;
    }

    private static boolean appendStandardizedVideoTracks(List<File> segments, List<HuanghunBuiltinVideoPreview.RecordingSnapshot> recordings, File output) {
        MediaMuxer muxer = null;
        boolean started = false;
        int muxerVideoTrack = -1;
        long timelineUs = 0;
        try {
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
                File segment = segments.get(segmentIndex);
                MediaExtractor extractor = new MediaExtractor();
                try {
                    extractor.setDataSource(segment.getAbsolutePath());
                    int videoTrack = MediaController.findTrack(extractor, false);
                    if (videoTrack < 0) {
                        return false;
                    }
                    MediaFormat format = extractor.getTrackFormat(videoTrack);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (!MediaController.VIDEO_MIME_TYPE.equals(mime)) {
                        return false;
                    }
                    if (muxerVideoTrack < 0) {
                        muxerVideoTrack = muxer.addTrack(format);
                        muxer.start();
                        started = true;
                    }
                    extractor.selectTrack(videoTrack);
                    int maxInputSize = 256 * 1024;
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxInputSize = Math.max(maxInputSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                    }
                    ByteBuffer buffer = ByteBuffer.allocateDirect(maxInputSize);
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    long firstTimestampUs = -1;
                    long lastTimestampUs = -1;
                    while (true) {
                        buffer.clear();
                        int size = extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            break;
                        }
                        long sampleTimeUs = extractor.getSampleTime();
                        if (firstTimestampUs < 0) {
                            firstTimestampUs = sampleTimeUs;
                        }
                        long relativeTimeUs = Math.max(0L, sampleTimeUs - firstTimestampUs);
                        info.offset = 0;
                        info.size = size;
                        info.presentationTimeUs = timelineUs + relativeTimeUs;
                        info.flags = extractor.getSampleFlags();
                        muxer.writeSampleData(muxerVideoTrack, buffer, info);
                        lastTimestampUs = relativeTimeUs;
                        extractor.advance();
                    }
                    long expectedDurationUs = Math.max(1L, recordings.get(segmentIndex).endTime - recordings.get(segmentIndex).startTime) * 1000L;
                    timelineUs += Math.max(expectedDurationUs, lastTimestampUs >= 0 ? lastTimestampUs + 1L : 1L);
                } finally {
                    extractor.release();
                }
            }
            return started;
        } catch (Throwable error) {
            FileLog.e(error);
            return false;
        } finally {
            if (muxer != null) {
                try {
                    if (started) {
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
}
