package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements 8-bit arithmetic instructions for the accumulator (A).
 * 
 * Supported operations:
 * - ADD / ADC: Add value to A (optionally including carry)
 * - SUB / SBC: Subtract value from A (optionally including carry)
 * - CP: Compare value with A without storing result
 * 
 * Supports operands from registers, immediate values, and HL memory.
 */
public class Arithmetic extends Instruction {

    private ArithmeticType arithmeticType;
    private ValueType valueType;
    private boolean carry;

    /**
     * Type of arithmetic operation to perform.
     */
    public enum ArithmeticType {
        ADD, SUBTRACT, CP;
    }

    /**
     * Constructs an arithmetic instruction targeting the accumulator (A).
     *
     * @param cpu       Reference to the DuckCPU instance
     * @param memory    Reference to memory
     * @param aType     Type of arithmetic operation (ADD, SUBTRACT, or CP)
     * @param valueType Source of the value (register, immediate, or HL memory)
     * @param carry     True if this instruction includes the carry flag (ADC/SBC)
     */
    public Arithmetic(DuckCPU cpu, DuckMemory memory, ArithmeticType aType, ValueType valueType, boolean carry) {
        super(cpu, memory, valueType == ValueType.REGISTER ? 1 : 2);
        this.arithmeticType = aType;
        this.valueType = valueType;
        this.carry = carry;
    }

    /**
     * Executes the arithmetic instruction.
     * 
     * Performs the specified operation between A and the selected value source.
     * 
     * - Updates Z, N, H, and C flags appropriately.
     * - Result is stored in A unless the operation is CP (compare).
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

        int result = 0;
        int accumulator = cpu.getAccumulator();
        int carryValue = cpu.getFlagBoolean(DuckCPU.Flag.C) && carry ? 1 : 0;

        switch (arithmeticType) {
            case ADD: {
                result = accumulator + value + carryValue;
                cpu.setFlag(Flag.C, result > 0xFF);
                cpu.setFlag(Flag.H, ((accumulator & 0x0F) + (value & 0x0F) + carryValue) > 0x0F);
                break;
            }
            case CP:
            case SUBTRACT: {
                result = accumulator - value - carryValue;
                cpu.setFlag(Flag.C, result < 0);
                cpu.setFlag(Flag.H, ((accumulator & 0x0F) - (value & 0x0F) - carryValue) < 0);
                break;
            }
            default: {
                throw new IllegalArgumentException("Invalid arithmetic type");
            }
        }

        result &= 0xFF;

        if (arithmeticType != ArithmeticType.CP)
            cpu.setAccumulator(result);

        cpu.setFlag(Flag.Z, result == 0);
        cpu.setFlag(Flag.N, arithmeticType != ArithmeticType.ADD);
    }

}
