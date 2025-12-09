package com.blackaby.OldBackEnd.Emulation.Instructions.Math;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.Instruction;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Implements CCF (Complement Carry Flag).
 * * 4 T-Cycles.
 */
public class CCF extends Instruction {

    public CCF(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 4);
    }

    @Override
    public void run() {
        boolean c = cpu.getFlag(Flag.C);
        cpu.setFlag(Flag.C, !c);
        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.H, false);
    }
}