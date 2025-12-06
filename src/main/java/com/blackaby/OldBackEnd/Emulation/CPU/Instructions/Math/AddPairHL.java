package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the ADD HL, rr instruction.
 * 
 * Adds a 16-bit register pair to HL and stores the result in HL.
 * Affects the carry and half-carry flags.
 */
public class AddPairHL extends Instruction {
    /**
     * Constructs the instruction to add a 16-bit register pair to HL.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public AddPairHL(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 2);
    }

    /**
     * Executes the instruction.
     * 
     * Adds the specified 16-bit register pair to HL.
     * 
     * Flags:
     * - N: Cleared
     * - H: Set if carry from bit 11 occurs
     * - C: Set if carry from bit 15 occurs
     */
    @Override
    public void run() {
        int hl = cpu.getHLValue();
        Register reg = Register.getRegFrom2Bit(opcodeValues[0], false);
        int value = cpu.regGet16(reg);
        cpu.setHL(hl + value);

        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.H, ((hl & 0x0FFF) + (value & 0x0FFF)) > 0x0FFF);
        cpu.setFlag(Flag.C, ((hl & 0xFFFF) + (value & 0xFFFF)) > 0xFFFF);
    }
}