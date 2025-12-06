package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the LD (HL±), A instruction.
 * 
 * Stores the value in the accumulator (A) at the address pointed to by HL,
 * then increments or decrements HL depending on the direction.
 */
public class AccumulatorMemoryHLChange extends Instruction {

    private boolean increment;

    /**
     * Constructs the instruction to store A into (HL), with optional HL increment
     * or decrement.
     *
     * @param cpu       Reference to the DuckCPU instance
     * @param memory    Reference to memory
     * @param increment True to increment HL after storing; false to decrement
     */
    public AccumulatorMemoryHLChange(DuckCPU cpu, DuckMemory memory, boolean increment) {
        super(cpu, memory, 2);
        this.increment = increment;
    }

    /**
     * Executes the instruction.
     * 
     * Writes the value of A to the address in HL,
     * then increments or decrements HL based on the direction flag.
     */
    @Override
    public void run() {
        int hlValue = cpu.getHLValue();
        int accumulator = cpu.getAccumulator();
        memory.write(hlValue, accumulator);
        int newHL = hlValue + (increment ? 1 : -1);
        cpu.setHL(newHL);
    }
}
