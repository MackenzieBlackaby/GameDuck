package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLibraryStoreTest {

    @TempDir
    Path tempDir;

    private String previousMetadataPath;
    private String previousRomDirectory;

    @BeforeEach
    void setUp() throws Exception {
        previousMetadataPath = System.getProperty("gameduck.library_metadata_path");
        previousRomDirectory = System.getProperty("gameduck.library_rom_dir");
        System.setProperty("gameduck.library_metadata_path", tempDir.resolve("game-library.properties").toString());
        System.setProperty("gameduck.library_rom_dir", tempDir.resolve("roms").toString());
        resetStore();
    }

    @AfterEach
    void tearDown() throws Exception {
        restoreProperty("gameduck.library_metadata_path", previousMetadataPath);
        restoreProperty("gameduck.library_rom_dir", previousRomDirectory);
        resetStore();
    }

    @Test
    void rememberFavouriteReloadAndDeleteRoundTrip() throws Exception {
        Path patchPath = tempDir.resolve("patch-one.ips");
        GBRom rom = createRom("tracked.gbc", "Tracked Display", List.of("Patch One"), List.of(patchPath.toString()));

        GameLibraryStore.LibraryEntry storedEntry = GameLibraryStore.RememberGame(rom);

        assertTrue(Files.exists(storedEntry.romPath()));
        assertEquals(rom.GetSourcePath(), storedEntry.sourcePath());
        assertEquals(rom.GetName(), storedEntry.displayName());
        assertEquals(List.of("Patch One"), storedEntry.patchNames());
        assertEquals(List.of(patchPath.toString()), storedEntry.patchSourcePaths());
        assertFalse(GameLibraryStore.IsFavourite(storedEntry.key()));

        GameLibraryStore.SetFavourite(storedEntry.key(), true);
        assertTrue(GameLibraryStore.IsFavourite(storedEntry.key()));

        resetStore();
        List<GameLibraryStore.LibraryEntry> reloadedEntries = GameLibraryStore.GetEntries();

        assertEquals(1, reloadedEntries.size());
        GameLibraryStore.LibraryEntry reloadedEntry = reloadedEntries.get(0);
        assertEquals(storedEntry.key(), reloadedEntry.key());
        assertEquals(storedEntry.sourcePath(), reloadedEntry.sourcePath());
        assertEquals(storedEntry.displayName(), reloadedEntry.displayName());
        assertEquals(storedEntry.headerTitle(), reloadedEntry.headerTitle());
        assertIterableEquals(storedEntry.patchNames(), reloadedEntry.patchNames());
        assertIterableEquals(storedEntry.patchSourcePaths(), reloadedEntry.patchSourcePaths());
        assertTrue(reloadedEntry.favourite());

        GameLibraryStore.DeleteEntry(storedEntry.key());
        assertFalse(Files.exists(storedEntry.romPath()));
        assertTrue(GameLibraryStore.GetEntries().isEmpty());
    }

    @Test
    void getEntriesOrdersNewestFirstAndSkipsMissingStoredRoms() throws Exception {
        GameLibraryStore.LibraryEntry firstEntry = GameLibraryStore.RememberGame(
                createRom("first.gb", "First", List.of(), List.of()));
        Thread.sleep(5L);
        GameLibraryStore.LibraryEntry secondEntry = GameLibraryStore.RememberGame(
                createRom("second.gbc", "Second", List.of("Patch"), List.of(tempDir.resolve("patch-two.ips").toString())));

        List<String> keysInOrder = GameLibraryStore.GetEntries().stream().map(GameLibraryStore.LibraryEntry::key).toList();
        assertEquals(List.of(secondEntry.key(), firstEntry.key()), keysInOrder);

        Files.deleteIfExists(secondEntry.romPath());
        resetStore();

        List<GameLibraryStore.LibraryEntry> remainingEntries = GameLibraryStore.GetEntries();
        assertEquals(1, remainingEntries.size());
        assertEquals(firstEntry.key(), remainingEntries.get(0).key());
    }

    @Test
    void recentEntriesCanBeListedAndCleared() throws Exception {
        GameLibraryStore.LibraryEntry firstEntry = GameLibraryStore.RememberGame(
                createRom("recent-one.gb", "Recent One", List.of(), List.of()));
        Thread.sleep(5L);
        GameLibraryStore.LibraryEntry secondEntry = GameLibraryStore.RememberGame(
                createRom("recent-two.gb", "Recent Two", List.of(), List.of()));

        List<String> recentKeys = GameLibraryStore.GetRecentEntries(10).stream()
                .map(GameLibraryStore.LibraryEntry::key)
                .toList();
        assertEquals(List.of(secondEntry.key(), firstEntry.key()), recentKeys);

        GameLibraryStore.ClearRecentHistory();

        assertTrue(GameLibraryStore.GetRecentEntries(10).isEmpty());
    }

    private GBRom createRom(String filename, String displayName, List<String> patchNames, List<String> patchSourcePaths) {
        GBRom baseRom = EmulatorTestUtils.CreateBlankRom(
                0x10,
                2,
                0x03,
                filename.endsWith(".gbc") ? 0x80 : 0x00,
                tempDir.resolve(filename).toString(),
                displayName);
        byte[] romBytes = baseRom.ToByteArray();
        byte[] identityBytes = (filename + "|" + displayName).getBytes(StandardCharsets.UTF_8);
        System.arraycopy(identityBytes, 0, romBytes, 0x0150, Math.min(identityBytes.length, romBytes.length - 0x0150));
        return GBRom.FromBytes(baseRom.GetSourcePath(), romBytes, displayName, patchNames, patchSourcePaths);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static void resetStore() {
        try {
            Field storeField = GameLibraryStore.class.getDeclaredField("store");
            storeField.setAccessible(true);
            Object store = storeField.get(null);

            Field loadedField = KeyedPropertiesStore.class.getDeclaredField("loaded");
            loadedField.setAccessible(true);
            loadedField.setBoolean(store, false);

            Field propertiesField = KeyedPropertiesStore.class.getDeclaredField("properties");
            propertiesField.setAccessible(true);
            ((Properties) propertiesField.get(store)).clear();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
