package com.blackaby.Backend.GB.Memory;

/**
 * This file contains constant addresses in memory and descriptors of each.
 * This acts as the central "address book" for the emulator.
 */
public class DuckAddresses {

    // --- Memory Regions ---

    /** Size of the full address space (64 KiB). */
    public static final int MEMORY_SIZE = 0x10000;

    /** Start of fixed ROM bank (bank 0). */
    public static final int ROM_BANK_0_START = 0x0000;
    /** End of fixed ROM bank (bank 0). */
    public static final int ROM_BANK_0_END = 0x3FFF;

    /** Start of switchable ROM bank area. */
    public static final int ROM_BANK_N_START = 0x4000;
    /** End of switchable ROM bank area. */
    public static final int ROM_BANK_N_END = 0x7FFF;

    /** Start of video RAM (tile data and tile maps). */
    public static final int VRAM_START = 0x8000;
    /** End of video RAM. */
    public static final int VRAM_END = 0x9FFF;

    /** Start of cartridge external RAM (if present). */
    public static final int EXTERNAL_RAM_START = 0xA000;
    /** End of cartridge external RAM. */
    public static final int EXTERNAL_RAM_END = 0xBFFF;

    /** Start of working RAM (WRAM0). */
    public static final int WORK_RAM_START = 0xC000;
    /** End of working RAM (WRAM0/1). */
    public static final int WORK_RAM_END = 0xDFFF;

    /** Start of echo RAM (mirror of 0xC000-0xDDFF). */
    public static final int ECHO_RAM_START = 0xE000;
    /** End of echo RAM mirror region. */
    public static final int ECHO_RAM_END = 0xFDFF;

    /** Start of sprite attribute table (OAM). */
    public static final int OAM_START = 0xFE00;
    /** End of sprite attribute table (OAM). */
    public static final int OAM_END = 0xFE9F;

    /** Unusable hardware region between OAM and I/O. */
    public static final int NOT_USABLE_START = 0xFEA0;
    /** End of unusable hardware region. */
    public static final int NOT_USABLE_END = 0xFEFF;

    /** Start of hardware I/O registers. */
    public static final int IO_REGISTERS_START = 0xFF00;
    /** End of hardware I/O registers (before HRAM). */
    public static final int IO_REGISTERS_END = 0xFF7F;

    /** Start of high RAM (fast 127-byte internal RAM). */
    public static final int HRAM_START = 0xFF80;
    /** End of high RAM. */
    public static final int HRAM_END = 0xFFFE;

    /** Interrupt enable register. */
    public static final int IE = 0xFFFF;

    // --- I/O Registers ---

    // Joypad
    public static final int JOYPAD = 0xFF00;

    // Serial Data Transfer
    public static final int SERIAL_DATA = 0xFF01;
    public static final int SERIAL_CONTROL = 0xFF02;

    // Timer
    public static final int DIV = 0xFF04; // Divider Register
    public static final int TIMA = 0xFF05; // Timer Counter
    public static final int TMA = 0xFF06; // Timer Modulo
    public static final int TAC = 0xFF07; // Timer Control

    // Interrupt Flag
    public static final int INTERRUPT_FLAG = 0xFF0F;

    // Audio (APU) - Ranges for future implementation
    public static final int AUDIO_START = 0xFF10;
    public static final int AUDIO_END = 0xFF26;
    public static final int WAVE_PATTERN_START = 0xFF30;
    public static final int WAVE_PATTERN_END = 0xFF3F;
    public static final int NR10 = 0xFF10;
    public static final int NR11 = 0xFF11;
    public static final int NR12 = 0xFF12;
    public static final int NR13 = 0xFF13;
    public static final int NR14 = 0xFF14;
    public static final int NR15 = 0xFF15;
    public static final int NR21 = 0xFF16;
    public static final int NR22 = 0xFF17;
    public static final int NR23 = 0xFF18;
    public static final int NR24 = 0xFF19;
    public static final int NR30 = 0xFF1A;
    public static final int NR31 = 0xFF1B;
    public static final int NR32 = 0xFF1C;
    public static final int NR33 = 0xFF1D;
    public static final int NR34 = 0xFF1E;
    public static final int NR1F = 0xFF1F;
    public static final int NR41 = 0xFF20;
    public static final int NR42 = 0xFF21;
    public static final int NR43 = 0xFF22;
    public static final int NR44 = 0xFF23;
    public static final int NR50 = 0xFF24;
    public static final int NR51 = 0xFF25;
    public static final int NR52 = 0xFF26;

    // Graphics (LCD / PPU)
    public static final int LCDC = 0xFF40; // LCD Control
    public static final int STAT = 0xFF41; // LCD Status
    public static final int SCY = 0xFF42; // Scroll Y
    public static final int SCX = 0xFF43; // Scroll X
    public static final int LY = 0xFF44; // LCD Y Coordinate (Scanline)
    public static final int LYC = 0xFF45; // LY Compare (Interrupt trigger)
    public static final int DMA = 0xFF46; // DMA Transfer
    public static final int BGP = 0xFF47; // Background Palette
    public static final int OBP0 = 0xFF48; // Object Palette 0
    public static final int OBP1 = 0xFF49; // Object Palette 1
    public static final int WY = 0xFF4A; // Window Y Position
    public static final int WX = 0xFF4B; // Window X Position (+7)
    public static final int KEY1 = 0xFF4D; // CGB speed switch
    public static final int VBK = 0xFF4F; // CGB VRAM bank
    public static final int HDMA1 = 0xFF51; // CGB DMA source high
    public static final int HDMA2 = 0xFF52; // CGB DMA source low
    public static final int HDMA3 = 0xFF53; // CGB DMA destination high
    public static final int HDMA4 = 0xFF54; // CGB DMA destination low
    public static final int HDMA5 = 0xFF55; // CGB DMA length/mode/start
    public static final int RP = 0xFF56; // CGB infrared
    public static final int BCPS = 0xFF68; // CGB BG palette index
    public static final int BCPD = 0xFF69; // CGB BG palette data
    public static final int OCPS = 0xFF6A; // CGB OBJ palette index
    public static final int OCPD = 0xFF6B; // CGB OBJ palette data
    public static final int OPRI = 0xFF6C; // CGB object priority mode
    public static final int SVBK = 0xFF70; // CGB WRAM bank

    // Misc
    public static final int BOOT_ROM_DISABLE = 0xFF50; // Write 1 to disable boot ROM
}

