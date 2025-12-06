package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Misc;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the STOP instruction.
 * 
 * Puts the CPU into a low-power stop mode.
 * Typically used in conjunction with hardware events such as key input to
 * resume execution.
 */
public class Stop extends Instruction {

    boolean halt;

    /**
     * Constructs the STOP instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public Stop(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 1);
    }

    /**
     * Executes the STOP instruction.
     * 
     * Sets the CPU's stopped state to true.
     */
    @Override
    public void run() {
        cpu.setStopped(true);
    }
}
