package com.blackaby.OldBackEnd.Emulation.Instructions.Math;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.Instruction;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Implements the ADD SP, e instruction (Opcode 0xE8).
 * * Adds a signed 8-bit immediate value to the stack pointer (SP).
 * * 16 T-Cycles.
 * * Note: Flags Z and N are always cleared. H and C are set based on the
 * unsigned addition of the lower byte, even though the full operation is
 * signed.
 */
public class AddByteSP extends Instruction {

    public AddByteSP(DuckCPU cpu, DuckMemory memory) {
        // ADD SP, e8 takes 16 T-Cycles
        super(cpu, memory, 16);
    }

    @Override
    public void run() {
        int sp = cpu.getSP();
        // operand[0] is guaranteed to be 0-255 by the Instruction class
        int immediate = operands[0];

        // 1. Calculate Flags (based on Unsigned Low Byte)
        // This is a hardware quirk: flags reflect the 8-bit ALU operation on the low
        // byte.
        int spLow = sp & 0xFF;

        // Half Carry: Carry from bit 3 to 4
        boolean halfCarry = ((spLow & 0x0F) + (immediate & 0x0F)) > 0x0F;

        // Full Carry: Carry from bit 7 to 8
        boolean carry = (spLow + immediate) > 0xFF;

        // 2. Calculate Result (based on Signed 16-bit addition)
        // Convert 0-255 to -128 to 127
        int signedOffset = (byte) immediate;
        int result = (sp + signedOffset) & 0xFFFF;

        cpu.setSP(result);

        // 3. Set Flags
        cpu.setFlag(Flag.Z, false); // Always cleared
        cpu.setFlag(Flag.N, false); // Always cleared
        cpu.setFlag(Flag.H, halfCarry);
        cpu.setFlag(Flag.C, carry);
    }
}