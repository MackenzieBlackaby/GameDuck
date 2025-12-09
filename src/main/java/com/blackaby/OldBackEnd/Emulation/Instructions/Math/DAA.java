package com.blackaby.OldBackEnd.Emulation.Instructions.Math;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.Instruction;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Implements the DAA (Decimal Adjust Accumulator) instruction.
 * * Adjusts A to be a valid BCD number after an add/sub.
 * * 4 T-Cycles.
 */
public class DAA extends Instruction {

    public DAA(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 4);
    }

    @Override
    public void run() {
        int a = cpu.regGet(DuckCPU.Register.A);

        boolean n = cpu.getFlag(Flag.N);
        boolean h = cpu.getFlag(Flag.H);
        boolean c = cpu.getFlag(Flag.C);

        int correction = 0;

        // If N is set, we are adjusting after a subtraction
        if (n) {
            if (h)
                correction |= 0x06;
            if (c)
                correction |= 0x60;
            a = (a - correction) & 0xFF;
        }
        // If N is clear, we are adjusting after an addition
        else {
            if (h || (a & 0x0F) > 0x09) {
                correction |= 0x06;
            }
            if (c || a > 0x99) {
                correction |= 0x60;
                c = true; // C is set if we overflow decimal 99
            }
            a = (a + correction) & 0xFF;
        }

        cpu.regSet(DuckCPU.Register.A, a);

        cpu.setFlag(Flag.Z, a == 0);
        cpu.setFlag(Flag.H, false); // H is always cleared by DAA
        cpu.setFlag(Flag.C, c); // C is updated (or preserved if N=1)
    }
}