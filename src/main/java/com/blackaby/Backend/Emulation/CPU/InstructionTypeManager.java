package com.blackaby.Backend.Emulation.CPU;

import com.blackaby.Backend.Emulation.DuckEmulation;

/**
 * This class manages the different types of instructions that the CPU can
 * execute
 * It contains an enum of all the different types of instructions, and a method
 * to get the type of an instruction
 * It also contains a method to construct an instruction from the given type,
 * opcode, and operands
 */
public class InstructionTypeManager {

    /**
     * This enum represents the different types of instructions that the CPU can
     * perform
     * Each instruction has an opcode string, for comparisons and value extraction,
     * and an operand count
     * The enum also contains various getters and a method to determine if the
     * instruction extracts values from the opcode
     */
    public enum InstructionType {
        // FROM_TO(_REGS/DETAILS)
        // Load instructions
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
        SP_PLUS_IMMEDIATE8_HL("11111000", 1),
        // Arithmetic instructions
        ADD_REGISTER_ACCUMULATOR("10000aaa", 0),
        ADD_MEMORY_HL("10000110", 0),
        ADD_IMMEDIATE("11000110", 1),
        ADC_REGISTER("10001aaa", 0),
        ADC_MEMORY_HL("10001110", 0),
        ADC_IMMEDIATE("11001110", 1),
        SUB_REGISTER("10010aaa", 0),
        SUB_MEMORY_HL("10010110", 0),
        SUB_IMMEDIATE("11010110", 1),
        SBC_REGISTER("10011aaa", 0),
        SBC_MEMORY_HL("10011110", 0),
        SBC_IMMEDIATE("11011110", 1),
        CP_REGISTER("10111aaa", 0),
        CP_MEMORY_HL("10111110", 0),
        CP_IMMEDIATE("11111110", 1),
        INC_REGISTER("00aaa100", 0),
        INC_MEMORY_HL("00110100", 0),
        DEC_REGISTER("00aaa101", 0),
        DEC_MEMORY_HL("00110101", 0),
        // Bitwise instructions
        AND_REGISTER("10100aaa", 0),
        AND_MEMORY_HL("10100110", 0),
        AND_IMMEDIATE("11100110", 1),
        OR_REGISTER("10110aaa", 0),
        OR_MEMORY_HL("10110110", 0),
        OR_IMMEDIATE("11110110", 1),
        XOR_REGISTER("10101aaa", 0),
        XOR_MEMORY_HL("10101110", 0),
        XOR_IMMEDIATE("11101110", 1),
        // Other instructions
        CCF("00111111", 0),
        SCF("00110111", 0),
        DAA("00100111", 0), // ! INCOMPLETE TECHNICAL REFERENCE. FIND ALTERNATIVES
        CPL("00101111", 0),
        // 16-bit arithmetic instructions
        INC_REGISTER_16("00aa0011", 0),
        DEC_REGISTER_16("00ss1011", 0),
        ADD_PAIR_TO_HL("00aa1001", 0),
        ADD_BYTE_TO_SP("11101000", 1),
        // Rotate, shift and bit operation instructions
        ROTATE_LEFT_CIRCLE_ACCUMULATOR("00000111", 0),
        ROTATE_RIGHT_CIRCLE_ACCUMULATOR("00001111", 0),
        ROTATE_LEFT_ACCUMULATOR("00010111", 0),
        ROTATE_RIGHT_ACCUMULATOR("00011111", 0),
        ROTATE_LEFT_CIRCLE_REGISTER("00000aaa", 0), // ! CB PREFIX
        ROTATE_LEFT_CIRCLE_HL("00000110", 0), // !
        ROTATE_RIGHT_CIRCLE_REGISTER("00001aaa", 0), // !
        ROTATE_RIGHT_CIRCLE_HL("00001110", 0), // !
        ROTATE_LEFT_REGISTER("00010aaa", 0), // !
        ROTATE_LEFT_HL("00010110", 0), // !
        ROTATE_RIGHT_REGISTER("00011aaa", 0), // !
        ROTATE_RIGHT_HL("00011110", 0), // !
        SHIFT_LEFT_ARITHMETIC_REGISTER("00100aaa", 0), // !
        SHIFT_LEFT_ARITHMETIC_HL("00100110", 0),
        SHIFT_RIGHT_ARITHMETIC_REGISTER("00101aaa", 0), // !
        SHIFT_RIGHT_ARITHMETIC_HL("00101110", 0), // !
        SWAP_NIBBLES_REGISTER("00110aaa", 0), // !
        SWAP_NIBBLES_HL("00110110", 0), // !
        SHIFT_RIGHT_LOGICAL_REGISTER("00111aaa", 0), // !
        SHIFT_RIGHT_LOGICAL_HL("00111110", 0), // !
        TEST_BIT_REGISTER("01aaaaaa", 0), // !
        TEST_BIT_HL("01aaa110", 0), // !
        RESET_BIT_REGISTER("10aaaaaa", 0), // !
        RESET_BIT_HL("10aaa110", 0), // !
        SET_BIT_REGISTER("11aaaaaa", 0), // !
        SET_BIT_HL("11aaa110", 0), // !
        // Control flow instructions
        JUMP_UNCONDITIONAL("11000011", 2),
        JUMP_HL("11101001", 0),
        JUMP_CONDITIONAL("110aa010", 2),
        JUMP_RELATIVE_UNCONDITIONAL("00011000", 1),
        JUMP_RELATIVE_CONDITIONAL("001aa000", 1),
        CALL_UNCONDITIONAL("11001101", 2),
        CALL_CONDITIONAL("110aa100", 2),
        RETURN_UNCONDITIONAL("11001001", 0),
        RETURN_CONDITIONAL("110aa000", 0),
        RETURN_INTERRUPT("11011001", 0),
        RESTART_UNCONDITIONAL("11aaa111", 0),
        // Misc
        HALT("TODO", 0),
        STOP("TODO", 0),
        DISABLE_INTERRUPTS("11110011", 0),
        ENABLE_INTERRUPTS("11111011", 0),
        NOP("00000000", 0)
        ;

        private final String opcode;
        private final byte operandCount;

        /**
         * This array contains all the instruction types that extract values from the
         * opcode
         */
        public static final InstructionType[] OpcodeExtractingTypes = {
                REGISTER_REGISTER, IMMEDIATE_REGISTER, MEMORY_REGISTER_HL, REGISTER_MEMORY_HL, IMMEDIATE_PAIR,
                STACKPUSH_RR, STACKPOP_RR, ADD_REGISTER_ACCUMULATOR, ADC_REGISTER, SUB_REGISTER, SBC_REGISTER,
                CP_REGISTER, INC_REGISTER, DEC_REGISTER
        };

        /**
         * This constructor creates a new instruction type with the given opcode and
         * operand count
         * 
         * @param opcode       The opcode of the instruction
         * @param operandCount The number of operands the instruction has
         */
        InstructionType(String opcode, int operandCount) {
            this.opcode = opcode;
            this.operandCount = (byte) operandCount;
        }

        /**
         * This method returns the opcode of the instruction
         * 
         * @return The opcode of the instruction
         */
        public String getOpcode() {
            return opcode;
        }

        /**
         * This method returns the number of operands the instruction has
         * 
         * @return The number of operands the instruction has
         */
        public byte getOperandCount() {
            return operandCount;
        }

        /**
         * This method returns the ID/index of the instruction in the enum
         * 
         * @return The ID of the instruction in the enum
         */
        public int getID() {
            return ordinal();
        }

        /**
         * This method returns whether the instruction extracts values from the opcode
         * 
         * @return true if the instruction extracts values from the opcode
         */
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

    /**
     * This method returns the type of instruction that the given opcode represents
     * 
     * @param opcode The opcode of the instruction
     * @return The type of instruction that the opcode represents, or null if no
     *         type
     */
    public static InstructionType getType(byte opcode) {
        for (InstructionType instruction : InstructionType.values()) {
            if (compareOpcode(instruction.getOpcode(), opcode)) {
                return instruction;
            }
        }
        return null;
    }

    /**
     * This method constructs an instruction from the given type, opcode, and
     * operands
     * 
     * @param boundEmulation The emulation that the instruction is bound to
     * @param instruction    The type of instruction
     * @param opcode         The opcode of the instruction
     * @param operands       The operands of the instruction
     * @return The constructed instruction
     */
    public static Duckstruction constructInstruction(DuckEmulation boundEmulation, InstructionType instruction,
            byte opcode, byte... operands) {
        // TODO: Implement multiple constructors based on the hardware needs
        return new Duckstruction(boundEmulation.getCPU(), boundEmulation.getMemory(), instruction, opcode,
                operands);

    }
}
