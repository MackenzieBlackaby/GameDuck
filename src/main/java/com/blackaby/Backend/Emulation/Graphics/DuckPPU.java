package com.blackaby.Backend.Emulation.Graphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.DuckSprite;
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

    @Deprecated public static final int OAM_DURATION = oamDuration;
    @Deprecated public static final int VRAM_DURATION = vramDuration;

    private static final int scanlineCycles = 456;
    private static final int vblankLines = 10;
    private static final int screenHeight = 144;
    private static final int screenWidth = 160;

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

    private PpuMode mode;
    private int scanline;
    private int cycle;
    private boolean statInterruptLine;

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
    }

    private void HandleLcdDisabled() {
        scanline = 0;
        cycle = 0;
        mode = PpuMode.HBLANK;
        statInterruptLine = false;
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
        int[] activeBackgroundPalette = cgbMode ? null : ActiveBackgroundPaletteRgb(useGbcColourisation);
        int[] activeSpritePalette0 = cgbMode ? null : ActiveSpritePalette0Rgb(useGbcColourisation);
        int[] activeSpritePalette1 = cgbMode ? null : ActiveSpritePalette1Rgb(useGbcColourisation);

        for (int x = 0; x < screenWidth; x++) {
            backgroundPriorityBuffer[x] = 0;
            backgroundTilePriorityBuffer[x] = false;
        }

        if (cgbMode || (lcdControl & 0x01) != 0) {
            RenderBackground(lcdControl, activeBackgroundPalette);
            if ((lcdControl & 0x20) != 0) {
                RenderWindow(lcdControl, activeBackgroundPalette);
            }
        } else {
            int white = activeBackgroundPalette[0];
            for (int x = 0; x < screenWidth; x++) {
                display.setPixel(x, scanline, white, false);
            }
        }

        if ((lcdControl & 0x02) != 0) {
            RenderSprites(lcdControl, activeSpritePalette0, activeSpritePalette1);
        }
    }

    private void RenderBackground(int lcdControl, int[] activeBackgroundPalette) {
        int scrollY = memory.Read(regScy);
        int scrollX = memory.Read(regScx);
        boolean cgbMode = memory.IsCgbMode();
        int[] backgroundPalette = cgbMode ? null : DecodePalette(memory.Read(regBgp), activeBackgroundPalette);

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
        int[] backgroundPalette = cgbMode ? null : DecodePalette(memory.Read(regBgp), activeBackgroundPalette);
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
        List<DuckSprite> visibleSprites = GetSpritesOnScanline(use8x16);
        for (DuckSprite sprite : visibleSprites) {
            DrawSprite(sprite, use8x16, activeSpritePalette0, activeSpritePalette1);
        }
    }

    private void DrawSprite(DuckSprite sprite, boolean use8x16, int[] activeSpritePalette0, int[] activeSpritePalette1) {
        boolean cgbMode = memory.IsCgbMode();
        int spriteHeight = use8x16 ? 16 : 8;
        int paletteRegister = sprite.UsesPalette1() ? regObp1 : regObp0;
        int[] palette = cgbMode
                ? null
                : DecodePalette(memory.Read(paletteRegister),
                        sprite.UsesPalette1() ? activeSpritePalette1 : activeSpritePalette0);

        int line = scanline - sprite.y;
        if (sprite.IsYFlip()) {
            line = spriteHeight - 1 - line;
        }

        int tileIndex = sprite.tileIndex;
        if (use8x16) {
            tileIndex &= 0xFE;
            if (line >= 8) {
                tileIndex |= 0x01;
                line -= 8;
            }
        }

        int tileAddress = 0x8000 + (tileIndex * 16) + (line * 2);
        int lowByte = cgbMode ? memory.ReadVideoRam(sprite.VramBank(), tileAddress) : memory.Read(tileAddress);
        int highByte = cgbMode ? memory.ReadVideoRam(sprite.VramBank(), tileAddress + 1) : memory.Read(tileAddress + 1);

        for (int x = 0; x < 8; x++) {
            int pixelX = sprite.x + x;
            if (pixelX < 0 || pixelX >= screenWidth) {
                continue;
            }

            int bit = sprite.IsXFlip() ? x : 7 - x;
            int high = (highByte >> bit) & 1;
            int low = (lowByte >> bit) & 1;
            int colourIndex = (high << 1) | low;

            if (colourIndex == 0) {
                continue;
            }

            if (cgbMode) {
                boolean bgMasterPriority = (memory.Read(regLcdc) & 0x01) != 0;
                if (bgMasterPriority && backgroundPriorityBuffer[pixelX] != 0
                        && (sprite.IsPriorityInternal() || backgroundTilePriorityBuffer[pixelX])) {
                    continue;
                }
            } else if (sprite.IsPriorityInternal() && backgroundPriorityBuffer[pixelX] != 0) {
                continue;
            }

            int colour = cgbMode
                    ? memory.ReadCgbObjectPaletteColourRgb(sprite.CgbPaletteIndex(), colourIndex)
                    : palette[colourIndex];
            display.setPixel(pixelX, scanline, colour, false);
        }
    }

    private List<DuckSprite> GetSpritesOnScanline(boolean use8x16) {
        List<DuckSprite> visibleSprites = new ArrayList<>();
        int spriteHeight = use8x16 ? 16 : 8;

        for (int index = 0; index < 40; index++) {
            int address = 0xFE00 + (index * 4);
            int y = memory.Read(address) - 16;
            int x = memory.Read(address + 1) - 8;
            int tile = memory.Read(address + 2);
            int attributes = memory.Read(address + 3);

            if (scanline >= y && scanline < (y + spriteHeight)) {
                visibleSprites.add(new DuckSprite(y, x, tile, attributes));
            }

            if (visibleSprites.size() >= 10) {
                break;
            }
        }

        if (!memory.IsCgbMode() || memory.Read(DuckAddresses.OPRI) != 0) {
            visibleSprites.sort(Comparator.comparingInt(sprite -> sprite.x));
        }
        return visibleSprites;
    }

    private int[] DecodePalette(int paletteRegister, int[] paletteColours) {
        return new int[] {
                PaletteColourRgb(0, paletteRegister, paletteColours),
                PaletteColourRgb(1, paletteRegister, paletteColours),
                PaletteColourRgb(2, paletteRegister, paletteColours),
                PaletteColourRgb(3, paletteRegister, paletteColours)
        };
    }

    private int PaletteColourRgb(int colourIndex, int paletteRegister, int[] paletteColours) {
        int shift = colourIndex * 2;
        int colourId = (paletteRegister >> shift) & 0x03;
        return paletteColours[Math.max(0, Math.min(3, colourId))];
    }

    private int[] ActiveBackgroundPaletteRgb(boolean useGbcColourisation) {
        if (useGbcColourisation) {
            return PaletteRgb(Settings.gbcBackgroundPaletteObjects);
        }

        return new int[] {
                Settings.gbColour0Object.ToRgb(),
                Settings.gbColour1Object.ToRgb(),
                Settings.gbColour2Object.ToRgb(),
                Settings.gbColour3Object.ToRgb()
        };
    }

    private int[] ActiveSpritePalette0Rgb(boolean useGbcColourisation) {
        if (useGbcColourisation) {
            return PaletteRgb(Settings.gbcSpritePalette0Objects);
        }

        return new int[] {
                Settings.gbColour0Object.ToRgb(),
                Settings.gbColour1Object.ToRgb(),
                Settings.gbColour2Object.ToRgb(),
                Settings.gbColour3Object.ToRgb()
        };
    }

    private int[] ActiveSpritePalette1Rgb(boolean useGbcColourisation) {
        if (useGbcColourisation) {
            return PaletteRgb(Settings.gbcSpritePalette1Objects);
        }

        return new int[] {
                Settings.gbColour0Object.ToRgb(),
                Settings.gbColour1Object.ToRgb(),
                Settings.gbColour2Object.ToRgb(),
                Settings.gbColour3Object.ToRgb()
        };
    }

    private int[] PaletteRgb(GBColor[] palette) {
        return new int[] {
                palette[0].ToRgb(),
                palette[1].ToRgb(),
                palette[2].ToRgb(),
                palette[3].ToRgb()
        };
    }

    private boolean ShouldUseGbcColourisation() {
        return Settings.gbcPaletteModeEnabled && !memory.IsLoadedRomCgbCompatible();
    }

    @Deprecated public void step() { Step(); }
}
