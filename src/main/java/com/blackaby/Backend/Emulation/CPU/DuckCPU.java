package com.blackaby.Backend.Emulation.CPU;

import com.blackaby.Backend.Emulation.DuckEmulation;
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

        /**
         * This constructor creates a new register with the given ID
         * 
         * @param id The ID of the register
         */
        Register(int id) {
            this.id = id;
        }

        /**
         * Returns true if the register is an 8-bit register.
         * The ID is masked to ensure it is within the valid range (0-10).
         * 
         * @return True if the register is an 8-bit register, false otherwise.
         */
        public boolean is8Bit() {
            if (id < 0 || id > 10)
                return false;
            return true;
        }

        /**
         * Returns the 8-bit register associated with a given ID.
         * The ID is masked to ensure it is within the valid range (0-10).
         * 
         * @param id The ID of the register.
         * @return The corresponding 8-bit register.
         */
        public static Register get8Bit(int id) {
            if (id < 0 || id > 10) {
                throw new IllegalArgumentException("Invalid 8-bit register ID: " + id);
            }
            return Register.values()[id];
        }

        /**
         * Returns the 16-bit register associated with a given ID.
         * The ID is masked to ensure it is within the valid range (11-16).
         *
         * @param id The ID of the register.
         * @return The corresponding 16-bit register.
         */
        public static Register get16Bit(int id) {
            if (id < 11 || id > 16) {
                throw new IllegalArgumentException("Invalid 16-bit register ID: " + id);
            }
            return Register.values()[id];
        }

        /**
         * Returns the 16-bit register associated with a 2-bit ID.
         * The ID is masked to ensure it is within the valid range (0-3).
         *
         * @param bitID The 2-bit ID of the register.
         * @return The corresponding 16-bit register.
         */
        public static Register getRegFrom2Bit(int bitID, boolean isAFContext) {
            bitID &= 0b11;
            switch (bitID) {
                case 0b00:
                    return Register.BC;
                case 0b01:
                    return Register.DE;
                case 0b10:
                    return Register.HL;
                case 0b11:
                    return isAFContext ? Register.AF : Register.SP;
                default:
                    throw new IllegalArgumentException("Invalid 16-bit register ID: " + bitID);
            }
        }

        /**
         * Returns the 8-bit register associated with a 3-bit ID.
         * The ID is masked to ensure it is within the valid range (0-7).
         *
         * @param bitID The 3-bit ID of the register.
         * @return The corresponding 8-bit register.
         */
        public static Register getRegFrom3Bit(int bitID) {
            bitID &= 0b111;
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

        /**
         * Returns the internal ID associated with this register.
         *
         * @return The numeric ID of the register.
         */
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
        Z(7),
        N(6),
        H(5),
        C(4);

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

        /**
         * This method returns the flag associated with a given bit ID.
         * The ID is masked to ensure it is within the valid range (0-3).
         * 
         * @param bitID The bit ID of the flag
         * @return The corresponding flag
         */
        public static Flag getFlagFrom2Bit(byte bitID) {
            bitID &= 0b11;
            switch (bitID) {
                case 0b00:
                    return Z;
                case 0b01:
                    return N;
                case 0b10:
                    return H;
                case 0b11:
                    return C;
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
        VBLANK(0x01, 0x40), // Interrupt 0
        LCD_STAT(0x02, 0x48), // Interrupt 1
        TIMER(0x04, 0x50), // Interrupt 2
        SERIAL(0x08, 0x58), // Interrupt 3
        JOYPAD(0x10, 0x60); // Interrupt 4

        private final int mask;
        private final short address;

        Interrupt(int mask, int address) {
            this.mask = mask;
            this.address = (short) address;
        }

        /**
         * This method returns the mask value of the interrupt
         * 
         * @return The mask value of the interrupt
         */
        public int getMask() {
            return mask;
        }

        /**
         * This method returns the address of the interrupt
         * 
         * @return The address of the interrupt
         */
        public short getAddress() {
            return address;
        }

        /**
         * This method returns the interrupt associated with a given index.
         * The index is masked to ensure it is within the valid range (0-4).
         * 
         * @param index The index of the interrupt
         * @return The corresponding interrupt
         */
        public static Interrupt getInterrupt(int index) {
            switch (index) {
                case 0:
                    return VBLANK;
                case 1:
                    return LCD_STAT;
                case 2:
                    return TIMER;
                case 3:
                    return SERIAL;
                case 4:
                    return JOYPAD;
                default:
                    throw new IllegalArgumentException("Invalid interrupt index: " + index);
            }
        }
    }

    // Registers
    private int programCounter;
    private int stackPointer;
    private int flags;
    private int accumulator;
    private int registerB;
    private int registerC;
    private int registerD;
    private int registerE;
    private int registerH;
    private int registerL;
    private int instructionRegister;
    private boolean interruptMasterEnable;
    private int interruptMasterEnableCounter = 0;

    private boolean haltBug = false;

    private boolean isHalted = false;
    private boolean isStopped = false;

    // Memory and emulation references
    public final DuckMemory memory;
    public final DuckEmulation emulation;

    /**
     * This constructor creates a new CPU with the given memory and emulation
     * references
     * 
     * @param memory    The memory reference
     * @param emulation The emulation reference
     */
    public DuckCPU(DuckMemory memory, DuckEmulation emulation) {
        this.memory = memory;
        this.emulation = emulation;
    }

    /**
     * This method sets halted
     * 
     * @param halted The value to set halted to
     */
    public void setHalted(boolean halted) {
        isHalted = halted;
    }

    /**
     * This method sets stopped
     * 
     * @param stopped The value to set stopped to
     */
    public void setStopped(boolean stopped) {
        isStopped = stopped;
    }

    /**
     * This method returns the value of stopped
     * 
     * @return The value of stopped
     */
    public boolean isStopped() {
        return isStopped;
    }

    /**
     * This method returns the value of halted
     * 
     * @return The value of halted
     */
    public boolean isHalted() {
        return isHalted;
    }

    /**
     * This method executes the next instruction in the queue
     */
    public int execute(Instruction instruction) {
        handleInterrupts();
        if (haltBug)
            haltBug = false;
        if (instruction != null && !isHalted) {
            // DebugLogger.logFile(this.toString(), DebugLogger.LOG_FILE);
            instruction.run();
        }
        if (interruptMasterEnableCounter >= 2) {
            interruptMasterEnable = true;
            interruptMasterEnableCounter = 0;
        } else if (interruptMasterEnableCounter == 1) {
            interruptMasterEnableCounter++;
        }
        // memory.printStack(stackPointer);
        return instruction.getCycleCount();
    }

    /**
     * This method returns the string representation of the CPU state
     * 
     * @return The string representation of the CPU state
     */
    public String toString() {
        // Format:
        // A:00 F:11 B:22 C:33 D:44 E:55 H:66 L:77 PC:8888 SP:9999 PCMEM:AA,BB,CC,DD
        StringBuilder sb = new StringBuilder();
        sb.append("A:").append(String.format("%02X", accumulator)).append(" ");
        sb.append("F:").append(String.format("%02X", flags)).append(" ");
        sb.append("B:").append(String.format("%02X", registerB)).append(" ");
        sb.append("C:").append(String.format("%02X", registerC)).append(" ");
        sb.append("D:").append(String.format("%02X", registerD)).append(" ");
        sb.append("E:").append(String.format("%02X", registerE)).append(" ");
        sb.append("H:").append(String.format("%02X", registerH)).append(" ");
        sb.append("L:").append(String.format("%02X", registerL)).append(" ");
        sb.append("PC:").append(String.format("%04X", programCounter)).append(" ");
        sb.append("SP:").append(String.format("%04X", stackPointer)).append(" ");
        sb.append("PCMEM:").append(String.format("%02X", memory.read(programCounter)));
        sb.append(",").append(String.format("%02X", memory.read(programCounter + 1)));
        sb.append(",").append(String.format("%02X", memory.read(programCounter + 2)));
        sb.append(",").append(String.format("%02X", memory.read(programCounter + 3)));
        return sb.toString();
    }

    /**
     * This method sets the value of the given register
     * 
     * @param reg   The register to set
     * @param value The value to set the register to
     * @throws IllegalArgumentException If the register is unknown
     */
    public void regSet(Register reg, int value) {
        value &= 0xFF;
        switch (reg) {
            case A:
                accumulator = value;
                break;
            case F:
                flags = value;
                break;
            case B:
                registerB = value;
                break;
            case C:
                registerC = value;
                break;
            case D:
                registerD = value;
                break;
            case E:
                registerE = value;
                break;
            case H:
                registerH = value;
                break;
            case L:
                registerL = value;
                break;
            case IR:
                instructionRegister = value;
                break;
            case HL_ADDR:
                memory.write(getHLValue(), value);
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
    public int regGet(Register reg) {
        switch (reg) {
            case A:
                return accumulator;
            case F:
                return flags;
            case B:
                return registerB;
            case C:
                return registerC;
            case D:
                return registerD;
            case E:
                return registerE;
            case H:
                return registerH;
            case L:
                return registerL;
            case IR:
                return instructionRegister;
            case HL_ADDR:
                return 0xFF & memory.read(getHLValue());
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
    public int regGet16(Register reg) {
        switch (reg) {
            case PC:
                return programCounter;
            case SP:
                return stackPointer;
            case BC:
                return ((registerB & 0xFF) << 8) | (registerC & 0xFF);
            case DE:
                return ((registerD & 0xFF) << 8) | (registerE & 0xFF);
            case HL:
                return ((registerH & 0xFF) << 8) | (registerL & 0xFF);
            case AF:
                return ((accumulator & 0xFF) << 8) | (flags & 0xFF);
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
    public void regSet16(Register reg, int value) {
        value &= 0xFFFF;
        switch (reg) {
            case PC:
                programCounter = value;
                break;
            case SP:
                stackPointer = value;
                break;
            case BC:
                registerB = (value >> 8) & 0xFF;
                registerC = value & 0xFF;
                break;
            case DE:
                registerD = (value >> 8) & 0xFF;
                registerE = value & 0xFF;
                break;
            case HL:
                registerH = (value >> 8) & 0xFF;
                registerL = value & 0xFF;
                break;
            case AF:
                accumulator = (value >> 8) & 0xFF;
                flags = value & 0xFF;
                break;
            default:
                throw new IllegalArgumentException("Invalid 16-bit register: " + reg);
        }
    }

    /**
     * This method gets the value of the HL register as a short
     * Provides Quick access to HL register without a switch case
     * 
     * @return The value of the HL register as a short
     */
    public int getHLValue() {
        int h = 0xFF & registerH;
        int l = 0xFF & registerL;
        int hl = (h << 8) | l;
        return 0xFFFF & hl;
    }

    /**
     * This method gets the value of the BC register as a short
     * Provides Quick access to BC register without a switch case
     * 
     * @return The value of the BC register as a short
     */
    public int getBCValue() {
        int b = 0xFF & registerB;
        int c = 0xFF & registerC;
        int bc = 0xFFFF & ((b << 8) | c);
        return bc;
    }

    /**
     * Sets the value of the BC register pair.
     *
     * @param value The 16-bit value to assign to BC.
     */
    public void setBC(int value) {
        registerB = (value >> 8) & 0xFF;
        registerC = 0xFF & value;
    }

    /**
     * This method gets the value of the DE register as a short
     * Provides Quick access to DE register without a switch case
     * 
     * @return The value of the DE register as a short
     */
    public int getDEValue() {
        int d = registerD & 0xFF;
        int e = registerE & 0xFF;
        int de = 0xFFFF & ((d << 8) | e);
        return de;
    }

    /**
     * This method gets the value of the program counter register as a short
     * Provides Quick access to program counter register without a switch case
     * 
     * @return The value of the program counter register as a short
     */
    public int getSP() {
        return 0xFFFF & stackPointer;
    }

    /**
     * This method gets the value of the accumulator register as a byte
     * Provides Quick access to accumulator register without a switch case
     * 
     * @return The value of the accumulator register as a byte
     */
    public int getAccumulator() {
        return 0xFF & accumulator;
    }

    /**
     * This method sets the value of the accumulator register
     * 
     * @param value The value to set the accumulator register to
     */
    public void setAccumulator(int value) {
        accumulator = 0xFF & value;
    }

    /**
     * This method gets the value of the flags register as a byte
     * Provides Quick access to flags register without a switch case
     * 
     * @return The value of the flags register as a byte
     */
    public void setHL(int value) {
        registerH = (value >> 8) & 0xFF;
        registerL = 0xFF & value;
    }

    /**
     * This method sets the value of the AF register
     * 
     * @param value The value to set the AF register to
     */
    public void setAF(int value) {
        accumulator = (value >> 8) & 0xFF;
        flags = 0xFF & value;
    }

    /**
     * This method sets the value of the DE register
     * 
     * @param value The value to set the DE register to
     */
    public void setDE(int value) {
        registerD = (value >> 8) & 0xFF;
        registerE = 0xFF & value;
    }

    /**
     * This method gets the value of the program counter register as a short
     * Provides Quick access to program counter register without a switch case
     * 
     * @return The value of the program counter register as a short
     */
    public int getPC() {
        return 0xFFFF & programCounter;
    }

    /**
     * This method sets the value of the program counter register
     * 
     * @param value The value to set the program counter register to
     */
    public void setPC(int value) {
        programCounter = 0xFFFF & value;
    }

    /**
     * This method gets the value of the flags register as a byte
     * Provides Quick access to flags register without a switch case
     * 
     * @return The value of the flags register as a byte
     */
    public int getC() {
        return 0xFF & registerC;
    }

    /**
     * This method sets the value of the SP register
     * 
     * @param value The value to set the SP register to
     */
    public void setSP(int value) {
        stackPointer = 0xFFFF & value;
    }

    /**
     * This method gets the value of the halt bug
     * 
     * @return The value of the halt bug
     */
    public void setHaltBug(boolean value) {
        haltBug = value;
    }

    /**
     * This method gets the value of the halt bug
     * 
     * @return The value of the halt bug
     */
    public boolean getHaltBug() {
        return haltBug;
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

    /**
     * This method requests an interrupt
     * 
     * @param interrupt The interrupt to request
     */
    public void requestInterrupt(Interrupt interrupt) {
        int interruptFlag = memory.read(DuckMemory.INTERRUPT_FLAG);
        memory.write(DuckMemory.INTERRUPT_FLAG, interruptFlag | interrupt.getMask());
    }

    private void handleInterrupts() {
        if (!interruptMasterEnable)
            return;
        int interruptEnable = memory.read(DuckMemory.IE);
        int interruptFlags = memory.read(DuckMemory.INTERRUPT_FLAG);
        int interruptsTriggered = interruptEnable & interruptFlags;

        if (interruptsTriggered == 0)
            return;

        interruptMasterEnable = false;
        for (int i = 0; i < 5; i++) {
            if ((interruptsTriggered & (1 << i)) != 0) {
                handleInterrupt(i);
                break;
            }
        }
    }

    private void handleInterrupt(int interruptBit) {
        int address = Interrupt.getInterrupt(interruptBit).getAddress();
        int interruptFlag = memory.read(DuckMemory.INTERRUPT_FLAG);
        memory.write(DuckMemory.INTERRUPT_FLAG, interruptFlag & ~(1 << interruptBit));
        // Write high byte
        memory.write(stackPointer - 1, (programCounter >> 8) & 0xFF);
        // Write low byte
        memory.write(stackPointer - 2, programCounter & 0xFF);
        stackPointer -= 2;
        programCounter = address;
    }

    /**
     * This method sets the interrupt master enable flag
     * 
     * @param enabled The value to set the interrupt master enable flag to
     */
    public void setInterruptEnable(boolean enabled) {
        if (enabled)
            interruptMasterEnableCounter = 1;
        else {
            interruptMasterEnable = false;
            interruptMasterEnableCounter = 0;
        }
    }

    /**
     * This method gets the value of the interrupt master enable flag
     * 
     * @return The value of the interrupt master enable flag
     */
    public boolean isInterruptMasterEnable() {
        return interruptMasterEnable;
    }

    /**
     * This method sets the interrupt master enable flag directly
     * 
     * @param enabled The value to set the interrupt master enable flag to
     */
    public void setInterruptMasterEnable(boolean enabled) {
        interruptMasterEnable = enabled;
    }
}
