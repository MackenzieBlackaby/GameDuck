package com.blackaby.Backend.Emulation.CPU.Instructions;

import com.blackaby.Frontend.DuckDisplay;

/**
 * This class represents a debug instruction for the CPU
 * It makes the display orange.
 */
public class DebugDisplay extends GDuckstruction {

    public DebugDisplay(DuckDisplay bound) {
        super(bound);
    }

    /**
     * This method executes the instruction
     * It makes the display orange.
     */
    @Override
    public void execute() {
        for (int x = 0; x < 160; x++) {
            for (int y = 0; y < 144; y++) {
                bound.setPixel(x, y, "#ff4400", false);
            }
        }
    }
}
