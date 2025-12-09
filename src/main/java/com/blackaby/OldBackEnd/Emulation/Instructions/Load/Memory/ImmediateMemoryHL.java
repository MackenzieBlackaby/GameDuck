package com.blackaby.OldBackEnd.Emulation.Instructions.Load.Memory;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.Instruction;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Implements the LD (HL), n instruction.
 * 
 * Stores an immediate 8-bit value into the memory location pointed to by HL.
 */
public class ImmediateMemoryHL extends Instruction {

    /**
     * Constructs the instruction to store an immediate value at (HL).
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public ImmediateMemoryHL(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 3);
    }

    /**
     * Executes the instruction.
     * 
     * Writes the 8-bit immediate operand to the address specified by the HL
     * register pair.
     */
    @Override
    public void run() {
        int address = cpu.getHLValue();
        int value = operands[0] & 0xFF;
        memory.write(address, value);
    }
}
