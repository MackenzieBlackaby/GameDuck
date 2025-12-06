package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Flow;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the RET and RETI instructions.
 * 
 * Handles:
 * - Unconditional returns
 * - Conditional returns based on Z and C flags
 * - RETI (return from interrupt), which re-enables interrupt handling
 */
public class Return extends Instruction {

    boolean conditional;
    boolean interrupt;

    // Return unconditional: 4 cycles
    // Return conditional: 5 cycles in success, 2 cycles in fail
    // Return from interrupt: 4 cycles
    /**
     * Constructs a Return instruction.
     *
     * @param cpu         Reference to the DuckCPU instance
     * @param memory      Reference to memory
     * @param conditional True if the return is conditional
     * @param interrupt   True if this is a RETI instruction
     */
    public Return(DuckCPU cpu, DuckMemory memory, boolean conditional, boolean interrupt) {
        super(cpu, memory, conditional ? 5 : 4);
        this.conditional = conditional;
        this.interrupt = interrupt;
    }

    /**
     * Executes the return instruction.
     * 
     * - If conditional and the condition is not met, the return is skipped and
     * cycles adjusted.
     * - Otherwise, the return address is popped from the stack and execution
     * resumes.
     * - If RETI, interrupt master enable is set.
     */
    @Override
    public void run() {
        if (conditional) {
            int condition = opcodeValues[0] & 0b11;
            switch (condition) {
                case 0b00: {
                    if (cpu.getFlagBoolean(Flag.Z)) {
                        cycles -= 3;
                        return;
                    }
                    break;
                }
                case 0b01: {
                    if (!cpu.getFlagBoolean(Flag.Z)) {
                        cycles -= 3;
                        return;
                    }
                    break;
                }
                case 0b10: {
                    if (cpu.getFlagBoolean(Flag.C)) {
                        cycles -= 3;
                        return;
                    }
                    break;
                }
                case 0b11: {
                    if (!cpu.getFlagBoolean(Flag.C)) {
                        cycles -= 3;
                        return;
                    }
                    break;
                }
            }
        }
        int sp = cpu.getSP();
        int low = memory.read(sp);
        sp++;
        int high = memory.read(sp);
        sp++;
        cpu.setSP(sp);
        int pc = (high << 8) | low;
        cpu.setPC(pc);
        if (interrupt) {
            cpu.setInterruptMasterEnable(true);
        }
    }
}
