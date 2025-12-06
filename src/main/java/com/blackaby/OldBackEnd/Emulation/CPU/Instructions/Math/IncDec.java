package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the INC and DEC instructions.
 * 
 * Increments or decrements an 8-bit register or the memory location at HL,
 * updating flags accordingly.
 */
public class IncDec extends Instruction {
    private ValueType valueType;
    private boolean increment;

    /**
     * Constructs an INC or DEC instruction.
     *
     * @param cpu       Reference to the DuckCPU instance
     * @param memory    Reference to memory
     * @param valueType Operand type (register or HL memory)
     * @param increment True for INC; false for DEC
     */
    public IncDec(DuckCPU cpu, DuckMemory memory, ValueType valueType, boolean increment) {
        super(cpu, memory, 3);
        this.valueType = valueType;
        this.increment = increment;
    }

    /**
     * Executes the INC or DEC instruction.
     * 
     * - Updates the specified operand
     * - Flags:
     * - Z: Set if result is zero
     * - N: Set on DEC, cleared on INC
     * - H: Set if lower nibble overflows (INC) or borrows (DEC)
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

        int oldValue = value;
        value = increment ? value + 1 : value - 1;
        value &= 0xFF;

        if (valueType == ValueType.REGISTER) {
            cpu.regSet(sourceReg, value);
        } else if (valueType == ValueType.HL_MEMORY) {
            memory.write(cpu.getHLValue(), value);
        } else {
            throw new IllegalArgumentException("Invalid arithmetic type");
        }

        cpu.setFlag(Flag.N, !increment);
        cpu.setFlag(Flag.Z, value == 0);
        if (increment) {
            // Check if (old nibble + 1) wrapped past 0xF
            boolean halfCarry = ((oldValue & 0x0F) + 1) > 0x0F;
            cpu.setFlag(Flag.H, halfCarry);
        } else {
            // Check if (old nibble - 1) went below 0x0
            boolean halfCarry = (oldValue & 0x0F) == 0x00;
            cpu.setFlag(Flag.H, halfCarry);
        }
    }

}
