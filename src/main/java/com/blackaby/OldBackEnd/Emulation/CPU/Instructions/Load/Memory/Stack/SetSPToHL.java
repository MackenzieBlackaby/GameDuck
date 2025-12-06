package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory.Stack;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD SP, HL instruction.
 * 
 * Copies the value in HL into the stack pointer (SP).
 * Does not affect flags.
 */
public class SetSPToHL extends Instruction {

    /**
     * Constructs the instruction to copy HL into SP.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public SetSPToHL(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 2);
    }

    /**
     * Executes the instruction.
     * 
     * Sets SP to the current value of HL.
     * No flags are modified.
     */
    @Override
    public void run() {
        int hl = 0xFFFF & cpu.getHLValue();
        cpu.setSP(hl);
    }
}