package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.Emulation.Misc.ROM;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

/**
 * Stores playable ROM images inside GameDuck's managed library.
 */
public final class GameLibraryStore {

    public record LibraryEntry(String key, Path romPath, String sourcePath, String sourceName, String displayName,
                               List<String> patchNames, List<String> patchSourcePaths, String headerTitle,
                               boolean cgbCompatible, boolean cgbOnly, long addedAtMillis, long lastPlayedMillis,
                               boolean favourite) {
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

    private static final String entryPrefix = "entry.";
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

    private static final Properties properties = new Properties();
    private static boolean loaded;

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

        EnsureLoaded();
        String key = BuildEntryKey(rom);
        String prefix = entryPrefix + key;
        Path storedPath = RomStorageDirectory().resolve(BuildStoredFilename(rom, key));

        try {
            Files.createDirectories(storedPath.getParent());
            Files.write(storedPath, rom.ToByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store the ROM in the managed library.", exception);
        }

        long now = System.currentTimeMillis();
        long addedAt = ParseLong(properties.getProperty(prefix + addedAtSuffix), now);
        properties.setProperty(prefix + romPathSuffix, storedPath.toString());
        properties.setProperty(prefix + sourcePathSuffix, NullToEmpty(rom.GetSourcePath()));
        properties.setProperty(prefix + sourceNameSuffix, NullToEmpty(rom.GetSourceName()));
        properties.setProperty(prefix + displayNameSuffix, NullToEmpty(rom.GetName()));
        properties.setProperty(prefix + headerTitleSuffix, NullToEmpty(rom.GetHeaderTitle()));
        properties.setProperty(prefix + cgbCompatibleSuffix, String.valueOf(RomConsoleSupport.IsGbc(rom)));
        properties.setProperty(prefix + cgbOnlySuffix, String.valueOf(RomConsoleSupport.IsCgbOnly(rom)));
        properties.setProperty(prefix + addedAtSuffix, String.valueOf(addedAt));
        properties.setProperty(prefix + lastPlayedSuffix, String.valueOf(now));
        WriteIndexedList(prefix + patchNamePrefix, prefix + patchNameCountSuffix, rom.GetPatchNames());
        WriteIndexedList(prefix + patchSourcePrefix, prefix + patchSourceCountSuffix, rom.GetPatchSourcePaths());
        Persist();
        return ReadEntry(key);
    }

    /**
     * Returns all managed library entries.
     *
     * @return library entries
     */
    public static synchronized List<LibraryEntry> GetEntries() {
        EnsureLoaded();
        List<LibraryEntry> entries = new ArrayList<>();
        for (String key : GetStoredKeys()) {
            LibraryEntry entry = ReadEntry(key);
            if (entry != null && Files.isRegularFile(entry.romPath())) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparingLong(LibraryEntry::lastPlayedMillis).reversed()
                .thenComparing(entry -> SaveFileManager.BuildFallbackBaseName(entry.SaveIdentity())));
        return List.copyOf(entries);
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

        EnsureLoaded();
        String prefix = entryPrefix + key;
        if (properties.getProperty(prefix + sourceNameSuffix) == null) {
            return;
        }

        properties.setProperty(prefix + favouriteSuffix, String.valueOf(favourite));
        Persist();
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

        EnsureLoaded();
        return Boolean.parseBoolean(properties.getProperty(entryPrefix + key + favouriteSuffix, "false"));
    }

    private static LibraryEntry ReadEntry(String key) {
        String prefix = entryPrefix + key;
        String romPathValue = properties.getProperty(prefix + romPathSuffix, "");
        String sourceName = properties.getProperty(prefix + sourceNameSuffix, "");
        String displayName = properties.getProperty(prefix + displayNameSuffix, "");
        if (romPathValue.isBlank() || (sourceName.isBlank() && displayName.isBlank())) {
            return null;
        }

        return new LibraryEntry(
                key,
                Path.of(romPathValue),
                properties.getProperty(prefix + sourcePathSuffix, ""),
                sourceName,
                displayName,
                ReadIndexedList(prefix + patchNamePrefix, prefix + patchNameCountSuffix),
                ReadIndexedList(prefix + patchSourcePrefix, prefix + patchSourceCountSuffix),
                properties.getProperty(prefix + headerTitleSuffix, ""),
                ResolveCgbCompatible(prefix, Path.of(romPathValue), properties.getProperty(prefix + sourcePathSuffix, "")),
                ResolveCgbOnly(prefix, Path.of(romPathValue), properties.getProperty(prefix + sourcePathSuffix, "")),
                ParseLong(properties.getProperty(prefix + addedAtSuffix), 0L),
                ParseLong(properties.getProperty(prefix + lastPlayedSuffix), 0L),
                Boolean.parseBoolean(properties.getProperty(prefix + favouriteSuffix, "false")));
    }

    private static List<String> GetStoredKeys() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String propertyName : properties.stringPropertyNames()) {
            if (!propertyName.startsWith(entryPrefix) || !propertyName.endsWith(sourceNameSuffix)) {
                continue;
            }
            String key = propertyName.substring(entryPrefix.length(), propertyName.length() - sourceNameSuffix.length());
            if (!key.isBlank()) {
                keys.add(key);
            }
        }
        return List.copyOf(keys);
    }

    private static String BuildEntryKey(ROM rom) {
        StringBuilder builder = new StringBuilder();
        builder.append(Hash(rom.ToByteArray())).append('|');
        builder.append(NullToEmpty(rom.GetSourceName())).append('|');
        builder.append(String.join("|", rom.GetPatchNames()));
        return Hash(builder.toString());
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
        if (lowerPath.endsWith(".gb")) {
            return ".gb";
        }
        return ".gb";
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

    private static void WriteIndexedList(String itemPrefix, String countKey, List<String> values) {
        int previousCount = ParseInt(properties.getProperty(countKey), 0);
        for (int index = 0; index < previousCount; index++) {
            properties.remove(itemPrefix + index);
        }

        List<String> safeValues = values == null ? List.of() : values;
        properties.setProperty(countKey, String.valueOf(safeValues.size()));
        for (int index = 0; index < safeValues.size(); index++) {
            properties.setProperty(itemPrefix + index, NullToEmpty(safeValues.get(index)));
        }
    }

    private static List<String> ReadIndexedList(String itemPrefix, String countKey) {
        int count = ParseInt(properties.getProperty(countKey), 0);
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(properties.getProperty(itemPrefix + index, ""));
        }
        return List.copyOf(values);
    }

    private static void EnsureLoaded() {
        if (loaded) {
            return;
        }

        properties.clear();
        Path metadataPath = MetadataPath();
        if (Files.exists(metadataPath)) {
            try (InputStream inputStream = Files.newInputStream(metadataPath)) {
                properties.load(inputStream);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        loaded = true;
    }

    private static void Persist() {
        Path metadataPath = MetadataPath();
        try {
            Path parent = metadataPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream outputStream = Files.newOutputStream(metadataPath)) {
                properties.store(outputStream, "GameDuck library");
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private static Path MetadataPath() {
        String configuredPath = System.getProperty("gameduck.library_metadata_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of("cache", "game-library.properties");
    }

    private static int ParseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long ParseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String NullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean ResolveCgbCompatible(String prefix, Path romPath, String sourcePath) {
        String storedValue = properties.getProperty(prefix + cgbCompatibleSuffix);
        if (storedValue != null) {
            return Boolean.parseBoolean(storedValue);
        }

        boolean cgbCompatible = RomConsoleSupport.IsGbc(romPath);
        if (!cgbCompatible) {
            cgbCompatible = RomConsoleSupport.IsProbablyGbc(sourcePath);
        }

        properties.setProperty(prefix + cgbCompatibleSuffix, String.valueOf(cgbCompatible));
        Persist();
        return cgbCompatible;
    }

    private static boolean ResolveCgbOnly(String prefix, Path romPath, String sourcePath) {
        String storedValue = properties.getProperty(prefix + cgbOnlySuffix);
        if (storedValue != null) {
            return Boolean.parseBoolean(storedValue);
        }

        boolean cgbOnly = RomConsoleSupport.IsCgbOnly(romPath);
        properties.setProperty(prefix + cgbOnlySuffix, String.valueOf(cgbOnly));
        Persist();
        return cgbOnly;
    }

    private static String Hash(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is unavailable.", exception);
        }
    }

    private static String Hash(String value) {
        return Hash(value.getBytes(StandardCharsets.UTF_8));
    }
}
