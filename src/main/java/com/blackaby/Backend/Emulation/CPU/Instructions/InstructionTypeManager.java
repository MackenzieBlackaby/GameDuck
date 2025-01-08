package com.blackaby.Backend.Emulation.CPU.Instructions;

import com.blackaby.Backend.Emulation.DuckEmulation;
import com.blackaby.Backend.Emulation.CPU.Instructions.SpecificInstructions.Duckstruction;
import com.blackaby.Backend.Emulation.CPU.Instructions.SpecificInstructions.ProcessorInstruction;

public class InstructionTypeManager {

    public enum InstructionType {
        // FROM_TO(_REGS/DETAILS)
        // Load instructions (ids 0 - 24)
        REGISTER_REGISTER("01aaabbb", 0),
        IMMEDIATE_REGISTER("00aaa110", 1),
        MEMORY_REGISTER_HL("01aaa110", 0),
        REGISTER_MEMORY_HL("0110aaa", 0),
        IMMEDIATE_MEMORY_HL("00110110", 1),
        MEMORY_ACCUMULATOR_BC("00001010", 0),
        MEMORY_ACCUMULATOR_DE("00011010", 0),
        ACCUMULATOR_MEMORY_BC("00000010", 0),
        ACCUMULATOR_MEMORY_DE("00010010", 0),
        MEMORY_ACCUMULATOR_IMMEDIATE("11111010", 2),
        ACCUMULATOR_MEMORY_IMMEDIATE("11101010", 2),
        MEMORY_ACCUMULATOR_MSB_0xFF_C("11110010", 0),
        ACCUMULATOR_MEMORY_MSB_0xFF_C("11100010", 0),
        MEMORY_ACCUMULATOR_MSB_0xFF_IMMEDIATE("11110000", 1),
        ACCUMULATOR_MEMORY_MSB_0xFF_IMMEDIATE("11100000", 1),
        MEMORY_ACCUMULATOR_HL_DECREMENT("00111010", 0),
        ACCUMULATOR_MEMORY_HL_DECREMENT("00110010", 0),
        MEMORY_ACCUMULATOR_HL_INCREMENT("00101010", 0),
        ACCUMULATOR_MEMORY_HL_INCREMENT("00100010", 0),
        IMMEDIATE_PAIR("00aa0001", 2),
        SP_MEMORY_IMMEDIATE("00001000", 2),
        HL_SP("11111001", 0),
        STACKPUSH_RR("11aa0101", 0),
        STACKPOP_RR("11aa0001", 0),
        SP_PLUS_IMMEDIATE8_HL("11111000", 1);
        // Arithmetic instructions (IDs 25 - x)

        private final String opcode;
        private final byte operandCount;

        public static final InstructionType[] OpcodeExtractingTypes = {
                REGISTER_REGISTER, IMMEDIATE_REGISTER, MEMORY_REGISTER_HL, REGISTER_MEMORY_HL, IMMEDIATE_PAIR,
                STACKPUSH_RR, STACKPOP_RR
        };

        InstructionType(String opcode, int operandCount) {
            this.opcode = opcode;
            this.operandCount = (byte) operandCount;
        }

        public String getOpcode() {
            return opcode;
        }

        public byte getOperandCount() {
            return operandCount;
        }

        public int getID() {
            return ordinal();
        }

        public boolean doesExtractOpcode() {
            for (InstructionType type : OpcodeExtractingTypes) {
                if (type == this)
                    return true;
            }
            return false;
        }
    }

    private static boolean compareOpcode(String base, byte opcode) {
        char array[] = base.toCharArray();
        for (int i = 0; i < array.length; i++) {
            if (array[i] == 'a' || array[i] == 'b')
                continue;
            boolean currentBit = (opcode & (1 << (7 - i))) != 0;
            if (array[i] == '0' && currentBit || array[i] == '1' && !currentBit)
                return false;
        }
        return true;
    }

    public static InstructionType getType(byte opcode) {
        for (InstructionType instruction : InstructionType.values()) {
            if (compareOpcode(instruction.getOpcode(), opcode)) {
                return instruction;
            }
        }
        return null;
    }

    public static Duckstruction constructInstruction(DuckEmulation boundEmulation, InstructionType instruction,
            byte opcode, byte... operands) {
        if (instruction.getID() < 25) {
            return new ProcessorInstruction(boundEmulation.getCPU(), boundEmulation.getMemory(), instruction, opcode,
                    operands);
        }
        return null;
    }
}
