package com.blackaby.Backend.GB.Peripherals;

import com.blackaby.Misc.Settings;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Wraps the host PCM output line used by the emulator.
 */
public class DuckAudioOutput {

    private static final int channels = 2;
    private static final int bytesPerSample = 2;
    private static final int frameBytes = channels * bytesPerSample;
    private static final int bufferFrames = 1024;
    private static final int queuedBatchCount = 8;

    private final byte[] buffer = new byte[bufferFrames * frameBytes];
    private final BlockingDeque<byte[]> pendingBatches = new LinkedBlockingDeque<>(queuedBatchCount);
    private final float sampleRate;
    private final AudioEnhancementChain enhancementChain;

    private volatile SourceDataLine line;
    private volatile boolean available;
    private volatile boolean closed;
    private int writeIndex;
    private long appliedEnhancementChainVersion = Long.MIN_VALUE;
    private Thread writerThread;

    /**
     * Creates an audio output wrapper for the requested sample rate.
     *
     * @param sampleRate output sample rate in hertz
     */
    public DuckAudioOutput(float sampleRate) {
        this.sampleRate = sampleRate;
        this.enhancementChain = new AudioEnhancementChain(sampleRate);
        Initialise();
    }

    private void Initialise() {
        AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
        try {
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, buffer.length * 8);
            line.start();
            available = true;
            closed = false;
            StartWriterThread();
        } catch (LineUnavailableException | IllegalArgumentException exception) {
            available = false;
            line = null;
            closed = true;
        }
    }

    /**
     * Appends one stereo sample frame to the output buffer.
     *
     * @param left left channel sample from -1.0 to 1.0
     * @param right right channel sample from -1.0 to 1.0
     */
    public synchronized void WriteSample(double left, double right) {
        if (!available) {
            return;
        }

        SyncEnhancementChain();
        AudioEnhancementChain.StereoFrame processedFrame = enhancementChain.Process(left, right);

        short leftSample = ToPcm16(processedFrame.Left());
        short rightSample = ToPcm16(processedFrame.Right());

        buffer[writeIndex++] = (byte) (leftSample & 0xFF);
        buffer[writeIndex++] = (byte) ((leftSample >>> 8) & 0xFF);
        buffer[writeIndex++] = (byte) (rightSample & 0xFF);
        buffer[writeIndex++] = (byte) ((rightSample >>> 8) & 0xFF);

        if (writeIndex >= buffer.length) {
            Flush();
        }
    }

    /**
     * Flushes the buffered PCM data to the host audio line.
     */
    public synchronized void Flush() {
        if (!available || writeIndex == 0) {
            return;
        }

        if (line == null) {
            writeIndex = 0;
            enhancementChain.ResetState();
            return;
        }
        int bytesToWrite = writeIndex - (writeIndex % frameBytes);
        if (bytesToWrite <= 0) {
            writeIndex = 0;
            return;
        }

        EnqueueBatch(Arrays.copyOf(buffer, bytesToWrite));
        writeIndex -= bytesToWrite;
        if (writeIndex > 0) {
            System.arraycopy(buffer, bytesToWrite, buffer, 0, writeIndex);
        }
    }

    /**
     * Flushes and closes the host audio line.
     */
    public synchronized void Close() {
        closed = true;
        available = false;
        writeIndex = 0;
        pendingBatches.clear();
        Thread activeWriterThread = writerThread;
        writerThread = null;
        if (activeWriterThread != null) {
            activeWriterThread.interrupt();
        }
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
        }
        available = false;
        line = null;
        if (activeWriterThread != null && activeWriterThread != Thread.currentThread()) {
            try {
                activeWriterThread.join(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Drops any host audio queued for playback and clears the pending PCM buffer.
     */
    public synchronized void DiscardBufferedAudio() {
        writeIndex = 0;
        pendingBatches.clear();
        if (line != null) {
            line.stop();
            line.flush();
            line.start();
        }
        enhancementChain.ResetState();
    }

    private void StartWriterThread() {
        if (writerThread != null) {
            return;
        }

        writerThread = new Thread(this::WriterLoop, "DuckAudioOutput");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    private void WriterLoop() {
        while (!closed) {
            byte[] nextBatch;
            try {
                nextBatch = pendingBatches.takeFirst();
            } catch (InterruptedException exception) {
                if (closed) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            SourceDataLine activeLine = line;
            if (!available || activeLine == null || nextBatch.length == 0) {
                continue;
            }

            try {
                if (!activeLine.isRunning()) {
                    activeLine.start();
                }
                activeLine.write(nextBatch, 0, nextBatch.length);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                if (closed) {
                    break;
                }
            }
        }
    }

    private void EnqueueBatch(byte[] batch) {
        if (closed || batch == null || batch.length == 0) {
            return;
        }

        while (!pendingBatches.offerLast(batch)) {
            pendingBatches.pollFirst();
            if (closed) {
                return;
            }
        }
    }

    private short ToPcm16(double sample) {
        double clamped = Math.max(-1.0, Math.min(1.0, sample));
        return (short) Math.round(clamped * Short.MAX_VALUE);
    }

    private void SyncEnhancementChain() {
        long currentVersion = Settings.AudioEnhancementChainVersion();
        if (currentVersion == appliedEnhancementChainVersion) {
            return;
        }

        enhancementChain.SetPresets(Settings.IsAudioEnhancementChainEnabled()
                ? Settings.CurrentAudioEnhancementChain()
                : java.util.List.of());
        appliedEnhancementChainVersion = currentVersion;
    }

}

