package com.blackaby.Backend.GB.Memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;
import com.blackaby.Backend.GB.Graphics.GBColor;

class DuckMemoryTest {

    @Test
    void hblankDmaTransfersOneBlockPerWindow() {
        GBMemory memory = CreateCgbMemory();
        WriteSequential(memory, 0xC000, 0x20, 0x10);

        memory.Write(GBMemAddresses.HDMA1, 0xC0);
        memory.Write(GBMemAddresses.HDMA2, 0x00);
        memory.Write(GBMemAddresses.HDMA3, 0x80);
        memory.Write(GBMemAddresses.HDMA4, 0x00);
        memory.Write(GBMemAddresses.HDMA5, 0x81);

        assertEquals(0x01, memory.Read(GBMemAddresses.HDMA5));
        assertEquals(0x00, memory.Read(0x8000));

        memory.TickHdma(false);
        assertEquals(0x00, memory.Read(0x8000));

        memory.TickHdma(true);
        AssertSequential(memory, 0x8000, 0x10, 0x10);
        assertEquals(0x00, memory.Read(GBMemAddresses.HDMA5));
        assertEquals(0x00, memory.Read(0x8010));

        memory.TickHdma(true);
        assertEquals(0x00, memory.Read(0x8010));

        memory.TickHdma(false);
        memory.TickHdma(true);
        AssertSequential(memory, 0x8010, 0x10, 0x20);
        assertEquals(0xFF, memory.Read(GBMemAddresses.HDMA5));
    }

    @Test
    void writingBitSevenClearCancelsActiveHblankDma() {
        GBMemory memory = CreateCgbMemory();
        WriteSequential(memory, 0xC000, 0x30, 0x40);

        memory.Write(GBMemAddresses.HDMA1, 0xC0);
        memory.Write(GBMemAddresses.HDMA2, 0x00);
        memory.Write(GBMemAddresses.HDMA3, 0x80);
        memory.Write(GBMemAddresses.HDMA4, 0x00);
        memory.Write(GBMemAddresses.HDMA5, 0x82);

        memory.TickHdma(true);
        AssertSequential(memory, 0x8000, 0x10, 0x40);
        assertEquals(0x01, memory.Read(GBMemAddresses.HDMA5));

        memory.Write(GBMemAddresses.HDMA5, 0x00);
        assertEquals(0x81, memory.Read(GBMemAddresses.HDMA5));

        memory.TickHdma(false);
        memory.TickHdma(true);
        assertEquals(0x00, memory.Read(0x8010));
        assertEquals(0x81, memory.Read(GBMemAddresses.HDMA5));
    }

    @Test
    void generalPurposeDmaCopiesAllBlocksImmediately() {
        GBMemory memory = CreateCgbMemory();
        WriteSequential(memory, 0xC000, 0x20, 0x70);

        memory.Write(GBMemAddresses.HDMA1, 0xC0);
        memory.Write(GBMemAddresses.HDMA2, 0x00);
        memory.Write(GBMemAddresses.HDMA3, 0x80);
        memory.Write(GBMemAddresses.HDMA4, 0x00);
        memory.Write(GBMemAddresses.HDMA5, 0x01);

        AssertSequential(memory, 0x8000, 0x20, 0x70);
        assertEquals(0xFF, memory.Read(GBMemAddresses.HDMA5));
    }

    @Test
    void rtcOnlyMbc3CartridgeStillExposesManagedSaveSupport() {
        byte[] romBytes = new byte[0x8000];
        romBytes[0x0147] = 0x0F;
        romBytes[0x0148] = 0x00;
        romBytes[0x0149] = 0x00;

        GBMemory memory = new GBMemory();
        memory.LoadRom(GBRom.FromBytes("rtc.gb", romBytes, "rtc"), false);

        assertEquals(true, memory.HasSaveData());
    }

    @Test
    void dmgRomCanRunInCgbCompatibilityModeWhenRequested() {
        GBMemory memory = new GBMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "compat.gb", "compat"), true);

        assertTrue(memory.IsCgbMode());
        assertTrue(memory.IsDmgCompatibilityMode());
        assertFalse(memory.IsLoadedRomCgbCompatible());
    }

    @Test
    void seedsDmgCompatibilityPalettesIntoCgbPaletteRam() {
        GBMemory memory = new GBMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "compat.gb", "compat"), true);
        memory.InitialiseCgbBootState();

        memory.SeedDmgCompatibilityPalettes(
                new GBColor[] {
                        new GBColor(0, 0, 0),
                        new GBColor(255, 0, 0),
                        new GBColor(0, 255, 0),
                        new GBColor(0, 0, 255)
                },
                new GBColor[] {
                        new GBColor(0, 0, 0),
                        new GBColor(255, 255, 255),
                        new GBColor(0, 0, 0),
                        new GBColor(0, 0, 0)
                },
                new GBColor[] {
                        new GBColor(0, 0, 0),
                        new GBColor(0, 0, 255),
                        new GBColor(0, 0, 0),
                        new GBColor(0, 0, 0)
                });

        memory.Write(GBMemAddresses.BCPS, 0x80);
        assertEquals(0x00, memory.Read(GBMemAddresses.BCPD));
        memory.Write(GBMemAddresses.BCPS, 0x82);
        assertEquals(0x1F, memory.Read(GBMemAddresses.BCPD));

        memory.Write(GBMemAddresses.OCPS, 0x88);
        assertEquals(0x00, memory.Read(GBMemAddresses.OCPD));
        memory.Write(GBMemAddresses.OCPS, 0x8A);
        assertEquals(0x00, memory.Read(GBMemAddresses.OCPD));
        memory.Write(GBMemAddresses.OCPS, 0x8B);
        assertEquals(0x7C, memory.Read(GBMemAddresses.OCPD));
    }

    @Test
    void dmgCompatibilityModeWaitsUntilCgbBootRomUnmaps() {
        GBMemory memory = new GBMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "compat.gb", "compat"), true);
        memory.LoadBootRom(new byte[0x800], true);

        assertTrue(memory.IsCgbMode());
        assertFalse(memory.IsDmgCompatibilityMode());

        memory.Write(GBMemAddresses.BOOT_ROM_DISABLE, 0x01);

        assertTrue(memory.IsDmgCompatibilityMode());
    }

    private static GBMemory CreateCgbMemory() {
        byte[] romBytes = new byte[0x8000];
        romBytes[0x0143] = (byte) 0x80;
        romBytes[0x0147] = 0x00;
        romBytes[0x0148] = 0x00;
        romBytes[0x0149] = 0x00;

        GBMemory memory = new GBMemory();
        memory.LoadRom(GBRom.FromBytes("test.gbc", romBytes, "test"), true);
        return memory;
    }

    private static void WriteSequential(GBMemory memory, int address, int length, int startValue) {
        for (int index = 0; index < length; index++) {
            memory.Write(address + index, (startValue + index) & 0xFF);
        }
    }

    private static void AssertSequential(GBMemory memory, int address, int length, int startValue) {
        for (int index = 0; index < length; index++) {
            assertEquals((startValue + index) & 0xFF, memory.Read(address + index),
                    "Unexpected value at address 0x" + Integer.toHexString(address + index));
        }
    }
}
