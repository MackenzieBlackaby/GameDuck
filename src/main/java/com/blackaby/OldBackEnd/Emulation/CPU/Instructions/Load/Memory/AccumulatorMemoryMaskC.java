package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD (FF00 + C), A instruction.
 * 
 * Writes the accumulator (A) to the I/O register at address 0xFF00 + C.
 * This is typically used for hardware I/O operations.
 */
public class AccumulatorMemoryMaskC extends Instruction {
    /**
     * Constructs the instruction to store A into an I/O register using C as the
     * offset.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public AccumulatorMemoryMaskC(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 2);
    }

    /**
     * Executes the instruction.
     * 
     * Writes the value of the accumulator (A) to the address 0xFF00 + C.
     */
    @Override
    public void run() {
        int address = 0xFF00 | cpu.getC();
        int value = cpu.getAccumulator();
        memory.write(address, value);
    }
}