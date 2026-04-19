package com.blackaby.Backend.GB.Graphics;

import java.util.Arrays;

import com.blackaby.Backend.GB.CPU.GBProcessor;
import com.blackaby.Backend.GB.Memory.GBMemAddresses;
import com.blackaby.Backend.GB.Memory.GBMemory;
import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Misc.NonGbcColourMode;
import com.blackaby.Misc.Settings;

/**
 * Emulates the Game Boy pixel processing unit.
 * <p>
 * The PPU advances one T-cycle at a time, manages the LCD mode state machine,
 * renders background, window, and sprite pixels, and raises the matching LCD
 * interrupts.
 */
public class GBPPU {

    public record PpuState(
            int modeOrdinal,
            int scanline,
            int cycle,
            boolean statInterruptLine,
            int pixelTransferX,
            int windowLineCounter,
            boolean windowRenderedOnCurrentScanline) implements java.io.Serializable {
    }

    public static final int oamDuration = 80;
    public static final int vramDuration = 172;

    private static final int scanlineCycles = 456;
    private static final int vblankLines = 10;
    private static final int screenHeight = 144;
    private static final int screenWidth = 160;
    private static final int maxSpritesPerScanline = 10;
    private static final int noSpritePixel = Integer.MIN_VALUE;

    private static final int regLcdc = GBMemAddresses.LCDC;
    private static final int regStat = GBMemAddresses.STAT;
    private static final int regScy = GBMemAddresses.SCY;
    private static final int regScx = GBMemAddresses.SCX;
    private static final int regLy = GBMemAddresses.LY;
    private static final int regLyc = GBMemAddresses.LYC;
    private static final int regBgp = GBMemAddresses.BGP;
    private static final int regObp0 = GBMemAddresses.OBP0;
    private static final int regObp1 = GBMemAddresses.OBP1;
    private static final int regWy = GBMemAddresses.WY;
    private static final int regWx = GBMemAddresses.WX;

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

    private final GBProcessor cpu;
    private final GBMemory memory;
    private final DuckDisplay display;
    private final int[] backgroundPriorityBuffer = new int[screenWidth];
    private final boolean[] backgroundTilePriorityBuffer = new boolean[screenWidth];
    private final int[] visibleSpriteY = new int[maxSpritesPerScanline];
    private final int[] visibleSpriteX = new int[maxSpritesPerScanline];
    private final int[] visibleSpriteTile = new int[maxSpritesPerScanline];
    private final int[] visibleSpriteAttributes = new int[maxSpritesPerScanline];
    private final int[] visibleSpriteRowLow = new int[maxSpritesPerScanline];
    private final int[] visibleSpriteRowHigh = new int[maxSpritesPerScanline];
    private final int[] activeBackgroundPalette = new int[4];
    private final int[] activeSpritePalette0 = new int[4];
    private final int[] activeSpritePalette1 = new int[4];
    private PpuMode mode;
    private int scanline;
    private int cycle;
    private boolean statInterruptLine;
    private int completedFrames;
    private int pixelTransferX;
    private int visibleSpriteCount;
    private int windowLineCounter;
    private boolean windowRenderedOnCurrentScanline;
    private boolean backgroundTileCacheValid;
    private boolean backgroundTileCacheWindowLayer;
    private int backgroundTileCacheTileAddress;
    private int backgroundTileCacheTileLine;
    private boolean backgroundTileCacheUnsignedTileData;
    private boolean backgroundTileCacheCgbMode;
    private int backgroundTileCacheAttributes;
    private int backgroundTileCacheLowByte;
    private int backgroundTileCacheHighByte;

    /**
     * Creates a PPU bound to the current CPU, memory bus, and display target.
     *
     * @param cpu     CPU for interrupt requests
     * @param memory  memory bus
     * @param display host display surface
     */
    public GBPPU(GBProcessor cpu, GBMemory memory, DuckDisplay display) {
        this.cpu = cpu;
        this.memory = memory;
        this.display = display;
        mode = PpuMode.OAM;
    }

    /**
     * Advances the PPU by one T-cycle.
     */
    public void Step() {
        int lcdControl = memory.ReadRegisterDirect(regLcdc);
        if ((lcdControl & 0x80) == 0) {
            HandleLcdDisabled();
            return;
        }

        // LYC comparison is a live signal, so games that rewrite LYC mid-frame
        // can immediately retrigger STAT without waiting for LY to change.
        UpdateLycCompare();
        cycle++;

        switch (mode) {
            case OAM -> {
                if (cycle >= oamDuration) {
                    cycle -= oamDuration;
                    BeginPixelTransfer();
                    SetMode(PpuMode.VRAM);
                }
            }
            case VRAM -> {
                while (pixelTransferX < screenWidth && pixelTransferX < cycle) {
                    RenderNextPixel();
                }
                if (cycle >= vramDuration) {
                    cycle -= vramDuration;
                    SetMode(PpuMode.HBLANK);
                }
            }
            case HBLANK -> {
                if (cycle >= scanlineCycles - oamDuration - vramDuration) {
                    cycle -= scanlineCycles - oamDuration - vramDuration;
                    CompleteCurrentScanline();
                    scanline++;

                    memory.WriteDirect(regLy, scanline);
                    UpdateLycCompare();

                    if (scanline == screenHeight) {
                        SetMode(PpuMode.VBLANK);
                        cpu.RequestInterrupt(GBProcessor.Interrupt.VBLANK);
                        completedFrames++;
                        if (Settings.enableFrameBlending) {
                            if (memory.IsCgbMode()) {
                                display.presentFrame(
                                        DuckDisplay.DefaultCgbPreviousFrameBlendWeight(),
                                        DuckDisplay.DefaultCgbCurrentFrameBlendWeight());
                            } else {
                                display.presentFrame(
                                        DuckDisplay.DefaultDmgPreviousFrameBlendWeight(),
                                        DuckDisplay.DefaultDmgCurrentFrameBlendWeight());
                            }
                        } else {
                            display.presentFrame();
                        }
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
                        windowLineCounter = 0;
                        windowRenderedOnCurrentScanline = false;
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
        return new PpuState(mode.ordinal(), scanline, cycle, statInterruptLine,
                pixelTransferX, windowLineCounter, windowRenderedOnCurrentScanline);
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
        pixelTransferX = Math.max(0, Math.min(screenWidth, state.pixelTransferX()));
        windowLineCounter = Math.max(0, state.windowLineCounter());
        windowRenderedOnCurrentScanline = state.windowRenderedOnCurrentScanline();
        visibleSpriteCount = (mode == PpuMode.VRAM || mode == PpuMode.HBLANK)
                ? LoadSpritesOnScanline((memory.ReadRegisterDirect(regLcdc) & 0x04) != 0)
                : 0;
        InvalidatePixelTransferCaches();
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
        return (memory.ReadRegisterDirect(regLcdc) & 0x80) != 0
                && mode == PpuMode.HBLANK
                && scanline < screenHeight;
    }

    private void HandleLcdDisabled() {
        scanline = 0;
        cycle = 0;
        mode = PpuMode.HBLANK;
        statInterruptLine = false;
        completedFrames = 0;
        pixelTransferX = 0;
        visibleSpriteCount = 0;
        windowLineCounter = 0;
        windowRenderedOnCurrentScanline = false;
        InvalidatePixelTransferCaches();
        memory.WriteDirect(regLy, 0);

        int stat = memory.ReadRegisterDirect(regStat);
        memory.WriteDirect(regStat, (stat & 0xFC) | 0x80);
    }

    private void SetMode(PpuMode newMode) {
        mode = newMode;
        int stat = memory.ReadRegisterDirect(regStat);
        memory.WriteDirect(regStat, (stat & 0xFC) | newMode.flag);
        UpdateStatInterruptLine();
    }

    private void UpdateLycCompare() {
        int ly = memory.ReadRegisterDirect(regLy);
        int lyc = memory.ReadRegisterDirect(regLyc);
        int stat = memory.ReadRegisterDirect(regStat);

        if (ly == lyc) {
            stat |= 0x04;
        } else {
            stat &= ~0x04;
        }

        memory.WriteDirect(regStat, stat);
        UpdateStatInterruptLine();
    }

    private void UpdateStatInterruptLine() {
        int stat = memory.ReadRegisterDirect(regStat);
        boolean coincidence = (stat & 0x04) != 0;
        boolean lineHigh = (mode == PpuMode.HBLANK && (stat & 0x08) != 0)
                || (mode == PpuMode.VBLANK && (stat & 0x10) != 0)
                || (mode == PpuMode.OAM && (stat & 0x20) != 0)
                || (coincidence && (stat & 0x40) != 0);

        if (lineHigh && !statInterruptLine) {
            cpu.RequestInterrupt(GBProcessor.Interrupt.LCD_STAT);
        }

        statInterruptLine = lineHigh;
    }

    private void BeginPixelTransfer() {
        pixelTransferX = 0;
        windowRenderedOnCurrentScanline = false;
        InvalidatePixelTransferCaches();
        Arrays.fill(backgroundPriorityBuffer, 0);
        Arrays.fill(backgroundTilePriorityBuffer, false);
        visibleSpriteCount = LoadSpritesOnScanline((memory.ReadRegisterDirect(regLcdc) & 0x04) != 0);

        if (!memory.IsCgbMode()) {
            boolean useGbcColourisation = ShouldUseGbcColourisation();
            if (useGbcColourisation) {
                LoadPalette(Settings.gbcBackgroundPaletteObjects, activeBackgroundPalette);
                LoadPalette(Settings.gbcSpritePalette0Objects, activeSpritePalette0);
                LoadPalette(Settings.gbcSpritePalette1Objects, activeSpritePalette1);
            } else {
                LoadDmgPalette(activeBackgroundPalette);
                LoadDmgPalette(activeSpritePalette0);
                LoadDmgPalette(activeSpritePalette1);
            }
        }
    }

    private void CompleteCurrentScanline() {
        if (windowRenderedOnCurrentScanline) {
            windowLineCounter++;
            windowRenderedOnCurrentScanline = false;
        }
        pixelTransferX = 0;
        visibleSpriteCount = 0;
    }

    private void RenderNextPixel() {
        int x = pixelTransferX;
        int lcdControl = memory.ReadRegisterDirect(regLcdc);
        boolean dmgCompatibilityMode = memory.IsDmgCompatibilityMode();
        boolean cgbMode = memory.IsCgbMode() && !dmgCompatibilityMode;

        int backgroundColour = ResolveBackgroundPixel(x, lcdControl, cgbMode, dmgCompatibilityMode);
        display.setPixel(x, scanline, backgroundColour, false);

        int spriteColour = ResolveSpritePixel(x, lcdControl, cgbMode, dmgCompatibilityMode);
        if (spriteColour != noSpritePixel) {
            display.setPixel(x, scanline, spriteColour, false);
        }

        pixelTransferX++;
    }

    private int ResolveBackgroundPixel(int screenX, int lcdControl, boolean cgbMode, boolean dmgCompatibilityMode) {
        if (!cgbMode && (lcdControl & 0x01) == 0) {
            backgroundPriorityBuffer[screenX] = 0;
            backgroundTilePriorityBuffer[screenX] = false;
            if (dmgCompatibilityMode) {
                return ResolveDmgCompatibilityBackgroundColour(0);
            }
            return ResolveDmgPaletteColour(memory.ReadRegisterDirect(regBgp), activeBackgroundPalette, 0);
        }

        int windowY = memory.ReadRegisterDirect(regWy);
        int windowX = memory.ReadRegisterDirect(regWx) - 7;
        boolean windowEnabled = (lcdControl & 0x20) != 0
                && scanline >= windowY
                && screenX >= windowX
                && windowX < screenWidth;
        if (windowEnabled) {
            windowRenderedOnCurrentScanline = true;
            return SampleBackgroundLayerPixel(
                    screenX, lcdControl, cgbMode, dmgCompatibilityMode, true, windowX, windowLineCounter);
        }
        return SampleBackgroundLayerPixel(screenX, lcdControl, cgbMode, dmgCompatibilityMode, false, windowX, 0);
    }

    private int SampleBackgroundLayerPixel(int screenX, int lcdControl, boolean cgbMode,
            boolean dmgCompatibilityMode, boolean windowLayer, int windowX, int activeWindowLine) {
        boolean unsignedTileData = (lcdControl & 0x10) != 0;
        int tileMapBase = windowLayer
                ? (((lcdControl & 0x40) != 0) ? 0x9C00 : 0x9800)
                : (((lcdControl & 0x08) != 0) ? 0x9C00 : 0x9800);
        int tileDataBase = unsignedTileData ? 0x8000 : 0x9000;
        int xPosition = windowLayer ? (screenX - windowX) : ((screenX + memory.ReadRegisterDirect(regScx)) & 0xFF);
        int yPosition = windowLayer ? activeWindowLine : ((scanline + memory.ReadRegisterDirect(regScy)) & 0xFF);
        int tileRow = yPosition / 8;
        int tileColumn = (xPosition & 0xFF) / 8;

        int tileAddress = tileMapBase + (tileRow * 32) + tileColumn;
        int tileLine = yPosition % 8;
        LoadBackgroundTileRow(windowLayer, tileAddress, tileLine, unsignedTileData, tileDataBase, cgbMode);

        int bit = 7 - (xPosition % 8);
        if (cgbMode && (backgroundTileCacheAttributes & 0x20) != 0) {
            bit = xPosition % 8;
        }
        int high = (backgroundTileCacheHighByte >> bit) & 1;
        int low = (backgroundTileCacheLowByte >> bit) & 1;
        int colourIndex = (high << 1) | low;

        backgroundPriorityBuffer[screenX] = colourIndex;
        backgroundTilePriorityBuffer[screenX] = cgbMode && (backgroundTileCacheAttributes & 0x80) != 0;

        if (cgbMode) {
            return memory.ReadCgbBackgroundPaletteColourRgb(backgroundTileCacheAttributes & 0x07, colourIndex);
        }
        if (dmgCompatibilityMode) {
            return ResolveDmgCompatibilityBackgroundColour(colourIndex);
        }
        return ResolveDmgPaletteColour(memory.ReadRegisterDirect(regBgp), activeBackgroundPalette, colourIndex);
    }

    private int ResolveSpritePixel(int screenX, int lcdControl, boolean cgbMode, boolean dmgCompatibilityMode) {
        if ((lcdControl & 0x02) == 0) {
            return noSpritePixel;
        }

        boolean bgMasterPriority = cgbMode && (lcdControl & 0x01) != 0;

        for (int spriteIndex = 0; spriteIndex < visibleSpriteCount; spriteIndex++) {
            int colour = ResolveSpritePixelFromEntry(
                    screenX, spriteIndex, cgbMode, dmgCompatibilityMode, bgMasterPriority);
            if (colour != noSpritePixel) {
                return colour;
            }
        }
        return noSpritePixel;
    }

    private int ResolveSpritePixelFromEntry(int screenX, int spriteIndex, boolean cgbMode,
            boolean dmgCompatibilityMode, boolean bgMasterPriority) {
        int attributes = visibleSpriteAttributes[spriteIndex];
        int spriteStartX = visibleSpriteX[spriteIndex];
        if (screenX < spriteStartX || screenX >= spriteStartX + 8) {
            return noSpritePixel;
        }

        int localX = screenX - spriteStartX;
        int bit = (attributes & 0x20) != 0 ? localX : 7 - localX;
        int high = (visibleSpriteRowHigh[spriteIndex] >> bit) & 1;
        int low = (visibleSpriteRowLow[spriteIndex] >> bit) & 1;
        int colourIndex = (high << 1) | low;

        if (colourIndex == 0) {
            return noSpritePixel;
        }

        if (dmgCompatibilityMode) {
            if ((attributes & 0x80) != 0 && backgroundPriorityBuffer[screenX] != 0) {
                return noSpritePixel;
            }
        } else if (cgbMode) {
            if (bgMasterPriority && backgroundPriorityBuffer[screenX] != 0
                    && (((attributes & 0x80) != 0) || backgroundTilePriorityBuffer[screenX])) {
                return noSpritePixel;
            }
        } else if ((attributes & 0x80) != 0 && backgroundPriorityBuffer[screenX] != 0) {
            return noSpritePixel;
        }

        if (cgbMode) {
            return memory.ReadCgbObjectPaletteColourRgb(attributes & 0x07, colourIndex);
        }
        if (dmgCompatibilityMode) {
            return ResolveDmgCompatibilitySpriteColour(attributes, colourIndex);
        }
        return ResolveDmgPaletteColour(
                memory.ReadRegisterDirect((attributes & 0x10) != 0 ? regObp1 : regObp0),
                (attributes & 0x10) != 0 ? activeSpritePalette1 : activeSpritePalette0,
                colourIndex);
    }

    private int LoadSpritesOnScanline(boolean use8x16) {
        int visibleSpriteCount = 0;
        int spriteHeight = use8x16 ? 16 : 8;

        for (int index = 0; index < 40; index++) {
            int address = 0xFE00 + (index * 4);
            int y = memory.ReadOamByte(address) - 16;
            int x = memory.ReadOamByte(address + 1) - 8;
            int tile = memory.ReadOamByte(address + 2);
            int attributes = memory.ReadOamByte(address + 3);

            if (scanline >= y && scanline < (y + spriteHeight)) {
                visibleSpriteY[visibleSpriteCount] = y;
                visibleSpriteX[visibleSpriteCount] = x;
                visibleSpriteTile[visibleSpriteCount] = tile;
                visibleSpriteAttributes[visibleSpriteCount] = attributes;
                CacheVisibleSpriteRow(visibleSpriteCount, use8x16, y, tile, attributes);
                visibleSpriteCount++;
            }

            if (visibleSpriteCount >= maxSpritesPerScanline) {
                break;
            }
        }

        if ((!memory.IsCgbMode() || memory.IsDmgCompatibilityMode()
                || memory.ReadRegisterDirect(GBMemAddresses.OPRI) != 0) && visibleSpriteCount > 1) {
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
            int spriteRowLow = visibleSpriteRowLow[index];
            int spriteRowHigh = visibleSpriteRowHigh[index];
            int compareIndex = index - 1;

            while (compareIndex >= 0 && spriteX < visibleSpriteX[compareIndex]) {
                visibleSpriteY[compareIndex + 1] = visibleSpriteY[compareIndex];
                visibleSpriteX[compareIndex + 1] = visibleSpriteX[compareIndex];
                visibleSpriteTile[compareIndex + 1] = visibleSpriteTile[compareIndex];
                visibleSpriteAttributes[compareIndex + 1] = visibleSpriteAttributes[compareIndex];
                visibleSpriteRowLow[compareIndex + 1] = visibleSpriteRowLow[compareIndex];
                visibleSpriteRowHigh[compareIndex + 1] = visibleSpriteRowHigh[compareIndex];
                compareIndex--;
            }

            visibleSpriteY[compareIndex + 1] = spriteY;
            visibleSpriteX[compareIndex + 1] = spriteX;
            visibleSpriteTile[compareIndex + 1] = spriteTile;
            visibleSpriteAttributes[compareIndex + 1] = spriteAttributes;
            visibleSpriteRowLow[compareIndex + 1] = spriteRowLow;
            visibleSpriteRowHigh[compareIndex + 1] = spriteRowHigh;
        }
    }

    private int ResolveDmgPaletteColour(int paletteRegister, int[] paletteColours, int colourIndex) {
        int colourId = ResolveDmgPaletteIndex(paletteRegister, colourIndex);
        return paletteColours[colourId];
    }

    private int ResolveDmgPaletteIndex(int paletteRegister, int colourIndex) {
        int shift = colourIndex * 2;
        return (paletteRegister >> shift) & 0x03;
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
        return Settings.nonGbcColourMode == NonGbcColourMode.GBC_COLOURISATION && !memory.IsLoadedRomCgbCompatible();
    }

    private void LoadBackgroundTileRow(boolean windowLayer, int tileAddress, int tileLine, boolean unsignedTileData,
            int tileDataBase, boolean cgbMode) {
        if (backgroundTileCacheValid
                && backgroundTileCacheWindowLayer == windowLayer
                && backgroundTileCacheTileAddress == tileAddress
                && backgroundTileCacheTileLine == tileLine
                && backgroundTileCacheUnsignedTileData == unsignedTileData
                && backgroundTileCacheCgbMode == cgbMode) {
            return;
        }

        int tileNumber = cgbMode ? memory.ReadVideoRam(0, tileAddress) : memory.Read(tileAddress);
        int tileAttributes = cgbMode ? memory.ReadVideoRam(1, tileAddress) : 0;
        if (!unsignedTileData) {
            tileNumber = (byte) tileNumber;
        }

        int resolvedTileLine = tileLine;
        if (cgbMode && (tileAttributes & 0x40) != 0) {
            resolvedTileLine = 7 - resolvedTileLine;
        }

        int tileLineAddress = tileDataBase + (tileNumber * 16);
        int lineOffset = resolvedTileLine * 2;
        int vramBank = cgbMode && (tileAttributes & 0x08) != 0 ? 1 : 0;

        backgroundTileCacheWindowLayer = windowLayer;
        backgroundTileCacheTileAddress = tileAddress;
        backgroundTileCacheTileLine = tileLine;
        backgroundTileCacheUnsignedTileData = unsignedTileData;
        backgroundTileCacheCgbMode = cgbMode;
        backgroundTileCacheAttributes = tileAttributes;
        backgroundTileCacheLowByte = cgbMode
                ? memory.ReadVideoRam(vramBank, tileLineAddress + lineOffset)
                : memory.Read(tileLineAddress + lineOffset);
        backgroundTileCacheHighByte = cgbMode
                ? memory.ReadVideoRam(vramBank, tileLineAddress + lineOffset + 1)
                : memory.Read(tileLineAddress + lineOffset + 1);
        backgroundTileCacheValid = true;
    }

    private void CacheVisibleSpriteRow(int spriteIndex, boolean use8x16, int spriteY, int tile, int attributes) {
        int line = scanline - spriteY;
        int spriteHeight = use8x16 ? 16 : 8;
        if ((attributes & 0x40) != 0) {
            line = spriteHeight - 1 - line;
        }

        int tileIndex = tile;
        if (use8x16) {
            tileIndex &= 0xFE;
            if (line >= 8) {
                tileIndex |= 0x01;
                line -= 8;
            }
        }

        int tileAddress = 0x8000 + (tileIndex * 16) + (line * 2);
        int vramBank = (attributes & 0x08) != 0 ? 1 : 0;
        boolean useCgbSpriteTileAttributes = memory.IsCgbMode() && !memory.IsDmgCompatibilityMode();
        visibleSpriteRowLow[spriteIndex] = useCgbSpriteTileAttributes
                ? memory.ReadVideoRam(vramBank, tileAddress)
                : memory.Read(tileAddress);
        visibleSpriteRowHigh[spriteIndex] = useCgbSpriteTileAttributes
                ? memory.ReadVideoRam(vramBank, tileAddress + 1)
                : memory.Read(tileAddress + 1);
    }

    private int ResolveDmgCompatibilityBackgroundColour(int colourIndex) {
        int mappedColourIndex = ResolveDmgPaletteIndex(memory.ReadRegisterDirect(regBgp), colourIndex);
        return memory.ReadCgbBackgroundPaletteColourRgb(0, mappedColourIndex);
    }

    private int ResolveDmgCompatibilitySpriteColour(int attributes, int colourIndex) {
        boolean usesPalette1 = (attributes & 0x10) != 0;
        int mappedColourIndex = ResolveDmgPaletteIndex(
                memory.ReadRegisterDirect(usesPalette1 ? regObp1 : regObp0),
                colourIndex);
        return memory.ReadCgbObjectPaletteColourRgb(usesPalette1 ? 1 : 0, mappedColourIndex);
    }

    private void InvalidatePixelTransferCaches() {
        backgroundTileCacheValid = false;
    }
}
