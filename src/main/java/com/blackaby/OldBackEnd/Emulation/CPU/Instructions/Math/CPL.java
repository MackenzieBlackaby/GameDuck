package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the CPL (Complement Accumulator) instruction.
 * 
 * Inverts all bits in the accumulator (A).
 * Sets the N and H flags.
 */
public class CPL extends Instruction {

    /**
     * Constructs the CPL instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public CPL(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 1);
    }

    /**
     * Executes the CPL instruction.
     * 
     * - Flips all bits in the accumulator (A)
     * - Sets the subtract (N) and half-carry (H) flags
     */
    @Override
    public void run() {
        int value = ~cpu.getAccumulator() & 0xFF;
        cpu.setAccumulator(value);
        cpu.setFlag(DuckCPU.Flag.N, true);
        cpu.setFlag(DuckCPU.Flag.H, true);
    }
}
