package com.blackaby.Backend.Emulation.Memory;

import com.blackaby.Backend.Emulation.Misc.ROM;
import com.blackaby.Backend.Emulation.Peripherals.DuckTimer;

/**
 * Represents the Game Boy's memory system.
 * <p>
 * Provides read/write access to all memory regions, including handling for:
 * - Echo RAM
 * - DMA transfers
 * - Stack operations
 * - Special register behaviour (e.g., DIV reset, DMA trigger)
 * </p>
 * Supports loading a ROM and provides access to memory-mapped IO.
 */

public class DuckMemory {

    /**
     * Represents the Memory Bank Controller (MBC) type in use by the cartridge.
     */
    public enum MBCType {
        ROM_ONLY, MBC1, MBC2, MBC3, MBC5, UNKNOWN
    }

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
    public static final int INTERRUPT_FLAG = 0xFF0F;
    public static final int IO_REGISTERS_END = 0xFF7F;
    public static final int HRAM_START = 0xFF80;
    public static final int HRAM_END = 0xFFFE;
    public static final int STACK_POINTER_START = HRAM_END;
    public static final int SERIAL_DATA = 0xFF01;
    public static final int SERIAL_CONTROL = 0xFF02;
    public static final int LCDC = 0xFF40;
    public static final int LY = 0xFF44;
    public static final int SCY = 0xFF42;
    public static final int SCX = 0xFF43;
    public static final int BGP = 0xFF47;
    public static final int DIV = 0xFF04;
    public static final int TIMA = 0xFF05;
    public static final int TMA = 0xFF06;
    public static final int TAC = 0xFF07;
    public static final int JOYPAD = 0xFF00;
    public static final int STAT = 0xFF41;
    public static final int DMA = 0xFF46;
    public static final int IE = 0xFFFF;

    private int ram[];
    private int rom[];
    private int romBank = 1;
    private int ramBank = 0;
    private boolean ramEnabled = false;
    private boolean bankingMode = false;
    private MBCType mbcType;
    private int totalRomBanks;
    private int totalRamBanks;
    private DuckTimer timerSet;
    private boolean dmaActive = false;
    private int dmaCounter = 0;
    private int dmaSource = 0;

    /**
     * Constructs a new memory instance and clears RAM.
     */
    public DuckMemory() {
        this.ram = new int[MEMORY_SIZE];
    }

    /**
     * Sets the attached timer component used for timing registers.
     *
     * @param timerSet The timer to bind.
     */
    public void setTimerSet(DuckTimer timerSet) {
        this.timerSet = timerSet;
    }

    /**
     * Clears the rom from address range 0x0000–0x3FFF.
     */
    public void unmapRom() {
        for (int i = ROM_BANK_0_START; i <= ROM_BANK_0_END; i++) {
            ram[i] = 0;
        }
    }

    /**
     * Loads a ROM into memory, copying ROM contents into address space.
     *
     * @param rom The ROM object containing cartridge data.
     */
    public void loadROM(ROM rom) {
        this.rom = rom.getData();

        // // Read Cartridge Type from ROM Header
        // int cartridgeType = this.rom[0x0147] & 0xFF;

        // switch (cartridgeType) {
        // case 0x00:
        // mbcType = MBCType.ROM_ONLY;
        // break;
        // case 0x01:
        // case 0x02:
        // case 0x03:
        // mbcType = MBCType.MBC1;
        // break;
        // case 0x05:
        // case 0x06:
        // mbcType = MBCType.MBC2;
        // break;
        // case 0x0F:
        // case 0x10:
        // case 0x11:
        // case 0x12:
        // case 0x13:
        // mbcType = MBCType.MBC3;
        // break;
        // case 0x19:
        // case 0x1A:
        // case 0x1B:
        // case 0x1C:
        // case 0x1D:
        // case 0x1E:
        // mbcType = MBCType.MBC5;
        // break;
        // default:
        // mbcType = MBCType.UNKNOWN;
        // break;
        // }

        // // Read ROM Size
        // int romSizeCode = rom.getData()[0x0148] & 0xFF;
        // totalRomBanks = 2 << romSizeCode; // ROM banks = 2^(n+1)

        // // Read RAM Size
        // int ramSizeCode = rom.getData()[0x0149] & 0xFF;
        // switch (ramSizeCode) {
        // case 0x00:
        // totalRamBanks = 0;
        // break;
        // case 0x01:
        // totalRamBanks = 1;
        // break; // 2 KB (MBC2 internal RAM)
        // case 0x02:
        // totalRamBanks = 1;
        // break; // 8 KB
        // case 0x03:
        // totalRamBanks = 4;
        // break; // 32 KB (4 banks)
        // case 0x04:
        // totalRamBanks = 16;
        // break; // 128 KB (16 banks)
        // case 0x05:
        // totalRamBanks = 8;
        // break; // 64 KB (8 banks)
        // default:
        // totalRamBanks = 0;
        // break;
        // }
        ram = new int[MEMORY_SIZE];
        System.arraycopy(this.rom, 0, ram, 0, this.rom.length);

        // DebugLogger.logn("MBC Type: " + mbcType);
        // DebugLogger.logn("Total ROM Banks: " + totalRomBanks);
        // DebugLogger.logn("Total RAM Banks: " + totalRamBanks);
    }

    /**
     * Reads a byte from memory, with special handling for echo RAM and special
     * registers.
     *
     * @param address The memory address to read from.
     * @return The 8-bit value at that address.
     */
    public int read(int address) {
        if (address == DuckMemory.DIV) {
            return timerSet.getInternalCounter() >> 8;
        }
        if (address >= ECHO_RAM_START && address <= ECHO_RAM_END) {
            return ram[address - ECHO_RAM_START + WORK_RAM_START];
        }
        if (address >= NOT_USABLE_START && address <= NOT_USABLE_END) {
            return 0xFF;
        }
        // if (address == LY) {
        //     return 0x90;
        // }
        return 0xFF & ram[address];
    }

    /**
     * Writes a byte to memory, with handling for echo RAM, DIV resets and DMA
     * triggers.
     *
     * @param address The memory address to write to.
     * @param value   The 8-bit value to write.
     */
    public void write(int address, int value) {
        if (address >= NOT_USABLE_START && address <= NOT_USABLE_END) {
            return;
        }
        if (address >= ECHO_RAM_START && address <= ECHO_RAM_END) {
            ram[address - ECHO_RAM_START + WORK_RAM_START] = value;
            return;
        }
        if (address == DIV) {
            timerSet.resetDIV();
            ram[DIV] = 0;
            return;
        }
        ram[address] = value & 0xFF;
        if (address == DMA) {
            dmaSource = value << 8;
            dmaCounter = 0;
            dmaActive = true;
        }
    }

    /**
     * Emulates a single DMA transfer cycle, copying one byte to OAM.
     */
    public void tickDMA() {
        if (!dmaActive)
            return;
        ram[OAM_START + dmaCounter] = read(dmaSource + dmaCounter);
        dmaCounter++;
        if (dmaCounter == 0xA0) {
            dmaActive = false;
            dmaCounter = 0;
        }
    }

    /**
     * Pushes a byte onto the stack at the given address.
     *
     * @param address The stack address.
     * @param value   The value to push.
     */
    public void stackPush(int address, int value) {
        write(address, value);
    }

    /**
     * Pops a byte from the stack at the given address.
     *
     * @param address The address to pop from.
     * @return The 8-bit value.
     */
    public int stackPop(int address) {
        return read(address);
    }

    /**
     * Reads a sequence of bytes from memory.
     *
     * @param start The start address.
     * @param count The number of bytes to read.
     * @return An array of 8-bit values.
     */
    public int[] readBytes(int start, int count) {
        int[] bytes = new int[count];
        for (int i = 0; i < count; i++) {
            bytes[i] = 0xFF & read(start + i);
        }
        return bytes;
    }

    /**
     * Returns the value of the Interrupt Enable register.
     *
     * @return The IE register value.
     */
    public int getIE() {
        return read(IE);
    }

    /**
     * Returns the value of the Interrupt Flag register.
     *
     * @return The IF register value.
     */
    public int getIF() {
        return read(INTERRUPT_FLAG);
    }
}
