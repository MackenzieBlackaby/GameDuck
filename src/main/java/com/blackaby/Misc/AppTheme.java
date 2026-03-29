package com.blackaby.Misc;

import java.awt.Color;
import java.util.EnumMap;

/**
 * Stores the editable host theme colours and derives the rest of the UI palette
 * from them.
 * <p>
 * Only the core colour slots are persisted. The other colours are generated so
 * the interface stays visually consistent when a user changes a theme.
 */
public final class AppTheme {

    private final EnumMap<AppThemeColorRole, Color> coreColours = new EnumMap<>(AppThemeColorRole.class);
    private final String name;

    /**
     * Creates a theme from its editable core colour slots.
     *
     * @param name theme name shown in the UI
     * @param appBackground window background colour
     * @param surface panel and card background colour
     * @param accent main emphasis colour
     * @param mutedText subdued text colour
     * @param displayFrame display surround colour
     * @param sectionHighlight section tint colour
     */
    public AppTheme(String name, String appBackground, String surface, String accent,
            String mutedText, String displayFrame, String sectionHighlight) {
        this.name = name;
        coreColours.put(AppThemeColorRole.APP_BACKGROUND, Parse(appBackground));
        coreColours.put(AppThemeColorRole.SURFACE, Parse(surface));
        coreColours.put(AppThemeColorRole.ACCENT, Parse(accent));
        coreColours.put(AppThemeColorRole.MUTED_TEXT, Parse(mutedText));
        coreColours.put(AppThemeColorRole.DISPLAY_FRAME, Parse(displayFrame));
        coreColours.put(AppThemeColorRole.SECTION_HIGHLIGHT, Parse(sectionHighlight));
    }

    /**
     * Creates a deep copy of an existing theme.
     *
     * @param other theme to copy
     */
    public AppTheme(AppTheme other) {
        this.name = other.name;
        for (AppThemeColorRole role : AppThemeColorRole.values()) {
            coreColours.put(role, other.coreColours.get(role));
        }
    }

    /**
     * Returns the display name for the theme.
     *
     * @return theme name
     */
    public String Name() {
        return name;
    }

    /**
     * Returns a deep copy of this theme.
     *
     * @return copied theme
     */
    public AppTheme Copy() {
        return new AppTheme(this);
    }

    /**
     * Returns a copy of this theme with a different display name.
     *
     * @param name replacement theme name
     * @return renamed theme copy
     */
    public AppTheme Renamed(String name) {
        return new AppTheme(name,
                CoreHex(AppThemeColorRole.APP_BACKGROUND),
                CoreHex(AppThemeColorRole.SURFACE),
                CoreHex(AppThemeColorRole.ACCENT),
                CoreHex(AppThemeColorRole.MUTED_TEXT),
                CoreHex(AppThemeColorRole.DISPLAY_FRAME),
                CoreHex(AppThemeColorRole.SECTION_HIGHLIGHT));
    }

    /**
     * Returns the stored colour for a core theme slot.
     *
     * @param role theme slot to read
     * @return colour assigned to the slot
     */
    public Color CoreColour(AppThemeColorRole role) {
        return coreColours.get(role);
    }

    /**
     * Returns a core colour as an uppercase hexadecimal string.
     *
     * @param role theme slot to read
     * @return colour in {@code #RRGGBB} form
     */
    public String CoreHex(AppThemeColorRole role) {
        return ToHex(CoreColour(role));
    }

    /**
     * Replaces a persisted theme slot.
     *
     * @param role theme slot to update
     * @param hex colour in hexadecimal form
     */
    public void SetCoreColour(AppThemeColorRole role, String hex) {
        coreColours.put(role, Parse(hex));
    }

    /**
     * Returns the derived frame background for the whole window.
     *
     * @return derived background colour
     */
    public Color BackgroundColour() {
        return Lighten(CoreColour(AppThemeColorRole.APP_BACKGROUND), 0.12);
    }

    /**
     * Returns the base application background colour.
     *
     * @return app background colour
     */
    public Color AppBackgroundColour() {
        return CoreColour(AppThemeColorRole.APP_BACKGROUND);
    }

    /**
     * Returns the main surface colour used for cards and panels.
     *
     * @return surface colour
     */
    public Color SurfaceColour() {
        return CoreColour(AppThemeColorRole.SURFACE);
    }

    /**
     * Returns the derived border colour for standard surfaces.
     *
     * @return surface border colour
     */
    public Color SurfaceBorderColour() {
        return Mix(SurfaceColour(), AccentColour(), 0.84);
    }

    /**
     * Returns the primary emphasis colour.
     *
     * @return accent colour
     */
    public Color AccentColour() {
        return CoreColour(AppThemeColorRole.ACCENT);
    }

    /**
     * Returns the subdued text colour.
     *
     * @return muted text colour
     */
    public Color MutedTextColour() {
        return CoreColour(AppThemeColorRole.MUTED_TEXT);
    }

    /**
     * Returns the display surround colour.
     *
     * @return display frame colour
     */
    public Color DisplayFrameColour() {
        return CoreColour(AppThemeColorRole.DISPLAY_FRAME);
    }

    /**
     * Returns the background colour for the status bar.
     *
     * @return derived status bar colour
     */
    public Color StatusBackgroundColour() {
        return Mix(AppBackgroundColour(), DisplayFrameColour(), 0.74);
    }

    /**
     * Returns the background colour for secondary buttons.
     *
     * @return derived button colour
     */
    public Color ButtonSecondaryBackground() {
        return Mix(SurfaceColour(), AppBackgroundColour(), 0.84);
    }

    /**
     * Returns the derived border colour for prominent buttons.
     *
     * @return primary button border colour
     */
    public Color PrimaryButtonBorderColour() {
        return Darken(AccentColour(), 0.14);
    }

    /**
     * Returns the derived border colour for the display frame.
     *
     * @return display border colour
     */
    public Color DisplayFrameBorderColour() {
        return Darken(DisplayFrameColour(), 0.14);
    }

    /**
     * Returns the section highlight tint.
     *
     * @return section tint colour
     */
    public Color SectionHighlightColour() {
        return CoreColour(AppThemeColorRole.SECTION_HIGHLIGHT);
    }

    /**
     * Returns the border colour paired with the section tint.
     *
     * @return highlight border colour
     */
    public Color SectionHighlightBorderColour() {
        return Mix(SectionHighlightColour(), AccentColour(), 0.84);
    }

    /**
     * Returns a gentle card tint derived from the surface and background.
     *
     * @return card tint colour
     */
    public Color CardTintColour() {
        return Mix(SurfaceColour(), AppBackgroundColour(), 0.86);
    }

    /**
     * Returns the border colour paired with the card tint.
     *
     * @return card tint border colour
     */
    public Color CardTintBorderColour() {
        return Mix(CardTintColour(), AccentColour(), 0.9);
    }

    /**
     * Returns the list selection tint.
     *
     * @return list selection colour
     */
    public Color ListSelectionColour() {
        return Mix(SectionHighlightColour(), SurfaceColour(), 0.72);
    }

    private static Color Parse(String hex) {
        return Color.decode(hex);
    }

    private static Color Lighten(Color colour, double amount) {
        return Mix(colour, Color.WHITE, 1.0 - amount);
    }

    private static Color Darken(Color colour, double amount) {
        return Mix(colour, Color.BLACK, 1.0 - amount);
    }

    private static Color Mix(Color first, Color second, double firstWeight) {
        double secondWeight = 1.0 - firstWeight;
        int red = Clamp((int) Math.round((first.getRed() * firstWeight) + (second.getRed() * secondWeight)));
        int green = Clamp((int) Math.round((first.getGreen() * firstWeight) + (second.getGreen() * secondWeight)));
        int blue = Clamp((int) Math.round((first.getBlue() * firstWeight) + (second.getBlue() * secondWeight)));
        return new Color(red, green, blue);
    }

    private static int Clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static String ToHex(Color colour) {
        return String.format("#%02X%02X%02X", colour.getRed(), colour.getGreen(), colour.getBlue());
    }
}
