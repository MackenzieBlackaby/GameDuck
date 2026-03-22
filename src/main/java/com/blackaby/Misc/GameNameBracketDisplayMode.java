package com.blackaby.Misc;

/**
 * Controls which bracketed suffixes are removed from displayed game names.
 */
public enum GameNameBracketDisplayMode {
    NONE(UiText.GameNameDisplayMode.NONE_LABEL, UiText.GameNameDisplayMode.NONE_DESCRIPTION),
    ROUND(UiText.GameNameDisplayMode.ROUND_LABEL, UiText.GameNameDisplayMode.ROUND_DESCRIPTION),
    SQUARE(UiText.GameNameDisplayMode.SQUARE_LABEL, UiText.GameNameDisplayMode.SQUARE_DESCRIPTION),
    BOTH(UiText.GameNameDisplayMode.BOTH_LABEL, UiText.GameNameDisplayMode.BOTH_DESCRIPTION);

    private final String label;
    private final String description;

    GameNameBracketDisplayMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /**
     * Returns the user-facing label for the mode.
     *
     * @return mode label
     */
    public String Label() {
        return label;
    }

    /**
     * Returns a short description of the mode.
     *
     * @return mode description
     */
    public String Description() {
        return description;
    }

    @Override
    public String toString() {
        return label;
    }

    @Deprecated public String label() { return Label(); }
    @Deprecated public String description() { return Description(); }
}
