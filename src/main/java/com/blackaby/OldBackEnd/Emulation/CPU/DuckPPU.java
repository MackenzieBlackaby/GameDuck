package com.blackaby.OldBackEnd.Emulation.CPU;

import java.util.ArrayList;
import java.util.List;

import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Misc.Settings;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Interrupt;
import com.blackaby.OldBackEnd.Emulation.Graphics.GBColor;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;
import com.blackaby.OldBackEnd.Emulation.Misc.Specifics;
import com.blackaby.Frontend.DebugLogger;

/**
 * Emulates the Pixel Processing Unit (PPU) of the Game Boy.
 * <p>
 * The PPU is responsible for rendering the screen by progressing through
 * timed scanline phases (OAM, VRAM, HBLANK, and VBLANK). It interacts
 * with video memory to fetch background tiles and writes pixels to the
 * display buffer. It also handles STAT and VBLANK interrupt generation.
 * </p>
 * <p>
 * Currently supports background rendering only. Window and sprite rendering
 * can be added later.
 * </p>
 */
public class DuckPPU {

    /** Duration (in cycles) of the OAM scanline phase. */
    public static final int OAM_DURATION = 80;

    /** Duration (in cycles) of the VRAM scanline phase. */
    public static final int VRAM_DURATION = 172;

    /** Duration (in cycles) of the HBLANK scanline phase. */
    public static final int HBLANK_DURATION = 204;

    /** Total number of cycles per scanline. */
    private static final int SCANLINE_CYCLES = 456;

    /**
     * Represents the current mode of the PPU.
     * Each mode corresponds to a specific phase in the LCD scanline timing:
     * - OAM: Scanline is fetching sprite data
     * - VRAM: Scanline is fetching tile data
     * - HBLANK: Horizontal blanking after scanline render
     * - VBLANK: Vertical blanking after all visible scanlines
     */
    private enum PPUMode {
        HBLANK,
        VBLANK,
        OAM,
        VRAM
    }

    private DuckCPU cpu;
    private DuckMemory memory;
    private DuckDisplay display;
    private PPUMode mode;
    private int scanline;
    private int cycle;

    /**
     * Constructor for the DuckPPU class.
     *
     * @param cpu     the DuckCPU object
     * @param memory  the DuckMemory object
     * @param display the DuckDisplay object
     */
    public DuckPPU(DuckCPU cpu, DuckMemory memory, DuckDisplay display) {
        this.cpu = cpu;
        this.memory = memory;
        this.display = display;
        this.scanline = 0;
        this.cycle = 0;
        this.mode = PPUMode.OAM;

    }

    /**
     * Sets the PPU mode and updates STAT register accordingly.
     */
    private void setMode(PPUMode mode) {
        this.mode = mode;
        int stat = memory.read(0xFF41) & 0xFF;

        stat = (stat & 0xFC) | mode.ordinal();
        memory.write(0xFF41, (byte) stat);

        if (mode != PPUMode.VRAM) { // VRAM mode never generates STAT interrupts
            int enableBit = 3 + mode.ordinal(); // HBLANK=3, VBLANK=4, OAM=5
            if ((stat & (1 << enableBit)) != 0) {
                cpu.requestInterrupt(Interrupt.LCD_STAT);
            }
        }
    }

    /**
     * Steps the PPU by one cycle, advancing mode/scanline as needed.
     */
    public void step() {
        int lcdc = memory.read(DuckMemory.LCDC) & 0xFF;

        // LCD disabled: freeze timing + reset LY/mode
        if ((lcdc & 0x80) == 0) {
            scanline = 0;
            cycle = 0;
            mode = PPUMode.HBLANK;
            memory.write(DuckMemory.LY, 0);
            return;
        }
        cycle++;
        switch (mode) {
            case OAM:
                if (cycle >= OAM_DURATION) {
                    cycle = 0;
                    setMode(PPUMode.VRAM);
                }
                break;
            case VRAM:
                if (cycle >= VRAM_DURATION) {
                    cycle = 0;
                    setMode(PPUMode.HBLANK);
                    renderScanline(scanline);
                }
                break;
            case HBLANK:
                if (cycle >= HBLANK_DURATION) {
                    cycle = 0;
                    scanline++;
                    if (scanline == 144) {
                        setMode(PPUMode.VBLANK);
                        cpu.requestInterrupt(Interrupt.VBLANK);
                    } else {
                        setMode(PPUMode.OAM);
                    }
                }
                break;
            case VBLANK:
                if (cycle >= SCANLINE_CYCLES) {
                    cycle = 0;
                    scanline++;
                    if (scanline >= 154) {
                        scanline = 0;
                        setMode(PPUMode.OAM);
                    }
                    display.repaint();
                }
                break;
        }
        memory.write(DuckMemory.LY, (byte) scanline);
        updateLYCCompare();
    }

    /**
     * Renders the background layer of the specified scanline.
     * (You could also add sprite rendering, window, etc.)
     */
    public void renderScanline(int scanline) {
        int lcdc = memory.read(0xFF40) & 0xFF;

        if ((lcdc & 0x01) == 0) {
            GBColor defaultColor = new GBColor(255, 255, 255);
            for (int x = 0; x < Specifics.GB_DISPLAY_WIDTH; x++) {
                display.setPixel(x, scanline, defaultColor.toColor(), false);
            }
        }

        // Get scroll offsets.
        int scrollY = memory.read(0xFF42) & 0xFF;
        int scrollX = memory.read(0xFF43) & 0xFF;

        // World position in the BG map:
        int worldY = (scanline + scrollY) & 0xFF;

        // Determine base addresses for tile map and tile data.
        int tileMapBase = ((lcdc & 0x08) != 0) ? 0x9C00 : 0x9800;
        boolean use8000 = ((lcdc & 0x10) != 0);
        int tileDataBase = use8000 ? 0x8000 : 0x9000;

        // BG palette.
        int bgPalette = memory.read(0xFF47) & 0xFF;

        // For each pixel in the scanline:
        for (int screenX = 0; screenX < Specifics.GB_DISPLAY_WIDTH; screenX++) {
            int worldX = (screenX + scrollX) & 0xFF;
            int tileColumn = worldX / 8;
            int tileRow = worldY / 8;

            // Find the tile ID in the BG map.
            int tileIndexAddr = tileMapBase + (tileRow * 32) + tileColumn;
            int tileID = memory.read(tileIndexAddr) & 0xFF;

            if (!use8000) {
                tileID = (byte) tileID;
            }

            // Each tile is 16 bytes, 2 bytes per row.
            int tileAddr = tileDataBase + (tileID * 16);
            int tileLine = worldY % 8;

            int byte1 = memory.read(tileAddr + (tileLine * 2)) & 0xFF;
            int byte2 = memory.read(tileAddr + (tileLine * 2 + 1)) & 0xFF;
            int bitIndex = 7 - (worldX % 8);

            int colorValue = (((byte2 >> bitIndex) & 1) << 1) |
                    ((byte1 >> bitIndex) & 1);

            // Convert that color index using the BG palette.
            int paletteShift = colorValue * 2;
            int paletteBits = (bgPalette >> paletteShift) & 0x03;

            GBColor pixelColor;
            switch (paletteBits) {
                case 0:
                    pixelColor = Settings.GB_COLOR_0_OBJ;
                    break;
                case 1:
                    pixelColor = Settings.GB_COLOR_1_OBJ;
                    break;
                case 2:
                    pixelColor = Settings.GB_COLOR_2_OBJ;
                    break;
                case 3:
                default:
                    pixelColor = Settings.GB_COLOR_3_OBJ;
                    break;
            }

            // Write the pixel to the display buffer.
            display.setPixel(screenX, scanline, pixelColor.toColor(), false);
        }

        if ((lcdc & 0x02) != 0) { // Bit 1: OBJ enable
            for (DuckSprite sprite : getSpritesOnScanline(scanline)) {
                drawSpritePixel(sprite, scanline);
            }
        }
    }

    /**
     * Compares LY with LYC and sets the STAT register’s coincidence flag and
     * interrupt.
     */
    private void updateLYCCompare() {
        int ly = memory.read(DuckMemory.LY) & 0xFF;
        int lyc = memory.read(0xFF45) & 0xFF;
        int stat = memory.read(0xFF41) & 0xFF;

        if (ly == lyc) {
            stat |= 0x04;
            if ((stat & 0x40) != 0) {
                cpu.requestInterrupt(DuckCPU.Interrupt.LCD_STAT);
            }
        } else {
            stat &= ~0x04;
        }
        memory.write(0xFF41, (byte) stat);
    }

    private List<DuckSprite> getSpritesOnScanline(int scanline) {
        List<DuckSprite> visible = new ArrayList<>();

        boolean is8x16 = (memory.read(0xFF40) & 0x04) != 0;

        for (int i = 0; i < 40; i++) {
            int index = i * 4;
            int y = (memory.read(0xFE00 + index) & 0xFF) - 16;
            int x = (memory.read(0xFE00 + index + 1) & 0xFF) - 8;
            int tileIndex = memory.read(0xFE00 + index + 2) & 0xFF;
            int attr = memory.read(0xFE00 + index + 3) & 0xFF;

            int height = is8x16 ? 16 : 8;
            if (scanline >= y && scanline < y + height) {
                visible.add(new DuckSprite(y, x, tileIndex, attr));
                if (visible.size() == 10)
                    break;
            }
        }

        return visible;
    }

    private void drawSpritePixel(DuckSprite sprite, int scanline) {
        int spriteHeight = ((memory.read(0xFF40) & 0x04) != 0) ? 16 : 8;
        int line = scanline - sprite.y;

        if ((sprite.attributes & (1 << 6)) != 0) {
            line = spriteHeight - 1 - line;
        }

        int tileIndex = sprite.tileIndex;
        if (spriteHeight == 16) {
            tileIndex &= 0xFE;
        }

        int addr = 0x8000 + (tileIndex * 16);
        int byte1 = memory.read(addr + line * 2) & 0xFF;
        int byte2 = memory.read(addr + line * 2 + 1) & 0xFF;

        for (int x = 0; x < 8; x++) {
            boolean xFlip = (sprite.attributes & (1 << 5)) != 0;
            int bit = xFlip ? x : 7 - x;
            int pixelX = sprite.x + (xFlip ? 7 - x : x);

            if (pixelX < 0 || pixelX >= Specifics.GB_DISPLAY_WIDTH)
                continue;

            int colorIndex = ((byte2 >> bit) & 1) << 1 | ((byte1 >> bit) & 1);
            if (colorIndex == 0)
                continue;

            int palette = (sprite.attributes & (1 << 4)) != 0
                    ? memory.read(0xFF49) & 0xFF
                    : memory.read(0xFF48) & 0xFF;
            int paletteBits = (palette >> (colorIndex * 2)) & 0x03;

            GBColor pixelColor;
            switch (paletteBits) {
                case 0:
                    pixelColor = Settings.GB_COLOR_0_OBJ;
                    break;
                case 1:
                    pixelColor = Settings.GB_COLOR_1_OBJ;
                    break;
                case 2:
                    pixelColor = Settings.GB_COLOR_2_OBJ;
                    break;
                case 3:
                default:
                    pixelColor = Settings.GB_COLOR_3_OBJ;
                    break;
            }

            boolean behindBG = (sprite.attributes & (1 << 7)) != 0;
            if (behindBG) {
                int lcdc = memory.read(0xFF40) & 0xFF;
                int scrollX = memory.read(0xFF43) & 0xFF;
                int scrollY = memory.read(0xFF42) & 0xFF;
                int bgX = (pixelX + scrollX) & 0xFF;
                int bgY = (scanline + scrollY) & 0xFF;
                int tileMapBase = ((lcdc & 0x08) != 0) ? 0x9C00 : 0x9800;
                boolean use8000 = (lcdc & 0x10) != 0;
                int tileDataBase = use8000 ? 0x8000 : 0x9000;

                int tileColumn = bgX / 8;
                int tileRow = bgY / 8;
                int tileIndexAddr = tileMapBase + tileRow * 32 + tileColumn;
                int tileID = memory.read(tileIndexAddr) & 0xFF;
                if (!use8000)
                    tileID = (byte) tileID;

                int tileAddr = tileDataBase + tileID * 16;
                int tileLine = bgY % 8;
                int bgByte1 = memory.read(tileAddr + tileLine * 2) & 0xFF;
                int bgByte2 = memory.read(tileAddr + tileLine * 2 + 1) & 0xFF;
                int bgBit = 7 - (bgX % 8);
                int bgColorIndex = ((bgByte2 >> bgBit) & 1) << 1 | ((bgByte1 >> bgBit) & 1);

                if (bgColorIndex != 0) {
                    continue;
                }
            }

            display.setPixel(pixelX, scanline, pixelColor.toColor(), false);
        }
    }

}
