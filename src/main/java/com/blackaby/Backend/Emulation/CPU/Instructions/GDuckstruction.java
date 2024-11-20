package com.blackaby.Backend.Emulation.CPU.Instructions;

import com.blackaby.Frontend.DuckDisplay;

/**
 * This class represents a graphical instruction for the CPU
 */
public class GDuckstruction implements Duckstruction {

    protected DuckDisplay bound;

    public GDuckstruction(DuckDisplay bound) {
        this.bound = bound;
    }

    @Override
    public void execute() {
        
    }
}
