package com.blackaby.Backend.Emulation.Memory;

/**
 * This class represents the memory of the GameBoy system.
 * It has methods for reading and writing to memory.
 * It also contains constants for the memory addresses.
 * It also contains a methods for reading from and writing to the stack with a
 * specified offset
 */
public class DuckMemory {
    // Memory Constants
    public static final int MEMORY_SIZE = 0x10000;
    public static final int ROM_BANK_0_START = 0x0000;
    public static final int ROM_BANK_0_END = 0x3FFF;
    public static final int ROM_BANK_N_START = 0x4000;
    public static final int ROM_BANK_N_END = 0x7FFF;
    public static final int VRAM_START = 0x8000;
    public static final int VRAM_END = 0x9FFF;
    public static final int EXTERNAL_RAM_START = 0xA000;
    public static final int EXTERNAL_RAM_END = 0xBFFF;
    public static final int WORK_RAM_START = 0xC000;
    public static final int WORK_RAM_END = 0xDFFF;
    public static final int ECHO_RAM_START = 0xE000;
    public static final int ECHO_RAM_END = 0xFDFF;
    public static final int OAM_START = 0xFE00;
    public static final int OAM_END = 0xFE9F;
    public static final int NOT_USABLE_START = 0xFEA0;
    public static final int NOT_USABLE_END = 0xFEFF;
    public static final int IO_REGISTERS_START = 0xFF00;
    public static final int IO_REGISTERS_END = 0xFF7F;
    public static final int HRAM_START = 0xFF80;
    public static final int HRAM_END = 0xFFFE;
    public static final int INTERRUPT_ENABLE = 0xFFFF;

    // Memory Array
    private byte memory[] = new byte[MEMORY_SIZE];

    /**
     * Creates a new DuckMemory object with all memory values set to 0
     */
    public DuckMemory() {
        for (int i = 0; i < MEMORY_SIZE; i++) {
            memory[i] = 0;
        }
    }

    /**
     * Reads a byte from the specified address
     * 
     * @param address The address to read from
     * @return The byte at the specified address
     */
    public byte read(int address) {
        address = address & 0xFFFFFFFF;
        validate(address);
        return memory[address];
    }

    /**
     * Writes a byte to the specified address
     * 
     * @param address The address to write to
     * @param value   The value to write
     */
    public void write(int address, byte value) {

        validate(address);
        if (readOnlyCheck(address)) {
            return;
        }
        memory[address] = value;
    }

    /**
     * Writes a byte to the stack with the specified offset
     * 
     * @param address The offset from the end of the stack to write to
     * @param value   The value to write
     */
    public void stackWrite(int offset, byte value) {
        write(HRAM_END + offset, value);
    }

    /**
     * Reads a byte from the stack with the specified offset
     * 
     * @param address The offset from the end of the stack to read from
     * @return The byte at the specified offset
     */
    public byte stackRead(int offset) {
        return read(HRAM_END + offset);
    }

    private void validate(int address) {
        if (address < 0 || address >= MEMORY_SIZE) {
            throw new IllegalArgumentException("Invalid memory address: " + address);
        }
    }

    private boolean readOnlyCheck(int address) {
        return (address >= ROM_BANK_0_START && address <= ROM_BANK_N_END) ||
                (address >= NOT_USABLE_START && address <= NOT_USABLE_END);
    }
}