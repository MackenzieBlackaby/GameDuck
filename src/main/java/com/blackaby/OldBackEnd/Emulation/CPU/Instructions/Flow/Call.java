package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Flow;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the CALL instruction.
 * 
 * Performs an unconditional or conditional subroutine call.
 * 
 * - If conditional, the decision is based on Z or C flags.
 * - On success, pushes the current PC onto the stack and jumps to the target
 * address.
 */
public class Call extends Instruction {

    boolean conditional;

    /**
     * Constructs a CALL instruction.
     *
     * @param cpu         Reference to the DuckCPU instance
     * @param memory      Reference to memory
     * @param conditional True if the CALL is conditional; false for unconditional
     */
    public Call(DuckCPU cpu, DuckMemory memory, boolean conditional) {
        super(cpu, memory, 3);
        this.conditional = conditional;
    }

    /**
     * Executes the CALL instruction.
     * 
     * If the condition is met (or unconditional), the current program counter is
     * pushed to the stack and control jumps to the 16-bit target address.
     * 
     * Updates:
     * - Stack pointer (SP) is decremented and used to store return address
     * - Program counter (PC) is set to the call target
     * - Adds 3 cycles if the call is executed
     */
    @Override
    public void run() {
        if (conditional) {
            int condition = opcodeValues[0] & 0b11;
            switch (condition) {
                case 0b00: {
                    if (cpu.getFlagBoolean(Flag.Z)) {
                        return;
                    }
                    break;
                }
                case 0b01: {
                    if (!cpu.getFlagBoolean(Flag.Z)) {
                        return;
                    }
                    break;
                }
                case 0b10: {
                    if (cpu.getFlagBoolean(Flag.C)) {
                        return;
                    }
                    break;
                }
                case 0b11: {
                    if (!cpu.getFlagBoolean(Flag.C)) {
                        return;
                    }
                    break;
                }
            }
        }

        cycles += 3;

        int address = (operands[1] << 8) | (operands[0] & 0xFF);

        int sp = cpu.getSP();
        int returnAddress = cpu.getPC();

        sp--;
        memory.write(sp, (returnAddress >> 8) & 0xFF);
        sp--;
        memory.write(sp, returnAddress & 0xFF);
        cpu.setSP(sp);

        cpu.setPC(address);
    }
}
