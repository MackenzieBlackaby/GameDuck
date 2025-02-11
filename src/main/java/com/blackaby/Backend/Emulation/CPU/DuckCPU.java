package com.blackaby.Backend.Emulation.CPU;

import java.util.LinkedList;
import java.util.Queue;

import com.blackaby.Backend.Emulation.Memory.DuckMemory;

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

    /**
     * This enum represents the registers of the CPU.
     * It has values for all 8-bit and 16-bit registers.
     * It also has values for special registers like the flags and the instruction
     * register.
     */
    public enum Register {

        // 8 bit registers
        B(0), // 000
        C(1), // 001
        D(2), // 010
        E(3), // 011
        H(4), // 100
        L(5), // 101
        HL_ADDR(6), // 110
        A(7), // 111

        // Special registers
        F(8), // Flags
        IR(9), // Instruction Register
        IE(10), // Interrupt Enable

        // 16-bit registers
        BC(11), // 00
        DE(12), // 01
        HL(13), // 10
        AF(14), // 11
        SP(15),
        PC(16);

        private final int id;

        Register(int id) {
            this.id = id;
        }

        public boolean is8Bit() {
            if (id < 0 || id > 10)
                return false;
            return true;
        }

        public static Register get8Bit(short id) {
            if (id < 0 || id > 10) { // Updated range check for 8-bit registers
                throw new IllegalArgumentException("Invalid 8-bit register ID: " + id);
            }
            return Register.values()[id];
        }

        public static Register get16Bit(short id) {
            if (id < 11 || id > 16) { // Updated range check for 16-bit registers
                throw new IllegalArgumentException("Invalid 16-bit register ID: " + id);
            }
            return Register.values()[id];
        }

        public static Register getRegFrom2Bit(byte bitID) {
            bitID &= 0b11; // Mask to 2 bits
            switch (bitID) {
                case 0b00:
                    return Register.BC;
                case 0b01:
                    return Register.DE;
                case 0b10:
                    return Register.HL;
                case 0b11:
                    return Register.AF;
                default:
                    throw new IllegalArgumentException("Invalid 16-bit register ID: " + bitID);
            }
        }

        public static Register getRegFrom3Bit(byte bitID) {
            bitID &= 0b111; // Mask to 3 bits
            switch (bitID) {
                case 0b000:
                    return Register.B;
                case 0b001:
                    return Register.C;
                case 0b010:
                    return Register.D;
                case 0b011:
                    return Register.E;
                case 0b100:
                    return Register.H;
                case 0b101:
                    return Register.L;
                case 0b110:
                    return Register.HL_ADDR;
                case 0b111:
                    return Register.A;
                default:
                    throw new IllegalArgumentException("Invalid 8-bit register ID: " + bitID);
            }
        }

        public byte getId() {
            return (byte) id;
        }
    }

    /**
     * This enum represents the flags of the CPU.
     * It has values for all 4 flags: Zero, Subtract, Half Carry, and Carry.
     * Each flag has a bit position in the flags register.
     * The flags register is an 8-bit register, so each flag has a bit from 4 to 7.
     * The flags register is used to store the status of the CPU.
     */
    public enum Flag {
        ZERO(7), // Z
        SUBTRACT(6), // N
        HALF_CARRY(5), // H
        CARRY(4), // C
        Z(7), // Z
        N(6), // N
        H(5), // H
        C(4); // C

        private final int bit;

        /**
         * This constructor creates a new flag with the given bit position
         * 
         * @param bit The bit position of the flag
         */
        Flag(int bit) {
            this.bit = bit;
        }

        /**
         * This method returns the bit position of the flag
         * 
         * @return The bit position of the flag
         */
        public int getBit() {
            return bit;
        }

        public static Flag getFlagFrom2Bit(byte bitID) {
            bitID &= 0b11; // Mask to 2 bits
            switch (bitID) {
                case 0b00:
                    return ZERO;
                case 0b01:
                    return SUBTRACT;
                case 0b10:
                    return HALF_CARRY;
                case 0b11:
                    return CARRY;
                default:
                    throw new IllegalArgumentException("Invalid flag ID: " + bitID);
            }
        }
    }

    /**
     * This enum represents the interrupts of the CPU.
     * It has values for all 5 interrupts: VBLANK, LCD_STAT, TIMER, SERIAL, and
     * JOYPAD.
     * Each interrupt has a mask value that is used to enable or disable the
     * interrupt.
     */
    public enum Interrupt {
        VBLANK(0x01, 0x0040), LCD_STAT(0x02, 0x0048), TIMER(0x04, 0x0050), SERIAL(0x08, 0x0058), JOYPAD(0x10, 0x0060);

        private final int mask;
        private final short address;

        Interrupt(int mask, int address) {
            this.mask = mask;
            this.address = (short) address;
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

    public final DuckALU alu;
    public final DuckIDU idu;
    public final DuckMemory memory;

    public DuckCPU(DuckMemory memory) {
        instructionQueue = new LinkedList<>();
        stackPointer = (short) DuckMemory.HRAM_END;
        programCounter = 0;
        alu = new DuckALU(this);
        idu = new DuckIDU(this);
        this.memory = memory;
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
            case HL_ADDR:
                memory.write(regGet16(Register.HL), value);
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
            case HL_ADDR:
                return memory.read(regGet16(Register.HL));
            default:
                throw new IllegalArgumentException("Unknown 8-bit register: " + reg);
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
            case AF:
                return (short) ((accumulator << 8) | flags);
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
            case AF:
                accumulator = (byte) (value >> 8);
                flags = (byte) value;
                break;
            default:
                throw new IllegalArgumentException("Invalid 16-bit register: " + reg);
        }
    }

    /**
     * This method gets the value of any register as an integer.
     * This is useful for debugging and logging.
     * 
     * @param reg The register to get
     * @return The value of the register as an integer
     */
    public int regGetInt(Register reg) {
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
            case SP:
                return stackPointer;
            case PC:
                return programCounter;
            case BC:
                return (byteRegs[0] << 8) | byteRegs[1];
            case DE:
                return (byteRegs[2] << 8) | byteRegs[3];
            case HL:
                return (byteRegs[4] << 8) | byteRegs[5];
            case AF:
                return (accumulator << 8) | flags;
            default:
                return 0;
        }
    }

    /**
     * This method activates given flags in the flags register
     * 
     * @param flagsToSet The flags to activate
     */
    public void activateFlags(Flag... flagsToSet) {
        for (Flag flag : flagsToSet) {
            this.flags |= 1 << flag.getBit();
        }
    }

    /**
     * This method sets the value of a flag in the flags register
     * 
     * @param flag  The flag to set
     * @param value The value to set the flag to
     */
    public void setFlag(Flag flag, boolean value) {
        if (value) {
            this.flags |= 1 << flag.getBit();
        } else {
            this.flags &= ~(1 << flag.getBit());
        }
    }

    /**
     * This method gets the value of a flag in the flags register
     * 
     * @param flag The flag to get
     * @return The value of the flag
     */
    public byte getFlag(Flag flag) {
        return (flags & (1 << flag.getBit())) == 0 ? (byte) 0b0 : (byte) 0b1;
    }

    /**
     * This method gets the value of a flag in the flags register as a boolean
     * 
     * @param flag The flag to get
     * @return The value of the flag as a boolean
     */
    public boolean getFlagBoolean(Flag flag) {
        return (flags & (1 << flag.getBit())) != 0;
    }

    /**
     * This method deactivates given flags in the flags register
     * 
     * @param flagsToClear The flags to deactivate
     */
    public void deactivateFlags(Flag... flagsToClear) {
        for (Flag flag : flagsToClear) {
            this.flags &= ~(1 << flag.getBit());
        }
    }

    public void requestInterrupt(Interrupt interrupt) {
        byte interruptQueue = memory.read(DuckMemory.INTERRUPT_ENABLE);
        interruptQueue |= interrupt.mask;
        memory.write(DuckMemory.INTERRUPT_ENABLE, interruptQueue);
    }

    private Interrupt[] extractInterrupts(byte interruptQueue) {
        Interrupt[] interrupts = new Interrupt[5];
        for (int i = 0; i < 5; i++) {
            if ((interruptQueue & (1 << i)) != 0) {
                interrupts[i] = Interrupt.values()[i];
            }
        }
        return interrupts;
    }

    public void handleInterrupts() {
        if (interruptEnable == 0) {
            return;
        }

        byte interruptQueue = memory.read(DuckMemory.INTERRUPT_ENABLE);
        Interrupt interrupts[] = extractInterrupts(interruptQueue);

        for (Interrupt interrupt : interrupts) {
            if (interrupt != null) {
                programCounter = interrupt.address;
                interruptQueue &= ~interrupt.mask;
            }
        }
    }

}
