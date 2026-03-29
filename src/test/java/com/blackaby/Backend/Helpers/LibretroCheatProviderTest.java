package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;
import com.blackaby.Backend.Platform.EmulatorCheat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibretroCheatProviderTest {

    @TempDir
    Path tempDir;

    private String previousCheatPath;
    private String previousGameMetadataPath;

    @BeforeEach
    void setUp() {
        previousCheatPath = System.getProperty("gameduck.cheat_metadata_path");
        previousGameMetadataPath = System.getProperty("gameduck.game_metadata_path");
        System.setProperty("gameduck.cheat_metadata_path", tempDir.resolve("cheats.properties").toString());
        System.setProperty("gameduck.game_metadata_path", tempDir.resolve("game-metadata.properties").toString());
        CheatStore.ResetForTests();
        GameMetadataStore.ResetForTests();
    }

    @AfterEach
    void tearDown() {
        restoreProperty("gameduck.cheat_metadata_path", previousCheatPath);
        restoreProperty("gameduck.game_metadata_path", previousGameMetadataPath);
        CheatStore.ResetForTests();
        GameMetadataStore.ResetForTests();
    }

    @Test
    void parseGameSharkCodeConvertsAddressAndValue() {
        LibretroCheatProvider.ParsedGameSharkCode parsedCode =
                LibretroCheatProvider.ParseGameSharkCode("018784D0").orElseThrow();

        assertEquals(0xD084, parsedCode.address());
        assertEquals(0x87, parsedCode.value());
    }

    @Test
    void parseCheatFileSplitsMultiCodeEntriesAndSkipsUnsupportedCodes() {
        var rom = EmulatorTestUtils.CreateBlankRom(0x80, 2, 0x00, 0x00,
                tempDir.resolve("conker.gbc").toString(), "Conker");
        String content = """
                cheats = 3

                cheat0_desc = "Infinite Lives"
                cheat0_code = "0109D6CE+0109D7CE"
                cheat0_enable = false

                cheat1_desc = "Invincible"
                cheat1_code = "9063D6CE"
                cheat1_enable = false

                cheat2_desc = "Game Genie Only"
                cheat2_code = "123-456-789"
                cheat2_enable = false
                """;

        List<EmulatorCheat> cheats = LibretroCheatProvider.ParseCheatFile(
                rom,
                "Nintendo - Game Boy Color",
                "Conker's Pocket Tales (USA, Europe) (GameShark).cht",
                content);

        assertEquals(3, cheats.size());
        assertEquals("Infinite Lives (1/2)", cheats.get(0).label());
        assertEquals(0xCED6, cheats.get(0).address());
        assertEquals(0x09, cheats.get(0).value());
        assertFalse(cheats.get(0).enabled());
        assertEquals("Infinite Lives (2/2)", cheats.get(1).label());
        assertEquals(0xCED7, cheats.get(1).address());
        assertEquals("Invincible", cheats.get(2).label());
        assertEquals(0xCED6, cheats.get(2).address());
        assertEquals(0x63, cheats.get(2).value());
    }

    @Test
    void selectMatchingFilesPrefersGameSharkVariantBeforeMixedFile() {
        Map<String, String> filesByName = new LinkedHashMap<>();
        filesByName.put("Adventure Island (USA, Europe).cht", "plain");
        filesByName.put("Adventure Island (USA, Europe) (Game Genie).cht", "genie");
        filesByName.put("Adventure Island (USA, Europe) (GameShark).cht", "gameshark");

        List<String> matchingFiles = LibretroCheatProvider.SelectMatchingFiles(
                filesByName,
                List.of("Adventure Island (USA, Europe)", "Adventure Island"));

        assertEquals(List.of(
                "Adventure Island (USA, Europe) (GameShark).cht",
                "Adventure Island (USA, Europe).cht"), matchingFiles);
    }

    @Test
    void autoImportCheatsMergesOnceAndRemembersMatchedTitle() {
        var rom = EmulatorTestUtils.CreateBlankRom(0x80, 2, 0x00, 0x00,
                tempDir.resolve("adventure.gb").toString(), "Adventure");
        CheatStore.SaveCheats(rom, List.of(
                new EmulatorCheat("", "Manual Lives", 0xC423, null, 0x0F, true)));
        LibretroCheatProvider.LibretroCheatSet cheatSet = new LibretroCheatProvider.LibretroCheatSet(
                "Adventure Island (USA, Europe)",
                "Adventure Island (USA, Europe) (GameShark).cht",
                List.of(
                        new EmulatorCheat("libretro-one", "Infinite Energy", 0xC423, null, 0x0F, false),
                        new EmulatorCheat("libretro-two", "Infinite Lives", 0xC419, null, 0x09, false)));

        LibretroCheatProvider.AutoImportResult firstImport = LibretroCheatProvider.AutoImportCheats(
                rom,
                () -> Optional.of(cheatSet));
        List<EmulatorCheat> storedCheats = CheatStore.GetCheats(rom);

        assertEquals(LibretroCheatProvider.AutoImportStatus.IMPORTED, firstImport.status());
        assertEquals(1, firstImport.importedCount());
        assertEquals(2, storedCheats.size());
        assertTrue(CheatStore.HasLibretroImportRecord(rom));
        assertEquals("Adventure Island (USA, Europe)", GameMetadataStore.GetLibretroTitle(rom).orElseThrow());

        LibretroCheatProvider.AutoImportResult secondImport = LibretroCheatProvider.AutoImportCheats(
                rom,
                () -> Optional.of(cheatSet));

        assertEquals(LibretroCheatProvider.AutoImportStatus.ALREADY_IMPORTED, secondImport.status());
        assertEquals(2, CheatStore.GetCheats(rom).size());
    }

    private void restoreProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }
}
