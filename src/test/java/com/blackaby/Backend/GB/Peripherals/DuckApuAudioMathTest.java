package com.blackaby.Backend.GB.Peripherals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuckApuAudioMathTest {

    private static final double epsilon = 0.0000001;

    @Test
    void nr50TerminalScaleNeverFullyMutesAnAudibleSignal() {
        assertEquals(0.125, GBAudioProcessingUnit.TerminalOutputScale(0), epsilon);
        assertEquals(0.5, GBAudioProcessingUnit.TerminalOutputScale(3), epsilon);
        assertEquals(1.0, GBAudioProcessingUnit.TerminalOutputScale(7), epsilon);
    }

    @Test
    void waveVolumeCodeZeroProducesSilence() {
        assertEquals(0.0, GBAudioProcessingUnit.WaveSampleAmplitude(0, 0), epsilon);
        assertEquals(0.0, GBAudioProcessingUnit.WaveSampleAmplitude(8, 0), epsilon);
        assertEquals(0.0, GBAudioProcessingUnit.WaveSampleAmplitude(15, 0), epsilon);
    }

    @Test
    void waveVolumeAttenuationScalesAroundTheWaveMidpoint() {
        assertEquals(1.0, GBAudioProcessingUnit.WaveSampleAmplitude(15, 1), epsilon);
        assertEquals(0.5, GBAudioProcessingUnit.WaveSampleAmplitude(15, 2), epsilon);
        assertEquals(0.25, GBAudioProcessingUnit.WaveSampleAmplitude(15, 3), epsilon);
        assertEquals(-1.0, GBAudioProcessingUnit.WaveSampleAmplitude(0, 1), epsilon);
        assertEquals(-0.5, GBAudioProcessingUnit.WaveSampleAmplitude(0, 2), epsilon);
        assertEquals(-0.25, GBAudioProcessingUnit.WaveSampleAmplitude(0, 3), epsilon);
    }

    @Test
    void waveMidpointRemainsCentredInsteadOfBiasingNegative() {
        double lowMidpoint = GBAudioProcessingUnit.WaveSampleAmplitude(7, 1);
        double highMidpoint = GBAudioProcessingUnit.WaveSampleAmplitude(8, 1);

        assertEquals(0.0, lowMidpoint + highMidpoint, epsilon);
    }
}
