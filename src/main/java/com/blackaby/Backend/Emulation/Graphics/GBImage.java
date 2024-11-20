package com.blackaby.Backend.Emulation.Graphics;

import com.blackaby.Backend.Emulation.Misc.Specifics;

/**
 * This class represents an image on the GameBoy screen.
 * It is a 2D array of GBColor objects.
 * Also has helper methods for manipulating the image.
 */
public class GBImage {
    private GBColor pixels[][];

    /**
     * Returns the GBColor at the specified coordinates
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @return GBColor at the specified coordinates
     */
    public GBImage() {
        pixels = new GBColor[Specifics.GB_DISPLAY_WIDTH][Specifics.GB_DISPLAY_HEIGHT];
    }

    /**
     * Sets a pixel at the specified coordinates
     * 
     * @param x     X coordinate
     * @param y     Y coordinate
     * @param color Color to set
     */
    public void setPixel(int x, int y, GBColor color) {
        if (x >= 0 && x < Specifics.GB_DISPLAY_WIDTH && y >= 0 && y < Specifics.GB_DISPLAY_HEIGHT) {
            pixels[x][y] = color;
        }
    }

    /**
     * Returns the GBColor at the specified coordinates
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @return GBColor at the specified coordinates
     */
    public GBColor getPixel(int x, int y) {
        if (x >= 0 && x < Specifics.GB_DISPLAY_WIDTH && y >= 0 && y < Specifics.GB_DISPLAY_HEIGHT) {
            return pixels[x][y];
        }
        return null;
    }
}
