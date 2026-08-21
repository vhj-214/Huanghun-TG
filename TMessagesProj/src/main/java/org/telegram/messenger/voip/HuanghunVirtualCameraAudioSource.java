package org.telegram.messenger.voip;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;

import org.telegram.messenger.FileLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import tw.nekomimi.nekogram.NekoConfig;

/**
 * 将通话虚拟摄像头的本地视频音轨解码为 PCM，并由 WebRtcAudioRecord 混入上行通话音频。
 *
 * 解码线程只负责生产有限的预缓冲；音频线程只做无阻塞读取和饱和混音，因此暂停、切换和
 * 通话销毁均不会阻塞 WebRTC 的实时录音回调。播放器自身保持静音，避免把本地外放重新录入。
 */
public final class HuanghunVirtualCameraAudioSource {

    private static final String TAG = "HuanghunVirtualAudio";
    private static final long WAIT_SLICE_MS = 20L;

    private final Object lock = new Object();
    private final ArrayDeque<short[]> pcmChunks = new ArrayDeque<>();

    private ArrayList<String> videoPaths = new ArrayList<>();
    private Thread decoderThread;
    private short[] currentChunk;
    private int currentChunkOffset;
    private int bufferedSamples;
    private volatile boolean running;
    private volatile boolean playbackEnabled;
    private volatile int selectedIndex;
    private volatile long generation;
    private volatile int outputSampleRate = 48000;
    private volatile int outputChannels = 1;
    private long playbackStartMs = -1L;
    private long pauseStartedMs = -1L;
    private long pausedTotalMs;

    public void start(List<String> paths, int index) {
        Thread previousThread;
        synchronized (lock) {
            previousThread = stopLocked(false);
        }
        joinDecoderThread(previousThread);
        synchronized (lock) {
            videoPaths = paths == null ? new ArrayList<>() : new ArrayList<>(paths);
            selectedIndex = normalizeIndex(index);
            generation++;
            running = !videoPaths.isEmpty();
            playbackEnabled = false;
            playbackStartMs = -1L;
            pauseStartedMs = -1L;
            pausedTotalMs = 0L;
            clearPcmLocked();
            if (running) {
                decoderThread = new Thread(this::decodeLoop, "HuanghunVirtualAudio");
                decoderThread.setPriority(Thread.NORM_PRIORITY);
                decoderThread.start();
            }
        }
    }

    public void stop() {
        Thread thread;
        synchronized (lock) {
            thread = stopLocked(true);
        }
        joinDecoderThread(thread);
    }

    public void selectVideo(int index) {
        synchronized (lock) {
            if (!running || videoPaths.isEmpty()) {
                return;
            }
            selectedIndex = normalizeIndex(index);
            generation++;
            playbackEnabled = false;
            playbackStartMs = -1L;
            pauseStartedMs = -1L;
            pausedTotalMs = 0L;
            clearPcmLocked();
            lock.notifyAll();
        }
    }

    public void setPlaybackEnabled(boolean enabled) {
        synchronized (lock) {
            if (!running || playbackEnabled == enabled) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (enabled) {
                if (playbackStartMs < 0L) {
                    playbackStartMs = now;
                } else if (pauseStartedMs >= 0L) {
                    pausedTotalMs += Math.max(0L, now - pauseStartedMs);
                }
                pauseStartedMs = -1L;
            } else {
                pauseStartedMs = now;
            }
            playbackEnabled = enabled;
            lock.notifyAll();
        }
    }

    /**
     * 在 WebRTC 音频线程调用。无可用原声时直接返回；关闭声音时仍无声消费队列以保持时间轴同步，绝不阻塞录音线程。
     */
    public void mixInto(ByteBuffer microphonePcm, int bytes, int sampleRate, int channels) {
        if (!running || !playbackEnabled || microphonePcm == null || bytes <= 1 || sampleRate <= 0 || channels <= 0) {
            return;
        }
        // 即使用户关闭声音也继续消耗原声队列，从而在重新开启时保持与正在播放的视频时间轴同步。
        final boolean sendVideoSound = NekoConfig.huanghunCallVirtualVideoSound.Bool();
        if (sampleRate != outputSampleRate || channels != outputChannels) {
            synchronized (lock) {
                if (sampleRate != outputSampleRate || channels != outputChannels) {
                    outputSampleRate = sampleRate;
                    outputChannels = channels;
                    generation++;
                    clearPcmLocked();
                    lock.notifyAll();
                }
            }
            return;
        }
        final int sampleCount = bytes / 2;
        boolean consumed = false;
        synchronized (lock) {
            for (int sample = 0; sample < sampleCount; sample++) {
                short virtualSample = readNextSampleLocked();
                if (virtualSample == 0) {
                    continue;
                }
                consumed = true;
                if (!sendVideoSound) {
                    continue;
                }
                int offset = sample * 2;
                int microphoneSample = microphonePcm.getShort(offset);
                // 原声略低于麦克风，既保留视频声音又防止饱和失真。
                int mixed = microphoneSample + (virtualSample * 4) / 5;
                if (mixed > Short.MAX_VALUE) {
                    mixed = Short.MAX_VALUE;
                } else if (mixed < Short.MIN_VALUE) {
                    mixed = Short.MIN_VALUE;
                }
                microphonePcm.putShort(offset, (short) mixed);
            }
            if (consumed) {
                lock.notifyAll();
            }
        }
    }

    private short readNextSampleLocked() {
        while (currentChunk == null || currentChunkOffset >= currentChunk.length) {
            currentChunk = pcmChunks.pollFirst();
            currentChunkOffset = 0;
            if (currentChunk == null) {
                return 0;
            }
        }
        short sample = currentChunk[currentChunkOffset++];
        bufferedSamples = Math.max(0, bufferedSamples - 1);
        return sample;
    }

    private void decodeLoop() {
        long decodedGeneration = -1L;
        while (running) {
            final long currentGeneration;
            final String path;
            synchronized (lock) {
                if (!running || videoPaths.isEmpty()) {
                    return;
                }
                currentGeneration = generation;
                if (currentGeneration == decodedGeneration) {
                    try {
                        lock.wait(WAIT_SLICE_MS);
                    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    continue;
                }
                decodedGeneration = currentGeneration;
                path = videoPaths.get(normalizeIndex(selectedIndex));
            }
            long durationMs = decodeVideoAudio(path, currentGeneration);
            waitForVideoEnd(currentGeneration, durationMs);
            synchronized (lock) {
                if (!running) {
                    return;
                }
                if (generation == currentGeneration && !videoPaths.isEmpty()) {
                    selectedIndex = normalizeIndex(selectedIndex + 1);
                    generation++;
                    playbackEnabled = false;
                    playbackStartMs = -1L;
                    pauseStartedMs = -1L;
                    pausedTotalMs = 0L;
                    clearPcmLocked();
                    lock.notifyAll();
                }
            }
        }
    }

    /** Returns the selected video duration in milliseconds, including for files without audio. */
    private long decodeVideoAudio(String path, long expectedGeneration) {
        MediaExtractor extractor = null;
        MediaCodec codec = null;
        long durationUs = 0L;
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(path);
            int audioTrack = -1;
            MediaFormat audioFormat = null;
            for (int track = 0; track < extractor.getTrackCount(); track++) {
                MediaFormat format = extractor.getTrackFormat(track);
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = Math.max(durationUs, format.getLong(MediaFormat.KEY_DURATION));
                }
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/") && audioTrack < 0) {
                    audioTrack = track;
                    audioFormat = format;
                }
            }
            if (audioTrack < 0 || audioFormat == null) {
                return Math.max(1L, durationUs / 1000L);
            }
            extractor.selectTrack(audioTrack);
            String mime = audioFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                return Math.max(1L, durationUs / 1000L);
            }
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(audioFormat, null, null, 0);
            codec.start();
            boolean inputDone = false;
            boolean outputDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int inputRate = audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 48000;
            int inputChannels = audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            while (!outputDone && isGenerationCurrent(expectedGeneration)) {
                if (!inputDone) {
                    int inputBufferIndex = codec.dequeueInputBuffer(10_000);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferIndex);
                        if (inputBuffer == null) {
                            continue;
                        }
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long presentationTimeUs = extractor.getSampleTime();
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0);
                            extractor.advance();
                        }
                    }
                }
                int outputBufferIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = codec.getOutputFormat();
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        inputRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        inputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = Math.max(durationUs, format.getLong(MediaFormat.KEY_DURATION));
                    }
                } else if (outputBufferIndex >= 0) {
                    if (info.size > 0) {
                        ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                        if (outputBuffer != null) {
                            outputBuffer.position(info.offset);
                            outputBuffer.limit(info.offset + info.size);
                            byte[] pcm = new byte[info.size];
                            outputBuffer.get(pcm);
                            enqueueConvertedPcm(pcm, inputRate, inputChannels, expectedGeneration);
                        }
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Throwable ignore) {
                }
                try {
                    codec.release();
                } catch (Throwable ignore) {
                }
            }
            if (extractor != null) {
                try {
                    extractor.release();
                } catch (Throwable ignore) {
                }
            }
        }
        return Math.max(1L, durationUs / 1000L);
    }

    private void enqueueConvertedPcm(byte[] pcm, int inputRate, int inputChannels, long expectedGeneration) {
        if (pcm == null || pcm.length < 2 || inputRate <= 0 || inputChannels <= 0) {
            return;
        }
        final int targetRate = outputSampleRate;
        final int targetChannels = outputChannels;
        int inputSamples = pcm.length / 2;
        int inputFrames = inputSamples / inputChannels;
        if (inputFrames <= 0 || targetRate <= 0 || targetChannels <= 0) {
            return;
        }
        int outputFrames = Math.max(1, (int) ((long) inputFrames * targetRate / inputRate));
        short[] converted = new short[outputFrames * targetChannels];
        ByteBuffer source = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        for (int outputFrame = 0; outputFrame < outputFrames; outputFrame++) {
            int inputFrame = Math.min(inputFrames - 1, (int) ((long) outputFrame * inputRate / targetRate));
            for (int channel = 0; channel < targetChannels; channel++) {
                int inputChannel = Math.min(inputChannels - 1, channel);
                if (targetChannels == 1 && inputChannels > 1) {
                    int sum = 0;
                    for (int sourceChannel = 0; sourceChannel < inputChannels; sourceChannel++) {
                        sum += source.getShort((inputFrame * inputChannels + sourceChannel) * 2);
                    }
                    converted[outputFrame] = (short) (sum / inputChannels);
                } else {
                    converted[outputFrame * targetChannels + channel] = source.getShort((inputFrame * inputChannels + inputChannel) * 2);
                }
            }
        }
        synchronized (lock) {
            while (isGenerationCurrentLocked(expectedGeneration) && bufferedSamples >= maxBufferedSamplesLocked()) {
                try {
                    lock.wait(WAIT_SLICE_MS);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (!isGenerationCurrentLocked(expectedGeneration)) {
                return;
            }
            pcmChunks.addLast(converted);
            bufferedSamples += converted.length;
            lock.notifyAll();
        }
    }

    private void waitForVideoEnd(long expectedGeneration, long durationMs) {
        if (durationMs <= 0L) {
            return;
        }
        synchronized (lock) {
            while (isGenerationCurrentLocked(expectedGeneration)) {
                if (!playbackEnabled || playbackStartMs < 0L) {
                    try {
                        lock.wait(WAIT_SLICE_MS);
                    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    continue;
                }
                long now = SystemClock.elapsedRealtime();
                long activeElapsed = now - playbackStartMs - pausedTotalMs;
                if (activeElapsed >= durationMs) {
                    return;
                }
                try {
                    lock.wait(Math.min(WAIT_SLICE_MS, durationMs - activeElapsed));
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private boolean isGenerationCurrent(long expectedGeneration) {
        synchronized (lock) {
            return isGenerationCurrentLocked(expectedGeneration);
        }
    }

    private boolean isGenerationCurrentLocked(long expectedGeneration) {
        return running && generation == expectedGeneration;
    }

    private int maxBufferedSamplesLocked() {
        return Math.max(outputChannels, outputSampleRate * outputChannels);
    }

    private int normalizeIndex(int index) {
        int size = videoPaths.size();
        if (size <= 0) {
            return 0;
        }
        int normalized = index % size;
        return normalized < 0 ? normalized + size : normalized;
    }

    private void clearPcmLocked() {
        pcmChunks.clear();
        currentChunk = null;
        currentChunkOffset = 0;
        bufferedSamples = 0;
    }

    private Thread stopLocked(boolean clearPaths) {
        running = false;
        playbackEnabled = false;
        generation++;
        clearPcmLocked();
        playbackStartMs = -1L;
        pauseStartedMs = -1L;
        pausedTotalMs = 0L;
        Thread thread = decoderThread;
        decoderThread = null;
        lock.notifyAll();
        if (clearPaths) {
            videoPaths.clear();
        }
        return thread;
    }

    private void joinDecoderThread(Thread thread) {
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        try {
            thread.join(300L);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
    }
}
