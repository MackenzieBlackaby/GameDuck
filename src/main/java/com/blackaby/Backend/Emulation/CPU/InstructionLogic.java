package com.blackaby.Backend.Emulation.CPU;

import java.rmi.registry.Registry;

import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;
import com.blackaby.Backend.Emulation.Graphics.DuckPPU;
import com.blackaby.Backend.Emulation.Memory.DuckAddresses;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * This class handles the logic for different CPU instructions
 * Each instruction will return the T-cycle count for that specific instruction
 */
public class InstructionLogic {
    private static DuckCPU cpu;
    private static DuckMemory memory;
    private static DuckPPU ppu;

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
            // TODO: Halt bug
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
        int a = cpu.regGet(Register.A);
        boolean n = cpu.getFlag(Flag.N);
        boolean h = cpu.getFlag(Flag.H);
        boolean c = cpu.getFlag(Flag.C);
        int correction = 0;
        if (n) {
            // Adjusting for subtraction
            if (h)
                correction |= 0x06;
            if (c)
                correction |= 0x60;
            a = (a - correction) & 0xFF;
        } else {
            // Adjusting for addition
            if (h || (a & 0x0F) > 0x09) {
                correction |= 0x06;
            }
            if (c || a > 0x99) {
                correction |= 0x60;
                c = true; // C is set if we overflow decimal 99
            }
            a = (a + correction) & 0xFF;
        }
        cpu.regSet(Register.A, a);

        cpu.setFlag(Flag.Z, a == 0);
        cpu.setFlag(Flag.H, false);
        cpu.setFlag(Flag.C, c);
        return 4;
    }

    /**
     * Inverts the Accumulator and adjusts flags
     * 
     * @return 4 T-cycles
     */
    public static int ComplementAccumulator() {
        int a = cpu.regGet(Register.A);
        a = (~a) & 0xFF;
        cpu.regSet(Register.A, a);
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
        int a = cpu.regGet(Register.A);
        int result = CalculateBitwiseOp(a, b, bitwiseType);
        cpu.regSet(Register.A, result);
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
        BitwiseEngine(bitwiseType, 0);
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

    public static int ArithmeticEngine(int a, int b, bool usingCarry, ArithmeticType type) {
        int result = 0;
        int carry = (usingCarry && cpu.getFlag(Flag.C) ? 1 : 0);
        if (type == ArithmeticType.ADD) result 
    }

}