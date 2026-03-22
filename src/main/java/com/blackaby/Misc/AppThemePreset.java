package com.blackaby.Misc;

/**
 * Built-in host themes offered in the options window.
 */
public enum AppThemePreset {
    HARBOR(UiText.ThemePresets.HARBOR_LABEL, UiText.ThemePresets.HARBOR_DESCRIPTION,
            new AppTheme(UiText.ThemePresets.HARBOR_LABEL, "#EBF2FA", "#F7FBFF", "#223C62", "#5B6E89", "#1A2738", "#E3EFFC")),
    MINT_DMG(UiText.ThemePresets.MINT_DMG_LABEL, UiText.ThemePresets.MINT_DMG_DESCRIPTION,
            new AppTheme(UiText.ThemePresets.MINT_DMG_LABEL, "#E8F1EA", "#F7FBF8", "#355C4A", "#6B7E73", "#18251F", "#E4F0E7")),
    GRAPHITE(UiText.ThemePresets.GRAPHITE_LABEL, UiText.ThemePresets.GRAPHITE_DESCRIPTION,
            new AppTheme(UiText.ThemePresets.GRAPHITE_LABEL, "#EEF1F5", "#FAFBFD", "#3C495C", "#6B7280", "#202A38", "#E6ECF4")),
    SUNSET(UiText.ThemePresets.SUNSET_LABEL, UiText.ThemePresets.SUNSET_DESCRIPTION,
            new AppTheme(UiText.ThemePresets.SUNSET_LABEL, "#F9EFE6", "#FFF8F1", "#8F5446", "#9B786C", "#3C2A25", "#F8E5D8"));

    private final String label;
    private final String description;
    private final AppTheme theme;

    AppThemePreset(String label, String description, AppTheme theme) {
        this.label = label;
        this.description = description;
        this.theme = theme;
    }

    /**
     * Returns the preset label shown in the UI.
     *
     * @return preset label
     */
    public String Label() {
        return label;
    }

    /**
     * Returns a short summary of the preset.
     *
     * @return preset summary
     */
    public String Description() {
        return description;
    }

    /**
     * Returns a copy of the preset theme so callers can edit it safely.
     *
     * @return copied preset theme
     */
    public AppTheme Theme() {
        return theme.Copy();
    }

    @Deprecated public String label() { return Label(); }
    @Deprecated public String description() { return Description(); }
    @Deprecated public AppTheme theme() { return Theme(); }
}
