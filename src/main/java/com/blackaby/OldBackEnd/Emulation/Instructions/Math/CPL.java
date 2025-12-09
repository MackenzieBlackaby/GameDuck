package com.blackaby.OldBackEnd.Emulation.Instructions.Math;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.Instruction;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Implements CPL (Complement Accumulator).
 * * 4 T-Cycles.
 */
public class CPL extends Instruction {

    public CPL(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 4);
    }

    @Override
    public void run() {
        int a = cpu.regGet(DuckCPU.Register.A);
        cpu.regSet(DuckCPU.Register.A, (~a) & 0xFF);

        cpu.setFlag(Flag.N, true);
        cpu.setFlag(Flag.H, true);
    }
}