package com.blackaby.OldBackEnd.Emulation.Instructions.Math;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.Instruction;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Implements the INC and DEC instructions (8-bit).
 * * INC r / DEC r (4 cycles)
 * * INC [HL] / DEC [HL] (12 cycles)
 */
public class IncDec extends Instruction {

    private final ValueType valueType;
    private final boolean increment;

    public IncDec(DuckCPU cpu, DuckMemory memory, ValueType valueType, boolean increment) {
        // [HL] takes 12 cycles, Registers take 4 cycles
        super(cpu, memory, valueType == ValueType.HL_MEMORY ? 12 : 4);
        this.valueType = valueType;
        this.increment = increment;
    }

    @Override
    public void run() {
        int value;
        Register sourceReg = null;

        // Fetch
        if (valueType == ValueType.REGISTER) {
            sourceReg = Register.getRegFrom3Bit(opcodeValues[0]);
            value = cpu.regGet(sourceReg);
        } else {
            // HL_MEMORY
            value = memory.read(cpu.getHL()); // Use new helper
        }

        int oldValue = value;

        // Execute
        value = increment ? value + 1 : value - 1;
        value &= 0xFF;

        // Store
        if (valueType == ValueType.REGISTER) {
            cpu.regSet(sourceReg, value);
        } else {
            memory.write(cpu.getHL(), value);
        }

        // Update Flags (Note: C flag is NOT affected)
        cpu.setFlag(Flag.Z, value == 0);
        cpu.setFlag(Flag.N, !increment);

        // Half Carry Calculation
        if (increment) {
            // (Old & 0xF) + 1 > 0xF implies carry from bit 3
            cpu.setFlag(Flag.H, (oldValue & 0x0F) == 0x0F);
        } else {
            // (Old & 0xF) == 0 implies borrow from bit 4
            cpu.setFlag(Flag.H, (oldValue & 0x0F) == 0x00);
        }
    }
}