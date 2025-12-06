package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.CPU;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements LD r, r and LD r, n instructions.
 * 
 * Loads an 8-bit value into a register from either another register or an
 * immediate value.
 */
public class LoadToRegister extends Instruction {

    private boolean sourceIsImmediate;

    /**
     * Constructs a register load instruction.
     *
     * @param cpu               Reference to the DuckCPU instance
     * @param memory            Reference to memory
     * @param sourceIsImmediate True if the value comes from an immediate operand;
     *                          false if from a register
     */
    public LoadToRegister(DuckCPU cpu, DuckMemory memory, boolean sourceIsImmediate) {
        super(cpu, memory, sourceIsImmediate ? 2 : 1);
        this.sourceIsImmediate = sourceIsImmediate;
    }

    /**
     * Executes the load instruction.
     * 
     * - If immediate, loads the operand into the destination register.
     * - Otherwise, loads from the specified source register.
     */
    @Override
    public void run() {
        int value;
        Register destination;
        destination = Register.getRegFrom3Bit(opcodeValues[0]);
        if (sourceIsImmediate) {
            value = operands[0] & 0xFF;
        } else {
            Register source = Register.getRegFrom3Bit(opcodeValues[1]);
            value = cpu.regGet(source);
        }
        cpu.regSet(destination, value);
    }

}
