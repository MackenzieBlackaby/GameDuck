package com.blackaby.OldBackEnd.Emulation.Graphics;

import java.awt.Color;

/**
 * This class represents a color on the GameBoy screen.
 * It has RGB values and a method to convert to a Color object.
 * This class is used by GBImage to represent pixels.
 */
public class GBColor {
    public int r;
    public int g;
    public int b;

    /**
     * Creates a new GBColor with the specified RGB values
     * 
     * @param r Red component
     * @param g Green component
     * @param b Blue component
     */
    public GBColor(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    /**
     * Creates a new GBColor with the specified hex string
     * 
     * @param hex Hex string
     */
    public GBColor(String hex) {
        this.r = Integer.parseInt(hex.substring(1, 3), 16);
        this.g = Integer.parseInt(hex.substring(3, 5), 16);
        this.b = Integer.parseInt(hex.substring(5, 7), 16);
    }

    /**
     * Converts the GBColor to a Color object
     * 
     * @return Color object
     */
    public Color toColor() {
        return new Color(r, g, b);
    }

    /**
     * Returns a string representation of the color
     * 
     * @return String representation
     */
    public String toString() {
        return "GBColor(" + r + ", " + g + ", " + b + ")";
    }

    /**
     * Returns a hex representation of the color
     * 
     * @return Hex representation
     */
    public String toHex() {
        return String.format("#%02x%02x%02x", r, g, b);
    }
}
