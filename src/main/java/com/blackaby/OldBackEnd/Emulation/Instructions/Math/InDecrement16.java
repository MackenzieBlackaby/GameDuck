package com.blackaby.OldBackEnd.Emulation.Instructions.Math;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.Instruction;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Implements the INC rr and DEC rr instructions (16-bit).
 * * Increments/Decrements BC, DE, HL, or SP.
 * * 8 T-Cycles.
 * * NO FLAGS are affected.
 */
public class InDecrement16 extends Instruction {

    private final boolean increment;

    public InDecrement16(DuckCPU cpu, DuckMemory memory, boolean increment) {
        super(cpu, memory, 8);
        this.increment = increment;
    }

    @Override
    public void run() {
        // Opcode bits 5-4 determine the register pair (BC, DE, HL, SP)
        Register source = Register.getRegFrom2Bit(opcodeValues[0], false);

        int value = cpu.regGet16(source);
        value = (value + (increment ? 1 : -1)) & 0xFFFF;
        cpu.regSet16(source, value);
    }
}