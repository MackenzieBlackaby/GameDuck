package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD A, (nn) instruction.
 * 
 * Loads a value from the specified 16-bit immediate memory address into the
 * accumulator (A).
 */
public class MemoryImmediateAccumulator extends Instruction {
    /**
     * Constructs the instruction to load A from a 16-bit immediate address.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public MemoryImmediateAccumulator(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 4);
    }

    /**
     * Executes the instruction.
     * 
     * Reads a byte from memory at the 16-bit address provided by the operands,
     * and stores it in the accumulator (A).
     */
    @Override
    public void run() {
        int address = 0xFFFF & ((operands[1] << 8) | (operands[0] & 0xFF));
        int value = memory.read(address);
        cpu.setAccumulator(value);
    }
}
