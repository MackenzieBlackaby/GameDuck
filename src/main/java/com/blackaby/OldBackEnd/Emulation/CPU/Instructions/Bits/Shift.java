package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Bits;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the shift instructions (SLA, SRA, SRL).
 * 
 * Supports logical and arithmetic right shifts, and left shifts.
 * Can operate on registers or on the memory pointed to by HL.
 */
public class Shift extends Instruction {

    boolean left;
    boolean arithmetic;
    boolean register;

    /**
     * Constructs a shift instruction.
     *
     * @param cpu        Reference to the DuckCPU instance
     * @param memory     Reference to memory
     * @param left       True to shift left; false to shift right
     * @param arithmetic True for arithmetic right shift (SRA); false for logical
     *                   (SRL)
     * @param register   True if the operand is a register; false for HL memory
     */
    public Shift(DuckCPU cpu, DuckMemory memory, boolean left, boolean arithmetic, boolean register) {
        super(cpu, memory, register ? 2 : 4);
        this.left = left;
        this.arithmetic = arithmetic;
        this.register = register;
    }

    /**
     * Executes the shift instruction.
     * 
     * - SLA: Shifts bits left, filling LSB with 0
     * - SRA: Shifts bits right, preserving the MSB (sign)
     * - SRL: Shifts bits right, inserting 0 at MSB
     * 
     * Updates flags accordingly:
     * - Z: Set if result is zero
     * - C: Set to the bit shifted out
     * - N, H: Always cleared
     */
    @Override
    public void run() {
        int value;
        Register reg = null;
        if (register) {
            reg = Register.getRegFrom3Bit(opcodeValues[0]);
            value = cpu.regGet(reg);
        } else {
            value = memory.read(cpu.getHLValue());
        }
        boolean carry = left ? (value & 0x80) != 0 : (value & 0x01) != 0;

        if (left) {
            value = (value << 1) & 0xFF;
        } else {
            if (arithmetic) {
                boolean bit7 = (value & 0x80) != 0;
                value >>= 1;
                if (bit7) {
                    value |= 0x80;
                }
            } else {
                value >>>= 1;
            }
            value = (value & 0xFF);
        }

        if (register) {
            cpu.regSet(reg, value);
        } else {
            memory.write(cpu.getHLValue(), value);
        }

        cpu.setFlag(DuckCPU.Flag.C, carry);
        cpu.setFlag(DuckCPU.Flag.Z, value == 0);
        cpu.deactivateFlags(DuckCPU.Flag.H, DuckCPU.Flag.N);
    }
}