package com.blackaby.Backend.GB.Memory;

import com.blackaby.Backend.GB.CPU.GBProcessor;
import com.blackaby.Backend.GB.Graphics.GBColor;
import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.GB.Peripherals.GBAudioProcessingUnit;
import com.blackaby.Backend.GB.Peripherals.GBGamepad;
import com.blackaby.Backend.GB.Peripherals.GBTimerSet;

import java.util.Arrays;

/**
 * Represents the Game Boy memory map used by the emulator.
 * <p>
 * Cartridge ROM and external RAM are routed through the active mapper, while
 * working RAM, echo RAM, DMA, timers, audio, and I/O registers remain under
 * the usual CPU-visible rules. In CGB mode, the memory map also exposes VRAM
 * bank switching, WRAM bank switching, palette RAM, and the extra boot ROM
 * layout used by the color models.
 */
public class GBMemory {

    public record MemoryState(
            int[] ram,
            int[] bootRom,
            boolean bootRomMapped,
            boolean cgbBootRomMapped,
            boolean cgbMode,
            int activeVramBank,
            int activeWramBank,
            boolean key1Armed,
            boolean doubleSpeedMode,
            boolean dmaActive,
            int dmaCounter,
            int dmaSource,
            int dmaCycleCounter,
            boolean hdmaActive,
            int hdmaSource,
            int hdmaDestination,
            int hdmaBlocksRemaining,
            boolean hdmaTransferredThisHblank,
            int[][] vramBanks,
            int[][] wramBanks,
            int[] bgPaletteRam,
            int[] objPaletteRam,
            GBCartController.MapperState cartridgeState) implements java.io.Serializable {
    }

    private static final int vramBankSize = 0x2000;
    private static final int wramBankSize = 0x1000;
    private static final int cgbPaletteRamSize = 0x40;

    private int[] ram;
    private int[] bootRom;
    private boolean bootRomMapped;
    private boolean cgbBootRomMapped;
    private GBCartController cartridge;
    private GBRom currentRom;
    private GBTimerSet timer;
    private GBProcessor cpu;
    private GBGamepad joypad;
    private GBAudioProcessingUnit apu;
    private GBCheatEngine cheatEngine;

    private final int[][] vramBanks = new int[2][vramBankSize];
    private final int[][] wramBanks = new int[8][wramBankSize];
    private final int[] bgPaletteRam = new int[cgbPaletteRamSize];
    private final int[] objPaletteRam = new int[cgbPaletteRamSize];
    private final int[] bgPaletteRgbCache = new int[32];
    private final int[] objPaletteRgbCache = new int[32];

    private boolean cgbMode;
    private int activeVramBank;
    private int activeWramBank = 1;
    private boolean key1Armed;
    private boolean doubleSpeedMode;

    private boolean dmaActive;
    private int dmaCounter;
    private int dmaSource;
    private int dmaCycleCounter;
    private boolean hdmaActive;
    private int hdmaSource;
    private int hdmaDestination;
    private int hdmaBlocksRemaining;
    private boolean hdmaTransferredThisHblank;

    /**
     * Creates an empty memory map with no cartridge attached.
     */
    public GBMemory() {
        ram = new int[GBMemAddresses.MEMORY_SIZE];
    }

    /**
     * Attaches the timer peripheral to memory.
     *
     * @param timer timer instance
     */
    public void SetTimer(GBTimerSet timer) {
        this.timer = timer;
    }

    /**
     * Attaches the CPU to memory for stack and interrupt side effects.
     *
     * @param cpu CPU instance
     */
    public void SetCpu(GBProcessor cpu) {
        this.cpu = cpu;
    }

    /**
     * Attaches the joypad peripheral and mirrors its current register state.
     *
     * @param joypad joypad instance
     */
    public void SetJoypad(GBGamepad joypad) {
        this.joypad = joypad;
        if (joypad != null) {
            WriteDirect(GBMemAddresses.JOYPAD, joypad.ReadRegister());
        }
    }

    /**
     * Attaches the audio unit and mirrors the master control register.
     *
     * @param apu audio unit
     */
    public void SetApu(GBAudioProcessingUnit apu) {
        this.apu = apu;
        if (apu != null) {
            WriteDirect(GBMemAddresses.NR52, apu.Read(GBMemAddresses.NR52));
        }
    }

    /**
     * Attaches the current cheat engine used for read overrides.
     *
     * @param cheatEngine compiled cheat engine
     */
    public void SetCheatEngine(GBCheatEngine cheatEngine) {
        this.cheatEngine = cheatEngine;
    }

    /**
     * Loads a cartridge and clears the non-cartridge memory map.
     *
     * @param rom cartridge image to attach
     */
    public void LoadRom(GBRom rom) {
        LoadRom(rom, rom != null && rom.IsCgbCompatible());
    }

    /**
     * Loads a cartridge and clears the non-cartridge memory map using the
     * requested hardware mode for compatible cartridges.
     *
     * @param rom        cartridge image to attach
     * @param useCgbMode whether to enable CGB hardware when the cartridge allows it
     */
    public void LoadRom(GBRom rom, boolean useCgbMode) {
        cartridge = GBCartController.Create(rom);
        currentRom = rom;
        cgbMode = rom != null && (rom.IsCgbOnly() || useCgbMode);

        ram = new int[GBMemAddresses.MEMORY_SIZE];
        bootRom = null;
        bootRomMapped = false;
        cgbBootRomMapped = false;
        activeVramBank = 0;
        activeWramBank = 1;
        key1Armed = false;
        doubleSpeedMode = false;
        dmaActive = false;
        dmaCounter = 0;
        dmaSource = 0;
        dmaCycleCounter = 0;
        hdmaActive = false;
        hdmaSource = 0;
        hdmaDestination = 0x8000;
        hdmaBlocksRemaining = 0;
        hdmaTransferredThisHblank = false;

        for (int bank = 0; bank < vramBanks.length; bank++) {
            vramBanks[bank] = new int[vramBankSize];
        }
        for (int bank = 0; bank < wramBanks.length; bank++) {
            wramBanks[bank] = new int[wramBankSize];
        }
        for (int index = 0; index < bgPaletteRam.length; index++) {
            bgPaletteRam[index] = 0;
            objPaletteRam[index] = 0;
        }
        Arrays.fill(bgPaletteRgbCache, 0);
        Arrays.fill(objPaletteRgbCache, 0);
    }

    /**
     * Loads a DMG or CGB boot ROM and maps it over cartridge ROM.
     *
     * @param bootRomBytes boot ROM bytes
     */
    public void LoadBootRom(byte[] bootRomBytes) {
        if (bootRomBytes == null) {
            throw new IllegalArgumentException("A boot ROM is required.");
        }
        LoadBootRom(bootRomBytes, bootRomBytes.length == 0x800 || bootRomBytes.length == 0x900);
    }

    /**
     * Loads a boot ROM and maps it over cartridge ROM.
     *
     * @param bootRomBytes boot ROM bytes
     * @param isCgbBootRom whether the boot ROM uses the CGB split mapping
     */
    public void LoadBootRom(byte[] bootRomBytes, boolean isCgbBootRom) {
        if (isCgbBootRom && bootRomBytes != null && bootRomBytes.length == 0x900) {
            bootRomBytes = NormaliseCgbBootRom(bootRomBytes);
        }

        int expectedLength = isCgbBootRom ? 0x800 : 0x100;
        if (bootRomBytes == null || bootRomBytes.length != expectedLength) {
            throw new IllegalArgumentException(
                    "The " + (isCgbBootRom ? "CGB" : "DMG") + " boot ROM must be exactly " + expectedLength
                            + " bytes.");
        }

        bootRom = new int[bootRomBytes.length];
        for (int index = 0; index < bootRomBytes.length; index++) {
            bootRom[index] = bootRomBytes[index] & 0xFF;
        }

        bootRomMapped = true;
        cgbBootRomMapped = isCgbBootRom;
        WriteDirect(GBMemAddresses.BOOT_ROM_DISABLE, 0x00);
    }

    /**
     * Seeds the DMG post-boot hardware register state when the boot ROM is
     * skipped.
     */
    public void InitialiseDmgBootState() {
        if (joypad != null) {
            joypad.Reset();
            WriteDirect(GBMemAddresses.JOYPAD, joypad.ReadRegister());
        } else {
            WriteDirect(GBMemAddresses.JOYPAD, 0xCF);
        }

        WriteDirect(GBMemAddresses.SERIAL_DATA, 0x00);
        WriteDirect(GBMemAddresses.SERIAL_CONTROL, 0x7E);
        WriteDirect(GBMemAddresses.TIMA, 0x00);
        WriteDirect(GBMemAddresses.TMA, 0x00);
        WriteDirect(GBMemAddresses.TAC, 0xF8);
        WriteDirect(GBMemAddresses.INTERRUPT_FLAG, 0xE1);

        if (apu != null) {
            apu.InitialiseDmgBootState();
        } else {
            WriteDirect(0xFF10, 0x80);
            WriteDirect(0xFF11, 0xBF);
            WriteDirect(0xFF12, 0xF3);
            WriteDirect(0xFF13, 0xFF);
            WriteDirect(0xFF14, 0xBF);
            WriteDirect(0xFF16, 0x3F);
            WriteDirect(0xFF17, 0x00);
            WriteDirect(0xFF18, 0xFF);
            WriteDirect(0xFF19, 0xBF);
            WriteDirect(0xFF1A, 0x7F);
            WriteDirect(0xFF1B, 0xFF);
            WriteDirect(0xFF1C, 0x9F);
            WriteDirect(0xFF1D, 0xFF);
            WriteDirect(0xFF1E, 0xBF);
            WriteDirect(0xFF20, 0xFF);
            WriteDirect(0xFF21, 0x00);
            WriteDirect(0xFF22, 0x00);
            WriteDirect(0xFF23, 0xBF);
            WriteDirect(0xFF24, 0x77);
            WriteDirect(0xFF25, 0xF3);
            WriteDirect(0xFF26, 0xF1);
        }

        WriteDirect(GBMemAddresses.LCDC, 0x91);
        WriteDirect(GBMemAddresses.STAT, 0x80);
        WriteDirect(GBMemAddresses.SCY, 0x00);
        WriteDirect(GBMemAddresses.SCX, 0x00);
        WriteDirect(GBMemAddresses.LY, 0x00);
        WriteDirect(GBMemAddresses.LYC, 0x00);
        WriteDirect(GBMemAddresses.DMA, 0xFF);
        WriteDirect(GBMemAddresses.BGP, 0xFC);
        WriteDirect(GBMemAddresses.OBP0, 0xFF);
        WriteDirect(GBMemAddresses.OBP1, 0xFF);
        WriteDirect(GBMemAddresses.WY, 0x00);
        WriteDirect(GBMemAddresses.WX, 0x00);
        WriteDirect(GBMemAddresses.IE, 0x00);
        WriteDirect(GBMemAddresses.BOOT_ROM_DISABLE, 0x01);
    }

    /**
     * Seeds the CGB post-boot hardware state when the boot ROM is skipped.
     */
    public void InitialiseCgbBootState() {
        if (joypad != null) {
            joypad.Reset();
            WriteDirect(GBMemAddresses.JOYPAD, joypad.ReadRegister());
        } else {
            WriteDirect(GBMemAddresses.JOYPAD, 0xCF);
        }

        WriteDirect(GBMemAddresses.SERIAL_DATA, 0x00);
        WriteDirect(GBMemAddresses.SERIAL_CONTROL, 0x7F);
        WriteDirect(GBMemAddresses.TIMA, 0x00);
        WriteDirect(GBMemAddresses.TMA, 0x00);
        WriteDirect(GBMemAddresses.TAC, 0xF8);
        WriteDirect(GBMemAddresses.INTERRUPT_FLAG, 0xE1);

        if (apu != null) {
            apu.InitialiseDmgBootState();
        } else {
            WriteDirect(0xFF10, 0x80);
            WriteDirect(0xFF11, 0xBF);
            WriteDirect(0xFF12, 0xF3);
            WriteDirect(0xFF13, 0xFF);
            WriteDirect(0xFF14, 0xBF);
            WriteDirect(0xFF16, 0x3F);
            WriteDirect(0xFF17, 0x00);
            WriteDirect(0xFF18, 0xFF);
            WriteDirect(0xFF19, 0xBF);
            WriteDirect(0xFF1A, 0x7F);
            WriteDirect(0xFF1B, 0xFF);
            WriteDirect(0xFF1C, 0x9F);
            WriteDirect(0xFF1D, 0xFF);
            WriteDirect(0xFF1E, 0xBF);
            WriteDirect(0xFF20, 0xFF);
            WriteDirect(0xFF21, 0x00);
            WriteDirect(0xFF22, 0x00);
            WriteDirect(0xFF23, 0xBF);
            WriteDirect(0xFF24, 0x77);
            WriteDirect(0xFF25, 0xF3);
            WriteDirect(0xFF26, 0xF1);
        }

        WriteDirect(GBMemAddresses.LCDC, 0x91);
        WriteDirect(GBMemAddresses.STAT, 0x80);
        WriteDirect(GBMemAddresses.SCY, 0x00);
        WriteDirect(GBMemAddresses.SCX, 0x00);
        WriteDirect(GBMemAddresses.LY, 0x00);
        WriteDirect(GBMemAddresses.LYC, 0x00);
        WriteDirect(GBMemAddresses.DMA, 0x00);
        WriteDirect(GBMemAddresses.BGP, 0xFC);
        WriteDirect(GBMemAddresses.OBP0, 0xFF);
        WriteDirect(GBMemAddresses.OBP1, 0xFF);
        WriteDirect(GBMemAddresses.WY, 0x00);
        WriteDirect(GBMemAddresses.WX, 0x00);
        WriteDirect(GBMemAddresses.KEY1, 0x00);
        WriteDirect(GBMemAddresses.VBK, 0x00);
        WriteDirect(GBMemAddresses.HDMA1, 0xFF);
        WriteDirect(GBMemAddresses.HDMA2, 0xFF);
        WriteDirect(GBMemAddresses.HDMA3, 0xFF);
        WriteDirect(GBMemAddresses.HDMA4, 0xFF);
        WriteDirect(GBMemAddresses.HDMA5, 0xFF);
        WriteDirect(GBMemAddresses.RP, 0x00);
        WriteDirect(GBMemAddresses.BCPS, 0xC0);
        WriteDirect(GBMemAddresses.OCPS, 0xC0);
        WriteDirect(GBMemAddresses.OPRI, 0x00);
        WriteDirect(GBMemAddresses.SVBK, 0x01);
        WriteDirect(GBMemAddresses.IE, 0x00);

        InitialiseCgbPalettesToWhite(bgPaletteRam, bgPaletteRgbCache);
        InitialiseCgbPalettesToWhite(objPaletteRam, objPaletteRgbCache);

        WriteDirect(GBMemAddresses.BOOT_ROM_DISABLE, 0x01);
    }

    /**
     * Writes straight to the memory backing store without applying CPU-visible
     * register side effects.
     *
     * @param address address to write
     * @param value   byte value to store
     */
    public void WriteDirect(int address, int value) {
        address &= 0xFFFF;
        value &= 0xFF;

        if (address >= GBMemAddresses.NOT_USABLE_START && address <= GBMemAddresses.NOT_USABLE_END) {
            return;
        }

        if (address >= GBMemAddresses.VRAM_START && address <= GBMemAddresses.VRAM_END) {
            vramBanks[activeVramBank][address - GBMemAddresses.VRAM_START] = value;
            return;
        }

        if (address >= GBMemAddresses.WORK_RAM_START && address <= 0xCFFF) {
            wramBanks[0][address - GBMemAddresses.WORK_RAM_START] = value;
            return;
        }

        if (address >= 0xD000 && address <= GBMemAddresses.WORK_RAM_END) {
            wramBanks[activeWramBank][address - 0xD000] = value;
            return;
        }

        if (address >= GBMemAddresses.ECHO_RAM_START && address <= GBMemAddresses.ECHO_RAM_END) {
            WriteDirect(address - 0x2000, value);
            return;
        }

        if (address == GBMemAddresses.VBK) {
            activeVramBank = value & 0x01;
            ram[address] = 0xFE | activeVramBank;
            return;
        }

        if (address == GBMemAddresses.SVBK) {
            activeWramBank = DecodeWramBank(value);
            ram[address] = 0xF8 | activeWramBank;
            return;
        }

        if (address == GBMemAddresses.KEY1) {
            key1Armed = (value & 0x01) != 0;
            ram[address] = (doubleSpeedMode ? 0x80 : 0x00) | (key1Armed ? 0x01 : 0x00);
            return;
        }

        if (address == GBMemAddresses.BCPS || address == GBMemAddresses.OCPS) {
            ram[address] = 0x40 | (value & 0xBF);
            return;
        }

        ram[address] = value;
    }

    /**
     * Reads a byte from the memory map.
     *
     * @param address CPU address to read
     * @return byte value visible to the CPU
     */
    public int Read(int address) {
        int resolvedAddress = address & 0xFFFF;
        int value = ReadInternal(resolvedAddress);
        GBCheatEngine currentCheatEngine = cheatEngine;
        if (currentCheatEngine == null || !currentCheatEngine.HasReadOverrides()) {
            return value;
        }
        return currentCheatEngine.ApplyReadOverride(NormaliseCheatAddress(resolvedAddress), value);
    }

    int ReadWithoutCheats(int address) {
        return ReadInternal(address & 0xFFFF);
    }

    private int ReadInternal(int address) {
        address &= 0xFFFF;

        if (bootRomMapped && bootRom != null && IsBootRomAddress(address)) {
            return bootRom[BootRomIndex(address)] & 0xFF;
        }

        if (address <= GBMemAddresses.ROM_BANK_N_END) {
            return cartridge != null ? cartridge.ReadRom(address) : 0xFF;
        }

        if (address >= GBMemAddresses.VRAM_START && address <= GBMemAddresses.VRAM_END) {
            return vramBanks[activeVramBank][address - GBMemAddresses.VRAM_START] & 0xFF;
        }

        if (address >= GBMemAddresses.EXTERNAL_RAM_START && address <= GBMemAddresses.EXTERNAL_RAM_END) {
            return cartridge != null ? cartridge.ReadRam(address - GBMemAddresses.EXTERNAL_RAM_START) : 0xFF;
        }

        if (address >= GBMemAddresses.WORK_RAM_START && address <= 0xCFFF) {
            return wramBanks[0][address - GBMemAddresses.WORK_RAM_START] & 0xFF;
        }

        if (address >= 0xD000 && address <= GBMemAddresses.WORK_RAM_END) {
            return wramBanks[activeWramBank][address - 0xD000] & 0xFF;
        }

        if (address >= GBMemAddresses.ECHO_RAM_START && address <= GBMemAddresses.ECHO_RAM_END) {
            return ReadInternal(address - 0x2000);
        }

        if (address >= GBMemAddresses.NOT_USABLE_START && address <= GBMemAddresses.NOT_USABLE_END) {
            return 0xFF;
        }

        if (address == GBMemAddresses.SERIAL_CONTROL) {
            return cgbMode ? (ram[address] | 0x7C) : (ram[address] | 0x7E);
        }

        if (address == GBMemAddresses.JOYPAD && joypad != null) {
            return joypad.ReadRegister();
        }

        if ((address >= GBMemAddresses.AUDIO_START && address <= GBMemAddresses.AUDIO_END)
                || (address >= GBMemAddresses.WAVE_PATTERN_START && address <= GBMemAddresses.WAVE_PATTERN_END)) {
            if (apu != null) {
                return apu.Read(address);
            }
        }

        if (address == GBMemAddresses.TAC) {
            return ram[address] | 0xF8;
        }

        if (address == GBMemAddresses.INTERRUPT_FLAG) {
            return ram[address] | 0xE0;
        }

        if (address == GBMemAddresses.STAT) {
            return ram[address] | 0x80;
        }

        if (address == GBMemAddresses.KEY1) {
            return cgbMode ? (0x7E | (doubleSpeedMode ? 0x80 : 0x00) | (key1Armed ? 0x01 : 0x00)) : 0xFF;
        }

        if (address == GBMemAddresses.VBK) {
            return cgbMode ? (0xFE | activeVramBank) : 0xFF;
        }

        if (address == GBMemAddresses.SVBK) {
            return cgbMode ? (0xF8 | activeWramBank) : 0xFF;
        }

        if (address == GBMemAddresses.BCPS || address == GBMemAddresses.OCPS
                || address == GBMemAddresses.HDMA1 || address == GBMemAddresses.HDMA2
                || address == GBMemAddresses.HDMA3 || address == GBMemAddresses.HDMA4
                || address == GBMemAddresses.HDMA5 || address == GBMemAddresses.OPRI
                || address == GBMemAddresses.RP) {
            return cgbMode ? (ram[address] & 0xFF) : 0xFF;
        }

        if (address == GBMemAddresses.BCPD) {
            return cgbMode ? bgPaletteRam[ram[GBMemAddresses.BCPS] & 0x3F] : 0xFF;
        }

        if (address == GBMemAddresses.OCPD) {
            return cgbMode ? objPaletteRam[ram[GBMemAddresses.OCPS] & 0x3F] : 0xFF;
        }

        return ram[address] & 0xFF;
    }

    private int NormaliseCheatAddress(int address) {
        if (address >= GBMemAddresses.ECHO_RAM_START && address <= GBMemAddresses.ECHO_RAM_END) {
            return address - 0x2000;
        }
        return address & 0xFFFF;
    }

    /**
     * Writes a byte to the memory map, applying register and peripheral side
     * effects where needed.
     *
     * @param address CPU address to write
     * @param value   byte value to store
     */
    public void Write(int address, int value) {
        address &= 0xFFFF;
        value &= 0xFF;

        if (address < 0x8000) {
            if (cartridge != null) {
                cartridge.Write(address, value);
            }
            return;
        }

        if (address >= GBMemAddresses.VRAM_START && address <= GBMemAddresses.VRAM_END) {
            vramBanks[activeVramBank][address - GBMemAddresses.VRAM_START] = value;
            return;
        }

        if (address >= GBMemAddresses.EXTERNAL_RAM_START && address <= GBMemAddresses.EXTERNAL_RAM_END) {
            if (cartridge != null) {
                cartridge.Write(address, value);
            }
            return;
        }

        if (address >= GBMemAddresses.WORK_RAM_START && address <= 0xCFFF) {
            wramBanks[0][address - GBMemAddresses.WORK_RAM_START] = value;
            return;
        }

        if (address >= 0xD000 && address <= GBMemAddresses.WORK_RAM_END) {
            wramBanks[activeWramBank][address - 0xD000] = value;
            return;
        }

        if (address >= GBMemAddresses.NOT_USABLE_START && address <= GBMemAddresses.NOT_USABLE_END) {
            return;
        }

        if (address >= GBMemAddresses.ECHO_RAM_START && address <= GBMemAddresses.ECHO_RAM_END) {
            Write(address - 0x2000, value);
            return;
        }

        if (address == GBMemAddresses.JOYPAD) {
            if (joypad != null) {
                joypad.WriteRegister(value);
                ram[address] = joypad.ReadRegister();
            } else {
                ram[address] = 0xC0 | (value & 0x30) | 0x0F;
            }
            return;
        }

        if (address == GBMemAddresses.SERIAL_CONTROL) {
            ram[address] = cgbMode ? (0x7C | (value & 0x83)) : (0x7E | (value & 0x81));
            return;
        }

        if (address == GBMemAddresses.BOOT_ROM_DISABLE) {
            ram[address] = value & 0x01;
            if ((value & 0x01) != 0) {
                bootRomMapped = false;
                cgbBootRomMapped = false;
            }
            return;
        }

        if ((address >= GBMemAddresses.AUDIO_START && address <= GBMemAddresses.AUDIO_END)
                || (address >= GBMemAddresses.WAVE_PATTERN_START && address <= GBMemAddresses.WAVE_PATTERN_END)) {
            if (apu != null) {
                apu.Write(address, value);
            } else {
                ram[address] = value;
            }
            return;
        }

        if (address == GBMemAddresses.DIV) {
            if (timer != null) {
                timer.ResetDiv();
            }
            return;
        }

        if (address == GBMemAddresses.TIMA) {
            if (timer != null && timer.timaOverflowPending) {
                timer.CancelPendingOverflow();
            }
        }

        if (address == GBMemAddresses.TAC) {
            if (timer != null) {
                timer.WriteTac(value);
            } else {
                ram[address] = 0xF8 | (value & 0x07);
            }
            return;
        }

        if (address == GBMemAddresses.INTERRUPT_FLAG) {
            ram[address] = 0xE0 | (value & 0x1F);
            return;
        }

        if (address == GBMemAddresses.STAT) {
            ram[address] = 0x80 | (ram[address] & 0x07) | (value & 0x78);
            return;
        }

        if (address == GBMemAddresses.LY) {
            ram[address] = 0;
            return;
        }

        if (address == GBMemAddresses.DMA) {
            dmaSource = value << 8;
            dmaCounter = 0;
            dmaCycleCounter = 0;
            dmaActive = true;
            ram[address] = value;
            return;
        }

        if (!cgbMode && IsCgbOnlyRegister(address)) {
            return;
        }

        if (address == GBMemAddresses.KEY1) {
            key1Armed = (value & 0x01) != 0;
            ram[address] = (doubleSpeedMode ? 0x80 : 0x00) | (key1Armed ? 0x01 : 0x00);
            return;
        }

        if (address == GBMemAddresses.VBK) {
            activeVramBank = value & 0x01;
            ram[address] = 0xFE | activeVramBank;
            return;
        }

        if (address == GBMemAddresses.SVBK) {
            activeWramBank = DecodeWramBank(value);
            ram[address] = 0xF8 | activeWramBank;
            return;
        }

        if (address == GBMemAddresses.BCPS || address == GBMemAddresses.OCPS) {
            ram[address] = 0x40 | (value & 0xBF);
            return;
        }

        if (address == GBMemAddresses.BCPD) {
            WritePaletteData(bgPaletteRam, bgPaletteRgbCache, GBMemAddresses.BCPS, value);
            return;
        }

        if (address == GBMemAddresses.OCPD) {
            WritePaletteData(objPaletteRam, objPaletteRgbCache, GBMemAddresses.OCPS, value);
            return;
        }

        if (address == GBMemAddresses.HDMA1 || address == GBMemAddresses.HDMA2
                || address == GBMemAddresses.HDMA3 || address == GBMemAddresses.HDMA4) {
            ram[address] = value;
            return;
        }

        if (address == GBMemAddresses.HDMA5) {
            HandleHdmaControlWrite(value);
            return;
        }

        ram[address] = value;
    }

    /**
     * Advances the DMA engine by one host tick.
     */
    public void TickDma() {
        if (!dmaActive) {
            return;
        }

        dmaCycleCounter++;
        if (dmaCycleCounter < 4) {
            return;
        }
        dmaCycleCounter = 0;

        int destination = (GBMemAddresses.OAM_START + dmaCounter) & 0xFFFF;
        int source = (dmaSource + dmaCounter) & 0xFFFF;
        int value = Read(source);
        ram[destination] = value;

        dmaCounter++;
        if (dmaCounter >= 0xA0) {
            dmaActive = false;
            dmaCounter = 0;
            dmaCycleCounter = 0;
        }
    }

    /**
     * Advances the CGB H-Blank DMA engine by one master clock.
     *
     * @param hblankTransferWindowOpen whether the current PPU state can service
     *                                 H-Blank DMA
     */
    public void TickHdma(boolean hblankTransferWindowOpen) {
        if (!hdmaActive) {
            hdmaTransferredThisHblank = false;
            return;
        }

        if (!hblankTransferWindowOpen) {
            hdmaTransferredThisHblank = false;
            return;
        }

        if (hdmaTransferredThisHblank) {
            return;
        }

        TransferHdmaBlock();
        hdmaTransferredThisHblank = true;
    }

    /**
     * Returns whether the active cartridge exposes battery-backed save data.
     *
     * @return {@code true} when save RAM should be persisted
     */
    public boolean HasSaveData() {
        return cartridge != null && cartridge.SupportsSaveData();
    }

    /**
     * Exports the raw cartridge save RAM.
     *
     * @return raw save bytes
     */
    public byte[] ExportSaveData() {
        return cartridge == null ? new byte[0] : cartridge.ExportSaveData();
    }

    /**
     * Exports supplementary cartridge persistence data such as RTC state.
     *
     * @return supplementary save bytes
     */
    public byte[] ExportSupplementalSaveData() {
        return cartridge == null ? new byte[0] : cartridge.ExportSupplementalSaveData();
    }

    /**
     * Returns whether the currently loaded ROM advertises Game Boy Color
     * compatibility.
     *
     * @return {@code true} when the loaded cartridge is CGB capable
     */
    public boolean IsLoadedRomCgbCompatible() {
        return currentRom != null && currentRom.IsCgbCompatible();
    }

    /**
     * Returns whether the active hardware mode is CGB.
     *
     * @return {@code true} when CGB-only features are enabled
     */
    public boolean IsCgbMode() {
        return cgbMode;
    }

    /**
     * Returns whether the active hardware is a CGB running a monochrome cartridge
     * in DMG compatibility mode.
     *
     * @return {@code true} when CGB hardware is active for a non-CGB cartridge
     */
    public boolean IsDmgCompatibilityMode() {
        return cgbMode
                && currentRom != null
                && !currentRom.IsCgbCompatible()
                && !cgbBootRomMapped;
    }

    /**
     * Seeds the first compatibility palettes used by DMG software running on CGB
     * hardware when the real CGB boot ROM is skipped.
     *
     * @param backgroundPalette background palette colours
     * @param spritePalette0    sprite palette 0 colours
     * @param spritePalette1    sprite palette 1 colours
     */
    public void SeedDmgCompatibilityPalettes(GBColor[] backgroundPalette, GBColor[] spritePalette0,
            GBColor[] spritePalette1) {
        if (!IsDmgCompatibilityMode()) {
            return;
        }

        WriteCgbPalette(0, backgroundPalette, bgPaletteRam);
        WriteCgbPalette(0, spritePalette0, objPaletteRam);
        WriteCgbPalette(1, spritePalette1, objPaletteRam);
        RebuildCgbPaletteCache(bgPaletteRam, bgPaletteRgbCache);
        RebuildCgbPaletteCache(objPaletteRam, objPaletteRgbCache);
    }

    /**
     * Returns whether the CPU is currently running in CGB double-speed mode.
     *
     * @return {@code true} when double-speed mode is active
     */
    public boolean IsDoubleSpeedMode() {
        return doubleSpeedMode;
    }

    /**
     * Loads raw cartridge save RAM into the active mapper.
     *
     * @param saveData raw save bytes
     */
    public void LoadSaveData(byte[] saveData) {
        if (cartridge != null) {
            cartridge.LoadSaveData(saveData);
        }
    }

    /**
     * Loads supplementary cartridge persistence data such as RTC state.
     *
     * @param saveData supplementary save bytes
     */
    public void LoadSupplementalSaveData(byte[] saveData) {
        if (cartridge != null) {
            cartridge.LoadSupplementalSaveData(saveData);
        }
    }

    /**
     * Reads a continuous block of bytes.
     *
     * @param start first address to read
     * @param count number of bytes to read
     * @return copied byte block
     */
    public int[] ReadBytes(int start, int count) {
        int[] bytes = new int[count];
        for (int index = 0; index < count; index++) {
            bytes[index] = Read(start + index);
        }
        return bytes;
    }

    /**
     * Pushes one byte on to the emulated stack.
     *
     * @param value byte value to push
     */
    public void StackPushByte(int value) {
        int stackPointer = cpu.GetSP();
        stackPointer = (stackPointer - 1) & 0xFFFF;
        Write(stackPointer, value & 0xFF);
        cpu.SetSP(stackPointer);
    }

    /**
     * Pushes a 16-bit value on to the emulated stack.
     *
     * @param value 16-bit value to push
     */
    public void StackPushShort(int value) {
        int upperByte = (value >> 8) & 0xFF;
        int lowerByte = value & 0xFF;
        StackPushByte(upperByte);
        StackPushByte(lowerByte);
    }

    /**
     * Pops one byte from the emulated stack.
     *
     * @return popped byte
     */
    public int StackPopByte() {
        int stackPointer = cpu.GetSP();
        int value = Read(stackPointer);
        stackPointer = (stackPointer + 1) & 0xFFFF;
        cpu.SetSP(stackPointer);
        return value;
    }

    /**
     * Pops a 16-bit value from the emulated stack.
     *
     * @return popped 16-bit value
     */
    public int StackPopShort() {
        int lowerByte = StackPopByte();
        int upperByte = StackPopByte();
        return (((upperByte << 8) & 0xFF00) | (lowerByte & 0xFF)) & 0xFFFF;
    }

    /**
     * Mirrors the timer's divider output into memory.
     *
     * @param value divider register value
     */
    public void SetDividerFromTimer(int value) {
        WriteDirect(GBMemAddresses.DIV, value);
    }

    /**
     * Mirrors the timer's TIMA output into memory.
     *
     * @param value TIMA register value
     */
    public void SetTimaFromTimer(int value) {
        WriteDirect(GBMemAddresses.TIMA, value);
    }

    /**
     * Returns the raw stored value for a memory-mapped register without applying
     * CPU-visible read masks or peripheral callbacks.
     *
     * @param address register address
     * @return underlying stored byte
     */
    public int ReadRegisterDirect(int address) {
        return ram[address & 0xFFFF] & 0xFF;
    }

    /**
     * Reads from a specific VRAM bank regardless of the currently selected bank.
     *
     * @param bank    VRAM bank index
     * @param address VRAM address from {@code 0x8000} to {@code 0x9FFF}
     * @return byte value from the requested VRAM bank
     */
    public int ReadVideoRam(int bank, int address) {
        int resolvedBank = bank & 0x01;
        int offset = (address & 0xFFFF) - GBMemAddresses.VRAM_START;
        if (offset < 0 || offset >= vramBankSize) {
            return 0xFF;
        }
        return vramBanks[resolvedBank][offset] & 0xFF;
    }

    /**
     * Reads one byte from OAM without applying CPU-side access rules.
     *
     * @param address OAM address from {@code 0xFE00} to {@code 0xFE9F}
     * @return raw OAM byte
     */
    public int ReadOamByte(int address) {
        int resolvedAddress = address & 0xFFFF;
        if (resolvedAddress < GBMemAddresses.OAM_START || resolvedAddress > GBMemAddresses.OAM_END) {
            return 0xFF;
        }
        return ram[resolvedAddress] & 0xFF;
    }

    /**
     * Returns one CGB background palette colour as packed ARGB.
     *
     * @param paletteIndex palette index from 0 to 7
     * @param colourIndex  palette colour index from 0 to 3
     * @return packed ARGB value
     */
    public int ReadCgbBackgroundPaletteColourRgb(int paletteIndex, int colourIndex) {
        int safePaletteIndex = Math.max(0, Math.min(7, paletteIndex));
        int safeColourIndex = Math.max(0, Math.min(3, colourIndex));
        return bgPaletteRgbCache[(safePaletteIndex * 4) + safeColourIndex];
    }

    /**
     * Returns one CGB object palette colour as packed ARGB.
     *
     * @param paletteIndex palette index from 0 to 7
     * @param colourIndex  palette colour index from 0 to 3
     * @return packed ARGB value
     */
    public int ReadCgbObjectPaletteColourRgb(int paletteIndex, int colourIndex) {
        int safePaletteIndex = Math.max(0, Math.min(7, paletteIndex));
        int safeColourIndex = Math.max(0, Math.min(3, colourIndex));
        return objPaletteRgbCache[(safePaletteIndex * 4) + safeColourIndex];
    }

    /**
     * Returns whether the serial port is currently requesting a transferred byte.
     *
     * @return {@code true} when the serial transfer-complete condition is active
     */
    public boolean IsSerialTransferInProgress() {
        return (ram[GBMemAddresses.SERIAL_CONTROL] & 0x81) == 0x81;
    }

    /**
     * Returns the raw serial data register.
     *
     * @return pending serial byte
     */
    public int ReadSerialDataRegister() {
        return ram[GBMemAddresses.SERIAL_DATA] & 0xFF;
    }

    /**
     * Applies the usual transfer-complete side effects for the serial port.
     */
    public void CompleteSerialTransfer() {
        ram[GBMemAddresses.SERIAL_DATA] = 0xFF;
        ram[GBMemAddresses.SERIAL_CONTROL] &= ~0x80;
    }

    /**
     * Handles the CGB speed-switch side effects of the STOP instruction.
     */
    public void HandleStopInstruction() {
        if (!cgbMode || !key1Armed) {
            return;
        }

        doubleSpeedMode = !doubleSpeedMode;
        key1Armed = false;
        ram[GBMemAddresses.KEY1] = doubleSpeedMode ? 0x80 : 0x00;
    }

    /**
     * Captures the mutable memory, mapper, DMA, and palette state.
     *
     * @return memory state snapshot
     */
    public MemoryState CaptureState() {
        return new MemoryState(
                Arrays.copyOf(ram, ram.length),
                bootRom == null ? null : Arrays.copyOf(bootRom, bootRom.length),
                bootRomMapped,
                cgbBootRomMapped,
                cgbMode,
                activeVramBank,
                activeWramBank,
                key1Armed,
                doubleSpeedMode,
                dmaActive,
                dmaCounter,
                dmaSource,
                dmaCycleCounter,
                hdmaActive,
                hdmaSource,
                hdmaDestination,
                hdmaBlocksRemaining,
                hdmaTransferredThisHblank,
                Copy2dArray(vramBanks),
                Copy2dArray(wramBanks),
                Arrays.copyOf(bgPaletteRam, bgPaletteRam.length),
                Arrays.copyOf(objPaletteRam, objPaletteRam.length),
                cartridge == null ? null : cartridge.CaptureState());
    }

    /**
     * Restores the mutable memory, mapper, DMA, and palette state.
     *
     * @param state memory snapshot to restore
     */
    public void RestoreState(MemoryState state) {
        if (state == null) {
            throw new IllegalArgumentException("A memory quick state is required.");
        }
        if (state.ram() == null || state.ram().length != GBMemAddresses.MEMORY_SIZE) {
            throw new IllegalArgumentException("The quick state memory image is invalid.");
        }
        if (state.cgbMode() != cgbMode) {
            throw new IllegalArgumentException("The quick state was created for a different hardware mode.");
        }

        ram = Arrays.copyOf(state.ram(), state.ram().length);
        bootRom = state.bootRom() == null ? null : Arrays.copyOf(state.bootRom(), state.bootRom().length);
        bootRomMapped = state.bootRomMapped();
        cgbBootRomMapped = state.cgbBootRomMapped();
        activeVramBank = state.activeVramBank() & 0x01;
        activeWramBank = DecodeWramBank(state.activeWramBank());
        key1Armed = state.key1Armed();
        doubleSpeedMode = state.doubleSpeedMode();
        dmaActive = state.dmaActive();
        dmaCounter = Math.max(0, state.dmaCounter());
        dmaSource = state.dmaSource() & 0xFFFF;
        dmaCycleCounter = Math.max(0, state.dmaCycleCounter());
        hdmaActive = state.hdmaActive();
        hdmaSource = state.hdmaSource() & 0xFFF0;
        hdmaDestination = 0x8000 | (state.hdmaDestination() & 0x1FF0);
        hdmaBlocksRemaining = Math.max(0, state.hdmaBlocksRemaining());
        hdmaTransferredThisHblank = state.hdmaTransferredThisHblank();

        CopyInto(state.vramBanks(), vramBanks);
        CopyInto(state.wramBanks(), wramBanks);
        CopyInto(state.bgPaletteRam(), bgPaletteRam);
        CopyInto(state.objPaletteRam(), objPaletteRam);
        RebuildCgbPaletteCache(bgPaletteRam, bgPaletteRgbCache);
        RebuildCgbPaletteCache(objPaletteRam, objPaletteRgbCache);

        if (cartridge != null && state.cartridgeState() != null) {
            cartridge.RestoreState(state.cartridgeState());
        }
    }

    private boolean IsBootRomAddress(int address) {
        if (!cgbBootRomMapped) {
            return address >= 0x0000 && address <= 0x00FF;
        }
        return (address >= 0x0000 && address <= 0x00FF) || (address >= 0x0200 && address <= 0x08FF);
    }

    private int BootRomIndex(int address) {
        return address <= 0x00FF ? address : address - 0x0100;
    }

    private byte[] NormaliseCgbBootRom(byte[] bootRomBytes) {
        byte[] normalisedBytes = new byte[0x800];
        System.arraycopy(bootRomBytes, 0, normalisedBytes, 0, 0x100);
        System.arraycopy(bootRomBytes, 0x200, normalisedBytes, 0x100, 0x700);
        return normalisedBytes;
    }

    private int DecodeWramBank(int value) {
        int bank = value & 0x07;
        return bank == 0 ? 1 : bank;
    }

    private boolean IsCgbOnlyRegister(int address) {
        return switch (address) {
            case GBMemAddresses.KEY1, GBMemAddresses.VBK, GBMemAddresses.HDMA1, GBMemAddresses.HDMA2,
                    GBMemAddresses.HDMA3, GBMemAddresses.HDMA4, GBMemAddresses.HDMA5,
                    GBMemAddresses.RP, GBMemAddresses.BCPS, GBMemAddresses.BCPD,
                    GBMemAddresses.OCPS, GBMemAddresses.OCPD, GBMemAddresses.OPRI,
                    GBMemAddresses.SVBK ->
                true;
            default -> false;
        };
    }

    private void WritePaletteData(int[] paletteRam, int[] rgbCache, int indexRegister, int value) {
        int paletteIndex = ram[indexRegister] & 0x3F;
        paletteRam[paletteIndex] = value & 0xFF;
        UpdateCgbPaletteCacheEntry(paletteRam, rgbCache, paletteIndex);
        if ((ram[indexRegister] & 0x80) != 0) {
            int nextIndex = (paletteIndex + 1) & 0x3F;
            ram[indexRegister] = (ram[indexRegister] & 0x80) | 0x40 | nextIndex;
        }
    }

    private void HandleHdmaControlWrite(int value) {
        if (hdmaActive) {
            if ((value & 0x80) == 0) {
                hdmaActive = false;
                hdmaTransferredThisHblank = false;
                ram[GBMemAddresses.HDMA5] = 0x80 | Math.max(0, hdmaBlocksRemaining - 1);
            }
            return;
        }

        if ((value & 0x80) != 0) {
            hdmaActive = true;
            hdmaSource = ((ram[GBMemAddresses.HDMA1] << 8) | (ram[GBMemAddresses.HDMA2] & 0xF0)) & 0xFFF0;
            hdmaDestination = 0x8000
                    | (((ram[GBMemAddresses.HDMA3] & 0x1F) << 8) | (ram[GBMemAddresses.HDMA4] & 0xF0));
            hdmaBlocksRemaining = (value & 0x7F) + 1;
            hdmaTransferredThisHblank = false;
            ram[GBMemAddresses.HDMA5] = Math.max(0, hdmaBlocksRemaining - 1) & 0x7F;
            return;
        }

        PerformGeneralHdmaTransfer(value);
    }

    private void PerformGeneralHdmaTransfer(int controlValue) {
        int length = ((controlValue & 0x7F) + 1) * 0x10;
        int source = ((ram[GBMemAddresses.HDMA1] << 8) | (ram[GBMemAddresses.HDMA2] & 0xF0)) & 0xFFF0;
        int destination = 0x8000 | (((ram[GBMemAddresses.HDMA3] & 0x1F) << 8) | (ram[GBMemAddresses.HDMA4] & 0xF0));

        for (int index = 0; index < length; index++) {
            Write(destination + index, Read(source + index));
        }

        hdmaActive = false;
        hdmaBlocksRemaining = 0;
        hdmaTransferredThisHblank = false;
        ram[GBMemAddresses.HDMA5] = 0xFF;
    }

    private void TransferHdmaBlock() {
        for (int index = 0; index < 0x10; index++) {
            Write((hdmaDestination + index) & 0xFFFF, Read((hdmaSource + index) & 0xFFFF));
        }

        hdmaSource = (hdmaSource + 0x10) & 0xFFF0;
        hdmaDestination = 0x8000 | ((hdmaDestination + 0x10) & 0x1FF0);
        hdmaBlocksRemaining = Math.max(0, hdmaBlocksRemaining - 1);

        if (hdmaBlocksRemaining == 0) {
            hdmaActive = false;
            ram[GBMemAddresses.HDMA5] = 0xFF;
            return;
        }

        ram[GBMemAddresses.HDMA5] = (hdmaBlocksRemaining - 1) & 0x7F;
    }

    private void InitialiseCgbPalettesToWhite(int[] paletteRam, int[] rgbCache) {
        for (int paletteIndex = 0; paletteIndex < 8; paletteIndex++) {
            for (int colourIndex = 0; colourIndex < 4; colourIndex++) {
                int base = (paletteIndex * 8) + (colourIndex * 2);
                paletteRam[base] = 0xFF;
                paletteRam[base + 1] = 0x7F;
            }
        }
        RebuildCgbPaletteCache(paletteRam, rgbCache);
    }

    private void WriteCgbPalette(int paletteIndex, GBColor[] sourcePalette, int[] targetPaletteRam) {
        int safePaletteIndex = Math.max(0, Math.min(7, paletteIndex));
        for (int colourIndex = 0; colourIndex < 4; colourIndex++) {
            GBColor colour = sourcePalette[Math.max(0, Math.min(sourcePalette.length - 1, colourIndex))];
            int colour555 = PackCgbRgb555(colour);
            int base = (safePaletteIndex * 8) + (colourIndex * 2);
            targetPaletteRam[base] = colour555 & 0xFF;
            targetPaletteRam[base + 1] = (colour555 >> 8) & 0x7F;
        }
    }

    private int PackCgbRgb555(GBColor colour) {
        int red = QuantiseToCgbChannel(colour.red);
        int green = QuantiseToCgbChannel(colour.green);
        int blue = QuantiseToCgbChannel(colour.blue);
        return red | (green << 5) | (blue << 10);
    }

    private int QuantiseToCgbChannel(int channel) {
        int clamped = Math.max(0, Math.min(255, channel));
        return (clamped * 31 + 127) / 255;
    }

    private void RebuildCgbPaletteCache(int[] paletteRam, int[] rgbCache) {
        for (int index = 0; index < rgbCache.length; index++) {
            int base = index * 2;
            rgbCache[index] = ReadCgbPaletteColourRgbFromRam(paletteRam, base);
        }
    }

    private void UpdateCgbPaletteCacheEntry(int[] paletteRam, int[] rgbCache, int paletteIndex) {
        int base = paletteIndex & 0x3E;
        rgbCache[base >> 1] = ReadCgbPaletteColourRgbFromRam(paletteRam, base);
    }

    private int ReadCgbPaletteColourRgbFromRam(int[] paletteRam, int base) {
        int colour555 = paletteRam[base] | ((paletteRam[base + 1] & 0x7F) << 8);
        return Cgb555ToRgb(colour555);
    }

    private int Cgb555ToRgb(int colour555) {
        int red = colour555 & 0x1F;
        int green = (colour555 >> 5) & 0x1F;
        int blue = (colour555 >> 10) & 0x1F;

        // Approximate the original CGB LCD's non-linear, cross-channel response
        // instead of treating RGB555 values as direct sRGB output.
        int correctedRed = ScaleCorrectedCgbChannel((red * 26) + (green * 4) + (blue * 2));
        int correctedGreen = ScaleCorrectedCgbChannel((green * 24) + (blue * 8));
        int correctedBlue = ScaleCorrectedCgbChannel((red * 6) + (green * 4) + (blue * 22));
        return 0xFF000000 | (correctedRed << 16) | (correctedGreen << 8) | correctedBlue;
    }

    private int ScaleCorrectedCgbChannel(int value) {
        int clamped = Math.max(0, Math.min(960, value));
        return (clamped * 255 + 480) / 960;
    }

    private int[][] Copy2dArray(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int index = 0; index < source.length; index++) {
            copy[index] = Arrays.copyOf(source[index], source[index].length);
        }
        return copy;
    }

    private void CopyInto(int[][] source, int[][] target) {
        if (source == null || target == null || source.length != target.length) {
            throw new IllegalArgumentException("The quick state bank data is invalid.");
        }

        for (int index = 0; index < target.length; index++) {
            if (source[index] == null || source[index].length != target[index].length) {
                throw new IllegalArgumentException("The quick state bank data is invalid.");
            }
            System.arraycopy(source[index], 0, target[index], 0, target[index].length);
        }
    }

    private void CopyInto(int[] source, int[] target) {
        if (source == null || target == null || source.length != target.length) {
            throw new IllegalArgumentException("The quick state palette data is invalid.");
        }
        System.arraycopy(source, 0, target, 0, target.length);
    }
}
