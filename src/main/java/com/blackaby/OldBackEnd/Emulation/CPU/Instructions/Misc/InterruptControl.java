package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Misc;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the DI and EI instructions.
 * 
 * Controls the master interrupt enable (IME) flag.
 * - EI enables interrupts after the next instruction.
 * - DI disables interrupts immediately.
 */
public class InterruptControl extends Instruction {

    private boolean enable;

    /**
     * Constructs an interrupt control instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     * @param enable True for EI (enable interrupts), false for DI (disable
     *               interrupts)
     */
    public InterruptControl(DuckCPU cpu, DuckMemory memory, boolean enable) {
        super(cpu, memory, 1);
        this.enable = enable;
    }

    /**
     * Executes the DI or EI instruction.
     * 
     * Sets the interrupt master enable flag accordingly.
     */
    @Override
    public void run() {
        cpu.setInterruptEnable(enable);
    }

}
