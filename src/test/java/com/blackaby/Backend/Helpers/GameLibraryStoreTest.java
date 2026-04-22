package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLibraryStoreTest {

    @TempDir
    Path tempDir;

    private String previousMetadataPath;
    private String previousRomDirectory;
    private String previousPortableMetadataPath;
    private String previousManagedGamesPath;

    @BeforeEach
    void setUp() {
        previousMetadataPath = System.getProperty("gameduck.library_metadata_path");
        previousRomDirectory = System.getProperty("gameduck.library_rom_dir");
        previousPortableMetadataPath = System.getProperty("gameduck.library_portable_metadata_path");
        previousManagedGamesPath = System.getProperty("gameduck.managed_games_path");
        System.setProperty("gameduck.library_metadata_path", tempDir.resolve("game-library.properties").toString());
        System.setProperty("gameduck.library_rom_dir", tempDir.resolve("roms").toString());
        System.setProperty("gameduck.library_portable_metadata_path",
                tempDir.resolve("library-portable.properties").toString());
        System.setProperty("gameduck.managed_games_path", tempDir.resolve("managed-games.properties").toString());
        resetStore();
    }

    @AfterEach
    void tearDown() {
        restoreProperty("gameduck.library_metadata_path", previousMetadataPath);
        restoreProperty("gameduck.library_rom_dir", previousRomDirectory);
        restoreProperty("gameduck.library_portable_metadata_path", previousPortableMetadataPath);
        restoreProperty("gameduck.managed_games_path", previousManagedGamesPath);
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

    @Test
    void refreshLibraryRebuildsMissingMetadataFromStoredRoms() throws Exception {
        GameLibraryStore.LibraryEntry storedEntry = GameLibraryStore.RememberGame(
                createRom("portable-copy.gb", "Portable Copy", List.of(), List.of()));
        Path metadataPath = tempDir.resolve("game-library.properties");
        Files.deleteIfExists(metadataPath);
        resetStore();

        GameLibraryStore.RefreshResult result = GameLibraryStore.RefreshLibrary();

        assertEquals(1, result.scannedRomCount());
        assertEquals(1, result.preservedEntryCount());
        assertEquals(0, result.rebuiltEntryCount());
        assertEquals(0, result.removedEntryCount());

        List<GameLibraryStore.LibraryEntry> rebuiltEntries = GameLibraryStore.GetEntries();
        assertEquals(1, rebuiltEntries.size());
        GameLibraryStore.LibraryEntry rebuiltEntry = rebuiltEntries.get(0);
        assertEquals(storedEntry.romPath(), rebuiltEntry.romPath());
        assertEquals(storedEntry.sourcePath(), rebuiltEntry.sourcePath());
        assertFalse(rebuiltEntry.displayName().isBlank());
        assertFalse(rebuiltEntry.favourite());
    }

    @Test
    void recoverLibraryRestoresPortableMirrorAndPreservesMetadata() throws Exception {
        Path patchPath = tempDir.resolve("mirror-patch.ips");
        GameLibraryStore.LibraryEntry storedEntry = GameLibraryStore.RememberGame(
                createRom("mirror-copy.gbc", "Mirror Copy", List.of("Patch One"), List.of(patchPath.toString())));
        GameLibraryStore.SetFavourite(storedEntry.key(), true);
        Path metadataPath = tempDir.resolve("game-library.properties");
        Files.deleteIfExists(metadataPath);
        resetStore();

        GameLibraryStore.RefreshResult result = GameLibraryStore.RecoverLibrary();

        assertTrue(result.restoredFromPortableMirror());
        List<GameLibraryStore.LibraryEntry> recoveredEntries = GameLibraryStore.GetEntries();
        assertEquals(1, recoveredEntries.size());
        GameLibraryStore.LibraryEntry recoveredEntry = recoveredEntries.get(0);
        assertEquals(storedEntry.key(), recoveredEntry.key());
        assertEquals(storedEntry.sourcePath(), recoveredEntry.sourcePath());
        assertEquals(storedEntry.patchSourcePaths(), recoveredEntry.patchSourcePaths());
        assertTrue(recoveredEntry.favourite());
    }

    @Test
    void refreshLibraryRemovesStaleEntriesAndRebuildsManagedSaveRegistry() throws Exception {
        GameLibraryStore.LibraryEntry entry = GameLibraryStore.RememberGame(
                createRom("battery-save.gb", "Battery Save", List.of(), List.of()));
        Files.deleteIfExists(entry.romPath());
        resetStore();

        GameLibraryStore.RefreshResult result = GameLibraryStore.RefreshLibrary();

        assertEquals(0, result.scannedRomCount());
        assertEquals(1, result.removedEntryCount());
        assertTrue(GameLibraryStore.GetEntries().isEmpty());
        assertTrue(ManagedGameRegistry.GetKnownGames().isEmpty());
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
        GameLibraryStore.ResetForTests();
        ManagedGameRegistry.ResetForTests();
    }
}
