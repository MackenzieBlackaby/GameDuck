package com.blackaby.Backend.GB.Peripherals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Backend.GB.CPU.GBProcessor;
import com.blackaby.Backend.GB.Memory.GBMemAddresses;
import com.blackaby.Backend.GB.Memory.GBMemory;
import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;

class DuckJoypadTest {

    @Test
    void exposesPressedButtonsThroughSelectedRows() {
        GBGamepad joypad = new GBGamepad();
        joypad.SetButtonPressed(GBButton.LEFT, true);
        joypad.SetButtonPressed(GBButton.A, true);

        joypad.WriteRegister(0x20);
        assertEquals(0xED, joypad.ReadRegister());

        joypad.WriteRegister(0x10);
        assertEquals(0xDE, joypad.ReadRegister());
    }

    @Test
    void requestsInterruptOnNewFallingEdgeOnly() {
        GBMemory memory = new GBMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "joypad.gb", "joypad"), false);
        GBProcessor cpu = new GBProcessor(memory, null,
                EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "joypad.gb", "joypad"));
        memory.SetCpu(cpu);
        GBGamepad joypad = new GBGamepad(cpu);

        joypad.WriteRegister(0x10);
        joypad.SetButtonPressed(GBButton.A, true);
        assertEquals(GBProcessor.Interrupt.JOYPAD.GetMask(), memory.Read(GBMemAddresses.INTERRUPT_FLAG) & 0x10);

        memory.Write(GBMemAddresses.INTERRUPT_FLAG, 0x00);
        joypad.SetButtonPressed(GBButton.A, true);
        assertEquals(0x00, memory.Read(GBMemAddresses.INTERRUPT_FLAG) & 0x10);
    }
}
