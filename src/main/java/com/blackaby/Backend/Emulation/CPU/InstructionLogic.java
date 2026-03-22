package com.blackaby.Backend.Emulation.CPU;

import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;
import com.blackaby.Backend.Emulation.Memory.DuckAddresses;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * Holds the shared implementation for decoded CPU instructions.
 * <p>
 * Each helper mutates the active CPU and memory state for one LR35902
 * instruction and returns the number of T-cycles consumed by that operation.
 */
public class InstructionLogic {
    private static DuckCPU cpu;
    private static DuckMemory memory;

    /**
     * Binds the instruction helpers to the active CPU and memory instances.
     *
     * @param cpuInstance active CPU
     * @param memoryInstance active memory bus
     */
    public static void Initialise(DuckCPU cpuInstance, DuckMemory memoryInstance) {
        cpu = cpuInstance;
        memory = memoryInstance;
    }

    @Deprecated
    public static void init(DuckCPU cpuInstance, DuckMemory memoryInstance) {
        Initialise(cpuInstance, memoryInstance);
    }

    /**
     * Bitwise logic operations that target the accumulator.
     */
    public enum BitwiseType {
        AND, OR, XOR
    }

    /**
     * Operand locations used by the decoder.
     */
    public enum OpLocation {
        REGISTER, HL_MEMORY, IMMEDIATE
    }

    /**
     * Arithmetic operations handled by the shared arithmetic helpers.
     */
    public enum ArithmeticType {
        ADD, SUB, CP
    }

    /**
     * CB-prefixed bit operations.
     */
    public enum BitOpType {
        BIT, RES, SET
    }

    /**
     * Rotate operations in both normal and CB-prefixed forms.
     */
    public enum RotateType {
        RL, RLA, RLC, RLCA, RR, RRA, RRC, RRCA
    }

    /**
     * Shift operations in CB-prefixed space.
     */
    public enum ShiftType {
        SLA, SRA, SRL
    }

    /**
     * Executes `STOP`.
     *
     * @return 4 T-cycles
     */
    public static int Stop() {
        memory.HandleStopInstruction();
        return 4;
    }

    /**
     * Executes `RST`.
     *
     * @param address restart vector
     * @return 16 T-cycles
     */
    public static int Restart(int address) {
        int pc = cpu.GetPC();
        memory.StackPushShort(pc);
        cpu.SetPC(address & 0xFFFF);
        return 16;
    }

    /**
     * Executes `NOP`.
     *
     * @return 4 T-cycles
     */
    public static int Nop() {
        return 4;
    }

    /**
     * Executes `DI` or `EI`.
     *
     * @param enable `true` to schedule interrupt enable, `false` to disable IME
     * @return 4 T-cycles
     */
    public static int InterruptControl(boolean enable) {
        if (enable) {
            cpu.ScheduleEnableInterrupts();
        } else {
            cpu.DisableInterrupts();
        }
        return 4;
    }

    /**
     * Executes `HALT`, including the halt bug case.
     *
     * @return 4 T-cycles
     */
    public static int Halt() {
        int ie = memory.Read(DuckAddresses.IE);
        int ifFlag = memory.Read(DuckAddresses.INTERRUPT_FLAG);
        boolean interruptPending = (ie & ifFlag & 0x1F) != 0;
        if (!cpu.IsInterruptMasterEnable() && interruptPending) {
            cpu.SetHaltBug();
        } else {
            cpu.SetHalted(true);
        }
        return 4;
    }

    /**
     * Executes `SCF`.
     *
     * @return 4 T-cycles
     */
    public static int SetCarryFlag() {
        cpu.SetFlag(Flag.C, true);
        cpu.SetFlag(Flag.N, false);
        cpu.SetFlag(Flag.H, false);
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
        int value = cpu.GetRegisterPair(register);
        value = (value + (isIncrement ? 1 : -1)) & 0xFFFF;
        cpu.SetRegisterPair(register, value);
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
        int value = cpu.GetRegister(register);
        int oldValue = value;
        value = (value + (isIncrement ? 1 : -1)) & 0xFF;
        cpu.SetRegister(register, value);
        cpu.SetFlag(Flag.Z, value == 0);
        cpu.SetFlag(Flag.N, !isIncrement);
        if (isIncrement) {
            cpu.SetFlag(Flag.H, (oldValue & 0xF) == 0xF);
        } else {
            cpu.SetFlag(Flag.H, (oldValue & 0xF) == 0x0);
        }
        return 4;
    }

    /**
     * Increments or decrements the byte at location specified by the HL register
     * 
     * @param isIncrement true if its increment, false if its decrement
     * @return 12 T cycles
     */
    public static int IncrementDecrementByteHL(boolean isIncrement) {
        int value = memory.Read(cpu.GetHL());
        int oldValue = value;
        value = (value + (isIncrement ? 1 : -1)) & 0xFF;
        memory.Write(cpu.GetHL(), value);
        cpu.SetFlag(Flag.Z, value == 0);
        cpu.SetFlag(Flag.N, !isIncrement);
        if (isIncrement) {
            cpu.SetFlag(Flag.H, (oldValue & 0xF) == 0xF);
        } else {
            cpu.SetFlag(Flag.H, (oldValue & 0xF) == 0x0);
        }
        return 12;
    }

    /**
     * Adjusts Accumulator to be a valid BCD number after an add/sub
     * 
     * @return 4 T-cycles
     */
    public static int DecimalAdjustAccumulator() {
        int a = cpu.GetAccumulator();
        int correction = 0;
        boolean n = cpu.GetFlag(Flag.N);
        boolean h = cpu.GetFlag(Flag.H);
        boolean c = cpu.GetFlag(Flag.C);
        if (n) {
            if (c)
                correction |= 0x60;
            if (h)
                correction |= 0x06;
            a -= correction;
        } else {
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
        cpu.SetAccumulator(a);
        cpu.SetFlag(Flag.Z, a == 0);
        cpu.SetFlag(Flag.H, false);
        cpu.SetFlag(Flag.C, c);
        return 4;
    }

    /**
     * Inverts the Accumulator and adjusts flags
     * 
     * @return 4 T-cycles
     */
    public static int ComplementAccumulator() {
        int a = cpu.GetAccumulator();
        a = (~a) & 0xFF;
        cpu.SetAccumulator(a);
        cpu.SetFlag(Flag.N, true);
        cpu.SetFlag(Flag.H, true);
        return 4;
    }

    /**
     * Flips the carry flag and adjusts other flags
     * 
     * @return 4 T-cycles
     */
    public static int ComplementCarryFlag() {
        cpu.SetFlag(Flag.C, !cpu.GetFlag(Flag.C));
        cpu.SetFlag(Flag.N, false);
        cpu.SetFlag(Flag.H, false);
        return 4;
    }

    private static void SetBitwiseFlags(int result, BitwiseType bitwiseType) {
        cpu.SetFlag(Flag.H, bitwiseType == BitwiseType.AND);
        cpu.SetFlag(Flag.Z, result == 0);
        cpu.SetFlag(Flag.N, false);
        cpu.SetFlag(Flag.C, false);
    }

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

    private static int BitwiseEngine(BitwiseType bitwiseType, int b) {
        int a = cpu.GetAccumulator();
        int result = CalculateBitwiseOp(a, b, bitwiseType);
        cpu.SetAccumulator(result);
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
        BitwiseEngine(bitwiseType, memory.Read(cpu.GetHL()));
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
        BitwiseEngine(bitwiseType, cpu.GetRegister(register));
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

    private static boolean CalculateHalfCarry(int positiveRegister, int modifier, int carry, ArithmeticType type) {
        if (type == ArithmeticType.ADD)
            return ((positiveRegister & 0xF) + (modifier & 0xF) + carry) > 0xF;
        else
            return ((positiveRegister & 0xF) - (modifier & 0xF) - carry) < 0;

    }

    private static boolean CalculateCarry(int result, ArithmeticType type) {
        if (type == ArithmeticType.ADD)
            return result > 0xFF;
        else
            return result < 0;

    }

    private static void ArithmeticEngine(int b, boolean usingCarry, ArithmeticType type) {
        int a = cpu.GetAccumulator();
        int result = 0;
        int carry = (usingCarry && cpu.GetFlag(Flag.C) ? 1 : 0);
        int diff = (b + carry);
        if (type == ArithmeticType.ADD)
            result = a + diff;
        else
            result = a - diff;

        if (type != ArithmeticType.CP)
            cpu.SetAccumulator(result);

        cpu.SetFlag(Flag.Z, (result & 0xFF) == 0);
        cpu.SetFlag(Flag.N, type != ArithmeticType.ADD);
        cpu.SetFlag(Flag.C, CalculateCarry(result, type));
        cpu.SetFlag(Flag.H, CalculateHalfCarry(a, b, carry, type));
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
        ArithmeticEngine(memory.Read(cpu.GetHL()), usingCarry, type);
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
        ArithmeticEngine(cpu.GetRegister(register), usingCarry, type);
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
        int hl = cpu.GetHL();
        int value = cpu.GetRegisterPair(registerPair);
        int result = hl + value;
        cpu.SetHL(result & 0xFFFF);

        cpu.SetFlag(Flag.N, false);
        cpu.SetFlag(Flag.H, (hl & 0xFFF) + (value & 0xFFF) > 0xFFF);
        cpu.SetFlag(Flag.C, result > 0xFFFF);
        return 8;
    }

    public static int AddByteSP(int immediate) {
        int sp = cpu.GetSP();
        int signedOffset = (byte) immediate;
        int result = (sp + signedOffset) & 0xFFFF;
        int xor = sp ^ signedOffset ^ result;

        cpu.SetSP(result);
        cpu.SetFlag(Flag.Z, false);
        cpu.SetFlag(Flag.N, false);
        cpu.SetFlag(Flag.H, (xor & 0x10) != 0);
        cpu.SetFlag(Flag.C, (xor & 0x100) != 0);
        return 16;
    }

    private static void LoadRegisterEngineByte(Register destination, int value) {
        cpu.SetRegister(destination, value);
    }

    private static void LoadRegisterEngineShort(Register destination, int value) {
        cpu.SetRegisterPair(destination, value);
    }

    /**
     * Loads a value from a register into another register
     * 
     * @param destination the destination register
     * @param source      the source register
     * @return 4 T-cycles
     */
    public static int LoadRegisterFromRegister(Register destination, Register source) {
        LoadRegisterEngineByte(destination, cpu.GetRegister(source));
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

    private static void StoreAccumulatorInAddress(int address) {
        memory.Write(address, cpu.GetAccumulator());
    }

    /**
     * Stores the accumulator value in memory at address specified by a register
     * pair
     * 
     * @param registerPair the register pair
     * @return 8 T-cycles
     */
    public static int AccumulatorToMemoryViaRegisterPair(Register registerPair) {
        StoreAccumulatorInAddress(cpu.GetRegisterPair(registerPair));
        return 8;
    }

    /**
     * Stores the accumulator value in memory at address specified by HL.
     * HL is then incremented
     * 
     * @return 8 T-cycles
     */
    public static int AccumulatorToMemoryViaHLIncrement() {
        StoreAccumulatorInAddress(cpu.GetHL());
        cpu.SetHL(cpu.GetHL() + 1);
        return 8;
    }

    /**
     * Stores the accumulator value in memory at address specified by HL.
     * HL is then decremented
     * 
     * @return 8 T-cycles
     */
    public static int AccumulatorToMemoryViaHLDecrement() {
        StoreAccumulatorInAddress(cpu.GetHL());
        cpu.SetHL(cpu.GetHL() - 1);
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
        AccumulatorToMemoryMasked(cpu.GetC());
        return 8;
    }

    /**
     * Stores an immmediate in memory at the address specified by HL
     * 
     * @param immediate the immediate value
     * @return 12 T-cycles
     */
    public static int ImmediateToMemoryViaHL(int immediate) {
        memory.Write(cpu.GetHL(), immediate);
        return 12;
    }

    /**
     * Stores an accumulator in memory at the address specified by HL
     * 
     * @return 8 T-cycles
     */
    public static int RegisterToMemoryViaHL(Register register) {
        memory.Write(cpu.GetHL(), cpu.GetRegister(register));
        return 8;
    }

    private static void LoadAccumulatorFromMemoryAddress(int address) {
        cpu.SetAccumulator(memory.Read(address));
    }

    public static int LoadAccumulatorFromMemoryViaRegisterPair(Register registerPair) {
        LoadAccumulatorFromMemoryAddress(cpu.GetRegisterPair(registerPair));
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
        cpu.SetHL(cpu.GetHL() + 1);
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
        cpu.SetHL(cpu.GetHL() - 1);
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
    public static int LoadAccumulatorFromMemoryViaMaskedImmediate(int mask) {
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
        LoadAccumulatorFromMemoryViaMaskedImmediate(cpu.GetC());
        return 8;
    }

    @Deprecated
    public static int LoadACcumulatorFromMemoryViaMaskedImmediate(int mask) {
        return LoadAccumulatorFromMemoryViaMaskedImmediate(mask);
    }

    /**
     * Loads a register from memory at the address specified by HL
     * 
     * @param register the destination register
     * @return 8 T-cycles
     */
    public static int LoadRegisterFromMemoryViaHL(Register register) {
        cpu.SetRegister(register, memory.Read(cpu.GetHL()));
        return 8;
    }

    /**
     * Sets the Stack Pointer (SP) to the value of the HL register pair
     * 
     * @return 8 T-cycles
     */
    public static int SetSPToHL() {
        cpu.SetSP(cpu.GetHL());
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
        int sp = cpu.GetSP();
        int address1 = cpu.GetRegisterPair(registerPair);
        int address2 = (address1 + 1) & 0xFFFF;
        memory.Write(address1, sp & 0xFF);
        memory.Write(address2, (sp >> 8) & 0xFF);
        return 20;
    }

    /**
     * Stores the Stack Pointer (SP) in memory at the specified address.
     *
     * @param address the destination address
     * @return 20 T-cycles
     */
    public static int StoreSPInImmediateAddress(int address) {
        int sp = cpu.GetSP();
        memory.Write(address, sp & 0xFF);
        memory.Write((address + 1) & 0xFFFF, (sp >> 8) & 0xFF);
        return 20;
    }

    /**
     * Loads HL with the result of SP + a signed immediate value
     * 
     * @param immediate the signed 8-bit immediate
     * @return 12 T-cycles
     */
    public static int LoadToHLStackPointerPlusImmediate(int immediate) {
        int sp = cpu.GetSP();
        int signedOffset = (byte) immediate;
        int result = (sp + signedOffset) & 0xFFFF;
        int xor = sp ^ signedOffset ^ result;
        cpu.SetHL(result);
        cpu.SetFlag(Flag.Z, false);
        cpu.SetFlag(Flag.N, false);
        cpu.SetFlag(Flag.H, (xor & 0x10) != 0);
        cpu.SetFlag(Flag.C, (xor & 0x100) != 0);
        return 12;
    }

    /**
     * Pops a 16-bit value from the stack into a register pair
     * 
     * @param registerPair the destination register pair
     * @return 12 T-cycles
     */
    public static int StackPopToRegisterPair(Register registerPair) {
        int popped = memory.StackPopShort();
        cpu.SetRegisterPair(registerPair, popped);
        return 12;
    }

    /**
     * Pushes a 16-bit value from a register pair onto the stack
     * 
     * @param registerPair the source register pair
     * @return 16 T-cycles
     */
    public static int StackPushFromRegisterPair(Register registerPair) {
        memory.StackPushShort(cpu.GetRegisterPair(registerPair));
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
                cpu.SetFlag(Flag.Z, (value & mask) == 0);
                cpu.SetFlag(Flag.N, false);
                cpu.SetFlag(Flag.H, true);
                return;
            case RES:
                result = value & ~mask;
                break;
            case SET:
                result = value | mask;
                break;
        }

        if (register == Register.HL_ADDR) {
            memory.Write(cpu.GetHL(), result);
        } else {
            cpu.SetRegister(register, result);
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
        BitOpEngine(opType, bit, cpu.GetRegister(register), register);
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
        BitOpEngine(opType, bit, memory.Read(cpu.GetHL()), Register.HL_ADDR);
        return opType == BitOpType.BIT ? 12 : 16;
    }

    private static void RotateEngine(RotateType type, int value, Register register, boolean isCBPrefix) {
        int result;
        boolean oldCarry = cpu.GetFlag(Flag.C);
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
                return;
        }

        result &= 0xFF;

        if (register == Register.HL_ADDR) {
            memory.Write(cpu.GetHL(), result);
        } else {
            cpu.SetRegister(register, result);
        }

        cpu.SetFlag(Flag.Z, isCBPrefix && (result == 0));
        cpu.SetFlag(Flag.N, false);
        cpu.SetFlag(Flag.H, false);
        cpu.SetFlag(Flag.C, newCarry);
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
        int value = (register == Register.HL_ADDR) ? memory.Read(cpu.GetHL()) : cpu.GetRegister(register);

        RotateEngine(type, value, register, isCBPrefix);

        if (!isCBPrefix)
            return 4;
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
                return;
        }
        result &= 0xFF;

        if (register == Register.HL_ADDR) {
            memory.Write(cpu.GetHL(), result);
        } else {
            cpu.SetRegister(register, result);
        }

        cpu.SetFlag(Flag.Z, result == 0);
        cpu.SetFlag(Flag.N, false);
        cpu.SetFlag(Flag.H, false);
        cpu.SetFlag(Flag.C, newCarry);
    }

    /**
     * Performs a shift operation on a register or memory at (HL).
     * 
     * @param type     the type of shift operation
     * @param register the target register, or HL_ADDR for memory
     * @return 8 T-cycles for registers, 16 for (HL)
     */
    public static int Shift(ShiftType type, Register register) {
        int value = (register == Register.HL_ADDR) ? memory.Read(cpu.GetHL()) : cpu.GetRegister(register);
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
        int value = (register == Register.HL_ADDR) ? memory.Read(cpu.GetHL()) : cpu.GetRegister(register);
        int result = ((value & 0x0F) << 4) | ((value & 0xF0) >> 4);

        if (register == Register.HL_ADDR) {
            memory.Write(cpu.GetHL(), result);
        } else {
            cpu.SetRegister(register, result);
        }

        cpu.SetFlag(Flag.Z, result == 0);
        cpu.SetFlag(Flag.N, false);
        cpu.SetFlag(Flag.H, false);
        cpu.SetFlag(Flag.C, false);

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
            return isRelative ? 8 : 12;
        }

        if (isHL) {
            cpu.SetPC(cpu.GetHL());
            return 4;
        } else if (isRelative) {
            cpu.SetPC(cpu.GetPC() + (byte) operand);
            return 12;
        } else {
            cpu.SetPC(operand);
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
        memory.StackPushShort(cpu.GetPC());
        cpu.SetPC(address);
        return 24;
    }

    /**
     * Returns from a subroutine. Can be conditional.
     * 
     * @param conditionMet true if the return condition is met
     * @param isInterrupt  true if this is a RETI instruction
     * @return T-cycles (8 if not taken, 16/20 if taken)
     */
    public static int Return(boolean conditionMet, boolean isInterrupt, boolean isConditional) {
        if (!conditionMet) {
            return 8;
        }
        cpu.SetPC(memory.StackPopShort());
        if (isInterrupt) {
            cpu.EnableInterruptsImmediately();
        }
        return isConditional ? 20 : 16;
    }
}
