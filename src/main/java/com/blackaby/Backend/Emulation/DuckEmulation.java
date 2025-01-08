package com.blackaby.Backend.Emulation;

import com.blackaby.Backend.Emulation.CPU.*;
import com.blackaby.Backend.Emulation.Misc.ROM;
import com.blackaby.Backend.Emulation.Misc.Specifics;
import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;
import com.blackaby.Backend.Emulation.CPU.Instructions.SpecificInstructions.Duckstruction;
import com.blackaby.Backend.Emulation.CPU.Instructions.InstructionTypeManager;
import com.blackaby.Backend.Emulation.CPU.Instructions.InstructionTypeManager.InstructionType;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * This class represents the emulation of the GameBoy
 * It has methods for starting the emulation and getting the CPU and display
 */
public class DuckEmulation implements Runnable {

    // Emulated Hardware Parts
    private DuckCPU cpu;
    private DuckMemory memory;
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
        cpu = new DuckCPU();
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
     * This method returns the memory of the emulation
     * 
     * @return The memory of the emulation
     */
    public DuckMemory getMemory() {
        return memory;
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
     * This method pauses or resumes the emulation
     */
    public void pauseEmulation() {
        paused = !paused;
    }

    /**
     * This method stops the emulation
     */
    public void stopEmulation() {
        running = false;
        paused = false;
        display.clear();
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
                Duckstruction instruction = ReadNextInstruction();
                if (instruction == null) {
                    break;
                }
                cpu.queueInstruction(instruction);
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
    private Duckstruction ReadNextInstruction() {
        byte opcode;
        try {
            opcode = rom.getByte(cpu.regGet16(Register.PC));
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }

        // Get operand type
        InstructionType type = InstructionTypeManager.getType(opcode);

        // Get operand count
        int operandCount = type.getOperandCount();

        // Read in next bytes
        byte[] operands = {};
        if (operandCount != 0)
            operands = rom.getBytes(cpu.regGet16(Register.PC) + 1, operandCount);

        // Increment PC by 1 + operandCount
        cpu.regSet16(Register.PC, (short) (cpu.regGet16(Register.PC) + 1 + operandCount));

        return InstructionTypeManager.constructInstruction(this, type, opcode, operands);
    }
}
