package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Misc;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the NOP (No Operation) instruction.
 * 
 * Consumes a cycle without performing any operation.
 * Typically used for timing alignment or instruction padding.
 */
public class Nop extends Instruction {

    /**
     * Constructs the NOP instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public Nop(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 1);
    }

    /**
     * Executes the NOP instruction.
     * 
     * Does nothing.
     */
    @Override
    public void run() {
        // Do nothing
    }

}
