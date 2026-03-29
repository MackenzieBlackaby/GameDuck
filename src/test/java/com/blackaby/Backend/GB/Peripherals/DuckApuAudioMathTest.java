package com.blackaby.Backend.GB.Peripherals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuckApuAudioMathTest {

    private static final double epsilon = 0.0000001;

    @Test
    void nr50TerminalScaleNeverFullyMutesAnAudibleSignal() {
        assertEquals(0.125, DuckAPU.TerminalOutputScale(0), epsilon);
        assertEquals(0.5, DuckAPU.TerminalOutputScale(3), epsilon);
        assertEquals(1.0, DuckAPU.TerminalOutputScale(7), epsilon);
    }

    @Test
    void waveVolumeCodeZeroProducesSilence() {
        assertEquals(0.0, DuckAPU.WaveSampleAmplitude(0, 0), epsilon);
        assertEquals(0.0, DuckAPU.WaveSampleAmplitude(8, 0), epsilon);
        assertEquals(0.0, DuckAPU.WaveSampleAmplitude(15, 0), epsilon);
    }

    @Test
    void waveVolumeAttenuationScalesAroundTheWaveMidpoint() {
        assertEquals(1.0, DuckAPU.WaveSampleAmplitude(15, 1), epsilon);
        assertEquals(0.5, DuckAPU.WaveSampleAmplitude(15, 2), epsilon);
        assertEquals(0.25, DuckAPU.WaveSampleAmplitude(15, 3), epsilon);
        assertEquals(-1.0, DuckAPU.WaveSampleAmplitude(0, 1), epsilon);
        assertEquals(-0.5, DuckAPU.WaveSampleAmplitude(0, 2), epsilon);
        assertEquals(-0.25, DuckAPU.WaveSampleAmplitude(0, 3), epsilon);
    }

    @Test
    void waveMidpointRemainsCentredInsteadOfBiasingNegative() {
        double lowMidpoint = DuckAPU.WaveSampleAmplitude(7, 1);
        double highMidpoint = DuckAPU.WaveSampleAmplitude(8, 1);

        assertEquals(0.0, lowMidpoint + highMidpoint, epsilon);
    }
}
