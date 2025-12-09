package com.blackaby.Backend.Emulation.CPU;

import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Abstract base class for all CPU instructions.
 * <p>
 * Defines the contract for instruction execution. Subclasses must implement
 * the {@code run()} method. This class handles the storage of decoded operands,
 * opcode-specific values, and cycle counting management.
 * </p>
 */
public abstract class Instruction { // Removed 'implements Runnable' (optional, see notes below)

    /** Reference to the system's memory bus. */
    protected final DuckMemory memory;

    /** Reference to the CPU executing this instruction. */
    protected final DuckCPU cpu;

    /**
     * Values extracted from the opcode bits (e.g., register source/dest).
     */
    protected int[] opcodeValues;

    /**
     * Raw immediate operands fetched (n8 or n16).
     */
    protected int[] operands;

    /**
     * Current cycle cost. Can be modified by conditional jumps.
     */
    protected int cycles;

    /**
     * The base cycle cost (used to reset after conditional operations).
     */
    private final int baseCycles;

    /**
     * Constructs a new instruction.
     *
     * @param cpu        The CPU instance.
     * @param memory     The memory bus.
     * @param baseCycles The base number of T-Cycles this instruction consumes.
     */
    public Instruction(DuckCPU cpu, DuckMemory memory, int baseCycles) {
        this.cpu = cpu;
        this.memory = memory;
        this.baseCycles = baseCycles;
        this.cycles = baseCycles;
    }

    /**
     * Configures the instruction with specific data for the current execution step.
     *
     * @param opcodeValues Values extracted from the opcode pattern.
     * @param operands     Immediate bytes fetched after the opcode.
     */
    public void setValues(int[] opcodeValues, int[] operands) {
        // We perform the masking here to ensure 8-bit safety
        if (opcodeValues != null) {
            for (int i = 0; i < opcodeValues.length; i++) {
                opcodeValues[i] &= 0xFF;
            }
        }

        if (operands != null) {
            for (int i = 0; i < operands.length; i++) {
                operands[i] &= 0xFF;
            }
        }

        this.opcodeValues = opcodeValues;
        this.operands = operands;
    }

    /**
     * Executes the instruction logic.
     * Must be implemented by specific instruction subclasses.
     */
    public abstract void run();

    /**
     * Helper to get a 16-bit immediate value from operands (Little Endian).
     * Used for instructions like JP a16 (jump to address).
     * * @return The combined 16-bit value (Operand 1 << 8 | Operand 0).
     */
    protected int getImmediate16() {
        if (operands == null || operands.length < 2) {
            throw new IllegalStateException("Instruction requires 16-bit operand but none provided.");
        }
        return (operands[1] << 8) | operands[0];
    }

    /**
     * Helper to get an 8-bit immediate value.
     */
    protected int getImmediate8() {
        if (operands == null || operands.length < 1) {
            throw new IllegalStateException("Instruction requires 8-bit operand but none provided.");
        }
        return operands[0];
    }

    /**
     * Returns the current cycle count for this execution.
     */
    public int getCycleCount() {
        return cycles;
    }

    /**
     * Resets the cycle count to the base value.
     * Call this after a conditional instruction finishes.
     */
    public void resetCycleCount() {
        this.cycles = baseCycles;
    }
}