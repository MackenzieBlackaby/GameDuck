package com.blackaby.Backend.Emulation.CPU;

/**
 * Represents a simplified sprite object for the rendering pipeline.
 * <p>
 * This class captures the state of a sprite for a specific scanline,
 * including pre-calculated screen coordinates and helper methods
 * for decoding the attribute byte (OAM flags).
 * </p>
 */
public class DuckSprite {

    // --- Attribute Bit Definitions ---
    private static final int MASK_PRIORITY = 0x80; // Bit 7: BG and Window over OBJ
    private static final int MASK_Y_FLIP = 0x40; // Bit 6: Y flip
    private static final int MASK_X_FLIP = 0x20; // Bit 5: X flip
    private static final int MASK_PALETTE = 0x10; // Bit 4: Palette number (0=OBP0, 1=OBP1)

    // Public final fields: These are data carriers that should not change once
    // created.
    public final int x;
    public final int y;
    public final int tileIndex;
    public final int attributes;

    /**
     * Constructs a new sprite.
     *
     * @param y          The Y-coordinate on the screen (Raw OAM Y - 16).
     * @param x          The X-coordinate on the screen (Raw OAM X - 8).
     * @param tileIndex  The tile index.
     * @param attributes The raw attribute byte.
     */
    public DuckSprite(int y, int x, int tileIndex, int attributes) {
        this.y = y;
        this.x = x;
        this.tileIndex = tileIndex;
        this.attributes = attributes;
    }

    // --- Helper Methods (Cleaner PPU Logic) ---

    /**
     * @return True if the sprite should be rendered behind the background/window
     *         (unless the BG/Win pixel is transparent).
     */
    public boolean isPriorityInternal() {
        return (attributes & MASK_PRIORITY) != 0;
    }

    /**
     * @return True if the sprite should be flipped vertically.
     */
    public boolean isYFlip() {
        return (attributes & MASK_Y_FLIP) != 0;
    }

    /**
     * @return True if the sprite should be flipped horizontally.
     */
    public boolean isXFlip() {
        return (attributes & MASK_X_FLIP) != 0;
    }

    /**
     * @return True if the sprite uses Object Palette 1 (OBP1), False for OBP0.
     */
    public boolean usePalette1() {
        return (attributes & MASK_PALETTE) != 0;
    }

    @Override
    public String toString() {
        return String.format("Sprite[x=%d, y=%d, tile=%02X, attr=%02X]", x, y, tileIndex, attributes);
    }
}