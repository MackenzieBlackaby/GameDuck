package com.blackaby.OldBackEnd.Emulation.Instructions.Math;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.Instruction;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Implements 8-bit bitwise operations (AND, OR, XOR) for the accumulator (A).
 * * Reg: 4 cycles
 * * [HL] / Imm: 8 cycles
 */
public class Bitwise extends Instruction {

    public enum BitwiseType {
        AND, OR, XOR
    }

    private final ValueType valueType;
    private final BitwiseType bitwiseType;

    public Bitwise(DuckCPU cpu, DuckMemory memory, ValueType valueType, BitwiseType bitwiseType) {
        // Registers take 4 cycles, [HL] or Immediate takes 8 cycles
        super(cpu, memory, valueType == ValueType.REGISTER ? 4 : 8);
        this.valueType = valueType;
        this.bitwiseType = bitwiseType;
    }

    @Override
    public void run() {
        int operand = 0;

        switch (valueType) {
            case REGISTER -> operand = cpu.regGet(Register.getRegFrom3Bit(opcodeValues[0]));
            case HL_MEMORY -> operand = memory.read(cpu.getHL());
            case IMMEDIATE -> operand = operands[0] & 0xFF; // Already masked in fetch, but safe
        }

        int a = cpu.regGet(Register.A);
        int result = 0;

        switch (bitwiseType) {
            case AND -> {
                result = a & operand;
                cpu.setFlag(Flag.H, true); // AND always sets H=1
            }
            case OR -> {
                result = a | operand;
                cpu.setFlag(Flag.H, false);
            }
            case XOR -> {
                result = a ^ operand;
                cpu.setFlag(Flag.H, false);
            }
        }

        result &= 0xFF;
        cpu.regSet(Register.A, result);

        cpu.setFlag(Flag.Z, result == 0);
        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.C, false);
    }
}