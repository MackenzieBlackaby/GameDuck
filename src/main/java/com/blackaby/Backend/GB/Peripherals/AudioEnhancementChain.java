package com.blackaby.Backend.GB.Peripherals;

import com.blackaby.Misc.AudioEnhancementPreset;
import com.blackaby.Misc.AudioEnhancementSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a configurable sequence of host-side audio enhancements.
 */
public final class AudioEnhancementChain {
    private static final double silenceThreshold = 0.0005;
    private static final double wetMixFloor = 0.0001;

    /**
     * Mutable stereo frame reused during processing to avoid per-sample allocation.
     */
    public static final class StereoFrame {
        private double left;
        private double right;

        public double Left() {
            return left;
        }

        public double Right() {
            return right;
        }
    }

    private interface Processor {
        void Process(StereoFrame frame);

        default void ResetState() {
        }
    }

    private final float sampleRate;
    private final StereoFrame workingFrame = new StereoFrame();
    private final int silenceHoldSamples;
    private final double wetMixFadeStep;
    private List<Processor> processors = List.of();
    private int consecutiveSilentSamples;
    private double wetPathMix = 1.0;
    private boolean processorsAtRest;

    public AudioEnhancementChain(float sampleRate) {
        this.sampleRate = sampleRate;
        this.silenceHoldSamples = Math.max(1, Math.round(sampleRate * 0.060f));
        int fadeSamples = Math.max(1, Math.round(sampleRate * 0.030f));
        this.wetMixFadeStep = 1.0 / fadeSamples;
    }

    /**
     * Rebuilds the processor chain from the selected presets.
     *
     * @param presets active preset order
     */
    public void SetPresets(List<AudioEnhancementSetting> presets) {
        List<Processor> rebuilt = new ArrayList<>();
        for (AudioEnhancementSetting setting : presets == null ? List.<AudioEnhancementSetting>of() : presets) {
            if (setting == null || setting.intensityPercent() <= 0) {
                continue;
            }
            rebuilt.add(ProcessorsFor(setting));
        }
        processors = List.copyOf(rebuilt);
        ResetState();
    }

    /**
     * Processes one stereo frame through the active enhancement chain.
     *
     * @param left input left sample
     * @param right input right sample
     * @return processed frame reference
     */
    public StereoFrame Process(double left, double right) {
        UpdateSilenceState(left, right);

        workingFrame.left = left;
        workingFrame.right = right;

        if (processors.isEmpty() || processorsAtRest || wetPathMix <= wetMixFloor) {
            return workingFrame;
        }

        for (Processor processor : processors) {
            processor.Process(workingFrame);
            workingFrame.left = ClampSample(workingFrame.left);
            workingFrame.right = ClampSample(workingFrame.right);
        }

        workingFrame.left = ClampSample(left + ((workingFrame.left - left) * wetPathMix));
        workingFrame.right = ClampSample(right + ((workingFrame.right - right) * wetPathMix));
        return workingFrame;
    }

    /**
     * Clears any retained effect state so the next audible sample starts cleanly.
     */
    public void ResetState() {
        consecutiveSilentSamples = 0;
        wetPathMix = 1.0;
        processorsAtRest = false;
        ResetProcessors();
    }

    private Processor ProcessorsFor(AudioEnhancementSetting setting) {
        List<Processor> processorsForPreset = switch (setting.preset()) {
            case SOFT_LOW_PASS -> List.of(new OnePoleLowPassProcessor(
                    Interpolate(setting.primaryPercent(), 1_600.0, 5_600.0),
                    sampleRate));
            case POCKET_SPEAKER -> List.of(
                    new OnePoleHighPassProcessor(Interpolate(setting.primaryPercent(), 80.0, 280.0), sampleRate),
                    new OnePoleLowPassProcessor(Interpolate(setting.primaryPercent(), 3_000.0, 1_800.0), sampleRate),
                    new SoftClipProcessor(Interpolate(setting.secondaryPercent(), 1.0, 1.4)));
            case SOFT_CLIP -> List.of(new SoftClipProcessor(Interpolate(setting.primaryPercent(), 1.1, 2.0)));
            case STEREO_WIDEN -> List.of(new StereoWidthProcessor(Interpolate(setting.primaryPercent(), 0.8, 1.6)));
            case ROOM_REVERB -> List.of(new RoomReverbProcessor(
                    sampleRate,
                    Interpolate(setting.primaryPercent(), 0.12, 0.24),
                    Interpolate(setting.primaryPercent(), 0.18, 0.30),
                    0.08,
                    Interpolate(setting.secondaryPercent(), 2_400.0, 6_000.0)));
            case SHIMMER_CHORUS -> List.of(
                    new ChorusProcessor(
                            sampleRate,
                            11.0,
                            Interpolate(setting.primaryPercent(), 2.0, 6.0),
                            Interpolate(setting.primaryPercent(), 0.16, 0.28),
                            0.04,
                            Interpolate(setting.secondaryPercent(), 0.12, 0.32)),
                    new StereoWidthProcessor(Interpolate(setting.secondaryPercent(), 1.0, 1.16)));
            case DUB_ECHO -> List.of(
                    new PingPongDelayProcessor(
                            sampleRate,
                            Interpolate(setting.secondaryPercent(), 0.16, 0.33),
                            Interpolate(setting.primaryPercent(), 0.22, 0.54),
                            Interpolate(setting.primaryPercent(), 0.28, 0.60),
                            Interpolate(setting.secondaryPercent(), 1_600.0, 3_600.0)),
                    new OnePoleLowPassProcessor(5_200.0, sampleRate));
            case UNDERWATER -> List.of(
                    new OnePoleLowPassProcessor(Interpolate(setting.primaryPercent(), 500.0, 1_340.0), sampleRate),
                    new ChorusProcessor(
                            sampleRate,
                            Interpolate(setting.secondaryPercent(), 10.0, 18.0),
                            Interpolate(setting.secondaryPercent(), 3.0, 9.0),
                            Interpolate(setting.secondaryPercent(), 0.26, 0.50),
                            Interpolate(setting.secondaryPercent(), 0.02, 0.14),
                            Interpolate(setting.secondaryPercent(), 0.08, 0.28)),
                    new StereoWidthProcessor(Interpolate(setting.secondaryPercent(), 1.0, 0.76)),
                    new SoftClipProcessor(Interpolate(setting.primaryPercent(), 0.9, 1.3)));
        };
        return new IntensityProcessor(processorsForPreset, setting.intensityPercent() / 100.0);
    }

    private static double Interpolate(int percent, double minimum, double maximum) {
        return minimum + ((maximum - minimum) * (Math.max(0, Math.min(100, percent)) / 100.0));
    }

    private void UpdateSilenceState(double left, double right) {
        double peak = Math.max(Math.abs(left), Math.abs(right));
        if (peak > silenceThreshold) {
            consecutiveSilentSamples = 0;
            wetPathMix = 1.0;
            processorsAtRest = false;
            return;
        }

        consecutiveSilentSamples++;
        if (processorsAtRest || consecutiveSilentSamples <= silenceHoldSamples) {
            return;
        }

        wetPathMix = Math.max(0.0, wetPathMix - wetMixFadeStep);
        if (wetPathMix > wetMixFloor) {
            return;
        }

        wetPathMix = 0.0;
        ResetProcessors();
        processorsAtRest = true;
    }

    private void ResetProcessors() {
        for (Processor processor : processors) {
            processor.ResetState();
        }
    }

    private static final class IntensityProcessor implements Processor {
        private final List<Processor> processors;
        private final double mix;

        private IntensityProcessor(List<Processor> processors, double mix) {
            this.processors = processors;
            this.mix = Math.max(0.0, Math.min(1.0, mix));
        }

        @Override
        public void Process(StereoFrame frame) {
            double dryLeft = frame.left;
            double dryRight = frame.right;

            for (Processor processor : processors) {
                processor.Process(frame);
                frame.left = ClampSample(frame.left);
                frame.right = ClampSample(frame.right);
            }

            frame.left = ClampSample(dryLeft + ((frame.left - dryLeft) * mix));
            frame.right = ClampSample(dryRight + ((frame.right - dryRight) * mix));
        }

        @Override
        public void ResetState() {
            for (Processor processor : processors) {
                processor.ResetState();
            }
        }
    }

    private static final class OnePoleLowPassProcessor implements Processor {
        private final double alpha;
        private double leftState;
        private double rightState;

        private OnePoleLowPassProcessor(double cutoffHz, float sampleRate) {
            double dt = 1.0 / sampleRate;
            double rc = 1.0 / (2.0 * Math.PI * cutoffHz);
            alpha = dt / (rc + dt);
        }

        @Override
        public void Process(StereoFrame frame) {
            leftState += alpha * (frame.left - leftState);
            rightState += alpha * (frame.right - rightState);
            frame.left = leftState;
            frame.right = rightState;
        }

        @Override
        public void ResetState() {
            leftState = 0.0;
            rightState = 0.0;
        }
    }

    private static final class OnePoleHighPassProcessor implements Processor {
        private final double alpha;
        private double previousInputLeft;
        private double previousInputRight;
        private double previousOutputLeft;
        private double previousOutputRight;

        private OnePoleHighPassProcessor(double cutoffHz, float sampleRate) {
            double dt = 1.0 / sampleRate;
            double rc = 1.0 / (2.0 * Math.PI * cutoffHz);
            alpha = rc / (rc + dt);
        }

        @Override
        public void Process(StereoFrame frame) {
            double currentLeft = frame.left;
            double currentRight = frame.right;

            double filteredLeft = alpha * (previousOutputLeft + currentLeft - previousInputLeft);
            double filteredRight = alpha * (previousOutputRight + currentRight - previousInputRight);

            previousInputLeft = currentLeft;
            previousInputRight = currentRight;
            previousOutputLeft = filteredLeft;
            previousOutputRight = filteredRight;

            frame.left = filteredLeft;
            frame.right = filteredRight;
        }

        @Override
        public void ResetState() {
            previousInputLeft = 0.0;
            previousInputRight = 0.0;
            previousOutputLeft = 0.0;
            previousOutputRight = 0.0;
        }
    }

    private static final class SoftClipProcessor implements Processor {
        private final double drive;
        private final double scale;

        private SoftClipProcessor(double drive) {
            this.drive = drive;
            this.scale = Math.tanh(drive);
        }

        @Override
        public void Process(StereoFrame frame) {
            frame.left = Math.tanh(frame.left * drive) / scale;
            frame.right = Math.tanh(frame.right * drive) / scale;
        }
    }

    private static final class StereoWidthProcessor implements Processor {
        private final double width;

        private StereoWidthProcessor(double width) {
            this.width = width;
        }

        @Override
        public void Process(StereoFrame frame) {
            double mid = (frame.left + frame.right) * 0.5;
            double side = (frame.left - frame.right) * 0.5 * width;
            frame.left = mid + side;
            frame.right = mid - side;
        }
    }

    private static final class ChorusProcessor implements Processor {
        private final double[] leftBuffer;
        private final double[] rightBuffer;
        private final double baseDelaySamples;
        private final double depthSamples;
        private final double wet;
        private final double feedback;
        private final double phaseIncrement;
        private int writeIndex;
        private double phase;

        private ChorusProcessor(float sampleRate, double baseDelayMs, double depthMs, double wet, double feedback,
                double rateHz) {
            this.baseDelaySamples = MillisecondsToSamples(sampleRate, baseDelayMs);
            this.depthSamples = MillisecondsToSamples(sampleRate, depthMs);
            this.wet = wet;
            this.feedback = feedback;
            this.phaseIncrement = (Math.PI * 2.0 * rateHz) / sampleRate;

            int bufferSize = Math.max(8, (int) Math.ceil(baseDelaySamples + depthSamples + 3.0));
            this.leftBuffer = new double[bufferSize];
            this.rightBuffer = new double[bufferSize];
        }

        @Override
        public void Process(StereoFrame frame) {
            double dryLeft = frame.left;
            double dryRight = frame.right;

            double modulatedLeftDelay = baseDelaySamples + depthSamples * ((Math.sin(phase) + 1.0) * 0.5);
            double modulatedRightDelay = baseDelaySamples
                    + depthSamples * ((Math.sin(phase + (Math.PI * 0.5)) + 1.0) * 0.5);

            double delayedLeft = ReadInterpolated(leftBuffer, writeIndex, modulatedLeftDelay);
            double delayedRight = ReadInterpolated(rightBuffer, writeIndex, modulatedRightDelay);

            leftBuffer[writeIndex] = ClampSample(dryLeft + delayedLeft * feedback);
            rightBuffer[writeIndex] = ClampSample(dryRight + delayedRight * feedback);

            writeIndex = (writeIndex + 1) % leftBuffer.length;
            phase += phaseIncrement;
            if (phase >= Math.PI * 2.0) {
                phase -= Math.PI * 2.0;
            }

            frame.left = ClampSample(dryLeft * (1.0 - wet) + delayedLeft * wet);
            frame.right = ClampSample(dryRight * (1.0 - wet) + delayedRight * wet);
        }

        @Override
        public void ResetState() {
            java.util.Arrays.fill(leftBuffer, 0.0);
            java.util.Arrays.fill(rightBuffer, 0.0);
            writeIndex = 0;
            phase = 0.0;
        }
    }

    private static final class PingPongDelayProcessor implements Processor {
        private final double[] leftBuffer;
        private final double[] rightBuffer;
        private final double wet;
        private final double feedback;
        private final double dampingAlpha;
        private int writeIndex;
        private double leftFeedbackState;
        private double rightFeedbackState;

        private PingPongDelayProcessor(float sampleRate, double delaySeconds, double wet, double feedback,
                double dampingCutoffHz) {
            int delaySamples = Math.max(2, (int) Math.round(delaySeconds * sampleRate));
            this.leftBuffer = new double[delaySamples];
            this.rightBuffer = new double[delaySamples];
            this.wet = wet;
            this.feedback = feedback;
            this.dampingAlpha = LowPassAlpha(dampingCutoffHz, sampleRate);
        }

        @Override
        public void Process(StereoFrame frame) {
            double dryLeft = frame.left;
            double dryRight = frame.right;
            double delayedLeft = leftBuffer[writeIndex];
            double delayedRight = rightBuffer[writeIndex];

            leftFeedbackState += dampingAlpha * ((dryLeft + delayedRight * feedback) - leftFeedbackState);
            rightFeedbackState += dampingAlpha * ((dryRight + delayedLeft * feedback) - rightFeedbackState);
            leftFeedbackState = ClampSample(leftFeedbackState);
            rightFeedbackState = ClampSample(rightFeedbackState);

            leftBuffer[writeIndex] = leftFeedbackState;
            rightBuffer[writeIndex] = rightFeedbackState;
            writeIndex = (writeIndex + 1) % leftBuffer.length;

            frame.left = ClampSample(dryLeft * (1.0 - wet) + delayedLeft * wet);
            frame.right = ClampSample(dryRight * (1.0 - wet) + delayedRight * wet);
        }

        @Override
        public void ResetState() {
            java.util.Arrays.fill(leftBuffer, 0.0);
            java.util.Arrays.fill(rightBuffer, 0.0);
            writeIndex = 0;
            leftFeedbackState = 0.0;
            rightFeedbackState = 0.0;
        }
    }

    private static final class RoomReverbProcessor implements Processor {
        private final double[] leftBuffer;
        private final double[] rightBuffer;
        private final int[] leftTapOffsets;
        private final int[] rightTapOffsets;
        private final double[] tapGains = { 0.46, 0.27, 0.17, 0.10 };
        private final double wet;
        private final double feedback;
        private final double crossfeed;
        private final double dampingAlpha;
        private int writeIndex;
        private double leftStoreState;
        private double rightStoreState;

        private RoomReverbProcessor(float sampleRate, double wet, double feedback, double crossfeed,
                double dampingCutoffHz) {
            this.leftTapOffsets = new int[] {
                    Math.max(1, (int) Math.round(sampleRate * 0.013)),
                    Math.max(1, (int) Math.round(sampleRate * 0.021)),
                    Math.max(1, (int) Math.round(sampleRate * 0.031)),
                    Math.max(1, (int) Math.round(sampleRate * 0.043))
            };
            this.rightTapOffsets = new int[] {
                    Math.max(1, (int) Math.round(sampleRate * 0.017)),
                    Math.max(1, (int) Math.round(sampleRate * 0.027)),
                    Math.max(1, (int) Math.round(sampleRate * 0.037)),
                    Math.max(1, (int) Math.round(sampleRate * 0.051))
            };
            int bufferSize = Math.max(leftTapOffsets[leftTapOffsets.length - 1], rightTapOffsets[rightTapOffsets.length - 1]) + 2;
            this.leftBuffer = new double[bufferSize];
            this.rightBuffer = new double[bufferSize];
            this.wet = wet;
            this.feedback = feedback;
            this.crossfeed = crossfeed;
            this.dampingAlpha = LowPassAlpha(dampingCutoffHz, sampleRate);
        }

        @Override
        public void Process(StereoFrame frame) {
            double dryLeft = frame.left;
            double dryRight = frame.right;

            double wetLeft = ClampSample(ReadTapped(leftBuffer, writeIndex, leftTapOffsets, tapGains)
                    + ReadTapped(rightBuffer, writeIndex, rightTapOffsets, tapGains) * 0.18);
            double wetRight = ClampSample(ReadTapped(rightBuffer, writeIndex, rightTapOffsets, tapGains)
                    + ReadTapped(leftBuffer, writeIndex, leftTapOffsets, tapGains) * 0.18);

            leftStoreState += dampingAlpha * ((dryLeft + wetLeft * feedback + wetRight * crossfeed) - leftStoreState);
            rightStoreState += dampingAlpha * ((dryRight + wetRight * feedback + wetLeft * crossfeed) - rightStoreState);
            leftStoreState = ClampSample(leftStoreState);
            rightStoreState = ClampSample(rightStoreState);

            leftBuffer[writeIndex] = leftStoreState;
            rightBuffer[writeIndex] = rightStoreState;
            writeIndex = (writeIndex + 1) % leftBuffer.length;

            frame.left = ClampSample(dryLeft * (1.0 - wet) + wetLeft * wet);
            frame.right = ClampSample(dryRight * (1.0 - wet) + wetRight * wet);
        }

        @Override
        public void ResetState() {
            java.util.Arrays.fill(leftBuffer, 0.0);
            java.util.Arrays.fill(rightBuffer, 0.0);
            writeIndex = 0;
            leftStoreState = 0.0;
            rightStoreState = 0.0;
        }
    }

    private static double MillisecondsToSamples(float sampleRate, double milliseconds) {
        return (milliseconds / 1_000.0) * sampleRate;
    }

    private static double ReadInterpolated(double[] buffer, int writeIndex, double delaySamples) {
        double readPosition = writeIndex - delaySamples;
        while (readPosition < 0.0) {
            readPosition += buffer.length;
        }

        int index0 = (int) Math.floor(readPosition);
        int index1 = (index0 + 1) % buffer.length;
        double fraction = readPosition - index0;
        return buffer[index0] * (1.0 - fraction) + buffer[index1] * fraction;
    }

    private static double ReadTapped(double[] buffer, int writeIndex, int[] tapOffsets, double[] tapGains) {
        double result = 0.0;
        for (int index = 0; index < tapOffsets.length && index < tapGains.length; index++) {
            int readIndex = writeIndex - tapOffsets[index];
            while (readIndex < 0) {
                readIndex += buffer.length;
            }
            result += buffer[readIndex] * tapGains[index];
        }
        return result;
    }

    private static double LowPassAlpha(double cutoffHz, float sampleRate) {
        double dt = 1.0 / sampleRate;
        double rc = 1.0 / (2.0 * Math.PI * cutoffHz);
        return dt / (rc + dt);
    }

    private static double ClampSample(double sample) {
        return Math.max(-1.0, Math.min(1.0, sample));
    }
}

