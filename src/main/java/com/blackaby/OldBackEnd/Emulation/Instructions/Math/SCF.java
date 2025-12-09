package com.blackaby.OldBackEnd.Emulation.Instructions.Math;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.Instruction;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Implements the SCF (Set Carry Flag) instruction.
 * * 4 T-Cycles.
 */
public class SCF extends Instruction {

    public SCF(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 4);
    }

    @Override
    public void run() {
        cpu.setFlag(Flag.C, true);
        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.H, false);
    }
}