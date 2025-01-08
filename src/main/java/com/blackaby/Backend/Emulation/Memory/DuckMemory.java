package com.blackaby.Backend.Emulation.Memory;

public class DuckMemory {
    private static final int MEMORY_SIZE = 0x10000;
    private static final int ROM_BANK_0_START = 0x0000;
    // private static final int ROM_BANK_0_END = 0x3FFF;
    // private static final int ROM_BANK_N_START = 0x4000;
    private static final int ROM_BANK_N_END = 0x7FFF;
    // private static final int VRAM_START = 0x8000;
    // private static final int VRAM_END = 0x9FFF;
    // private static final int EXTERNAL_RAM_START = 0xA000;
    // private static final int EXTERNAL_RAM_END = 0xBFFF;
    // private static final int WORK_RAM_START = 0xC000;
    // private static final int WORK_RAM_END = 0xDFFF;
    // private static final int ECHO_RAM_START = 0xE000;
    // private static final int ECHO_RAM_END = 0xFDFF;
    // private static final int OAM_START = 0xFE00;
    // private static final int OAM_END = 0xFE9F;
    private static final int NOT_USABLE_START = 0xFEA0;
    private static final int NOT_USABLE_END = 0xFEFF;
    // private static final int IO_REGISTERS_START = 0xFF00;
    // private static final int IO_REGISTERS_END = 0xFF7F;
    // private static final int HRAM_START = 0xFF80;
    // private static final int HRAM_END = 0xFFFE;
    // private static final int INTERRUPT_ENABLE = 0xFFFF;
    private byte memory[] = new byte[MEMORY_SIZE];

    public DuckMemory() {
        for (int i = 0; i < MEMORY_SIZE; i++) {
            memory[i] = 0;
        }
    }

    public byte read(int address) {
        validate(address);
        return memory[address];
    }

    public void write(int address, byte value) {
        validate(address);
        if (readOnlyCheck(address)) {
            return;
        }
        memory[address] = value;
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