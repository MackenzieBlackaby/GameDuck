package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Misc;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the HALT instruction.
 * 
 * Stops the CPU until an interrupt occurs.
 * 
 * If IME is disabled and an interrupt is pending, the HALT bug is triggered,
 * allowing execution to continue incorrectly without incrementing the PC.
 */
public class Halt extends Instruction {
    /**
     * Constructs the HALT instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public Halt(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 1);
    }

    /**
     * Executes the HALT instruction.
     * 
     * - If interrupts are enabled or no interrupt is pending, the CPU is halted
     * normally.
     * - If interrupts are disabled but one is pending, the HALT bug occurs,
     * causing the next opcode to be executed twice.
     */
    @Override
    public void run() {
        boolean interruptPending = (memory.read(DuckMemory.IE) & memory.read(DuckMemory.INTERRUPT_FLAG) & 0x1F) != 0;

        if (!cpu.isInterruptMasterEnable() && interruptPending) {
            // IME is disabled and an interrupt is pending: halt bug
            cpu.setHaltBug(true);
        } else {
            // Normal halt
            cpu.setHalted(true);
        }
    }
}
