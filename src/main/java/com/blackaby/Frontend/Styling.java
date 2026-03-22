package com.blackaby.Frontend;

import com.blackaby.Misc.AppTheme;
import com.blackaby.Misc.AppThemePreset;

import java.awt.Color;
import java.awt.Font;

/**
 * Shared visual tokens for the host UI.
 * <p>
 * The mutable colours are derived from the active {@link AppTheme}. Fonts and
 * a few fixed overlay colours remain constant across themes.
 */
public final class Styling {

    public static Color backgroundColour;
    public static Color appBackgroundColour;
    public static Color surfaceColour;
    public static Color surfaceBorderColour;
    public static Color accentColour;
    public static Color mutedTextColour;
    public static Color displayFrameColour;
    public static Color statusBackgroundColour;
    public static Color buttonSecondaryBackground;
    public static Color primaryButtonBorderColour;
    public static Color displayFrameBorderColour;
    public static Color sectionHighlightColour;
    public static Color sectionHighlightBorderColour;
    public static Color cardTintColour;
    public static Color cardTintBorderColour;
    public static Color listSelectionColour;

    public static final Font menuFont = new Font("Roboto", Font.PLAIN, 12);
    public static final Font titleFont = new Font("Roboto", Font.BOLD, 36);
    public static final Color menuBackgroundColour = new Color(0, 0, 0, 0);
    public static final Color menuForegroundColour = Color.BLACK;
    public static final Color menuSelectionBackgroundColour = new Color(0, 0, 0, 0);
    public static final Color menuSelectionForegroundColour = Color.WHITE;
    public static final Color displayBackgroundColour = Color.BLACK;
    public static final Color fpsBackgroundColour = new Color(0, 0, 0, 192);
    public static final Color fpsForegroundColour = Color.WHITE;
    @Deprecated public static Color BACKGROUND_COLOR;
    @Deprecated public static Color APP_BACKGROUND_COLOR;
    @Deprecated public static Color SURFACE_COLOR;
    @Deprecated public static Color SURFACE_BORDER_COLOR;
    @Deprecated public static Color ACCENT_COLOR;
    @Deprecated public static Color MUTED_TEXT_COLOR;
    @Deprecated public static Color DISPLAY_FRAME_COLOR;
    @Deprecated public static Color STATUS_BACKGROUND_COLOR;
    @Deprecated public static Color BUTTON_SECONDARY_BACKGROUND;
    @Deprecated public static Color PRIMARY_BUTTON_BORDER_COLOR;
    @Deprecated public static Color DISPLAY_FRAME_BORDER_COLOR;
    @Deprecated public static Color SECTION_HIGHLIGHT_COLOR;
    @Deprecated public static Color SECTION_HIGHLIGHT_BORDER_COLOR;
    @Deprecated public static Color CARD_TINT_COLOR;
    @Deprecated public static Color CARD_TINT_BORDER_COLOR;
    @Deprecated public static Color LIST_SELECTION_COLOR;
    @Deprecated public static final Font MENU_FONT = menuFont;
    @Deprecated public static final Font TITLE_FONT = titleFont;
    @Deprecated public static final Color MENU_BACKGROUND_COLOR = menuBackgroundColour;
    @Deprecated public static final Color MENU_FOREGROUND_COLOR = menuForegroundColour;
    @Deprecated public static final Color MENU_SELECTION_BACKGROUND_COLOR = menuSelectionBackgroundColour;
    @Deprecated public static final Color MENU_SELECTION_FOREGROUND_COLOR = menuSelectionForegroundColour;
    @Deprecated public static final Color DISPLAY_BACKGROUND_COLOR = displayBackgroundColour;
    @Deprecated public static final Color FPS_BACKGROUND_COLOR = fpsBackgroundColour;
    @Deprecated public static final Color FPS_FOREGROUND_COLOR = fpsForegroundColour;

    static {
        ApplyTheme(AppThemePreset.HARBOR.Theme());
    }

    private Styling() {
    }

    /**
     * Rebuilds the shared colour tokens from the active host theme.
     *
     * @param theme theme to apply
     */
    public static void ApplyTheme(AppTheme theme) {
        backgroundColour = theme.BackgroundColour();
        appBackgroundColour = theme.AppBackgroundColour();
        surfaceColour = theme.SurfaceColour();
        surfaceBorderColour = theme.SurfaceBorderColour();
        accentColour = theme.AccentColour();
        mutedTextColour = theme.MutedTextColour();
        displayFrameColour = theme.DisplayFrameColour();
        statusBackgroundColour = theme.StatusBackgroundColour();
        buttonSecondaryBackground = theme.ButtonSecondaryBackground();
        primaryButtonBorderColour = theme.PrimaryButtonBorderColour();
        displayFrameBorderColour = theme.DisplayFrameBorderColour();
        sectionHighlightColour = theme.SectionHighlightColour();
        sectionHighlightBorderColour = theme.SectionHighlightBorderColour();
        cardTintColour = theme.CardTintColour();
        cardTintBorderColour = theme.CardTintBorderColour();
        listSelectionColour = theme.ListSelectionColour();
        BACKGROUND_COLOR = backgroundColour;
        APP_BACKGROUND_COLOR = appBackgroundColour;
        SURFACE_COLOR = surfaceColour;
        SURFACE_BORDER_COLOR = surfaceBorderColour;
        ACCENT_COLOR = accentColour;
        MUTED_TEXT_COLOR = mutedTextColour;
        DISPLAY_FRAME_COLOR = displayFrameColour;
        STATUS_BACKGROUND_COLOR = statusBackgroundColour;
        BUTTON_SECONDARY_BACKGROUND = buttonSecondaryBackground;
        PRIMARY_BUTTON_BORDER_COLOR = primaryButtonBorderColour;
        DISPLAY_FRAME_BORDER_COLOR = displayFrameBorderColour;
        SECTION_HIGHLIGHT_COLOR = sectionHighlightColour;
        SECTION_HIGHLIGHT_BORDER_COLOR = sectionHighlightBorderColour;
        CARD_TINT_COLOR = cardTintColour;
        CARD_TINT_BORDER_COLOR = cardTintBorderColour;
        LIST_SELECTION_COLOR = listSelectionColour;
    }

    @Deprecated
    public static void applyTheme(AppTheme theme) {
        ApplyTheme(theme);
    }
}
