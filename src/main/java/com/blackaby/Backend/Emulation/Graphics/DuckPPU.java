package com.blackaby.Backend.Emulation.Graphics;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Misc.Settings;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;
import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.DuckSprite;
import com.blackaby.Backend.Emulation.Memory.DuckAddresses;

/**
 * Emulates the Pixel Processing Unit (PPU) of the Game Boy.
 * <p>
 * Handles the LCD State Machine (OAM -> VRAM -> HBLANK -> VBLANK),
 * Background rendering, Sprite rendering, and Interrupt generation.
 * </p>
 */
public class DuckPPU {

    // --- Timing Constants ---
    public static final int OAM_DURATION = 80;
    public static final int VRAM_DURATION = 172;
    public static final int HBLANK_DURATION = 204;
    private static final int SCANLINE_CYCLES = 456;
    private static final int VBLANK_LINES = 10;
    private static final int SCREEN_HEIGHT = 144;
    private static final int SCREEN_WIDTH = 160;

    // --- Register Addresses ---
    private static final int REG_LCDC = DuckAddresses.LCDC;
    private static final int REG_STAT = DuckAddresses.STAT;
    private static final int REG_SCY = DuckAddresses.SCY;
    private static final int REG_SCX = DuckAddresses.SCX;
    private static final int REG_LY = DuckAddresses.LY;
    private static final int REG_LYC = DuckAddresses.LYC;
    private static final int REG_BGP = DuckAddresses.BGP;
    private static final int REG_OBP0 = DuckAddresses.OBP0;
    private static final int REG_OBP1 = DuckAddresses.OBP1;

    // Window Registers (Unused in this version, but kept for reference)
    // private static final int REG_WY = DuckAddresses.WY;
    // private static final int REG_WX = DuckAddresses.WX;

    // --- Mode Definitions ---
    private enum PPUMode {
        HBLANK(0),
        VBLANK(1),
        OAM(2),
        VRAM(3);

        final int flag;

        PPUMode(int flag) {
            this.flag = flag;
        }
    }

    private final DuckCPU cpu;
    private final DuckMemory memory;
    private final DuckDisplay display;

    private PPUMode mode;
    private int scanline = 0;
    private int cycle = 0;

    // Optimization: Buffer to store BG color IDs (0-3) for the current scanline.
    // Used to determine if a Sprite should appear behind the BG.
    private final int[] bgPriorityBuffer = new int[SCREEN_WIDTH];

    public DuckPPU(DuckCPU cpu, DuckMemory memory, DuckDisplay display) {
        this.cpu = cpu;
        this.memory = memory;
        this.display = display;
        this.mode = PPUMode.OAM;
    }

    /**
     * Steps the PPU by one T-Cycle (4MHz).
     */
    public void step() {
        int lcdc = memory.read(REG_LCDC);

        // Check if LCD is Disabled (Bit 7 of LCDC)
        if ((lcdc & 0x80) == 0) {
            handleLCDDisabled();
            return;
        }

        cycle++;

        switch (mode) {
            case OAM:
                if (cycle >= OAM_DURATION) {
                    cycle -= OAM_DURATION;
                    setMode(PPUMode.VRAM);
                }
                break;
            case VRAM:
                if (cycle >= VRAM_DURATION) {
                    cycle -= VRAM_DURATION;
                    setMode(PPUMode.HBLANK);
                    // Render the scanline at the end of VRAM mode
                    renderScanline();
                }
                break;
            case HBLANK:
                if (cycle >= HBLANK_DURATION) {
                    cycle -= HBLANK_DURATION;
                    scanline++;

                    memory.write(REG_LY, scanline);
                    updateLYCCompare();

                    if (scanline == SCREEN_HEIGHT) {
                        setMode(PPUMode.VBLANK);
                        cpu.requestInterrupt(DuckCPU.Interrupt.VBLANK);
                        display.repaint(); // Frame finished
                    } else {
                        setMode(PPUMode.OAM);
                    }
                }
                break;
            case VBLANK:
                if (cycle >= SCANLINE_CYCLES) {
                    cycle -= SCANLINE_CYCLES;
                    scanline++;

                    if (scanline >= SCREEN_HEIGHT + VBLANK_LINES) {
                        scanline = 0;
                        setMode(PPUMode.OAM);
                    }

                    memory.write(REG_LY, scanline);
                    updateLYCCompare();
                }
                break;
        }
    }

    private void handleLCDDisabled() {
        scanline = 0;
        cycle = 0;
        mode = PPUMode.HBLANK;
        memory.write(REG_LY, 0);

        int stat = memory.read(REG_STAT);
        memory.write(REG_STAT, stat & 0xFC);
    }

    private void setMode(PPUMode newMode) {
        this.mode = newMode;
        int stat = memory.read(REG_STAT);

        stat = (stat & 0xFC) | newMode.flag;
        memory.write(REG_STAT, stat);

        // Handle STAT Interrupts
        boolean requestInt = false;
        if (newMode == PPUMode.HBLANK && (stat & 0x08) != 0)
            requestInt = true;
        if (newMode == PPUMode.VBLANK && (stat & 0x10) != 0)
            requestInt = true;
        if (newMode == PPUMode.OAM && (stat & 0x20) != 0)
            requestInt = true;

        if (requestInt) {
            cpu.requestInterrupt(DuckCPU.Interrupt.LCD_STAT);
        }
    }

    private void updateLYCCompare() {
        int ly = memory.read(REG_LY);
        int lyc = memory.read(REG_LYC);
        int stat = memory.read(REG_STAT);

        if (ly == lyc) {
            stat |= 0x04;
            if ((stat & 0x40) != 0) {
                cpu.requestInterrupt(DuckCPU.Interrupt.LCD_STAT);
            }
        } else {
            stat &= ~0x04;
        }
        memory.write(REG_STAT, stat);
    }

    /**
     * Renders one complete line of pixels (BG + Sprites).
     */
    private void renderScanline() {
        int lcdc = memory.read(REG_LCDC);

        // Clear priority buffer
        for (int i = 0; i < SCREEN_WIDTH; i++)
            bgPriorityBuffer[i] = 0;

        // 1. Render Background
        if ((lcdc & 0x01) != 0) {
            renderBackground(lcdc);
        } else {
            // FIX: Use Color object instead of int
            Color white = Settings.GB_COLOR_0_OBJ.toColor();
            for (int x = 0; x < SCREEN_WIDTH; x++) {
                display.setPixel(x, scanline, white, false);
            }
        }

        // 2. Render Sprites
        if ((lcdc & 0x02) != 0) {
            renderSprites(lcdc);
        }
    }

    private void renderBackground(int lcdc) {
        int scrollY = memory.read(REG_SCY);
        int scrollX = memory.read(REG_SCX);
        int bgPalette = memory.read(REG_BGP);

        boolean unsignedData = (lcdc & 0x10) != 0;
        int tileMapBase = ((lcdc & 0x08) != 0) ? 0x9C00 : 0x9800;
        int tileDataBase = unsignedData ? 0x8000 : 0x9000;

        int yPos = (scanline + scrollY) & 0xFF;
        int tileRow = yPos / 8;

        for (int x = 0; x < SCREEN_WIDTH; x++) {
            int xPos = (x + scrollX) & 0xFF;
            int tileCol = xPos / 8;

            int tileAddress = tileMapBase + (tileRow * 32) + tileCol;
            int tileNum = memory.read(tileAddress);

            if (!unsignedData) {
                tileNum = (byte) tileNum;
            }

            int location = tileDataBase + (tileNum * 16);
            int line = (yPos % 8) * 2;
            int b1 = memory.read(location + line);
            int b2 = memory.read(location + line + 1);

            int bit = 7 - (xPos % 8);
            int hi = (b2 >> bit) & 1;
            int lo = (b1 >> bit) & 1;
            int colorIndex = (hi << 1) | lo;

            bgPriorityBuffer[x] = colorIndex;

            GBColor color = getPaletteColor(colorIndex, bgPalette);
            display.setPixel(x, scanline, color.toColor(), false);
        }
    }

    private void renderSprites(int lcdc) {
        boolean use8x16 = (lcdc & 0x04) != 0;
        List<DuckSprite> sprites = getSpritesOnScanline(use8x16);

        for (DuckSprite sprite : sprites) {
            drawSprite(sprite, use8x16);
        }
    }

    private void drawSprite(DuckSprite sprite, boolean use8x16) {
        // Use the cleaner helper methods from the updated DuckSprite class
        int spriteHeight = use8x16 ? 16 : 8;
        int paletteReg = sprite.usePalette1() ? REG_OBP1 : REG_OBP0;
        int palette = memory.read(paletteReg);

        int line = scanline - sprite.y;

        if (sprite.isYFlip()) {
            line = spriteHeight - 1 - line;
        }

        int tileIndex = sprite.tileIndex;
        if (use8x16)
            tileIndex &= 0xFE;

        int tileAddr = 0x8000 + (tileIndex * 16) + (line * 2);
        int b1 = memory.read(tileAddr);
        int b2 = memory.read(tileAddr + 1);

        for (int x = 0; x < 8; x++) {
            int pixelX = sprite.x + x;

            if (pixelX < 0 || pixelX >= SCREEN_WIDTH)
                continue;

            // Cleaner X-Flip check
            int bit = sprite.isXFlip() ? x : 7 - x;

            int hi = (b2 >> bit) & 1;
            int lo = (b1 >> bit) & 1;
            int colorIndex = (hi << 1) | lo;

            if (colorIndex == 0)
                continue; // Transparent

            // Cleaner Priority Check
            if (sprite.isPriorityInternal() && bgPriorityBuffer[pixelX] != 0)
                continue;

            GBColor color = getPaletteColor(colorIndex, palette);
            display.setPixel(pixelX, scanline, color.toColor(), false);
        }
    }

    private List<DuckSprite> getSpritesOnScanline(boolean use8x16) {
        List<DuckSprite> visible = new ArrayList<>();
        int height = use8x16 ? 16 : 8;

        for (int i = 0; i < 40; i++) {
            int addr = 0xFE00 + (i * 4);
            int y = memory.read(addr) - 16;
            int x = memory.read(addr + 1) - 8;
            int tile = memory.read(addr + 2);
            int attr = memory.read(addr + 3);

            if (scanline >= y && scanline < (y + height)) {
                visible.add(new DuckSprite(y, x, tile, attr));
            }

            if (visible.size() >= 10)
                break;
        }

        // Stable sort by X coordinate
        visible.sort(Comparator.comparingInt(s -> s.x));
        return visible;
    }

    private GBColor getPaletteColor(int colorIndex, int palette) {
        int shift = colorIndex * 2;
        int colorId = (palette >> shift) & 0x03;

        return switch (colorId) {
            case 0 -> Settings.GB_COLOR_0_OBJ;
            case 1 -> Settings.GB_COLOR_1_OBJ;
            case 2 -> Settings.GB_COLOR_2_OBJ;
            default -> Settings.GB_COLOR_3_OBJ;
        };
    }
}