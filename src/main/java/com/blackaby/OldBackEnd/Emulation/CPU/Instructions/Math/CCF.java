package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the CCF (Complement Carry Flag) instruction.
 * 
 * Flips the state of the carry flag (C).
 * Clears the N and H flags.
 */
public class CCF extends Instruction {

    /**
     * Constructs the CCF instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public CCF(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 1);
    }

    /**
     * Executes the CCF instruction.
     * 
     * - Inverts the carry flag (C)
     * - Clears the subtract flag (N) and half-carry flag (H)
     */
    @Override
    public void run() {
        boolean carry = cpu.getFlagBoolean(DuckCPU.Flag.C);
        cpu.setFlag(DuckCPU.Flag.C, !carry);
        cpu.setFlag(DuckCPU.Flag.N, false);
        cpu.setFlag(DuckCPU.Flag.H, false);
    }
}
