package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.Misc.ROM;
import com.blackaby.Backend.Platform.EmulatorGame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Stores playable ROM images inside GameDuck's managed library.
 */
public final class GameLibraryStore {

    public record LibraryEntry(String key, Path romPath, String sourcePath, String sourceName, String displayName,
                               List<String> patchNames, List<String> patchSourcePaths, String headerTitle,
                               boolean cgbCompatible, boolean cgbOnly, long addedAtMillis, long lastPlayedMillis,
                               boolean favourite) implements EmulatorGame {
        public LibraryEntry {
            patchNames = List.copyOf(patchNames == null ? List.of() : patchNames);
            patchSourcePaths = List.copyOf(patchSourcePaths == null ? List.of() : patchSourcePaths);
        }

        public SaveFileManager.SaveIdentity SaveIdentity() {
            return new SaveFileManager.SaveIdentity(sourcePath, sourceName, displayName, patchNames, true);
        }

        public GameArtProvider.GameArtDescriptor ArtDescriptor() {
            return new GameArtProvider.GameArtDescriptor(sourcePath, sourceName, displayName, headerTitle);
        }

        public ROM LoadRom() throws IOException {
            byte[] romBytes = Files.readAllBytes(romPath);
            return ROM.FromBytes(sourcePath, romBytes, displayName, patchNames, patchSourcePaths);
        }
    }

    private static final String romPathSuffix = ".rom_path";
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
    private static final String addedAtSuffix = ".added_at";
    private static final String lastPlayedSuffix = ".last_played";
    private static final String favouriteSuffix = ".favourite";

    private static final Comparator<LibraryEntry> entrySortOrder = Comparator
            .comparingLong(LibraryEntry::lastPlayedMillis)
            .reversed()
            .thenComparing(entry -> SaveFileManager.BuildFallbackBaseName(entry.SaveIdentity()));

    private static final Store store = new Store();

    private GameLibraryStore() {
    }

    /**
     * Copies a playable ROM image into GameDuck's managed library and updates its metadata.
     *
     * @param rom playable ROM image
     * @return persisted library entry
     */
    public static synchronized LibraryEntry RememberGame(ROM rom) {
        if (rom == null) {
            throw new IllegalArgumentException("A ROM is required.");
        }

        store.EnsureLoaded();
        String key = BuildEntryKey(rom);
        EntryProperties entry = new EntryProperties(key);
        Path storedPath = RomStorageDirectory().resolve(BuildStoredFilename(rom, key));

        try {
            Files.createDirectories(storedPath.getParent());
            Files.write(storedPath, rom.ToByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store the ROM in the managed library.", exception);
        }

        long now = System.currentTimeMillis();
        entry.WriteMetadata(rom, storedPath, now);
        store.Persist();
        return entry.Read();
    }

    /**
     * Returns all managed library entries.
     *
     * @return library entries
     */
    public static synchronized List<LibraryEntry> GetEntries() {
        store.EnsureLoaded();
        return store.StoredKeys().stream()
                .map(GameLibraryStore::ReadEntry)
                .filter(entry -> entry != null && Files.isRegularFile(entry.romPath()))
                .sorted(entrySortOrder)
                .toList();
    }

    /**
     * Returns the most recently played library entries up to the requested limit.
     *
     * @param limit maximum number of entries to return
     * @return recent entries ordered newest first
     */
    public static synchronized List<LibraryEntry> GetRecentEntries(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return GetEntries().stream()
                .filter(entry -> entry.lastPlayedMillis() > 0L)
                .limit(limit)
                .toList();
    }

    /**
     * Marks a managed library entry as favourite or not favourite.
     *
     * @param key entry key
     * @param favourite whether the entry should be favourited
     */
    public static synchronized void SetFavourite(String key, boolean favourite) {
        if (key == null || key.isBlank()) {
            return;
        }

        store.EnsureLoaded();
        EntryProperties entry = new EntryProperties(key);
        if (!entry.Exists()) {
            return;
        }

        entry.Set(favouriteSuffix, favourite);
        store.Persist();
    }

    /**
     * Deletes a managed library entry and its stored ROM image.
     *
     * @param key entry key
     * @throws IOException when the stored ROM cannot be removed
     */
    public static synchronized void DeleteEntry(String key) throws IOException {
        if (key == null || key.isBlank()) {
            return;
        }

        store.EnsureLoaded();
        LibraryEntry entry = ReadEntry(key);
        if (entry == null) {
            return;
        }

        Files.deleteIfExists(entry.romPath());
        new EntryProperties(key).RemoveAll();
        store.Persist();
    }

    /**
     * Returns whether a managed library entry is favourited.
     *
     * @param key entry key
     * @return {@code true} when the entry is marked as favourite
     */
    public static synchronized boolean IsFavourite(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        store.EnsureLoaded();
        return new EntryProperties(key).GetBoolean(favouriteSuffix, false);
    }

    /**
     * Clears the recent-play history for all managed library entries.
     */
    public static synchronized void ClearRecentHistory() {
        store.EnsureLoaded();
        for (String key : store.StoredKeys()) {
            EntryProperties entry = new EntryProperties(key);
            if (entry.Exists()) {
                entry.Set(lastPlayedSuffix, 0L);
            }
        }
        store.Persist();
    }

    private static LibraryEntry ReadEntry(String key) {
        return new EntryProperties(key).Read();
    }

    private static String BuildEntryKey(ROM rom) {
        return KeyedPropertiesStore.Hash(String.join("|",
                KeyedPropertiesStore.Hash(rom.ToByteArray()),
                KeyedPropertiesStore.NullToEmpty(rom.GetSourceName()),
                String.join("|", rom.GetPatchNames())));
    }

    private static String BuildStoredFilename(ROM rom, String key) {
        String baseName = GameMetadataStore.GetLibretroTitle(rom).orElse(SaveFileManager.BuildFallbackBaseName(rom));
        String extension = ResolveExtension(rom.GetSourcePath());
        return SanitiseFileComponent(baseName) + " [" + key.substring(0, Math.min(8, key.length())) + "]" + extension;
    }

    private static String ResolveExtension(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return ".gb";
        }

        String lowerPath = sourcePath.toLowerCase();
        if (lowerPath.endsWith(".gbc") || lowerPath.endsWith(".cgb")) {
            return ".gbc";
        }
        return lowerPath.endsWith(".gb") ? ".gb" : ".gb";
    }

    private static String SanitiseFileComponent(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        String cleaned = value.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ")
                .replaceAll("\\.+$", "")
                .trim();
        return cleaned.isBlank() ? "unknown" : cleaned;
    }

    private static Path RomStorageDirectory() {
        String configuredPath = System.getProperty("gameduck.library_rom_dir");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of("library", "roms");
    }

    private static boolean ResolveCgbCompatible(String prefix, Path romPath, String sourcePath) {
        String storedValue = store.EntryValue(prefix + cgbCompatibleSuffix);
        if (storedValue != null) {
            return Boolean.parseBoolean(storedValue);
        }

        boolean cgbCompatible = RomConsoleSupport.IsGbc(romPath);
        if (!cgbCompatible) {
            cgbCompatible = RomConsoleSupport.IsProbablyGbc(sourcePath);
        }

        store.SetValue(prefix + cgbCompatibleSuffix, String.valueOf(cgbCompatible));
        store.Persist();
        return cgbCompatible;
    }

    private static boolean ResolveCgbOnly(String prefix, Path romPath) {
        String storedValue = store.EntryValue(prefix + cgbOnlySuffix);
        if (storedValue != null) {
            return Boolean.parseBoolean(storedValue);
        }

        boolean cgbOnly = RomConsoleSupport.IsCgbOnly(romPath);
        store.SetValue(prefix + cgbOnlySuffix, String.valueOf(cgbOnly));
        store.Persist();
        return cgbOnly;
    }

    private static final class Store extends KeyedPropertiesStore {
        private Store() {
            super("entry.", sourceNameSuffix, "gameduck.library_metadata_path",
                    Path.of("cache", "game-library.properties"), "GameDuck library");
        }

        private String EntryValue(String key) {
            return RawProperty(key);
        }

        private void SetValue(String key, String value) {
            SetRawProperty(key, value);
        }
    }

    private static final class EntryProperties {
        private final KeyedPropertiesStore.Entry entry;

        private EntryProperties(String key) {
            this.entry = store.Entry(key);
        }

        private boolean Exists() {
            return entry.Has(sourceNameSuffix);
        }

        private void WriteMetadata(ROM rom, Path storedPath, long now) {
            entry.Set(romPathSuffix, storedPath.toString());
            entry.Set(sourcePathSuffix, rom.GetSourcePath());
            entry.Set(sourceNameSuffix, rom.GetSourceName());
            entry.Set(displayNameSuffix, rom.GetName());
            entry.Set(headerTitleSuffix, rom.GetHeaderTitle());
            entry.Set(cgbCompatibleSuffix, RomConsoleSupport.IsGbc(rom));
            entry.Set(cgbOnlySuffix, RomConsoleSupport.IsCgbOnly(rom));
            entry.Set(addedAtSuffix, entry.GetLong(addedAtSuffix, now));
            entry.Set(lastPlayedSuffix, now);
            entry.WriteIndexedList(patchNamePrefix, patchNameCountSuffix, rom.GetPatchNames());
            entry.WriteIndexedList(patchSourcePrefix, patchSourceCountSuffix, rom.GetPatchSourcePaths());
        }

        private LibraryEntry Read() {
            String romPathValue = entry.Get(romPathSuffix);
            String sourceName = entry.Get(sourceNameSuffix);
            String displayName = entry.Get(displayNameSuffix);
            if (romPathValue.isBlank() || (sourceName.isBlank() && displayName.isBlank())) {
                return null;
            }

            Path romPath = Path.of(romPathValue);
            String sourcePath = entry.Get(sourcePathSuffix);
            return new LibraryEntry(
                    entry.Key(),
                    romPath,
                    sourcePath,
                    sourceName,
                    displayName,
                    entry.ReadIndexedList(patchNamePrefix, patchNameCountSuffix),
                    entry.ReadIndexedList(patchSourcePrefix, patchSourceCountSuffix),
                    entry.Get(headerTitleSuffix),
                    ResolveCgbCompatible(entry.Prefix(), romPath, sourcePath),
                    ResolveCgbOnly(entry.Prefix(), romPath),
                    entry.GetLong(addedAtSuffix, 0L),
                    entry.GetLong(lastPlayedSuffix, 0L),
                    entry.GetBoolean(favouriteSuffix, false));
        }

        private void RemoveAll() {
            entry.RemoveAll();
        }

        private void Set(String suffix, boolean value) {
            entry.Set(suffix, value);
        }

        private void Set(String suffix, long value) {
            entry.Set(suffix, value);
        }

        private boolean GetBoolean(String suffix, boolean fallback) {
            return entry.GetBoolean(suffix, fallback);
        }
    }
}

