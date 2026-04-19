package com.blackaby.Misc;

import com.blackaby.Backend.GB.Graphics.GBColor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Stores the editable DMG and GBC palette data in a dedicated JSON document.
 */
public final class PaletteStore {

    /**
     * Result of loading the palette store.
     *
     * @param store             loaded palette store
     * @param migratedFromLegacy whether the store was built from legacy properties data
     */
    public record LoadResult(PaletteStore store, boolean migratedFromLegacy) {
    }

    /**
     * Result of merging imported palettes into the current store.
     *
     * @param importedCount number of palettes added
     * @param duplicateCount number of palettes skipped as duplicates
     */
    public record MergeResult(int importedCount, int duplicateCount) {
    }

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final int paletteSize = 4;

    private ActivePalettes active = new ActivePalettes();
    private SavedPalettes saved = new SavedPalettes();

    /**
     * Loads palette data from JSON, or migrates it from the legacy properties
     * structure when needed.
     *
     * @param path             JSON palette file path
     * @param legacyProperties legacy properties for migration fallback
     * @return loaded palette store
     */
    public static LoadResult Load(Path path, Properties legacyProperties) {
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                PaletteStore loaded = gson.fromJson(reader, PaletteStore.class);
                return new LoadResult(Normalize(loaded), false);
            } catch (IOException | JsonParseException exception) {
                exception.printStackTrace();
            }
        }

        if (HasLegacyPaletteData(legacyProperties)) {
            return new LoadResult(FromLegacyProperties(legacyProperties), true);
        }

        return new LoadResult(new PaletteStore(), false);
    }

    /**
     * Loads a palette store from JSON and fails when the file is not valid.
     *
     * @param path JSON palette file path
     * @return loaded palette store
     * @throws IOException when the file cannot be read
     */
    public static PaletteStore ReadStrict(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return Normalize(gson.fromJson(reader, PaletteStore.class));
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("The selected file is not a valid GameDuck palette JSON file.", exception);
        }
    }

    /**
     * Saves the palette store as formatted JSON.
     *
     * @param path output path
     * @throws IOException when writing fails
     */
    public void Save(Path path) throws IOException {
        NormalizeInPlace();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(this, writer);
        }
    }

    /**
     * Returns the active DMG palette.
     *
     * @return palette colours in display order
     */
    public String[] CurrentDmgPalette() {
        return active.dmg.clone();
    }

    /**
     * Replaces the active DMG palette.
     *
     * @param palette new palette colours
     */
    public void SetCurrentDmgPalette(String[] palette) {
        active.dmg = NormalizePalette(palette, DefaultDmgPalette());
    }

    /**
     * Returns whether dual-mode cartridges should prefer DMG mode.
     *
     * @return {@code true} when DMG mode is preferred
     */
    public boolean PreferDmgModeForGbcCompatibleGames() {
        return active.preferDmgModeForGbcCompatibleGames;
    }

    /**
     * Sets whether dual-mode cartridges should prefer DMG mode.
     *
     * @param prefer whether DMG mode is preferred
     */
    public void SetPreferDmgModeForGbcCompatibleGames(boolean prefer) {
        active.preferDmgModeForGbcCompatibleGames = prefer;
    }

    /**
     * Returns the selected colour mode for monochrome games.
     *
     * @return active non-CGB colour mode
     */
    public NonGbcColourMode NonGbcColourMode() {
        return active.nonGbcColourMode;
    }

    /**
     * Sets the colour mode for monochrome games.
     *
     * @param mode non-CGB colour mode
     */
    public void SetNonGbcColourMode(NonGbcColourMode mode) {
        active.nonGbcColourMode = mode == null ? com.blackaby.Misc.NonGbcColourMode.GB_ORIGINAL : mode;
    }

    /**
     * Returns the active GBC background palette.
     *
     * @return background palette colours
     */
    public String[] CurrentGbcBackgroundPalette() {
        return active.gbc.background.clone();
    }

    /**
     * Replaces the active GBC background palette.
     *
     * @param palette new background palette
     */
    public void SetCurrentGbcBackgroundPalette(String[] palette) {
        active.gbc.background = NormalizePalette(palette, DefaultGbcBackgroundPalette());
    }

    /**
     * Returns the active GBC sprite palette 0.
     *
     * @return sprite palette colours
     */
    public String[] CurrentGbcSpritePalette0() {
        return active.gbc.sprite0.clone();
    }

    /**
     * Replaces the active GBC sprite palette 0.
     *
     * @param palette new sprite palette
     */
    public void SetCurrentGbcSpritePalette0(String[] palette) {
        active.gbc.sprite0 = NormalizePalette(palette, DefaultGbcSpritePalette0());
    }

    /**
     * Returns the active GBC sprite palette 1.
     *
     * @return sprite palette colours
     */
    public String[] CurrentGbcSpritePalette1() {
        return active.gbc.sprite1.clone();
    }

    /**
     * Replaces the active GBC sprite palette 1.
     *
     * @param palette new sprite palette
     */
    public void SetCurrentGbcSpritePalette1(String[] palette) {
        active.gbc.sprite1 = NormalizePalette(palette, DefaultGbcSpritePalette1());
    }

    /**
     * Returns the saved DMG palette names in display order.
     *
     * @return saved DMG palette names
     */
    public List<String> SavedDmgPaletteNames() {
        List<String> names = new ArrayList<>();
        for (NamedPalette palette : saved.dmg) {
            names.add(palette.name);
        }
        return names;
    }

    /**
     * Returns the saved GBC palette names in display order.
     *
     * @return saved GBC palette names
     */
    public List<String> SavedGbcPaletteNames() {
        List<String> names = new ArrayList<>();
        for (NamedGbcPalette palette : saved.gbc) {
            names.add(palette.name);
        }
        return names;
    }

    /**
     * Saves or replaces a named DMG palette.
     *
     * @param name   palette name
     * @param colours palette colours
     */
    public void SaveDmgPalette(String name, String[] colours) {
        String safeName = NormalizeName(name);
        if (safeName == null) {
            return;
        }

        NamedPalette palette = FindDmgPaletteEntry(safeName);
        if (palette == null) {
            saved.dmg.add(new NamedPalette(safeName, colours));
            return;
        }

        palette.colors = NormalizePalette(colours, DefaultDmgPalette());
    }

    /**
     * Saves or replaces a named GBC palette set.
     *
     * @param name      palette name
     * @param background background palette
     * @param sprite0   sprite palette 0
     * @param sprite1   sprite palette 1
     */
    public void SaveGbcPalette(String name, String[] background, String[] sprite0, String[] sprite1) {
        String safeName = NormalizeName(name);
        if (safeName == null) {
            return;
        }

        NamedGbcPalette palette = FindGbcPaletteEntry(safeName);
        if (palette == null) {
            saved.gbc.add(new NamedGbcPalette(safeName, background, sprite0, sprite1));
            return;
        }

        palette.background = NormalizePalette(background, DefaultGbcBackgroundPalette());
        palette.sprite0 = NormalizePalette(sprite0, DefaultGbcSpritePalette0());
        palette.sprite1 = NormalizePalette(sprite1, DefaultGbcSpritePalette1());
    }

    /**
     * Returns a saved DMG palette by name.
     *
     * @param name palette name
     * @return palette colours, or {@code null} when not found
     */
    public String[] FindDmgPalette(String name) {
        NamedPalette palette = FindDmgPaletteEntry(name);
        return palette == null ? null : palette.colors.clone();
    }

    /**
     * Returns a saved GBC palette set by name.
     *
     * @param name palette name
     * @return palette set, or {@code null} when not found
     */
    public StoredGbcPalette FindGbcPalette(String name) {
        NamedGbcPalette palette = FindGbcPaletteEntry(name);
        if (palette == null) {
            return null;
        }
        return new StoredGbcPalette(
                palette.background.clone(),
                palette.sprite0.clone(),
                palette.sprite1.clone());
    }

    /**
     * Deletes a saved DMG palette.
     *
     * @param name palette name
     * @return {@code true} when a palette was removed
     */
    public boolean DeleteDmgPalette(String name) {
        NamedPalette palette = FindDmgPaletteEntry(name);
        return palette != null && saved.dmg.remove(palette);
    }

    /**
     * Deletes a saved GBC palette set.
     *
     * @param name palette name
     * @return {@code true} when a palette was removed
     */
    public boolean DeleteGbcPalette(String name) {
        NamedGbcPalette palette = FindGbcPaletteEntry(name);
        return palette != null && saved.gbc.remove(palette);
    }

    /**
     * Merges saved DMG palettes from another palette store.
     *
     * Existing palettes are preserved. Imported palettes are skipped when a saved
     * palette with the same name or the same colours already exists.
     *
     * @param importedStore imported palette store
     * @return merge summary
     */
    public MergeResult MergeSavedDmgPalettes(PaletteStore importedStore) {
        PaletteStore safeImportedStore = Normalize(importedStore);
        int importedCount = 0;
        int duplicateCount = 0;

        for (NamedPalette importedPalette : safeImportedStore.saved.dmg) {
            if (FindDmgPaletteEntry(importedPalette.name) != null || HasMatchingDmgPalette(importedPalette.colors)) {
                duplicateCount++;
                continue;
            }

            saved.dmg.add(new NamedPalette(importedPalette.name, importedPalette.colors));
            importedCount++;
        }

        return new MergeResult(importedCount, duplicateCount);
    }

    /**
     * Merges saved GBC palettes from another palette store.
     *
     * Existing palettes are preserved. Imported palettes are skipped when a saved
     * palette with the same name or the same colours already exists.
     *
     * @param importedStore imported palette store
     * @return merge summary
     */
    public MergeResult MergeSavedGbcPalettes(PaletteStore importedStore) {
        PaletteStore safeImportedStore = Normalize(importedStore);
        int importedCount = 0;
        int duplicateCount = 0;

        for (NamedGbcPalette importedPalette : safeImportedStore.saved.gbc) {
            if (FindGbcPaletteEntry(importedPalette.name) != null || HasMatchingGbcPalette(importedPalette)) {
                duplicateCount++;
                continue;
            }

            saved.gbc.add(new NamedGbcPalette(
                    importedPalette.name,
                    importedPalette.background,
                    importedPalette.sprite0,
                    importedPalette.sprite1));
            importedCount++;
        }

        return new MergeResult(importedCount, duplicateCount);
    }

    /**
     * Immutable GBC palette set returned from lookups.
     *
     * @param background background palette colours
     * @param sprite0    sprite palette 0 colours
     * @param sprite1    sprite palette 1 colours
     */
    public record StoredGbcPalette(String[] background, String[] sprite0, String[] sprite1) {
    }

    private static PaletteStore Normalize(PaletteStore store) {
        PaletteStore safeStore = store == null ? new PaletteStore() : store;
        safeStore.NormalizeInPlace();
        return safeStore;
    }

    private void NormalizeInPlace() {
        active = active == null ? new ActivePalettes() : active;
        active.dmg = NormalizePalette(active.dmg, DefaultDmgPalette());
        if (active.gbcPaletteModeEnabled != null
                && (active.nonGbcColourMode == null || active.nonGbcColourMode == NonGbcColourMode.GB_ORIGINAL)) {
            active.nonGbcColourMode = NonGbcColourMode.FromLegacyBoolean(active.gbcPaletteModeEnabled);
        }
        if (active.nonGbcColourMode == null) {
            active.nonGbcColourMode = NonGbcColourMode.GB_ORIGINAL;
        }
        active.gbcPaletteModeEnabled = null;
        active.gbc = active.gbc == null ? new GbcPalettes() : active.gbc;
        active.gbc.background = NormalizePalette(active.gbc.background, DefaultGbcBackgroundPalette());
        active.gbc.sprite0 = NormalizePalette(active.gbc.sprite0, DefaultGbcSpritePalette0());
        active.gbc.sprite1 = NormalizePalette(active.gbc.sprite1, DefaultGbcSpritePalette1());

        saved = saved == null ? new SavedPalettes() : saved;
        saved.dmg = NormalizeNamedPalettes(saved.dmg);
        saved.gbc = NormalizeNamedGbcPalettes(saved.gbc);
    }

    private static boolean HasLegacyPaletteData(Properties legacyProperties) {
        if (legacyProperties == null || legacyProperties.isEmpty()) {
            return false;
        }

        String[] directKeys = {
                "palette.names",
                "palette.gbc.saved.names",
                "palette.prefer_dmg_mode_for_gbc_compatible_games",
                "palette.gbc_mode_enabled"
        };
        for (String key : directKeys) {
            if (legacyProperties.getProperty(key) != null) {
                return true;
            }
        }

        for (String key : legacyProperties.stringPropertyNames()) {
            if (key.startsWith("palette.current.")
                    || key.startsWith("palette.saved.")
                    || key.startsWith("palette.gbc.saved.")
                    || key.startsWith("palette.gbc.background.")
                    || key.startsWith("palette.gbc.sprite0.")
                    || key.startsWith("palette.gbc.sprite1.")) {
                return true;
            }
        }

        return false;
    }

    private static PaletteStore FromLegacyProperties(Properties legacyProperties) {
        PaletteStore store = new PaletteStore();
        store.active.dmg = ReadLegacyPalette(legacyProperties, "palette.current.", DefaultDmgPalette());
        store.active.preferDmgModeForGbcCompatibleGames = Boolean.parseBoolean(
                legacyProperties.getProperty("palette.prefer_dmg_mode_for_gbc_compatible_games", "false"));
        store.active.nonGbcColourMode = com.blackaby.Misc.NonGbcColourMode.FromLegacyBoolean(
                Boolean.parseBoolean(legacyProperties.getProperty("palette.gbc_mode_enabled", "false")));
        store.active.gbc.background = ReadLegacyPalette(
                legacyProperties, "palette.gbc.background.", DefaultGbcBackgroundPalette());
        store.active.gbc.sprite0 = ReadLegacyPalette(
                legacyProperties, "palette.gbc.sprite0.", DefaultGbcSpritePalette0());
        store.active.gbc.sprite1 = ReadLegacyPalette(
                legacyProperties, "palette.gbc.sprite1.", DefaultGbcSpritePalette1());

        for (String name : DecodeLegacyNames(legacyProperties.getProperty("palette.names", ""))) {
            String encodedName = EncodeLegacyName(name);
            store.saved.dmg.add(new NamedPalette(name,
                    ReadLegacyPalette(legacyProperties, "palette.saved." + encodedName + ".", DefaultDmgPalette())));
        }

        for (String name : DecodeLegacyNames(legacyProperties.getProperty("palette.gbc.saved.names", ""))) {
            String encodedName = EncodeLegacyName(name);
            store.saved.gbc.add(new NamedGbcPalette(
                    name,
                    ReadLegacyPalette(legacyProperties,
                            "palette.gbc.saved." + encodedName + ".background.", DefaultGbcBackgroundPalette()),
                    ReadLegacyPalette(legacyProperties,
                            "palette.gbc.saved." + encodedName + ".sprite0.", DefaultGbcSpritePalette0()),
                    ReadLegacyPalette(legacyProperties,
                            "palette.gbc.saved." + encodedName + ".sprite1.", DefaultGbcSpritePalette1())));
        }

        store.NormalizeInPlace();
        return store;
    }

    private static List<NamedPalette> NormalizeNamedPalettes(List<NamedPalette> palettes) {
        List<NamedPalette> normalized = new ArrayList<>();
        if (palettes == null) {
            return normalized;
        }

        Set<String> seenNames = new LinkedHashSet<>();
        for (NamedPalette palette : palettes) {
            if (palette == null) {
                continue;
            }

            String safeName = NormalizeName(palette.name);
            if (safeName == null || !seenNames.add(safeName)) {
                continue;
            }

            normalized.add(new NamedPalette(safeName, palette.colors));
        }
        return normalized;
    }

    private static List<NamedGbcPalette> NormalizeNamedGbcPalettes(List<NamedGbcPalette> palettes) {
        List<NamedGbcPalette> normalized = new ArrayList<>();
        if (palettes == null) {
            return normalized;
        }

        Set<String> seenNames = new LinkedHashSet<>();
        for (NamedGbcPalette palette : palettes) {
            if (palette == null) {
                continue;
            }

            String safeName = NormalizeName(palette.name);
            if (safeName == null || !seenNames.add(safeName)) {
                continue;
            }

            normalized.add(new NamedGbcPalette(
                    safeName,
                    palette.background,
                    palette.sprite0,
                    palette.sprite1));
        }
        return normalized;
    }

    private static String NormalizeName(String name) {
        if (name == null) {
            return null;
        }

        String trimmed = name.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String[] NormalizePalette(String[] palette, String[] defaults) {
        String[] normalized = defaults.clone();
        if (palette == null) {
            return normalized;
        }

        for (int index = 0; index < Math.min(palette.length, paletteSize); index++) {
            normalized[index] = NormalizeHex(palette[index], defaults[index]);
        }
        return normalized;
    }

    private static String NormalizeHex(String value, String fallback) {
        String safeFallback = fallback == null ? "#000000" : fallback;
        if (value == null || value.isBlank()) {
            return ToCanonicalHex(safeFallback);
        }

        try {
            return ToCanonicalHex(value);
        } catch (RuntimeException exception) {
            return ToCanonicalHex(safeFallback);
        }
    }

    private static String ToCanonicalHex(String value) {
        return new GBColor(value).ToHex().toUpperCase();
    }

    private static String[] ReadLegacyPalette(Properties legacyProperties, String prefix, String[] defaults) {
        String[] palette = defaults.clone();
        for (int index = 0; index < palette.length; index++) {
            palette[index] = NormalizeHex(legacyProperties.getProperty(prefix + index), defaults[index]);
        }
        return palette;
    }

    private static List<String> DecodeLegacyNames(String stored) {
        List<String> names = new ArrayList<>();
        if (stored == null || stored.isBlank()) {
            return names;
        }

        for (String encodedName : stored.split(",")) {
            if (encodedName.isBlank()) {
                continue;
            }
            try {
                names.add(new String(
                        Base64.getUrlDecoder().decode(encodedName),
                        StandardCharsets.UTF_8));
            } catch (IllegalArgumentException exception) {
                // Skip invalid legacy entries.
            }
        }
        return names;
    }

    private static String EncodeLegacyName(String name) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));
    }

    private static String[] DefaultDmgPalette() {
        return new String[] {
                ToCanonicalHex(Settings.gbColour0),
                ToCanonicalHex(Settings.gbColour1),
                ToCanonicalHex(Settings.gbColour2),
                ToCanonicalHex(Settings.gbColour3)
        };
    }

    private static String[] DefaultGbcBackgroundPalette() {
        return NormalizePalette(Settings.gbcBackgroundPaletteDefaults, Settings.gbcBackgroundPaletteDefaults);
    }

    private static String[] DefaultGbcSpritePalette0() {
        return NormalizePalette(Settings.gbcSpritePalette0Defaults, Settings.gbcSpritePalette0Defaults);
    }

    private static String[] DefaultGbcSpritePalette1() {
        return NormalizePalette(Settings.gbcSpritePalette1Defaults, Settings.gbcSpritePalette1Defaults);
    }

    private NamedPalette FindDmgPaletteEntry(String name) {
        String safeName = NormalizeName(name);
        if (safeName == null) {
            return null;
        }

        for (NamedPalette palette : saved.dmg) {
            if (palette.name.equals(safeName)) {
                return palette;
            }
        }
        return null;
    }

    private NamedGbcPalette FindGbcPaletteEntry(String name) {
        String safeName = NormalizeName(name);
        if (safeName == null) {
            return null;
        }

        for (NamedGbcPalette palette : saved.gbc) {
            if (palette.name.equals(safeName)) {
                return palette;
            }
        }
        return null;
    }

    private boolean HasMatchingDmgPalette(String[] colours) {
        String[] normalizedColours = NormalizePalette(colours, DefaultDmgPalette());
        for (NamedPalette palette : saved.dmg) {
            if (Arrays.equals(palette.colors, normalizedColours)) {
                return true;
            }
        }
        return false;
    }

    private boolean HasMatchingGbcPalette(NamedGbcPalette importedPalette) {
        String[] normalizedBackground = NormalizePalette(importedPalette.background, DefaultGbcBackgroundPalette());
        String[] normalizedSprite0 = NormalizePalette(importedPalette.sprite0, DefaultGbcSpritePalette0());
        String[] normalizedSprite1 = NormalizePalette(importedPalette.sprite1, DefaultGbcSpritePalette1());
        for (NamedGbcPalette palette : saved.gbc) {
            if (Arrays.equals(palette.background, normalizedBackground)
                    && Arrays.equals(palette.sprite0, normalizedSprite0)
                    && Arrays.equals(palette.sprite1, normalizedSprite1)) {
                return true;
            }
        }
        return false;
    }

    private static final class ActivePalettes {
        private String[] dmg = DefaultDmgPalette();
        private boolean preferDmgModeForGbcCompatibleGames;
        private com.blackaby.Misc.NonGbcColourMode nonGbcColourMode = com.blackaby.Misc.NonGbcColourMode.GB_ORIGINAL;
        private Boolean gbcPaletteModeEnabled;
        private GbcPalettes gbc = new GbcPalettes();
    }

    private static final class GbcPalettes {
        private String[] background = DefaultGbcBackgroundPalette();
        private String[] sprite0 = DefaultGbcSpritePalette0();
        private String[] sprite1 = DefaultGbcSpritePalette1();
    }

    private static final class SavedPalettes {
        private List<NamedPalette> dmg = new ArrayList<>();
        private List<NamedGbcPalette> gbc = new ArrayList<>();
    }

    private static final class NamedPalette {
        private String name;
        private String[] colors;

        private NamedPalette() {
        }

        private NamedPalette(String name, String[] colors) {
            this.name = name;
            this.colors = NormalizePalette(colors, DefaultDmgPalette());
        }
    }

    private static final class NamedGbcPalette {
        private String name;
        private String[] background;
        private String[] sprite0;
        private String[] sprite1;

        private NamedGbcPalette() {
        }

        private NamedGbcPalette(String name, String[] background, String[] sprite0, String[] sprite1) {
            this.name = name;
            this.background = NormalizePalette(background, DefaultGbcBackgroundPalette());
            this.sprite0 = NormalizePalette(sprite0, DefaultGbcSpritePalette0());
            this.sprite1 = NormalizePalette(sprite1, DefaultGbcSpritePalette1());
        }
    }
}

