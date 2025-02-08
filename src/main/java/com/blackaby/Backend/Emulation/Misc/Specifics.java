package com.blackaby.Backend.Emulation.Misc;

/**
 * This class contains specific values for the GameBoy system.
 * These values are used by other classes to maintain consistency.
 */
public class Specifics {
    public static final int GB_DISPLAY_WIDTH = 160;
    public static final int GB_DISPLAY_HEIGHT = 144;
    public static final int REFRESH_RATE = 60;
    public static final int CLOCK_SPEED_HZ = 4194304;
    public static final double CYCLE_DELAY = (double) 1000 / CLOCK_SPEED_HZ;
    public static final double FRAME_DELAY = (double) 1000 / REFRESH_RATE;
}
