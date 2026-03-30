package com.blackaby.Backend.GB.Misc;

/**
 * Hardware-wide constants for the original DMG model.
 */
public final class GBConstants {

    /** Game Boy display width in pixels. */
    public static final int gameBoyDisplayWidth = 160;

    /** Game Boy display height in pixels. */
    public static final int gameBoyDisplayHeight = 144;

    /** Nominal display refresh rate in frames per second. */
    public static final int refreshRate = 60;

    /** CPU clock rate in cycles per second. */
    public static final double cyclesPerSecond = 4_194_304;

    /** Length of one CPU cycle in nanoseconds. */
    public static final double nanosecondsPerCycle = 1_000_000_000.0 / cyclesPerSecond;

    private GBConstants() {
    }
}
