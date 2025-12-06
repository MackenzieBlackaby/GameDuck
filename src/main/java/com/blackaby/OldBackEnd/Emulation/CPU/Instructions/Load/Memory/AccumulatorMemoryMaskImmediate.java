package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD (FF00 + n), A instruction.
 * 
 * Writes the value in the accumulator (A) to a memory-mapped I/O address in the
 * range 0xFF00–0xFFFF.
 * The address is calculated as 0xFF00 + immediate 8-bit value.
 */
public class AccumulatorMemoryMaskImmediate extends Instruction {
    /**
     * Constructs the instruction to store A into a high-memory I/O register with
     * immediate offset.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public AccumulatorMemoryMaskImmediate(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 3);
    }

    /**
     * Executes the instruction.
     * 
     * Calculates the target address as 0xFF00 + n (where n is an 8-bit immediate),
     * and writes the accumulator (A) to that address.
     */
    @Override
    public void run() {
        int lsb = operands[0] & 0xFF;
        int address = 0xFF00 | (lsb & 0xFF);
        int value = cpu.getAccumulator();
        memory.write(address, value);
    }
}
