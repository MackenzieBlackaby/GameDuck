package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Misc;

import com.blackaby.OldBackEnd.Emulation.CPU.DuckCPU;
import com.blackaby.OldBackEnd.Emulation.CPU.Instruction;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * Implements the RST (Restart) instructions.
 * 
 * RST is a call to a fixed address in memory, acting like a shorthand
 * subroutine call.
 * The current PC is pushed to the stack, and control jumps to one of 8 fixed
 * addresses.
 */
public class Restart extends Instruction {

    /**
     * Enum representing the possible restart vector addresses for the RST
     * instruction.
     */
    public enum RestartType {
        RST00(0x00),
        RST08(0x08),
        RST10(0x10),
        RST18(0x18),
        RST20(0x20),
        RST28(0x28),
        RST30(0x30),
        RST38(0x38);

        private final int address;

        RestartType(int address) {
            this.address = address;
        }

        /**
         * @return The fixed memory address this RST type jumps to.
         */
        public int getAddress() {
            return address;
        }
    }

    /**
     * Constructs a RST instruction.
     *
     * @param cpu    Reference to the DuckCPU instance
     * @param memory Reference to memory
     */
    public Restart(DuckCPU cpu, DuckMemory memory) {
        super(cpu, memory, 4);
    }

    /**
     * Executes the RST instruction.
     * 
     * Pushes the current PC + 1 onto the stack and jumps to the
     * restart vector determined by the opcode.
     */
    @Override
    public void run() {
        // Standard PUSH logic
        int pc = cpu.getPC();

        // 1. Decrement SP, write High Byte
        cpu.setSP(cpu.getSP() - 1);
        memory.write(cpu.getSP(), (pc >> 8) & 0xFF);

        // 2. Decrement SP, write Low Byte
        cpu.setSP(cpu.getSP() - 1);
        memory.write(cpu.getSP(), pc & 0xFF);

        RestartType type = RestartType.values()[opcodeValues[0]];
        cpu.setPC(type.getAddress());
    }
}
