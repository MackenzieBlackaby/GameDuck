package com.blackaby.Backend.Emulation.CPU;

import com.blackaby.Backend.Emulation.CPU.DuckCPU.Interrupt;
import com.blackaby.Backend.Emulation.Graphics.GBColor;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;
import com.blackaby.Backend.Emulation.Misc.Specifics;
import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Misc.Settings;

/**
 * DuckPPU class
 */
public class DuckPPU {

    public static final int HBLANK_DURATION = 204;
    public static final int VBLANK_DURATION = 4560;
    public static final int OAM_DURATION = 80;
    public static final int VRAM_DURATION = 172;

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
     * Constructor for the DuckPPU class
     * 
     * @param cpu    the DuckCPU object
     * @param memory the DuckMemory object
     */
    public DuckPPU(DuckCPU cpu, DuckMemory memory, DuckDisplay display) {
        this.cpu = cpu;
        this.memory = memory;
        this.display = display;
        setMode(PPUMode.OAM);
    }

    private void setMode(PPUMode mode) {
        this.mode = mode;
    }

    public void step() {
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
                    renderScanline();
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
                if (cycle >= VBLANK_DURATION) {
                    cycle = 0;
                    scanline = 0;
                    setMode(PPUMode.OAM);
                }
                break;
        }
    }

    private void renderScanline() {
        for (int x = 0; x < Specifics.GB_DISPLAY_WIDTH; x++) {
            // Fetch tile data from VRAM
            int tileIndex = (scanline / 8) * 32 + (x / 8);
            int tileAddress = DuckMemory.VRAM_START + tileIndex * 16;
            int line = scanline % 8;
            int byte1 = memory.read(tileAddress + line * 2);
            int byte2 = memory.read(tileAddress + line * 2 + 1);

            // Calculate pixel color
            int bit = 7 - (x % 8);
            int colorIndex = ((byte1 >> bit) & 1) | (((byte2 >> bit) & 1) << 1);
            GBColor color = getColorFromPalette(colorIndex);

            // Set pixel on display
            display.setPixel(x, scanline, color.toColor(), false);
        }
        display.repaint();
    }

    private GBColor getColorFromPalette(int colorIndex) {
        switch (colorIndex) {
            case 0:
                return Settings.GB_COLOR_0_OBJ;
            case 1:
                return Settings.GB_COLOR_1_OBJ;
            case 2:
                return Settings.GB_COLOR_2_OBJ;
            case 3:
                return Settings.GB_COLOR_3_OBJ;
            default:
                return null;
        }
    }
}
