package com.blackaby.Backend.GB.Memory;

import com.blackaby.Backend.GB.Misc.GBRom;

/**
 * Mapper for ROM-only cartridges, with optional unbanked external RAM.
 */
final class GBCCartROMOnly extends GBCartController {

    GBCCartROMOnly(GBRom rom) {
        super(rom, rom.GetExternalRamSizeBytes());
    }

    @Override
    public int ReadRom(int address) {
        return ReadRomBank(address / 0x4000, address & 0x3FFF);
    }

    @Override
    public int ReadRam(int address) {
        if (!HasRam()) {
            return 0xFF;
        }
        return ReadRamBank(0, address & 0x1FFF);
    }

    @Override
    public void Write(int address, int value) {
        if (address >= GBMemAddresses.EXTERNAL_RAM_START && address <= GBMemAddresses.EXTERNAL_RAM_END && HasRam()) {
            WriteRamBank(0, address - GBMemAddresses.EXTERNAL_RAM_START, value);
        }
    }
}
