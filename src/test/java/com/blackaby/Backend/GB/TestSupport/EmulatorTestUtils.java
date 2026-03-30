package com.blackaby.Backend.GB.TestSupport;

import java.util.Arrays;

import com.blackaby.Backend.GB.CPU.GBProcessor;
import com.blackaby.Backend.GB.CPU.GBInstructionLogic;
import com.blackaby.Backend.GB.Memory.GBMemory;
import com.blackaby.Backend.GB.Misc.GBRom;

public final class EmulatorTestUtils {

    private EmulatorTestUtils() {
    }

    public static GBRom CreateBlankRom(int cartridgeTypeCode, int bankCount, int ramSizeCode, int cgbFlag,
            String filename,
            String displayName) {
        byte[] romBytes = new byte[Math.max(2, bankCount) * 0x4000];
        ApplyHeader(romBytes, cartridgeTypeCode, bankCount, ramSizeCode, cgbFlag);
        return GBRom.FromBytes(filename, romBytes, displayName);
    }

    public static GBRom CreatePatternedRom(int cartridgeTypeCode, int bankCount, int ramSizeCode, int cgbFlag,
            String filename, String displayName) {
        byte[] romBytes = new byte[Math.max(2, bankCount) * 0x4000];
        for (int bank = 0; bank < Math.max(2, bankCount); bank++) {
            int base = bank * 0x4000;
            Arrays.fill(romBytes, base, base + 0x4000, (byte) (bank & 0xFF));
            romBytes[base + 1] = (byte) ((bank >> 8) & 0xFF);
        }
        ApplyHeader(romBytes, cartridgeTypeCode, bankCount, ramSizeCode, cgbFlag);
        return GBRom.FromBytes(filename, romBytes, displayName);
    }

    public static CpuHarness CreateCpuHarness(byte[] program) {
        GBRom rom = CreateBlankRom(0x00, 2, 0x00, 0x00, "cpu_test.gb", "cpu-test");
        byte[] romBytes = rom.ToByteArray();
        System.arraycopy(program, 0, romBytes, 0x0100, Math.min(program.length, romBytes.length - 0x0100));
        rom = GBRom.FromBytes("cpu_test.gb", romBytes, "cpu-test");

        GBMemory memory = new GBMemory();
        memory.LoadRom(rom, false);
        GBProcessor cpu = new GBProcessor(memory, null, rom);
        memory.SetCpu(cpu);
        GBInstructionLogic.Initialise(cpu, memory);
        cpu.SetPC(0x0100);
        cpu.SetSP(0xFFFE);
        return new CpuHarness(rom, memory, cpu);
    }

    public static final class CpuHarness {
        public final GBRom rom;
        public final GBMemory memory;
        public final GBProcessor cpu;

        private CpuHarness(GBRom rom, GBMemory memory, GBProcessor cpu) {
            this.rom = rom;
            this.memory = memory;
            this.cpu = cpu;
        }

        public int StepInstruction() {
            if (!cpu.IsHalted()) {
                cpu.Fetch();
                cpu.Decode();
            }
            return cpu.Execute();
        }
    }

    private static void ApplyHeader(byte[] romBytes, int cartridgeTypeCode, int bankCount, int ramSizeCode,
            int cgbFlag) {
        romBytes[0x0134] = 'T';
        romBytes[0x0135] = 'E';
        romBytes[0x0136] = 'S';
        romBytes[0x0137] = 'T';
        romBytes[0x0143] = (byte) (cgbFlag & 0xFF);
        romBytes[0x0147] = (byte) (cartridgeTypeCode & 0xFF);
        romBytes[0x0148] = (byte) RomSizeCodeForBankCount(bankCount);
        romBytes[0x0149] = (byte) (ramSizeCode & 0xFF);
    }

    private static int RomSizeCodeForBankCount(int bankCount) {
        return switch (bankCount) {
            case 2 -> 0x00;
            case 4 -> 0x01;
            case 8 -> 0x02;
            case 16 -> 0x03;
            case 32 -> 0x04;
            case 64 -> 0x05;
            case 128 -> 0x06;
            case 256 -> 0x07;
            case 512 -> 0x08;
            default -> throw new IllegalArgumentException("Unsupported ROM bank count for tests: " + bankCount);
        };
    }
}
