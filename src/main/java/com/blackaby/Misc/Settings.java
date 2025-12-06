package com.blackaby.Misc;

import com.blackaby.OldBackEnd.Emulation.Graphics.GBColor;

/**
 * This class contains the default colors for the GameBoy screen.
 * It also contains methods to reset the colors to their default values.
 * This class is used by the PaletteManager to set the colors of the GameBoy
 * screen.
 */
public class Settings {
    public static final String GB_COLOR_0 = "#E0F8D0";
    public static final String GB_COLOR_1 = "#88C070";
    public static final String GB_COLOR_2 = "#346856";
    public static final String GB_COLOR_3 = "#081820";

    public static GBColor GB_COLOR_0_OBJ = new GBColor(GB_COLOR_0);
    public static GBColor GB_COLOR_1_OBJ = new GBColor(GB_COLOR_1);
    public static GBColor GB_COLOR_2_OBJ = new GBColor(GB_COLOR_2);
    public static GBColor GB_COLOR_3_OBJ = new GBColor(GB_COLOR_3);

    public static void reset() {
        GB_COLOR_0_OBJ = new GBColor(GB_COLOR_0);
        GB_COLOR_1_OBJ = new GBColor(GB_COLOR_1);
        GB_COLOR_2_OBJ = new GBColor(GB_COLOR_2);
        GB_COLOR_3_OBJ = new GBColor(GB_COLOR_3);
    }
}
