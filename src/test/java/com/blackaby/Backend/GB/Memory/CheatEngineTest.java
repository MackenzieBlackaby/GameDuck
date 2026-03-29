package com.blackaby.Backend.GB.Memory;

import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;
import com.blackaby.Backend.Platform.EmulatorCheat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheatEngineTest {

    @Test
    void readOverridesApplyWhenCompareMatches() {
        DuckMemory memory = createMemory();
        CheatEngine engine = new CheatEngine();
        memory.SetCheatEngine(engine);
        engine.SetCheats(List.of(new EmulatorCheat("rom", "ROM Override", 0x0150, 0x00, 0x42, true)));

        assertEquals(0x00, memory.ReadWithoutCheats(0x0150));
        assertEquals(0x42, memory.Read(0x0150));
    }

    @Test
    void readOverridesAreSkippedWhenCompareDoesNotMatch() {
        DuckMemory memory = createMemory();
        CheatEngine engine = new CheatEngine();
        memory.SetCheatEngine(engine);
        engine.SetCheats(List.of(new EmulatorCheat("rom", "ROM Override", 0x0150, 0x01, 0x42, true)));

        assertEquals(0x00, memory.Read(0x0150));
    }

    @Test
    void writeCheatsForceMutableMemory() {
        DuckMemory memory = createMemory();
        CheatEngine engine = new CheatEngine();
        memory.SetCheatEngine(engine);
        memory.Write(0xC123, 0x11);
        engine.SetCheats(List.of(new EmulatorCheat("ram", "WRAM Override", 0xC123, null, 0x99, true)));

        engine.ApplyWriteCheats(memory);

        assertEquals(0x99, memory.ReadWithoutCheats(0xC123));
        assertEquals(0x99, memory.Read(0xC123));
    }

    @Test
    void writeCheatsRespectCompareValues() {
        DuckMemory memory = createMemory();
        CheatEngine engine = new CheatEngine();
        memory.SetCheatEngine(engine);
        memory.Write(0xC123, 0x11);
        engine.SetCheats(List.of(new EmulatorCheat("ram", "Conditional WRAM Override", 0xC123, 0x44, 0x99, true)));

        engine.ApplyWriteCheats(memory);

        assertEquals(0x11, memory.ReadWithoutCheats(0xC123));
    }

    private DuckMemory createMemory() {
        DuckMemory memory = new DuckMemory();
        memory.LoadRom(EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "cheat.gb", "cheat"), false);
        return memory;
    }
}
