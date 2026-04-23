package com.blackaby.Misc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Stores saved app themes as JSON documents in the managed theme directory.
 */
public final class ThemeStore {

    public record LoadResult(boolean migratedFromLegacy, boolean seededDefaults) {
    }

    private static final String savedThemeListKey = "theme.saved.names";
    private static final String savedThemePrefix = "theme.saved.";

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private ThemeStore() {
    }

    /**
     * Loads and migrates the managed theme library.
     *
     * @param legacyProperties config properties containing legacy saved themes
     * @return load result describing migration or default seeding work
     */
    public static LoadResult Load(Properties legacyProperties) {
        boolean migratedFromLegacy = false;
        boolean seededDefaults;

        try {
            Files.createDirectories(ThemeDirectory());
            migratedFromLegacy = MigrateLegacyThemes(legacyProperties);
            seededDefaults = EnsureDefaultThemesPresent();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the managed theme library.", exception);
        }

        return new LoadResult(migratedFromLegacy, seededDefaults);
    }

    /**
     * Saves a theme into the managed JSON library.
     *
     * @param theme theme to save
     */
    public static void SaveTheme(AppTheme theme) {
        if (theme == null || theme.Name() == null || theme.Name().isBlank()) {
            throw new IllegalArgumentException("A theme name is required.");
        }

        try {
            Files.createDirectories(ThemeDirectory());
            Files.writeString(ThemePath(theme.Name()), ToJson(theme), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save the theme JSON.", exception);
        }
    }

    /**
     * Returns the saved theme names with bundled defaults listed first.
     *
     * @return saved theme names
     */
    public static List<String> SavedThemeNames() {
        Map<String, AppTheme> themes = LoadThemes();
        List<String> ordered = new ArrayList<>();
        for (AppThemePreset preset : AppThemePreset.values()) {
            if (themes.containsKey(preset.Label())) {
                ordered.add(preset.Label());
            }
        }

        themes.keySet().stream()
                .filter(name -> !ordered.contains(name))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(ordered::add);
        return List.copyOf(ordered);
    }

    /**
     * Loads one saved theme by display name.
     *
     * @param name theme name
     * @return theme copy, or {@code null} when missing
     */
    public static AppTheme FindTheme(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        Path path = ThemePath(name);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return null;
        }

        try {
            return ReadTheme(path);
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Deletes one saved theme JSON.
     *
     * @param name theme name
     */
    public static void DeleteTheme(String name) {
        if (name == null || name.isBlank()) {
            return;
        }

        try {
            Files.deleteIfExists(ThemePath(name));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete the saved theme JSON.", exception);
        }
    }

    /**
     * Restores the bundled default themes into the managed JSON library.
     */
    public static void RestoreDefaultThemes() {
        for (AppThemePreset preset : AppThemePreset.values()) {
            SaveTheme(preset.Theme());
        }
    }

    public static Path ThemeDirectoryPath() {
        return ThemeDirectory();
    }

    static Path ThemeDirectory() {
        String configuredPath = System.getProperty("gameduck.theme_dir");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of(System.getProperty("user.home"), ".gameduck", "themes");
    }

    private static Map<String, AppTheme> LoadThemes() {
        Map<String, AppTheme> themes = new LinkedHashMap<>();
        for (Path path : ListThemePaths()) {
            try {
                AppTheme theme = ReadTheme(path);
                themes.putIfAbsent(theme.Name(), theme);
            } catch (IOException | IllegalArgumentException exception) {
                // Skip unreadable or malformed theme files.
            }
        }
        return themes;
    }

    private static List<Path> ListThemePaths() {
        Path directory = ThemeDirectory();
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static boolean MigrateLegacyThemes(Properties legacyProperties) {
        if (legacyProperties == null) {
            return false;
        }

        List<String> themeNames = DecodeNames(legacyProperties.getProperty(savedThemeListKey, ""));
        if (themeNames.isEmpty()) {
            return false;
        }

        boolean migrated = false;
        for (String themeName : themeNames) {
            AppTheme theme = ReadLegacyTheme(legacyProperties, themeName);
            if (theme == null) {
                continue;
            }
            SaveTheme(theme);
            RemoveLegacyTheme(legacyProperties, themeName);
            migrated = true;
        }

        legacyProperties.remove(savedThemeListKey);
        return migrated;
    }

    private static boolean EnsureDefaultThemesPresent() {
        boolean wroteAnyDefaults = false;
        for (AppThemePreset preset : AppThemePreset.values()) {
            Path path = ThemePath(preset.Label());
            if (Files.exists(path)) {
                continue;
            }
            SaveTheme(preset.Theme());
            wroteAnyDefaults = true;
        }
        return wroteAnyDefaults;
    }

    private static AppTheme ReadLegacyTheme(Properties legacyProperties, String themeName) {
        String encodedName = EncodeName(themeName);
        AppTheme theme = AppThemePreset.HARBOR.Theme().Renamed(themeName);
        boolean found = false;

        for (AppThemeColorRole role : AppThemeColorRole.values()) {
            String value = legacyProperties.getProperty(savedThemePrefix + encodedName + "." + role.name());
            if (value == null || value.isBlank()) {
                continue;
            }
            theme.SetCoreColour(role, value);
            found = true;
        }

        return found ? theme : null;
    }

    private static void RemoveLegacyTheme(Properties legacyProperties, String themeName) {
        String encodedName = EncodeName(themeName);
        for (AppThemeColorRole role : AppThemeColorRole.values()) {
            legacyProperties.remove(savedThemePrefix + encodedName + "." + role.name());
        }
    }

    private static Path ThemePath(String themeName) {
        String safeName = SanitiseFileComponent(themeName);
        int identityHash = themeName.hashCode();
        return ThemeDirectory().resolve(safeName + " [" + String.format("%08X", identityHash) + "].json");
    }

    private static AppTheme ReadTheme(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        JsonElement rootElement = JsonParser.parseString(json);
        if (!rootElement.isJsonObject()) {
            throw new IllegalArgumentException("Theme JSON must contain an object root.");
        }

        JsonObject rootObject = rootElement.getAsJsonObject();
        String themeName = ReadString(rootObject, "name");
        if (themeName == null || themeName.isBlank()) {
            throw new IllegalArgumentException("Theme JSON must include a name.");
        }

        AppTheme theme = AppThemePreset.HARBOR.Theme().Renamed(themeName);
        JsonObject colorsObject = rootObject.getAsJsonObject("colors");
        if (colorsObject == null) {
            throw new IllegalArgumentException("Theme JSON must include a colors object.");
        }

        for (AppThemeColorRole role : AppThemeColorRole.values()) {
            String value = ReadString(colorsObject, role.name());
            if (value != null && !value.isBlank()) {
                theme.SetCoreColour(role, value);
            }
        }

        return theme;
    }

    private static String ToJson(AppTheme theme) {
        JsonObject rootObject = new JsonObject();
        rootObject.addProperty("name", theme.Name());

        JsonObject colorsObject = new JsonObject();
        for (AppThemeColorRole role : AppThemeColorRole.values()) {
            colorsObject.addProperty(role.name(), theme.CoreHex(role));
        }
        rootObject.add("colors", colorsObject);
        return gson.toJson(rootObject);
    }

    private static String ReadString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static List<String> DecodeNames(String storedNames) {
        if (storedNames == null || storedNames.isBlank()) {
            return List.of();
        }

        List<String> decodedNames = new ArrayList<>();
        for (String encodedName : storedNames.split(",")) {
            if (encodedName == null || encodedName.isBlank()) {
                continue;
            }

            try {
                decodedNames.add(new String(
                        Base64.getUrlDecoder().decode(encodedName),
                        StandardCharsets.UTF_8));
            } catch (IllegalArgumentException exception) {
                // Skip malformed legacy names.
            }
        }
        return decodedNames;
    }

    private static String EncodeName(String name) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));
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
}
