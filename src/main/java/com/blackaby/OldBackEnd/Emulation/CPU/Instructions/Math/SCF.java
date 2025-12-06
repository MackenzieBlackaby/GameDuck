package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the SCF (Set Carry Flag) instruction.
 * 
 * Sets the carry flag (C) to true.
 * Clears the subtract (N) and half-carry (H) flags.
 */
public class SCF extends Instruction {

    /**
     * Constructs the SCF instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public SCF(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 1);
    }

    /**
     * Executes the SCF instruction.
     * 
     * - Sets the carry flag (C)
     * - Clears the subtract (N) and half-carry (H) flags
     */
    @Override
    public void run() {
        cpu.setFlag(DuckCPU.Flag.C, true);
        cpu.setFlag(DuckCPU.Flag.N, false);
        cpu.setFlag(DuckCPU.Flag.H, false);
    }
}
