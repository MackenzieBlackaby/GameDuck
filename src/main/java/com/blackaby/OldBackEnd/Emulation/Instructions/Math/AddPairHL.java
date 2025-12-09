package com.blackaby.OldBackEnd.Emulation.Instructions.Math;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.Instruction;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Implements ADD HL, rr (16-bit add).
 * * 8 T-Cycles.
 */
public class AddPairHL extends Instruction {

    public AddPairHL(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 8);
    }

    @Override
    public void run() {
        int hl = cpu.getHL();
        Register sourceReg = Register.getRegFrom2Bit(opcodeValues[0], false); // SP context
        int value = cpu.regGet16(sourceReg);

        int result = hl + value;

        cpu.setHL(result & 0xFFFF);

        cpu.setFlag(Flag.N, false);
        // Half Carry: Carry from bit 11 to 12
        cpu.setFlag(Flag.H, ((hl & 0x0FFF) + (value & 0x0FFF)) > 0x0FFF);
        // Full Carry: Carry from bit 15 to 16
        cpu.setFlag(Flag.C, result > 0xFFFF);
        // Z Flag is NOT affected
    }
}