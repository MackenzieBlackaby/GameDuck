package com.blackaby.Backend.GB.Memory;

import com.blackaby.Backend.GB.Misc.GBRom;

/**
 * Mapper for MBC2 cartridges, including their built-in 512 x 4-bit RAM.
 */
final class GBCartMBC2 extends GBCartController {

    private boolean ramEnabled;
    private int romBank = 1;

    GBCartMBC2(GBRom rom) {
        super(rom, 0x200);
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
        if (!ramEnabled) {
            return 0xFF;
        }
        int index = address & 0x01FF;
        return 0xF0 | (ramData[index] & 0x0F);
    }

    @Override
    public void Write(int address, int value) {
        if (address <= 0x3FFF) {
            if ((address & 0x0100) == 0) {
                ramEnabled = (value & 0x0F) == 0x0A;
            } else {
                romBank = value & 0x0F;
                if (romBank == 0) {
                    romBank = 1;
                }
            }
            return;
        }

        if (address >= GBMemAddresses.EXTERNAL_RAM_START && address <= GBMemAddresses.EXTERNAL_RAM_END && ramEnabled) {
            ramData[address & 0x01FF] = value & 0x0F;
        }
    }

    @Override
    protected int[] CaptureRegisters() {
        return new int[] { ramEnabled ? 1 : 0, romBank };
    }

    @Override
    protected void RestoreRegisters(int[] registers) {
        if (registers == null || registers.length < 2) {
            throw new IllegalArgumentException("The MBC2 quick state is invalid.");
        }

        ramEnabled = registers[0] != 0;
        romBank = registers[1] & 0x0F;
        if (romBank == 0) {
            romBank = 1;
        }
    }
}
