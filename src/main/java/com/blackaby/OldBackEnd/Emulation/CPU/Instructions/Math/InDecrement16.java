package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the INC rr and DEC rr instructions.
 * 
 * Increments or decrements a 16-bit register pair (BC, DE, HL, or SP).
 * These operations do not affect flags.
 */
public class InDecrement16 extends Instruction {

    private boolean increment;

    /**
     * Constructs a 16-bit increment or decrement instruction.
     *
     * @param cpu       Reference to the DuckCPU instance
     * @param memory    Reference to memory
     * @param increment True for INC rr; false for DEC rr
     */
    public InDecrement16(DuckCPU cpu, DuckMemory memory,
            boolean increment) {
        super(cpu, memory, 2);
        this.increment = increment;
    }

    /**
     * Executes the 16-bit increment or decrement instruction.
     * 
     * Increments or decrements the selected 16-bit register.
     * Flags are unaffected.
     */
    @Override
    public void run() {
        Register source = Register.getRegFrom2Bit(opcodeValues[0], false);
        int value = cpu.regGet16(source);
        value = (value + (increment ? 1 : -1)) & 0xFFFF;
        cpu.regSet16(source, value);
    }
}
