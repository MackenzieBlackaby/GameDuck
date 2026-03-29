package com.blackaby.Backend.GB.Peripherals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.blackaby.Backend.GB.CPU.DuckCPU;
import com.blackaby.Backend.GB.Memory.DuckAddresses;
import com.blackaby.Backend.GB.Memory.DuckMemory;
import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;

class DuckTimerTest {

    @Test
    void overflowReloadsAfterFourTicksAndRequestsInterrupt() {
        DuckMemory memory = new DuckMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "timer.gb", "timer"), false);
        DuckCPU cpu = new DuckCPU(memory, null, EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "timer.gb", "timer"));
        memory.SetCpu(cpu);
        DuckTimer timer = new DuckTimer(cpu, memory);

        memory.Write(DuckAddresses.TAC, 0x05);
        memory.Write(DuckAddresses.TMA, 0xAB);
        memory.Write(DuckAddresses.TIMA, 0xFF);
        timer.RestoreState(new DuckTimer.TimerState(0x000F, true, 0, false));

        timer.Tick();
        assertEquals(0x00, memory.Read(DuckAddresses.TIMA));

        timer.Tick();
        timer.Tick();
        timer.Tick();
        timer.Tick();

        assertEquals(0xAB, memory.Read(DuckAddresses.TIMA));
        assertEquals(DuckCPU.Interrupt.TIMER.GetMask(), memory.Read(DuckAddresses.INTERRUPT_FLAG) & 0x04);
    }

    @Test
    void resetDivAppliesFallingEdgeIncrementGlitch() {
        DuckMemory memory = new DuckMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "timer.gb", "timer"), false);
        DuckCPU cpu = new DuckCPU(memory, null, EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "timer.gb", "timer"));
        memory.SetCpu(cpu);
        DuckTimer timer = new DuckTimer(cpu, memory);

        memory.Write(DuckAddresses.TAC, 0x05);
        memory.Write(DuckAddresses.TIMA, 0x10);
        timer.RestoreState(new DuckTimer.TimerState(0x0008, true, 0, false));

        timer.ResetDiv();

        assertEquals(0x11, memory.Read(DuckAddresses.TIMA));
        assertEquals(0x00, memory.Read(DuckAddresses.DIV));
    }

    @Test
    void writingTacCanCauseFallingEdgeIncrement() {
        DuckMemory memory = new DuckMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "timer.gb", "timer"), false);
        DuckCPU cpu = new DuckCPU(memory, null, EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "timer.gb", "timer"));
        memory.SetCpu(cpu);
        DuckTimer timer = new DuckTimer(cpu, memory);

        memory.Write(DuckAddresses.TAC, 0x05);
        memory.Write(DuckAddresses.TIMA, 0x20);
        timer.RestoreState(new DuckTimer.TimerState(0x0008, true, 0, false));

        timer.WriteTac(0x04);

        assertEquals(0x21, memory.Read(DuckAddresses.TIMA));
    }
}
