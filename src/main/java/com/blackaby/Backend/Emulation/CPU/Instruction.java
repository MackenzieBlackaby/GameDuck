package com.blackaby.Backend.Emulation.CPU;

import com.blackaby.Backend.Emulation.CPU.DuckDecoder.InstructionType;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

public abstract class Instruction implements Runnable {

    protected DuckMemory memory;
    protected DuckCPU cpu;
    protected int[] opcodeValues;
    protected int[] operands;
    protected int cycles;
    private InstructionType type;

    public Instruction(DuckCPU cpu, DuckMemory memory, int cycleCount) {
        this.cpu = cpu;
        this.memory = memory;
        this.cycles = cycleCount;
    }

    public void setValues(int[] opcodeValues, int[] operands) {
        for (int i = 0; i < opcodeValues.length; i++) {
            opcodeValues[i] = 0xFF & opcodeValues[i];
        }
        for (int i = 0; i < operands.length; i++) {
            operands[i] = 0xFF & operands[i];
        }
        this.operands = operands;
        this.opcodeValues = opcodeValues;
    }

    public int getCycleCount() {
        return cycles;
    }

    public void setType(InstructionType type) {
        this.type = type;
    }

    public InstructionType getType() {
        return type;
    }

    @Override
    public void run() {
        System.err.println("Should not run superclass");
    }
}
