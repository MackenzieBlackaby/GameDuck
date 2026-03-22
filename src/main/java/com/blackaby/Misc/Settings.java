package com.blackaby.Misc;

import com.blackaby.Backend.Emulation.Graphics.GBColor;

import java.util.List;

/**
 * Holds the mutable application settings used by the emulator and the host UI.
 * <p>
 * The class keeps the active DMG palette, theme, shortcut bindings, sound
 * options, and a small set of window preferences in one place so the rest of
 * the codebase can read and reset them consistently.
 */
public final class Settings {

    /** Default DMG palette colour 0, used for the lightest shade. */
    public static final String gbColour0 = "#E0F8D0";

    /** Default DMG palette colour 1. */
    public static final String gbColour1 = "#88C070";

    /** Default DMG palette colour 2. */
    public static final String gbColour2 = "#346856";

    /** Default DMG palette colour 3, used for the darkest shade. */
    public static final String gbColour3 = "#081820";

    /** Default GBC background palette used for monochrome games when colourisation is enabled. */
    public static final String[] gbcBackgroundPaletteDefaults = {
            "#F8F8F8", "#B8D8B0", "#607060", "#203028"
    };

    /** Default GBC sprite palette 0 used for monochrome games when colourisation is enabled. */
    public static final String[] gbcSpritePalette0Defaults = {
            "#F8F8F8", "#F8C088", "#C87048", "#502018"
    };

    /** Default GBC sprite palette 1 used for monochrome games when colourisation is enabled. */
    public static final String[] gbcSpritePalette1Defaults = {
            "#F8F8F8", "#88C0F8", "#4868C8", "#182048"
    };

    /** Active DMG palette colour 0. */
    public static GBColor gbColour0Object = new GBColor(gbColour0);

    /** Active DMG palette colour 1. */
    public static GBColor gbColour1Object = new GBColor(gbColour1);

    /** Active DMG palette colour 2. */
    public static GBColor gbColour2Object = new GBColor(gbColour2);

    /** Active DMG palette colour 3. */
    public static GBColor gbColour3Object = new GBColor(gbColour3);

    /** Whether dual-mode CGB-compatible cartridges should boot in original DMG mode. */
    public static boolean preferDmgModeForGbcCompatibleGames = false;

    /** Whether alternate GBC-style palettes should be used for non-CGB games. */
    public static boolean gbcPaletteModeEnabled = false;

    /** Active GBC-style background palette for monochrome games. */
    public static GBColor[] gbcBackgroundPaletteObjects = CreatePalette(gbcBackgroundPaletteDefaults);

    /** Active GBC-style sprite palette 0 for monochrome games. */
    public static GBColor[] gbcSpritePalette0Objects = CreatePalette(gbcSpritePalette0Defaults);

    /** Active GBC-style sprite palette 1 for monochrome games. */
    public static GBColor[] gbcSpritePalette1Objects = CreatePalette(gbcSpritePalette1Defaults);

    /** Active host theme. */
    public static AppTheme appTheme = AppThemePreset.HARBOR.Theme();

    /** Active keyboard bindings for emulated Game Boy buttons. */
    public static final InputBindings inputBindings = new InputBindings();

    /** Active keyboard bindings for host window shortcuts. */
    public static final AppShortcutBindings appShortcutBindings = new AppShortcutBindings();

    /** Whether host audio output is enabled. */
    public static boolean soundEnabled = true;

    /** Master output volume as a percentage from 0 to 100. */
    public static int masterVolume = 100;

    /** Whether each of the four DMG audio channels is muted. */
    public static boolean[] channelMuted = new boolean[] { false, false, false, false };

    /** Per-channel volume as a percentage from 0 to 100. */
    public static int[] channelVolume = new int[] { 100, 100, 100, 100 };

    /** Ordered host-side enhancement chain applied after APU mixing. */
    public static volatile List<AudioEnhancementPreset> audioEnhancementChain = List.of();

    /** Monotonic version used to refresh the live enhancement chain. */
    private static volatile long audioEnhancementChainVersion;

    /** Whether the emulator should boot through an installed DMG boot ROM. */
    public static boolean useBootRom = false;

    /** Whether the emulator should boot through an installed CGB boot ROM for CGB-capable games. */
    public static boolean useCgbBootRom = false;

    /** Whether the main display should stretch to fill the host window. */
    public static boolean fillWindowOutput = false;

    /** Whether the serial output panel should be shown in the main window. */
    public static boolean showSerialOutput = true;

    /** Which libretro artwork type should be shown in the main window. */
    public static GameArtDisplayMode gameArtDisplayMode = GameArtDisplayMode.BOX_ART;

    /** Which bracketed suffixes should be removed from displayed game names. */
    public static GameNameBracketDisplayMode gameNameBracketDisplayMode = GameNameBracketDisplayMode.NONE;
    @Deprecated public static final String GB_COLOR_0 = gbColour0;
    @Deprecated public static final String GB_COLOR_1 = gbColour1;
    @Deprecated public static final String GB_COLOR_2 = gbColour2;
    @Deprecated public static final String GB_COLOR_3 = gbColour3;
    @Deprecated public static GBColor GB_COLOR_0_OBJ = gbColour0Object;
    @Deprecated public static GBColor GB_COLOR_1_OBJ = gbColour1Object;
    @Deprecated public static GBColor GB_COLOR_2_OBJ = gbColour2Object;
    @Deprecated public static GBColor GB_COLOR_3_OBJ = gbColour3Object;
    @Deprecated public static boolean PREFER_DMG_MODE_FOR_GBC_COMPATIBLE_GAMES = preferDmgModeForGbcCompatibleGames;
    @Deprecated public static boolean GBC_PALETTE_MODE_ENABLED = gbcPaletteModeEnabled;
    @Deprecated public static GBColor[] GBC_BACKGROUND_PALETTE = gbcBackgroundPaletteObjects;
    @Deprecated public static GBColor[] GBC_SPRITE_PALETTE_0 = gbcSpritePalette0Objects;
    @Deprecated public static GBColor[] GBC_SPRITE_PALETTE_1 = gbcSpritePalette1Objects;
    @Deprecated public static AppTheme APP_THEME = appTheme;
    @Deprecated public static final InputBindings INPUT_BINDINGS = inputBindings;
    @Deprecated public static final AppShortcutBindings APP_SHORTCUT_BINDINGS = appShortcutBindings;
    @Deprecated public static boolean SOUND_ENABLED = soundEnabled;
    @Deprecated public static int MASTER_VOLUME = masterVolume;
    @Deprecated public static boolean[] CHANNEL_MUTED = channelMuted;
    @Deprecated public static int[] CHANNEL_VOLUME = channelVolume;
    @Deprecated public static List<AudioEnhancementPreset> AUDIO_ENHANCEMENT_CHAIN = audioEnhancementChain;
    @Deprecated public static boolean USE_BOOT_ROM = useBootRom;
    @Deprecated public static boolean USE_CGB_BOOT_ROM = useCgbBootRom;
    @Deprecated public static boolean FILL_WINDOW_OUTPUT = fillWindowOutput;
    @Deprecated public static boolean SHOW_SERIAL_OUTPUT = showSerialOutput;
    @Deprecated public static GameArtDisplayMode GAME_ART_DISPLAY_MODE = gameArtDisplayMode;
    @Deprecated public static GameNameBracketDisplayMode GAME_NAME_BRACKET_DISPLAY_MODE = gameNameBracketDisplayMode;

    private Settings() {
    }

    /**
     * Restores every mutable setting to its default value.
     */
    public static void Reset() {
        ResetPalette();
        ResetAppTheme();
        ResetControls();
        ResetAppShortcuts();
        ResetSound();
        ResetEmulation();
        ResetWindow();
        ResetLibrary();
    }

    /**
     * Restores the DMG palette to the stock colours.
     */
    public static void ResetPalette() {
        gbColour0Object = new GBColor(gbColour0);
        gbColour1Object = new GBColor(gbColour1);
        gbColour2Object = new GBColor(gbColour2);
        gbColour3Object = new GBColor(gbColour3);
        SyncLegacyFields();
    }

    /**
     * Restores GBC-style colourisation settings to their defaults.
     */
    public static void ResetGbcPaletteMode() {
        preferDmgModeForGbcCompatibleGames = false;
        gbcPaletteModeEnabled = false;
        gbcBackgroundPaletteObjects = CreatePalette(gbcBackgroundPaletteDefaults);
        gbcSpritePalette0Objects = CreatePalette(gbcSpritePalette0Defaults);
        gbcSpritePalette1Objects = CreatePalette(gbcSpritePalette1Defaults);
        SyncLegacyFields();
    }

    /**
     * Restores the Game Boy button bindings to their default keys.
     */
    public static void ResetControls() {
        inputBindings.ResetToDefaults();
    }

    /**
     * Restores the host application theme preset.
     */
    public static void ResetAppTheme() {
        ApplyAppTheme(AppThemePreset.HARBOR.Theme());
    }

    /**
     * Applies a host theme and pushes it into the shared UI styling state.
     *
     * @param theme theme to apply
     */
    public static void ApplyAppTheme(AppTheme theme) {
        appTheme = theme.Copy();
        com.blackaby.Frontend.Styling.ApplyTheme(appTheme);
        SyncLegacyFields();
    }

    /**
     * Returns a defensive copy of the current host theme.
     *
     * @return current theme copy
     */
    public static AppTheme CurrentAppTheme() {
        return appTheme.Copy();
    }

    /**
     * Updates one editable theme colour slot and reapplies the theme.
     *
     * @param role theme slot to change
     * @param hex replacement colour in hexadecimal form
     */
    public static void SetAppThemeColour(AppThemeColorRole role, String hex) {
        appTheme.SetCoreColour(role, hex);
        com.blackaby.Frontend.Styling.ApplyTheme(appTheme);
        SyncLegacyFields();
    }

    /**
     * Restores the host shortcut map to its default bindings.
     */
    public static void ResetAppShortcuts() {
        appShortcutBindings.ResetToDefaults();
    }

    /**
     * Restores sound preferences to their defaults.
     */
    public static void ResetSound() {
        soundEnabled = true;
        masterVolume = 100;
        channelMuted = new boolean[] { false, false, false, false };
        channelVolume = new int[] { 100, 100, 100, 100 };
        SetAudioEnhancementChainInternal(List.of(), false);
        SyncLegacyFields();
    }

    /**
     * Returns whether the given DMG channel is muted.
     *
     * @param channelIndex channel index from 0 to 3
     * @return {@code true} when muted
     */
    public static boolean IsChannelMuted(int channelIndex) {
        ValidateChannelIndex(channelIndex);
        return channelMuted[channelIndex];
    }

    /**
     * Sets whether a DMG channel is muted.
     *
     * @param channelIndex channel index from 0 to 3
     * @param muted mute state
     */
    public static void SetChannelMuted(int channelIndex, boolean muted) {
        ValidateChannelIndex(channelIndex);
        channelMuted[channelIndex] = muted;
        SyncLegacyFields();
    }

    /**
     * Returns the configured channel volume as a percentage.
     *
     * @param channelIndex channel index from 0 to 3
     * @return volume from 0 to 100
     */
    public static int GetChannelVolume(int channelIndex) {
        ValidateChannelIndex(channelIndex);
        return channelVolume[channelIndex];
    }

    /**
     * Sets the channel volume as a percentage.
     *
     * @param channelIndex channel index from 0 to 3
     * @param volume volume from 0 to 100
     */
    public static void SetChannelVolume(int channelIndex, int volume) {
        ValidateChannelIndex(channelIndex);
        channelVolume[channelIndex] = Math.max(0, Math.min(100, volume));
        SyncLegacyFields();
    }

    /**
     * Returns the ordered host-side audio enhancement chain.
     *
     * @return immutable enhancement chain
     */
    public static List<AudioEnhancementPreset> CurrentAudioEnhancementChain() {
        return audioEnhancementChain;
    }

    /**
     * Returns the current enhancement-chain version for live audio refresh.
     *
     * @return enhancement-chain version
     */
    public static long AudioEnhancementChainVersion() {
        return audioEnhancementChainVersion;
    }

    /**
     * Replaces the ordered host-side audio enhancement chain.
     *
     * @param chain new enhancement order
     */
    public static void SetAudioEnhancementChain(List<AudioEnhancementPreset> chain) {
        SetAudioEnhancementChainInternal(chain, true);
    }

    /**
     * Restores emulation-specific settings to their defaults.
     */
    public static void ResetEmulation() {
        useBootRom = false;
        useCgbBootRom = false;
        SyncLegacyFields();
    }

    /**
     * Restores window-related settings to their defaults.
     */
    public static void ResetWindow() {
        fillWindowOutput = false;
        showSerialOutput = true;
        gameArtDisplayMode = GameArtDisplayMode.BOX_ART;
        SyncLegacyFields();
    }

    /**
     * Restores library-related settings to their defaults.
     */
    public static void ResetLibrary() {
        gameNameBracketDisplayMode = GameNameBracketDisplayMode.NONE;
        SyncLegacyFields();
    }

    /**
     * Returns the active DMG palette in display order.
     *
     * @return current four-colour palette
     */
    public static GBColor[] CurrentPalette() {
        return new GBColor[] {
                gbColour0Object,
                gbColour1Object,
                gbColour2Object,
                gbColour3Object
        };
    }

    /**
     * Returns the active GBC-style background palette.
     *
     * @return background palette copy
     */
    public static GBColor[] CurrentGbcBackgroundPalette() {
        return CopyPalette(gbcBackgroundPaletteObjects);
    }

    /**
     * Returns the active GBC-style sprite palette 0.
     *
     * @return sprite palette 0 copy
     */
    public static GBColor[] CurrentGbcSpritePalette0() {
        return CopyPalette(gbcSpritePalette0Objects);
    }

    /**
     * Returns the active GBC-style sprite palette 1.
     *
     * @return sprite palette 1 copy
     */
    public static GBColor[] CurrentGbcSpritePalette1() {
        return CopyPalette(gbcSpritePalette1Objects);
    }

    /**
     * Replaces one entry in the active DMG palette.
     *
     * @param index palette index from 0 to 3
     * @param hex colour in hexadecimal form
     */
    public static void SetPaletteColour(int index, String hex) {
        switch (index) {
            case 0 -> gbColour0Object = new GBColor(hex);
            case 1 -> gbColour1Object = new GBColor(hex);
            case 2 -> gbColour2Object = new GBColor(hex);
            case 3 -> gbColour3Object = new GBColor(hex);
            default -> throw new IllegalArgumentException("Invalid palette index: " + index);
        }
        SyncLegacyFields();
    }

    /**
     * Replaces one entry in one of the GBC-style non-CGB palettes.
     *
     * @param paletteIndex palette selector: 0 background, 1 sprite 0, 2 sprite 1
     * @param colourIndex palette colour index from 0 to 3
     * @param hex colour in hexadecimal form
     */
    public static void SetGbcPaletteColour(int paletteIndex, int colourIndex, String hex) {
        GBColor[] targetPalette = switch (paletteIndex) {
            case 0 -> gbcBackgroundPaletteObjects;
            case 1 -> gbcSpritePalette0Objects;
            case 2 -> gbcSpritePalette1Objects;
            default -> throw new IllegalArgumentException("Invalid GBC palette index: " + paletteIndex);
        };

        if (colourIndex < 0 || colourIndex >= 4) {
            throw new IllegalArgumentException("Invalid GBC palette colour index: " + colourIndex);
        }

        targetPalette[colourIndex] = new GBColor(hex);
        SyncLegacyFields();
    }

    private static void SyncLegacyFields() {
        GB_COLOR_0_OBJ = gbColour0Object;
        GB_COLOR_1_OBJ = gbColour1Object;
        GB_COLOR_2_OBJ = gbColour2Object;
        GB_COLOR_3_OBJ = gbColour3Object;
        PREFER_DMG_MODE_FOR_GBC_COMPATIBLE_GAMES = preferDmgModeForGbcCompatibleGames;
        GBC_PALETTE_MODE_ENABLED = gbcPaletteModeEnabled;
        GBC_BACKGROUND_PALETTE = gbcBackgroundPaletteObjects;
        GBC_SPRITE_PALETTE_0 = gbcSpritePalette0Objects;
        GBC_SPRITE_PALETTE_1 = gbcSpritePalette1Objects;
        APP_THEME = appTheme;
        SOUND_ENABLED = soundEnabled;
        MASTER_VOLUME = masterVolume;
        CHANNEL_MUTED = channelMuted;
        CHANNEL_VOLUME = channelVolume;
        AUDIO_ENHANCEMENT_CHAIN = audioEnhancementChain;
        USE_BOOT_ROM = useBootRom;
        USE_CGB_BOOT_ROM = useCgbBootRom;
        FILL_WINDOW_OUTPUT = fillWindowOutput;
        SHOW_SERIAL_OUTPUT = showSerialOutput;
        GAME_ART_DISPLAY_MODE = gameArtDisplayMode;
        GAME_NAME_BRACKET_DISPLAY_MODE = gameNameBracketDisplayMode;
    }

    private static GBColor[] CreatePalette(String[] hexPalette) {
        GBColor[] palette = new GBColor[hexPalette.length];
        for (int index = 0; index < hexPalette.length; index++) {
            palette[index] = new GBColor(hexPalette[index]);
        }
        return palette;
    }

    private static GBColor[] CopyPalette(GBColor[] palette) {
        GBColor[] copy = new GBColor[palette.length];
        for (int index = 0; index < palette.length; index++) {
            copy[index] = new GBColor(palette[index].ToHex());
        }
        return copy;
    }

    private static void ValidateChannelIndex(int channelIndex) {
        if (channelIndex < 0 || channelIndex >= 4) {
            throw new IllegalArgumentException("Invalid channel index: " + channelIndex);
        }
    }

    private static void SetAudioEnhancementChainInternal(List<AudioEnhancementPreset> chain, boolean syncLegacyFields) {
        audioEnhancementChain = chain == null ? List.of() : List.copyOf(chain);
        audioEnhancementChainVersion++;
        if (syncLegacyFields) {
            SyncLegacyFields();
        }
    }

    @Deprecated public static void reset() { Reset(); }
    @Deprecated public static void resetPalette() { ResetPalette(); }
    @Deprecated public static void resetGbcPaletteMode() { ResetGbcPaletteMode(); }
    @Deprecated public static void resetControls() { ResetControls(); }
    @Deprecated public static void resetAppTheme() { ResetAppTheme(); }
    @Deprecated public static void applyAppTheme(AppTheme theme) { ApplyAppTheme(theme); }
    @Deprecated public static AppTheme currentAppTheme() { return CurrentAppTheme(); }
    @Deprecated public static void setAppThemeColor(AppThemeColorRole role, String hex) { SetAppThemeColour(role, hex); }
    @Deprecated public static void resetAppShortcuts() { ResetAppShortcuts(); }
    @Deprecated public static void resetSound() { ResetSound(); }
    @Deprecated public static boolean isChannelMuted(int channelIndex) { return IsChannelMuted(channelIndex); }
    @Deprecated public static void setChannelMuted(int channelIndex, boolean muted) { SetChannelMuted(channelIndex, muted); }
    @Deprecated public static int getChannelVolume(int channelIndex) { return GetChannelVolume(channelIndex); }
    @Deprecated public static void setChannelVolume(int channelIndex, int volume) { SetChannelVolume(channelIndex, volume); }
    @Deprecated public static List<AudioEnhancementPreset> currentAudioEnhancementChain() { return CurrentAudioEnhancementChain(); }
    @Deprecated public static long audioEnhancementChainVersion() { return AudioEnhancementChainVersion(); }
    @Deprecated public static void setAudioEnhancementChain(List<AudioEnhancementPreset> chain) { SetAudioEnhancementChain(chain); }
    @Deprecated public static void resetEmulation() { ResetEmulation(); }
    @Deprecated public static void resetWindow() { ResetWindow(); }
    @Deprecated public static void resetLibrary() { ResetLibrary(); }
    @Deprecated public static GBColor[] currentPalette() { return CurrentPalette(); }
    @Deprecated public static GBColor[] currentGbcBackgroundPalette() { return CurrentGbcBackgroundPalette(); }
    @Deprecated public static GBColor[] currentGbcSpritePalette0() { return CurrentGbcSpritePalette0(); }
    @Deprecated public static GBColor[] currentGbcSpritePalette1() { return CurrentGbcSpritePalette1(); }
    @Deprecated public static void setPaletteColor(int index, String hex) { SetPaletteColour(index, hex); }
    @Deprecated public static void setGbcPaletteColor(int paletteIndex, int colourIndex, String hex) {
        SetGbcPaletteColour(paletteIndex, colourIndex, hex);
    }
}
