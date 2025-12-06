package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.CPU;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD rr, nn instruction.
 * 
 * Loads a 16-bit immediate value into a 16-bit register pair.
 * Affects BC, DE, HL, or SP depending on opcode.
 */
public class ImmediateRegisterPair extends Instruction {
    /**
     * Constructs an immediate 16-bit load instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public ImmediateRegisterPair(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 3);
    }

    /**
     * Executes the load instruction.
     * 
     * Loads a 16-bit immediate value into the specified 16-bit register pair.
     */
    @Override
    public void run() {
        int value = 0xFFFF & (((operands[1] & 0xFF) << 8) | (operands[0] & 0xFF));
        Register destination = Register.getRegFrom2Bit(opcodeValues[0], false);
        cpu.regSet16(destination, value);
    }
}
