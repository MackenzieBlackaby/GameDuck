package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Flow;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the JP, JR, and JP (HL) instructions.
 * 
 * Supports:
 * - Absolute jumps (JP)
 * - Relative jumps (JR)
 * - Jumps to HL (JP (HL))
 * - Conditional and unconditional forms
 * 
 * Adjusts cycle timing depending on whether the jump is taken and the type of
 * jump.
 */
public class Jump extends Instruction {

    private boolean conditional;
    private boolean isRelative;
    private boolean isHL;

    // Absolute jumps:
    // Unconditional or successful conditional jumps: 4 cycles
    // Unsuccessful conditional jumps: 3 cycles
    // Relative jumps:
    // Unconditional or successful conditional jumps: 3 cycles
    // Unsuccessful conditional jumps: 2 cycles

    /**
     * Constructs a Jump instruction.
     *
     * @param cpu         Reference to the DuckCPU instance
     * @param memory      Reference to memory
     * @param conditional True if the jump is conditional
     * @param isRelative  True if this is a relative jump (JR)
     * @param isHL        True if the jump is to the address in HL (JP (HL))
     */
    public Jump(DuckCPU cpu, DuckMemory memory, boolean conditional, boolean isRelative, boolean isHL) {
        super(cpu, memory, 1);
        this.conditional = conditional;
        this.isRelative = isRelative;
        this.isHL = isHL;
    }

    /**
     * Executes the jump instruction.
     * 
     * - If the condition is not met, the program counter remains unchanged.
     * - If the condition is met or the jump is unconditional, the program counter
     * is updated to the appropriate target:
     * - A relative offset (JR)
     * - A direct address (JP)
     * - The address in HL (JP (HL))
     * 
     * Cycles are adjusted based on whether the jump is taken and the jump type.
     */
    @Override
    public void run() {
        int pc = cpu.getPC();

        // Determine cycles based on jump type and condition
        if (conditional) {
            int condCode = opcodeValues[0] & 0b11;
            boolean jumpConditionMet = false;
            switch (condCode) {
                case 0b00:
                    // Z flag set
                    jumpConditionMet = !cpu.getFlagBoolean(Flag.Z);
                    break;
                case 0b01:
                    // Z flag clear
                    jumpConditionMet = cpu.getFlagBoolean(Flag.Z);
                    break;
                case 0b10:
                    // C flag set
                    jumpConditionMet = !cpu.getFlagBoolean(Flag.C);
                    break;
                case 0b11:
                    // C flag clear
                    jumpConditionMet = cpu.getFlagBoolean(Flag.C);
                    break;
            }
            if (!jumpConditionMet) {
                // Condition not met: use unsuccessful cycle count.
                if (isRelative) {
                    cycles = 2;
                } else {
                    cycles = 3;
                }
                return;
            } else {
                // Condition met: use the longer timing.
                if (isRelative) {
                    cycles = 3;
                } else {
                    cycles = 4;
                }
            }
        } else {
            // Unconditional jump
            if (isHL) {
                cycles = 1;
            } else if (isRelative) {
                cycles = 3;
            } else {
                cycles = 4;
            }
        }

        // Now perform the jump.
        if (isRelative) {
            int offset = (byte) operands[0];
            int newPC = pc + offset;
            cpu.setPC(newPC);
        } else if (isHL) {
            int hlValue = cpu.getHLValue();
            cpu.setPC(hlValue);
        } else {
            // For absolute jumps, operand bytes are stored in values[1] (low) and values[2]
            // (high)
            int target = ((operands[1] & 0xFF) << 8) | (operands[0] & 0xFF);
            cpu.setPC(target);
        }
    }

}