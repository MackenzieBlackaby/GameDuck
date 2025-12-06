package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Bits;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Handles the CB-prefixed bit manipulation instructions:
 * BIT (test), SET (activate), and RES (reset/clear) for a specific bit.
 * 
 * Can operate on either a register or the memory location pointed to by HL.
 */
public class BitSet extends Instruction {

    private boolean isRegister;
    private boolean activate;
    private boolean test;

    /**
     * Constructs a bit manipulation instruction.
     *
     * @param cpu        Reference to the DuckCPU instance
     * @param memory     Reference to memory
     * @param isActive   If true, the instruction is a SET; false for RES
     * @param isRegister If true, the operand is a register; otherwise HL memory
     * @param isTest     If true, the instruction is a BIT (test) operation
     */
    public BitSet(DuckCPU cpu, DuckMemory memory, boolean isActive, boolean isRegister, boolean isTest) {
        super(cpu, memory, isRegister ? 2 : 4);
        if (isTest)
            cycles = 3;
        this.isRegister = isRegister;
        this.activate = isActive;
        this.test = isTest;
    }

    /**
     * Executes the bit instruction.
     * 
     * - BIT: Tests the specified bit and sets flags accordingly
     * - SET: Sets the specified bit
     * - RES: Clears the specified bit
     */
    @Override
    public void run() {
        int value = 0;
        Register reg = Register.A;
        if (isRegister) {
            reg = Register.getRegFrom3Bit(opcodeValues[1]);
            value = cpu.regGet(reg);
        } else {
            value = memory.read(cpu.getHLValue());
        }
        int bitPos = opcodeValues[0] & 0b111;
        int mask = 0xFF & (1 << bitPos);
        if (test) {
            boolean isSet = (value & mask) != 0;
            cpu.setFlag(Flag.Z, !isSet);
            cpu.setFlag(Flag.H, true);
            cpu.deactivateFlags(Flag.N);
        } else {
            if (activate) {
                value |= mask;
            } else {
                value &= ~mask;
            }

            if (isRegister) {
                cpu.regSet(reg, (byte) value);
            } else {
                memory.write(cpu.getHLValue(), (byte) value);
            }
        }
    }
}
