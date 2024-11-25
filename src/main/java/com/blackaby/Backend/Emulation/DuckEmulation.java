package com.blackaby.Backend.Emulation;

import com.blackaby.Backend.Emulation.CPU.*;
import com.blackaby.Backend.Emulation.Misc.ROM;
import com.blackaby.Backend.Emulation.Misc.Specifics;
import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;

/**
 * This class represents the emulation of the GameBoy
 * It has methods for starting the emulation and getting the CPU and display
 */
public class DuckEmulation implements Runnable {

    // Emulated Hardware Parts
    private DuckCPU cpu;
    private DuckDisplay display;
    private ROM rom;

    // Threading Variables
    private Thread emulationThread;
    private volatile boolean running = false;
    private volatile boolean paused = false;

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
     * This method starts the emulation with the given ROM file
     * 
     * @param romfile The ROM file to be loaded
     */
    public void startEmulation(String romfile) {
        running = true;
        paused = false;
        rom = new ROM(romfile);
        emulationThread = new Thread(this);
        emulationThread.start();
    }

    /**
     * This method returns the display of the emulation
     * 
     * @return The display of the emulation
     */
    public DuckDisplay getDisplay() {
        return display;
    }

    @Override
    public void run() {
        running = true;
        long lastFrameTime = System.currentTimeMillis();
        while (running) {
            while (paused)
                ;
            if (System.currentTimeMillis() - lastFrameTime >= Specifics.CYCLE_DELAY) {
                int instruction[] = ReadNextInstruction();
                if (instruction[0] == 0x00) {
                    break;
                } else {
                    cpu.queueInstruction(instruction[0], instruction[1], instruction[2]);
                }
                cpu.executeNextInstruction();
                lastFrameTime = System.currentTimeMillis();
            }
        }
    }

    /**
     * This method reads the next instruction from the ROM, incrementing the PC and
     * returning the instruction
     * as an array of integers
     * 
     * @return The next instruction as an array of integers
     */
    private int[] ReadNextInstruction() {
        // Get the next instruction from the ROM
        int opcode, operands[];
        try {
            opcode = rom.getOpcode(cpu.regGet(Register.PC));
            operands = rom.getOperands(cpu.regGet(Register.PC));
        } catch (ArrayIndexOutOfBoundsException e) {
            return new int[] { 0x00, 0x00, 0x00 };
        }
        // Increment the PC
        cpu.regIncrement(Register.PC);
        // Parse the instruction into integers
        int[] instruction = { 0, 0, 0 };
        instruction[0] = opcode;
        for (int i = 0; i < operands.length; i++) {
            instruction[i + 1] = operands[i];
        }
        return instruction;
    }
}
