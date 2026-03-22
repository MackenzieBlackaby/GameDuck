package com.blackaby.Backend.Emulation.Memory;

import com.blackaby.Backend.Emulation.Misc.ROM;

/**
 * Mapper for MBC1 cartridges with ROM banking and optional RAM banking.
 */
final class Mbc1CartridgeController extends CartridgeController {

    private boolean ramEnabled;
    private int romBankLow5 = 1;
    private int upperBankBits;
    private boolean ramBankingMode;

    Mbc1CartridgeController(ROM rom) {
        super(rom, rom.GetExternalRamSizeBytes());
    }

    @Override
    public int ReadRom(int address) {
        if (address < 0x4000) {
            int bank = ramBankingMode ? (upperBankBits << 5) : 0;
            return ReadRomBank(bank, address & 0x3FFF);
        }

        int bank = (upperBankBits << 5) | romBankLow5;
        if ((bank & 0x1F) == 0) {
            bank++;
        }
        return ReadRomBank(bank, address - 0x4000);
    }

    @Override
    public int ReadRam(int address) {
        if (!ramEnabled || !HasRam()) {
            return 0xFF;
        }
        int bank = ramBankingMode ? upperBankBits : 0;
        return ReadRamBank(bank, address & 0x1FFF);
    }

    @Override
    public void Write(int address, int value) {
        if (address <= 0x1FFF) {
            ramEnabled = (value & 0x0F) == 0x0A;
            return;
        }
        if (address <= 0x3FFF) {
            romBankLow5 = value & 0x1F;
            if (romBankLow5 == 0) {
                romBankLow5 = 1;
            }
            return;
        }
        if (address <= 0x5FFF) {
            upperBankBits = value & 0x03;
            return;
        }
        if (address <= 0x7FFF) {
            ramBankingMode = (value & 0x01) == 0x01;
            return;
        }
        if (address >= DuckAddresses.EXTERNAL_RAM_START && address <= DuckAddresses.EXTERNAL_RAM_END
                && ramEnabled && HasRam()) {
            int bank = ramBankingMode ? upperBankBits : 0;
            WriteRamBank(bank, address - DuckAddresses.EXTERNAL_RAM_START, value);
        }
    }

    @Override
    protected int[] CaptureRegisters() {
        return new int[] {
                ramEnabled ? 1 : 0,
                romBankLow5,
                upperBankBits,
                ramBankingMode ? 1 : 0
        };
    }

    @Override
    protected void RestoreRegisters(int[] registers) {
        if (registers == null || registers.length < 4) {
            throw new IllegalArgumentException("The MBC1 quick state is invalid.");
        }

        ramEnabled = registers[0] != 0;
        romBankLow5 = registers[1] & 0x1F;
        if (romBankLow5 == 0) {
            romBankLow5 = 1;
        }
        upperBankBits = registers[2] & 0x03;
        ramBankingMode = registers[3] != 0;
    }
}
