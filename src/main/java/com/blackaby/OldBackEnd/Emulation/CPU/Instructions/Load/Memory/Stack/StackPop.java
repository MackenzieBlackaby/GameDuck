package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory.Stack;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the POP rr instruction.
 * 
 * Pops two bytes from the stack and stores them in a 16-bit register pair.
 * 
 * Special handling is applied for AF, as the lower nibble of F must be zeroed.
 */
public class StackPop extends Instruction {
    /**
     * Constructs a POP instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public StackPop(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 3);
    }

    /**
     * Executes the POP instruction.
     * 
     * - Reads two bytes from the stack and combines them into a 16-bit value.
     * - Stores the value in the target register pair.
     * - In the case of AF, the lower nibble of F is cleared.
     * 
     * Updates the stack pointer accordingly.
     */
    @Override
    public void run() {
        int sp = cpu.getSP();
        Register register = Register.getRegFrom2Bit(opcodeValues[0], true);

        int lsb = memory.read(sp);
        if (register == Register.AF)
            lsb &= 0xF0;

        sp += 1;
        int msb = memory.read(sp);
        sp += 1;

        int value = ((msb & 0xFF) << 8) | (lsb & 0xFF);

        cpu.regSet16(register, value);
        cpu.setSP(sp);
    }

}
