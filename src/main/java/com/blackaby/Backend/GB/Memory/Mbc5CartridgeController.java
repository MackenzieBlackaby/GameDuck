package com.blackaby.Backend.GB.Memory;

import com.blackaby.Backend.GB.Misc.ROM;

/**
 * Mapper for MBC5 cartridges with 9-bit ROM bank selection.
 */
final class Mbc5CartridgeController extends CartridgeController {

    private boolean ramEnabled;
    private int romBank = 1;
    private int ramBank;

    Mbc5CartridgeController(ROM rom) {
        super(rom, rom.GetExternalRamSizeBytes());
    }

    @Override
    public int ReadRom(int address) {
        if (address < 0x4000) {
            return ReadRomBank(0, address & 0x3FFF);
        }
        return ReadRomBank(romBank, address - 0x4000);
    }

    @Override
    public int ReadRam(int address) {
        if (!ramEnabled || !HasRam()) {
            return 0xFF;
        }
        return ReadRamBank(ramBank, address & 0x1FFF);
    }

    @Override
    public void Write(int address, int value) {
        if (address <= 0x1FFF) {
            ramEnabled = (value & 0x0F) == 0x0A;
            return;
        }
        if (address <= 0x2FFF) {
            romBank = (romBank & 0x100) | value;
            return;
        }
        if (address <= 0x3FFF) {
            romBank = ((value & 0x01) << 8) | (romBank & 0xFF);
            return;
        }
        if (address <= 0x5FFF) {
            ramBank = value & 0x0F;
            return;
        }
        if (address >= DuckAddresses.EXTERNAL_RAM_START && address <= DuckAddresses.EXTERNAL_RAM_END
                && ramEnabled && HasRam()) {
            WriteRamBank(ramBank, address - DuckAddresses.EXTERNAL_RAM_START, value);
        }
    }

    @Override
    protected int[] CaptureRegisters() {
        return new int[] { ramEnabled ? 1 : 0, romBank, ramBank };
    }

    @Override
    protected void RestoreRegisters(int[] registers) {
        if (registers == null || registers.length < 3) {
            throw new IllegalArgumentException("The MBC5 quick state is invalid.");
        }

        ramEnabled = registers[0] != 0;
        romBank = registers[1] & 0x1FF;
        ramBank = registers[2] & 0x0F;
    }
}

