package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;
import com.blackaby.Backend.Platform.EmulatorCheat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheatStoreTest {

    @TempDir
    Path tempDir;

    private String previousCheatPath;

    @BeforeEach
    void setUp() {
        previousCheatPath = System.getProperty("gameduck.cheat_metadata_path");
        System.setProperty("gameduck.cheat_metadata_path", tempDir.resolve("cheats.properties").toString());
        CheatStore.ResetForTests();
    }

    @AfterEach
    void tearDown() {
        if (previousCheatPath == null) {
            System.clearProperty("gameduck.cheat_metadata_path");
        } else {
            System.setProperty("gameduck.cheat_metadata_path", previousCheatPath);
        }
        CheatStore.ResetForTests();
    }

    @Test
    void saveAndLoadCheatsRoundTripPerGame() {
        var firstRom = EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00,
                tempDir.resolve("first.gb").toString(), "First");
        var secondRom = EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00,
                tempDir.resolve("second.gb").toString(), "Second");

        List<EmulatorCheat> savedCheats = CheatStore.SaveCheats(firstRom, List.of(
                new EmulatorCheat("", "Infinite Lives", 0xC123, null, 0x09, true),
                new EmulatorCheat("", "", 0x0150, 0x00, 0x42, false)));

        assertEquals(2, savedCheats.size());
        assertTrue(savedCheats.stream().allMatch(cheat -> !cheat.key().isBlank()));
        assertEquals("Infinite Lives", savedCheats.get(0).label());
        assertEquals("Cheat 0150=42", savedCheats.get(1).label());
        assertEquals(savedCheats, CheatStore.GetCheats(firstRom));

        CheatStore.SaveCheats(secondRom, List.of(
                new EmulatorCheat("", "Second Game Cheat", 0xC000, null, 0x77, true)));

        assertEquals(2, CheatStore.GetCheats(firstRom).size());
        assertEquals(1, CheatStore.GetCheats(secondRom).size());
    }

    @Test
    void saveCheatsReplacesRemovedEntriesForOneGameOnly() {
        var rom = EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00,
                tempDir.resolve("replace.gb").toString(), "Replace");

        List<EmulatorCheat> initial = CheatStore.SaveCheats(rom, List.of(
                new EmulatorCheat("", "Lives", 0xC100, null, 0x09, true),
                new EmulatorCheat("", "Coins", 0xC101, null, 0x63, true)));
        assertEquals(2, initial.size());

        List<EmulatorCheat> updated = CheatStore.SaveCheats(rom, List.of(
                new EmulatorCheat(initial.get(1).key(), "Coins", 0xC101, null, 0x99, true)));

        assertEquals(1, updated.size());
        assertEquals("Coins", updated.get(0).label());
        assertEquals(0x99, updated.get(0).value());
        assertFalse(updated.stream().anyMatch(cheat -> "Lives".equals(cheat.label())));
    }
}
