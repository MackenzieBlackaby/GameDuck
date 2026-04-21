package com.blackaby.Backend.GB.Graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.blackaby.Backend.GB.CPU.GBProcessor;
import com.blackaby.Backend.GB.Memory.GBMemAddresses;
import com.blackaby.Backend.GB.Memory.GBMemory;
import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;
import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Misc.Settings;

class DuckPpuTest {

    @BeforeEach
    void resetSettings() {
        Settings.Reset();
    }

    @Test
    void rendersBackgroundPixelsFromTileData() {
        GBMemory memory = new GBMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"), false);
        GBProcessor cpu = new GBProcessor(memory, null,
                EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"));
        memory.SetCpu(cpu);
        memory.InitialiseDmgBootState();
        memory.Write(GBMemAddresses.BGP, 0xE4);
        memory.Write(0x9800, 0x00);
        memory.Write(0x8000, 0x80);
        memory.Write(0x8001, 0x00);

        DuckDisplay display = new DuckDisplay();
        GBPPU ppu = new GBPPU(cpu, memory, display);

        for (int index = 0; index < GBPPU.oamDuration + GBPPU.vramDuration; index++) {
            ppu.Step();
        }

        DuckDisplay.FrameState frame = display.SnapshotFrameState();
        assertEquals(Settings.gbColour1Object.ToRgb(), frame.backBuffer()[0]);
        assertEquals(Settings.gbColour0Object.ToRgb(), frame.backBuffer()[1]);
    }

    @Test
    void requestsVblankInterruptAtEndOfVisibleFrame() {
        GBMemory memory = new GBMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"), false);
        GBProcessor cpu = new GBProcessor(memory, null,
                EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"));
        memory.SetCpu(cpu);
        memory.InitialiseDmgBootState();
        GBPPU ppu = new GBPPU(cpu, memory, new DuckDisplay());

        for (int index = 0; index < 456 * 144; index++) {
            ppu.Step();
        }

        assertEquals(144, memory.Read(GBMemAddresses.LY));
        assertEquals(GBProcessor.Interrupt.VBLANK.GetMask(), memory.Read(GBMemAddresses.INTERRUPT_FLAG) & 0x01);
        assertEquals(1, ppu.ConsumeCompletedFrames());
    }

    @Test
    void midScanlineScrollChangesAffectLaterPixels() {
        GBMemory memory = new GBMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"), false);
        GBProcessor cpu = new GBProcessor(memory, null,
                EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"));
        memory.SetCpu(cpu);
        memory.InitialiseDmgBootState();
        memory.Write(GBMemAddresses.BGP, 0xE4);
        memory.Write(0x9800, 0x00);
        memory.Write(0x9801, 0x01);
        memory.Write(0x8000, 0x00);
        memory.Write(0x8001, 0x00);
        memory.Write(0x8010, 0xFF);
        memory.Write(0x8011, 0x00);
        memory.Write(GBMemAddresses.SCX, 0x00);

        DuckDisplay display = new DuckDisplay();
        GBPPU ppu = new GBPPU(cpu, memory, display);

        for (int index = 0; index < GBPPU.oamDuration + 1; index++) {
            ppu.Step();
        }
        memory.Write(GBMemAddresses.SCX, 0x08);
        for (int index = 0; index < 2; index++) {
            ppu.Step();
        }

        DuckDisplay.FrameState frame = display.SnapshotFrameState();
        assertEquals(Settings.gbColour0Object.ToRgb(), frame.backBuffer()[0]);
        assertEquals(Settings.gbColour1Object.ToRgb(), frame.backBuffer()[1]);
        assertEquals(Settings.gbColour1Object.ToRgb(), frame.backBuffer()[2]);
    }

    @Test
    void rewritingLycToCurrentLineTriggersStatWithoutWaitingForNextScanline() {
        GBMemory memory = new GBMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"), false);
        GBProcessor cpu = new GBProcessor(memory, null,
                EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"));
        memory.SetCpu(cpu);
        memory.InitialiseDmgBootState();
        memory.Write(GBMemAddresses.INTERRUPT_FLAG, 0x00);
        memory.Write(GBMemAddresses.STAT, 0x40);
        memory.Write(GBMemAddresses.LYC, 0x01);

        GBPPU ppu = new GBPPU(cpu, memory, new DuckDisplay());

        for (int index = 0; index < 456; index++) {
            ppu.Step();
        }

        assertEquals(1, memory.Read(GBMemAddresses.LY));
        memory.Write(GBMemAddresses.INTERRUPT_FLAG, 0x00);
        memory.Write(GBMemAddresses.LYC, 0x02);
        ppu.Step();
        memory.Write(GBMemAddresses.INTERRUPT_FLAG, 0x00);
        memory.Write(GBMemAddresses.LYC, 0x01);

        ppu.Step();

        assertEquals(0x04, memory.Read(GBMemAddresses.STAT) & 0x04);
        assertEquals(GBProcessor.Interrupt.LCD_STAT.GetMask(), memory.Read(GBMemAddresses.INTERRUPT_FLAG) & 0x02);
    }

    @Test
    void rendersPureWhiteCgbSpritePixelsInsteadOfTreatingThemAsTransparent() {
        GBMemory memory = new GBMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x80, "ppu.gbc", "ppu"), true);
        GBProcessor cpu = new GBProcessor(memory, null,
                EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x80, "ppu.gbc", "ppu"));
        memory.SetCpu(cpu);
        memory.InitialiseCgbBootState();

        memory.Write(GBMemAddresses.BCPS, 0x80);
        memory.Write(GBMemAddresses.BCPD, 0x00);
        memory.Write(GBMemAddresses.BCPD, 0x00);

        memory.Write(0x8000, 0x80);
        memory.Write(0x8001, 0x00);
        memory.Write(0xFE00, 0x10);
        memory.Write(0xFE01, 0x08);
        memory.Write(0xFE02, 0x00);
        memory.Write(0xFE03, 0x00);

        DuckDisplay display = new DuckDisplay();
        GBPPU ppu = new GBPPU(cpu, memory, display);

        for (int index = 0; index < GBPPU.oamDuration + GBPPU.vramDuration; index++) {
            ppu.Step();
        }

        DuckDisplay.FrameState frame = display.SnapshotFrameState();
        assertEquals(0xFFFFFFFF, frame.backBuffer()[0]);
    }

    @Test
    void dmgCompatibilityModeUsesObpSelectionWithCgbObjectPalettes() {
        GBMemory memory = new GBMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "compat.gb", "compat"), true);
        GBProcessor cpu = new GBProcessor(memory, null,
                EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "compat.gb", "compat"));
        memory.SetCpu(cpu);
        memory.InitialiseCgbBootState();
        memory.SeedDmgCompatibilityPalettes(
                new GBColor[] {
                        new GBColor(255, 255, 255),
                        new GBColor(255, 255, 255),
                        new GBColor(255, 255, 255),
                        new GBColor(255, 255, 255)
                },
                new GBColor[] {
                        new GBColor(255, 255, 255),
                        new GBColor(0, 0, 0),
                        new GBColor(255, 255, 255),
                        new GBColor(255, 255, 255)
                },
                new GBColor[] {
                        new GBColor(255, 255, 255),
                        new GBColor(0, 0, 255),
                        new GBColor(255, 255, 255),
                        new GBColor(255, 255, 255)
                });

        memory.Write(GBMemAddresses.LCDC, memory.Read(GBMemAddresses.LCDC) | 0x02);
        memory.Write(GBMemAddresses.OBP1, 0xE4);
        memory.Write(0x8000, 0x80);
        memory.Write(0x8001, 0x00);
        memory.Write(0xFE00, 0x10);
        memory.Write(0xFE01, 0x08);
        memory.Write(0xFE02, 0x00);
        memory.Write(0xFE03, 0x10);

        DuckDisplay display = new DuckDisplay();
        GBPPU ppu = new GBPPU(cpu, memory, display);

        for (int index = 0; index < GBPPU.oamDuration + GBPPU.vramDuration; index++) {
            ppu.Step();
        }

        DuckDisplay.FrameState frame = display.SnapshotFrameState();
        assertEquals(memory.ReadCgbObjectPaletteColourRgb(1, 1), frame.backBuffer()[0]);
    }
}
