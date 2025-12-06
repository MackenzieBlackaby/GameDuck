package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD A, (BC) instruction.
 * 
 * Loads the value from the memory address pointed to by BC into the accumulator
 * (A).
 */
public class MemoryBCAccumulator extends Instruction {
    /**
     * Constructs the instruction to load A from memory at address BC.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public MemoryBCAccumulator(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 2);
    }

    /**
     * Executes the instruction.
     * 
     * Reads a byte from the memory location pointed to by BC
     * and stores it in the accumulator (A).
     */
    @Override
    public void run() {
        int address = cpu.getBCValue();
        int value = memory.read(address);
        cpu.setAccumulator(value);
    }
}
