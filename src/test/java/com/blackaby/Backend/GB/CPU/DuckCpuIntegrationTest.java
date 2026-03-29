package com.blackaby.Backend.GB.CPU;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.blackaby.Backend.GB.Memory.DuckAddresses;
import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;
import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils.CpuHarness;

class DuckCpuIntegrationTest {

    @Test
    void executesLoadAddAndStoreProgram() {
        CpuHarness harness = EmulatorTestUtils.CreateCpuHarness(new byte[] {
                (byte) 0x3E, 0x12,
                0x06, 0x08,
                (byte) 0x80,
                (byte) 0xEA, 0x00, (byte) 0xC0,
                0x76
        });

        for (int step = 0; step < 5; step++) {
            harness.StepInstruction();
        }

        assertEquals(0x1A, harness.cpu.GetAccumulator());
        assertEquals(0x1A, harness.memory.Read(0xC000));
        assertFalse(harness.cpu.GetFlag(DuckCPU.Flag.Z));
        assertFalse(harness.cpu.GetFlag(DuckCPU.Flag.N));
        assertFalse(harness.cpu.GetFlag(DuckCPU.Flag.H));
        assertFalse(harness.cpu.GetFlag(DuckCPU.Flag.C));
        assertTrue(harness.cpu.IsHalted());
    }

    @Test
    void executesCallReturnAndJumpProgram() {
        CpuHarness harness = EmulatorTestUtils.CreateCpuHarness(new byte[] {
                (byte) 0xCD, 0x08, 0x01,
                (byte) 0xC3, 0x0B, 0x01,
                0x00, 0x00,
                (byte) 0x3E, 0x42,
                (byte) 0xC9,
                (byte) 0xEA, 0x00, (byte) 0xC0,
                0x76
        });

        for (int step = 0; step < 6; step++) {
            harness.StepInstruction();
        }

        assertEquals(0x42, harness.memory.Read(0xC000));
        assertEquals(0x010F, harness.cpu.GetPC());
        assertTrue(harness.cpu.IsHalted());
    }

    @Test
    void servicesInterruptAfterInstructionCompletes() {
        CpuHarness harness = EmulatorTestUtils.CreateCpuHarness(new byte[0]);
        byte[] romBytes = harness.rom.ToByteArray();
        romBytes[0x01FE] = 0x00;
        harness = EmulatorTestUtils.CreateCpuHarness(new byte[0]);
        harness.cpu.SetPC(0x01FE);
        harness.cpu.SetSP(0xFFFE);
        harness.memory.Write(DuckAddresses.IE, DuckCPU.Interrupt.VBLANK.GetMask());
        harness.memory.Write(DuckAddresses.INTERRUPT_FLAG, DuckCPU.Interrupt.VBLANK.GetMask());
        harness.cpu.EnableInterruptsImmediately();

        harness.StepInstruction();

        assertEquals(0x0040, harness.cpu.GetPC());
        assertEquals(0xFFFC, harness.cpu.GetSP());
        assertEquals(0xFF, harness.memory.Read(0xFFFC));
        assertEquals(0x01, harness.memory.Read(0xFFFD));
        assertEquals(0x00, harness.memory.Read(DuckAddresses.INTERRUPT_FLAG) & 0x01);
        assertFalse(harness.cpu.IsInterruptMasterEnable());
    }

    @Test
    void reproducesHaltBugWhenInterruptIsPendingWithImeDisabled() {
        CpuHarness harness = EmulatorTestUtils.CreateCpuHarness(new byte[] {
                0x76,
                (byte) 0x3E, 0x12
        });
        harness.memory.Write(DuckAddresses.IE, DuckCPU.Interrupt.VBLANK.GetMask());
        harness.memory.Write(DuckAddresses.INTERRUPT_FLAG, DuckCPU.Interrupt.VBLANK.GetMask());
        harness.cpu.DisableInterrupts();

        harness.StepInstruction();
        harness.StepInstruction();

        assertEquals(0x3E, harness.cpu.GetAccumulator());
        assertEquals(0x0102, harness.cpu.GetPC());
        assertFalse(harness.cpu.IsHalted());
    }
}
