package com.blackaby.Backend.Emulation;

import com.blackaby.Backend.Emulation.CPU.*;
import com.blackaby.Frontend.DuckDisplay;

/**
 * This class represents the emulation of the GameBoy
 * It has methods for starting the emulation and getting the CPU and display
 */
public class DuckEmulation {

    private DuckCPU cpu;
    private DuckDisplay display;

    /**
     * This constructor creates a new DuckEmulation
     * 
     * @param display The display to be used in the emulation
     */
    public DuckEmulation(DuckDisplay display) {
        cpu = new DuckCPU(this);
        this.display = display;
    }

    /**
     * This method returns the CPU of the emulation
     * 
     * @return The CPU of the emulation
     */
    public DuckCPU getCPU() {
        return cpu;
    }

    /**
     * This method returns the display of the emulation
     * 
     * @return The display of the emulation
     */
    public DuckDisplay getDisplay() {
        return display;
    }

    /**
     * This method starts the emulation
     */
    public void start() {
        cpu.queueInstruction(0xFF);
        cpu.executeNextInstruction();
    }
}
