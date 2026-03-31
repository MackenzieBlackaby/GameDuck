package com.blackaby.Frontend;

import com.blackaby.Misc.AppTheme;
import com.blackaby.Misc.AppThemePreset;

import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

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

    public static final Font menuFont = resolveFont(12f, Font.PLAIN, "Bahnschrift", "Trebuchet MS", Font.DIALOG);
    public static final Font unicodeFont = resolveFont(12f, Font.PLAIN, "Segoe UI Symbol", "Arial Unicode MS", Font.DIALOG);
    public static final Font titleFont = resolveFont(34f, Font.BOLD, "Bahnschrift", "Trebuchet MS", Font.DIALOG);
    public static final Color menuBackgroundColour = new Color(0, 0, 0, 0);
    public static final Color menuForegroundColour = Color.BLACK;
    public static final Color menuSelectionBackgroundColour = new Color(0, 0, 0, 0);
    public static final Color menuSelectionForegroundColour = Color.WHITE;
    public static final Color displayBackgroundColour = Color.BLACK;
    public static final Color fpsBackgroundColour = new Color(0, 0, 0, 192);
    public static final Color fpsForegroundColour = Color.WHITE;

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

        applySwingThemeDefaults(theme);
        refreshOpenWindows();
    }

    private static void applySwingThemeDefaults(AppTheme theme) {
        Color textForeground = theme.MutedTextColour();
        Color selectionBackground = theme.ListSelectionColour();
        Color selectionForeground = theme.AccentColour();
        Color textSelectionBackground = theme.AccentColour();
        Color textSelectionForeground = theme.SurfaceColour();

        putColor("Panel.background", theme.AppBackgroundColour());
        putColor("Panel.foreground", textForeground);
        putColor("Viewport.background", theme.SurfaceColour());
        putColor("ScrollPane.background", theme.SurfaceColour());
        putColor("TabbedPane.background", theme.AppBackgroundColour());
        putColor("TabbedPane.foreground", theme.AccentColour());
        putColor("TabbedPane.selected", theme.SurfaceColour());
        putColor("TabbedPane.unselectedBackground", theme.ButtonSecondaryBackground());
        putColor("TabbedPane.contentAreaColor", theme.SurfaceColour());
        putColor("TabbedPane.focus", theme.SectionHighlightBorderColour());
        putColor("TabbedPane.highlight", theme.SectionHighlightColour());
        putColor("TabbedPane.light", theme.SectionHighlightColour());
        putColor("TabbedPane.shadow", theme.SurfaceBorderColour());
        putColor("TabbedPane.darkShadow", theme.DisplayFrameBorderColour());
        putColor("TabbedPane.selectHighlight", theme.SectionHighlightBorderColour());
        putColor("Label.foreground", theme.AccentColour());
        putColor("Button.background", theme.AccentColour());
        putColor("Button.foreground", Color.WHITE);
        putColor("Button.select", theme.PrimaryButtonBorderColour());
        putColor("Button.focus", new Color(0, 0, 0, 0));
        putColor("ToggleButton.background", theme.ButtonSecondaryBackground());
        putColor("ToggleButton.foreground", theme.AccentColour());
        putColor("CheckBox.background", theme.AppBackgroundColour());
        putColor("CheckBox.foreground", theme.AccentColour());
        putColor("RadioButton.background", theme.AppBackgroundColour());
        putColor("RadioButton.foreground", theme.AccentColour());
        putColor("ComboBox.background", theme.SurfaceColour());
        putColor("ComboBox.foreground", textForeground);
        putColor("ComboBox.selectionBackground", selectionBackground);
        putColor("ComboBox.selectionForeground", selectionForeground);
        putColor("ComboBox.buttonBackground", theme.ButtonSecondaryBackground());
        putColor("ComboBox.buttonShadow", theme.SurfaceBorderColour());
        putColor("ComboBox.buttonDarkShadow", theme.DisplayFrameBorderColour());
        putColor("ComboBox.buttonHighlight", theme.SurfaceBorderColour());
        putColor("ComboBox.disabledBackground", theme.CardTintColour());
        putColor("ComboBox.disabledForeground", theme.SurfaceBorderColour());
        putColor("List.background", theme.SurfaceColour());
        putColor("List.foreground", textForeground);
        putColor("List.selectionBackground", selectionBackground);
        putColor("List.selectionForeground", selectionForeground);
        putColor("MenuBar.background", theme.SurfaceColour());
        putColor("MenuBar.foreground", theme.MutedTextColour());
        putColor("Menu.background", theme.SurfaceColour());
        putColor("Menu.foreground", theme.MutedTextColour());
        putColor("Menu.selectionBackground", selectionBackground);
        putColor("Menu.selectionForeground", selectionForeground);
        putColor("Menu.disabledForeground", theme.SurfaceBorderColour());
        putColor("Menu.acceleratorForeground", theme.MutedTextColour());
        putColor("MenuItem.background", theme.SurfaceColour());
        putColor("MenuItem.foreground", textForeground);
        putColor("MenuItem.selectionBackground", selectionBackground);
        putColor("MenuItem.selectionForeground", selectionForeground);
        putColor("MenuItem.disabledForeground", theme.SurfaceBorderColour());
        putColor("MenuItem.acceleratorForeground", theme.MutedTextColour());
        putColor("MenuItem.acceleratorSelectionForeground", selectionForeground);
        putColor("CheckBoxMenuItem.background", theme.SurfaceColour());
        putColor("CheckBoxMenuItem.foreground", textForeground);
        putColor("CheckBoxMenuItem.selectionBackground", selectionBackground);
        putColor("CheckBoxMenuItem.selectionForeground", selectionForeground);
        putColor("RadioButtonMenuItem.background", theme.SurfaceColour());
        putColor("RadioButtonMenuItem.foreground", textForeground);
        putColor("RadioButtonMenuItem.selectionBackground", selectionBackground);
        putColor("RadioButtonMenuItem.selectionForeground", selectionForeground);
        putColor("PopupMenu.background", theme.SurfaceColour());
        putColor("PopupMenu.foreground", theme.AccentColour());
        putColor("PopupMenuSeparator.background", theme.SurfaceColour());
        putColor("PopupMenuSeparator.foreground", theme.SurfaceBorderColour());
        putColor("Separator.background", theme.SurfaceBorderColour());
        putColor("Separator.foreground", theme.SurfaceBorderColour());
        putColor("TextField.background", theme.SurfaceColour());
        putColor("TextField.foreground", textForeground);
        putColor("TextField.caretForeground", theme.AccentColour());
        putColor("TextField.inactiveForeground", theme.SurfaceBorderColour());
        putColor("TextField.selectionBackground", textSelectionBackground);
        putColor("TextField.selectionForeground", textSelectionForeground);
        putColor("FormattedTextField.background", theme.SurfaceColour());
        putColor("FormattedTextField.foreground", textForeground);
        putColor("FormattedTextField.caretForeground", theme.AccentColour());
        putColor("FormattedTextField.selectionBackground", textSelectionBackground);
        putColor("FormattedTextField.selectionForeground", textSelectionForeground);
        putColor("PasswordField.background", theme.SurfaceColour());
        putColor("PasswordField.foreground", textForeground);
        putColor("PasswordField.caretForeground", theme.AccentColour());
        putColor("PasswordField.selectionBackground", textSelectionBackground);
        putColor("PasswordField.selectionForeground", textSelectionForeground);
        putColor("TextArea.background", theme.SurfaceColour());
        putColor("TextArea.foreground", textForeground);
        putColor("TextArea.caretForeground", theme.AccentColour());
        putColor("TextArea.selectionBackground", textSelectionBackground);
        putColor("TextArea.selectionForeground", textSelectionForeground);
        putColor("TextPane.background", theme.SurfaceColour());
        putColor("TextPane.foreground", textForeground);
        putColor("TextPane.selectionBackground", textSelectionBackground);
        putColor("TextPane.selectionForeground", textSelectionForeground);
        putColor("EditorPane.background", theme.SurfaceColour());
        putColor("EditorPane.foreground", textForeground);
        putColor("EditorPane.selectionBackground", textSelectionBackground);
        putColor("EditorPane.selectionForeground", textSelectionForeground);
        putColor("Spinner.background", theme.SurfaceColour());
        putColor("Spinner.foreground", textForeground);
        putColor("ToolTip.background", theme.SurfaceColour());
        putColor("ToolTip.foreground", textForeground);
        putColor("OptionPane.background", theme.AppBackgroundColour());
        putColor("OptionPane.foreground", textForeground);
        putColor("OptionPane.messageForeground", textForeground);
        putColor("ScrollBar.thumb", theme.CardTintColour());
        putColor("ScrollBar.track", theme.SurfaceColour());

        UIManager.put("MenuBar.border",
                new BorderUIResource(BorderFactory.createLineBorder(theme.SurfaceBorderColour(), 1)));
        UIManager.put("Menu.border",
                new BorderUIResource(BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        UIManager.put("MenuItem.border",
                new BorderUIResource(BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        UIManager.put("CheckBoxMenuItem.border",
                new BorderUIResource(BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        UIManager.put("RadioButtonMenuItem.border",
                new BorderUIResource(BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        UIManager.put("RootPane.frameBorder",
                new BorderUIResource(BorderFactory.createLineBorder(theme.SurfaceBorderColour(), 1)));
        UIManager.put("PopupMenu.border",
                new BorderUIResource(BorderFactory.createLineBorder(theme.SurfaceBorderColour(), 1)));
        UIManager.put("ToolTip.border",
                new BorderUIResource(BorderFactory.createLineBorder(theme.SurfaceBorderColour(), 1)));
    }

    private static void putColor(String key, Color color) {
        UIManager.put(key, new ColorUIResource(color));
    }

    private static Font resolveFont(float size, int style, String... preferredFamilies) {
        int roundedSize = Math.max(1, Math.round(size));
        if (GraphicsEnvironment.isHeadless()) {
            return new Font(Font.DIALOG, style, roundedSize);
        }

        String[] availableFamilies = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String preferredFamily : preferredFamilies) {
            for (String availableFamily : availableFamilies) {
                if (availableFamily.equalsIgnoreCase(preferredFamily)) {
                    return new Font(availableFamily, style, roundedSize);
                }
            }
        }

        return new Font(Font.DIALOG, style, roundedSize);
    }

    private static void refreshOpenWindows() {
        for (Window window : Window.getWindows()) {
            if (window == null) {
                continue;
            }
            SwingUtilities.updateComponentTreeUI(window);
            WindowUiSupport.applyComponentTheme(window);
        }
    }
}
