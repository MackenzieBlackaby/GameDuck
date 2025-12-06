package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD (BC), A instruction.
 * 
 * Stores the value in the accumulator (A) at the address pointed to by BC.
 */
public class AccumulatorMemoryBC extends Instruction {
    /**
     * Constructs the instruction to store A into memory at address BC.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public AccumulatorMemoryBC(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 2);
    }

    /**
     * Executes the instruction.
     * 
     * Writes the accumulator (A) to the memory location specified by the BC
     * register pair.
     */
    @Override
    public void run() {
        int address = cpu.getBCValue();
        int value = cpu.getAccumulator();
        memory.write(address, value);
    }
}
