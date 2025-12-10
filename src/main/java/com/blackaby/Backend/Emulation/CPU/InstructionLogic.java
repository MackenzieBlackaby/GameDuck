package com.blackaby.Backend.Emulation.CPU;

import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;
import com.blackaby.Backend.Emulation.Memory.DuckAddresses;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * This class handles the logic for different CPU instructions
 * Each instruction will return the T-cycle count for that specific instruction
 */
public class InstructionLogic {
    private static DuckCPU cpu;
    private static DuckMemory memory;

    /**
     * Link the logic to the CPU and Memory instances.
     * MUST be called before execution starts.
     */
    public static void init(DuckCPU cpuInstance, DuckMemory memoryInstance) {
        cpu = cpuInstance;
        memory = memoryInstance;
    }

    /**
     * Enum representing different types of bitwise operations
     */
    public enum BitwiseType {
        AND, OR, XOR
    }

    /**
     * Enum representing various locations that can be accessed by opeations
     */
    public enum OpLocation {
        REGISTER, HL_MEMORY, IMMEDIATE
    }

    /**
     * Enum representing different arithmetic operations
     */
    public enum ArithmeticType {
        ADD, SUB, CP
    }

    /**
     * Enum representing different CB-prefixed bit operations
     */
    public enum BitOpType {
        BIT, RES, SET
    }

    /**
     * Enum representing different rotate operations
     */
    public enum RotateType {
        RL, RLA, RLC, RLCA, RR, RRA, RRC, RRCA
    }

    /**
     * Enum representing different shift operations
     */
    public enum ShiftType {
        SLA, SRA, SRL
    }

    /**
     * Stop Instruction
     * 
     * @return 4 T cycles
     */
    public static int Stop() {
        cpu.setStopped(true);
        return 4;
    }

    /**
     * Restart instruction
     * 
     * @param address the address to jump to - must be calculated
     * @return 16 T cycles
     */
    public static int Restart(int address) {
        int pc = cpu.getPC();
        memory.stackPushShort(pc);
        cpu.setPC(address & 0xFFFF);
        return 16;
    }

    /**
     * Nop instruction
     * 
     * @return 4 T cycles
     */
    public static int Nop() {
        return 4;
    }

    /**
     * Interrupt control
     * 
     * @param enable whether to enable interrupts or not
     * @return 4 T cycles
     */
    public static int InterruptControl(boolean enable) {
        if (enable)
            cpu.scheduleEnableInterrupts();
        else
            cpu.disableInterrupts();
        return 4;
    }

    /**
     * Halt instruction, with halt bug implemented
     * 
     * @return 4 T cycles
     */
    public static int Halt() {
        int ie = memory.read(DuckAddresses.IE);
        int ifFlag = memory.read(DuckAddresses.INTERRUPT_FLAG);
        boolean interruptPending = (ie & ifFlag & 0x1F) != 0;
        if (!cpu.isInterruptMasterEnable() && interruptPending) {
            // TODO: Halt bug, Return 99 and check in execute
        } else {
            cpu.setHalted(true);
        }
        return 4;
    }

    /**
     * Set carry flag instruction
     * 
     * @return 4 T cycles
     */
    public static int SetCarryFlag() {
        cpu.setFlag(Flag.C, true);
        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.H, false);
        return 4;
    }

    /**
     * Increments or decrements a 16 bit register
     * 
     * @param register    the register to act on - must be calculated
     * @param isIncrement true if its increment, false if its decrement
     * @return 8 T cycles
     */
    public static int IncrementDecrementShort(Register register, boolean isIncrement) {
        int value = cpu.regGet16(register);
        value = (value + (isIncrement ? 1 : -1)) & 0xFFFF;
        cpu.regSet16(register, value);
        return 8;
    }

    /**
     * Increments or decrements a 8 bit register
     * 
     * @param register    the register to act on - must be calculated
     * @param isIncrement true if its increment, false if its decrement
     * @return 4 T cycles
     */
    public static int IncrementDecrementByteRegister(Register register, boolean isIncrement) {
        int value = cpu.regGet(register);
        int oldValue = value;
        value = (value + (isIncrement ? 1 : -1)) & 0xFF;
        cpu.regSet(register, value);
        // Setting flags
        cpu.setFlag(Flag.Z, value == 0);
        cpu.setFlag(Flag.N, !isIncrement);
        if (isIncrement)
            cpu.setFlag(Flag.H, (oldValue & 0xF) == 0xF);
        else
            cpu.setFlag(Flag.H, (oldValue & 0xF) == 0x0);
        return 4;
    }

    /**
     * Increments or decrements the byte at location specified by the HL register
     * 
     * @param isIncrement true if its increment, false if its decrement
     * @return 12 T cycles
     */
    public static int IncrementDecrementByteHL(boolean isIncrement) {
        int value = memory.read(cpu.getHL());
        int oldValue = value;
        value = (value + (isIncrement ? 1 : -1)) & 0xFF;
        memory.write(cpu.getHL(), value);
        // Setting flags
        cpu.setFlag(Flag.Z, value == 0);
        cpu.setFlag(Flag.N, !isIncrement);
        if (isIncrement)
            cpu.setFlag(Flag.H, (oldValue & 0xF) == 0xF);
        else
            cpu.setFlag(Flag.H, (oldValue & 0xF) == 0x0);
        return 12;
    }

    /**
     * Adjusts Accumulator to be a valid BCD number after an add/sub
     * 
     * @return 4 T-cycles
     */
    public static int DecimalAdjustAccumulator() {
        int a = cpu.getAccumulator();
        int correction = 0;
        boolean n = cpu.getFlag(Flag.N);
        boolean h = cpu.getFlag(Flag.H);
        boolean c = cpu.getFlag(Flag.C);
        if (n) {
            // Adjusting for subtraction
            if (c)
                correction |= 0x60;
            if (h)
                correction |= 0x06;
            a -= correction;
        } else {
            // Adjusting for addition
            if (c || a > 0x99) {
                correction |= 0x60;
                c = true;
            }
            if (h || (a & 0x0F) > 9) {
                correction |= 0x06;
            }
            a += correction;
        }
        a &= 0xFF;
        cpu.setAccumulator(a);

        cpu.setFlag(Flag.Z, a == 0);
        cpu.setFlag(Flag.H, false);
        return 4;
    }

    /**
     * Inverts the Accumulator and adjusts flags
     * 
     * @return 4 T-cycles
     */
    public static int ComplementAccumulator() {
        int a = cpu.getAccumulator();
        a = (~a) & 0xFF;
        cpu.setAccumulator(a);
        cpu.setFlag(Flag.N, true);
        cpu.setFlag(Flag.H, true);
        return 4;
    }

    /**
     * Flips the carry flag and adjusts other flags
     * 
     * @return 4 T-cycles
     */
    public static int ComplementCarryFlag() {
        cpu.setFlag(Flag.C, !cpu.getFlag(Flag.C));
        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.H, false);
        return 4;
    }

    // Sets the bitwise flags of the CPU
    private static void SetBitwiseFlags(int result, BitwiseType bitwiseType) {
        cpu.setFlag(Flag.H, bitwiseType == BitwiseType.AND);
        cpu.setFlag(Flag.Z, result == 0);
        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.C, false);
    }

    // Function to calculate the result of a bitwise operation
    private static int CalculateBitwiseOp(int a, int b, BitwiseType bitwiseType) {
        switch (bitwiseType) {
            case AND -> {
                return a & b;
            }
            case OR -> {
                return a | b;
            }
            case XOR -> {
                return a ^ b;
            }
        }
        return -1;
    }

    // Engine that links all the bitwise operations, simplifying things
    private static int BitwiseEngine(BitwiseType bitwiseType, int b) {
        int a = cpu.getAccumulator();
        int result = CalculateBitwiseOp(a, b, bitwiseType);
        cpu.setAccumulator(result);
        SetBitwiseFlags(result, bitwiseType);
        return 4;
    }

    /**
     * Bitwise Operation using byte specified in register HL
     * 
     * @param bitwiseType
     * @return 8 T-cycles
     */
    public static int Bitwise(BitwiseType bitwiseType) {
        BitwiseEngine(bitwiseType, memory.read(cpu.getHL()));
        return 8;
    }

    /**
     * Bitwise operation using specified register
     * 
     * @param bitwiseType type of operation to use
     * @param register
     * @return 4 T-cycles
     */
    public static int Bitwise(BitwiseType bitwiseType, Register register) {
        BitwiseEngine(bitwiseType, cpu.regGet(register));
        return 4;
    }

    /**
     * Bitwise operation using an immediate
     * 
     * @param bitwiseType type of operation to use
     * @param immediate   the immediate value
     * @return 8 T-cycles
     */
    public static int Bitwise(BitwiseType bitwiseType, int immediate) {
        BitwiseEngine(bitwiseType, immediate);
        return 8;
    }

    // Calculates the half carry when the modifier and carry is added/subtracted
    // from the positive register
    private static boolean CalculateHalfCarry(int positiveRegister, int modifier, int carry, ArithmeticType type) {
        if (type == ArithmeticType.ADD)
            return ((positiveRegister & 0xF) + (modifier & 0xF) + carry) > 0xF;
        else
            return ((positiveRegister & 0xF) - (modifier & 0xF) - carry) < 0;

    }

    // Calculates carry by comparing the result to the boundaries of a 1 byte int
    private static boolean CalculateCarry(int result, ArithmeticType type) {
        if (type == ArithmeticType.ADD)
            return result > 0xFF;
        else
            return result < 0;

    }

    private static void ArithmeticEngine(int b, boolean usingCarry, ArithmeticType type) {
        int a = cpu.getAccumulator();
        int result = 0;
        int carry = (usingCarry && cpu.getFlag(Flag.C) ? 1 : 0);
        int diff = (b + carry);
        // Perform operation
        if (type == ArithmeticType.ADD)
            result = a + diff;
        else
            result = a - diff;

        if (type != ArithmeticType.CP)
            cpu.setAccumulator(result);

        cpu.setFlag(Flag.Z, (result & 0xFF) == 0);
        cpu.setFlag(Flag.N, type != ArithmeticType.ADD);
        cpu.setFlag(Flag.C, CalculateCarry(result, type));
        cpu.setFlag(Flag.H, CalculateHalfCarry(a, b, carry, type));
    }

    /**
     * Arithmetic operation for working on the data specified at address in register
     * HL
     * Default is not using carry
     * 
     * @param type the opeation type
     * @return 8 T-cycles
     */
    public static int Arithmetic(ArithmeticType type) {
        return Arithmetic(type, false);
    }

    /**
     * Arithmetic operation for working on the data specified at address in register
     * HL, specifying carry
     * 
     * @param type       the opeation type
     * @param usingCarry whether to use carry or not
     * @return 8 T-cycles
     */
    public static int Arithmetic(ArithmeticType type, boolean usingCarry) {
        ArithmeticEngine(memory.read(cpu.getHL()), usingCarry, type);
        return 8;
    }

    /**
     * Arithmetic operation for working on the data in a register
     * Default is not using carry
     * 
     * @param type     the opeation type
     * @param register the register
     * @return 4 T-cycles
     */
    public static int Arithmetic(ArithmeticType type, Register register) {
        return Arithmetic(type, register, false);
    }

    /**
     * Arithmetic operation for working on the data in a register, specifying carry
     * 
     * @param type       the opeation type
     * @param register   the register
     * @param usingCarry whether to use carry or not
     * @return 4 T-cycles
     */
    public static int Arithmetic(ArithmeticType type, Register register, boolean usingCarry) {
        ArithmeticEngine(cpu.regGet(register), usingCarry, type);
        return 4;
    }

    /**
     * Arithmetic operation for working on an immediate
     * Default is not using carry
     * 
     * @param type      the opeation type
     * @param immediate the immediate
     * @return 8 T-cycles
     */
    public static int Arithmetic(ArithmeticType type, int immediate) {
        return Arithmetic(type, immediate, false);
    }

    /**
     * Arithmetic operation for working on an immediate, specifying carry
     * 
     * @param type       the opeation type
     * @param immediate  the immediate
     * @param usingCarry whether to use carry or not
     * @return 8 T-cycles
     */
    public static int Arithmetic(ArithmeticType type, int immediate, boolean usingCarry) {
        ArithmeticEngine(immediate, usingCarry, type);
        return 8;
    }

    /**
     * Adds a register pair to HL
     * 
     * @param registerPair the register pair to add
     * @return 8 T-cycles
     */
    public static int AddPairHL(Register registerPair) {
        int hl = cpu.getHL();
        int value = cpu.regGet16(registerPair);
        int result = hl + value;
        cpu.setHL(result & 0xFFFF);

        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.H, (hl & 0xFFF) + (value & 0xFFF) > 0xFFF);
        cpu.setFlag(Flag.C, result > 0xFFFF);
        // Z is not affected
        return 8;
    }

    public static int AddByteSP(int immediate) {
        int sp = cpu.getSP();

        int signedOffset = (byte) immediate;
        boolean halfCarry = ((sp & 0x0F) + (immediate & 0x0F)) > 0x0F;
        boolean carry = ((sp & 0xFF) + (immediate & 0xFF)) > 0xFF;

        int result = (sp + signedOffset) & 0xFFFF;

        cpu.setSP(result);

        cpu.setFlag(Flag.Z, false);
        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.H, halfCarry);
        cpu.setFlag(Flag.C, carry);
        return 16;

    }

    private static void LoadRegisterEngineByte(Register destination, int value) {
        cpu.regSet(destination, value);
    }

    private static void LoadRegisterEngineShort(Register destination, int value) {
        cpu.regSet16(destination, value);
    }

    /**
     * Loads a value from a register into another register
     * 
     * @param destination the destination register
     * @param source      the source register
     * @return 4 T-cycles
     */
    public static int LoadRegisterFromRegister(Register destination, Register source) {
        LoadRegisterEngineByte(destination, cpu.regGet(source));
        return 4;
    }

    /**
     * Loads a value from an immediate into a register
     * 
     * @param destination the destination register
     * @param value       the immediate
     * @return 8 T-cycles
     */
    public static int LoadRegisterFromImmediate(Register destination, int value) {
        LoadRegisterEngineByte(destination, value);
        return 8;
    }

    /**
     * Loads a value from an immediate into a register pair
     * 
     * @param destination the destination register pair
     * @param immediate   the immediate
     * @return 12 T-cycles
     */
    public static int LoadRegisterPairFromImmediate(Register destination, int immediate) {
        LoadRegisterEngineShort(destination, immediate);
        return 12;
    }

    // Stores accumulator in given memory address
    private static void StoreAccumulatorInAddress(int address) {
        memory.write(address, cpu.getAccumulator());
    }

    /**
     * Stores the accumulator value in memory at address specified by a register
     * pair
     * 
     * @param registerPair the register pair
     * @return 8 T-cycles
     */
    public static int AccumulatorToMemoryViaRegisterPair(Register registerPair) {
        StoreAccumulatorInAddress(cpu.regGet16(registerPair));
        return 8;
    }

    /**
     * Stores the accumulator value in memory at address specified by HL.
     * HL is then incremented
     * 
     * @return 8 T-cycles
     */
    public static int AccumulatorToMemoryViaHLIncrement() {
        StoreAccumulatorInAddress(cpu.getHL());
        cpu.setHL(cpu.getHL() + 1);
        return 8;
    }

    /**
     * Stores the accumulator value in memory at address specified by HL.
     * HL is then decremented
     * 
     * @return 8 T-cycles
     */
    public static int AccumulatorToMemoryViaHLDecrement() {
        StoreAccumulatorInAddress(cpu.getHL());
        cpu.setHL(cpu.getHL() - 1);
        return 8;
    }

    /**
     * Store the accumulator contents specified by a 2-byte immediate value
     * 
     * @param immediate the immediate value
     * @return 16 T-cycles
     */
    public static int AccumulatorToMemoryImmediate(int immediate) {
        StoreAccumulatorInAddress(immediate);
        return 16;
    }

    private static void AccumulatorToMemoryMasked(int mask) {
        int maskedAddress = 0xFF00 | (mask & 0xFF);
        StoreAccumulatorInAddress(maskedAddress);
    }

    /**
     * Stores the accumulator in memory at register 0xFF00 + immediate
     * 
     * @param immediate the immediate value
     * @return 12 T-cycles
     */
    public static int AccumulatorToMemoryWithImmediateMask(int immediate) {
        AccumulatorToMemoryMasked(immediate);
        return 12;
    }

    /**
     * Stores the accumulator in memory at register 0xFF00 + C
     * 
     * @return 8 T-cycles
     */
    public static int AccumulatorToMemoryWithCRegisterMask() {
        AccumulatorToMemoryMasked(cpu.getC());
        return 8;
    }

    /**
     * Stores an immmediate in memory at the address specified by HL
     * 
     * @param immediate the immediate value
     * @return 12 T-cycles
     */
    public static int ImmediateToMemoryViaHL(int immediate) {
        memory.write(cpu.getHL(), immediate);
        return 12;
    }

    /**
     * Stores an accumulator in memory at the address specified by HL
     * 
     * @return 8 T-cycles
     */
    public static int RegisterToMemoryViaHL(Register register) {
        memory.write(cpu.getHL(), cpu.regGet(register));
        return 8;
    }

    private static void LoadAccumulatorFromMemoryAddress(int address) {
        cpu.setAccumulator(memory.read(address));
    }

    public static int LoadAccumulatorFromMemoryViaRegisterPair(Register registerPair) {
        LoadAccumulatorFromMemoryAddress(cpu.regGet16(registerPair));
        return 8;
    }

    /**
     * Loads the accumulator from memory at address specified by HL.
     * HL is then incremented
     * 
     * @return 8 T-cycles
     */

    public static int LoadAccumulatorFromMemoryViaHLIncrement() {
        LoadAccumulatorFromMemoryViaRegisterPair(Register.HL);
        cpu.setHL(cpu.getHL() + 1);
        return 8;
    }

    /**
     * Loads the accumulator from memory at address specified by HL.
     * HL is then decremented
     * 
     * @return 8 T-cycles
     */
    public static int LoadAccumulatorFromMemoryViaHLDecrement() {
        LoadAccumulatorFromMemoryViaRegisterPair(Register.HL);
        cpu.setHL(cpu.getHL() - 1);
        return 8;
    }

    /**
     * Loads the accumulator from memory at address specified by an immediate
     * 
     * @param immediate the immediate address
     * @return 16 T-cycles
     */
    public static int LoadAccumulatorFromMemoryViaImmediate(int immediate) {
        LoadAccumulatorFromMemoryAddress(immediate);
        return 16;
    }

    /**
     * Loads the accumulator from memory at address 0xFF00 + immediate mask
     * 
     * @param mask the immediate mask
     * @return 12 T-cycles
     */
    public static int LoadACcumulatorFromMemoryViaMaskedImmediate(int mask) {
        int maskedAddress = 0xFF00 | (mask & 0xFF);
        LoadAccumulatorFromMemoryAddress(maskedAddress);
        return 12;
    }

    /**
     * Loads the accumulator from memory at address 0xFF00 + C register value
     * 
     * @return 8 T-cycles
     */
    public static int LoadAccumulatorFromMemoryViaCRegisterMask() {
        LoadACcumulatorFromMemoryViaMaskedImmediate(cpu.getC());
        return 8;
    }

    /**
     * Loads a register from memory at the address specified by HL
     * 
     * @param register the destination register
     * @return 8 T-cycles
     */
    public static int LoadRegisterFromMemoryViaHL(Register register) {
        cpu.regSet(register, memory.read(cpu.getHL()));
        return 8;
    }

    /**
     * Sets the Stack Pointer (SP) to the value of the HL register pair
     * 
     * @return 8 T-cycles
     */
    public static int SetSPToHL() {
        cpu.setSP(cpu.getHL());
        return 8;
    }

    /**
     * Stores the Stack Pointer (SP) in memory at the address specified by a
     * register pair
     * 
     * @param registerPair the register pair specifying the address
     * @return 20 T-cycles
     */
    public static int StoreSPInAddressViaRegisterPair(Register registerPair) {
        int sp = cpu.getSP();
        int address1 = cpu.regGet16(registerPair);
        int address2 = (address1 + 1) & 0xFFFF;
        memory.write(address1, sp & 0xFF);
        memory.write(address2, (sp >> 8) & 0xFF);
        return 20;
    }

    /**
     * Loads HL with the result of SP + a signed immediate value
     * 
     * @param immediate the signed 8-bit immediate
     * @return 12 T-cycles
     */
    public static int LoadToHLStackPointerPlusImmediate(int immediate) {
        int sp = cpu.getSP();
        int signedOffset = (byte) immediate;
        cpu.setHL(sp + signedOffset);

        // H and C flags are calculated on the lower byte as an *unsigned* addition
        boolean halfCarry = ((sp & 0x0F) + (immediate & 0x0F)) > 0x0F;
        boolean carry = ((sp & 0xFF) + (immediate & 0xFF)) > 0xFF;

        cpu.setFlag(Flag.Z, false);
        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.H, halfCarry);
        cpu.setFlag(Flag.C, carry);
        return 12;
    }

    /**
     * Pops a 16-bit value from the stack into a register pair
     * 
     * @param registerPair the destination register pair
     * @return 12 T-cycles
     */
    public static int StackPopToRegisterPair(Register registerPair) {
        int popped = memory.stackPopShort();
        cpu.regSet16(registerPair, popped);
        return 12;
    }

    /**
     * Pushes a 16-bit value from a register pair onto the stack
     * 
     * @param registerPair the source register pair
     * @return 16 T-cycles
     */
    public static int StackPushFromRegisterPair(Register registerPair) {
        memory.stackPushShort(cpu.regGet16(registerPair));
        return 16;
    }

    // =============================================================
    // CB-PREFIXED INSTRUCTIONS
    // =============================================================

    private static void BitOpEngine(BitOpType opType, int bit, int value, Register register) {
        int mask = 1 << bit;
        int result = value;

        switch (opType) {
            case BIT:
                cpu.setFlag(Flag.Z, (value & mask) == 0);
                cpu.setFlag(Flag.N, false);
                cpu.setFlag(Flag.H, true);
                // C flag is not affected
                return; // No value to write back
            case RES:
                result = value & ~mask;
                break;
            case SET:
                result = value | mask;
                break;
        }

        if (register == Register.HL_ADDR) {
            memory.write(cpu.getHL(), result);
        } else {
            cpu.regSet(register, result);
        }
    }

    /**
     * Performs a BIT, RES, or SET operation on a register.
     * 
     * @param opType   the type of bit operation (BIT, RES, SET)
     * @param bit      the bit position (0-7)
     * @param register the target register
     * @return 8 T-cycles
     */
    public static int BitOperation(BitOpType opType, int bit, Register register) {
        BitOpEngine(opType, bit, cpu.regGet(register), register);
        return 8;
    }

    /**
     * Performs a BIT, RES, or SET operation on the memory at (HL).
     * 
     * @param opType the type of bit operation (BIT, RES, SET)
     * @param bit    the bit position (0-7)
     * @return 12 T-cycles for BIT, 16 for RES/SET
     */
    public static int BitOperationHL(BitOpType opType, int bit) {
        BitOpEngine(opType, bit, memory.read(cpu.getHL()), Register.HL_ADDR);
        return opType == BitOpType.BIT ? 12 : 16;
    }

    private static void RotateEngine(RotateType type, int value, Register register, boolean isCBPrefix) {
        int result;
        boolean oldCarry = cpu.getFlag(Flag.C);
        boolean newCarry;

        switch (type) {
            case RLC: // Rotate Left Circular
            case RLCA:
                newCarry = (value & 0x80) != 0;
                result = (value << 1) | (newCarry ? 1 : 0);
                break;
            case RRC: // Rotate Right Circular
            case RRCA:
                newCarry = (value & 0x01) != 0;
                result = (value >>> 1) | (newCarry ? 0x80 : 0);
                break;
            case RL: // Rotate Left through Carry
            case RLA:
                newCarry = (value & 0x80) != 0;
                result = (value << 1) | (oldCarry ? 1 : 0);
                break;
            case RR: // Rotate Right through Carry
            case RRA:
                newCarry = (value & 0x01) != 0;
                result = (value >>> 1) | (oldCarry ? 0x80 : 0);
                break;
            default:
                return; // Should not happen
        }

        result &= 0xFF;

        if (register == Register.HL_ADDR) {
            memory.write(cpu.getHL(), result);
        } else {
            cpu.regSet(register, result);
        }

        cpu.setFlag(Flag.Z, isCBPrefix && (result == 0));
        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.H, false);
        cpu.setFlag(Flag.C, newCarry);
    }

    /**
     * Performs a rotate operation on a register or memory at (HL).
     * 
     * @param type     the type of rotate operation
     * @param register the target register, or HL_ADDR for memory
     * @return 4 for A-reg, 8 for other regs, 16 for (HL)
     */
    public static int Rotate(RotateType type, Register register) {
        boolean isCBPrefix = type != RotateType.RLA && type != RotateType.RLCA && type != RotateType.RRA
                && type != RotateType.RRCA;
        int value = (register == Register.HL_ADDR) ? memory.read(cpu.getHL()) : cpu.regGet(register);

        RotateEngine(type, value, register, isCBPrefix);

        if (!isCBPrefix)
            return 4; // RLA, RLCA, etc.
        return (register == Register.HL_ADDR) ? 16 : 8;
    }

    private static void ShiftEngine(ShiftType type, int value, Register register) {
        int result;
        boolean newCarry;

        switch (type) {
            case SLA: // Shift Left Arithmetic
                newCarry = (value & 0x80) != 0;
                result = value << 1;
                break;
            case SRA: // Shift Right Arithmetic
                newCarry = (value & 0x01) != 0;
                result = (value >> 1) | (value & 0x80); // Preserve MSB
                break;
            case SRL: // Shift Right Logical
                newCarry = (value & 0x01) != 0;
                result = value >>> 1;
                break;
            default:
                return; // Should not happen
        }
        result &= 0xFF;

        if (register == Register.HL_ADDR) {
            memory.write(cpu.getHL(), result);
        } else {
            cpu.regSet(register, result);
        }

        cpu.setFlag(Flag.Z, result == 0);
        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.H, false);
        cpu.setFlag(Flag.C, newCarry);
    }

    /**
     * Performs a shift operation on a register or memory at (HL).
     * 
     * @param type     the type of shift operation
     * @param register the target register, or HL_ADDR for memory
     * @return 8 T-cycles for registers, 16 for (HL)
     */
    public static int Shift(ShiftType type, Register register) {
        int value = (register == Register.HL_ADDR) ? memory.read(cpu.getHL()) : cpu.regGet(register);
        ShiftEngine(type, value, register);
        return (register == Register.HL_ADDR) ? 16 : 8;
    }

    /**
     * Swaps the upper and lower nibbles of a register or memory at (HL).
     * 
     * @param register the target register, or HL_ADDR for memory
     * @return 8 T-cycles for registers, 16 for (HL)
     */
    public static int Swap(Register register) {
        int value = (register == Register.HL_ADDR) ? memory.read(cpu.getHL()) : cpu.regGet(register);
        int result = ((value & 0x0F) << 4) | ((value & 0xF0) >> 4);

        if (register == Register.HL_ADDR) {
            memory.write(cpu.getHL(), result);
        } else {
            cpu.regSet(register, result);
        }

        cpu.setFlag(Flag.Z, result == 0);
        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.H, false);
        cpu.setFlag(Flag.C, false);

        return (register == Register.HL_ADDR) ? 16 : 8;
    }

    // =============================================================
    // FLOW CONTROL INSTRUCTIONS
    // =============================================================

    /**
     * Jumps to a new address. Can be conditional, relative, or to (HL).
     * 
     * @param conditionMet true if the jump condition is met
     * @param isRelative   true for a relative jump (JR)
     * @param isHL         true for a jump to the address in HL
     * @param operand      the 8-bit or 16-bit operand for the jump
     * @return T-cycles (12/16 for JP, 8/12 for JR, 4 for JP (HL))
     */
    public static int Jump(boolean conditionMet, boolean isRelative, boolean isHL, int operand) {
        if (!conditionMet) {
            return isRelative ? 8 : 12; // Cycles for not taking jump
        }

        if (isHL) {
            cpu.setPC(cpu.getHL());
            return 4;
        } else if (isRelative) {
            cpu.setPC(cpu.getPC() + (byte) operand);
            return 12;
        } else {
            cpu.setPC(operand);
            return 16;
        }
    }

    /**
     * Calls a subroutine at a new address. Can be conditional.
     * 
     * @param conditionMet true if the call condition is met
     * @param address      the 16-bit address to call
     * @return T-cycles (12 if not taken, 24 if taken)
     */
    public static int Call(boolean conditionMet, int address) {
        if (!conditionMet) {
            return 12;
        }
        memory.stackPushShort(cpu.getPC());
        cpu.setPC(address);
        return 24;
    }

    /**
     * Returns from a subroutine. Can be conditional.
     * 
     * @param conditionMet true if the return condition is met
     * @param isInterrupt  true if this is a RETI instruction
     * @return T-cycles (8 if not taken, 16/20 if taken)
     */
    public static int Return(boolean conditionMet, boolean isInterrupt) {
        if (!conditionMet) {
            return 8;
        }
        cpu.setPC(memory.stackPopShort());
        if (isInterrupt) {
            cpu.scheduleEnableInterrupts();
        }
        return 16;
    }
}