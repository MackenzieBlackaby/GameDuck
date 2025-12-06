package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the DAA (Decimal Adjust Accumulator) instruction.
 * 
 * Adjusts the accumulator (A) for BCD (Binary-Coded Decimal) representation,
 * based on the result of the previous arithmetic operation.
 */
public class DAA extends Instruction {

    /**
     * Constructs the DAA instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public DAA(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 1);
    }

    /**
     * Executes the DAA instruction.
     * 
     * Applies a correction to the accumulator (A) to form a valid BCD value,
     * depending on the N (subtract), H (half-carry), and C (carry) flags.
     * 
     * Flags:
     * - Z: Set if the result is zero
     * - H: Always cleared
     * - C: Set if the adjustment included 0x60
     */
    @Override
    public void run() {
        int a = cpu.getAccumulator();
        boolean n = cpu.getFlagBoolean(DuckCPU.Flag.N);
        boolean c = cpu.getFlagBoolean(DuckCPU.Flag.C);
        boolean h = cpu.getFlagBoolean(DuckCPU.Flag.H);
        int correction = 0;

        if (n) {
            if (c) {
                correction += 0x60;
            }
            if (h) {
                correction += 0x06;
            }

            a = (a - correction) & 0xFF;
        } else {
            if (c || a > 0x99) {
                correction += 0x60;
                c = true;
            } else {
                c = false;
            }
            if (h || (a & 0x0F) > 0x09) {
                correction += 0x06;
            }
            a = (a + correction) & 0xFF;
        }

        cpu.setAccumulator(a);
        cpu.setFlag(DuckCPU.Flag.C, c);
        cpu.setFlag(DuckCPU.Flag.H, false);
        cpu.setFlag(DuckCPU.Flag.Z, a == 0);
    }
}
