package com.blackaby.Backend.Emulation.CPU;

import java.util.LinkedList;
import java.util.Queue;

import com.blackaby.Backend.Emulation.DuckEmulation;
import com.blackaby.Backend.Emulation.CPU.Instructions.*;

/**
 * This class represents the CPU of the GameBoy.
 * It has methods for executing instructions and managing registers.
 * It also has methods for reading and writing to memory.
 * It is the main class for the CPU emulation.
 * 
 * The instructions are implemented as methods in this class.
 * The CPU has a reference to the memory and the display.
 */
public class DuckCPU {

    public enum Register {
        PC,
        SP,
        A,
        B,
        C,
        D,
        E,
        H,
        L,
        F
    }

    // Emulated Parts
    private Queue<Duckstruction> instructionQueue;
    private DuckEmulation boundEmulator;

    // Registers
    private int programCounter = 0;

    public DuckCPU(DuckEmulation boundEmulator) {
        this.boundEmulator = boundEmulator;
        instructionQueue = new LinkedList<>();
        programCounter = 0;
    }

    /**
     * This method queues an instruction
     */
    public void queueInstruction(byte opcode, int... values) {
        InstructionType type = InstructionType.fromOpcode(opcode);
        switch (type) {
            case DEBUG_DISPLAY:
                instructionQueue.add(new DebugDisplay(boundEmulator.getDisplay()));
                break;
            case DEBUG_CONSOLE:
                instructionQueue.add(new DebugConsole());
                break;
            default:
                throw new IllegalArgumentException("Unknown opcode: " + opcode);
        }
    }

    /**
     * This method executes the next instruction in the queue
     */
    public void executeNextInstruction() {
        Duckstruction instruction = instructionQueue.poll();
        if (instruction != null) {
            instruction.execute();
        }
    }

    /**
     * This method sets the value of the given register
     * 
     * @param reg   The register to set
     * @param value The value to set the register to
     * @throws IllegalArgumentException If the register is unknown
     */
    public void regSet(Register reg, int value) throws IllegalArgumentException {
        switch (reg) {
            case PC:
                programCounter = value;
                break;
            default:
                throw new IllegalArgumentException("Unknown register: " + reg);
        }
    }

    /**
     * This method gets the value of the given register
     * 
     * @param reg The register to get
     * @return The value of the register
     * @throws IllegalArgumentException If the register is unknown
     */
    public int regGet(Register reg) throws IllegalArgumentException {
        switch (reg) {
            case PC:
                return programCounter;
            default:
                throw new IllegalArgumentException("Unknown register: " + reg);
        }
    }

    /**
     * This method increments the value of the given register
     * 
     * @param reg The register to increment
     */
    public void regIncrement(Register reg) {
        int value = regGet(reg);
        regSet(reg, value + 1);
    }
}
