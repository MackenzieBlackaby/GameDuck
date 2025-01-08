package com.blackaby.Backend.Emulation.CPU;

import java.util.LinkedList;
import java.util.Queue;

import com.blackaby.Backend.Emulation.CPU.Instructions.SpecificInstructions.*;

/**
 * This class represents the CPU of the GameBoy.
 * It has methods for executing instructions and managing registers.
 * It also has methods for reading and writing to memory.
 * It is the main class for the CPU emulation.
 * 
 * The instructions are implemented as methods in this class.
 * The CPU has a reference to the memory and the display.
 */
public class DuckCPU {

    public enum Register {
        A(0), // Accumulator
        F(1), // Flags
        B(2),
        C(3),
        D(4),
        E(5),
        H(6),
        L(7),
        IR(8), // Instruction Register
        IE(9), // Interrupt Enable
        PC(10), // Program Counter
        SP(11), // Stack Pointer

        // Combo registers

        BC(12), // 00
        DE(13), // 01
        HL(14); // 10

        private final int id;

        Register(int id) {
            this.id = id;
        }

        public static Register get8Bit(short id) {
            if (id < 0 || id > 9) { // Updated range check for 8-bit registers
                throw new IllegalArgumentException("Invalid 8-bit register ID: " + id);
            }
            return Register.values()[id];
        }

        public static Register get16Bit(short id) {
            if (id < 10 || id > 14) { // Updated range check for 16-bit registers
                throw new IllegalArgumentException("Invalid 16-bit register ID: " + id);
            }
            return Register.values()[id];
        }

        public static Register getRegFrom2Bit(byte bitID) {
            switch (bitID) {
                case 0b00:
                    return Register.BC;
                case 0b01:
                    return Register.DE;
                case 0b10:
                    return Register.HL;
                case 0b11:
                    return Register.SP;
                default:
                    throw new IllegalArgumentException("Invalid 16-bit register ID: " + bitID);
            }
        }

        public byte getId() {
            return (byte) id;
        }
    }

    // Emulated Parts
    private Queue<Duckstruction> instructionQueue;

    // Registers
    private short programCounter;
    private short stackPointer;
    private byte flags = 0;
    private byte accumulator = 0;
    private byte[] byteRegs = { 0, 0, 0, 0, 0, 0 };
    private byte interruptEnable = 0;
    private byte instructionRegister = 0;

    public DuckCPU() {
        instructionQueue = new LinkedList<>();
        programCounter = 0;
    }

    /**
     * This method queues an instruction
     */
    public void queueInstruction(Duckstruction instruction) {
        instructionQueue.add(instruction);
    }

    /**
     * This method executes the next instruction in the queue
     */
    public void executeNextInstruction() {
        Duckstruction instruction = instructionQueue.poll();
        if (instruction != null) {
            instruction.execute();
        }
    }

    /**
     * This method sets the value of the given register
     * 
     * @param reg   The register to set
     * @param value The value to set the register to
     * @throws IllegalArgumentException If the register is unknown
     */
    public void regSet(Register reg, byte value) throws IllegalArgumentException {
        switch (reg) {
            case A:
                accumulator = value;
                break;
            case F:
                flags = value;
                break;
            case B:
                byteRegs[0] = value;
                break;
            case C:
                byteRegs[1] = value;
                break;
            case D:
                byteRegs[2] = value;
                break;
            case E:
                byteRegs[3] = value;
                break;
            case H:
                byteRegs[4] = value;
                break;
            case L:
                byteRegs[5] = value;
                break;
            case IR:
                instructionRegister = value;
                break;
            case IE:
                interruptEnable = value;
                break;
            default:
                throw new IllegalArgumentException("Unknown 8-bit register: " + reg);
        }
    }

    /**
     * This method gets the value of the given register
     * 
     * @param reg The register to get
     * @return The value of the register
     * @throws IllegalArgumentException If the register is unknown
     */
    public byte regGet(Register reg) throws IllegalArgumentException {
        switch (reg) {
            case A:
                return accumulator;
            case F:
                return flags;
            case B:
                return byteRegs[0];
            case C:
                return byteRegs[1];
            case D:
                return byteRegs[2];
            case E:
                return byteRegs[3];
            case H:
                return byteRegs[4];
            case L:
                return byteRegs[5];
            case IR:
                return instructionRegister;
            case IE:
                return interruptEnable;
            default:
                throw new IllegalArgumentException("Unknown 8-bit register: " + reg);
        }
    }

    /**
     * This method increments the value of the given register
     * 
     * @param reg The register to increment
     */
    public void regIncrement(Register reg) {
        switch (reg) {
            case A:
                accumulator++;
                break;
            case F:
                flags++;
                break;
            case B:
                byteRegs[0]++;
                break;
            case C:
                byteRegs[1]++;
                break;
            case D:
                byteRegs[2]++;
                break;
            case E:
                byteRegs[3]++;
                break;
            case H:
                byteRegs[4]++;
                break;
            case L:
                byteRegs[5]++;
                break;
            case IR:
                instructionRegister++;
                break;
            case IE:
                interruptEnable++;
                break;
            case SP:
                stackPointer++;
                break;
            case PC:
                programCounter++;
                break;
            case BC:
                regSet16(Register.BC, (short) (regGet16(Register.BC) + 1));
                break;
            case DE:
                regSet16(Register.DE, (short) (regGet16(Register.DE) + 1));
                break;
            case HL:
                regSet16(Register.HL, (short) (regGet16(Register.HL) + 1));
                break;
            default:
                throw new IllegalArgumentException("Unknown register: " + reg);
        }
    }

    /**
     * This method gets a specified 16-bit register
     * 
     * @param reg the 16-bit register to get
     * @return the value of the register
     */
    public short regGet16(Register reg) {
        switch (reg) {
            case PC:
                return programCounter;
            case SP:
                return stackPointer;
            case BC:
                return (short) ((byteRegs[Register.B.id] << 8) | byteRegs[Register.C.id]);
            case DE:
                return (short) ((byteRegs[Register.D.id] << 8) | byteRegs[Register.E.id]);
            case HL:
                return (short) ((byteRegs[Register.H.id] << 8) | byteRegs[Register.L.id]);
            default:
                throw new IllegalArgumentException("Invalid 16-bit register: " + reg);
        }
    }

    /**
     * This method sets a specified 16-bit register
     * 
     * @param reg   the 16-bit register to set
     * @param value the value to set the register to
     */
    public void regSet16(Register reg, short value) {
        switch (reg) {
            case PC:
                programCounter = value;
                break;
            case SP:
                stackPointer = value;
                break;
            case BC:
                byteRegs[0] = (byte) (value >> 8);
                byteRegs[1] = (byte) value;
                break;
            case DE:
                byteRegs[2] = (byte) (value >> 8);
                byteRegs[3] = (byte) value;
                break;
            case HL:
                byteRegs[4] = (byte) (value >> 8);
                byteRegs[5] = (byte) value;
                break;
            default:
                throw new IllegalArgumentException("Invalid 16-bit register: " + reg);
        }
    }
}
