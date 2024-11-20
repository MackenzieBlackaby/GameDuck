package com.blackaby.Backend.Emulation.Graphics;

import java.awt.Color;

/**
 * This class represents a color on the GameBoy screen.
 * It has RGB values and a method to convert to a Color object.
 * This class is used by GBImage to represent pixels.
 */
public class GBColor {
    private int r;
    private int g;
    private int b;

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
     * Returns the red component of the color
     * 
     * @return Red component
     */
    public int getR() {
        return r;
    }

    /**
     * Returns the green component of the color
     * 
     * @return Green component
     */
    public int getG() {
        return g;
    }

    /**
     * Returns the blue component of the color
     * 
     * @return Blue component
     */
    public int getB() {
        return b;
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
