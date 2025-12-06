package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory.Stack;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the PUSH rr instruction.
 * 
 * Pushes a 16-bit register pair onto the stack.
 * 
 * If the register is AF, the lower nibble of F is cleared before pushing.
 */
public class StackPush extends Instruction {
    /**
     * Constructs a PUSH instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public StackPush(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 4);
    }

    /**
     * Executes the PUSH instruction.
     * 
     * - Retrieves the 16-bit value from the specified register pair.
     * - Pushes the upper byte first, then the lower byte, to the stack.
     * - Adjusts the stack pointer accordingly.
     * 
     * For AF, the lower nibble of F is zeroed before the value is stored.
     */
    @Override
    public void run() {
        int sp = cpu.getSP();
        Register register = Register.getRegFrom2Bit(opcodeValues[0], true);
        int value = cpu.regGet16(register) & 0xFFFF;
        if (register == Register.AF)
            value &= 0xFFF0;
        int msb = (value >> 8) & 0xFF;
        int lsb = value & 0xFF;

        sp--;
        memory.write(sp, msb);

        sp--;
        memory.write(sp, lsb);

        cpu.setSP(sp);

    }

}
