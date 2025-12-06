package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD (HL), r instruction.
 * 
 * Stores the value from the specified 8-bit register into the memory address
 * pointed to by HL.
 */
public class RegisterMemoryHL extends Instruction {

    /**
     * Constructs the instruction to store a register value at memory address HL.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public RegisterMemoryHL(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 2);
    }

    /**
     * Executes the instruction.
     * 
     * Retrieves the value from the source register and writes it to the address in
     * HL.
     */
    @Override
    public void run() {
        Register source = Register.getRegFrom3Bit(opcodeValues[0]);
        int address = cpu.getHLValue();
        int value = cpu.regGet(source);
        memory.write(address, value);
    }
}
