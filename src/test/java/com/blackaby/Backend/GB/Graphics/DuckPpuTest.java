package com.blackaby.Backend.GB.Graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.blackaby.Backend.GB.CPU.DuckCPU;
import com.blackaby.Backend.GB.Memory.DuckAddresses;
import com.blackaby.Backend.GB.Memory.DuckMemory;
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
        DuckMemory memory = new DuckMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"), false);
        DuckCPU cpu = new DuckCPU(memory, null, EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"));
        memory.SetCpu(cpu);
        memory.InitialiseDmgBootState();
        memory.Write(DuckAddresses.BGP, 0xE4);
        memory.Write(0x9800, 0x00);
        memory.Write(0x8000, 0x80);
        memory.Write(0x8001, 0x00);

        DuckDisplay display = new DuckDisplay();
        DuckPPU ppu = new DuckPPU(cpu, memory, display);

        for (int index = 0; index < DuckPPU.oamDuration + DuckPPU.vramDuration; index++) {
            ppu.Step();
        }

        DuckDisplay.FrameState frame = display.SnapshotFrameState();
        assertEquals(Settings.gbColour1Object.ToRgb(), frame.backBuffer()[0]);
        assertEquals(Settings.gbColour0Object.ToRgb(), frame.backBuffer()[1]);
    }

    @Test
    void requestsVblankInterruptAtEndOfVisibleFrame() {
        DuckMemory memory = new DuckMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"), false);
        DuckCPU cpu = new DuckCPU(memory, null, EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"));
        memory.SetCpu(cpu);
        memory.InitialiseDmgBootState();
        DuckPPU ppu = new DuckPPU(cpu, memory, new DuckDisplay());

        for (int index = 0; index < 456 * 144; index++) {
            ppu.Step();
        }

        assertEquals(144, memory.Read(DuckAddresses.LY));
        assertEquals(DuckCPU.Interrupt.VBLANK.GetMask(), memory.Read(DuckAddresses.INTERRUPT_FLAG) & 0x01);
        assertEquals(1, ppu.ConsumeCompletedFrames());
    }

    @Test
    void midScanlineScrollChangesAffectLaterPixels() {
        DuckMemory memory = new DuckMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"), false);
        DuckCPU cpu = new DuckCPU(memory, null, EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"));
        memory.SetCpu(cpu);
        memory.InitialiseDmgBootState();
        memory.Write(DuckAddresses.BGP, 0xE4);
        memory.Write(0x9800, 0x00);
        memory.Write(0x9801, 0x01);
        memory.Write(0x8000, 0x00);
        memory.Write(0x8001, 0x00);
        memory.Write(0x8010, 0xFF);
        memory.Write(0x8011, 0x00);
        memory.Write(DuckAddresses.SCX, 0x00);

        DuckDisplay display = new DuckDisplay();
        DuckPPU ppu = new DuckPPU(cpu, memory, display);

        for (int index = 0; index < DuckPPU.oamDuration + 1; index++) {
            ppu.Step();
        }
        memory.Write(DuckAddresses.SCX, 0x08);
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
        DuckMemory memory = new DuckMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"), false);
        DuckCPU cpu = new DuckCPU(memory, null, EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "ppu.gb", "ppu"));
        memory.SetCpu(cpu);
        memory.InitialiseDmgBootState();
        memory.Write(DuckAddresses.INTERRUPT_FLAG, 0x00);
        memory.Write(DuckAddresses.STAT, 0x40);
        memory.Write(DuckAddresses.LYC, 0x01);

        DuckPPU ppu = new DuckPPU(cpu, memory, new DuckDisplay());

        for (int index = 0; index < 456; index++) {
            ppu.Step();
        }

        assertEquals(1, memory.Read(DuckAddresses.LY));
        memory.Write(DuckAddresses.INTERRUPT_FLAG, 0x00);
        memory.Write(DuckAddresses.LYC, 0x02);
        ppu.Step();
        memory.Write(DuckAddresses.INTERRUPT_FLAG, 0x00);
        memory.Write(DuckAddresses.LYC, 0x01);

        ppu.Step();

        assertEquals(0x04, memory.Read(DuckAddresses.STAT) & 0x04);
        assertEquals(DuckCPU.Interrupt.LCD_STAT.GetMask(), memory.Read(DuckAddresses.INTERRUPT_FLAG) & 0x02);
    }

    @Test
    void rendersPureWhiteCgbSpritePixelsInsteadOfTreatingThemAsTransparent() {
        DuckMemory memory = new DuckMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x80, "ppu.gbc", "ppu"), true);
        DuckCPU cpu = new DuckCPU(memory, null, EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x80, "ppu.gbc", "ppu"));
        memory.SetCpu(cpu);
        memory.InitialiseCgbBootState();

        memory.Write(DuckAddresses.BCPS, 0x80);
        memory.Write(DuckAddresses.BCPD, 0x00);
        memory.Write(DuckAddresses.BCPD, 0x00);

        memory.Write(0x8000, 0x80);
        memory.Write(0x8001, 0x00);
        memory.Write(0xFE00, 0x10);
        memory.Write(0xFE01, 0x08);
        memory.Write(0xFE02, 0x00);
        memory.Write(0xFE03, 0x00);

        DuckDisplay display = new DuckDisplay();
        DuckPPU ppu = new DuckPPU(cpu, memory, display);

        for (int index = 0; index < DuckPPU.oamDuration + DuckPPU.vramDuration; index++) {
            ppu.Step();
        }

        DuckDisplay.FrameState frame = display.SnapshotFrameState();
        assertEquals(0xFFFFFFFF, frame.backBuffer()[0]);
    }
}
