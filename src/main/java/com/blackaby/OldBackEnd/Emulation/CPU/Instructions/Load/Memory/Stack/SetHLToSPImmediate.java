package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory.Stack;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD HL, SP + e instruction.
 * 
 * Adds a signed 8-bit immediate value to the stack pointer (SP)
 * and stores the result in HL. Affects flags accordingly.
 */
public class SetHLToSPImmediate extends Instruction {
    /**
     * Constructs the HL = SP + immediate instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public SetHLToSPImmediate(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 3);
    }

    /**
     * Executes the instruction.
     * 
     * Adds a signed 8-bit immediate to SP and stores the result in HL.
     * 
     * Flags:
     * - Z: Cleared
     * - N: Cleared
     * - H: Set if lower nibble overflow occurs
     * - C: Set if lower byte overflow occurs
     */
    @Override
    public void run() {
        int sp = cpu.getSP();
        int offset = (byte) operands[0];
        int value = sp + offset;
        cpu.setHL(value);
        cpu.deactivateFlags(Flag.Z, Flag.N);
        cpu.setFlag(Flag.H, (sp & 0xF) + (offset & 0xF) > 0xF);
        cpu.setFlag(Flag.C, (sp & 0xFF) + (offset & 0xFF) > 0xFF);
    }

}
