package com.blackaby.Backend.Emulation.Peripherals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.Memory.DuckAddresses;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;
import com.blackaby.Backend.Emulation.TestSupport.EmulatorTestUtils;

class DuckJoypadTest {

    @Test
    void exposesPressedButtonsThroughSelectedRows() {
        DuckJoypad joypad = new DuckJoypad();
        joypad.SetButtonPressed(DuckJoypad.Button.LEFT, true);
        joypad.SetButtonPressed(DuckJoypad.Button.A, true);

        joypad.WriteRegister(0x20);
        assertEquals(0xED, joypad.ReadRegister());

        joypad.WriteRegister(0x10);
        assertEquals(0xDE, joypad.ReadRegister());
    }

    @Test
    void requestsInterruptOnNewFallingEdgeOnly() {
        DuckMemory memory = new DuckMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "joypad.gb", "joypad"), false);
        DuckCPU cpu = new DuckCPU(memory, null, EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "joypad.gb", "joypad"));
        memory.SetCpu(cpu);
        DuckJoypad joypad = new DuckJoypad(cpu);

        joypad.WriteRegister(0x10);
        joypad.SetButtonPressed(DuckJoypad.Button.A, true);
        assertEquals(DuckCPU.Interrupt.JOYPAD.GetMask(), memory.Read(DuckAddresses.INTERRUPT_FLAG) & 0x10);

        memory.Write(DuckAddresses.INTERRUPT_FLAG, 0x00);
        joypad.SetButtonPressed(DuckJoypad.Button.A, true);
        assertEquals(0x00, memory.Read(DuckAddresses.INTERRUPT_FLAG) & 0x10);
    }
}
