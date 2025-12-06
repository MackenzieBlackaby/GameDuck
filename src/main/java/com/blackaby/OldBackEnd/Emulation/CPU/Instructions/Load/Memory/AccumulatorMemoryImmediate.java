package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD (nn), A instruction.
 * 
 * Stores the value in the accumulator (A) at the specified 16-bit memory
 * address.
 */
public class AccumulatorMemoryImmediate extends Instruction {
    /**
     * Constructs the instruction to store A at a 16-bit immediate address.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public AccumulatorMemoryImmediate(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 4);
    }

    /**
     * Executes the instruction.
     * 
     * Writes the accumulator (A) to the memory location given by a 16-bit immediate
     * address.
     */
    @Override
    public void run() {
        int address = 0xFFFF & ((operands[1] << 8) | (operands[0] & 0xFF));
        int value = cpu.getAccumulator();
        memory.write(address, value);
    }
}
