package com.blackaby.Backend.GB.Peripherals;

import com.blackaby.Backend.GB.Memory.DuckAddresses;
import com.blackaby.Backend.GB.Memory.DuckMemory;
import com.blackaby.Backend.GB.Misc.Specifics;
import com.blackaby.Misc.Settings;

import java.util.Arrays;

/**
 * DMG audio processing unit.
 * <p>
 * This is a practical implementation of the four standard Game Boy channels
 * with register semantics, frame sequencer timing, mixing, and host audio
 * output.
 * </p>
 */
public class DuckAPU {

    public record PulseChannelState(
            boolean enabled,
            boolean lengthEnabled,
            int lengthCounter,
            int nr0,
            int nr1,
            int nr2,
            int nr4,
            int duty,
            int frequency,
            int timer,
            int dutyPosition,
            int volume,
            int envelopeTimer,
            int envelopePeriod,
            boolean envelopeIncrease,
            boolean dacEnabled,
            int sweepTimer,
            int sweepPeriod,
            int sweepShift,
            boolean sweepNegate,
            boolean sweepEnabled,
            int shadowFrequency) implements java.io.Serializable {
    }

    public record WaveChannelState(
            boolean enabled,
            boolean lengthEnabled,
            int lengthCounter,
            int nr0,
            int nr2,
            int nr4,
            int frequency,
            int timer,
            int sampleIndex,
            boolean dacEnabled) implements java.io.Serializable {
    }

    public record NoiseChannelState(
            boolean enabled,
            boolean lengthEnabled,
            int lengthCounter,
            int nr2,
            int nr3,
            int nr4,
            int timer,
            int lfsr,
            int volume,
            int envelopeTimer,
            int envelopePeriod,
            boolean envelopeIncrease,
            boolean dacEnabled) implements java.io.Serializable {
    }

    public record ApuState(
            boolean powerEnabled,
            int nr50,
            int nr51,
            int nr52,
            int frameSequencerCounter,
            int frameSequencerStep,
            double sampleAccumulator,
            int[] waveRam,
            PulseChannelState channel1,
            PulseChannelState channel2,
            WaveChannelState channel3,
            NoiseChannelState channel4) implements java.io.Serializable {
    }

    private static final float outputSampleRate = 48_000.0f;
    private static final double cyclesPerSample = Specifics.cyclesPerSecond / outputSampleRate;
    private static final int frameSequencerPeriod = 8192;
    private static final int[] dutyPatterns = {
            0b00000001,
            0b10000001,
            0b10000111,
            0b01111110
    };
    private static final int[] noiseDivisors = { 8, 16, 32, 48, 64, 80, 96, 112 };

    private final DuckMemory memory;
    private final DuckAudioOutput audioOutput;
    private final int[] waveRam = new int[16];

    private final PulseChannel channel1 = new PulseChannel(true);
    private final PulseChannel channel2 = new PulseChannel(false);
    private final WaveChannel channel3 = new WaveChannel();
    private final NoiseChannel channel4 = new NoiseChannel();

    private boolean powerEnabled;
    private int nr50;
    private int nr51;
    private int nr52;

    private int frameSequencerCounter = frameSequencerPeriod;
    private int frameSequencerStep;
    private double sampleAccumulator;

    public DuckAPU(DuckMemory memory) {
        this.memory = memory;
        this.audioOutput = new DuckAudioOutput(outputSampleRate);
        PowerOff();
    }

    public void InitialiseDmgBootState() {
        powerEnabled = true;
        nr50 = 0x77;
        nr51 = 0xF3;
        nr52 = 0x80;
        frameSequencerCounter = frameSequencerPeriod;
        frameSequencerStep = 0;
        sampleAccumulator = 0.0;

        // When boot ROM execution is skipped, keep the boot register defaults but
        // do not start channel 1 already sounding. That avoids a loud startup tone
        // before the loaded ROM has a chance to initialise audio itself.
        channel1.InitialiseBootState(0x80, 0xBF, 0xF3, 0xFF, 0xBF, false);
        channel2.InitialiseBootState(0x00, 0x3F, 0x00, 0xFF, 0xBF, false);
        channel3.InitialiseBootState(0x7F, 0xFF, 0x9F, 0xFF, 0xBF);
        channel4.InitialiseBootState(0xFF, 0x00, 0x00, 0xBF);

        SyncMirrors();
    }

    public void Shutdown() {
        audioOutput.Close();
    }

    public void ResetAudioOutputState() {
        audioOutput.DiscardBufferedAudio();
    }

    public void Tick() {
        if (powerEnabled) {
            channel1.TickTimer();
            channel2.TickTimer();
            channel3.TickTimer();
            channel4.TickTimer();

            frameSequencerCounter--;
            if (frameSequencerCounter <= 0) {
                frameSequencerCounter += frameSequencerPeriod;
                StepFrameSequencer();
            }
        }

        sampleAccumulator += 1.0;
        while (sampleAccumulator >= cyclesPerSample) {
            sampleAccumulator -= cyclesPerSample;
            WriteMixedSample();
        }
    }

    public int Read(int address) {
        return switch (address) {
            case DuckAddresses.NR10 -> channel1.nr0 | 0x80;
            case DuckAddresses.NR11 -> channel1.nr1 | 0x3F;
            case DuckAddresses.NR12 -> channel1.nr2;
            case DuckAddresses.NR13 -> 0xFF;
            case DuckAddresses.NR14 -> channel1.nr4 | 0xBF;
            case DuckAddresses.NR21 -> channel2.nr1 | 0x3F;
            case DuckAddresses.NR22 -> channel2.nr2;
            case DuckAddresses.NR23 -> 0xFF;
            case DuckAddresses.NR24 -> channel2.nr4 | 0xBF;
            case DuckAddresses.NR30 -> channel3.nr0 | 0x7F;
            case DuckAddresses.NR31 -> 0xFF;
            case DuckAddresses.NR32 -> channel3.nr2 | 0x9F;
            case DuckAddresses.NR33 -> 0xFF;
            case DuckAddresses.NR34 -> channel3.nr4 | 0xBF;
            case DuckAddresses.NR41 -> 0xFF;
            case DuckAddresses.NR42 -> channel4.nr2;
            case DuckAddresses.NR43 -> channel4.nr3;
            case DuckAddresses.NR44 -> channel4.nr4 | 0xBF;
            case DuckAddresses.NR50 -> nr50;
            case DuckAddresses.NR51 -> nr51;
            case DuckAddresses.NR52 -> 0x70 | (powerEnabled ? 0x80 : 0x00) | StatusBits();
            default -> {
                if (address >= DuckAddresses.WAVE_PATTERN_START && address <= DuckAddresses.WAVE_PATTERN_END) {
                    yield waveRam[address - DuckAddresses.WAVE_PATTERN_START] & 0xFF;
                }
                yield 0xFF;
            }
        };
    }

    public void Write(int address, int value) {
        value &= 0xFF;

        if (address >= DuckAddresses.WAVE_PATTERN_START && address <= DuckAddresses.WAVE_PATTERN_END) {
            waveRam[address - DuckAddresses.WAVE_PATTERN_START] = value;
            memory.WriteDirect(address, value);
            return;
        }

        if (address == DuckAddresses.NR52) {
            if ((value & 0x80) == 0) {
                PowerOff();
            } else if (!powerEnabled) {
                PowerOn();
            }
            memory.WriteDirect(address, Read(address));
            return;
        }

        if (!powerEnabled) {
            return;
        }

        switch (address) {
            case DuckAddresses.NR10 -> channel1.WriteSweep(value);
            case DuckAddresses.NR11 -> channel1.WriteDutyLength(value);
            case DuckAddresses.NR12 -> channel1.WriteEnvelope(value);
            case DuckAddresses.NR13 -> channel1.WriteFrequencyLow(value);
            case DuckAddresses.NR14 -> channel1.WriteFrequencyHigh(value);
            case DuckAddresses.NR21 -> channel2.WriteDutyLength(value);
            case DuckAddresses.NR22 -> channel2.WriteEnvelope(value);
            case DuckAddresses.NR23 -> channel2.WriteFrequencyLow(value);
            case DuckAddresses.NR24 -> channel2.WriteFrequencyHigh(value);
            case DuckAddresses.NR30 -> channel3.WriteDacPower(value);
            case DuckAddresses.NR31 -> channel3.WriteLength(value);
            case DuckAddresses.NR32 -> channel3.WriteVolume(value);
            case DuckAddresses.NR33 -> channel3.WriteFrequencyLow(value);
            case DuckAddresses.NR34 -> channel3.WriteFrequencyHigh(value);
            case DuckAddresses.NR41 -> channel4.WriteLength(value);
            case DuckAddresses.NR42 -> channel4.WriteEnvelope(value);
            case DuckAddresses.NR43 -> channel4.WritePolynomial(value);
            case DuckAddresses.NR44 -> channel4.WriteControl(value);
            case DuckAddresses.NR50 -> nr50 = value;
            case DuckAddresses.NR51 -> nr51 = value;
            default -> {
                return;
            }
        }

        memory.WriteDirect(address, Read(address));
        memory.WriteDirect(DuckAddresses.NR52, Read(DuckAddresses.NR52));
    }

    /**
     * Captures the live APU register, channel, and waveform state.
     *
     * @return APU state snapshot
     */
    public synchronized ApuState CaptureState() {
        return new ApuState(
                powerEnabled,
                nr50,
                nr51,
                nr52,
                frameSequencerCounter,
                frameSequencerStep,
                sampleAccumulator,
                Arrays.copyOf(waveRam, waveRam.length),
                CapturePulseChannel(channel1),
                CapturePulseChannel(channel2),
                CaptureWaveChannel(channel3),
                CaptureNoiseChannel(channel4));
    }

    /**
     * Restores the live APU register, channel, and waveform state.
     *
     * @param state APU snapshot to restore
     */
    public synchronized void RestoreState(ApuState state) {
        if (state == null) {
            throw new IllegalArgumentException("An APU quick state is required.");
        }
        if (state.waveRam() == null || state.waveRam().length != waveRam.length) {
            throw new IllegalArgumentException("The quick state wave RAM is invalid.");
        }

        powerEnabled = state.powerEnabled();
        nr50 = state.nr50() & 0xFF;
        nr51 = state.nr51() & 0xFF;
        nr52 = state.nr52() & 0xFF;
        frameSequencerCounter = Math.max(1, state.frameSequencerCounter());
        frameSequencerStep = state.frameSequencerStep() & 0x07;
        sampleAccumulator = Math.max(0.0, state.sampleAccumulator());
        System.arraycopy(state.waveRam(), 0, waveRam, 0, waveRam.length);

        RestorePulseChannel(channel1, state.channel1());
        RestorePulseChannel(channel2, state.channel2());
        RestoreWaveChannel(channel3, state.channel3());
        RestoreNoiseChannel(channel4, state.channel4());

        audioOutput.DiscardBufferedAudio();
        SyncMirrors();
    }

    private void PowerOn() {
        powerEnabled = true;
        nr50 = 0;
        nr51 = 0;
        nr52 = 0x80;
        frameSequencerCounter = frameSequencerPeriod;
        frameSequencerStep = 0;

        channel1.Reset();
        channel2.Reset();
        channel3.Reset();
        channel4.Reset();

        SyncMirrors();
    }

    private void PowerOff() {
        powerEnabled = false;
        nr50 = 0;
        nr51 = 0;
        nr52 = 0;
        frameSequencerCounter = frameSequencerPeriod;
        frameSequencerStep = 0;
        sampleAccumulator = 0.0;

        channel1.Reset();
        channel2.Reset();
        channel3.Reset();
        channel4.Reset();

        for (int address = DuckAddresses.NR10; address <= DuckAddresses.NR51; address++) {
            memory.WriteDirect(address, 0x00);
        }
        memory.WriteDirect(DuckAddresses.NR52, Read(DuckAddresses.NR52));
        for (int address = DuckAddresses.WAVE_PATTERN_START; address <= DuckAddresses.WAVE_PATTERN_END; address++) {
            memory.WriteDirect(address, waveRam[address - DuckAddresses.WAVE_PATTERN_START]);
        }
    }

    private void SyncMirrors() {
        for (int address = DuckAddresses.NR10; address <= DuckAddresses.NR52; address++) {
            if (address == DuckAddresses.NR15 || address == DuckAddresses.NR1F) {
                continue;
            }
            memory.WriteDirect(address, Read(address));
        }
        for (int address = DuckAddresses.WAVE_PATTERN_START; address <= DuckAddresses.WAVE_PATTERN_END; address++) {
            memory.WriteDirect(address, waveRam[address - DuckAddresses.WAVE_PATTERN_START]);
        }
    }

    private int StatusBits() {
        int status = 0;
        if (channel1.IsActive()) {
            status |= 0x01;
        }
        if (channel2.IsActive()) {
            status |= 0x02;
        }
        if (channel3.IsActive()) {
            status |= 0x04;
        }
        if (channel4.IsActive()) {
            status |= 0x08;
        }
        return status;
    }

    private void StepFrameSequencer() {
        switch (frameSequencerStep) {
            case 0, 2, 4, 6 -> {
                channel1.StepLength();
                channel2.StepLength();
                channel3.StepLength();
                channel4.StepLength();
            }
            default -> {
            }
        }

        if (frameSequencerStep == 2 || frameSequencerStep == 6) {
            channel1.StepSweep();
        }

        if (frameSequencerStep == 7) {
            channel1.StepEnvelope();
            channel2.StepEnvelope();
            channel4.StepEnvelope();
        }

        frameSequencerStep = (frameSequencerStep + 1) & 0x07;
    }

    private void WriteMixedSample() {
        double leftOutput = 0.0;
        double rightOutput = 0.0;

        if (powerEnabled && Settings.soundEnabled && Settings.masterVolume > 0) {
            boolean[] muted = Settings.channelMuted;
            int[] volumes = Settings.channelVolume;

            double channel1Sample = ApplyChannelSettings(channel1.Sample(), muted[0], volumes[0]);
            double channel2Sample = ApplyChannelSettings(channel2.Sample(), muted[1], volumes[1]);
            double channel3Sample = ApplyChannelSettings(channel3.Sample(), muted[2], volumes[2]);
            double channel4Sample = ApplyChannelSettings(channel4.Sample(), muted[3], volumes[3]);

            int leftRouteMask = (nr51 >>> 4) & 0x0F;
            int rightRouteMask = nr51 & 0x0F;

            double leftMix = MixChannels(leftRouteMask, channel1Sample, channel2Sample, channel3Sample, channel4Sample);
            double rightMix = MixChannels(rightRouteMask, channel1Sample, channel2Sample, channel3Sample, channel4Sample);

            double masterVolume = Settings.masterVolume / 100.0;
            double leftVolume = TerminalOutputScale((nr50 >>> 4) & 0x07);
            double rightVolume = TerminalOutputScale(nr50 & 0x07);

            leftOutput = (leftMix / 4.0) * leftVolume * masterVolume * 0.30;
            rightOutput = (rightMix / 4.0) * rightVolume * masterVolume * 0.30;
        }

        audioOutput.WriteSample(leftOutput, rightOutput);
    }

    private double ApplyChannelSettings(double sample, boolean muted, int volume) {
        if (muted || volume <= 0) {
            return 0.0;
        }
        return sample * (volume / 100.0);
    }

    private double MixChannels(int routeMask, double channel1Sample, double channel2Sample,
            double channel3Sample, double channel4Sample) {
        double mix = 0.0;
        if ((routeMask & 0x01) != 0) {
            mix += channel1Sample;
        }
        if ((routeMask & 0x02) != 0) {
            mix += channel2Sample;
        }
        if ((routeMask & 0x04) != 0) {
            mix += channel3Sample;
        }
        if ((routeMask & 0x08) != 0) {
            mix += channel4Sample;
        }
        return mix;
    }

    static double TerminalOutputScale(int terminalVolume) {
        int clampedVolume = Math.max(0, Math.min(7, terminalVolume));
        // NR50 scales each terminal from 1/8 to 8/8; zero is the quietest step,
        // not a hard mute.
        return (clampedVolume + 1) / 8.0;
    }

    static double WaveSampleAmplitude(int sample, int volumeCode) {
        int clampedSample = Math.max(0, Math.min(15, sample));
        // Channel 3 attenuation needs to preserve the wave's midpoint; shifting
        // the already-centred waveform avoids pinning quieter settings negative.
        double centredSample = (clampedSample - 7.5) / 7.5;
        return switch (volumeCode & 0x03) {
            case 0 -> 0.0;
            case 1 -> centredSample;
            case 2 -> centredSample * 0.5;
            case 3 -> centredSample * 0.25;
            default -> 0.0;
        };
    }

    private PulseChannelState CapturePulseChannel(PulseChannel channel) {
        return new PulseChannelState(
                channel.enabled,
                channel.lengthEnabled,
                channel.lengthCounter,
                channel.nr0,
                channel.nr1,
                channel.nr2,
                channel.nr4,
                channel.duty,
                channel.frequency,
                channel.timer,
                channel.dutyPosition,
                channel.volume,
                channel.envelopeTimer,
                channel.envelopePeriod,
                channel.envelopeIncrease,
                channel.dacEnabled,
                channel.sweepTimer,
                channel.sweepPeriod,
                channel.sweepShift,
                channel.sweepNegate,
                channel.sweepEnabled,
                channel.shadowFrequency);
    }

    private void RestorePulseChannel(PulseChannel channel, PulseChannelState state) {
        if (state == null) {
            throw new IllegalArgumentException("The quick state pulse channel is invalid.");
        }

        channel.enabled = state.enabled();
        channel.lengthEnabled = state.lengthEnabled();
        channel.lengthCounter = Math.max(0, state.lengthCounter());
        channel.nr0 = state.nr0() & 0x7F;
        channel.nr1 = state.nr1() & 0xC0;
        channel.nr2 = state.nr2() & 0xFF;
        channel.nr4 = state.nr4() & 0x47;
        channel.duty = state.duty() & 0x03;
        channel.frequency = state.frequency() & 0x07FF;
        channel.timer = Math.max(1, state.timer());
        channel.dutyPosition = state.dutyPosition() & 0x07;
        channel.volume = Math.max(0, Math.min(15, state.volume()));
        channel.envelopeTimer = Math.max(0, state.envelopeTimer());
        channel.envelopePeriod = state.envelopePeriod() & 0x07;
        channel.envelopeIncrease = state.envelopeIncrease();
        channel.dacEnabled = state.dacEnabled();
        channel.sweepTimer = Math.max(0, state.sweepTimer());
        channel.sweepPeriod = state.sweepPeriod() & 0x07;
        channel.sweepShift = state.sweepShift() & 0x07;
        channel.sweepNegate = state.sweepNegate();
        channel.sweepEnabled = state.sweepEnabled();
        channel.shadowFrequency = state.shadowFrequency() & 0x07FF;
    }

    private WaveChannelState CaptureWaveChannel(WaveChannel channel) {
        return new WaveChannelState(
                channel.enabled,
                channel.lengthEnabled,
                channel.lengthCounter,
                channel.nr0,
                channel.nr2,
                channel.nr4,
                channel.frequency,
                channel.timer,
                channel.sampleIndex,
                channel.dacEnabled);
    }

    private void RestoreWaveChannel(WaveChannel channel, WaveChannelState state) {
        if (state == null) {
            throw new IllegalArgumentException("The quick state wave channel is invalid.");
        }

        channel.enabled = state.enabled();
        channel.lengthEnabled = state.lengthEnabled();
        channel.lengthCounter = Math.max(0, state.lengthCounter());
        channel.nr0 = state.nr0() & 0x80;
        channel.nr2 = state.nr2() & 0x60;
        channel.nr4 = state.nr4() & 0x47;
        channel.frequency = state.frequency() & 0x07FF;
        channel.timer = Math.max(1, state.timer());
        channel.sampleIndex = state.sampleIndex() & 0x1F;
        channel.dacEnabled = state.dacEnabled();
    }

    private NoiseChannelState CaptureNoiseChannel(NoiseChannel channel) {
        return new NoiseChannelState(
                channel.enabled,
                channel.lengthEnabled,
                channel.lengthCounter,
                channel.nr2,
                channel.nr3,
                channel.nr4,
                channel.timer,
                channel.lfsr,
                channel.volume,
                channel.envelopeTimer,
                channel.envelopePeriod,
                channel.envelopeIncrease,
                channel.dacEnabled);
    }

    private void RestoreNoiseChannel(NoiseChannel channel, NoiseChannelState state) {
        if (state == null) {
            throw new IllegalArgumentException("The quick state noise channel is invalid.");
        }

        channel.enabled = state.enabled();
        channel.lengthEnabled = state.lengthEnabled();
        channel.lengthCounter = Math.max(0, state.lengthCounter());
        channel.nr2 = state.nr2() & 0xFF;
        channel.nr3 = state.nr3() & 0xFF;
        channel.nr4 = state.nr4() & 0x40;
        channel.timer = Math.max(1, state.timer());
        channel.lfsr = state.lfsr() & 0x7FFF;
        channel.volume = Math.max(0, Math.min(15, state.volume()));
        channel.envelopeTimer = Math.max(0, state.envelopeTimer());
        channel.envelopePeriod = state.envelopePeriod() & 0x07;
        channel.envelopeIncrease = state.envelopeIncrease();
        channel.dacEnabled = state.dacEnabled();
    }


    private abstract static class BaseChannel {
        protected boolean enabled;
        protected boolean lengthEnabled;
        protected int lengthCounter;

        public boolean IsActive() {
            return enabled && IsDacEnabled();
        }

        protected void Disable() {
            enabled = false;
        }

        protected void StepLength() {
            if (lengthEnabled && lengthCounter > 0) {
                lengthCounter--;
                if (lengthCounter == 0) {
                    Disable();
                }
            }
        }

        protected abstract boolean IsDacEnabled();
    }

    private static class PulseChannel extends BaseChannel {
        private final boolean withSweep;

        private int nr0;
        private int nr1;
        private int nr2;
        private int nr4;

        private int duty;
        private int frequency;
        private int timer;
        private int dutyPosition;

        private int volume;
        private int envelopeTimer;
        private int envelopePeriod;
        private boolean envelopeIncrease;
        private boolean dacEnabled;

        private int sweepTimer;
        private int sweepPeriod;
        private int sweepShift;
        private boolean sweepNegate;
        private boolean sweepEnabled;
        private int shadowFrequency;

        private PulseChannel(boolean withSweep) {
            this.withSweep = withSweep;
            Reset();
        }

        public void InitialiseBootState(int nr0, int nr1, int nr2, int frequencyLow, int nr4, boolean enabled) {
            Reset();
            this.nr0 = nr0 & 0x7F;
            this.nr1 = nr1 & 0xC0;
            this.nr2 = nr2;
            this.nr4 = nr4 & 0x47;
            this.enabled = enabled;
            duty = (nr1 >>> 6) & 0x03;
            lengthCounter = 64 - (nr1 & 0x3F);
            volume = (nr2 >>> 4) & 0x0F;
            envelopePeriod = nr2 & 0x07;
            envelopeIncrease = (nr2 & 0x08) != 0;
            dacEnabled = (nr2 & 0xF8) != 0;
            frequency = ((nr4 & 0x07) << 8) | frequencyLow;
            lengthEnabled = (nr4 & 0x40) != 0;
            ReloadTimer();

            if (withSweep) {
                sweepPeriod = (nr0 >>> 4) & 0x07;
                sweepNegate = (nr0 & 0x08) != 0;
                sweepShift = nr0 & 0x07;
                shadowFrequency = frequency;
                sweepTimer = sweepPeriod == 0 ? 8 : sweepPeriod;
                sweepEnabled = sweepPeriod != 0 || sweepShift != 0;
            }
        }

        public void Reset() {
            nr0 = 0;
            nr1 = 0;
            nr2 = 0;
            nr4 = 0;
            duty = 0;
            frequency = 0;
            timer = 4;
            dutyPosition = 0;
            volume = 0;
            envelopeTimer = 0;
            envelopePeriod = 0;
            envelopeIncrease = false;
            dacEnabled = false;
            sweepTimer = 0;
            sweepPeriod = 0;
            sweepShift = 0;
            sweepNegate = false;
            sweepEnabled = false;
            shadowFrequency = 0;
            enabled = false;
            lengthEnabled = false;
            lengthCounter = 0;
        }

        public void WriteSweep(int value) {
            if (!withSweep) {
                return;
            }
            nr0 = value & 0x7F;
            sweepPeriod = (nr0 >>> 4) & 0x07;
            sweepNegate = (nr0 & 0x08) != 0;
            sweepShift = nr0 & 0x07;
        }

        public void WriteDutyLength(int value) {
            nr1 = value & 0xC0;
            duty = (value >>> 6) & 0x03;
            lengthCounter = 64 - (value & 0x3F);
            if (lengthCounter == 0) {
                lengthCounter = 64;
            }
        }

        public void WriteEnvelope(int value) {
            nr2 = value;
            dacEnabled = (value & 0xF8) != 0;
            envelopePeriod = value & 0x07;
            envelopeIncrease = (value & 0x08) != 0;
            if (!dacEnabled) {
                enabled = false;
            }
        }

        public void WriteFrequencyLow(int value) {
            frequency = (frequency & 0x0700) | value;
        }

        public void WriteFrequencyHigh(int value) {
            nr4 = value & 0x47;
            frequency = (frequency & 0x00FF) | ((value & 0x07) << 8);
            lengthEnabled = (value & 0x40) != 0;
            if ((value & 0x80) != 0) {
                Trigger();
            }
        }

        public void TickTimer() {
            if (--timer <= 0) {
                ReloadTimer();
                dutyPosition = (dutyPosition + 1) & 0x07;
            }
        }

        public void StepEnvelope() {
            if (envelopePeriod == 0) {
                return;
            }

            if (--envelopeTimer <= 0) {
                envelopeTimer = envelopePeriod;
                if (envelopeIncrease && volume < 15) {
                    volume++;
                } else if (!envelopeIncrease && volume > 0) {
                    volume--;
                }
            }
        }

        public void StepSweep() {
            if (!withSweep || !sweepEnabled) {
                return;
            }

            if (--sweepTimer <= 0) {
                sweepTimer = sweepPeriod == 0 ? 8 : sweepPeriod;
                int newFrequency = CalculateSweepFrequency();
                if (newFrequency > 2047) {
                    enabled = false;
                    return;
                }

                if (sweepShift != 0) {
                    shadowFrequency = newFrequency;
                    frequency = newFrequency;
                    ReloadTimer();

                    int secondCheck = CalculateSweepFrequency();
                    if (secondCheck > 2047) {
                        enabled = false;
                    }
                }
            }
        }

        public double Sample() {
            if (!enabled || !dacEnabled) {
                return 0.0;
            }

            int pattern = dutyPatterns[duty];
            int bit = (pattern >>> (7 - dutyPosition)) & 0x01;
            double level = bit == 0 ? -1.0 : 1.0;
            return level * (volume / 15.0);
        }

        @Override
        protected boolean IsDacEnabled() {
            return dacEnabled;
        }

        private void Trigger() {
            if (!dacEnabled) {
                enabled = false;
                return;
            }

            enabled = true;
            if (lengthCounter == 0) {
                lengthCounter = 64;
            }
            ReloadTimer();
            dutyPosition = 0;
            volume = (nr2 >>> 4) & 0x0F;
            envelopeTimer = envelopePeriod == 0 ? 8 : envelopePeriod;

            if (withSweep) {
                shadowFrequency = frequency;
                sweepTimer = sweepPeriod == 0 ? 8 : sweepPeriod;
                sweepEnabled = sweepPeriod != 0 || sweepShift != 0;
                if (sweepShift != 0 && CalculateSweepFrequency() > 2047) {
                    enabled = false;
                }
            }
        }

        private int CalculateSweepFrequency() {
            int delta = shadowFrequency >> sweepShift;
            return sweepNegate ? shadowFrequency - delta : shadowFrequency + delta;
        }

        private void ReloadTimer() {
            timer = Math.max(1, (2048 - frequency) * 4);
        }
    }

    private class WaveChannel extends BaseChannel {
        private int nr0;
        private int nr2;
        private int nr4;

        private int frequency;
        private int timer;
        private int sampleIndex;
        private boolean dacEnabled;

        public void InitialiseBootState(int nr0, int nr1, int nr2, int frequencyLow, int nr4) {
            Reset();
            this.nr0 = nr0 & 0x80;
            this.nr2 = nr2 & 0x60;
            this.nr4 = nr4 & 0x47;
            dacEnabled = (nr0 & 0x80) != 0;
            lengthCounter = 256 - (nr1 & 0xFF);
            if (lengthCounter == 0) {
                lengthCounter = 256;
            }
            frequency = ((nr4 & 0x07) << 8) | frequencyLow;
            lengthEnabled = (nr4 & 0x40) != 0;
            ReloadTimer();
        }

        public void Reset() {
            nr0 = 0;
            nr2 = 0;
            nr4 = 0;
            frequency = 0;
            timer = 2;
            sampleIndex = 0;
            dacEnabled = false;
            enabled = false;
            lengthEnabled = false;
            lengthCounter = 0;
        }

        public void WriteDacPower(int value) {
            nr0 = value & 0x80;
            dacEnabled = (value & 0x80) != 0;
            if (!dacEnabled) {
                enabled = false;
            }
        }

        public void WriteLength(int value) {
            lengthCounter = 256 - (value & 0xFF);
            if (lengthCounter == 0) {
                lengthCounter = 256;
            }
        }

        public void WriteVolume(int value) {
            nr2 = value & 0x60;
        }

        public void WriteFrequencyLow(int value) {
            frequency = (frequency & 0x0700) | value;
        }

        public void WriteFrequencyHigh(int value) {
            nr4 = value & 0x47;
            frequency = (frequency & 0x00FF) | ((value & 0x07) << 8);
            lengthEnabled = (value & 0x40) != 0;
            if ((value & 0x80) != 0) {
                Trigger();
            }
        }

        public void TickTimer() {
            if (--timer <= 0) {
                ReloadTimer();
                sampleIndex = (sampleIndex + 1) & 0x1F;
            }
        }

        public double Sample() {
            if (!enabled || !dacEnabled) {
                return 0.0;
            }

            int sampleByte = waveRam[sampleIndex >>> 1] & 0xFF;
            int sample = ((sampleIndex & 1) == 0) ? (sampleByte >>> 4) : (sampleByte & 0x0F);
            int volumeCode = (nr2 >>> 5) & 0x03;
            return WaveSampleAmplitude(sample, volumeCode);
        }

        @Override
        protected boolean IsDacEnabled() {
            return dacEnabled;
        }

        private void Trigger() {
            if (!dacEnabled) {
                enabled = false;
                return;
            }

            enabled = true;
            if (lengthCounter == 0) {
                lengthCounter = 256;
            }
            sampleIndex = 0;
            ReloadTimer();
        }

        private void ReloadTimer() {
            timer = Math.max(1, (2048 - frequency) * 2);
        }
    }

    private static class NoiseChannel extends BaseChannel {
        private int nr2;
        private int nr3;
        private int nr4;

        private int timer;
        private int lfsr;
        private int volume;
        private int envelopeTimer;
        private int envelopePeriod;
        private boolean envelopeIncrease;
        private boolean dacEnabled;

        public void InitialiseBootState(int nr1, int nr2, int nr3, int nr4) {
            Reset();
            this.nr2 = nr2;
            this.nr3 = nr3;
            this.nr4 = nr4 & 0x40;
            lengthCounter = 64 - (nr1 & 0x3F);
            if (lengthCounter == 0) {
                lengthCounter = 64;
            }
            volume = (nr2 >>> 4) & 0x0F;
            envelopePeriod = nr2 & 0x07;
            envelopeIncrease = (nr2 & 0x08) != 0;
            dacEnabled = (nr2 & 0xF8) != 0;
            ReloadTimer();
        }

        public void Reset() {
            nr2 = 0;
            nr3 = 0;
            nr4 = 0;
            timer = 8;
            lfsr = 0x7FFF;
            volume = 0;
            envelopeTimer = 0;
            envelopePeriod = 0;
            envelopeIncrease = false;
            dacEnabled = false;
            enabled = false;
            lengthEnabled = false;
            lengthCounter = 0;
        }

        public void WriteLength(int value) {
            lengthCounter = 64 - (value & 0x3F);
            if (lengthCounter == 0) {
                lengthCounter = 64;
            }
        }

        public void WriteEnvelope(int value) {
            nr2 = value;
            volume = (value >>> 4) & 0x0F;
            envelopePeriod = value & 0x07;
            envelopeIncrease = (value & 0x08) != 0;
            dacEnabled = (value & 0xF8) != 0;
            if (!dacEnabled) {
                enabled = false;
            }
        }

        public void WritePolynomial(int value) {
            nr3 = value;
            ReloadTimer();
        }

        public void WriteControl(int value) {
            nr4 = value & 0x40;
            lengthEnabled = (value & 0x40) != 0;
            if ((value & 0x80) != 0) {
                Trigger();
            }
        }

        public void TickTimer() {
            if (--timer <= 0) {
                ReloadTimer();
                int xor = (lfsr & 0x01) ^ ((lfsr >>> 1) & 0x01);
                lfsr = (lfsr >>> 1) | (xor << 14);
                if ((nr3 & 0x08) != 0) {
                    lfsr = (lfsr & ~(1 << 6)) | (xor << 6);
                }
            }
        }

        public void StepEnvelope() {
            if (envelopePeriod == 0) {
                return;
            }

            if (--envelopeTimer <= 0) {
                envelopeTimer = envelopePeriod;
                if (envelopeIncrease && volume < 15) {
                    volume++;
                } else if (!envelopeIncrease && volume > 0) {
                    volume--;
                }
            }
        }

        public double Sample() {
            if (!enabled || !dacEnabled) {
                return 0.0;
            }
            double level = (lfsr & 0x01) == 0 ? 1.0 : -1.0;
            return level * (volume / 15.0);
        }

        @Override
        protected boolean IsDacEnabled() {
            return dacEnabled;
        }

        private void Trigger() {
            if (!dacEnabled) {
                enabled = false;
                return;
            }

            enabled = true;
            if (lengthCounter == 0) {
                lengthCounter = 64;
            }
            lfsr = 0x7FFF;
            envelopeTimer = envelopePeriod == 0 ? 8 : envelopePeriod;
            volume = (nr2 >>> 4) & 0x0F;
            ReloadTimer();
        }

        private void ReloadTimer() {
            int divisor = noiseDivisors[nr3 & 0x07];
            int clockShift = (nr3 >>> 4) & 0x0F;
            timer = Math.max(1, divisor << clockShift);
        }
    }
}

