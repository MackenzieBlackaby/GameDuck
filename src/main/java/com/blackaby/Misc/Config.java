package com.blackaby.Misc;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Backend.GB.Graphics.GBColor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Loads and saves the persistent application configuration.
 * <p>
 * The config file keeps host theme values, control bindings, shortcut bindings,
 * sound options, and simple window preferences. Palette data is stored in a
 * separate JSON document, but loaded through the same facade here so the rest
 * of
 * the application can request a load once and then work with in-memory
 * settings.
 */
public final class Config {

    private static final Path appDataDirectoryPath = Path.of(System.getProperty("user.home"), ".gameduck");
    private static final Path legacyConfigPath = Path.of(System.getProperty("user.home"), ".gameduck.properties");
    private static final Path legacyPaletteConfigPath = Path.of(System.getProperty("user.home"),
            ".gameduck-palettes.json");
    private static final Path configPath = appDataDirectoryPath.resolve("config.properties");
    private static final Path paletteConfigPath = appDataDirectoryPath.resolve("palettes.json");
    private static final String currentPalettePrefix = "palette.current.";
    private static final String savedPaletteListKey = "palette.names";
    private static final String savedPalettePrefix = "palette.saved.";
    private static final String savedGbcPaletteListKey = "palette.gbc.saved.names";
    private static final String savedGbcPalettePrefix = "palette.gbc.saved.";
    private static final String inputPrefix = "input.";
    private static final String controllerInputPrefix = "controller.input.";
    private static final String shortcutPrefix = "shortcut.";
    private static final String controllerShortcutPrefix = "shortcut.controller.";
    private static final String themePrefix = "theme.";
    private static final String controllerEnabledKey = "controller.enabled";
    private static final String controllerPreferredIdKey = "controller.preferred_id";
    private static final String controllerDeadzoneKey = "controller.deadzone_percent";
    private static final String controllerPollingModeKey = "controller.polling_mode";
    private static final String soundEnabledKey = "sound.enabled";
    private static final String soundVolumeKey = "sound.volume";
    private static final String soundChannelMutedPrefix = "sound.channel.muted.";
    private static final String soundChannelVolumePrefix = "sound.channel.volume.";
    private static final String soundEnhancementEnabledKey = "sound.enhancement.enabled";
    private static final String soundEnhancementChainKey = "sound.enhancement.chain";
    private static final String useBootRomKey = "emulation.use_boot_rom";
    private static final String useCgbBootRomKey = "emulation.use_cgb_boot_rom";
    private static final String fillWindowOutputKey = "ui.fill_window_output";
    private static final String integerScaleWindowOutputKey = "ui.integer_scale_window_output";
    private static final String displayShaderKey = "ui.display_shader";
    private static final String displayBorderKey = "ui.display_border";
    private static final String showSerialOutputKey = "ui.show_serial_output";
    private static final String displayFrameBlendingKey = "ui.display_frame_blending";
    private static final String showDisplayFpsKey = "ui.show_display_fps";
    private static final String gameArtDisplayModeKey = "ui.game_art_display_mode";
    private static final String gameNameBracketDisplayModeKey = "library.game_name_bracket_display_mode";
    private static final String libraryViewModeKey = "library.view_mode";
    private static final String libraryFilterModeKey = "library.filter_mode";
    private static final String libraryConsoleFilterKey = "library.console_filter";
    private static final String librarySortModeKey = "library.sort_mode";
    private static final String librarySearchQueryKey = "library.search_query";
    private static final String loadRecentMenuLimitKey = "library.load_recent_menu_limit";
    private static final String preferDmgModeForGbcCompatibleGamesKey = "palette.prefer_dmg_mode_for_gbc_compatible_games";
    private static final String gbcPaletteModeEnabledKey = "palette.gbc_mode_enabled";
    private static final String gbcBackgroundPalettePrefix = "palette.gbc.background.";
    private static final String gbcSpritePalette0Prefix = "palette.gbc.sprite0.";
    private static final String gbcSpritePalette1Prefix = "palette.gbc.sprite1.";

    private static final Properties properties = new Properties();
    private static PaletteStore paletteStore = new PaletteStore();
    private static boolean loaded;

    private Config() {
    }

    /**
     * Loads the configuration file into {@link Settings}.
     */
    public static synchronized void Load() {
        MigrateLegacyFileIfNeeded(legacyConfigPath, configPath);
        MigrateLegacyFileIfNeeded(legacyPaletteConfigPath, paletteConfigPath);
        properties.clear();
        if (Files.exists(configPath)) {
            try (InputStream inputStream = Files.newInputStream(configPath)) {
                properties.load(inputStream);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }

        PaletteStore.LoadResult paletteLoadResult = PaletteStore.Load(paletteConfigPath, properties);
        ThemeStore.LoadResult themeLoadResult = ThemeStore.Load(properties);
        paletteStore = paletteLoadResult.store();
        ApplyCurrentPalette();
        ApplyAppTheme();
        ApplyInputBindings();
        ApplyControllerBindings();
        ApplyAppShortcuts();
        ApplySoundSettings();
        ApplyEmulationSettings();
        ApplyWindowSettings();
        ApplyLibrarySettings();
        loaded = true;

        if (paletteLoadResult.migratedFromLegacy() || themeLoadResult.migratedFromLegacy()) {
            Persist();
        }
    }

    /**
     * Saves the current in-memory settings to disk.
     */
    public static synchronized void Save() {
        EnsureLoaded();
        SyncCurrentPalette();
        SyncAppTheme();
        SyncInputBindings();
        SyncControllerBindings();
        SyncAppShortcuts();
        SyncSoundSettings();
        SyncEmulationSettings();
        SyncWindowSettings();
        SyncLibrarySettings();
        Persist();
    }

    /**
     * Saves the current DMG palette under a user-supplied name.
     *
     * @param name palette name
     */
    public static void SavePalette(String name) {
        synchronized (Config.class) {
            EnsureLoaded();
            SyncCurrentPalette();
            paletteStore.SaveDmgPalette(name, paletteStore.CurrentDmgPalette());
            SyncAppTheme();
            SyncInputBindings();
            SyncControllerBindings();
            SyncAppShortcuts();
            SyncSoundSettings();
            SyncEmulationSettings();
            SyncWindowSettings();
            SyncLibrarySettings();
            Persist();
        }
    }

    /**
     * Saves the current GBC colourisation palettes under a user-supplied name.
     *
     * @param name palette name
     */
    public static void SaveGbcPalette(String name) {
        synchronized (Config.class) {
            EnsureLoaded();
            SyncCurrentPalette();
            paletteStore.SaveGbcPalette(
                    name,
                    paletteStore.CurrentGbcBackgroundPalette(),
                    paletteStore.CurrentGbcSpritePalette0(),
                    paletteStore.CurrentGbcSpritePalette1());
            SyncAppTheme();
            SyncInputBindings();
            SyncControllerBindings();
            SyncAppShortcuts();
            SyncSoundSettings();
            SyncEmulationSettings();
            SyncWindowSettings();
            SyncLibrarySettings();
            Persist();
        }
    }

    /**
     * Saves the current host theme under a user-supplied name.
     *
     * @param name theme name
     */
    public static void SaveTheme(String name) {
        synchronized (Config.class) {
            EnsureLoaded();
            ThemeStore.SaveTheme(Settings.CurrentAppTheme().Renamed(name));
            SyncCurrentPalette();
            SyncAppTheme();
            SyncInputBindings();
            SyncControllerBindings();
            SyncAppShortcuts();
            SyncSoundSettings();
            SyncEmulationSettings();
            SyncWindowSettings();
            SyncLibrarySettings();
            Persist();
        }
    }

    /**
     * Returns the saved palette names in display order.
     *
     * @return saved palette names
     */
    public static synchronized List<String> GetSavedPaletteNames() {
        EnsureLoaded();
        return new ArrayList<>(paletteStore.SavedDmgPaletteNames());
    }

    /**
     * Returns the saved GBC palette names in display order.
     *
     * @return saved GBC palette names
     */
    public static synchronized List<String> GetSavedGbcPaletteNames() {
        EnsureLoaded();
        return new ArrayList<>(paletteStore.SavedGbcPaletteNames());
    }

    /**
     * Imports saved DMG palettes from another palette JSON file.
     *
     * @param path palette JSON file
     * @return merge summary
     * @throws IOException when the file cannot be read
     */
    public static synchronized PaletteStore.MergeResult ImportPalettes(Path path) throws IOException {
        EnsureLoaded();
        PaletteStore importedStore = PaletteStore.ReadStrict(path);
        PaletteStore.MergeResult mergeResult = paletteStore.MergeSavedDmgPalettes(importedStore);
        SyncCurrentPalette();
        SyncAppTheme();
        SyncInputBindings();
        SyncControllerBindings();
        SyncAppShortcuts();
        SyncSoundSettings();
        SyncEmulationSettings();
        SyncWindowSettings();
        SyncLibrarySettings();
        Persist();
        return mergeResult;
    }

    /**
     * Imports saved GBC palettes from another palette JSON file.
     *
     * @param path palette JSON file
     * @return merge summary
     * @throws IOException when the file cannot be read
     */
    public static synchronized PaletteStore.MergeResult ImportGbcPalettes(Path path) throws IOException {
        EnsureLoaded();
        PaletteStore importedStore = PaletteStore.ReadStrict(path);
        PaletteStore.MergeResult mergeResult = paletteStore.MergeSavedGbcPalettes(importedStore);
        SyncCurrentPalette();
        SyncAppTheme();
        SyncInputBindings();
        SyncControllerBindings();
        SyncAppShortcuts();
        SyncSoundSettings();
        SyncEmulationSettings();
        SyncWindowSettings();
        SyncLibrarySettings();
        Persist();
        return mergeResult;
    }

    /**
     * Returns the saved theme names in display order.
     *
     * @return saved theme names
     */
    public static synchronized List<String> GetSavedThemeNames() {
        EnsureLoaded();
        return new ArrayList<>(ThemeStore.SavedThemeNames());
    }

    /**
     * Loads a saved palette into the active settings.
     *
     * @param name palette name
     * @return {@code true} if the palette existed
     */
    public static synchronized boolean LoadPalette(String name) {
        EnsureLoaded();

        String[] palette = paletteStore.FindDmgPalette(name);
        if (palette == null) {
            return false;
        }

        ApplyPaletteToSettings(palette);
        SyncCurrentPalette();
        SyncAppTheme();
        SyncInputBindings();
        SyncControllerBindings();
        SyncAppShortcuts();
        SyncSoundSettings();
        SyncWindowSettings();
        SyncLibrarySettings();
        Persist();

        return true;
    }

    /**
     * Loads a saved GBC colourisation palette set into the active settings.
     *
     * @param name palette name
     * @return {@code true} if the palette existed
     */
    public static synchronized boolean LoadGbcPalette(String name) {
        EnsureLoaded();

        PaletteStore.StoredGbcPalette palette = paletteStore.FindGbcPalette(name);
        if (palette == null) {
            return false;
        }

        ApplyGbcPaletteToSettings(0, palette.background());
        ApplyGbcPaletteToSettings(1, palette.sprite0());
        ApplyGbcPaletteToSettings(2, palette.sprite1());
        SyncCurrentPalette();
        SyncAppTheme();
        SyncInputBindings();
        SyncControllerBindings();
        SyncAppShortcuts();
        SyncSoundSettings();
        SyncWindowSettings();
        SyncLibrarySettings();
        Persist();

        return true;
    }

    /**
     * Loads a saved theme into the active settings.
     *
     * @param name theme name
     * @return {@code true} if the theme existed
     */
    public static synchronized boolean LoadTheme(String name) {
        EnsureLoaded();
        AppTheme loadedTheme = ThemeStore.FindTheme(name);
        boolean found = loadedTheme != null;

        if (found) {
            Settings.ApplyAppTheme(loadedTheme);
            SyncCurrentPalette();
            SyncAppTheme();
            SyncInputBindings();
            SyncAppShortcuts();
            SyncSoundSettings();
            SyncEmulationSettings();
            SyncWindowSettings();
            SyncLibrarySettings();
            Persist();
        }

        return found;
    }

    /**
     * Deletes a stored palette.
     *
     * @param name palette name
     */
    public static synchronized void DeletePalette(String name) {
        EnsureLoaded();
        paletteStore.DeleteDmgPalette(name);
        SyncCurrentPalette();
        SyncAppTheme();
        SyncInputBindings();
        SyncControllerBindings();
        SyncAppShortcuts();
        SyncSoundSettings();
        SyncEmulationSettings();
        SyncWindowSettings();
        SyncLibrarySettings();
        Persist();
    }

    /**
     * Deletes a stored GBC palette set.
     *
     * @param name palette name
     */
    public static synchronized void DeleteGbcPalette(String name) {
        EnsureLoaded();
        paletteStore.DeleteGbcPalette(name);
        SyncCurrentPalette();
        SyncAppTheme();
        SyncInputBindings();
        SyncControllerBindings();
        SyncAppShortcuts();
        SyncSoundSettings();
        SyncEmulationSettings();
        SyncWindowSettings();
        SyncLibrarySettings();
        Persist();
    }

    /**
     * Deletes a stored host theme.
     *
     * @param name theme name
     */
    public static synchronized void DeleteTheme(String name) {
        EnsureLoaded();
        ThemeStore.DeleteTheme(name);
        SyncCurrentPalette();
        SyncAppTheme();
        SyncInputBindings();
        SyncControllerBindings();
        SyncAppShortcuts();
        SyncSoundSettings();
        SyncEmulationSettings();
        SyncWindowSettings();
        SyncLibrarySettings();
        Persist();
    }

    /**
     * Restores the bundled default themes into the managed theme JSON library.
     */
    public static synchronized void RestoreDefaultThemes() {
        EnsureLoaded();
        ThemeStore.RestoreDefaultThemes();
    }

    private static void ApplyCurrentPalette() {
        ApplyPaletteToSettings(paletteStore.CurrentDmgPalette());
        Settings.ResetGbcPaletteMode();
        Settings.preferDmgModeForGbcCompatibleGames = paletteStore.PreferDmgModeForGbcCompatibleGames();
        Settings.gbcPaletteModeEnabled = paletteStore.GbcPaletteModeEnabled();
        ApplyGbcPaletteToSettings(0, paletteStore.CurrentGbcBackgroundPalette());
        ApplyGbcPaletteToSettings(1, paletteStore.CurrentGbcSpritePalette0());
        ApplyGbcPaletteToSettings(2, paletteStore.CurrentGbcSpritePalette1());
    }

    private static void ApplyAppTheme() {
        Settings.ResetAppTheme();
        AppTheme theme = Settings.CurrentAppTheme();
        for (AppThemeColorRole role : AppThemeColorRole.values()) {
            String value = properties.getProperty(themePrefix + role.name());
            if (value != null && !value.isBlank()) {
                theme.SetCoreColour(role, value);
            }
        }
        Settings.ApplyAppTheme(theme);
    }

    private static void ApplyInputBindings() {
        Settings.ResetControls();
        for (GBButton button : GBButton.values()) {
            String storedCode = properties.getProperty(inputPrefix + button.name());
            if (storedCode == null) {
                continue;
            }

            try {
                Settings.inputBindings.SetKeyCode(button, Integer.parseInt(storedCode));
            } catch (NumberFormatException exception) {
                Settings.ResetControls();
                return;
            }
        }
    }

    private static void ApplyAppShortcuts() {
        Settings.ResetAppShortcuts();
        for (AppShortcut shortcut : AppShortcut.values()) {
            Settings.appShortcutBindings.LoadFromConfigValue(shortcut,
                    properties.getProperty(shortcutPrefix + shortcut.name()));
            Settings.appShortcutControllerBindings.LoadFromConfigValue(shortcut,
                    properties.getProperty(controllerShortcutPrefix + shortcut.name()));
        }
    }

    private static void ApplyControllerBindings() {
        Settings.ResetControllerControls();
        Settings.controllerInputEnabled = Boolean.parseBoolean(properties.getProperty(controllerEnabledKey, "true"));
        Settings.preferredControllerId = properties.getProperty(controllerPreferredIdKey, "");

        try {
            int configuredDeadzone = Integer.parseInt(properties.getProperty(controllerDeadzoneKey, "45"));
            Settings.controllerDeadzonePercent = Math.max(0, Math.min(95, configuredDeadzone));
        } catch (NumberFormatException exception) {
            Settings.controllerDeadzonePercent = 45;
        }

        try {
            Settings.controllerPollingMode = ControllerPollingMode
                    .valueOf(properties.getProperty(controllerPollingModeKey, ControllerPollingMode.BALANCED.name()));
        } catch (IllegalArgumentException exception) {
            Settings.controllerPollingMode = ControllerPollingMode.BALANCED;
        }

        for (GBButton button : GBButton.values()) {
            ControllerBinding binding = ControllerBinding.FromConfigValue(
                    properties.getProperty(controllerInputPrefix + button.name()));
            if (binding != null) {
                Settings.controllerBindings.SetBinding(button, binding);
            }
        }
    }

    private static void ApplySoundSettings() {
        Settings.ResetSound();
        Settings.soundEnabled = Boolean.parseBoolean(properties.getProperty(soundEnabledKey, "true"));
        try {
            int configuredVolume = Integer.parseInt(properties.getProperty(soundVolumeKey, "100"));
            Settings.masterVolume = Math.max(0, Math.min(100, configuredVolume));
        } catch (NumberFormatException exception) {
            Settings.ResetSound();
            return;
        }

        for (int channelIndex = 0; channelIndex < 4; channelIndex++) {
            Settings.SetChannelMuted(channelIndex,
                    Boolean.parseBoolean(properties.getProperty(soundChannelMutedPrefix + channelIndex, "false")));
            try {
                int configuredChannelVolume = Integer.parseInt(
                        properties.getProperty(soundChannelVolumePrefix + channelIndex, "100"));
                Settings.SetChannelVolume(channelIndex, configuredChannelVolume);
            } catch (NumberFormatException exception) {
                Settings.SetChannelVolume(channelIndex, 100);
            }
        }

        Settings.SetAudioEnhancementChain(
                ParseAudioEnhancementChain(properties.getProperty(soundEnhancementChainKey, "")));
        Settings.SetAudioEnhancementChainEnabled(
                Boolean.parseBoolean(properties.getProperty(soundEnhancementEnabledKey, "false")));
    }

    private static void ApplyWindowSettings() {
        Settings.ResetWindow();
        Settings.fillWindowOutput = Boolean.parseBoolean(properties.getProperty(fillWindowOutputKey, "false"));
        Settings.integerScaleWindowOutput = Boolean.parseBoolean(
                properties.getProperty(integerScaleWindowOutputKey, "false"));
        Settings.displayShaderId = properties.getProperty(displayShaderKey, "none");
        Settings.displayBorderId = properties.getProperty(displayBorderKey, "none");
        Settings.showSerialOutput = Boolean.parseBoolean(properties.getProperty(showSerialOutputKey, "true"));
        Settings.enableFrameBlending = Boolean.parseBoolean(properties.getProperty(displayFrameBlendingKey, "true"));
        Settings.showDisplayFps = Boolean.parseBoolean(properties.getProperty(showDisplayFpsKey, "true"));
        String configuredGameArtMode = properties.getProperty(gameArtDisplayModeKey, GameArtDisplayMode.BOX_ART.name());
        try {
            Settings.gameArtDisplayMode = GameArtDisplayMode.valueOf(configuredGameArtMode);
        } catch (IllegalArgumentException exception) {
            Settings.gameArtDisplayMode = GameArtDisplayMode.BOX_ART;
        }
    }

    private static void ApplyLibrarySettings() {
        Settings.ResetLibrary();
        String configuredMode = properties.getProperty(gameNameBracketDisplayModeKey,
                GameNameBracketDisplayMode.NONE.name());
        try {
            Settings.gameNameBracketDisplayMode = GameNameBracketDisplayMode.valueOf(configuredMode);
        } catch (IllegalArgumentException exception) {
            Settings.ResetLibrary();
            return;
        }

        String configuredViewMode = properties.getProperty(libraryViewModeKey, "LIST");
        Settings.libraryViewMode = configuredViewMode == null || configuredViewMode.isBlank()
                ? "LIST"
                : configuredViewMode;
        String configuredFilterMode = properties.getProperty(libraryFilterModeKey, "ALL");
        Settings.libraryFilterMode = configuredFilterMode == null || configuredFilterMode.isBlank()
                ? "ALL"
                : configuredFilterMode;
        String configuredConsoleFilter = properties.getProperty(libraryConsoleFilterKey, "ALL");
        Settings.libraryConsoleFilter = configuredConsoleFilter == null || configuredConsoleFilter.isBlank()
                ? "ALL"
                : configuredConsoleFilter;
        String configuredSortMode = properties.getProperty(librarySortModeKey, "ALPHABETICAL");
        Settings.librarySortMode = configuredSortMode == null || configuredSortMode.isBlank()
                ? "ALPHABETICAL"
                : configuredSortMode;
        String configuredSearchQuery = properties.getProperty(librarySearchQueryKey, "");
        Settings.librarySearchQuery = configuredSearchQuery == null ? "" : configuredSearchQuery;
        try {
            int configuredRecentLimit = Integer.parseInt(properties.getProperty(loadRecentMenuLimitKey, "10"));
            Settings.loadRecentMenuLimit = Math.max(1, Math.min(25, configuredRecentLimit));
        } catch (NumberFormatException exception) {
            Settings.loadRecentMenuLimit = 10;
        }
    }

    private static void ApplyEmulationSettings() {
        Settings.ResetEmulation();
        Settings.useBootRom = Boolean.parseBoolean(properties.getProperty(useBootRomKey, "false"));
        Settings.useCgbBootRom = Boolean.parseBoolean(properties.getProperty(useCgbBootRomKey, "false"));
    }

    private static void SyncCurrentPalette() {
        paletteStore.SetCurrentDmgPalette(ToHexPalette(Settings.CurrentPalette()));
        paletteStore.SetPreferDmgModeForGbcCompatibleGames(Settings.preferDmgModeForGbcCompatibleGames);
        paletteStore.SetGbcPaletteModeEnabled(Settings.gbcPaletteModeEnabled);
        paletteStore.SetCurrentGbcBackgroundPalette(ToHexPalette(Settings.CurrentGbcBackgroundPalette()));
        paletteStore.SetCurrentGbcSpritePalette0(ToHexPalette(Settings.CurrentGbcSpritePalette0()));
        paletteStore.SetCurrentGbcSpritePalette1(ToHexPalette(Settings.CurrentGbcSpritePalette1()));
    }

    private static void SyncAppTheme() {
        AppTheme theme = Settings.CurrentAppTheme();
        for (AppThemeColorRole role : AppThemeColorRole.values()) {
            properties.setProperty(themePrefix + role.name(), theme.CoreHex(role));
        }
    }

    private static void SyncInputBindings() {
        for (GBButton button : GBButton.values()) {
            properties.setProperty(inputPrefix + button.name(),
                    String.valueOf(Settings.inputBindings.GetKeyCode(button)));
        }
    }

    private static void SyncControllerBindings() {
        properties.setProperty(controllerEnabledKey, String.valueOf(Settings.controllerInputEnabled));
        properties.setProperty(controllerPreferredIdKey,
                Settings.preferredControllerId == null ? "" : Settings.preferredControllerId);
        properties.setProperty(controllerDeadzoneKey, String.valueOf(Settings.controllerDeadzonePercent));
        properties.setProperty(controllerPollingModeKey, Settings.controllerPollingMode.name());
        for (GBButton button : GBButton.values()) {
            ControllerBinding binding = Settings.controllerBindings.GetBinding(button);
            properties.setProperty(controllerInputPrefix + button.name(),
                    binding == null ? "" : binding.ToConfigValue());
        }
    }

    private static void SyncAppShortcuts() {
        for (AppShortcut shortcut : AppShortcut.values()) {
            properties.setProperty(shortcutPrefix + shortcut.name(),
                    Settings.appShortcutBindings.ToConfigValue(shortcut));
            properties.setProperty(controllerShortcutPrefix + shortcut.name(),
                    Settings.appShortcutControllerBindings.ToConfigValue(shortcut));
        }
    }

    private static void SyncSoundSettings() {
        properties.setProperty(soundEnabledKey, String.valueOf(Settings.soundEnabled));
        properties.setProperty(soundVolumeKey, String.valueOf(Settings.masterVolume));
        for (int channelIndex = 0; channelIndex < 4; channelIndex++) {
            properties.setProperty(soundChannelMutedPrefix + channelIndex,
                    String.valueOf(Settings.IsChannelMuted(channelIndex)));
            properties.setProperty(soundChannelVolumePrefix + channelIndex,
                    String.valueOf(Settings.GetChannelVolume(channelIndex)));
        }
        properties.setProperty(soundEnhancementEnabledKey, String.valueOf(Settings.IsAudioEnhancementChainEnabled()));
        properties.setProperty(soundEnhancementChainKey,
                EncodeAudioEnhancementChain(Settings.CurrentAudioEnhancementChain()));
    }

    private static void SyncEmulationSettings() {
        properties.setProperty(useBootRomKey, String.valueOf(Settings.useBootRom));
        properties.setProperty(useCgbBootRomKey, String.valueOf(Settings.useCgbBootRom));
    }

    private static void SyncWindowSettings() {
        properties.setProperty(fillWindowOutputKey, String.valueOf(Settings.fillWindowOutput));
        properties.setProperty(integerScaleWindowOutputKey, String.valueOf(Settings.integerScaleWindowOutput));
        properties.setProperty(displayShaderKey,
                Settings.displayShaderId == null || Settings.displayShaderId.isBlank() ? "none"
                        : Settings.displayShaderId);
        properties.setProperty(displayBorderKey,
                Settings.displayBorderId == null || Settings.displayBorderId.isBlank() ? "none"
                        : Settings.displayBorderId);
        properties.setProperty(showSerialOutputKey, String.valueOf(Settings.showSerialOutput));
        properties.setProperty(displayFrameBlendingKey, String.valueOf(Settings.enableFrameBlending));
        properties.setProperty(showDisplayFpsKey, String.valueOf(Settings.showDisplayFps));
        properties.setProperty(gameArtDisplayModeKey, Settings.gameArtDisplayMode.name());
    }

    private static void SyncLibrarySettings() {
        properties.setProperty(gameNameBracketDisplayModeKey, Settings.gameNameBracketDisplayMode.name());
        properties.setProperty(libraryViewModeKey,
                Settings.libraryViewMode == null || Settings.libraryViewMode.isBlank() ? "LIST"
                        : Settings.libraryViewMode);
        properties.setProperty(libraryFilterModeKey,
                Settings.libraryFilterMode == null || Settings.libraryFilterMode.isBlank() ? "ALL"
                        : Settings.libraryFilterMode);
        properties.setProperty(libraryConsoleFilterKey,
                Settings.libraryConsoleFilter == null || Settings.libraryConsoleFilter.isBlank()
                        ? "ALL"
                        : Settings.libraryConsoleFilter);
        properties.setProperty(librarySortModeKey,
                Settings.librarySortMode == null || Settings.librarySortMode.isBlank()
                        ? "ALPHABETICAL"
                        : Settings.librarySortMode);
        properties.setProperty(librarySearchQueryKey,
                Settings.librarySearchQuery == null ? "" : Settings.librarySearchQuery);
        properties.setProperty(loadRecentMenuLimitKey, String.valueOf(Settings.loadRecentMenuLimit));
    }

    private static void EnsureLoaded() {
        if (!loaded) {
            Load();
        }
    }

    private static void ApplyPaletteToSettings(String[] palette) {
        for (int index = 0; index < palette.length; index++) {
            Settings.SetPaletteColour(index, palette[index]);
        }
    }

    private static void ApplyGbcPaletteToSettings(int paletteIndex, String[] palette) {
        for (int colourIndex = 0; colourIndex < palette.length; colourIndex++) {
            Settings.SetGbcPaletteColour(paletteIndex, colourIndex, palette[colourIndex]);
        }
    }

    private static String[] ToHexPalette(GBColor[] palette) {
        String[] hexPalette = new String[palette.length];
        for (int index = 0; index < palette.length; index++) {
            hexPalette[index] = palette[index].ToHex();
        }
        return hexPalette;
    }

    private static void RemoveLegacyPaletteProperties() {
        properties.remove(savedPaletteListKey);
        properties.remove(savedGbcPaletteListKey);
        properties.remove(preferDmgModeForGbcCompatibleGamesKey);
        properties.remove(gbcPaletteModeEnabledKey);

        List<String> keysToRemove = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(currentPalettePrefix)
                    || key.startsWith(savedPalettePrefix)
                    || key.startsWith(savedGbcPalettePrefix)
                    || key.startsWith(gbcBackgroundPalettePrefix)
                    || key.startsWith(gbcSpritePalette0Prefix)
                    || key.startsWith(gbcSpritePalette1Prefix)) {
                keysToRemove.add(key);
            }
        }

        for (String key : keysToRemove) {
            properties.remove(key);
        }
    }

    private static List<AudioEnhancementSetting> ParseAudioEnhancementChain(String storedValue) {
        List<AudioEnhancementSetting> chain = new ArrayList<>();
        if (storedValue == null || storedValue.isBlank()) {
            return chain;
        }

        for (String token : storedValue.split(",")) {
            AudioEnhancementSetting setting = AudioEnhancementSetting.FromConfigValue(token);
            if (setting != null) {
                chain.add(setting);
            }
        }
        return chain;
    }

    private static String EncodeAudioEnhancementChain(List<AudioEnhancementSetting> chain) {
        if (chain == null || chain.isEmpty()) {
            return "";
        }

        List<String> encoded = new ArrayList<>();
        for (AudioEnhancementSetting setting : chain) {
            if (setting != null) {
                encoded.add(setting.ToConfigValue());
            }
        }
        return String.join(",", encoded);
    }

    private static void Persist() {
        try {
            RemoveLegacyPaletteProperties();
            paletteStore.Save(paletteConfigPath);
            Files.createDirectories(appDataDirectoryPath);
            try (OutputStream outputStream = Files.newOutputStream(configPath)) {
                properties.store(outputStream, "GameDuck configuration");
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private static void MigrateLegacyFileIfNeeded(Path legacyPath, Path newPath) {
        if (!Files.exists(legacyPath) || Files.exists(newPath)) {
            return;
        }

        try {
            Files.createDirectories(appDataDirectoryPath);
            Files.move(legacyPath, newPath);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}
