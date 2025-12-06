package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD A, (HL±) instruction.
 * 
 * Loads a value from the memory address pointed to by HL into the accumulator
 * (A),
 * then increments or decrements HL.
 */
public class MemoryHLAccumulatorChange extends Instruction {

    private boolean increment;

    /**
     * Constructs the instruction to load A from (HL), with optional HL increment or
     * decrement.
     *
     * @param cpu       Reference to the DuckCPU instance
     * @param memory    Reference to memory
     * @param increment True to increment HL after reading; false to decrement
     */
    public MemoryHLAccumulatorChange(DuckCPU cpu, DuckMemory memory, boolean increment) {
        super(cpu, memory, 2);
        this.increment = increment;
    }

    /**
     * Executes the instruction.
     * 
     * Reads a byte from memory at the address in HL,
     * stores it in the accumulator (A),
     * then increments or decrements HL depending on the mode.
     */
    @Override
    public void run() {
        int hlValue = cpu.getHLValue();
        int memoryValue = memory.read(hlValue);
        cpu.setAccumulator(memoryValue);
        int newHL = hlValue + (increment ? 1 : -1);
        cpu.setHL(newHL);
    }
}
