package com.blackaby.Backend.Emulation.Peripherals;

import com.blackaby.Misc.AudioEnhancementPreset;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a configurable sequence of host-side audio enhancements.
 */
public final class AudioEnhancementChain {

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
    }

    private final float sampleRate;
    private final StereoFrame workingFrame = new StereoFrame();
    private List<Processor> processors = List.of();

    public AudioEnhancementChain(float sampleRate) {
        this.sampleRate = sampleRate;
    }

    /**
     * Rebuilds the processor chain from the selected presets.
     *
     * @param presets active preset order
     */
    public void SetPresets(List<AudioEnhancementPreset> presets) {
        List<Processor> rebuilt = new ArrayList<>();
        for (AudioEnhancementPreset preset : presets == null ? List.<AudioEnhancementPreset>of() : presets) {
            if (preset == null) {
                continue;
            }
            rebuilt.addAll(ProcessorsFor(preset));
        }
        processors = List.copyOf(rebuilt);
    }

    /**
     * Processes one stereo frame through the active enhancement chain.
     *
     * @param left input left sample
     * @param right input right sample
     * @return processed frame reference
     */
    public StereoFrame Process(double left, double right) {
        workingFrame.left = left;
        workingFrame.right = right;

        for (Processor processor : processors) {
            processor.Process(workingFrame);
        }

        return workingFrame;
    }

    private List<Processor> ProcessorsFor(AudioEnhancementPreset preset) {
        return switch (preset) {
            case SOFT_LOW_PASS -> List.of(new OnePoleLowPassProcessor(3_600.0, sampleRate));
            case POCKET_SPEAKER -> List.of(
                    new OnePoleHighPassProcessor(180.0, sampleRate),
                    new OnePoleLowPassProcessor(2_400.0, sampleRate),
                    new SoftClipProcessor(1.20));
            case SOFT_CLIP -> List.of(new SoftClipProcessor(1.55));
            case STEREO_WIDEN -> List.of(new StereoWidthProcessor(1.20));
            case ROOM_REVERB -> List.of(new RoomReverbProcessor(sampleRate, 0.26, 0.32, 0.12, 4_200.0));
            case SHIMMER_CHORUS -> List.of(
                    new ChorusProcessor(sampleRate, 11.0, 5.0, 0.32, 0.10, 0.27),
                    new StereoWidthProcessor(1.12));
            case BITCRUSH -> List.of(
                    new BitCrusherProcessor(4, 18),
                    new SoftClipProcessor(1.18));
            case DUB_ECHO -> List.of(
                    new PingPongDelayProcessor(sampleRate, 0.245, 0.38, 0.44, 2_600.0),
                    new OnePoleLowPassProcessor(5_200.0, sampleRate));
            case UNDERWATER -> List.of(
                    new OnePoleLowPassProcessor(920.0, sampleRate),
                    new ChorusProcessor(sampleRate, 14.0, 6.0, 0.38, 0.08, 0.18),
                    new StereoWidthProcessor(0.88),
                    new SoftClipProcessor(1.10));
        };
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

    private static final class BitCrusherProcessor implements Processor {
        private final int holdSamples;
        private final double quantisationSteps;
        private int holdCounter;
        private double heldLeft;
        private double heldRight;

        private BitCrusherProcessor(int holdSamples, int quantisationSteps) {
            this.holdSamples = Math.max(1, holdSamples);
            this.quantisationSteps = Math.max(2, quantisationSteps);
        }

        @Override
        public void Process(StereoFrame frame) {
            if (holdCounter <= 0) {
                heldLeft = Quantise(frame.left);
                heldRight = Quantise(frame.right);
                holdCounter = holdSamples;
            }

            holdCounter--;
            frame.left = heldLeft;
            frame.right = heldRight;
        }

        private double Quantise(double sample) {
            return Math.round(sample * quantisationSteps) / quantisationSteps;
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

            leftBuffer[writeIndex] = dryLeft + delayedLeft * feedback;
            rightBuffer[writeIndex] = dryRight + delayedRight * feedback;

            writeIndex = (writeIndex + 1) % leftBuffer.length;
            phase += phaseIncrement;
            if (phase >= Math.PI * 2.0) {
                phase -= Math.PI * 2.0;
            }

            frame.left = dryLeft * (1.0 - wet) + delayedLeft * wet;
            frame.right = dryRight * (1.0 - wet) + delayedRight * wet;
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

            leftBuffer[writeIndex] = leftFeedbackState;
            rightBuffer[writeIndex] = rightFeedbackState;
            writeIndex = (writeIndex + 1) % leftBuffer.length;

            frame.left = dryLeft * (1.0 - wet) + delayedLeft * wet;
            frame.right = dryRight * (1.0 - wet) + delayedRight * wet;
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

            double wetLeft = ReadTapped(leftBuffer, writeIndex, leftTapOffsets, tapGains)
                    + ReadTapped(rightBuffer, writeIndex, rightTapOffsets, tapGains) * 0.18;
            double wetRight = ReadTapped(rightBuffer, writeIndex, rightTapOffsets, tapGains)
                    + ReadTapped(leftBuffer, writeIndex, leftTapOffsets, tapGains) * 0.18;

            leftStoreState += dampingAlpha * ((dryLeft + wetLeft * feedback + wetRight * crossfeed) - leftStoreState);
            rightStoreState += dampingAlpha * ((dryRight + wetRight * feedback + wetLeft * crossfeed) - rightStoreState);

            leftBuffer[writeIndex] = leftStoreState;
            rightBuffer[writeIndex] = rightStoreState;
            writeIndex = (writeIndex + 1) % leftBuffer.length;

            frame.left = dryLeft * (1.0 - wet) + wetLeft * wet;
            frame.right = dryRight * (1.0 - wet) + wetRight * wet;
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
}
