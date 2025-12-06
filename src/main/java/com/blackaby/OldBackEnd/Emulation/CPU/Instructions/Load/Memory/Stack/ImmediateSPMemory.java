package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory.Stack;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD (nn), SP instruction.
 * 
 * Stores the current stack pointer (SP) into the given 16-bit immediate memory
 * address.
 * The value is stored in little-endian order: low byte first, then high byte.
 */
public class ImmediateSPMemory extends Instruction {

    /**
     * Constructs an SP store instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public ImmediateSPMemory(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 5);
    }

    /**
     * Executes the instruction.
     * 
     * Writes the current stack pointer to the specified address in memory,
     * storing the lower byte first, followed by the upper byte.
     */
    @Override
    public void run() {
        int sp = cpu.getSP();
        int address = ((operands[1] & 0xFF) << 8) | (operands[0] & 0xFF);
        memory.write(address, sp & 0xFF); // Store LSB
        memory.write(address + 1, (sp >> 8) & 0xFF); // Store MSB
    }
}
