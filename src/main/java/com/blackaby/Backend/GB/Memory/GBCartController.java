package com.blackaby.Backend.GB.Memory;

import com.blackaby.Backend.GB.Misc.GBRom;

import java.util.Arrays;

/**
 * Base class for cartridge mapper implementations.
 * <p>
 * A controller is responsible for mapping CPU reads and writes in cartridge
 * address space to the correct ROM bank, external RAM bank, and mapper control
 * registers.
 */
public abstract class GBCartController {

    public record MapperState(String mapperType, byte[] ramData, int[] registers, byte[] supplementalData)
            implements java.io.Serializable {
    }

    protected final GBRom rom;
    protected final int[] romData;
    protected final int[] ramData;

    /**
     * Creates a controller backed by the supplied cartridge image.
     *
     * @param rom          cartridge image and header metadata
     * @param ramSizeBytes amount of external RAM exposed by the cartridge
     */
    protected GBCartController(GBRom rom, int ramSizeBytes) {
        this.rom = rom;
        romData = rom.GetData();
        ramData = ramSizeBytes > 0 ? new int[ramSizeBytes] : new int[0];
    }

    /**
     * Creates the correct mapper controller for a ROM header.
     *
     * @param rom cartridge image and header metadata
     * @return mapper controller for the cartridge type
     */
    public static GBCartController Create(GBRom rom) {
        return switch (rom.GetMapperType()) {
            case ROM_ONLY -> new GBCCartROMOnly(rom);
            case MBC1 -> new GBCartMBC1(rom);
            case MBC2 -> new GBCartMBC2(rom);
            case MBC3 -> new GBCartMBC3(rom);
            case MBC5 -> new GBCartMBC5(rom);
            case UNSUPPORTED -> throw new IllegalArgumentException(
                    "Unsupported cartridge type: 0x" + String.format("%02X", rom.GetCartridgeTypeCode()));
        };
    }

    /**
     * Reads a byte from cartridge ROM space.
     *
     * @param address CPU address in the cartridge ROM range
     * @return mapped ROM byte
     */
    public abstract int ReadRom(int address);

    /**
     * Reads a byte from cartridge external RAM space.
     *
     * @param address address offset within the external RAM window
     * @return mapped RAM byte
     */
    public int ReadRam(int address) {
        return 0xFF;
    }

    /**
     * Handles a write to cartridge-controlled address space.
     *
     * @param address CPU address being written
     * @param value   byte value being written
     */
    public abstract void Write(int address, int value);

    /**
     * Reads a byte from a ROM bank, clamping out-of-range addresses to
     * {@code 0xFF}.
     *
     * @param bank              bank number to read
     * @param addressWithinBank offset within the selected bank
     * @return ROM byte
     */
    protected int ReadRomBank(int bank, int addressWithinBank) {
        int bankCount = Math.max(1, rom.GetEffectiveRomBankCount());
        int normalisedBank = Math.floorMod(bank, bankCount);
        int index = (normalisedBank * 0x4000) + addressWithinBank;
        if (index < 0 || index >= romData.length) {
            return 0xFF;
        }
        return romData[index] & 0xFF;
    }

    /**
     * Reads a byte from an external RAM bank.
     *
     * @param bank              RAM bank number
     * @param addressWithinBank offset within the selected bank
     * @return RAM byte
     */
    protected int ReadRamBank(int bank, int addressWithinBank) {
        if (ramData.length == 0) {
            return 0xFF;
        }

        int bankSize = 0x2000;
        int bankCount = Math.max(1, ramData.length / bankSize);
        int normalisedBank = Math.floorMod(bank, bankCount);
        int index = (normalisedBank * bankSize) + addressWithinBank;
        if (index < 0 || index >= ramData.length) {
            return 0xFF;
        }
        return ramData[index] & 0xFF;
    }

    /**
     * Writes a byte into an external RAM bank.
     *
     * @param bank              RAM bank number
     * @param addressWithinBank offset within the selected bank
     * @param value             byte value to store
     */
    protected void WriteRamBank(int bank, int addressWithinBank, int value) {
        if (ramData.length == 0) {
            return;
        }

        int bankSize = 0x2000;
        int bankCount = Math.max(1, ramData.length / bankSize);
        int normalisedBank = Math.floorMod(bank, bankCount);
        int index = (normalisedBank * bankSize) + addressWithinBank;
        if (index < 0 || index >= ramData.length) {
            return;
        }
        ramData[index] = value & 0xFF;
    }

    /**
     * Returns whether the cartridge exposes any external RAM.
     *
     * @return {@code true} if RAM exists
     */
    protected boolean HasRam() {
        return ramData.length > 0;
    }

    /**
     * Returns whether the cartridge should persist RAM to disk.
     *
     * @return {@code true} when save files are supported
     */
    public boolean SupportsSaveData() {
        return rom.HasBatteryBackedSave() && (HasRam() || HasSupplementalSaveData());
    }

    /**
     * Returns a raw copy of the cartridge RAM for persistence.
     *
     * @return raw save RAM bytes
     */
    public byte[] ExportSaveData() {
        byte[] saveData = new byte[ramData.length];
        for (int index = 0; index < ramData.length; index++) {
            saveData[index] = (byte) (ramData[index] & 0xFF);
        }
        return saveData;
    }

    /**
     * Returns a raw copy of any supplementary cartridge persistence data.
     *
     * @return supplementary save bytes
     */
    public byte[] ExportSupplementalSaveData() {
        return new byte[0];
    }

    /**
     * Replaces cartridge RAM from persisted save bytes.
     *
     * @param saveData raw save RAM bytes
     */
    public void LoadSaveData(byte[] saveData) {
        Arrays.fill(ramData, 0);
        if (saveData == null) {
            return;
        }
        int limit = Math.min(ramData.length, saveData.length);
        for (int index = 0; index < limit; index++) {
            ramData[index] = saveData[index] & 0xFF;
        }
    }

    /**
     * Replaces supplementary cartridge persistence data from raw bytes.
     *
     * @param saveData supplementary save bytes
     */
    public void LoadSupplementalSaveData(byte[] saveData) {
    }

    /**
     * Captures mapper registers and external RAM for quick-state persistence.
     *
     * @return mapper snapshot
     */
    public MapperState CaptureState() {
        return new MapperState(
                getClass().getSimpleName(),
                ExportSaveData(),
                CaptureRegisters(),
                ExportSupplementalSaveData());
    }

    /**
     * Restores mapper registers and external RAM from a quick-state snapshot.
     *
     * @param state mapper snapshot
     */
    public void RestoreState(MapperState state) {
        if (state == null) {
            throw new IllegalArgumentException("A cartridge quick state is required.");
        }
        if (!getClass().getSimpleName().equals(state.mapperType())) {
            throw new IllegalArgumentException("Quick state mapper does not match the loaded cartridge.");
        }

        LoadSaveData(state.ramData());
        RestoreRegisters(state.registers());
        LoadSupplementalSaveData(state.supplementalData());
    }

    protected boolean HasSupplementalSaveData() {
        return false;
    }

    protected int[] CaptureRegisters() {
        return new int[0];
    }

    protected void RestoreRegisters(int[] registers) {
    }
}
