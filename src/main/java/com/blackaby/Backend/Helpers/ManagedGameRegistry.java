package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.Misc.ROM;
import com.blackaby.Backend.Platform.EmulatorGame;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
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
        public Optional<ROM> LoadRom() {
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

    private static final Comparator<StoredGame> gameSortOrder = Comparator
            .comparingLong(StoredGame::lastSeenMillis)
            .reversed()
            .thenComparing(game -> SaveFileManager.BuildFallbackBaseName(game.saveIdentity()));

    private static final Store store = new Store();

    private ManagedGameRegistry() {
    }

    /**
     * Records a save-capable ROM so it can appear in the multi-game save manager.
     *
     * @param rom loaded ROM
     */
    public static synchronized void RememberGame(ROM rom) {
        if (rom == null || !rom.HasBatteryBackedSave()) {
            return;
        }

        store.EnsureLoaded();
        String key = BuildGameKey(rom);
        EntryProperties entry = new EntryProperties(key);
        SaveFileManager.SaveIdentity saveIdentity = SaveFileManager.SaveIdentity.FromRom(rom);

        entry.WriteMetadata(rom, saveIdentity);
        store.Persist();
    }

    /**
     * Returns the known save-capable games sorted by most recent activity.
     *
     * @return tracked games
     */
    public static synchronized List<StoredGame> GetKnownGames() {
        store.EnsureLoaded();
        return store.StoredKeys().stream()
                .map(ManagedGameRegistry::ReadStoredGame)
                .filter(game -> game != null)
                .sorted(gameSortOrder)
                .toList();
    }

    /**
     * Builds the registry key used for a loaded ROM.
     *
     * @param rom loaded ROM
     * @return stable registry key
     */
    public static String BuildGameKey(ROM rom) {
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

    private static StoredGame ReadStoredGame(String key) {
        return new EntryProperties(key).Read();
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

    private static ROM LoadTrackedRom(String sourcePath, List<String> patchNames, List<String> patchSourcePaths) throws IOException {
        ROM rom = new ROM(sourcePath);
        List<String> safePatchNames = patchNames == null ? List.of() : patchNames;
        List<String> safePatchSourcePaths = patchSourcePaths == null ? List.of() : patchSourcePaths;
        for (int index = 0; index < safePatchSourcePaths.size(); index++) {
            String patchSourcePath = safePatchSourcePaths.get(index);
            String patchName = index < safePatchNames.size() ? safePatchNames.get(index) : null;
            if (patchSourcePath == null || patchSourcePath.isBlank()) {
                return rom;
            }
            rom = ROM.LoadPatched(rom, patchSourcePath, patchName);
        }
        return rom;
    }

    private static final class Store extends KeyedPropertiesStore {
        private Store() {
            super("game.", sourceNameSuffix, "gameduck.managed_games_path",
                    Path.of("cache", "managed-games.properties"), "GameDuck managed games");
        }
    }

    private static final class EntryProperties {
        private final KeyedPropertiesStore.Entry entry;

        private EntryProperties(String key) {
            this.entry = store.Entry(key);
        }

        private void WriteMetadata(ROM rom, SaveFileManager.SaveIdentity saveIdentity) {
            entry.Set(sourcePathSuffix, saveIdentity.sourcePath());
            entry.Set(sourceNameSuffix, saveIdentity.sourceName());
            entry.Set(displayNameSuffix, saveIdentity.displayName());
            entry.Set(headerTitleSuffix, rom.GetHeaderTitle());
            entry.Set(cgbCompatibleSuffix, RomConsoleSupport.IsGbc(rom));
            entry.Set(cgbOnlySuffix, RomConsoleSupport.IsCgbOnly(rom));
            entry.Set(expectedSaveSizeSuffix, rom.GetExternalRamSizeBytes());
            entry.Set(lastSeenSuffix, System.currentTimeMillis());
            entry.WriteIndexedList(patchNamePrefix, patchNameCountSuffix, saveIdentity.patchNames());
            entry.WriteIndexedList(patchSourcePrefix, patchSourceCountSuffix, rom.GetPatchSourcePaths());
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
            SaveFileManager.SaveIdentity saveIdentity = new SaveFileManager.SaveIdentity(
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
                    ResolveCgbCompatible(entry.Prefix(), sourcePath, saveIdentity, patchSourcePaths),
                    ResolveCgbOnly(entry.Prefix(), sourcePath, saveIdentity, patchSourcePaths),
                    entry.GetInt(expectedSaveSizeSuffix, 0),
                    entry.GetLong(lastSeenSuffix, 0L));
        }
    }
}

