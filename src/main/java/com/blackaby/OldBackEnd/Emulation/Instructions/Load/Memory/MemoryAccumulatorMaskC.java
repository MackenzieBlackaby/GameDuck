package com.blackaby.OldBackEnd.Emulation.Instructions.Load.Memory;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.Instruction;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Implements the LD A, (FF00 + C) instruction.
 * 
 * Loads the value from the I/O register at address 0xFF00 + C into the
 * accumulator (A).
 */
public class MemoryAccumulatorMaskC extends Instruction {
    /**
     * Constructs the instruction to load A from an I/O register using C as the
     * offset.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public MemoryAccumulatorMaskC(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 2);
    }

    /**
     * Executes the instruction.
     * 
     * Reads a byte from memory at address 0xFF00 + C
     * and stores it in the accumulator (A).
     */
    @Override
    public void run() {
        int c = cpu.getC();
        int address = 0xFF00 | (c & 0xFF);
        int value = memory.read(address);
        cpu.setAccumulator(value);
        
    }
}