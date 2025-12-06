package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Bits;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the rotate instructions (RL, RR, RLC, RRC).
 * 
 * Supports both circular rotates and rotates through carry, for:
 * - The accumulator (A)
 * - HL memory
 * - General-purpose registers
 */
public class Rotate extends Instruction {

    private boolean left;
    private boolean accumulator;
    private boolean hl;
    private boolean circle; // true = circular rotate; false = rotate through carry
    private boolean forceZ;

    /**
     * Constructs a rotate instruction.
     *
     * @param cpu         Reference to the DuckCPU instance
     * @param memory      Reference to memory
     * @param left        True for left rotation, false for right
     * @param circle      True for circular rotate, false for rotate through carry
     * @param accumulator True if the instruction targets the accumulator (A)
     * @param hl          True if the instruction targets the address pointed to by
     *                    HL
     * @param isCBPrefix  True if this is a CB-prefixed instruction (affects zero
     *                    flag behaviour)
     */
    public Rotate(DuckCPU cpu, DuckMemory memory, boolean left, boolean circle, boolean accumulator, boolean hl,
            boolean isCBPrefix) {
        super(cpu, memory, 2);
        this.left = left;
        this.circle = circle;
        this.accumulator = accumulator;
        this.hl = hl;
        this.forceZ = !isCBPrefix;
        if (accumulator)
            cycles = 1;
        else if (hl)
            cycles = 4;
    }

    /**
     * Executes the rotate instruction.
     * 
     * - Circular rotates reinsert the shifted-out bit at the opposite end.
     * - Rotates through carry use the carry flag as input and set it from the
     * shifted-out bit.
     * 
     * Updates flags accordingly:
     * - Z: Set if result is zero (unless overridden by instruction type)
     * - C: Set if bit was shifted out
     * - N and H: Always cleared
     */
    @Override
    public void run() {
        int value;
        if (accumulator) {
            value = cpu.getAccumulator();
        } else if (hl) {
            value = memory.read(cpu.getHLValue());
        } else {
            value = cpu.regGet(Register.getRegFrom3Bit(opcodeValues[0]));
        }

        if (circle) {
            // Circular rotate: the shifted-out bit is reinserted.
            boolean shiftedOut;
            if (left) {
                shiftedOut = (value & 0x80) != 0;
                value = (value << 1) & 0xFF;
                if (shiftedOut) {
                    value |= 0x01;
                }
            } else {
                shiftedOut = (value & 0x01) != 0;
                value = (value >>> 1) & 0xFF;
                if (shiftedOut) {
                    value |= 0x80;
                }
            }
            // Update carry flag with the bit that was shifted out.
            cpu.setFlag(Flag.C, shiftedOut);
        } else {
            // Rotate through carry: insert the old carry flag into the new bit.
            boolean oldCarry = cpu.getFlagBoolean(Flag.C);
            int shiftedOut;
            if (left) {
                shiftedOut = (value & 0x80) != 0 ? 1 : 0;
                value = (value << 1) & 0xFF;
                if (oldCarry) {
                    value |= 0x01;
                }
            } else {
                shiftedOut = (value & 0x01) != 0 ? 1 : 0;
                value = (value >>> 1) & 0xFF;
                if (oldCarry) {
                    value |= 0x80;
                }
            }
            // Update carry flag with the shifted-out bit.
            cpu.setFlag(Flag.C, shiftedOut == 1);
        }

        // Write the result back to the appropriate destination.
        if (accumulator) {
            cpu.setAccumulator(value);
        } else if (hl) {
            memory.write(cpu.getHLValue(), value);
        } else {
            cpu.regSet(Register.getRegFrom3Bit(opcodeValues[0]), value);
        }
        // Set the zero flag if the result is zero and affectZ is true.
        if (forceZ)
            cpu.setFlag(Flag.Z, false);
        else
            cpu.setFlag(Flag.Z, value == 0);
        cpu.deactivateFlags(Flag.H, Flag.N);
    }
}
