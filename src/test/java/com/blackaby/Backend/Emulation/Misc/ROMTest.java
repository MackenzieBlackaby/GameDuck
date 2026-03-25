package com.blackaby.Backend.Emulation.Misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.blackaby.Backend.Emulation.Memory.CartridgeMapperType;
import com.blackaby.Backend.Emulation.TestSupport.EmulatorTestUtils;

class ROMTest {

    @Test
    void parsesMapperFlagsAndSizesFromHeader() {
        ROM rom = EmulatorTestUtils.CreateBlankRom(0x1B, 32, 0x03, 0xC0, "pokemon.gbc", "Pokemon Crystal");

        assertEquals(CartridgeMapperType.MBC5, rom.GetMapperType());
        assertEquals(32, rom.GetDeclaredRomBankCount());
        assertEquals(32, rom.GetEffectiveRomBankCount());
        assertEquals(0x8000, rom.GetExternalRamSizeBytes());
        assertTrue(rom.HasBatteryBackedSave());
        assertTrue(rom.IsCgbCompatible());
        assertTrue(rom.IsCgbOnly());
        assertEquals("TEST", rom.GetHeaderTitle());
    }

    @Test
    void keepsSourceNameSeparateFromDisplayName() {
        ROM rom = EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00,
                "C:\\roms\\Super Mario Bros Deluxe.gbc", "Mario DX");

        assertEquals("Super Mario Bros Deluxe", rom.GetSourceName());
        assertEquals("Mario DX", rom.GetName());
        assertEquals("C:\\roms\\Super Mario Bros Deluxe.gbc", rom.GetSourcePath());
    }
}
