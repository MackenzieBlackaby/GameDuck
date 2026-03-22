package com.blackaby.Backend.Emulation.Memory;

import com.blackaby.Backend.Emulation.Misc.ROM;

/**
 * Mapper for MBC3 cartridges.
 * <p>
 * ROM banking and RAM banking are implemented here. RTC register selection is
 * recognised, though the clock itself is still not modelled.
 */
final class Mbc3CartridgeController extends CartridgeController {

    private boolean ramEnabled;
    private int romBank = 1;
    private int ramBankOrRtcRegister;

    Mbc3CartridgeController(ROM rom) {
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
        if (!ramEnabled) {
            return 0xFF;
        }
        if (ramBankOrRtcRegister >= 0x08) {
            return 0xFF;
        }
        return ReadRamBank(ramBankOrRtcRegister, address & 0x1FFF);
    }

    @Override
    public void Write(int address, int value) {
        if (address <= 0x1FFF) {
            ramEnabled = (value & 0x0F) == 0x0A;
            return;
        }
        if (address <= 0x3FFF) {
            romBank = value & 0x7F;
            if (romBank == 0) {
                romBank = 1;
            }
            return;
        }
        if (address <= 0x5FFF) {
            ramBankOrRtcRegister = value & 0x0F;
            return;
        }
        if (address <= 0x7FFF) {
            return;
        }

        if (address >= DuckAddresses.EXTERNAL_RAM_START && address <= DuckAddresses.EXTERNAL_RAM_END
                && ramEnabled && ramBankOrRtcRegister < 0x04) {
            WriteRamBank(ramBankOrRtcRegister, address - DuckAddresses.EXTERNAL_RAM_START, value);
        }
    }

    @Override
    protected int[] CaptureRegisters() {
        return new int[] { ramEnabled ? 1 : 0, romBank, ramBankOrRtcRegister };
    }

    @Override
    protected void RestoreRegisters(int[] registers) {
        if (registers == null || registers.length < 3) {
            throw new IllegalArgumentException("The MBC3 quick state is invalid.");
        }

        ramEnabled = registers[0] != 0;
        romBank = registers[1] & 0x7F;
        if (romBank == 0) {
            romBank = 1;
        }
        ramBankOrRtcRegister = registers[2] & 0x0F;
    }
}
