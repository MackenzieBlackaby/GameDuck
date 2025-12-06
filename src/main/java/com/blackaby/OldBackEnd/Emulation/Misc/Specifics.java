package com.blackaby.OldBackEnd.Emulation.Misc;

/**
 * This class contains specific values for the GameBoy system.
 * These values are used by other classes to maintain consistency.
 */
public class Specifics {
    public static final int GB_DISPLAY_WIDTH = 160;
    public static final int GB_DISPLAY_HEIGHT = 144;
    public static final int REFRESH_RATE = 60;
    public static final double CPS = 4194304;
    public static final double US_PER_CYCLE = 1_000_000_000.0 / CPS;
}
