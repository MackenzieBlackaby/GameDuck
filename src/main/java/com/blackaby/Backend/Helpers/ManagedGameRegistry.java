package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.Platform.EmulatorGame;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists the set of known save-capable games so save management is not tied
 * to the currently running ROM.
 */
public final class ManagedGameRegistry {

    public record StoredGame(String key, SaveFileManager.SaveIdentity saveIdentity, List<String> patchSourcePaths,
                             String headerTitle, boolean cgbCompatible, boolean cgbOnly, int expectedSaveSizeBytes,
                             long lastSeenMillis) {
        public StoredGame {
            patchSourcePaths = List.copyOf(patchSourcePaths == null ? List.of() : patchSourcePaths);
        }

        /**
         * Attempts to reconstruct the tracked ROM from its stored source paths.
         *
         * @return reconstructed ROM when the base and patch files are still available
         */
        public Optional<GBRom> LoadRom() {
            if (saveIdentity == null || saveIdentity.sourcePath() == null || saveIdentity.sourcePath().isBlank()) {
                return Optional.empty();
            }

            try {
                return Optional.of(LoadTrackedRom(saveIdentity.sourcePath(), saveIdentity.patchNames(), patchSourcePaths));
            } catch (IOException | IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
    }

    private static final String sourcePathSuffix = ".source_path";
    private static final String systemIdSuffix = ".system_id";
    private static final String systemVariantIdSuffix = ".system_variant_id";
    private static final String systemVariantLabelSuffix = ".system_variant_label";
    private static final String sourceNameSuffix = ".source_name";
    private static final String displayNameSuffix = ".display_name";
    private static final String patchNameCountSuffix = ".patch_name_count";
    private static final String patchSourceCountSuffix = ".patch_source_count";
    private static final String patchNamePrefix = ".patch_name.";
    private static final String patchSourcePrefix = ".patch_source.";
    private static final String headerTitleSuffix = ".header_title";
    private static final String cgbCompatibleSuffix = ".cgb_compatible";
    private static final String cgbOnlySuffix = ".cgb_only";
    private static final String expectedSaveSizeSuffix = ".expected_save_size";
    private static final String lastSeenSuffix = ".last_seen";
    private static final String contentHashSuffix = ".content_hash";

    private static final Comparator<StoredGame> gameSortOrder = Comparator
            .comparingLong(StoredGame::lastSeenMillis)
            .reversed()
            .thenComparing(game -> SaveFileManager.BuildFallbackBaseName(game.saveIdentity()));
    private static final Comparator<GameSnapshot> duplicatePreference = Comparator
            .comparing((GameSnapshot snapshot) -> IsManagedLibraryPath(snapshot.game().saveIdentity().sourcePath()))
            .thenComparingLong(snapshot -> -snapshot.game().lastSeenMillis())
            .thenComparing(game -> SaveFileManager.BuildFallbackBaseName(game.game().saveIdentity()),
                    String.CASE_INSENSITIVE_ORDER);

    private static final Store store = new Store();

    private ManagedGameRegistry() {
    }

    /**
     * Records a save-capable ROM so it can appear in the multi-game save manager.
     *
     * @param rom loaded ROM
     */
    public static synchronized void RememberGame(GBRom rom) {
        if (rom == null || !rom.HasBatteryBackedSave()) {
            return;
        }

        store.EnsureLoaded();
        CleanupDuplicateGamesInternal();
        String contentHash = KeyedPropertiesStore.Hash(rom.ToByteArray());
        long now = System.currentTimeMillis();
        StoredGame existingGame = FindGameByContentHash(contentHash);
        if (existingGame != null) {
            EntryProperties existingEntry = new EntryProperties(existingGame.key());
            existingEntry.TouchFromExistingRom(rom, SaveFileManager.SaveIdentity.FromRom(rom), contentHash, now);
            store.Persist();
            return;
        }

        String key = BuildGameKey(rom);
        EntryProperties entry = new EntryProperties(key);
        SaveFileManager.SaveIdentity saveIdentity = SaveFileManager.SaveIdentity.FromRom(rom);

        entry.WriteMetadata(rom, saveIdentity, contentHash, now);
        store.Persist();
    }

    /**
     * Returns the known save-capable games sorted by most recent activity.
     *
     * @return tracked games
     */
    public static synchronized List<StoredGame> GetKnownGames() {
        store.EnsureLoaded();
        CleanupDuplicateGamesInternal();
        return store.StoredKeys().stream()
                .map(ManagedGameRegistry::ReadStoredGame)
                .filter(game -> game != null)
                .sorted(gameSortOrder)
                .toList();
    }

    /**
     * Rebuilds the tracked save-capable game registry from the current library.
     *
     * @param libraryEntries current managed-library entries
     */
    public static synchronized void RefreshFromLibraryEntries(List<GameLibraryStore.LibraryEntry> libraryEntries) {
        store.EnsureLoaded();
        List<GameSnapshot> existingSnapshots = BuildSnapshots();
        Map<String, StoredGame> existingByHash = new HashMap<>();
        for (GameSnapshot snapshot : existingSnapshots) {
            if (snapshot.contentHash() != null && !snapshot.contentHash().isBlank()) {
                existingByHash.putIfAbsent(snapshot.contentHash(), snapshot.game());
            }
        }

        store.ClearEntries();
        List<GameLibraryStore.LibraryEntry> safeEntries = libraryEntries == null ? List.of() : libraryEntries;
        for (GameLibraryStore.LibraryEntry entry : safeEntries) {
            if (entry == null) {
                continue;
            }

            try {
                GBRom rom = entry.LoadRom();
                if (!rom.HasBatteryBackedSave()) {
                    continue;
                }

                String contentHash = KeyedPropertiesStore.Hash(rom.ToByteArray());
                StoredGame existingGame = existingByHash.get(contentHash);
                long lastSeenMillis = Math.max(
                        existingGame == null ? 0L : existingGame.lastSeenMillis(),
                        Math.max(entry.lastPlayedMillis(), entry.addedAtMillis()));
                EntryProperties refreshedEntry = new EntryProperties(BuildGameKey(rom));
                refreshedEntry.WriteMetadata(rom, SaveFileManager.SaveIdentity.FromRom(rom), contentHash, lastSeenMillis);
            } catch (IOException | IllegalArgumentException exception) {
                // Ignore entries that can no longer be reconstructed from disk.
            }
        }
        store.Persist();
    }

    /**
     * Builds the registry key used for a loaded ROM.
     *
     * @param rom loaded ROM
     * @return stable registry key
     */
    public static String BuildGameKey(GBRom rom) {
        if (rom == null) {
            return "";
        }
        return BuildGameKey(rom.GetSourcePath(), rom.GetSourceName(), rom.GetPatchSourcePaths(), rom.GetPatchNames());
    }

    public static String BuildGameKey(EmulatorGame game) {
        if (game == null) {
            return "";
        }
        return BuildGameKey(game.sourcePath(), game.sourceName(), game.patchSourcePaths(), game.patchNames());
    }

    public static Path MetadataFilePath() {
        return store.MetadataPath();
    }

    public static synchronized void ResetForTests() {
        store.ResetForTests();
    }

    private static String BuildGameKey(String sourcePath, String sourceName, List<String> patchSourcePaths, List<String> patchNames) {
        StringBuilder builder = new StringBuilder();
        builder.append(KeyedPropertiesStore.NullToEmpty(sourcePath)).append('|');
        builder.append(KeyedPropertiesStore.NullToEmpty(sourceName)).append('|');
        for (String patchSourcePath : patchSourcePaths == null ? List.<String>of() : patchSourcePaths) {
            builder.append(KeyedPropertiesStore.NullToEmpty(patchSourcePath)).append('|');
        }
        builder.append('|');
        for (String patchName : patchNames == null ? List.<String>of() : patchNames) {
            builder.append(KeyedPropertiesStore.NullToEmpty(patchName)).append('|');
        }
        return KeyedPropertiesStore.Hash(builder.toString());
    }

    private static int CleanupDuplicateGamesInternal() {
        List<GameSnapshot> snapshots = BuildSnapshots();
        Map<String, List<GameSnapshot>> snapshotsByHash = new HashMap<>();
        for (GameSnapshot snapshot : snapshots) {
            if (snapshot.contentHash().isBlank()) {
                continue;
            }
            snapshotsByHash.computeIfAbsent(snapshot.contentHash(), ignored -> new ArrayList<>()).add(snapshot);
        }

        int removedCount = 0;
        boolean changed = false;
        for (List<GameSnapshot> group : snapshotsByHash.values()) {
            if (group.size() < 2) {
                continue;
            }

            group.sort(duplicatePreference);
            GameSnapshot canonical = group.get(0);
            MergeDuplicateMetadata(canonical, group);
            for (int index = 1; index < group.size(); index++) {
                new EntryProperties(group.get(index).game().key()).RemoveAll();
                removedCount++;
                changed = true;
            }
        }

        if (changed) {
            store.Persist();
        }
        return removedCount;
    }

    private static void MergeDuplicateMetadata(GameSnapshot canonical, List<GameSnapshot> group) {
        EntryProperties canonicalProperties = new EntryProperties(canonical.game().key());
        GameSnapshot preferredSource = group.stream().min(duplicatePreference).orElse(canonical);
        long latestSeen = group.stream()
                .mapToLong(snapshot -> snapshot.game().lastSeenMillis())
                .max()
                .orElse(canonical.game().lastSeenMillis());
        int expectedSaveSize = group.stream()
                .mapToInt(snapshot -> snapshot.game().expectedSaveSizeBytes())
                .max()
                .orElse(canonical.game().expectedSaveSizeBytes());

        canonicalProperties.SetString(sourcePathSuffix, preferredSource.game().saveIdentity().sourcePath());
        canonicalProperties.SetString(systemIdSuffix, preferredSource.game().saveIdentity().systemId());
        canonicalProperties.SetString(systemVariantIdSuffix, preferredSource.game().saveIdentity().systemVariantId());
        canonicalProperties.SetString(systemVariantLabelSuffix, preferredSource.game().saveIdentity().systemVariantLabel());
        canonicalProperties.SetString(sourceNameSuffix, preferredSource.game().saveIdentity().sourceName());
        canonicalProperties.SetString(displayNameSuffix, preferredSource.game().saveIdentity().displayName());
        canonicalProperties.SetString(headerTitleSuffix, preferredSource.game().headerTitle());
        canonicalProperties.SetBoolean(cgbCompatibleSuffix, preferredSource.game().cgbCompatible());
        canonicalProperties.SetBoolean(cgbOnlySuffix, preferredSource.game().cgbOnly());
        canonicalProperties.SetInt(expectedSaveSizeSuffix, expectedSaveSize);
        canonicalProperties.SetLong(lastSeenSuffix, latestSeen);
        canonicalProperties.SetString(contentHashSuffix, canonical.contentHash());
        canonicalProperties.WriteIndexedList(patchNamePrefix, patchNameCountSuffix, preferredSource.game().saveIdentity().patchNames());
        canonicalProperties.WriteIndexedList(patchSourcePrefix, patchSourceCountSuffix, preferredSource.game().patchSourcePaths());
    }

    private static StoredGame FindGameByContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return null;
        }

        return BuildSnapshots().stream()
                .filter(snapshot -> contentHash.equals(snapshot.contentHash()))
                .min(duplicatePreference)
                .map(GameSnapshot::game)
                .orElse(null);
    }

    private static List<GameSnapshot> BuildSnapshots() {
        List<GameSnapshot> snapshots = new ArrayList<>();
        for (String key : store.StoredKeys()) {
            EntryProperties properties = new EntryProperties(key);
            StoredGame storedGame = properties.Read();
            if (storedGame == null) {
                continue;
            }
            snapshots.add(new GameSnapshot(storedGame, properties.ResolveContentHash()));
        }
        return snapshots;
    }

    private static boolean IsManagedLibraryPath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return false;
        }

        try {
            Path candidatePath = Path.of(sourcePath).toAbsolutePath().normalize();
            Path libraryPath = GameLibraryStore.LibraryDirectoryPath().toAbsolutePath().normalize();
            return candidatePath.startsWith(libraryPath);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean ShouldPreferSourcePath(String currentSourcePath, String candidateSourcePath) {
        if (candidateSourcePath == null || candidateSourcePath.isBlank()) {
            return false;
        }
        if (currentSourcePath == null || currentSourcePath.isBlank()) {
            return true;
        }
        return IsManagedLibraryPath(currentSourcePath) && !IsManagedLibraryPath(candidateSourcePath);
    }

    private static StoredGame ReadStoredGame(String key) {
        return new EntryProperties(key).Read();
    }

    private record GameSnapshot(StoredGame game, String contentHash) {
    }

    private static boolean ResolveCgbCompatible(String prefix, String sourcePath, SaveFileManager.SaveIdentity saveIdentity,
                                                List<String> patchSourcePaths) {
        String storedValue = store.RawProperty(prefix + cgbCompatibleSuffix);
        if (storedValue != null) {
            return Boolean.parseBoolean(storedValue);
        }

        boolean cgbCompatible = RomConsoleSupport.IsProbablyGbc(sourcePath);
        try {
            if (sourcePath != null && !sourcePath.isBlank()) {
                cgbCompatible = RomConsoleSupport.IsGbc(
                        LoadTrackedRom(sourcePath, saveIdentity == null ? List.of() : saveIdentity.patchNames(), patchSourcePaths));
            }
        } catch (IOException | IllegalArgumentException exception) {
            // Keep the extension-based fallback when original files are unavailable.
        }

        store.SetRawProperty(prefix + cgbCompatibleSuffix, String.valueOf(cgbCompatible));
        store.Persist();
        return cgbCompatible;
    }

    private static boolean ResolveCgbOnly(String prefix, String sourcePath, SaveFileManager.SaveIdentity saveIdentity,
                                          List<String> patchSourcePaths) {
        String storedValue = store.RawProperty(prefix + cgbOnlySuffix);
        if (storedValue != null) {
            return Boolean.parseBoolean(storedValue);
        }

        boolean cgbOnly = false;
        try {
            if (sourcePath != null && !sourcePath.isBlank()) {
                cgbOnly = RomConsoleSupport.IsCgbOnly(
                        LoadTrackedRom(sourcePath, saveIdentity == null ? List.of() : saveIdentity.patchNames(), patchSourcePaths));
            }
        } catch (IOException | IllegalArgumentException exception) {
            // Keep false when original files are unavailable.
        }

        store.SetRawProperty(prefix + cgbOnlySuffix, String.valueOf(cgbOnly));
        store.Persist();
        return cgbOnly;
    }

    private static GBRom LoadTrackedRom(String sourcePath, List<String> patchNames, List<String> patchSourcePaths) throws IOException {
        GBRom rom = new GBRom(sourcePath);
        List<String> safePatchNames = patchNames == null ? List.of() : patchNames;
        List<String> safePatchSourcePaths = patchSourcePaths == null ? List.of() : patchSourcePaths;
        for (int index = 0; index < safePatchSourcePaths.size(); index++) {
            String patchSourcePath = safePatchSourcePaths.get(index);
            String patchName = index < safePatchNames.size() ? safePatchNames.get(index) : null;
            if (patchSourcePath == null || patchSourcePath.isBlank()) {
                return rom;
            }
            rom = GBRom.LoadPatched(rom, patchSourcePath, patchName);
        }
        return rom;
    }

    private static final class Store extends KeyedPropertiesStore {
        private Store() {
            super("game.", sourceNameSuffix, "gameduck.managed_games_path",
                    Path.of("cache", "managed-games.properties"), "GameDuck managed games");
        }

        private Path MetadataPath() {
            return ResolvedStorePath();
        }

        private void ClearEntries() {
            ClearAllProperties();
        }
    }

    private static final class EntryProperties {
        private final KeyedPropertiesStore.Entry entry;

        private EntryProperties(String key) {
            this.entry = store.Entry(key);
        }

        private void WriteMetadata(GBRom rom, SaveFileManager.SaveIdentity saveIdentity, String contentHash, long lastSeen) {
            entry.Set(sourcePathSuffix, saveIdentity.sourcePath());
            entry.Set(systemIdSuffix, saveIdentity.systemId());
            entry.Set(systemVariantIdSuffix, saveIdentity.systemVariantId());
            entry.Set(systemVariantLabelSuffix, saveIdentity.systemVariantLabel());
            entry.Set(sourceNameSuffix, saveIdentity.sourceName());
            entry.Set(displayNameSuffix, saveIdentity.displayName());
            entry.Set(headerTitleSuffix, rom.GetHeaderTitle());
            entry.Set(cgbCompatibleSuffix, RomConsoleSupport.IsGbc(rom));
            entry.Set(cgbOnlySuffix, RomConsoleSupport.IsCgbOnly(rom));
            entry.Set(expectedSaveSizeSuffix, rom.GetExternalRamSizeBytes());
            entry.Set(contentHashSuffix, contentHash);
            entry.Set(lastSeenSuffix, lastSeen);
            entry.WriteIndexedList(patchNamePrefix, patchNameCountSuffix, saveIdentity.patchNames());
            entry.WriteIndexedList(patchSourcePrefix, patchSourceCountSuffix, rom.GetPatchSourcePaths());
        }

        private void TouchFromExistingRom(GBRom rom, SaveFileManager.SaveIdentity saveIdentity, String contentHash, long now) {
            entry.Set(lastSeenSuffix, Math.max(entry.GetLong(lastSeenSuffix, 0L), now));
            entry.Set(contentHashSuffix, contentHash);
            entry.Set(cgbCompatibleSuffix, RomConsoleSupport.IsGbc(rom));
            entry.Set(cgbOnlySuffix, RomConsoleSupport.IsCgbOnly(rom));
            entry.Set(expectedSaveSizeSuffix, rom.GetExternalRamSizeBytes());
            if (ShouldPreferSourcePath(entry.Get(sourcePathSuffix), saveIdentity.sourcePath())) {
                entry.Set(sourcePathSuffix, saveIdentity.sourcePath());
                entry.Set(systemIdSuffix, saveIdentity.systemId());
                entry.Set(systemVariantIdSuffix, saveIdentity.systemVariantId());
                entry.Set(systemVariantLabelSuffix, saveIdentity.systemVariantLabel());
                entry.Set(sourceNameSuffix, saveIdentity.sourceName());
                entry.Set(displayNameSuffix, saveIdentity.displayName());
                entry.Set(headerTitleSuffix, rom.GetHeaderTitle());
                entry.WriteIndexedList(patchNamePrefix, patchNameCountSuffix, saveIdentity.patchNames());
                entry.WriteIndexedList(patchSourcePrefix, patchSourceCountSuffix, rom.GetPatchSourcePaths());
            }
        }

        private String ResolveContentHash() {
            String storedHash = entry.Get(contentHashSuffix);
            if (!storedHash.isBlank()) {
                return storedHash;
            }

            String sourcePath = entry.Get(sourcePathSuffix);
            if (sourcePath.isBlank()) {
                return "";
            }

            try {
                GBRom rom = LoadTrackedRom(
                        sourcePath,
                        entry.ReadIndexedList(patchNamePrefix, patchNameCountSuffix),
                        entry.ReadIndexedList(patchSourcePrefix, patchSourceCountSuffix));
                String resolvedHash = KeyedPropertiesStore.Hash(rom.ToByteArray());
                entry.Set(contentHashSuffix, resolvedHash);
                return resolvedHash;
            } catch (IOException | IllegalArgumentException exception) {
                return "";
            }
        }

        private StoredGame Read() {
            String sourcePath = entry.Get(sourcePathSuffix);
            String sourceName = entry.Get(sourceNameSuffix);
            String displayName = entry.Get(displayNameSuffix);
            if (sourceName.isBlank() && displayName.isBlank()) {
                return null;
            }

            List<String> patchNames = entry.ReadIndexedList(patchNamePrefix, patchNameCountSuffix);
            List<String> patchSourcePaths = entry.ReadIndexedList(patchSourcePrefix, patchSourceCountSuffix);
            SaveFileManager.SaveIdentity legacyIdentity = new SaveFileManager.SaveIdentity(
                    sourcePath,
                    sourceName,
                    displayName,
                    patchNames,
                    true);
            boolean cgbCompatible = ResolveCgbCompatible(entry.Prefix(), sourcePath, legacyIdentity, patchSourcePaths);
            boolean cgbOnly = ResolveCgbOnly(entry.Prefix(), sourcePath, legacyIdentity, patchSourcePaths);
            SaveFileManager.SaveIdentity saveIdentity = new SaveFileManager.SaveIdentity(
                    entry.Get(systemIdSuffix).isBlank() ? GBRom.systemId : entry.Get(systemIdSuffix),
                    entry.Get(systemVariantIdSuffix).isBlank()
                            ? (cgbCompatible ? GBRom.variantIdGbc : GBRom.variantIdGb)
                            : entry.Get(systemVariantIdSuffix),
                    entry.Get(systemVariantLabelSuffix).isBlank()
                            ? (cgbCompatible ? "GBC" : "GB")
                            : entry.Get(systemVariantLabelSuffix),
                    sourcePath,
                    sourceName,
                    displayName,
                    patchNames,
                    true);

            return new StoredGame(
                    entry.Key(),
                    saveIdentity,
                    patchSourcePaths,
                    entry.Get(headerTitleSuffix),
                    cgbCompatible,
                    cgbOnly,
                    entry.GetInt(expectedSaveSizeSuffix, 0),
                    entry.GetLong(lastSeenSuffix, 0L));
        }

        private void RemoveAll() {
            entry.RemoveAll();
        }

        private void SetString(String suffix, String value) {
            entry.Set(suffix, value);
        }

        private void SetBoolean(String suffix, boolean value) {
            entry.Set(suffix, value);
        }

        private void SetInt(String suffix, int value) {
            entry.Set(suffix, value);
        }

        private void SetLong(String suffix, long value) {
            entry.Set(suffix, value);
        }

        private void WriteIndexedList(String itemPrefix, String countSuffix, List<String> values) {
            entry.WriteIndexedList(itemPrefix, countSuffix, values);
        }
    }
}

