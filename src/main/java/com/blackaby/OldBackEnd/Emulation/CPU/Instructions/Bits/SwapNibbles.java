package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Bits;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU.Register;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the SWAP instruction.
 * 
 * Swaps the upper and lower nibbles of the operand.
 * Can operate on a register or on the memory location pointed to by HL.
 */
public class SwapNibbles extends Instruction {

    private boolean isRegister;

    /**
     * Constructs a SWAP instruction.
     *
     * @param cpu        Reference to the DuckCPU instance
     * @param memory     Reference to memory
     * @param isHL       True if targeting memory at HL (unused but maintained for
     *                   consistency)
     * @param isRegister True if the operand is a register; false for HL memory
     */
    public SwapNibbles(DuckCPU cpu, DuckMemory memory, boolean isHL, boolean isRegister) {
        super(cpu, memory, isRegister ? 2 : 4);
        this.isRegister = isRegister;
    }

    /**
     * Executes the SWAP instruction.
     * 
     * Exchanges the upper and lower 4 bits (nibbles) of the operand.
     * 
     * Updates flags accordingly:
     * - Z: Set if result is zero
     * - N, H, C: Always cleared
     */
    @Override
    public void run() {
        int value = isRegister ? cpu.regGet(Register.getRegFrom3Bit(opcodeValues[0]))
                : memory.read(cpu.getHLValue());
        int result = ((value & 0x0F) << 4) | ((value & 0xF0) >> 4);
        result &= 0xFF;
        if (isRegister) {
            cpu.regSet(Register.getRegFrom3Bit(opcodeValues[0]), result);
        } else {
            memory.write(cpu.getHLValue(), result);
        }
        cpu.setFlag(Flag.Z, result == 0);
        cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);

    }
}
