package com.blackaby.OldBackEnd.Emulation.CPU;

import java.util.HashMap;
import java.util.Map;

import com.blackaby.OldBackEnd.Emulation.DuckEmulation;
import com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Bits.*;
import com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Flow.*;
import com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.CPU.*;
import com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory.*;
import com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Load.Memory.Stack.*;
import com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math.*;
import com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math.Arithmetic.ArithmeticType;
import com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math.Bitwise.BitwiseType;
import com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Misc.*;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;

/**
 * The DuckDecoder class is responsible for decoding Game Boy CPU opcodes and
 * mapping them to their corresponding Instruction instances.
 * <p>
 * It defines an {@link InstructionType} enumeration that categorises all
 * supported
 * instruction formats by opcode patterns, operand count, and CB-prefix status.
 * The class also provides static methods for initialising a map of opcodes to
 * instructions, determining an InstructionType from an opcode, and constructing
 * a fully configured Instruction instance from its opcode and operands.
 * </p>
 */
public class DuckDecoder {
    /**
     * The InstructionType enum enumerates all the supported opcode patterns and
     * associated instruction metadata.
     * <p>
     * Each instance holds the base opcode, a main mask (or masks) used for matching
     * and
     * operand extraction, the number of operand bytes required, and a flag
     * indicating
     * whether this is a CB-prefixed instruction.
     * </p>
     */
    public enum InstructionType {
        // Load instructions. Main mask specifies which values are excluded from the
        // opcode
        REGISTER_REGISTER(0x40, 0x3F, new int[] { 0x38, 0x07 }, 0, false),
        IMMEDIATE_REGISTER(0x06, 0x38, 1, false),
        MEMORY_REGISTER_HL(0x46, 0x38, 0, false),
        REGISTER_MEMORY_HL(0x70, 0x07, 0, false),
        IMMEDIATE_MEMORY_HL(0x36, 0x00, 1, false),
        MEMORY_ACCUMULATOR_BC(0x0A, 0x00, 0, false),
        MEMORY_ACCUMULATOR_DE(0x1A, 0x00, 0, false),
        ACCUMULATOR_MEMORY_BC(0x02, 0x00, 0, false),
        ACCUMULATOR_MEMORY_DE(0x12, 0x00, 0, false),
        MEMORY_ACCUMULATOR_IMMEDIATE(0xFA, 0x00, 2, false),
        ACCUMULATOR_MEMORY_IMMEDIATE(0xEA, 0x00, 2, false),
        MEMORY_ACCUMULATOR_MSB_0xFF_C(0xF2, 0x00, 0, false),
        ACCUMULATOR_MEMORY_MSB_0xFF_C(0xE2, 0x00, 0, false),
        MEMORY_ACCUMULATOR_MSB_0xFF_IMMEDIATE(0xF0, 0x00, 1, false),
        ACCUMULATOR_MEMORY_MSB_0xFF_IMMEDIATE(0xE0, 0x00, 1, false),
        MEMORY_ACCUMULATOR_HL_DECREMENT(0x3A, 0x00, 0, false),
        ACCUMULATOR_MEMORY_HL_DECREMENT(0x32, 0x00, 0, false),
        MEMORY_ACCUMULATOR_HL_INCREMENT(0x2A, 0x00, 0, false),
        ACCUMULATOR_MEMORY_HL_INCREMENT(0x22, 0x00, 0, false),
        IMMEDIATE_PAIR(0x01, 0x30, 2, false),
        SP_MEMORY_IMMEDIATE(0x08, 0x00, 2, false),
        HL_SP(0xF9, 0x00, 0, false),
        STACKPUSH_RR(0xC5, 0x30, 0, false),
        STACKPOP_RR(0xC1, 0x30, 0, false),
        SP_PLUS_IMMEDIATE8_HL(0xF8, 0x00, 1, false),

        // Arithmetic instructions
        ADD_REGISTER_ACCUMULATOR(0x80, 0x07, 0, false),
        ADD_MEMORY_HL(0x86, 0x00, 0, false),
        ADD_IMMEDIATE(0xC6, 0x00, 1, false),
        ADC_REGISTER(0x88, 0x07, 0, false),
        ADC_MEMORY_HL(0x8E, 0x00, 0, false),
        ADC_IMMEDIATE(0xCE, 0x00, 1, false),
        SUB_REGISTER(0x90, 0x07, 0, false),
        SUB_MEMORY_HL(0x96, 0x00, 0, false),
        SUB_IMMEDIATE(0xD6, 0x00, 1, false),
        SBC_REGISTER(0x98, 0x07, 0, false),
        SBC_MEMORY_HL(0x9E, 0x00, 0, false),
        SBC_IMMEDIATE(0xDE, 0x00, 1, false),
        CP_REGISTER(0xB8, 0x07, 0, false),
        CP_MEMORY_HL(0xBE, 0x00, 0, false),
        CP_IMMEDIATE(0xFE, 0x00, 1, false),
        INC_REGISTER(0x04, 0x38, 0, false),
        INC_MEMORY_HL(0x34, 0x00, 0, false),
        DEC_REGISTER(0x05, 0x38, 0, false),
        DEC_MEMORY_HL(0x35, 0x00, 0, false),

        // Bitwise instructions
        AND_REGISTER(0xA0, 0x07, 0, false),
        AND_MEMORY_HL(0xA6, 0x00, 0, false),
        AND_IMMEDIATE(0xE6, 0x00, 1, false),
        OR_REGISTER(0xB0, 0x07, 0, false),
        OR_MEMORY_HL(0xB6, 0x00, 0, false),
        OR_IMMEDIATE(0xF6, 0x00, 1, false),
        XOR_REGISTER(0xA8, 0x07, 0, false),
        XOR_MEMORY_HL(0xAE, 0x00, 0, false),
        XOR_IMMEDIATE(0xEE, 0x00, 1, false),

        // Other instructions
        CCF(0x3F, 0x00, 0, false),
        SCF(0x37, 0x00, 0, false),
        DAA(0x27, 0x00, 0, false),
        CPL(0x2F, 0x00, 0, false),

        // 16-bit arithmetic instructions
        INC_REGISTER_16(0x03, 0x30, 0, false),
        DEC_REGISTER_16(0x0B, 0x30, 0, false),
        ADD_PAIR_TO_HL(0x09, 0x30, 0, false),
        ADD_BYTE_TO_SP(0xE8, 0x00, 1, false),

        // Rotate, shift and bit operation instructions
        ROTATE_LEFT_CIRCLE_ACCUMULATOR(0x07, 0x00, 0, false),
        ROTATE_RIGHT_CIRCLE_ACCUMULATOR(0x0F, 0x00, 0, false),
        ROTATE_LEFT_ACCUMULATOR(0x17, 0x00, 0, false),
        ROTATE_RIGHT_ACCUMULATOR(0x1F, 0x00, 0, false),
        ROTATE_LEFT_CIRCLE_REGISTER(0x00, 0x07, 0, true), // CB from now on
        ROTATE_LEFT_CIRCLE_HL(0x06, 0x00, 0, true),
        ROTATE_RIGHT_CIRCLE_REGISTER(0x08, 0x07, 0, true),
        ROTATE_RIGHT_CIRCLE_HL(0x0E, 0x00, 0, true),
        ROTATE_LEFT_REGISTER(0x10, 0x07, 0, true),
        ROTATE_LEFT_HL(0x16, 0x00, 0, true),
        ROTATE_RIGHT_REGISTER(0x18, 0x07, 0, true),
        ROTATE_RIGHT_HL(0x1E, 0x00, 0, true),
        SHIFT_LEFT_ARITHMETIC_REGISTER(0x20, 0x07, 0, true),
        SHIFT_LEFT_ARITHMETIC_HL(0x26, 0x00, 0, true),
        SHIFT_RIGHT_ARITHMETIC_REGISTER(0x28, 0x07, 0, true),
        SHIFT_RIGHT_ARITHMETIC_HL(0x2E, 0x00, 0, true),
        SWAP_NIBBLES_REGISTER(0x30, 0x07, 0, true),
        SWAP_NIBBLES_HL(0x36, 0x00, 0, true),
        SHIFT_RIGHT_LOGICAL_REGISTER(0x38, 0x07, 0, true),
        SHIFT_RIGHT_LOGICAL_HL(0x3E, 0x00, 0, true),
        TEST_BIT_REGISTER(0x40, 0x3F, new int[] { 0x38, 0x07 }, 0, true),
        TEST_BIT_HL(0x46, 0x38, 0, true),
        RESET_BIT_REGISTER(0x80, 0x3F, new int[] { 0x38, 0x07 }, 0, true),
        RESET_BIT_HL(0x86, 0x38, 0, true),
        SET_BIT_REGISTER(0xC0, 0x3F, new int[] { 0x38, 0x07 }, 0, true),
        SET_BIT_HL(0xC6, 0x38, 0, true),

        // Control flow instructions
        JUMP_UNCONDITIONAL(0xC3, 0x00, 2, false),
        JUMP_HL(0xE9, 0x00, 0, false),
        JUMP_CONDITIONAL(0xC2, 0x18, 2, false),
        JUMP_RELATIVE_UNCONDITIONAL(0x18, 0x00, 1, false),
        JUMP_RELATIVE_CONDITIONAL(0x20, 0x18, 1, false),
        CALL_UNCONDITIONAL(0xCD, 0x00, 2, false),
        CALL_CONDITIONAL(0xC4, 0x18, 2, false),
        RETURN_UNCONDITIONAL(0xC9, 0x00, 0, false),
        RETURN_CONDITIONAL(0xC0, 0x18, 0, false),
        RETURN_INTERRUPT(0xD9, 0x00, 0, false),
        RESTART_UNCONDITIONAL(0xC7, 0x38, 0, false),

        // Misc
        HALT(0x76, 0x00, 0, false),
        STOP(0x10, 0x00, 1, false),
        DISABLE_INTERRUPTS(0xF3, 0x00, 0, false),
        ENABLE_INTERRUPTS(0xFB, 0x00, 0, false),
        NOP(0x00, 0x00, 0, false);

        private final int opcode;
        private final int mainMask;
        private final int[] masks;
        private final int operandCount;
        private final boolean cbStatus;

        /**
         * Constructs an InstructionType with multiple masks.
         *
         * @param opcode       The base opcode value.
         * @param mainMask     The main mask used to exclude variable bits.
         * @param masks        Additional masks for operand extraction.
         * @param operandCount The number of operand bytes.
         * @param cbStatus     True if this instruction is CB-prefixed.
         */
        InstructionType(int opcode, int mainMask, int masks[], int operandCount, boolean cbStatus) {
            this.opcode = opcode;
            this.masks = masks;
            this.mainMask = mainMask;
            this.operandCount = operandCount;
            this.cbStatus = cbStatus;
        }

        /**
         * Constructs an InstructionType with a single mask.
         *
         * @param opcode       The base opcode value.
         * @param mainMask     The mask used for both matching and operand extraction.
         * @param operandCount The number of operand bytes.
         * @param cbStatus     True if this instruction is CB-prefixed.
         */
        InstructionType(int opcode, int mainMask, int operandCount, boolean cbStatus) {
            this.opcode = opcode;
            this.masks = new int[] { mainMask };
            this.mainMask = mainMask;
            this.operandCount = operandCount;
            this.cbStatus = cbStatus;
        }

        /**
         * Returns the base opcode associated with this instruction type.
         *
         * @return The opcode integer value.
         */
        public int getOpcode() {
            return opcode;
        }

        /**
         * Returns the number of operand bytes required by this instruction.
         *
         * @return The operand count.
         */
        public int getOperandCount() {
            return operandCount;
        }

        /**
         * Calculates the number of right shifts needed to isolate a value from a given
         * bitmask.
         *
         * @param mask The bitmask used to extract bits from the opcode.
         * @return The number of bits to shift.
         */
        public static int getShiftAmount(int mask) {
            int shift = 0;
            while ((mask & 1) == 0) {
                mask >>= 1;
                shift++;
            }
            return shift;
        }

        /**
         * Determines if the given opcode matches this instruction type's pattern.
         *
         * @param inputOpcode The full 8-bit opcode to test.
         * @return True if the opcode matches this type; false otherwise.
         */
        public boolean matches(int inputOpcode) {
            return (inputOpcode & ~mainMask) == (opcode & ~mainMask);
        }

        /**
         * Extracts the value from the input opcode using the specified bitmask.
         *
         * @param inputOpcode The opcode from which to extract the value.
         * @param bitMask     The bitmask used for extraction.
         * @return The extracted value.
         */
        private int extractValue(int inputOpcode, int bitMask) {
            return (inputOpcode & bitMask) >> getShiftAmount(bitMask);
        }

        /**
         * Extracts encoded bitfield values from the opcode using this instruction
         * type's masks.
         *
         * @param opcode The full 8-bit opcode.
         * @return An array of values decoded from the opcode.
         */
        public int[] getValues(int opcode) {
            int[] values = new int[masks.length];
            if (masks[0] != 0) {
                for (int i = 0; i < masks.length; i++) {
                    values[i] = extractValue(opcode, masks[i]);
                }
                return values;
            } else
                return new int[0];
        }
    }

    private static Map<InstructionType, Instruction> instructionMap;

    /**
     * Initialises the instruction map by associating each InstructionType with its
     * corresponding Instruction instance.
     *
     * @param cpu       The DuckCPU instance.
     * @param memory    The DuckMemory instance.
     * @param emulation The DuckEmulation instance.
     */
    public static void initialiseMap(DuckCPU cpu, DuckMemory memory, DuckEmulation emulation) {
        instructionMap = new HashMap<InstructionType, Instruction>();
        instructionMap.put(InstructionType.REGISTER_REGISTER, new LoadToRegister(cpu, memory, false));
        instructionMap.put(InstructionType.IMMEDIATE_REGISTER, new LoadToRegister(cpu, memory, true));
        instructionMap.put(InstructionType.MEMORY_REGISTER_HL, new MemoryRegisterHL(cpu, memory));
        instructionMap.put(InstructionType.REGISTER_MEMORY_HL, new RegisterMemoryHL(cpu, memory));
        instructionMap.put(InstructionType.IMMEDIATE_MEMORY_HL, new ImmediateMemoryHL(cpu, memory));
        instructionMap.put(InstructionType.MEMORY_ACCUMULATOR_BC, new MemoryBCAccumulator(cpu, memory));
        instructionMap.put(InstructionType.MEMORY_ACCUMULATOR_DE, new MemoryDEAccumulator(cpu, memory));
        instructionMap.put(InstructionType.ACCUMULATOR_MEMORY_BC, new AccumulatorMemoryBC(cpu, memory));
        instructionMap.put(InstructionType.ACCUMULATOR_MEMORY_DE, new AccumulatorMemoryDE(cpu, memory));
        instructionMap.put(InstructionType.MEMORY_ACCUMULATOR_IMMEDIATE, new MemoryImmediateAccumulator(cpu, memory));
        instructionMap.put(InstructionType.ACCUMULATOR_MEMORY_IMMEDIATE, new AccumulatorMemoryImmediate(cpu, memory));
        instructionMap.put(InstructionType.MEMORY_ACCUMULATOR_MSB_0xFF_C, new MemoryAccumulatorMaskC(cpu, memory));
        instructionMap.put(InstructionType.ACCUMULATOR_MEMORY_MSB_0xFF_C, new AccumulatorMemoryMaskC(cpu, memory));
        instructionMap.put(InstructionType.MEMORY_ACCUMULATOR_MSB_0xFF_IMMEDIATE,
                new MemoryAccumulatorMaskImmediate(cpu, memory));
        instructionMap.put(InstructionType.ACCUMULATOR_MEMORY_MSB_0xFF_IMMEDIATE,
                new AccumulatorMemoryMaskImmediate(cpu, memory));
        instructionMap.put(InstructionType.MEMORY_ACCUMULATOR_HL_DECREMENT,
                new MemoryHLAccumulatorChange(cpu, memory, false));
        instructionMap.put(InstructionType.ACCUMULATOR_MEMORY_HL_DECREMENT,
                new AccumulatorMemoryHLChange(cpu, memory, false));
        instructionMap.put(InstructionType.MEMORY_ACCUMULATOR_HL_INCREMENT,
                new MemoryHLAccumulatorChange(cpu, memory, true));
        instructionMap.put(InstructionType.ACCUMULATOR_MEMORY_HL_INCREMENT,
                new AccumulatorMemoryHLChange(cpu, memory, true));
        instructionMap.put(InstructionType.IMMEDIATE_PAIR, new ImmediateRegisterPair(cpu, memory));
        instructionMap.put(InstructionType.SP_MEMORY_IMMEDIATE, new ImmediateSPMemory(cpu, memory));
        instructionMap.put(InstructionType.HL_SP, new SetSPToHL(cpu, memory));
        instructionMap.put(InstructionType.STACKPUSH_RR, new StackPush(cpu, memory));
        instructionMap.put(InstructionType.STACKPOP_RR, new StackPop(cpu, memory));
        instructionMap.put(InstructionType.SP_PLUS_IMMEDIATE8_HL, new SetHLToSPImmediate(cpu, memory));
        instructionMap.put(InstructionType.ADD_REGISTER_ACCUMULATOR,
                new Arithmetic(cpu, memory, ArithmeticType.ADD, ValueType.REGISTER, false));
        instructionMap.put(InstructionType.ADD_MEMORY_HL,
                new Arithmetic(cpu, memory, ArithmeticType.ADD, ValueType.HL_MEMORY, false));
        instructionMap.put(InstructionType.ADD_IMMEDIATE,
                new Arithmetic(cpu, memory, ArithmeticType.ADD, ValueType.IMMEDIATE, false));
        instructionMap.put(InstructionType.ADC_REGISTER,
                new Arithmetic(cpu, memory, ArithmeticType.ADD, ValueType.REGISTER, true));
        instructionMap.put(InstructionType.ADC_MEMORY_HL,
                new Arithmetic(cpu, memory, ArithmeticType.ADD, ValueType.HL_MEMORY, true));
        instructionMap.put(InstructionType.ADC_IMMEDIATE,
                new Arithmetic(cpu, memory, ArithmeticType.ADD, ValueType.IMMEDIATE, true));
        instructionMap.put(InstructionType.SUB_REGISTER,
                new Arithmetic(cpu, memory, ArithmeticType.SUBTRACT, ValueType.REGISTER, false));
        instructionMap.put(InstructionType.SUB_MEMORY_HL,
                new Arithmetic(cpu, memory, ArithmeticType.SUBTRACT, ValueType.HL_MEMORY, false));
        instructionMap.put(InstructionType.SUB_IMMEDIATE,
                new Arithmetic(cpu, memory, ArithmeticType.SUBTRACT, ValueType.IMMEDIATE, false));
        instructionMap.put(InstructionType.SBC_REGISTER,
                new Arithmetic(cpu, memory, ArithmeticType.SUBTRACT, ValueType.REGISTER, true));
        instructionMap.put(InstructionType.SBC_MEMORY_HL,
                new Arithmetic(cpu, memory, ArithmeticType.SUBTRACT, ValueType.HL_MEMORY, true));
        instructionMap.put(InstructionType.SBC_IMMEDIATE,
                new Arithmetic(cpu, memory, ArithmeticType.SUBTRACT, ValueType.IMMEDIATE, true));
        instructionMap.put(InstructionType.CP_REGISTER,
                new Arithmetic(cpu, memory, ArithmeticType.CP, ValueType.REGISTER, false));
        instructionMap.put(InstructionType.CP_MEMORY_HL,
                new Arithmetic(cpu, memory, ArithmeticType.CP, ValueType.HL_MEMORY, false));
        instructionMap.put(InstructionType.CP_IMMEDIATE,
                new Arithmetic(cpu, memory, ArithmeticType.CP, ValueType.IMMEDIATE, false));
        instructionMap.put(InstructionType.INC_REGISTER,
                new IncDec(cpu, memory, ValueType.REGISTER, true));
        instructionMap.put(InstructionType.INC_MEMORY_HL,
                new IncDec(cpu, memory, ValueType.HL_MEMORY, true));
        instructionMap.put(InstructionType.DEC_REGISTER,
                new IncDec(cpu, memory, ValueType.REGISTER, false));
        instructionMap.put(InstructionType.DEC_MEMORY_HL,
                new IncDec(cpu, memory, ValueType.HL_MEMORY, false));
        instructionMap.put(InstructionType.AND_REGISTER,
                new Bitwise(cpu, memory, ValueType.REGISTER, BitwiseType.AND));
        instructionMap.put(InstructionType.AND_MEMORY_HL,
                new Bitwise(cpu, memory, ValueType.HL_MEMORY, BitwiseType.AND));
        instructionMap.put(InstructionType.AND_IMMEDIATE,
                new Bitwise(cpu, memory, ValueType.IMMEDIATE, BitwiseType.AND));
        instructionMap.put(InstructionType.OR_REGISTER,
                new Bitwise(cpu, memory, ValueType.REGISTER, BitwiseType.OR));
        instructionMap.put(InstructionType.OR_MEMORY_HL,
                new Bitwise(cpu, memory, ValueType.HL_MEMORY, BitwiseType.OR));
        instructionMap.put(InstructionType.OR_IMMEDIATE,
                new Bitwise(cpu, memory, ValueType.IMMEDIATE, BitwiseType.OR));
        instructionMap.put(InstructionType.XOR_REGISTER,
                new Bitwise(cpu, memory, ValueType.REGISTER, BitwiseType.XOR));
        instructionMap.put(InstructionType.XOR_MEMORY_HL,
                new Bitwise(cpu, memory, ValueType.HL_MEMORY, BitwiseType.XOR));
        instructionMap.put(InstructionType.XOR_IMMEDIATE,
                new Bitwise(cpu, memory, ValueType.IMMEDIATE, BitwiseType.XOR));
        instructionMap.put(InstructionType.CCF, new CCF(cpu, memory));
        instructionMap.put(InstructionType.SCF, new SCF(cpu, memory));
        instructionMap.put(InstructionType.DAA, new DAA(cpu, memory));
        instructionMap.put(InstructionType.CPL, new CPL(cpu, memory));
        instructionMap.put(InstructionType.INC_REGISTER_16,
                new InDecrement16(cpu, memory, true));
        instructionMap.put(InstructionType.DEC_REGISTER_16,
                new InDecrement16(cpu, memory, false));
        instructionMap.put(InstructionType.ADD_PAIR_TO_HL,
                new AddPairHL(cpu, memory));
        instructionMap.put(InstructionType.ADD_BYTE_TO_SP,
                new AddByteSP(cpu, memory));
        instructionMap.put(InstructionType.ROTATE_LEFT_CIRCLE_ACCUMULATOR,
                new Rotate(cpu, memory, true, true, true, false, false));
        instructionMap.put(InstructionType.ROTATE_RIGHT_CIRCLE_ACCUMULATOR,
                new Rotate(cpu, memory, false, true, true, false, false));
        instructionMap.put(InstructionType.ROTATE_LEFT_ACCUMULATOR,
                new Rotate(cpu, memory, true, false, true, false, false));
        instructionMap.put(InstructionType.ROTATE_RIGHT_ACCUMULATOR,
                new Rotate(cpu, memory, false, false, true, false, false));
        instructionMap.put(InstructionType.ROTATE_LEFT_CIRCLE_REGISTER,
                new Rotate(cpu, memory, true, true, false, false, true));
        instructionMap.put(InstructionType.ROTATE_LEFT_CIRCLE_HL,
                new Rotate(cpu, memory, true, true, false, true, true));
        instructionMap.put(InstructionType.ROTATE_RIGHT_CIRCLE_REGISTER,
                new Rotate(cpu, memory, false, true, false, false, true));
        instructionMap.put(InstructionType.ROTATE_RIGHT_CIRCLE_HL,
                new Rotate(cpu, memory, false, true, false, true, true));
        instructionMap.put(InstructionType.ROTATE_LEFT_REGISTER,
                new Rotate(cpu, memory, true, false, false, false, true));
        instructionMap.put(InstructionType.ROTATE_LEFT_HL,
                new Rotate(cpu, memory, true, false, false, true, true));
        instructionMap.put(InstructionType.ROTATE_RIGHT_REGISTER,
                new Rotate(cpu, memory, false, false, false, false, true));
        instructionMap.put(InstructionType.ROTATE_RIGHT_HL,
                new Rotate(cpu, memory, false, false, false, true, true));
        instructionMap.put(InstructionType.SHIFT_LEFT_ARITHMETIC_REGISTER,
                new Shift(cpu, memory, true, true, true));
        instructionMap.put(InstructionType.SHIFT_LEFT_ARITHMETIC_HL,
                new Shift(cpu, memory, true, true, false));
        instructionMap.put(InstructionType.SHIFT_RIGHT_ARITHMETIC_REGISTER,
                new Shift(cpu, memory, false, true, true));
        instructionMap.put(InstructionType.SHIFT_RIGHT_ARITHMETIC_HL,
                new Shift(cpu, memory, false, true, false));
        instructionMap.put(InstructionType.SWAP_NIBBLES_REGISTER,
                new SwapNibbles(cpu, memory, false, true));
        instructionMap.put(InstructionType.SWAP_NIBBLES_HL,
                new SwapNibbles(cpu, memory, true, false));
        instructionMap.put(InstructionType.SHIFT_RIGHT_LOGICAL_REGISTER,
                new Shift(cpu, memory, false, false, true));
        instructionMap.put(InstructionType.SHIFT_RIGHT_LOGICAL_HL,
                new Shift(cpu, memory, false, false, false));
        instructionMap.put(InstructionType.TEST_BIT_REGISTER,
                new BitSet(cpu, memory, false, true, true));
        instructionMap.put(InstructionType.TEST_BIT_HL,
                new BitSet(cpu, memory, false, false, true));
        instructionMap.put(InstructionType.RESET_BIT_REGISTER,
                new BitSet(cpu, memory, false, true, false));
        instructionMap.put(InstructionType.RESET_BIT_HL,
                new BitSet(cpu, memory, false, false, false));
        instructionMap.put(InstructionType.SET_BIT_REGISTER,
                new BitSet(cpu, memory, true, true, false));
        instructionMap.put(InstructionType.SET_BIT_HL,
                new BitSet(cpu, memory, true, false, false));
        instructionMap.put(InstructionType.JUMP_UNCONDITIONAL,
                new Jump(cpu, memory, false, false, false));
        instructionMap.put(InstructionType.JUMP_HL,
                new Jump(cpu, memory, false, false, true));
        instructionMap.put(InstructionType.JUMP_CONDITIONAL,
                new Jump(cpu, memory, true, false, false));
        instructionMap.put(InstructionType.JUMP_RELATIVE_UNCONDITIONAL,
                new Jump(cpu, memory, false, true, false));
        instructionMap.put(InstructionType.JUMP_RELATIVE_CONDITIONAL,
                new Jump(cpu, memory, true, true, false));
        instructionMap.put(InstructionType.CALL_UNCONDITIONAL,
                new Call(cpu, memory, false));
        instructionMap.put(InstructionType.CALL_CONDITIONAL,
                new Call(cpu, memory, true));
        instructionMap.put(InstructionType.RETURN_UNCONDITIONAL,
                new Return(cpu, memory, false, false));
        instructionMap.put(InstructionType.RETURN_CONDITIONAL,
                new Return(cpu, memory, true, false));
        instructionMap.put(InstructionType.RETURN_INTERRUPT,
                new Return(cpu, memory, false, true));
        instructionMap.put(InstructionType.RESTART_UNCONDITIONAL,
                new Restart(cpu, memory));
        instructionMap.put(InstructionType.HALT, new Halt(cpu, memory));
        instructionMap.put(InstructionType.STOP, new Stop(cpu, memory));
        instructionMap.put(InstructionType.DISABLE_INTERRUPTS, new InterruptControl(cpu, memory, false));
        instructionMap.put(InstructionType.ENABLE_INTERRUPTS, new InterruptControl(cpu, memory, true));
        instructionMap.put(InstructionType.NOP, new Nop(cpu, memory));
    }

    /**
     * Determines the InstructionType corresponding to the given opcode.
     *
     * @param opcode The 8-bit opcode.
     * @param isCB   True if the opcode is CB-prefixed; false otherwise.
     * @return The matching InstructionType, or null if no match is found.
     */
    public static InstructionType getType(int opcode, boolean isCB) {
        opcode &= 0xFF;
        for (InstructionType type : InstructionType.values()) {
            if (isCB == type.cbStatus && type.matches(opcode)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Constructs a fully configured Instruction instance based on the given
     * InstructionType, opcode, and operands.
     *
     * @param type     The InstructionType to use.
     * @param opcode   The 8-bit opcode.
     * @param operands An array of operand values.
     * @return A fully configured Instruction instance ready for execution.
     */
    public static Instruction constructInstruction(InstructionType type, int opcode, int[] operands) {
        InstructionType instructionType = type;
        Instruction instruction = instructionMap.get(instructionType);
        int extractedValues[] = instructionType.getValues(opcode);

        instruction.setValues(extractedValues, operands);
        return instruction;
    }
}