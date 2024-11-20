package com.blackaby.Backend.Emulation.CPU.Instructions;

import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Misc.Settings;

/**
 * This class represents a debug instruction for the CPU
 * It makes the display orange.
 */
public class DebugDisplay extends GDuckstruction {

    private static final int PILLAR_WIDTH = 4;

    public DebugDisplay(DuckDisplay bound) {
        super(bound);
    }

    private String getColor(int currentX) {
        switch ((currentX / PILLAR_WIDTH) % 4) {
            case 0:
                return Settings.GB_COLOR_0;
            case 1:
                return Settings.GB_COLOR_1;
            case 2:
                return Settings.GB_COLOR_2;
            case 3:
                return Settings.GB_COLOR_3;
            default:
                break;
        }
        return null;
    }

    /**
     * This method executes the instruction
     * It makes the display orange.
     */
    @Override
    public void execute() {
        for (int x = 0; x < 160; x++) {
            for (int y = 0; y < 144; y++) {
                bound.setPixel(x, y, getColor(x), false);
            }
        }
    }
}
