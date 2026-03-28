package com.blackaby.Backend.GB.Graphics;

import java.awt.Color;

/**
 * Represents one DMG palette colour as discrete RGB channels.
 */
public class GBColor {

    public int red;
    public int green;
    public int blue;

    /**
     * Creates a colour from separate RGB components.
     *
     * @param red red channel from 0 to 255
     * @param green green channel from 0 to 255
     * @param blue blue channel from 0 to 255
     */
    public GBColor(int red, int green, int blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    /**
     * Creates a colour from a `#RRGGBB` string.
     *
     * @param hex six-digit RGB colour value
     */
    public GBColor(String hex) {
        red = Integer.parseInt(hex.substring(1, 3), 16);
        green = Integer.parseInt(hex.substring(3, 5), 16);
        blue = Integer.parseInt(hex.substring(5, 7), 16);
    }

    /**
     * Returns the colour as an AWT `Color`.
     *
     * @return AWT colour
     */
    public Color ToColour() {
        return new Color(red, green, blue);
    }

    /**
     * Returns the colour packed as `0xAARRGGBB`.
     *
     * @return packed ARGB value
     */
    public int ToRgb() {
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /**
     * Returns the colour as a `#RRGGBB` string.
     *
     * @return hexadecimal colour string
     */
    public String ToHex() {
        return String.format("#%02x%02x%02x", red, green, blue);
    }

    @Override
    public String toString() {
        return "GBColor(" + red + ", " + green + ", " + blue + ")";
    }
}

