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
 * Persists the set of known save-capable games so save management is not tied
 * to the currently running ROM.
 */
public final class ManagedGameRegistry {

    public record StoredGame(String key, SaveFileManager.SaveIdentity saveIdentity, List<String> patchSourcePaths,
                             String headerTitle, boolean cgbCompatible, int expectedSaveSizeBytes, long lastSeenMillis) {
        public StoredGame {
            patchSourcePaths = List.copyOf(patchSourcePaths == null ? List.of() : patchSourcePaths);
        }

        /**
         * Attempts to reconstruct the tracked ROM from its stored source paths.
         *
         * @return reconstructed ROM when the base and patch files are still available
         */
        public java.util.Optional<ROM> LoadRom() {
            if (saveIdentity == null || saveIdentity.sourcePath() == null || saveIdentity.sourcePath().isBlank()) {
                return java.util.Optional.empty();
            }

            try {
                ROM rom = new ROM(saveIdentity.sourcePath());
                List<String> patchNames = saveIdentity.patchNames();
                for (int index = 0; index < patchSourcePaths.size(); index++) {
                    String patchSourcePath = patchSourcePaths.get(index);
                    String patchName = index < patchNames.size() ? patchNames.get(index) : null;
                    if (patchSourcePath == null || patchSourcePath.isBlank()) {
                        return java.util.Optional.empty();
                    }
                    rom = ROM.LoadPatched(rom, patchSourcePath, patchName);
                }
                return java.util.Optional.of(rom);
            } catch (IOException | IllegalArgumentException exception) {
                return java.util.Optional.empty();
            }
        }
    }

    private static final String gamePrefix = "game.";
    private static final String sourcePathSuffix = ".source_path";
    private static final String sourceNameSuffix = ".source_name";
    private static final String displayNameSuffix = ".display_name";
    private static final String patchNameCountSuffix = ".patch_name_count";
    private static final String patchSourceCountSuffix = ".patch_source_count";
    private static final String patchNamePrefix = ".patch_name.";
    private static final String patchSourcePrefix = ".patch_source.";
    private static final String headerTitleSuffix = ".header_title";
    private static final String cgbCompatibleSuffix = ".cgb_compatible";
    private static final String expectedSaveSizeSuffix = ".expected_save_size";
    private static final String lastSeenSuffix = ".last_seen";

    private static final Properties properties = new Properties();
    private static boolean loaded;

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

        EnsureLoaded();
        String key = BuildGameKey(rom);
        String prefix = gamePrefix + key;
        SaveFileManager.SaveIdentity saveIdentity = SaveFileManager.SaveIdentity.FromRom(rom);

        properties.setProperty(prefix + sourcePathSuffix, NullToEmpty(saveIdentity.sourcePath()));
        properties.setProperty(prefix + sourceNameSuffix, NullToEmpty(saveIdentity.sourceName()));
        properties.setProperty(prefix + displayNameSuffix, NullToEmpty(saveIdentity.displayName()));
        properties.setProperty(prefix + headerTitleSuffix, NullToEmpty(rom.GetHeaderTitle()));
        properties.setProperty(prefix + cgbCompatibleSuffix, String.valueOf(RomConsoleSupport.IsGbc(rom)));
        properties.setProperty(prefix + expectedSaveSizeSuffix, String.valueOf(rom.GetExternalRamSizeBytes()));
        properties.setProperty(prefix + lastSeenSuffix, String.valueOf(System.currentTimeMillis()));

        WriteIndexedList(prefix + patchNamePrefix, prefix + patchNameCountSuffix, saveIdentity.patchNames());
        WriteIndexedList(prefix + patchSourcePrefix, prefix + patchSourceCountSuffix, rom.GetPatchSourcePaths());
        Persist();
    }

    /**
     * Returns the known save-capable games sorted by most recent activity.
     *
     * @return tracked games
     */
    public static synchronized List<StoredGame> GetKnownGames() {
        EnsureLoaded();
        List<StoredGame> games = new ArrayList<>();
        for (String key : GetStoredKeys()) {
            StoredGame game = ReadStoredGame(key);
            if (game != null) {
                games.add(game);
            }
        }
        games.sort(Comparator.comparingLong(StoredGame::lastSeenMillis).reversed()
                .thenComparing(game -> SaveFileManager.BuildFallbackBaseName(game.saveIdentity())));
        return List.copyOf(games);
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

    private static String BuildGameKey(String sourcePath, String sourceName, List<String> patchSourcePaths, List<String> patchNames) {
        StringBuilder builder = new StringBuilder();
        builder.append(NullToEmpty(sourcePath)).append('|');
        builder.append(NullToEmpty(sourceName)).append('|');
        for (String patchSourcePath : patchSourcePaths == null ? List.<String>of() : patchSourcePaths) {
            builder.append(NullToEmpty(patchSourcePath)).append('|');
        }
        builder.append('|');
        for (String patchName : patchNames == null ? List.<String>of() : patchNames) {
            builder.append(NullToEmpty(patchName)).append('|');
        }
        return Hash(builder.toString());
    }

    private static StoredGame ReadStoredGame(String key) {
        String prefix = gamePrefix + key;
        String sourcePath = properties.getProperty(prefix + sourcePathSuffix, "");
        String sourceName = properties.getProperty(prefix + sourceNameSuffix, "");
        String displayName = properties.getProperty(prefix + displayNameSuffix, "");
        if (sourceName.isBlank() && displayName.isBlank()) {
            return null;
        }

        List<String> patchNames = ReadIndexedList(prefix + patchNamePrefix, prefix + patchNameCountSuffix);
        List<String> patchSourcePaths = ReadIndexedList(prefix + patchSourcePrefix, prefix + patchSourceCountSuffix);
        SaveFileManager.SaveIdentity saveIdentity = new SaveFileManager.SaveIdentity(
                sourcePath,
                sourceName,
                displayName,
                patchNames,
                true);

        return new StoredGame(
                key,
                saveIdentity,
                patchSourcePaths,
                properties.getProperty(prefix + headerTitleSuffix, ""),
                ResolveCgbCompatible(prefix, sourcePath, saveIdentity, patchSourcePaths),
                ParseInt(properties.getProperty(prefix + expectedSaveSizeSuffix), 0),
                ParseLong(properties.getProperty(prefix + lastSeenSuffix), 0L));
    }

    private static List<String> GetStoredKeys() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String propertyName : properties.stringPropertyNames()) {
            if (!propertyName.startsWith(gamePrefix) || !propertyName.endsWith(sourceNameSuffix)) {
                continue;
            }
            String key = propertyName.substring(gamePrefix.length(), propertyName.length() - sourceNameSuffix.length());
            if (!key.isBlank()) {
                keys.add(key);
            }
        }
        return List.copyOf(keys);
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
        Path registryPath = RegistryPath();
        if (Files.exists(registryPath)) {
            try (InputStream inputStream = Files.newInputStream(registryPath)) {
                properties.load(inputStream);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        loaded = true;
    }

    private static void Persist() {
        Path registryPath = RegistryPath();
        try {
            Path parent = registryPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream outputStream = Files.newOutputStream(registryPath)) {
                properties.store(outputStream, "GameDuck managed games");
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private static Path RegistryPath() {
        String configuredPath = System.getProperty("gameduck.managed_games_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of("cache", "managed-games.properties");
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

    private static boolean ResolveCgbCompatible(String prefix, String sourcePath, SaveFileManager.SaveIdentity saveIdentity,
                                                List<String> patchSourcePaths) {
        String storedValue = properties.getProperty(prefix + cgbCompatibleSuffix);
        if (storedValue != null) {
            return Boolean.parseBoolean(storedValue);
        }

        boolean cgbCompatible = RomConsoleSupport.IsProbablyGbc(sourcePath);
        if (sourcePath != null && !sourcePath.isBlank()) {
            try {
                ROM rom = new ROM(sourcePath);
                List<String> patchNames = saveIdentity == null ? List.of() : saveIdentity.patchNames();
                for (int index = 0; index < patchSourcePaths.size(); index++) {
                    String patchSourcePath = patchSourcePaths.get(index);
                    String patchName = index < patchNames.size() ? patchNames.get(index) : null;
                    if (patchSourcePath == null || patchSourcePath.isBlank()) {
                        cgbCompatible = RomConsoleSupport.IsGbc(rom);
                        break;
                    }
                    rom = ROM.LoadPatched(rom, patchSourcePath, patchName);
                }
                cgbCompatible = RomConsoleSupport.IsGbc(rom);
            } catch (IOException | IllegalArgumentException exception) {
                // Keep the best-effort extension fallback when the original files are unavailable.
            }
        }

        properties.setProperty(prefix + cgbCompatibleSuffix, String.valueOf(cgbCompatible));
        Persist();
        return cgbCompatible;
    }

    private static String Hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is unavailable.", exception);
        }
    }
}
