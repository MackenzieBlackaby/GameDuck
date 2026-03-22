package com.blackaby.Misc;

/**
 * Editable colour slots that make up a host theme.
 */
public enum AppThemeColorRole {
    APP_BACKGROUND(UiText.ThemeColorRoles.APP_BACKGROUND_LABEL, UiText.ThemeColorRoles.APP_BACKGROUND_DESCRIPTION),
    SURFACE(UiText.ThemeColorRoles.SURFACE_LABEL, UiText.ThemeColorRoles.SURFACE_DESCRIPTION),
    ACCENT(UiText.ThemeColorRoles.ACCENT_LABEL, UiText.ThemeColorRoles.ACCENT_DESCRIPTION),
    MUTED_TEXT(UiText.ThemeColorRoles.MUTED_TEXT_LABEL, UiText.ThemeColorRoles.MUTED_TEXT_DESCRIPTION),
    DISPLAY_FRAME(UiText.ThemeColorRoles.DISPLAY_FRAME_LABEL, UiText.ThemeColorRoles.DISPLAY_FRAME_DESCRIPTION),
    SECTION_HIGHLIGHT(UiText.ThemeColorRoles.SECTION_HIGHLIGHT_LABEL, UiText.ThemeColorRoles.SECTION_HIGHLIGHT_DESCRIPTION);

    private final String label;
    private final String description;

    AppThemeColorRole(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /**
     * Returns the UI label for the theme slot.
     *
     * @return role label
     */
    public String Label() {
        return label;
    }

    /**
     * Returns the helper text for the theme slot.
     *
     * @return role description
     */
    public String Description() {
        return description;
    }

    @Deprecated public String label() { return Label(); }
    @Deprecated public String description() { return Description(); }
}
