package com.blackaby.OldBackEnd.Emulation.CPU;

import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Abstract base class for all CPU instructions.
 * <p>
 * Each concrete instruction subclass implements the {@code run()} method
 * to define its behaviour. This class handles storage of decoded operands,
 * opcode values, and base cycle counts.
 * </p>
 */

public abstract class Instruction implements Runnable {

    /** Reference to the system's memory bus. */
    protected DuckMemory memory;

    /** Reference to the CPU executing this instruction. */
    protected DuckCPU cpu;

    /**
     * Values extracted from the opcode using the associated instruction mask(s).
     * Used to determine things like registers or bit positions.
     */
    protected int[] opcodeValues;

    /**
     * Raw operands fetched during instruction decoding.
     * Values are masked to 8 bits.
     */
    protected int[] operands;

    /**
     * Base number of cycles this instruction takes to execute.
     * May be adjusted dynamically at runtime in subclasses.
     */
    protected int cycles;
    protected int initCycles;

    /**
     * Constructs a new instruction.
     *
     * @param cpu        The CPU instance executing this instruction.
     * @param memory     The memory bus used during instruction execution.
     * @param cycleCount The base number of CPU cycles this instruction consumes.
     */
    public Instruction(DuckCPU cpu, DuckMemory memory, int cycleCount) {
        this.cpu = cpu;
        this.memory = memory;
        this.cycles = cycleCount;
        this.initCycles = cycleCount;
    }

    /**
     * Sets the decoded values and operands for this instruction.
     * Values are masked to ensure they're within 8-bit range.
     *
     * @param opcodeValues Values extracted from the opcode.
     * @param operands     Operands fetched from memory after the opcode.
     */
    public void setValues(int[] opcodeValues, int[] operands) {
        for (int i = 0; i < opcodeValues.length; i++) {
            opcodeValues[i] = 0xFF & opcodeValues[i];
        }
        for (int i = 0; i < operands.length; i++) {
            operands[i] = 0xFF & operands[i];
        }
        this.operands = operands;
        this.opcodeValues = opcodeValues;
    }

    /**
     * Returns the cycle count for this instruction.
     *
     * @return The number of CPU cycles this instruction consumes.
     */
    public int getCycleCount() {
        return cycles;
    }

    /**
     * Placeholder implementation of {@code run()}.
     * Should be overridden by subclasses to perform actual behaviour.
     */
    @Override
    public void run() {
        System.err.println("Should not run superclass");
    }

    public void resetCycleCount() {
        cycles = initCycles;
    }
}
