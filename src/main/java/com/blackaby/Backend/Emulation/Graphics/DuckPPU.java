package com.blackaby.Backend.Emulation.Graphics;

import java.util.Arrays;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.Memory.DuckAddresses;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;
import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Misc.Settings;

/**
 * Emulates the Game Boy pixel processing unit.
 * <p>
 * The PPU advances one T-cycle at a time, manages the LCD mode state machine,
 * renders background, window, and sprite pixels, and raises the matching LCD
 * interrupts.
 */
public class DuckPPU {

    public record PpuState(
            int modeOrdinal,
            int scanline,
            int cycle,
            boolean statInterruptLine) implements java.io.Serializable {
    }

    public static final int oamDuration = 80;
    public static final int vramDuration = 172;

    private static final int scanlineCycles = 456;
    private static final int vblankLines = 10;
    private static final int screenHeight = 144;
    private static final int screenWidth = 160;
    private static final int maxSpritesPerScanline = 10;

    private static final int regLcdc = DuckAddresses.LCDC;
    private static final int regStat = DuckAddresses.STAT;
    private static final int regScy = DuckAddresses.SCY;
    private static final int regScx = DuckAddresses.SCX;
    private static final int regLy = DuckAddresses.LY;
    private static final int regLyc = DuckAddresses.LYC;
    private static final int regBgp = DuckAddresses.BGP;
    private static final int regObp0 = DuckAddresses.OBP0;
    private static final int regObp1 = DuckAddresses.OBP1;
    private static final int regWy = DuckAddresses.WY;
    private static final int regWx = DuckAddresses.WX;

    private enum PpuMode {
        HBLANK(0),
        VBLANK(1),
        OAM(2),
        VRAM(3);

        private final int flag;

        PpuMode(int flag) {
            this.flag = flag;
        }
    }

    private final DuckCPU cpu;
    private final DuckMemory memory;
    private final DuckDisplay display;
    private final int[] backgroundPriorityBuffer = new int[screenWidth];
    private final boolean[] backgroundTilePriorityBuffer = new boolean[screenWidth];
    private final int[] visibleSpriteY = new int[maxSpritesPerScanline];
    private final int[] visibleSpriteX = new int[maxSpritesPerScanline];
    private final int[] visibleSpriteTile = new int[maxSpritesPerScanline];
    private final int[] visibleSpriteAttributes = new int[maxSpritesPerScanline];
    private final int[] activeBackgroundPalette = new int[4];
    private final int[] activeSpritePalette0 = new int[4];
    private final int[] activeSpritePalette1 = new int[4];
    private final int[] decodedBackgroundPalette = new int[4];
    private final int[] decodedSpritePalette0 = new int[4];
    private final int[] decodedSpritePalette1 = new int[4];

    private PpuMode mode;
    private int scanline;
    private int cycle;
    private boolean statInterruptLine;
    private int completedFrames;

    /**
     * Creates a PPU bound to the current CPU, memory bus, and display target.
     *
     * @param cpu CPU for interrupt requests
     * @param memory memory bus
     * @param display host display surface
     */
    public DuckPPU(DuckCPU cpu, DuckMemory memory, DuckDisplay display) {
        this.cpu = cpu;
        this.memory = memory;
        this.display = display;
        mode = PpuMode.OAM;
    }

    /**
     * Advances the PPU by one T-cycle.
     */
    public void Step() {
        int lcdControl = memory.Read(regLcdc);
        if ((lcdControl & 0x80) == 0) {
            HandleLcdDisabled();
            return;
        }

        cycle++;

        switch (mode) {
            case OAM -> {
                if (cycle >= oamDuration) {
                    cycle -= oamDuration;
                    SetMode(PpuMode.VRAM);
                }
            }
            case VRAM -> {
                if (cycle >= vramDuration) {
                    cycle -= vramDuration;
                    SetMode(PpuMode.HBLANK);
                    RenderScanline();
                }
            }
            case HBLANK -> {
                if (cycle >= scanlineCycles - oamDuration - vramDuration) {
                    cycle -= scanlineCycles - oamDuration - vramDuration;
                    scanline++;

                    memory.WriteDirect(regLy, scanline);
                    UpdateLycCompare();

                    if (scanline == screenHeight) {
                        SetMode(PpuMode.VBLANK);
                        cpu.RequestInterrupt(DuckCPU.Interrupt.VBLANK);
                        completedFrames++;
                        display.presentFrame();
                    } else {
                        SetMode(PpuMode.OAM);
                    }
                }
            }
            case VBLANK -> {
                if (cycle >= scanlineCycles) {
                    cycle -= scanlineCycles;
                    scanline++;

                    if (scanline >= screenHeight + vblankLines) {
                        scanline = 0;
                        SetMode(PpuMode.OAM);
                    }

                    memory.WriteDirect(regLy, scanline);
                    UpdateLycCompare();
                }
            }
        }
    }

    /**
     * Captures the live LCD mode machine state.
     *
     * @return PPU state snapshot
     */
    public PpuState CaptureState() {
        return new PpuState(mode.ordinal(), scanline, cycle, statInterruptLine);
    }

    /**
     * Restores the live LCD mode machine state.
     *
     * @param state PPU snapshot to restore
     */
    public void RestoreState(PpuState state) {
        if (state == null) {
            throw new IllegalArgumentException("A PPU quick state is required.");
        }

        PpuMode[] modes = PpuMode.values();
        int ordinal = Math.max(0, Math.min(modes.length - 1, state.modeOrdinal()));
        mode = modes[ordinal];
        scanline = state.scanline();
        cycle = Math.max(0, state.cycle());
        statInterruptLine = state.statInterruptLine();
        completedFrames = 0;
    }

    /**
     * Returns and clears the number of frames completed since the last poll.
     *
     * @return completed frame count
     */
    public int ConsumeCompletedFrames() {
        int frames = completedFrames;
        completedFrames = 0;
        return frames;
    }

    /**
     * Returns the current scanline index.
     *
     * @return current LY value
     */
    public int GetCurrentScanline() {
        return scanline;
    }

    /**
     * Returns whether the current LCD mode can service one CGB H-Blank DMA block.
     *
     * @return {@code true} during visible-line H-Blank while the LCD is enabled
     */
    public boolean IsHblankTransferWindowOpen() {
        return (memory.Read(regLcdc) & 0x80) != 0
                && mode == PpuMode.HBLANK
                && scanline < screenHeight;
    }

    private void HandleLcdDisabled() {
        scanline = 0;
        cycle = 0;
        mode = PpuMode.HBLANK;
        statInterruptLine = false;
        completedFrames = 0;
        memory.WriteDirect(regLy, 0);

        int stat = memory.Read(regStat);
        memory.WriteDirect(regStat, (stat & 0xFC) | 0x80);
    }

    private void SetMode(PpuMode newMode) {
        mode = newMode;
        int stat = memory.Read(regStat);
        memory.WriteDirect(regStat, (stat & 0xFC) | newMode.flag);
        UpdateStatInterruptLine();
    }

    private void UpdateLycCompare() {
        int ly = memory.Read(regLy);
        int lyc = memory.Read(regLyc);
        int stat = memory.Read(regStat);

        if (ly == lyc) {
            stat |= 0x04;
        } else {
            stat &= ~0x04;
        }

        memory.WriteDirect(regStat, stat);
        UpdateStatInterruptLine();
    }

    private void UpdateStatInterruptLine() {
        int stat = memory.Read(regStat);
        boolean coincidence = (stat & 0x04) != 0;
        boolean lineHigh = (mode == PpuMode.HBLANK && (stat & 0x08) != 0)
                || (mode == PpuMode.VBLANK && (stat & 0x10) != 0)
                || (mode == PpuMode.OAM && (stat & 0x20) != 0)
                || (coincidence && (stat & 0x40) != 0);

        if (lineHigh && !statInterruptLine) {
            cpu.RequestInterrupt(DuckCPU.Interrupt.LCD_STAT);
        }

        statInterruptLine = lineHigh;
    }

    private void RenderScanline() {
        int lcdControl = memory.Read(regLcdc);
        boolean cgbMode = memory.IsCgbMode();
        boolean useGbcColourisation = ShouldUseGbcColourisation();
        if (!cgbMode) {
            if (useGbcColourisation) {
                LoadPalette(Settings.gbcBackgroundPaletteObjects, activeBackgroundPalette);
                LoadPalette(Settings.gbcSpritePalette0Objects, activeSpritePalette0);
                LoadPalette(Settings.gbcSpritePalette1Objects, activeSpritePalette1);
            } else {
                LoadDmgPalette(activeBackgroundPalette);
                LoadDmgPalette(activeSpritePalette0);
                LoadDmgPalette(activeSpritePalette1);
            }

            DecodePalette(memory.Read(regBgp), activeBackgroundPalette, decodedBackgroundPalette);
            DecodePalette(memory.Read(regObp0), activeSpritePalette0, decodedSpritePalette0);
            DecodePalette(memory.Read(regObp1), activeSpritePalette1, decodedSpritePalette1);
        }

        Arrays.fill(backgroundPriorityBuffer, 0);
        Arrays.fill(backgroundTilePriorityBuffer, false);

        if (cgbMode || (lcdControl & 0x01) != 0) {
            RenderBackground(lcdControl, cgbMode ? null : decodedBackgroundPalette);
            if ((lcdControl & 0x20) != 0) {
                RenderWindow(lcdControl, cgbMode ? null : decodedBackgroundPalette);
            }
        } else {
            int white = decodedBackgroundPalette[0];
            for (int x = 0; x < screenWidth; x++) {
                display.setPixel(x, scanline, white, false);
            }
        }

        if ((lcdControl & 0x02) != 0) {
            RenderSprites(lcdControl, cgbMode ? null : decodedSpritePalette0, cgbMode ? null : decodedSpritePalette1);
        }
    }

    private void RenderBackground(int lcdControl, int[] activeBackgroundPalette) {
        int scrollY = memory.Read(regScy);
        int scrollX = memory.Read(regScx);
        boolean cgbMode = memory.IsCgbMode();
        int[] backgroundPalette = cgbMode ? null : activeBackgroundPalette;

        boolean unsignedTileData = (lcdControl & 0x10) != 0;
        int tileMapBase = ((lcdControl & 0x08) != 0) ? 0x9C00 : 0x9800;
        int tileDataBase = unsignedTileData ? 0x8000 : 0x9000;

        int yPosition = (scanline + scrollY) & 0xFF;
        int tileRow = yPosition / 8;

        for (int x = 0; x < screenWidth; x++) {
            int xPosition = (x + scrollX) & 0xFF;
            int tileColumn = xPosition / 8;

            int tileAddress = tileMapBase + (tileRow * 32) + tileColumn;
            int tileNumber = cgbMode ? memory.ReadVideoRam(0, tileAddress) : memory.Read(tileAddress);
            int tileAttributes = cgbMode ? memory.ReadVideoRam(1, tileAddress) : 0;
            if (!unsignedTileData) {
                tileNumber = (byte) tileNumber;
            }

            int tileLineAddress = tileDataBase + (tileNumber * 16);
            int tileLine = yPosition % 8;
            if (cgbMode && (tileAttributes & 0x40) != 0) {
                tileLine = 7 - tileLine;
            }
            int lineOffset = tileLine * 2;
            int vramBank = cgbMode && (tileAttributes & 0x08) != 0 ? 1 : 0;
            int lowByte = cgbMode
                    ? memory.ReadVideoRam(vramBank, tileLineAddress + lineOffset)
                    : memory.Read(tileLineAddress + lineOffset);
            int highByte = cgbMode
                    ? memory.ReadVideoRam(vramBank, tileLineAddress + lineOffset + 1)
                    : memory.Read(tileLineAddress + lineOffset + 1);

            int bit = 7 - (xPosition % 8);
            if (cgbMode && (tileAttributes & 0x20) != 0) {
                bit = xPosition % 8;
            }
            int high = (highByte >> bit) & 1;
            int low = (lowByte >> bit) & 1;
            int colourIndex = (high << 1) | low;

            backgroundPriorityBuffer[x] = colourIndex;
            backgroundTilePriorityBuffer[x] = cgbMode && (tileAttributes & 0x80) != 0;

            int colour = cgbMode
                    ? memory.ReadCgbBackgroundPaletteColourRgb(tileAttributes & 0x07, colourIndex)
                    : backgroundPalette[colourIndex];
            display.setPixel(x, scanline, colour, false);
        }
    }

    private void RenderWindow(int lcdControl, int[] activeBackgroundPalette) {
        int windowY = memory.Read(regWy);
        int windowX = memory.Read(regWx) - 7;
        if (scanline < windowY || windowX >= screenWidth) {
            return;
        }

        boolean cgbMode = memory.IsCgbMode();
        int[] backgroundPalette = cgbMode ? null : activeBackgroundPalette;
        boolean unsignedTileData = (lcdControl & 0x10) != 0;
        int tileMapBase = ((lcdControl & 0x40) != 0) ? 0x9C00 : 0x9800;
        int tileDataBase = unsignedTileData ? 0x8000 : 0x9000;
        int yPosition = scanline - windowY;
        int tileRow = yPosition / 8;
        int startX = Math.max(windowX, 0);

        for (int x = startX; x < screenWidth; x++) {
            int xPosition = x - windowX;
            int tileColumn = xPosition / 8;

            int tileAddress = tileMapBase + (tileRow * 32) + tileColumn;
            int tileNumber = cgbMode ? memory.ReadVideoRam(0, tileAddress) : memory.Read(tileAddress);
            int tileAttributes = cgbMode ? memory.ReadVideoRam(1, tileAddress) : 0;
            if (!unsignedTileData) {
                tileNumber = (byte) tileNumber;
            }

            int tileLineAddress = tileDataBase + (tileNumber * 16);
            int tileLine = yPosition % 8;
            if (cgbMode && (tileAttributes & 0x40) != 0) {
                tileLine = 7 - tileLine;
            }
            int lineOffset = tileLine * 2;
            int vramBank = cgbMode && (tileAttributes & 0x08) != 0 ? 1 : 0;
            int lowByte = cgbMode
                    ? memory.ReadVideoRam(vramBank, tileLineAddress + lineOffset)
                    : memory.Read(tileLineAddress + lineOffset);
            int highByte = cgbMode
                    ? memory.ReadVideoRam(vramBank, tileLineAddress + lineOffset + 1)
                    : memory.Read(tileLineAddress + lineOffset + 1);

            int bit = 7 - (xPosition % 8);
            if (cgbMode && (tileAttributes & 0x20) != 0) {
                bit = xPosition % 8;
            }
            int high = (highByte >> bit) & 1;
            int low = (lowByte >> bit) & 1;
            int colourIndex = (high << 1) | low;

            backgroundPriorityBuffer[x] = colourIndex;
            backgroundTilePriorityBuffer[x] = cgbMode && (tileAttributes & 0x80) != 0;

            int colour = cgbMode
                    ? memory.ReadCgbBackgroundPaletteColourRgb(tileAttributes & 0x07, colourIndex)
                    : backgroundPalette[colourIndex];
            display.setPixel(x, scanline, colour, false);
        }
    }

    private void RenderSprites(int lcdControl, int[] activeSpritePalette0, int[] activeSpritePalette1) {
        boolean use8x16 = (lcdControl & 0x04) != 0;
        boolean cgbMode = memory.IsCgbMode();
        boolean bgMasterPriority = cgbMode && (lcdControl & 0x01) != 0;
        int visibleSpriteCount = LoadSpritesOnScanline(use8x16);
        for (int spriteIndex = 0; spriteIndex < visibleSpriteCount; spriteIndex++) {
            DrawSprite(spriteIndex, use8x16, cgbMode, bgMasterPriority, activeSpritePalette0, activeSpritePalette1);
        }
    }

    private void DrawSprite(int spriteIndex, boolean use8x16, boolean cgbMode, boolean bgMasterPriority,
            int[] activeSpritePalette0, int[] activeSpritePalette1) {
        int spriteHeight = use8x16 ? 16 : 8;
        int attributes = visibleSpriteAttributes[spriteIndex];
        int[] palette = cgbMode ? null : (((attributes & 0x10) != 0) ? activeSpritePalette1 : activeSpritePalette0);

        int line = scanline - visibleSpriteY[spriteIndex];
        if ((attributes & 0x40) != 0) {
            line = spriteHeight - 1 - line;
        }

        int tileIndex = visibleSpriteTile[spriteIndex];
        if (use8x16) {
            tileIndex &= 0xFE;
            if (line >= 8) {
                tileIndex |= 0x01;
                line -= 8;
            }
        }

        int tileAddress = 0x8000 + (tileIndex * 16) + (line * 2);
        int vramBank = (attributes & 0x08) != 0 ? 1 : 0;
        int lowByte = cgbMode ? memory.ReadVideoRam(vramBank, tileAddress) : memory.Read(tileAddress);
        int highByte = cgbMode ? memory.ReadVideoRam(vramBank, tileAddress + 1) : memory.Read(tileAddress + 1);

        for (int x = 0; x < 8; x++) {
            int pixelX = visibleSpriteX[spriteIndex] + x;
            if (pixelX < 0 || pixelX >= screenWidth) {
                continue;
            }

            int bit = (attributes & 0x20) != 0 ? x : 7 - x;
            int high = (highByte >> bit) & 1;
            int low = (lowByte >> bit) & 1;
            int colourIndex = (high << 1) | low;

            if (colourIndex == 0) {
                continue;
            }

            if (cgbMode) {
                if (bgMasterPriority && backgroundPriorityBuffer[pixelX] != 0
                        && (((attributes & 0x80) != 0) || backgroundTilePriorityBuffer[pixelX])) {
                    continue;
                }
            } else if ((attributes & 0x80) != 0 && backgroundPriorityBuffer[pixelX] != 0) {
                continue;
            }

            int colour = cgbMode
                    ? memory.ReadCgbObjectPaletteColourRgb(attributes & 0x07, colourIndex)
                    : palette[colourIndex];
            display.setPixel(pixelX, scanline, colour, false);
        }
    }

    private int LoadSpritesOnScanline(boolean use8x16) {
        int visibleSpriteCount = 0;
        int spriteHeight = use8x16 ? 16 : 8;

        for (int index = 0; index < 40; index++) {
            int address = 0xFE00 + (index * 4);
            int y = memory.Read(address) - 16;
            int x = memory.Read(address + 1) - 8;
            int tile = memory.Read(address + 2);
            int attributes = memory.Read(address + 3);

            if (scanline >= y && scanline < (y + spriteHeight)) {
                visibleSpriteY[visibleSpriteCount] = y;
                visibleSpriteX[visibleSpriteCount] = x;
                visibleSpriteTile[visibleSpriteCount] = tile;
                visibleSpriteAttributes[visibleSpriteCount] = attributes;
                visibleSpriteCount++;
            }

            if (visibleSpriteCount >= maxSpritesPerScanline) {
                break;
            }
        }

        if ((!memory.IsCgbMode() || memory.Read(DuckAddresses.OPRI) != 0) && visibleSpriteCount > 1) {
            SortVisibleSpritesByX(visibleSpriteCount);
        }
        return visibleSpriteCount;
    }

    private void SortVisibleSpritesByX(int count) {
        for (int index = 1; index < count; index++) {
            int spriteY = visibleSpriteY[index];
            int spriteX = visibleSpriteX[index];
            int spriteTile = visibleSpriteTile[index];
            int spriteAttributes = visibleSpriteAttributes[index];
            int compareIndex = index - 1;

            while (compareIndex >= 0 && spriteX < visibleSpriteX[compareIndex]) {
                visibleSpriteY[compareIndex + 1] = visibleSpriteY[compareIndex];
                visibleSpriteX[compareIndex + 1] = visibleSpriteX[compareIndex];
                visibleSpriteTile[compareIndex + 1] = visibleSpriteTile[compareIndex];
                visibleSpriteAttributes[compareIndex + 1] = visibleSpriteAttributes[compareIndex];
                compareIndex--;
            }

            visibleSpriteY[compareIndex + 1] = spriteY;
            visibleSpriteX[compareIndex + 1] = spriteX;
            visibleSpriteTile[compareIndex + 1] = spriteTile;
            visibleSpriteAttributes[compareIndex + 1] = spriteAttributes;
        }
    }

    private void DecodePalette(int paletteRegister, int[] paletteColours, int[] targetPalette) {
        for (int colourIndex = 0; colourIndex < targetPalette.length; colourIndex++) {
            targetPalette[colourIndex] = PaletteColourRgb(colourIndex, paletteRegister, paletteColours);
        }
    }

    private int PaletteColourRgb(int colourIndex, int paletteRegister, int[] paletteColours) {
        int shift = colourIndex * 2;
        int colourId = (paletteRegister >> shift) & 0x03;
        return paletteColours[colourId];
    }

    private void LoadPalette(GBColor[] palette, int[] target) {
        for (int index = 0; index < target.length; index++) {
            target[index] = palette[index].ToRgb();
        }
    }

    private void LoadDmgPalette(int[] target) {
        target[0] = Settings.gbColour0Object.ToRgb();
        target[1] = Settings.gbColour1Object.ToRgb();
        target[2] = Settings.gbColour2Object.ToRgb();
        target[3] = Settings.gbColour3Object.ToRgb();
    }

    private boolean ShouldUseGbcColourisation() {
        return Settings.gbcPaletteModeEnabled && !memory.IsLoadedRomCgbCompatible();
    }
}
