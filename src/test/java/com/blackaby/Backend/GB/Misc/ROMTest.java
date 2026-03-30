package com.blackaby.Backend.GB.Misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.blackaby.Backend.GB.Memory.GBMapperTypes;
import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;

class ROMTest {

    @Test
    void parsesMapperFlagsAndSizesFromHeader() {
        GBRom rom = EmulatorTestUtils.CreateBlankRom(0x1B, 32, 0x03, 0xC0, "pokemon.gbc", "Pokemon Crystal");

        assertEquals(GBMapperTypes.MBC5, rom.GetMapperType());
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
        GBRom rom = EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00,
                "C:\\roms\\Super Mario Bros Deluxe.gbc", "Mario DX");

        assertEquals("Super Mario Bros Deluxe", rom.GetSourceName());
        assertEquals("Mario DX", rom.GetName());
        assertEquals("C:\\roms\\Super Mario Bros Deluxe.gbc", rom.GetSourcePath());
    }
}
