package com.blackaby.Backend.GB.CPU;

/**
 * Holds one sprite entry prepared for scanline rendering.
 * <p>
 * The class stores screen-space coordinates derived from OAM and provides small
 * helpers for reading the attribute bits used by the PPU.
 */
public class GBSprite {

    private static final int priorityMask = 0x80;
    private static final int yFlipMask = 0x40;
    private static final int xFlipMask = 0x20;
    private static final int paletteMask = 0x10;
    private static final int vramBankMask = 0x08;
    private static final int cgbPaletteMask = 0x07;

    public final int x;
    public final int y;
    public final int tileIndex;
    public final int attributes;

    /**
     * Creates a sprite snapshot from raw OAM data.
     *
     * @param y          sprite Y coordinate adjusted by the DMG OAM offset
     * @param x          sprite X coordinate adjusted by the DMG OAM offset
     * @param tileIndex  tile index used by the sprite
     * @param attributes raw OAM attribute byte
     */
    public GBSprite(int y, int x, int tileIndex, int attributes) {
        this.y = y;
        this.x = x;
        this.tileIndex = tileIndex;
        this.attributes = attributes;
    }

    /**
     * Returns whether the sprite should sit behind non-zero background pixels.
     *
     * @return `true` if background priority is enabled
     */
    public boolean IsPriorityInternal() {
        return (attributes & priorityMask) != 0;
    }

    /**
     * Returns whether the sprite is vertically flipped.
     *
     * @return `true` if Y flip is enabled
     */
    public boolean IsYFlip() {
        return (attributes & yFlipMask) != 0;
    }

    /**
     * Returns whether the sprite is horizontally flipped.
     *
     * @return `true` if X flip is enabled
     */
    public boolean IsXFlip() {
        return (attributes & xFlipMask) != 0;
    }

    /**
     * Returns whether the sprite uses OBP1 instead of OBP0.
     *
     * @return `true` if palette 1 is selected
     */
    public boolean UsesPalette1() {
        return (attributes & paletteMask) != 0;
    }

    /**
     * Returns the CGB object palette index.
     *
     * @return palette index from 0 to 7
     */
    public int CgbPaletteIndex() {
        return attributes & cgbPaletteMask;
    }

    /**
     * Returns the sprite tile VRAM bank in CGB mode.
     *
     * @return VRAM bank index
     */
    public int VramBank() {
        return (attributes & vramBankMask) != 0 ? 1 : 0;
    }

    @Override
    public String toString() {
        return String.format("Sprite[x=%d, y=%d, tile=%02X, attr=%02X]", x, y, tileIndex, attributes);
    }
}
