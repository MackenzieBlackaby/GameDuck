package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements 8-bit bitwise operations for the accumulator (A).
 * 
 * Supported operations:
 * - AND: Logical AND with A
 * - OR: Logical OR with A
 * - XOR: Logical XOR with A
 * 
 * Operands may be registers, immediate values, or HL memory.
 */
public class Bitwise extends Instruction {
    private ValueType valueType;
    private BitwiseType bitwiseType;

    /**
     * Specifies the type of bitwise operation.
     */
    public enum BitwiseType {
        AND, OR, XOR
    }

    /**
     * Constructs a bitwise instruction for A with a specified operand type.
     *
     * @param cpu         Reference to the DuckCPU instance
     * @param memory      Reference to memory
     * @param valueType   Source of the operand (register, HL memory, or immediate)
     * @param bitwiseType Type of bitwise operation to perform
     */
    public Bitwise(DuckCPU cpu, DuckMemory memory, ValueType valueType, BitwiseType bitwiseType) {
        super(cpu, memory, 2);
        this.valueType = valueType;
        this.bitwiseType = bitwiseType;
        if (valueType == ValueType.REGISTER)
            cycles = 1;
    }

    /**
     * Executes the bitwise instruction.
     * 
     * Performs the specified logical operation between A and the operand.
     * 
     * Flags:
     * - Z: Set if result is zero
     * - H: Set only for AND
     * - N, C: Cleared
     */
    @Override
    public void run() {
        int value = 0;
        Register sourceReg = null;

        switch (valueType) {
            case REGISTER: {
                sourceReg = Register.getRegFrom3Bit(opcodeValues[0]);
                value = cpu.regGet(sourceReg);
                break;
            }
            case HL_MEMORY: {
                value = memory.read(cpu.getHLValue());
                break;
            }
            case IMMEDIATE: {
                value = operands[0];
                value &= 0xFF;
                break;
            }
            default: {
                throw new IllegalArgumentException("Invalid arithmetic type");
            }
        }

        switch (bitwiseType) {
            case AND: {
                value &= cpu.regGet(Register.A);
                break;
            }
            case OR: {
                value |= cpu.regGet(Register.A);
                break;
            }
            case XOR: {
                value ^= cpu.regGet(Register.A);
                break;
            }
        }
        value &= 0xFF;
        cpu.regSet(Register.A, value);

        cpu.setFlag(Flag.Z, value == 0);
        cpu.setFlag(Flag.H, bitwiseType == BitwiseType.AND);
        cpu.deactivateFlags(Flag.C, Flag.N);
    }
}
