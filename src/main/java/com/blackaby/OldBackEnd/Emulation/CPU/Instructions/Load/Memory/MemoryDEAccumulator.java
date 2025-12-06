package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD A, (DE) instruction.
 * 
 * Loads the value from the memory address pointed to by DE into the accumulator
 * (A).
 */
public class MemoryDEAccumulator extends Instruction {
    /**
     * Constructs the instruction to load A from memory at address DE.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public MemoryDEAccumulator(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 2);
    }

    /**
     * Executes the instruction.
     * 
     * Reads a byte from the memory location pointed to by DE
     * and stores it in the accumulator (A).
     */
    @Override
    public void run() {
        int address = cpu.getDEValue();
        int value = memory.read(address);
        cpu.setAccumulator(value);
    }
}
