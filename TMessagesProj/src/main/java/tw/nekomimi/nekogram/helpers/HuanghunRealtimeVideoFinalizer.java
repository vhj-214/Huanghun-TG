package tw.nekomimi.nekogram.helpers;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 为实时内置录制封装最终 MP4。
 *
 * 实时录像器负责产生连续的 360×360 H.264 画面，但不会从扬声器回录音频。此类不重新
 * 编码画面，而是保留该实时文件的视频轨，并从录制快照对应的源 MP4 中复制 AAC 音频样本。
 * 这样最终文件自身包含音轨，发送时可直接使用 Telegram 的圆形消息属性，无需再进入会把
 * 预览中的圆形消息降级为普通视频的二次视频转换流程。
 */
public final class HuanghunRealtimeVideoFinalizer {

    private static final String AAC_MIME = "audio/mp4a-latm";
    private static final long MIN_TIMESTAMP_STEP_US = 1L;

    private HuanghunRealtimeVideoFinalizer() {
    }

    /**
     * 将实时视频轨与快照时间线上的源 AAC 音轨封装为一个新 MP4。所有录制快照必须包含兼容
     * 的 AAC 轨；任意源文件不满足条件时返回 null，让调用方明确提示，而不是悄悄发送无声文件。
     */
    public static File finalizeWithSourceAudio(File realtimeVideo, List<HuanghunBuiltinVideoPreview.RecordingSnapshot> recordings) {
        if (realtimeVideo == null || !realtimeVideo.isFile() || realtimeVideo.length() <= 0 || recordings == null || recordings.isEmpty()) {
            return null;
        }
        ArrayList<HuanghunBuiltinVideoPreview.RecordingSnapshot> valid = new ArrayList<>();
        for (HuanghunBuiltinVideoPreview.RecordingSnapshot recording : recordings) {
            if (recording != null && recording.path != null && recording.endTime > recording.startTime && new File(recording.path).isFile()) {
                valid.add(recording);
            }
        }
        if (valid.isEmpty()) {
            return null;
        }

        MediaExtractor probe = new MediaExtractor();
        MediaFormat audioFormat = null;
        try {
            for (HuanghunBuiltinVideoPreview.RecordingSnapshot recording : valid) {
                probe.setDataSource(recording.path);
                int audioTrack = MediaController.findTrack(probe, true);
                if (audioTrack < 0) {
                    return null;
                }
                MediaFormat format = probe.getTrackFormat(audioTrack);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (!AAC_MIME.equals(mime)) {
                    return null;
                }
                if (audioFormat == null) {
                    audioFormat = format;
                } else if (!isCompatibleAacFormat(audioFormat, format)) {
                    return null;
                }
                probe.release();
                probe = new MediaExtractor();
            }
        } catch (Throwable error) {
            FileLog.e(error);
            return null;
        } finally {
            try {
                probe.release();
            } catch (Throwable ignore) {
            }
        }
        if (audioFormat == null) {
            return null;
        }

        File output = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "huanghun_realtime_final_" + System.currentTimeMillis() + ".mp4");
        if (output.exists() && !output.delete()) {
            return null;
        }

        MediaExtractor videoExtractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean muxerStarted = false;
        try {
            videoExtractor.setDataSource(realtimeVideo.getAbsolutePath());
            int sourceVideoTrack = MediaController.findTrack(videoExtractor, false);
            if (sourceVideoTrack < 0) {
                return null;
            }
            MediaFormat videoFormat = videoExtractor.getTrackFormat(sourceVideoTrack);
            String videoMime = videoFormat.getString(MediaFormat.KEY_MIME);
            if (videoMime == null || !videoMime.startsWith("video/")) {
                return null;
            }

            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outputVideoTrack = muxer.addTrack(videoFormat);
            int outputAudioTrack = muxer.addTrack(audioFormat);
            muxer.start();
            muxerStarted = true;

            copyVideoTrack(videoExtractor, sourceVideoTrack, muxer, outputVideoTrack);
            copyAudioTimeline(valid, muxer, outputAudioTrack);
            return output.isFile() && output.length() > 0 ? output : null;
        } catch (Throwable error) {
            FileLog.e(error);
            //noinspection ResultOfMethodCallIgnored
            output.delete();
            return null;
        } finally {
            try {
                videoExtractor.release();
            } catch (Throwable ignore) {
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

    private static boolean isCompatibleAacFormat(MediaFormat first, MediaFormat second) {
        if (!AAC_MIME.equals(first.getString(MediaFormat.KEY_MIME)) || !AAC_MIME.equals(second.getString(MediaFormat.KEY_MIME))) {
            return false;
        }
        if (first.containsKey(MediaFormat.KEY_SAMPLE_RATE) && second.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                && first.getInteger(MediaFormat.KEY_SAMPLE_RATE) != second.getInteger(MediaFormat.KEY_SAMPLE_RATE)) {
            return false;
        }
        return !first.containsKey(MediaFormat.KEY_CHANNEL_COUNT) || !second.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                || first.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == second.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    }

    private static void copyVideoTrack(MediaExtractor extractor, int sourceTrack, MediaMuxer muxer, int outputTrack) {
        extractor.selectTrack(sourceTrack);
        ByteBuffer buffer = ByteBuffer.allocateDirect(getBufferSize(extractor.getTrackFormat(sourceTrack)));
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long firstTimestampUs = -1L;
        long lastTimestampUs = -1L;
        while (true) {
            buffer.clear();
            int size = extractor.readSampleData(buffer, 0);
            if (size < 0) {
                break;
            }
            long sourceTimestampUs = extractor.getSampleTime();
            if (firstTimestampUs < 0L) {
                firstTimestampUs = sourceTimestampUs;
            }
            long timestampUs = Math.max(0L, sourceTimestampUs - firstTimestampUs);
            if (timestampUs <= lastTimestampUs) {
                timestampUs = lastTimestampUs + MIN_TIMESTAMP_STEP_US;
            }
            info.offset = 0;
            info.size = size;
            info.presentationTimeUs = timestampUs;
            info.flags = extractor.getSampleFlags();
            muxer.writeSampleData(outputTrack, buffer, info);
            lastTimestampUs = timestampUs;
            extractor.advance();
        }
    }

    private static void copyAudioTimeline(List<HuanghunBuiltinVideoPreview.RecordingSnapshot> recordings, MediaMuxer muxer, int outputTrack) {
        long timelineOffsetUs = 0L;
        long lastTimestampUs = -1L;
        for (HuanghunBuiltinVideoPreview.RecordingSnapshot recording : recordings) {
            long startUs = recording.startTime * 1000L;
            long endUs = recording.endTime * 1000L;
            long expectedDurationUs = Math.max(MIN_TIMESTAMP_STEP_US, endUs - startUs);
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(recording.path);
                int audioTrack = MediaController.findTrack(extractor, true);
                if (audioTrack < 0) {
                    throw new IllegalStateException("Missing audio track in recording source");
                }
                extractor.selectTrack(audioTrack);
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                ByteBuffer buffer = ByteBuffer.allocateDirect(getBufferSize(extractor.getTrackFormat(audioTrack)));
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (true) {
                    buffer.clear();
                    int size = extractor.readSampleData(buffer, 0);
                    if (size < 0) {
                        break;
                    }
                    long sourceTimestampUs = extractor.getSampleTime();
                    if (sourceTimestampUs < startUs) {
                        extractor.advance();
                        continue;
                    }
                    if (sourceTimestampUs >= endUs) {
                        break;
                    }
                    long timestampUs = timelineOffsetUs + Math.max(0L, sourceTimestampUs - startUs);
                    if (timestampUs <= lastTimestampUs) {
                        timestampUs = lastTimestampUs + MIN_TIMESTAMP_STEP_US;
                    }
                    info.offset = 0;
                    info.size = size;
                    info.presentationTimeUs = timestampUs;
                    info.flags = extractor.getSampleFlags();
                    muxer.writeSampleData(outputTrack, buffer, info);
                    lastTimestampUs = timestampUs;
                    extractor.advance();
                }
            } catch (Throwable error) {
                FileLog.e(error);
                throw new IllegalStateException("Cannot copy built-in recording audio", error);
            } finally {
                extractor.release();
            }
            timelineOffsetUs += expectedDurationUs;
        }
    }

    private static int getBufferSize(MediaFormat format) {
        if (format != null && format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            return Math.max(64 * 1024, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
        }
        return 256 * 1024;
    }
}
