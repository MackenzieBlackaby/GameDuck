package com.blackaby.Misc;

/**
 * Controls which libretro artwork type is shown in the main window.
 */
public enum GameArtDisplayMode {
    BOX_ART(UiText.GameArt.BOX_ART_LABEL, UiText.GameArt.BOX_ART_DESCRIPTION, "Named_Boxarts",
            UiText.GameArt.SOURCE_LIBRETRO_BOXART),
    TITLE_SCREEN(UiText.GameArt.TITLE_SCREEN_LABEL, UiText.GameArt.TITLE_SCREEN_DESCRIPTION, "Named_Titles",
            UiText.GameArt.SOURCE_LIBRETRO_TITLE),
    SCREENSHOT(UiText.GameArt.SCREENSHOT_LABEL, UiText.GameArt.SCREENSHOT_DESCRIPTION, "Named_Snaps",
            UiText.GameArt.SOURCE_LIBRETRO_SCREENSHOT),
    NONE(UiText.GameArt.NONE_LABEL, UiText.GameArt.NONE_DESCRIPTION, "", UiText.GameArt.SOURCE_NONE);

    private final String label;
    private final String description;
    private final String libretroArtType;
    private final String sourceLabel;

    GameArtDisplayMode(String label, String description, String libretroArtType, String sourceLabel) {
        this.label = label;
        this.description = description;
        this.libretroArtType = libretroArtType;
        this.sourceLabel = sourceLabel;
    }

    /**
     * Returns the label shown in host UI selectors.
     *
     * @return display label
     */
    public String Label() {
        return label;
    }

    /**
     * Returns the helper text shown in options.
     *
     * @return description
     */
    public String Description() {
        return description;
    }

    /**
     * Returns the libretro artwork folder for the mode.
     *
     * @return libretro art folder name
     */
    public String LibretroArtType() {
        return libretroArtType;
    }

    /**
     * Returns the attribution label for the mode.
     *
     * @return source label
     */
    public String SourceLabel() {
        return sourceLabel;
    }

    @Override
    public String toString() {
        return label;
    }
}
