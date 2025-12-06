package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD (DE), A instruction.
 * 
 * Stores the value in the accumulator (A) at the address pointed to by DE.
 */
public class AccumulatorMemoryDE extends Instruction {
    /**
     * Constructs the instruction to store A into memory at address DE.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public AccumulatorMemoryDE(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 2);
    }

    /**
     * Executes the instruction.
     * 
     * Writes the accumulator (A) to the memory location specified by the DE
     * register pair.
     */
    @Override
    public void run() {
        int address = cpu.getDEValue();
        int value = cpu.getAccumulator();
        memory.write(address, value);
    }
}
