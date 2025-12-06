package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD r, (HL) instruction.
 * 
 * Loads the value from the memory address pointed to by HL into the specified
 * 8-bit register.
 */
public class MemoryRegisterHL extends Instruction {

    /**
     * Constructs the instruction to load a register from memory at address HL.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public MemoryRegisterHL(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 2);
    }

    /**
     * Executes the instruction.
     * 
     * Reads a byte from memory at the address in HL and stores it in the
     * destination register.
     */
    @Override
    public void run() {
        Register destination = Register.getRegFrom3Bit(opcodeValues[0]);
        int address = cpu.getHLValue();
        int value = memory.read(address);
        cpu.regSet(destination, value);
    }
}
