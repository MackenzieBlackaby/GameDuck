package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD A, (FF00 + n) instruction.
 * 
 * Loads a value from the I/O register at address 0xFF00 + n into the
 * accumulator (A).
 * The offset n is an 8-bit immediate value.
 */
public class MemoryAccumulatorMaskImmediate extends Instruction {
    /**
     * Constructs the instruction to load A from a high-memory I/O register with an
     * immediate offset.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public MemoryAccumulatorMaskImmediate(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 3);
    }

    /**
     * Executes the instruction.
     * 
     * Reads a byte from memory at address 0xFF00 + n,
     * where n is an 8-bit immediate operand,
     * and stores the result in the accumulator (A).
     */
    @Override
    public void run() {
        int lsb = operands[0];
        int address = 0xFF00 | (lsb & 0xFF);
        int value = memory.read(address);
        cpu.setAccumulator(value);
    }
}