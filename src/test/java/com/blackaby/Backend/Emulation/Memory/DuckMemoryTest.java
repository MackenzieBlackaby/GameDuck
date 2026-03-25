package com.blackaby.Backend.Emulation.Memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.blackaby.Backend.Emulation.Misc.ROM;

class DuckMemoryTest {

    @Test
    void hblankDmaTransfersOneBlockPerWindow() {
        DuckMemory memory = CreateCgbMemory();
        WriteSequential(memory, 0xC000, 0x20, 0x10);

        memory.Write(DuckAddresses.HDMA1, 0xC0);
        memory.Write(DuckAddresses.HDMA2, 0x00);
        memory.Write(DuckAddresses.HDMA3, 0x80);
        memory.Write(DuckAddresses.HDMA4, 0x00);
        memory.Write(DuckAddresses.HDMA5, 0x81);

        assertEquals(0x01, memory.Read(DuckAddresses.HDMA5));
        assertEquals(0x00, memory.Read(0x8000));

        memory.TickHdma(false);
        assertEquals(0x00, memory.Read(0x8000));

        memory.TickHdma(true);
        AssertSequential(memory, 0x8000, 0x10, 0x10);
        assertEquals(0x00, memory.Read(DuckAddresses.HDMA5));
        assertEquals(0x00, memory.Read(0x8010));

        memory.TickHdma(true);
        assertEquals(0x00, memory.Read(0x8010));

        memory.TickHdma(false);
        memory.TickHdma(true);
        AssertSequential(memory, 0x8010, 0x10, 0x20);
        assertEquals(0xFF, memory.Read(DuckAddresses.HDMA5));
    }

    @Test
    void writingBitSevenClearCancelsActiveHblankDma() {
        DuckMemory memory = CreateCgbMemory();
        WriteSequential(memory, 0xC000, 0x30, 0x40);

        memory.Write(DuckAddresses.HDMA1, 0xC0);
        memory.Write(DuckAddresses.HDMA2, 0x00);
        memory.Write(DuckAddresses.HDMA3, 0x80);
        memory.Write(DuckAddresses.HDMA4, 0x00);
        memory.Write(DuckAddresses.HDMA5, 0x82);

        memory.TickHdma(true);
        AssertSequential(memory, 0x8000, 0x10, 0x40);
        assertEquals(0x01, memory.Read(DuckAddresses.HDMA5));

        memory.Write(DuckAddresses.HDMA5, 0x00);
        assertEquals(0x81, memory.Read(DuckAddresses.HDMA5));

        memory.TickHdma(false);
        memory.TickHdma(true);
        assertEquals(0x00, memory.Read(0x8010));
        assertEquals(0x81, memory.Read(DuckAddresses.HDMA5));
    }

    @Test
    void generalPurposeDmaCopiesAllBlocksImmediately() {
        DuckMemory memory = CreateCgbMemory();
        WriteSequential(memory, 0xC000, 0x20, 0x70);

        memory.Write(DuckAddresses.HDMA1, 0xC0);
        memory.Write(DuckAddresses.HDMA2, 0x00);
        memory.Write(DuckAddresses.HDMA3, 0x80);
        memory.Write(DuckAddresses.HDMA4, 0x00);
        memory.Write(DuckAddresses.HDMA5, 0x01);

        AssertSequential(memory, 0x8000, 0x20, 0x70);
        assertEquals(0xFF, memory.Read(DuckAddresses.HDMA5));
    }

    private static DuckMemory CreateCgbMemory() {
        byte[] romBytes = new byte[0x8000];
        romBytes[0x0143] = (byte) 0x80;
        romBytes[0x0147] = 0x00;
        romBytes[0x0148] = 0x00;
        romBytes[0x0149] = 0x00;

        DuckMemory memory = new DuckMemory();
        memory.LoadRom(ROM.FromBytes("test.gbc", romBytes, "test"), true);
        return memory;
    }

    private static void WriteSequential(DuckMemory memory, int address, int length, int startValue) {
        for (int index = 0; index < length; index++) {
            memory.Write(address + index, (startValue + index) & 0xFF);
        }
    }

    private static void AssertSequential(DuckMemory memory, int address, int length, int startValue) {
        for (int index = 0; index < length; index++) {
            assertEquals((startValue + index) & 0xFF, memory.Read(address + index),
                    "Unexpected value at address 0x" + Integer.toHexString(address + index));
        }
    }
}
