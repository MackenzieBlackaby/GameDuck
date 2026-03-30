package com.blackaby.Backend.GB.Peripherals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.blackaby.Backend.GB.CPU.GBProcessor;
import com.blackaby.Backend.GB.Memory.GBMemAddresses;
import com.blackaby.Backend.GB.Memory.GBMemory;
import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;

class DuckTimerTest {

    @Test
    void overflowReloadsAfterFourTicksAndRequestsInterrupt() {
        GBMemory memory = new GBMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "timer.gb", "timer"), false);
        GBProcessor cpu = new GBProcessor(memory, null,
                EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "timer.gb", "timer"));
        memory.SetCpu(cpu);
        GBTimerSet timer = new GBTimerSet(cpu, memory);

        memory.Write(GBMemAddresses.TAC, 0x05);
        memory.Write(GBMemAddresses.TMA, 0xAB);
        memory.Write(GBMemAddresses.TIMA, 0xFF);
        timer.RestoreState(new GBTimerSet.TimerState(0x000F, true, 0, false));

        timer.Tick();
        assertEquals(0x00, memory.Read(GBMemAddresses.TIMA));

        timer.Tick();
        timer.Tick();
        timer.Tick();
        timer.Tick();

        assertEquals(0xAB, memory.Read(GBMemAddresses.TIMA));
        assertEquals(GBProcessor.Interrupt.TIMER.GetMask(), memory.Read(GBMemAddresses.INTERRUPT_FLAG) & 0x04);
    }

    @Test
    void resetDivAppliesFallingEdgeIncrementGlitch() {
        GBMemory memory = new GBMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "timer.gb", "timer"), false);
        GBProcessor cpu = new GBProcessor(memory, null,
                EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "timer.gb", "timer"));
        memory.SetCpu(cpu);
        GBTimerSet timer = new GBTimerSet(cpu, memory);

        memory.Write(GBMemAddresses.TAC, 0x05);
        memory.Write(GBMemAddresses.TIMA, 0x10);
        timer.RestoreState(new GBTimerSet.TimerState(0x0008, true, 0, false));

        timer.ResetDiv();

        assertEquals(0x11, memory.Read(GBMemAddresses.TIMA));
        assertEquals(0x00, memory.Read(GBMemAddresses.DIV));
    }

    @Test
    void writingTacCanCauseFallingEdgeIncrement() {
        GBMemory memory = new GBMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "timer.gb", "timer"), false);
        GBProcessor cpu = new GBProcessor(memory, null,
                EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "timer.gb", "timer"));
        memory.SetCpu(cpu);
        GBTimerSet timer = new GBTimerSet(cpu, memory);

        memory.Write(GBMemAddresses.TAC, 0x05);
        memory.Write(GBMemAddresses.TIMA, 0x20);
        timer.RestoreState(new GBTimerSet.TimerState(0x0008, true, 0, false));

        timer.WriteTac(0x04);

        assertEquals(0x21, memory.Read(GBMemAddresses.TIMA));
    }
}
