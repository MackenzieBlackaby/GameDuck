package com.blackaby.OldBackEnd.Emulation.CPU;

/**
 * Simple sprite class for helping with sprite rendering.
 */
public class DuckSprite {

    // The x-coordinate of the sprite on the screen.
    public int x;
    // The y-coordinate of the sprite on the screen.
    public int y;
    // The index of the tile to use for this sprite.
    public int tileIndex;
    // Additional attributes for the sprite, such as flags.
    public int attributes;

    /**
     * Constructs a new sprite with the specified parameters.
     *
     * @param x          The x-coordinate of the sprite.
     * @param y          The y-coordinate of the sprite.
     * @param tileIndex  The index of the tile to use for this sprite.
     * @param attributes Additional attributes for the sprite (e.g., flags).
     */
    public DuckSprite(int x, int y, int tileIndex, int attributes) {
        this.x = x;
        this.y = y;
        this.tileIndex = tileIndex;
        this.attributes = attributes;
    }
}
