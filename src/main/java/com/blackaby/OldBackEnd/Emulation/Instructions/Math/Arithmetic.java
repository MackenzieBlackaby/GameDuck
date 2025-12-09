package com.blackaby.OldBackEnd.Emulation.Instructions.Math;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.Instruction;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Implements 8-bit arithmetic (ADD, ADC, SUB, SBC, CP).
 * * Reg: 4 cycles
 * * [HL] / Imm: 8 cycles
 */
public class Arithmetic extends Instruction {

    public enum ArithmeticType {
        ADD, SUBTRACT, CP
    }

    private final ArithmeticType arithmeticType;
    private final ValueType valueType;
    private final boolean useCarry;

    public Arithmetic(DuckCPU cpu, DuckMemory memory, ArithmeticType aType, ValueType valueType, boolean useCarry) {
        // Registers take 4 cycles, [HL] or Immediate takes 8 cycles
        super(cpu, memory, valueType == ValueType.REGISTER ? 4 : 8);
        this.arithmeticType = aType;
        this.valueType = valueType;
        this.useCarry = useCarry;
    }

    @Override
    public void run() {
        int operand = 0;
        switch (valueType) {
            case REGISTER -> operand = cpu.regGet(Register.getRegFrom3Bit(opcodeValues[0]));
            case HL_MEMORY -> operand = memory.read(cpu.getHL());
            case IMMEDIATE -> operand = operands[0];
        }

        int a = cpu.regGet(Register.A);
        int carry = (useCarry && cpu.getFlag(Flag.C)) ? 1 : 0;
        int result = 0;

        if (arithmeticType == ArithmeticType.ADD) {
            result = a + operand + carry;

            cpu.setFlag(Flag.Z, (result & 0xFF) == 0);
            cpu.setFlag(Flag.N, false);
            // Half Carry: Check carry from bit 3 to 4
            cpu.setFlag(Flag.H, ((a & 0xF) + (operand & 0xF) + carry) > 0xF);
            // Full Carry: Check carry from bit 7 to 8
            cpu.setFlag(Flag.C, result > 0xFF);

            cpu.regSet(Register.A, result & 0xFF);

        } else { // SUBTRACT or CP
            result = a - operand - carry;

            cpu.setFlag(Flag.Z, (result & 0xFF) == 0);
            cpu.setFlag(Flag.N, true);
            // Half Carry (Borrow): Check borrow from bit 4
            cpu.setFlag(Flag.H, ((a & 0xF) - (operand & 0xF) - carry) < 0);
            // Full Carry (Borrow): Check borrow from bit 8 (result negative)
            cpu.setFlag(Flag.C, result < 0);

            if (arithmeticType == ArithmeticType.SUBTRACT) {
                cpu.regSet(Register.A, result & 0xFF);
            }
        }
    }
}