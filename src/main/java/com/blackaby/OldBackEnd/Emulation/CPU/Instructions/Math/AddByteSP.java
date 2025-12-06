package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the ADD SP, e instruction.
 * 
 * Adds a signed 8-bit immediate value to the stack pointer (SP),
 * storing the result back in SP and setting flags accordingly.
 */
public class AddByteSP extends Instruction {
    /**
     * Constructs the instruction to add an immediate signed byte to SP.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public AddByteSP(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 4);
    }

    /**
     * Executes the instruction.
     * 
     * Adds a signed 8-bit immediate value to SP.
     * 
     * Flags:
     * - Z: Cleared
     * - N: Cleared
     * - H: Set if lower nibble overflow occurs
     * - C: Set if lower byte overflow occurs
     */
    @Override
    public void run() {
        int sp = cpu.getSP();
        int offset = operands[0]; // Keep it as loaded (signed or unsigned 8-bit)

        // In Java, byte is signed (-128 to 127).
        // For specific flag calculation, we need the raw unsigned values.

        int spLow = sp & 0xFF;
        int immediateUnsigned = offset & 0xFF; // Treat as 0-255 for flags

        // H Flag: Carry from bit 3
        boolean halfCarry = ((spLow & 0x0F) + (immediateUnsigned & 0x0F)) > 0x0F;

        // C Flag: Carry from bit 7 (Overflow of 8-bit addition)
        boolean carry = (spLow + immediateUnsigned) > 0xFF;

        // The actual math is done on the full 16-bit SP with the SIGNED offset
        // Convert 8-bit signed operand to int (e.g., 0xFF becomes -1)
        int signedOffset = (byte) offset;
        int result = sp + signedOffset;

        cpu.setSP(result & 0xFFFF);

        cpu.deactivateFlags(Flag.Z, Flag.N);
        cpu.setFlag(Flag.H, halfCarry);
        cpu.setFlag(Flag.C, carry);
    }
}