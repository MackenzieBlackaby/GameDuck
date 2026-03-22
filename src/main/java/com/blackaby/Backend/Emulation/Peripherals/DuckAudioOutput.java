package com.blackaby.Backend.Emulation.Peripherals;

import com.blackaby.Misc.Settings;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Wraps the host PCM output line used by the emulator.
 */
public class DuckAudioOutput {

    private static final int channels = 2;
    private static final int bytesPerSample = 2;
    private static final int frameBytes = channels * bytesPerSample;
    private static final int bufferFrames = 1024;

    private final byte[] buffer = new byte[bufferFrames * frameBytes];
    private final float sampleRate;
    private final AudioEnhancementChain enhancementChain;

    private SourceDataLine line;
    private boolean available;
    private int writeIndex;
    private long appliedEnhancementChainVersion = Long.MIN_VALUE;

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
        } catch (LineUnavailableException | IllegalArgumentException exception) {
            available = false;
            line = null;
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

        line.write(buffer, 0, writeIndex);
        writeIndex = 0;
    }

    /**
     * Flushes and closes the host audio line.
     */
    public synchronized void Close() {
        if (!available) {
            return;
        }

        Flush();
        line.drain();
        line.stop();
        line.close();
        available = false;
        line = null;
    }

    /**
     * Drops any host audio queued for playback and clears the pending PCM buffer.
     */
    public synchronized void DiscardBufferedAudio() {
        writeIndex = 0;
        if (line != null) {
            line.flush();
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

        enhancementChain.SetPresets(Settings.CurrentAudioEnhancementChain());
        appliedEnhancementChainVersion = currentVersion;
    }

    @Deprecated public synchronized void writeSample(double left, double right) { WriteSample(left, right); }
    @Deprecated public synchronized void flush() { Flush(); }
    @Deprecated public synchronized void close() { Close(); }
    @Deprecated public synchronized void discardBufferedAudio() { DiscardBufferedAudio(); }
}
