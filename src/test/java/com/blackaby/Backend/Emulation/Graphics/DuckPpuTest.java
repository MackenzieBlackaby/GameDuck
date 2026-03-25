package com.blackaby.Backend.Emulation.Graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.Memory.DuckAddresses;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;
import com.blackaby.Backend.Emulation.TestSupport.EmulatorTestUtils;
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
}
