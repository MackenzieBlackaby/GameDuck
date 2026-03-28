package com.blackaby.Frontend;

import com.blackaby.Backend.GB.GBBackends;
import com.blackaby.Backend.GB.Graphics.GBColor;
import com.blackaby.Backend.Platform.EmulatorButton;
import com.blackaby.Backend.Platform.EmulatorProfile;
import com.blackaby.Backend.Helpers.ManagedGameRegistry;
import com.blackaby.Backend.Helpers.SaveFileManager;
import com.blackaby.Frontend.Borders.DisplayBorderManager;
import com.blackaby.Frontend.Borders.DisplayBorderPreviewRenderer;
import com.blackaby.Frontend.Borders.LoadedDisplayBorder;
import com.blackaby.Frontend.Shaders.DisplayShaderManager;
import com.blackaby.Frontend.Shaders.LoadedDisplayShader;
import com.blackaby.Misc.AudioEnhancementPreset;
import com.blackaby.Misc.AppShortcut;
import com.blackaby.Misc.AppShortcutBindings;
import com.blackaby.Misc.AppTheme;
import com.blackaby.Misc.AppThemeColorRole;
import com.blackaby.Misc.AudioEnhancementSetting;
import com.blackaby.Misc.BootRomManager;
import com.blackaby.Misc.Config;
import com.blackaby.Misc.ControllerBinding;
import com.blackaby.Misc.GameArtDisplayMode;
import com.blackaby.Misc.GameNameBracketDisplayMode;
import com.blackaby.Misc.Settings;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Hosts the application options window.
 * <p>
 * The window groups palette editing, control rebinding, sound settings, window
 * layout, and host theme changes in one place.
 */
public class OptionsWindow extends DuckWindow {

    private enum DmgPaletteModeOption {
        GB_PALETTE(UiText.OptionsWindow.DMG_PALETTE_MODE_GB, false),
        GBC_COLOURISATION(UiText.OptionsWindow.DMG_PALETTE_MODE_GBC, true);

        private final String label;
        private final boolean gbcPaletteModeEnabled;

        DmgPaletteModeOption(String label, boolean gbcPaletteModeEnabled) {
            this.label = label;
            this.gbcPaletteModeEnabled = gbcPaletteModeEnabled;
        }

        private static DmgPaletteModeOption fromSetting(boolean gbcPaletteModeEnabled) {
            return gbcPaletteModeEnabled ? GBC_COLOURISATION : GB_PALETTE;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum GbcCompatiblePaletteModeOption {
        FULL_COLOUR(UiText.OptionsWindow.GBC_COMPATIBLE_MODE_FULL_COLOUR, false),
        GB_PALETTE_ON_COMPATIBLE_GAMES(UiText.OptionsWindow.GBC_COMPATIBLE_MODE_GB_PALETTE, true);

        private final String label;
        private final boolean preferDmgModeForCompatibleGames;

        GbcCompatiblePaletteModeOption(String label, boolean preferDmgModeForCompatibleGames) {
            this.label = label;
            this.preferDmgModeForCompatibleGames = preferDmgModeForCompatibleGames;
        }

        private static GbcCompatiblePaletteModeOption fromSetting(boolean preferDmgModeForCompatibleGames) {
            return preferDmgModeForCompatibleGames ? GB_PALETTE_ON_COMPATIBLE_GAMES : FULL_COLOUR;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Color panelBackground;
    private final Color cardBackground;
    private final Color cardBorder;
    private final Color accentColour;
    private final Color mutedText;
    private final ControllerInputService controllerInputService = ControllerInputService.Shared();

    private final JPanel[] paletteStripPreviews = new JPanel[4];
    private final JPanel[] colorPreviews = new JPanel[4];
    private final JLabel[] colorHexLabels = new JLabel[4];
    private final JPanel[] gbcColorPreviews = new JPanel[12];
    private final JLabel[] gbcColorHexLabels = new JLabel[12];
    private final JPanel[] themeStripPreviews = new JPanel[AppThemeColorRole.values().length];
    private final Map<EmulatorButton, JButton> bindingButtons = new HashMap<>();
    private final Map<EmulatorButton, JButton> controllerBindingButtons = new HashMap<>();
    private final EnumMap<AppShortcut, JButton> shortcutButtons = new EnumMap<>(AppShortcut.class);
    private final EnumMap<AppShortcut, JButton> controllerShortcutButtons = new EnumMap<>(AppShortcut.class);
    private final MainWindow mainWindow;
    private final int initialTabIndex;
    private JTabbedPane tabs;
    private JComboBox<DmgPaletteModeOption> dmgPaletteModeSelector;
    private JComboBox<GbcCompatiblePaletteModeOption> gbcCompatiblePaletteModeSelector;
    private JComboBox<ControllerChoice> controllerSelector;
    private JCheckBox controllerEnabledCheckBox;
    private JLabel controllerActiveValueLabel;
    private JLabel controllerStatusBadgeLabel;
    private JLabel controllerStatusHelperLabel;
    private JLabel controllerLiveInputsArea;
    private JLabel controllerMappedButtonsArea;
    private JLabel controllerDeadzoneValueLabel;
    private JSlider controllerDeadzoneSlider;
    private Timer controllerRefreshTimer;
    private boolean updatingControllerUi;
    private List<String> lastControllerDeviceEntries = List.of();

    public OptionsWindow(MainWindow mainWindow) {
        this(mainWindow, 0);
    }

    public OptionsWindow(MainWindow mainWindow, int initialTabIndex) {
        super(UiText.OptionsWindow.WINDOW_TITLE, 920, 760, true);
        this.mainWindow = mainWindow;
        this.initialTabIndex = initialTabIndex;
        this.panelBackground = Styling.appBackgroundColour;
        this.cardBackground = Styling.surfaceColour;
        this.cardBorder = Styling.surfaceBorderColour;
        this.accentColour = Styling.accentColour;
        this.mutedText = Styling.mutedTextColour;
        setMinimumSize(new Dimension(760, 620));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(panelBackground);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTabbedContent(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        controllerRefreshTimer = new Timer(250, event -> refreshControllerStatus());
        controllerRefreshTimer.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (controllerRefreshTimer != null) {
                    controllerRefreshTimer.stop();
                }
            }
        });
        refreshControllerStatus();

        setVisible(true);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setBackground(panelBackground);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(58, 92, 132, 24)),
                BorderFactory.createEmptyBorder(20, 22, 12, 22)));

        JLabel titleLabel = new JLabel(UiText.OptionsWindow.HEADER_TITLE);
        titleLabel.setFont(Styling.titleFont);
        titleLabel.setForeground(accentColour);

        JTextArea subtitleLabel = createBodyTextArea(UiText.OptionsWindow.HEADER_SUBTITLE, 13f);

        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subtitleLabel, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildTabbedContent() {
        tabs = new JTabbedPane();
        tabs.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        tabs.setBackground(panelBackground);
        tabs.setForeground(accentColour);
        tabs.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));

        if (backendProfile().capabilities().supportsPaletteConfiguration()) {
            tabs.addTab(UiText.OptionsWindow.TAB_PALETTE, buildTabScrollPane(buildPaletteTab()));
        }
        tabs.addTab(UiText.OptionsWindow.TAB_CONTROLS, buildTabScrollPane(buildControlsTab()));
        tabs.addTab(UiText.OptionsWindow.TAB_SOUND, buildTabScrollPane(buildSoundTab()));
        if (backendProfile().capabilities().supportsSaveDataManagement()
                || backendProfile().capabilities().supportsBootRomConfiguration()
                || backendProfile().capabilities().supportsSaveStates()) {
            tabs.addTab(UiText.OptionsWindow.TAB_EMULATION, buildTabScrollPane(buildEmulationTab()));
        }
        tabs.addTab(UiText.OptionsWindow.TAB_WINDOW, buildTabScrollPane(buildWindowTab()));
        tabs.addTab(UiText.OptionsWindow.TAB_LIBRARY, buildTabScrollPane(buildLibraryTab()));
        tabs.addTab(UiText.OptionsWindow.TAB_THEME, buildTabScrollPane(buildThemeTab()));
        tabs.addChangeListener(event -> {
            if (isControlsTabSelected()) {
                refreshControllerStatus();
            }
            resetSelectedTabScrollPosition();
        });
        if (initialTabIndex >= 0 && initialTabIndex < tabs.getTabCount()) {
            tabs.setSelectedIndex(initialTabIndex);
        }
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(this::resetSelectedTabScrollPosition));
        return tabs;
    }

    private JScrollPane buildTabScrollPane(JComponent content) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(panelBackground);
        return scrollPane;
    }

    private void resetSelectedTabScrollPosition() {
        if (tabs == null) {
            return;
        }

        Component selectedComponent = tabs.getSelectedComponent();
        if (!(selectedComponent instanceof JScrollPane scrollPane)) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            scrollPane.getViewport().setViewPosition(new Point(0, 0));
            scrollPane.getVerticalScrollBar().setValue(0);
        });
    }

    private JComponent buildPaletteTab() {
        JPanel content = createVerticalContentPanel();
        content.add(createSectionCard(
                UiText.OptionsWindow.SECTION_PALETTE_TITLE,
                UiText.OptionsWindow.SECTION_PALETTE_DESCRIPTION,
                createPaletteLibraryPanel()));
        content.add(Box.createVerticalStrut(16));
        content.add(createSectionCard(
                UiText.OptionsWindow.SECTION_GBC_TITLE,
                UiText.OptionsWindow.SECTION_GBC_DESCRIPTION,
                createGbcPalettePanel()));
        return content;
    }

    private JComponent buildControlsTab() {
        JPanel content = createVerticalContentPanel();
        content.add(createSectionCard(
                UiText.OptionsWindow.SECTION_CONTROLS_TITLE,
                UiText.OptionsWindow.SECTION_CONTROLS_DESCRIPTION,
                createControlsPanel()));
        content.add(Box.createVerticalStrut(16));
        content.add(createSectionCard(
                UiText.OptionsWindow.SECTION_CONTROLLER_TITLE,
                UiText.OptionsWindow.SECTION_CONTROLLER_DESCRIPTION,
                createControllerPanel()));
        content.add(Box.createVerticalStrut(16));
        content.add(createSectionCard(
                UiText.OptionsWindow.SECTION_SHORTCUTS_TITLE,
                UiText.OptionsWindow.SECTION_SHORTCUTS_DESCRIPTION,
                createShortcutPanel()));
        content.add(Box.createVerticalGlue());
        return content;
    }

    private JComponent buildSoundTab() {
        JPanel content = createVerticalContentPanel();
        content.add(createSectionCard(
                UiText.OptionsWindow.SECTION_SOUND_TITLE,
                UiText.OptionsWindow.SECTION_SOUND_DESCRIPTION,
                createSoundPanel()));
        return content;
    }

    private JComponent buildEmulationTab() {
        JPanel content = createVerticalContentPanel();
        content.add(createSectionCard(
                UiText.OptionsWindow.SECTION_EMULATION_TITLE,
                UiText.OptionsWindow.SECTION_EMULATION_DESCRIPTION,
                createEmulationPanel()));
        return content;
    }

    private JComponent buildWindowTab() {
        JPanel content = createVerticalContentPanel();
        content.add(createSectionCard(
                UiText.OptionsWindow.SECTION_WINDOW_TITLE,
                UiText.OptionsWindow.SECTION_WINDOW_DESCRIPTION,
                createWindowPanel()));
        content.add(Box.createVerticalStrut(16));
        content.add(createSectionCard(
                UiText.OptionsWindow.SECTION_DISPLAY_SHADER_TITLE,
                UiText.OptionsWindow.SECTION_DISPLAY_SHADER_DESCRIPTION,
                createDisplayShaderPanel()));
        return content;
    }

    private JComponent buildLibraryTab() {
        JPanel content = createVerticalContentPanel();
        content.add(createSectionCard(
                UiText.OptionsWindow.SECTION_LIBRARY_TITLE,
                UiText.OptionsWindow.SECTION_LIBRARY_DESCRIPTION,
                createLibraryPanel()));
        return content;
    }

    private JComponent buildThemeTab() {
        JPanel content = createVerticalContentPanel();
        content.add(createSectionCard(
                UiText.OptionsWindow.SECTION_THEME_LIBRARY_TITLE,
                UiText.OptionsWindow.SECTION_THEME_LIBRARY_DESCRIPTION,
                createThemeLibraryPanel()));
        return content;
    }

    private JPanel createVerticalContentPanel() {
        JPanel content = new VerticalScrollPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(panelBackground);
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        return content;
    }

    private JPanel createSectionCard(String title, String description, JComponent body) {
        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setBackground(cardBackground);
        card.setBorder(createCardBorder());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel header = createInfoTextBlock(title, description, 20f);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));

        card.add(header, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JTextArea createBodyTextArea(String text, float fontSize) {
        JTextArea area = createWrappingTextArea(text == null ? "" : text);
        area.setFont(Styling.menuFont.deriveFont(Font.PLAIN, fontSize));
        area.setForeground(mutedText);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }

    private JPanel createInfoTextBlock(String titleText, String helperText, float titleFontSize) {
        JPanel textBlock = new JPanel();
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        textBlock.setOpaque(false);

        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, titleFontSize));
        titleLabel.setForeground(accentColour);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textBlock.add(titleLabel);

        if (shouldRenderUiText(helperText)) {
            textBlock.add(Box.createVerticalStrut(4));
            textBlock.add(createBodyTextArea(helperText, 12f));
        }

        return textBlock;
    }

    private JPanel createResponsiveGroup(int minTileWidth, int maxColumns, JComponent... components) {
        JPanel panel = new ResponsiveTileGridPanel(minTileWidth, maxColumns);
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        for (JComponent component : components) {
            if (component == null) {
                continue;
            }
            panel.add(component);
        }
        return panel;
    }

    private JPanel createFillStackPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        return panel;
    }

    private void addFillStackRow(JPanel panel, JComponent component, int row, int topInset) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(topInset, 0, 0, 0);
        panel.add(component, constraints);
    }

    private JComponent createPaletteLibraryPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        content.add(createPalettePreviewBanner());
        content.add(Box.createVerticalStrut(12));

        dmgPaletteModeSelector = new JComboBox<>(DmgPaletteModeOption.values());
        dmgPaletteModeSelector.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        dmgPaletteModeSelector.setSelectedItem(DmgPaletteModeOption.fromSetting(Settings.gbcPaletteModeEnabled));
        dmgPaletteModeSelector.addActionListener(event -> {
            Object selectedItem = dmgPaletteModeSelector.getSelectedItem();
            if (selectedItem instanceof DmgPaletteModeOption selectedMode) {
                Settings.gbcPaletteModeEnabled = selectedMode.gbcPaletteModeEnabled;
            }
            Config.Save();
        });

        content.add(createPaletteModeSelectorCard(
                UiText.OptionsWindow.GBC_NON_CGB_MODE_TITLE,
                UiText.OptionsWindow.GBC_NON_CGB_MODE_HELPER,
                dmgPaletteModeSelector));
        content.add(Box.createVerticalStrut(12));

        JPanel actionCard = new JPanel(new BorderLayout());
        actionCard.setBackground(Styling.cardTintColour);
        actionCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JPanel textColumn = createInfoTextBlock(
                UiText.OptionsWindow.SAVE_CURRENT_PALETTE,
                UiText.OptionsWindow.SAVE_CURRENT_PALETTE_HELPER,
                13f);

        JTextField paletteNameField = new JTextField();
        paletteNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        paletteNameField.setPreferredSize(new Dimension(240, 38));
        paletteNameField.setFont(Styling.menuFont.deriveFont(13f));
        paletteNameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardBorder, 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        textColumn.add(Box.createVerticalStrut(8));
        textColumn.add(paletteNameField);

        JButton savePaletteButton = createPrimaryButton(UiText.OptionsWindow.SAVE_PALETTE_BUTTON);
        savePaletteButton.addActionListener(event -> {
            String name = paletteNameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, UiText.OptionsWindow.PaletteNameRequiredMessage(),
                        UiText.Common.WARNING_TITLE,
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            Config.SavePalette(name);
            JOptionPane.showMessageDialog(this, UiText.OptionsWindow.PaletteSavedMessage(name));
        });

        JButton loadPaletteButton = createSecondaryButton(UiText.OptionsWindow.BROWSE_BUTTON);
        loadPaletteButton.addActionListener(event -> new PaletteManager(this::refreshPaletteDetails));

        JPanel buttonColumn = createResponsiveGroup(120, 2, savePaletteButton, loadPaletteButton);
        actionCard.add(createResponsiveGroup(280, 2, textColumn, buttonColumn), BorderLayout.CENTER);

        content.add(actionCard);
        content.add(Box.createVerticalStrut(12));

        JButton resetPaletteButton = createSecondaryButton(UiText.OptionsWindow.RESET_PALETTE_BUTTON);
        resetPaletteButton.addActionListener(event -> {
            Settings.ResetPalette();
            refreshPaletteDetails();
            Config.Save();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        actions.setOpaque(false);
        actions.add(resetPaletteButton);
        content.add(actions);
        return content;
    }

    private JComponent createGbcPalettePanel() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);

        gbcCompatiblePaletteModeSelector = new JComboBox<>(GbcCompatiblePaletteModeOption.values());
        gbcCompatiblePaletteModeSelector.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        gbcCompatiblePaletteModeSelector.setSelectedItem(
                GbcCompatiblePaletteModeOption.fromSetting(Settings.preferDmgModeForGbcCompatibleGames));
        gbcCompatiblePaletteModeSelector.addActionListener(event -> {
            Object selectedItem = gbcCompatiblePaletteModeSelector.getSelectedItem();
            if (selectedItem instanceof GbcCompatiblePaletteModeOption selectedMode) {
                Settings.preferDmgModeForGbcCompatibleGames = selectedMode.preferDmgModeForCompatibleGames;
            }
            Config.Save();
        });

        JPanel toggleStack = new JPanel();
        toggleStack.setLayout(new BoxLayout(toggleStack, BoxLayout.Y_AXIS));
        toggleStack.setOpaque(false);
        toggleStack.add(createPaletteModeSelectorCard(
                UiText.OptionsWindow.GBC_COMPATIBLE_MODE_TITLE,
                UiText.OptionsWindow.GBC_COMPATIBLE_MODE_HELPER,
                gbcCompatiblePaletteModeSelector));
        toggleStack.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel paletteGrid = createResponsiveGroup(220, 3);
        paletteGrid.add(createGbcPaletteRow(0, UiText.OptionsWindow.GBC_BACKGROUND_PALETTE_TITLE,
                UiText.OptionsWindow.GBC_BACKGROUND_PALETTE_HELPER,
                Settings.CurrentGbcBackgroundPalette()));
        paletteGrid.add(createGbcPaletteRow(1, UiText.OptionsWindow.GBC_SPRITE0_PALETTE_TITLE,
                UiText.OptionsWindow.GBC_SPRITE0_PALETTE_HELPER,
                Settings.CurrentGbcSpritePalette0()));
        paletteGrid.add(createGbcPaletteRow(2, UiText.OptionsWindow.GBC_SPRITE1_PALETTE_TITLE,
                UiText.OptionsWindow.GBC_SPRITE1_PALETTE_HELPER,
                Settings.CurrentGbcSpritePalette1()));

        JPanel actionCard = new JPanel(new BorderLayout());
        actionCard.setBackground(Styling.cardTintColour);
        actionCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        actionCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textColumn = createInfoTextBlock(
                UiText.OptionsWindow.SAVE_CURRENT_GBC_PALETTE,
                UiText.OptionsWindow.SAVE_CURRENT_GBC_PALETTE_HELPER,
                13f);

        JTextField paletteNameField = new JTextField();
        paletteNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        paletteNameField.setPreferredSize(new Dimension(240, 38));
        paletteNameField.setFont(Styling.menuFont.deriveFont(13f));
        paletteNameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardBorder, 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        textColumn.add(Box.createVerticalStrut(8));
        textColumn.add(paletteNameField);

        JButton savePaletteButton = createPrimaryButton(UiText.OptionsWindow.SAVE_PALETTE_BUTTON);
        savePaletteButton.addActionListener(event -> {
            String name = paletteNameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, UiText.OptionsWindow.PaletteNameRequiredMessage(),
                        UiText.Common.WARNING_TITLE,
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            Config.SaveGbcPalette(name);
            JOptionPane.showMessageDialog(this, UiText.OptionsWindow.PaletteSavedMessage(name));
        });

        JButton loadPaletteButton = createSecondaryButton(UiText.OptionsWindow.BROWSE_BUTTON);
        loadPaletteButton.addActionListener(
                event -> new PaletteManager(PaletteManager.PaletteKind.GBC, this::refreshPaletteDetails));

        JPanel buttonColumn = createResponsiveGroup(120, 2, savePaletteButton, loadPaletteButton);
        actionCard.add(createResponsiveGroup(280, 2, textColumn, buttonColumn), BorderLayout.CENTER);

        JButton resetGbcPaletteButton = createSecondaryButton(UiText.OptionsWindow.RESET_GBC_SETTINGS_BUTTON);
        resetGbcPaletteButton.addActionListener(event -> {
            Settings.ResetGbcPaletteMode();
            if (gbcCompatiblePaletteModeSelector != null) {
                gbcCompatiblePaletteModeSelector.setSelectedItem(
                        GbcCompatiblePaletteModeOption.fromSetting(Settings.preferDmgModeForGbcCompatibleGames));
            }
            if (dmgPaletteModeSelector != null) {
                dmgPaletteModeSelector
                        .setSelectedItem(DmgPaletteModeOption.fromSetting(Settings.gbcPaletteModeEnabled));
            }
            refreshPaletteDetails();
            Config.Save();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        actions.setOpaque(false);
        actions.add(resetGbcPaletteButton);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);

        container.add(toggleStack);
        container.add(Box.createVerticalStrut(10));
        container.add(paletteGrid);
        container.add(Box.createVerticalStrut(10));
        container.add(actionCard);
        container.add(Box.createVerticalStrut(10));
        container.add(actions);
        return container;
    }

    private JComponent createPaletteModeSelectorCard(String titleText, String helperText, JComboBox<?> selector) {
        JPanel toggleCard = new JPanel(new BorderLayout());
        toggleCard.setBackground(Styling.sectionHighlightColour);
        toggleCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        JPanel toggleText = createInfoTextBlock(titleText, helperText, 16f);

        JPanel toggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toggleWrap.setOpaque(false);
        selector.setPreferredSize(new Dimension(220, 34));
        selector.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        selector.setBackground(Color.WHITE);
        selector.setForeground(accentColour);
        toggleWrap.add(selector);

        toggleCard.add(createResponsiveGroup(260, 2, toggleText, toggleWrap), BorderLayout.CENTER);
        return toggleCard;
    }

    private JTextArea createWrappingTextArea(String text) {
        JTextArea area = new JTextArea(text) {
            @Override
            public Dimension getPreferredSize() {
                int width = getWidth();
                if (width <= 0 && getParent() != null) {
                    width = getParent().getWidth();
                }
                if (width <= 0) {
                    width = 280;
                }
                setSize(width, Short.MAX_VALUE);
                return super.getPreferredSize();
            }
        };
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder());
        return area;
    }

    private JComponent createGbcPaletteRow(int paletteIndex, String titleText, String helperText, GBColor[] palette) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);

        JLabel title = new JLabel(titleText);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        title.setForeground(accentColour);

        text.add(title);
        if (shouldRenderUiText(helperText)) {
            text.add(Box.createVerticalStrut(3));
            text.add(createBodyTextArea(helperText, 12f));
        }

        JPanel swatchGrid = new JPanel(new GridLayout(2, 2, 8, 8));
        swatchGrid.setOpaque(false);
        for (int colourIndex = 0; colourIndex < palette.length; colourIndex++) {
            swatchGrid.add(createGbcPaletteSwatch(paletteIndex, titleText, colourIndex, palette[colourIndex]));
        }

        card.add(text, BorderLayout.NORTH);
        card.add(swatchGrid, BorderLayout.CENTER);
        return card;
    }

    private JComponent createGbcPaletteSwatch(int paletteIndex, String paletteTitle, int colourIndex, GBColor colour) {
        int flatIndex = (paletteIndex * 4) + colourIndex;
        MouseAdapter chooseListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                chooseGbcPaletteColor(paletteIndex, colourIndex);
            }
        };

        JPanel swatch = new JPanel();
        swatch.setPreferredSize(new Dimension(52, 52));
        swatch.setBackground(colour.ToColour());
        swatch.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(58, 92, 132, 45), 1, true),
                BorderFactory.createEmptyBorder(3, 3, 3, 3)));
        swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        swatch.setToolTipText(UiText.OptionsWindow.ChooseColorTitle(
                paletteTitle + " " + UiText.OptionsWindow.GbcPaletteButtonLabel(colourIndex)));
        swatch.addMouseListener(chooseListener);
        gbcColorPreviews[flatIndex] = swatch;

        JLabel hexLabel = new JLabel(colour.ToHex().toUpperCase());
        hexLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 9f));
        hexLabel.setForeground(accentColour);
        hexLabel.setOpaque(true);
        hexLabel.setBackground(new Color(255, 255, 255, 180));
        hexLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(160, 186, 216), 1, true),
                BorderFactory.createEmptyBorder(2, 3, 2, 3)));
        gbcColorHexLabels[flatIndex] = hexLabel;

        JPanel hexWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        hexWrap.setOpaque(false);
        hexWrap.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hexWrap.addMouseListener(chooseListener);
        hexLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hexLabel.addMouseListener(chooseListener);
        hexWrap.add(hexLabel);

        swatch.setLayout(new BorderLayout());
        swatch.add(hexWrap, BorderLayout.SOUTH);

        JButton chooseButton = createSecondaryButton(UiText.OptionsWindow.GbcPaletteButtonLabel(colourIndex));
        chooseButton.setFont(Styling.menuFont.deriveFont(Font.BOLD, 11f));
        chooseButton.setPreferredSize(new Dimension(52, 24));
        chooseButton.addActionListener(event -> chooseGbcPaletteColor(paletteIndex, colourIndex));

        JPanel cell = new JPanel();
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        cell.setOpaque(false);
        swatch.setAlignmentX(Component.CENTER_ALIGNMENT);
        chooseButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        cell.add(swatch);
        cell.add(Box.createVerticalStrut(4));
        cell.add(chooseButton);
        return cell;
    }

    private JComponent createThemeLibraryPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JComponent previewBanner = createThemePreviewBanner();
        previewBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(previewBanner);
        content.add(Box.createVerticalStrut(12));

        JPanel actionCard = new JPanel(new BorderLayout());
        actionCard.setBackground(Styling.cardTintColour);
        actionCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        actionCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textColumn = createInfoTextBlock(
                UiText.OptionsWindow.SAVE_CURRENT_THEME,
                UiText.OptionsWindow.SAVE_CURRENT_THEME_HELPER,
                13f);

        JTextField themeNameField = new JTextField();
        themeNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        themeNameField.setPreferredSize(new Dimension(240, 38));
        themeNameField.setFont(Styling.menuFont.deriveFont(13f));
        themeNameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardBorder, 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        textColumn.add(Box.createVerticalStrut(4));
        textColumn.add(themeNameField);

        JButton saveThemeButton = createPrimaryButton(UiText.OptionsWindow.SAVE_THEME_BUTTON);
        saveThemeButton.addActionListener(event -> {
            String name = themeNameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, UiText.OptionsWindow.ThemeNameRequiredMessage(),
                        UiText.Common.WARNING_TITLE,
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                Config.SaveTheme(name);
                JOptionPane.showMessageDialog(this, UiText.OptionsWindow.ThemeSavedMessage(name));
            } catch (IllegalStateException exception) {
                JOptionPane.showMessageDialog(this, exception.getMessage(),
                        UiText.Common.WARNING_TITLE,
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton browseThemesButton = createSecondaryButton(UiText.OptionsWindow.BROWSE_BUTTON);
        browseThemesButton.addActionListener(event -> new ThemeManager(() -> {
            if (mainWindow != null) {
                mainWindow.RefreshTheme();
            }
            reopenWithCurrentTab();
        }));

        JPanel buttonColumn = createResponsiveGroup(120, 2, saveThemeButton, browseThemesButton);
        actionCard.add(createResponsiveGroup(280, 2, textColumn, buttonColumn), BorderLayout.CENTER);

        content.add(actionCard);
        content.add(Box.createVerticalStrut(12));

        JButton resetThemeButton = createSecondaryButton(UiText.OptionsWindow.RESET_THEME_BUTTON);
        resetThemeButton.addActionListener(event -> {
            Settings.ResetAppTheme();
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshTheme();
            }
            reopenWithCurrentTab();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.add(resetThemeButton);
        content.add(actions);
        return content;
    }

    private JComponent createControlsPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 18));
        container.setOpaque(false);
        container.add(createInputMapperLauncherCard(), BorderLayout.CENTER);
        return container;
    }

    private JComponent createControllerPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 18));
        container.setOpaque(false);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);

        stack.add(createControllerSettingsCard());
        stack.add(Box.createVerticalStrut(12));
        stack.add(createControllerLiveTesterCard());

        container.add(stack, BorderLayout.CENTER);
        return container;
    }

    private JComponent createInputMapperLauncherCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Styling.sectionHighlightColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel textBlock = createInfoTextBlock(
                UiText.OptionsWindow.INPUT_MAPPER_LAUNCH_TITLE,
                UiText.OptionsWindow.INPUT_MAPPER_LAUNCH_DESCRIPTION,
                18f);

        JPanel actionColumn = new JPanel();
        actionColumn.setOpaque(false);
        actionColumn.setLayout(new BoxLayout(actionColumn, BoxLayout.Y_AXIS));

        JButton openMapperButton = createPrimaryButton(UiText.OptionsWindow.INPUT_MAPPER_OPEN_BUTTON);
        openMapperButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
        openMapperButton.addActionListener(event -> new InputMappingWindow(this, mainWindow));

        JLabel badgeLabel = createBadgeLabel(controlBadgeText());
        badgeLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        actionColumn.add(badgeLabel);
        actionColumn.add(Box.createVerticalStrut(10));
        actionColumn.add(openMapperButton);

        card.add(createResponsiveGroup(280, 2, textBlock, actionColumn), BorderLayout.CENTER);
        return card;
    }

    private JComponent createControllerSettingsCard() {
        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        controllerEnabledCheckBox = new JCheckBox(UiText.OptionsWindow.CONTROLLER_ENABLE_CHECKBOX,
                Settings.controllerInputEnabled);
        controllerEnabledCheckBox.setOpaque(false);
        controllerEnabledCheckBox.setForeground(accentColour);
        controllerEnabledCheckBox.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        controllerEnabledCheckBox.addActionListener(event -> {
            if (updatingControllerUi) {
                return;
            }
            Settings.controllerInputEnabled = controllerEnabledCheckBox.isSelected();
            Config.Save();
            refreshControllerStatus();
        });

        JButton refreshControllerButton = createSecondaryButton(UiText.OptionsWindow.CONTROLLER_REFRESH_BUTTON);
        refreshControllerButton.addActionListener(event -> {
            controllerInputService.RefreshControllers();
            refreshControllerStatus();
        });

        JPanel topRow = createResponsiveGroup(220, 2, controllerEnabledCheckBox, refreshControllerButton);

        controllerSelector = new JComboBox<>();
        controllerSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        controllerSelector.addActionListener(event -> {
            if (updatingControllerUi) {
                return;
            }
            ControllerChoice selectedChoice = (ControllerChoice) controllerSelector.getSelectedItem();
            String preferredId = selectedChoice == null ? "" : selectedChoice.id();
            if (!preferredId.equals(Settings.preferredControllerId)) {
                Settings.preferredControllerId = preferredId;
                Config.Save();
                controllerInputService.RefreshControllers();
                refreshControllerStatus();
            }
        });

        controllerActiveValueLabel = createValueLabel(UiText.OptionsWindow.CONTROLLER_NONE_CONNECTED);
        controllerStatusBadgeLabel = createBadgeLabel(UiText.OptionsWindow.CONTROLLER_STATUS_DISCONNECTED);
        controllerStatusHelperLabel = new JLabel(UiText.OptionsWindow.CONTROLLER_STATUS_HELPER);
        controllerStatusHelperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        controllerStatusHelperLabel.setForeground(mutedText);

        controllerDeadzoneSlider = new JSlider(0, 95, Settings.controllerDeadzonePercent);
        controllerDeadzoneSlider.setOpaque(false);
        controllerDeadzoneSlider.addChangeListener(event -> {
            if (updatingControllerUi) {
                return;
            }
            Settings.controllerDeadzonePercent = controllerDeadzoneSlider.getValue();
            if (controllerDeadzoneValueLabel != null) {
                controllerDeadzoneValueLabel.setText(UiText.OptionsWindow.PercentValue(Settings.controllerDeadzonePercent));
            }
            if (!controllerDeadzoneSlider.getValueIsAdjusting()) {
                Config.Save();
            }
        });
        controllerDeadzoneValueLabel = createValueLabel(UiText.OptionsWindow.PercentValue(Settings.controllerDeadzonePercent));

        JPanel grid = createResponsiveGroup(240, 2);
        grid.add(createFieldCard(UiText.OptionsWindow.CONTROLLER_SELECTION_LABEL, controllerSelector));
        grid.add(createFieldCard(UiText.OptionsWindow.CONTROLLER_ACTIVE_LABEL, controllerActiveValueLabel));
        grid.add(createFieldCard(UiText.OptionsWindow.CONTROLLER_DEADZONE_LABEL, wrapControllerDeadzoneControls()));
        grid.add(createFieldCard(UiText.OptionsWindow.CONTROLLER_STATUS_LABEL, wrapControllerStatusControls()));

        card.add(topRow, BorderLayout.NORTH);
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JComponent createControllerLiveTesterCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        controllerLiveInputsArea = createCompactReadoutLabel(UiText.OptionsWindow.CONTROLLER_LIVE_NONE);
        controllerMappedButtonsArea = createCompactReadoutLabel(UiText.OptionsWindow.CONTROLLER_MAPPED_NONE);

        card.add(createResponsiveGroup(
                260,
                2,
                createFieldCard(UiText.OptionsWindow.CONTROLLER_LIVE_INPUTS_LABEL, controllerLiveInputsArea),
                createFieldCard(UiText.OptionsWindow.CONTROLLER_MAPPED_BUTTONS_LABEL, controllerMappedButtonsArea)),
                BorderLayout.CENTER);
        return card;
    }

    private JComponent createControllerBindingCard(EmulatorButton button) {
        return createActionBindingCard(
                formatControlButtonName(button),
                controlButtonHelper(button),
                null,
                false,
                Settings.controllerBindings.GetBindingText(button),
                new Dimension(148, 38),
                13f,
                12,
                14,
                () -> captureControllerBinding(button),
                bindingButton -> controllerBindingButtons.put(button, bindingButton));
    }

    private JComponent createShortcutPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 18));
        container.setOpaque(false);

        JPanel stack = createFillStackPanel();
        addFillStackRow(stack, createBindingIntroCard(
                UiText.OptionsWindow.WINDOW_SHORTCUTS_TITLE,
                UiText.OptionsWindow.WINDOW_SHORTCUTS_DESCRIPTION,
                UiText.OptionsWindow.WINDOW_SHORTCUTS_BADGE), 0, 0);

        JPanel grid = createResponsiveGroup(280, AppShortcut.values().length);

        for (AppShortcut shortcut : AppShortcut.values()) {
            grid.add(createShortcutCard(shortcut));
        }

        addFillStackRow(stack, grid, 1, 10);
        container.add(stack, BorderLayout.CENTER);

        JButton resetShortcutsButton = createSecondaryButton(UiText.OptionsWindow.RESET_SHORTCUTS_BUTTON);
        resetShortcutsButton.addActionListener(event -> {
            Settings.ResetAppShortcuts();
            refreshShortcutButtons();
            if (mainWindow != null) {
                mainWindow.RefreshAppShortcuts();
            }
            Config.Save();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        actions.setOpaque(false);
        actions.add(resetShortcutsButton);
        container.add(actions, BorderLayout.SOUTH);

        return container;
    }

    private JComponent createBindingCard(EmulatorButton button) {
        return createActionBindingCard(
                formatControlButtonName(button),
                controlButtonHelper(button),
                controlBadgeText(),
                false,
                Settings.inputBindings.GetKeyText(button),
                new Dimension(128, 40),
                14f,
                12,
                14,
                () -> captureBinding(button),
                bindingButton -> bindingButtons.put(button, bindingButton));
    }

    private JComponent createShortcutCard(AppShortcut shortcut) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JLabel titleLabel = new JLabel(shortcut.Label());
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        titleLabel.setForeground(accentColour);

        JPanel labelPanel = new JPanel(new BorderLayout(0, 3));
        labelPanel.setOpaque(false);
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(titleLabel, BorderLayout.WEST);
        titleRow.add(createBadgeLabel(UiText.OptionsWindow.APP_BADGE), BorderLayout.EAST);
        labelPanel.add(titleRow, BorderLayout.NORTH);
        labelPanel.add(createBodyTextArea(shortcut.Description(), 11f), BorderLayout.CENTER);

        JButton keyboardButton = createPrimaryButton(Settings.appShortcutBindings.GetKeyText(shortcut));
        keyboardButton.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        keyboardButton.setPreferredSize(new Dimension(108, 30));
        keyboardButton.addActionListener(event -> captureShortcut(shortcut));
        shortcutButtons.put(shortcut, keyboardButton);

        JButton controllerButton = createSecondaryButton(Settings.appShortcutControllerBindings.GetBindingText(shortcut));
        controllerButton.setFont(Styling.menuFont.deriveFont(Font.BOLD, 11f));
        controllerButton.setPreferredSize(new Dimension(108, 28));
        controllerButton.addActionListener(event -> captureControllerShortcut(shortcut));
        controllerShortcutButtons.put(shortcut, controllerButton);

        JPanel bindingsStack = new JPanel();
        bindingsStack.setLayout(new BoxLayout(bindingsStack, BoxLayout.Y_AXIS));
        bindingsStack.setOpaque(false);
        bindingsStack.add(createShortcutBindingRow(UiText.OptionsWindow.SHORTCUT_KEYBOARD_LABEL, keyboardButton));
        bindingsStack.add(Box.createVerticalStrut(6));
        bindingsStack.add(createShortcutBindingRow(UiText.OptionsWindow.SHORTCUT_CONTROLLER_LABEL, controllerButton));

        card.add(createResponsiveGroup(240, 2, labelPanel, bindingsStack), BorderLayout.CENTER);
        return card;
    }

    private JComponent createShortcutBindingRow(String labelText, JButton actionButton) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(true);
        row.setBackground(Styling.sectionHighlightColour);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        JLabel rowLabel = new JLabel(labelText);
        rowLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 9f));
        rowLabel.setForeground(mutedText);
        rowLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));

        JPanel buttonWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonWrap.setOpaque(false);
        buttonWrap.add(actionButton);

        row.add(rowLabel, BorderLayout.WEST);
        row.add(buttonWrap, BorderLayout.CENTER);
        return row;
    }

    private JComponent createActionBindingCard(String title, String helperText, String badgeText,
                                               boolean wrapHelperText, String buttonText, Dimension buttonSize,
                                               float buttonFontSize, int verticalPadding, int horizontalPadding,
                                               Runnable action, Consumer<JButton> buttonRegistrar) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(verticalPadding, horizontalPadding, verticalPadding, horizontalPadding)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(accentColour);

        JPanel labelPanel = new JPanel(new BorderLayout(0, 4));
        labelPanel.setOpaque(false);
        if (badgeText == null || badgeText.isBlank()) {
            labelPanel.add(titleLabel, BorderLayout.NORTH);
        } else {
            JPanel titleRow = new JPanel(new BorderLayout());
            titleRow.setOpaque(false);
            titleRow.add(titleLabel, BorderLayout.WEST);
            titleRow.add(createBadgeLabel(badgeText), BorderLayout.EAST);
            labelPanel.add(titleRow, BorderLayout.NORTH);
        }
        labelPanel.add(createBodyTextArea(helperText, wrapHelperText ? 11f : 12f), BorderLayout.CENTER);

        JButton actionButton = createPrimaryButton(buttonText);
        actionButton.setFont(Styling.menuFont.deriveFont(Font.BOLD, buttonFontSize));
        actionButton.setPreferredSize(buttonSize);
        actionButton.addActionListener(event -> action.run());
        buttonRegistrar.accept(actionButton);

        JPanel buttonWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonWrap.setOpaque(false);
        buttonWrap.add(actionButton);

        card.add(createResponsiveGroup(240, 2, labelPanel, buttonWrap), BorderLayout.CENTER);
        return card;
    }

    private JComponent createBindingIntroCard(String title, String description, String badgeText) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Styling.sectionHighlightColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel textBlock = createInfoTextBlock(title, description, 16f);

        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        badgeWrap.setOpaque(false);
        badgeWrap.add(createBadgeLabel(badgeText));

        card.add(createResponsiveGroup(260, 2, textBlock, badgeWrap), BorderLayout.CENTER);
        return card;
    }

    private JComponent createPalettePreviewBanner() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Styling.sectionHighlightColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        JPanel textBlock = createInfoTextBlock(
                UiText.OptionsWindow.ACTIVE_DMG_PALETTE_TITLE,
                UiText.OptionsWindow.ACTIVE_DMG_PALETTE_HELPER,
                16f);

        JPanel swatchStrip = createResponsiveGroup(52, 4);

        GBColor[] palette = Settings.CurrentPalette();
        String[] toneNames = UiText.OptionsWindow.DMG_TONE_NAMES;
        for (int i = 0; i < palette.length; i++) {
            JPanel swatch = new JPanel();
            swatch.setPreferredSize(new Dimension(48, 48));
            swatch.setBackground(palette[i].ToColour());
            swatch.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(58, 92, 132, 60), 1, true),
                    BorderFactory.createEmptyBorder(4, 4, 4, 4)));
            swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            swatch.setToolTipText(UiText.OptionsWindow.EditPaletteToneTooltip(toneNames[i]));
            final int paletteIndex = i;
            swatch.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    chooseColor(paletteIndex, UiText.OptionsWindow.PaletteToneColorLabel(toneNames[paletteIndex]));
                }
            });
            paletteStripPreviews[i] = swatch;
            swatchStrip.add(swatch);
        }

        card.add(createResponsiveGroup(260, 2, textBlock, swatchStrip), BorderLayout.CENTER);
        return card;
    }

    private JComponent createThemePreviewBanner() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Styling.sectionHighlightColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        JPanel textBlock = createInfoTextBlock(
                UiText.OptionsWindow.ACTIVE_APP_THEME_TITLE,
                UiText.OptionsWindow.ACTIVE_APP_THEME_HELPER,
                16f);

        JPanel swatchStrip = createResponsiveGroup(44, AppThemeColorRole.values().length);

        AppTheme currentTheme = Settings.CurrentAppTheme();
        for (AppThemeColorRole role : AppThemeColorRole.values()) {
            final AppThemeColorRole swatchRole = role;
            JPanel swatch = new JPanel();
            swatch.setPreferredSize(new Dimension(38, 38));
            swatch.setBackground(currentTheme.CoreColour(role));
            swatch.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(58, 92, 132, 60), 1, true),
                    BorderFactory.createEmptyBorder(3, 3, 3, 3)));
            swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            swatch.setToolTipText(UiText.OptionsWindow.ChooseColorTitle(role.Label()));
            swatch.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    chooseThemeColor(swatchRole);
                }
            });
            themeStripPreviews[role.ordinal()] = swatch;
            swatchStrip.add(swatch);
        }

        card.add(createResponsiveGroup(260, 2, textBlock, swatchStrip), BorderLayout.CENTER);
        return card;
    }

    private JComponent createSoundPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 18));
        container.setOpaque(false);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);

        JCheckBox[] channelMuteCheckBoxes = new JCheckBox[4];
        AudioKnob[] channelVolumeKnobs = new AudioKnob[4];
        AudioKnob[] masterVolumeKnobHolder = new AudioKnob[1];
        DefaultListModel<AudioEnhancementSetting> enhancementChainModel = new DefaultListModel<>();
        for (AudioEnhancementSetting setting : Settings.CurrentAudioEnhancementChain()) {
            enhancementChainModel.addElement(setting);
        }

        JCheckBox soundEnabledCheckBox = new JCheckBox(UiText.OptionsWindow.SOUND_ENABLED_CHECKBOX,
                Settings.soundEnabled);
        soundEnabledCheckBox.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        soundEnabledCheckBox.setForeground(accentColour);
        soundEnabledCheckBox.setBackground(Styling.sectionHighlightColour);
        soundEnabledCheckBox.addActionListener(event -> {
            Settings.soundEnabled = soundEnabledCheckBox.isSelected();
            Config.Save();
            mainWindow.GetEmulation().ResetTransientAudioState();
        });

        JPanel outputCard = new JPanel(new BorderLayout());
        outputCard.setBackground(Styling.sectionHighlightColour);
        outputCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel outputText = createInfoTextBlock(
                UiText.OptionsWindow.PLAYBACK_TITLE,
                UiText.OptionsWindow.PLAYBACK_HELPER,
                16f);

        JPanel toggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toggleWrap.setOpaque(false);
        toggleWrap.add(soundEnabledCheckBox);

        outputCard.add(createResponsiveGroup(280, 2, outputText, toggleWrap), BorderLayout.CENTER);
        stack.add(outputCard);
        stack.add(Box.createVerticalStrut(10));

        JPanel volumeCard = new JPanel(new BorderLayout(0, 14));
        volumeCard.setBackground(Styling.cardTintColour);
        volumeCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JLabel masterValueBadge = createBadgeLabel(UiText.OptionsWindow.PercentValue(Settings.masterVolume));
        JPanel volumeHeader = createResponsiveGroup(
                260,
                2,
                createInfoTextBlock(
                        UiText.OptionsWindow.MASTER_VOLUME_TITLE,
                        UiText.OptionsWindow.MASTER_VOLUME_HELPER,
                        15f),
                masterValueBadge);

        JComponent masterKnobTile = createMixerKnobTile(
                UiText.OptionsWindow.MASTER_VOLUME_TITLE,
                UiText.OptionsWindow.MASTER_VOLUME_HELPER,
                Settings.masterVolume,
                (newValue, adjusting) -> {
                    Settings.masterVolume = newValue;
                    masterValueBadge.setText(UiText.OptionsWindow.PercentValue(newValue));
                    if (!adjusting) {
                        Config.Save();
                    }
                },
                masterVolumeKnobHolder,
                0,
                null);

        volumeCard.add(volumeHeader, BorderLayout.NORTH);
        volumeCard.add(masterKnobTile, BorderLayout.CENTER);
        stack.add(volumeCard);
        stack.add(Box.createVerticalStrut(10));

        JPanel channelCard = new JPanel(new BorderLayout(0, 12));
        channelCard.setBackground(Styling.cardTintColour);
        channelCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        JPanel channelHeader = createInfoTextBlock(
                UiText.OptionsWindow.CHANNEL_MIXER_TITLE,
                UiText.OptionsWindow.CHANNEL_MIXER_HELPER,
                15f);

        JPanel channelGrid = new ResponsiveTileGridPanel(116, 4);
        channelGrid.setOpaque(false);

        for (int channelIndex = 0; channelIndex < 4; channelIndex++) {
            channelGrid.add(createChannelMixerKnobTile(
                    channelIndex,
                    channelMuteCheckBoxes,
                    channelVolumeKnobs));
        }

        channelCard.add(channelHeader, BorderLayout.NORTH);
        channelCard.add(channelGrid, BorderLayout.CENTER);
        stack.add(channelCard);
        stack.add(Box.createVerticalStrut(10));

        JCheckBox enhancementEnabledCheckBox = new JCheckBox(UiText.OptionsWindow.AUDIO_ENHANCEMENTS_ENABLED_CHECKBOX,
                Settings.IsAudioEnhancementChainEnabled());
        enhancementEnabledCheckBox.setOpaque(false);
        enhancementEnabledCheckBox.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        enhancementEnabledCheckBox.setForeground(accentColour);
        enhancementEnabledCheckBox.addActionListener(event -> {
            Settings.SetAudioEnhancementChainEnabled(enhancementEnabledCheckBox.isSelected());
            Config.Save();
        });

        stack.add(createAudioEnhancementCard(enhancementChainModel, enhancementEnabledCheckBox));

        container.add(stack, BorderLayout.CENTER);

        JButton resetSoundButton = createSecondaryButton(UiText.OptionsWindow.RESET_SOUND_BUTTON);
        resetSoundButton.addActionListener(event -> {
            Settings.ResetSound();
            soundEnabledCheckBox.setSelected(Settings.soundEnabled);
            if (masterVolumeKnobHolder[0] != null) {
                masterVolumeKnobHolder[0].SetValue(Settings.masterVolume);
            }
            masterValueBadge.setText(UiText.OptionsWindow.PercentValue(Settings.masterVolume));
            for (int channelIndex = 0; channelIndex < 4; channelIndex++) {
                if (channelMuteCheckBoxes[channelIndex] != null) {
                    channelMuteCheckBoxes[channelIndex].setSelected(Settings.IsChannelMuted(channelIndex));
                }
                if (channelVolumeKnobs[channelIndex] != null) {
                    channelVolumeKnobs[channelIndex].SetValue(Settings.GetChannelVolume(channelIndex));
                }
            }
            enhancementChainModel.clear();
            enhancementEnabledCheckBox.setSelected(Settings.IsAudioEnhancementChainEnabled());
            Config.Save();
            mainWindow.GetEmulation().ResetTransientAudioState();
        });

        JPanel actions = createResponsiveGroup(180, 1, resetSoundButton);
        container.add(actions, BorderLayout.SOUTH);

        return container;
    }

    private JComponent createAudioEnhancementCard(DefaultListModel<AudioEnhancementSetting> enhancementChainModel,
            JCheckBox enhancementEnabledCheckBox) {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setBackground(Styling.surfaceColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel toggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toggleWrap.setOpaque(false);
        toggleWrap.add(enhancementEnabledCheckBox);

        JPanel header = createResponsiveGroup(
                280,
                2,
                createInfoTextBlock(
                        UiText.OptionsWindow.AUDIO_ENHANCEMENTS_TITLE,
                        UiText.OptionsWindow.AUDIO_ENHANCEMENTS_HELPER,
                        15f),
                toggleWrap);

        JPanel composer = new JPanel(new BorderLayout(8, 8));
        composer.setOpaque(true);
        composer.setBackground(Styling.sectionHighlightColour);
        composer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JComboBox<AudioEnhancementPreset> presetSelector = new JComboBox<>(AudioEnhancementPreset.values());
        presetSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        presetSelector.setBackground(Styling.surfaceColour);
        presetSelector.setForeground(accentColour);
        presetSelector.setPreferredSize(new Dimension(0, 32));

        JTextArea presetDescription = createBodyTextArea("", 11f);
        updateAudioEnhancementDescription(presetSelector, presetDescription);
        presetSelector.addActionListener(event -> updateAudioEnhancementDescription(presetSelector, presetDescription));

        JButton addButton = createSecondaryButton(UiText.OptionsWindow.ADD_TO_CHAIN_BUTTON);
        addButton.setPreferredSize(new Dimension(108, 32));
        addButton.addActionListener(event -> {
            Object selectedPreset = presetSelector.getSelectedItem();
            if (selectedPreset instanceof AudioEnhancementPreset enhancementPreset) {
                enhancementChainModel.addElement(AudioEnhancementSetting.Default(enhancementPreset));
                applyAudioEnhancementModel(enhancementChainModel);
            }
        });

        JPanel composerText = createInfoTextBlock(UiText.OptionsWindow.ADD_PRESET_TITLE, "", 13f);
        composerText.add(Box.createVerticalStrut(4));
        composerText.add(presetDescription);

        JPanel composerControls = createResponsiveGroup(168, 2, presetSelector, addButton);
        composer.add(createResponsiveGroup(280, 2, composerText, composerControls), BorderLayout.CENTER);

        JPanel chainCard = new JPanel(new BorderLayout(0, 8));
        chainCard.setOpaque(true);
        chainCard.setBackground(Styling.surfaceColour);
        chainCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JButton clearButton = createSecondaryButton(UiText.OptionsWindow.CLEAR_CHAIN_BUTTON);
        clearButton.setPreferredSize(new Dimension(102, 30));
        clearButton.addActionListener(event -> {
            if (!enhancementChainModel.isEmpty()) {
                enhancementChainModel.clear();
                applyAudioEnhancementModel(enhancementChainModel);
            }
        });

        JPanel chainHeader = createResponsiveGroup(
                280,
                2,
                createInfoTextBlock(
                        UiText.OptionsWindow.ACTIVE_CHAIN_TITLE,
                        UiText.OptionsWindow.ACTIVE_CHAIN_HELPER,
                        13f),
                clearButton);

        JPanel chainStack = new VerticalScrollPanel();
        chainStack.setLayout(new BoxLayout(chainStack, BoxLayout.Y_AXIS));
        chainStack.setOpaque(false);

        JScrollPane chainScrollPane = new JScrollPane(chainStack);
        chainScrollPane.setBorder(BorderFactory.createEmptyBorder());
        chainScrollPane.getViewport().setBackground(Styling.surfaceColour);
        chainScrollPane.setPreferredSize(new Dimension(0, 330));
        chainScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        Runnable rebuildCards = () -> rebuildAudioEnhancementCards(enhancementChainModel, chainStack, chainScrollPane);
        enhancementChainModel.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent event) {
                rebuildCards.run();
            }

            @Override
            public void intervalRemoved(ListDataEvent event) {
                rebuildCards.run();
            }

            @Override
            public void contentsChanged(ListDataEvent event) {
                chainStack.repaint();
            }
        });

        rebuildCards.run();

        chainCard.add(chainHeader, BorderLayout.NORTH);
        chainCard.add(chainScrollPane, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(composer, BorderLayout.NORTH);
        body.add(chainCard, BorderLayout.CENTER);

        card.add(header, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void rebuildAudioEnhancementCards(DefaultListModel<AudioEnhancementSetting> enhancementChainModel,
            JPanel chainStack, JScrollPane chainScrollPane) {
        chainStack.removeAll();

        if (enhancementChainModel.isEmpty()) {
            JPanel emptyCard = new JPanel(new BorderLayout());
            emptyCard.setOpaque(true);
            emptyCard.setBackground(Styling.cardTintColour);
            emptyCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                    BorderFactory.createEmptyBorder(18, 18, 18, 18)));

            JLabel emptyLabel = new JLabel(UiText.OptionsWindow.AUDIO_ENHANCEMENTS_EMPTY_STATE, SwingConstants.CENTER);
            emptyLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
            emptyLabel.setForeground(mutedText);
            emptyCard.add(emptyLabel, BorderLayout.CENTER);

            chainStack.add(emptyCard);
        } else {
            for (int index = 0; index < enhancementChainModel.size(); index++) {
                chainStack.add(createAudioEnhancementEffectCard(enhancementChainModel, index, chainStack));
            }
        }

        chainStack.revalidate();
        chainStack.repaint();
        chainScrollPane.revalidate();
        chainScrollPane.repaint();
    }

    private JComponent createAudioEnhancementEffectCard(DefaultListModel<AudioEnhancementSetting> enhancementChainModel,
            int index, JPanel chainStack) {
        AudioEnhancementSetting setting = enhancementChainModel.getElementAt(index);

        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferredSize = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferredSize.height);
            }

            @Override
            public Dimension getMinimumSize() {
                Dimension minimumSize = super.getMinimumSize();
                return new Dimension(0, minimumSize.height);
            }
        };
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0,
                index == enhancementChainModel.size() - 1 ? 0 : 8, 0));

        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setOpaque(true);
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        wrapper.add(card, BorderLayout.CENTER);

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);

        JLabel dragHandle = new JLabel(":::");
        dragHandle.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        dragHandle.setForeground(mutedText);
        dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        dragHandle.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 4));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel effectTitle = new JLabel(setting.preset().Label());
        effectTitle.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        effectTitle.setForeground(accentColour);
        textPanel.add(effectTitle);

        JTextArea effectDescription = createWrappingTextArea(setting.preset().Description());
        effectDescription.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 11f));
        effectDescription.setForeground(mutedText);
        effectDescription.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        textPanel.add(effectDescription);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(createBadgeLabel("#" + (index + 1)));

        JButton removeButton = createSecondaryButton("-");
        removeButton.setToolTipText(UiText.OptionsWindow.REMOVE_BUTTON);
        removeButton.setPreferredSize(new Dimension(38, 28));
        removeButton.addActionListener(event -> {
            if (index >= 0 && index < enhancementChainModel.size()) {
                enhancementChainModel.remove(index);
                applyAudioEnhancementModel(enhancementChainModel);
            }
        });
        actions.add(removeButton);

        header.add(dragHandle, BorderLayout.WEST);
        header.add(textPanel, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);
        installResponsiveAudioEffectCardLayout(card, header, actions, effectTitle, effectDescription, removeButton);

        AudioEnhancementPreset.ParameterSpec primaryParameter = setting.preset().PrimaryParameter();
        AudioEnhancementPreset.ParameterSpec secondaryParameter = setting.preset().SecondaryParameter();
        int knobCount = 1 + (primaryParameter == null ? 0 : 1) + (secondaryParameter == null ? 0 : 1);

        JPanel knobRow = new ResponsiveAudioKnobPanel(knobCount);
        knobRow.setOpaque(false);

        knobRow.add(new AudioKnob(
                UiText.OptionsWindow.EFFECT_INTENSITY_TITLE,
                UiText.OptionsWindow.EFFECT_INTENSITY_HELPER,
                setting.intensityPercent(),
                (newValue, adjusting) -> updateAudioEnhancementSetting(
                        enhancementChainModel,
                        index,
                        current -> current.WithIntensity(newValue),
                        !adjusting)));

        if (primaryParameter != null) {
            knobRow.add(new AudioKnob(
                    primaryParameter.label(),
                    primaryParameter.description(),
                    setting.primaryPercent(),
                    (newValue, adjusting) -> updateAudioEnhancementSetting(
                            enhancementChainModel,
                            index,
                            current -> current.WithPrimary(newValue),
                            !adjusting)));
        }

        if (secondaryParameter != null) {
            knobRow.add(new AudioKnob(
                    secondaryParameter.label(),
                    secondaryParameter.description(),
                    setting.secondaryPercent(),
                    (newValue, adjusting) -> updateAudioEnhancementSetting(
                            enhancementChainModel,
                            index,
                            current -> current.WithSecondary(newValue),
                            !adjusting)));
        }

        final int sourceIndex = index;
        MouseAdapter dragListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragHandle.setForeground(accentColour);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragHandle.setForeground(mutedText);
                if (sourceIndex < 0 || sourceIndex >= enhancementChainModel.size()) {
                    return;
                }

                Point dropPoint = SwingUtilities.convertPoint(dragHandle, event.getPoint(), chainStack);
                int insertionIndex = audioEnhancementDropIndex(chainStack, dropPoint.y);
                if (insertionIndex == sourceIndex || insertionIndex == sourceIndex + 1) {
                    return;
                }
                int targetIndex = insertionIndex > sourceIndex ? insertionIndex - 1 : insertionIndex;

                AudioEnhancementSetting movedSetting = enhancementChainModel.getElementAt(sourceIndex);
                enhancementChainModel.remove(sourceIndex);
                if (targetIndex >= enhancementChainModel.size()) {
                    enhancementChainModel.addElement(movedSetting);
                } else {
                    enhancementChainModel.add(targetIndex, movedSetting);
                }
                applyAudioEnhancementModel(enhancementChainModel);
            }
        };
        dragHandle.addMouseListener(dragListener);

        card.add(header, BorderLayout.NORTH);
        card.add(knobRow, BorderLayout.CENTER);
        return wrapper;
    }

    private JComponent createMixerKnobTile(String title, String helperText, int initialValue,
            AudioKnobListener listener, AudioKnob[] knobHolders, int holderIndex, JComponent footer) {
        JPanel tile = new JPanel(new BorderLayout(0, 8));
        tile.setOpaque(true);
        tile.setBackground(Styling.sectionHighlightColour);
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        AudioKnob knob = new AudioKnob(title, helperText, initialValue, listener);
        knobHolders[holderIndex] = knob;
        tile.add(knob, BorderLayout.CENTER);

        if (footer != null) {
            JPanel footerWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            footerWrap.setOpaque(false);
            footerWrap.add(footer);
            tile.add(footerWrap, BorderLayout.SOUTH);
        }

        return tile;
    }

    private JComponent createChannelMixerKnobTile(int channelIndex, JCheckBox[] channelMuteCheckBoxes,
            AudioKnob[] channelVolumeKnobs) {
        JCheckBox muteCheckBox = new JCheckBox(UiText.OptionsWindow.MUTE_CHECKBOX, Settings.IsChannelMuted(channelIndex));
        muteCheckBox.setOpaque(false);
        muteCheckBox.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 11f));
        muteCheckBox.setForeground(accentColour);
        muteCheckBox.addActionListener(event -> {
            Settings.SetChannelMuted(channelIndex, muteCheckBox.isSelected());
            Config.Save();
        });
        channelMuteCheckBoxes[channelIndex] = muteCheckBox;

        return createMixerKnobTile(
                channelName(channelIndex),
                channelName(channelIndex) + " level",
                Settings.GetChannelVolume(channelIndex),
                (newValue, adjusting) -> {
                    Settings.SetChannelVolume(channelIndex, newValue);
                    if (!adjusting) {
                        Config.Save();
                    }
                },
                channelVolumeKnobs,
                channelIndex,
                muteCheckBox);
    }

    private String channelName(int channelIndex) {
        return UiText.OptionsWindow.ChannelName(channelIndex);
    }

    private void updateAudioEnhancementDescription(JComboBox<AudioEnhancementPreset> presetSelector,
            JTextArea descriptionLabel) {
        Object selectedPreset = presetSelector.getSelectedItem();
        if (descriptionLabel == null) {
            return;
        }

        if (selectedPreset instanceof AudioEnhancementPreset enhancementPreset) {
            descriptionLabel.setText(enhancementPreset.Description());
        } else {
            descriptionLabel.setText(UiText.OptionsWindow.PRESET_DESCRIPTION_PLACEHOLDER);
        }
        descriptionLabel.setCaretPosition(0);
    }

    private void applyAudioEnhancementModel(DefaultListModel<AudioEnhancementSetting> enhancementChainModel) {
        applyAudioEnhancementModel(enhancementChainModel, true);
    }

    private void applyAudioEnhancementModel(DefaultListModel<AudioEnhancementSetting> enhancementChainModel,
            boolean persist) {
        List<AudioEnhancementSetting> chain = new ArrayList<>();
        for (int index = 0; index < enhancementChainModel.size(); index++) {
            chain.add(enhancementChainModel.getElementAt(index));
        }
        Settings.SetAudioEnhancementChain(chain);
        if (persist) {
            Config.Save();
        }
    }

    private void updateAudioEnhancementSetting(DefaultListModel<AudioEnhancementSetting> enhancementChainModel,
            int index, UnaryOperator<AudioEnhancementSetting> updater, boolean persist) {
        if (index < 0 || index >= enhancementChainModel.size()) {
            return;
        }

        AudioEnhancementSetting currentSetting = enhancementChainModel.getElementAt(index);
        AudioEnhancementSetting updatedSetting = updater.apply(currentSetting);
        if (updatedSetting == null || updatedSetting.equals(currentSetting)) {
            return;
        }

        enhancementChainModel.set(index, updatedSetting);
        applyAudioEnhancementModel(enhancementChainModel, persist);
    }

    private int audioEnhancementDropIndex(JPanel chainStack, int y) {
        int childCount = chainStack.getComponentCount();
        for (int index = 0; index < childCount; index++) {
            Rectangle bounds = chainStack.getComponent(index).getBounds();
            if (y < bounds.y + (bounds.height / 2)) {
                return index;
            }
        }
        return childCount;
    }

    private void installResponsiveAudioEffectCardLayout(JPanel card, JPanel header, JPanel actions, JLabel effectTitle,
            JTextArea effectDescription, JButton removeButton) {
        final boolean[] compact = { false };
        final boolean[] ultraCompact = { false };
        JLabel indexBadge = null;
        if (actions.getComponentCount() > 0 && actions.getComponent(0) instanceof JLabel label) {
            indexBadge = label;
        }
        JLabel badgeLabel = indexBadge;
        Runnable applyLayout = () -> {
            int width = card.getWidth();
            boolean nextCompact = width > 0 && width < 560;
            boolean nextUltraCompact = width > 0 && width < 380;
            if (compact[0] != nextCompact) {
                compact[0] = nextCompact;
                header.remove(actions);
                header.add(actions, nextCompact ? BorderLayout.SOUTH : BorderLayout.EAST);
            }
            ultraCompact[0] = nextUltraCompact;

            if (actions.getLayout() instanceof FlowLayout flowLayout) {
                flowLayout.setAlignment(nextCompact ? FlowLayout.LEFT : FlowLayout.RIGHT);
                flowLayout.setHgap(nextUltraCompact ? 4 : 6);
            }

            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                    BorderFactory.createEmptyBorder(
                            nextUltraCompact ? 8 : nextCompact ? 10 : 14,
                            nextUltraCompact ? 8 : nextCompact ? 10 : 14,
                            nextUltraCompact ? 8 : nextCompact ? 10 : 14,
                            nextUltraCompact ? 8 : nextCompact ? 10 : 14)));
            effectTitle.setFont(Styling.menuFont.deriveFont(Font.BOLD, nextUltraCompact ? 11f : nextCompact ? 12f : 13f));
            effectDescription.setFont(Styling.menuFont.deriveFont(Font.PLAIN, nextUltraCompact ? 10f : nextCompact ? 11f : 12f));
            removeButton.setPreferredSize(nextUltraCompact ? new Dimension(32, 24)
                    : nextCompact ? new Dimension(36, 28)
                    : new Dimension(42, 32));
            if (badgeLabel != null) {
                badgeLabel.setVisible(!nextUltraCompact);
            }
            card.revalidate();
            card.repaint();
        };

        card.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                applyLayout.run();
            }
        });
        SwingUtilities.invokeLater(applyLayout);
    }

    @FunctionalInterface
    private interface AudioKnobListener {
        void valueChanged(int newValue, boolean adjusting);
    }

    private static final class ResponsiveTileGridPanel extends JPanel {
        private final int minTileWidth;
        private final int maxColumns;
        private final int gap = 10;

        private ResponsiveTileGridPanel(int minTileWidth) {
            this(minTileWidth, Integer.MAX_VALUE);
        }

        private ResponsiveTileGridPanel(int minTileWidth, int maxColumns) {
            super(null);
            this.minTileWidth = Math.max(72, minTileWidth);
            this.maxColumns = Math.max(1, maxColumns);
            setOpaque(false);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    revalidate();
                    if (getParent() != null) {
                        getParent().revalidate();
                    }
                }
            });
        }

        @Override
        public void doLayout() {
            int componentCount = getComponentCount();
            if (componentCount == 0) {
                return;
            }

            int availableWidth = Math.max(0, getWidth());
            int columns = computeColumns(availableWidth);
            int cellWidth = Math.max(0, (availableWidth - (gap * (columns - 1))) / columns);

            int index = 0;
            int y = 0;
            while (index < componentCount) {
                int rowStart = index;
                int rowEnd = Math.min(componentCount, rowStart + columns);
                int rowHeight = 0;
                for (int rowIndex = rowStart; rowIndex < rowEnd; rowIndex++) {
                    int column = rowIndex - rowStart;
                    int x = column * (cellWidth + gap);
                    int width = column == columns - 1 ? Math.max(0, availableWidth - x) : cellWidth;
                    rowHeight = Math.max(rowHeight, preferredSizeForWidth(getComponent(rowIndex), width).height);
                }

                for (int rowIndex = rowStart; rowIndex < rowEnd; rowIndex++) {
                    int column = rowIndex - rowStart;
                    int x = column * (cellWidth + gap);
                    int width = column == columns - 1 ? Math.max(0, availableWidth - x) : cellWidth;
                    getComponent(rowIndex).setBounds(x, y, width, rowHeight);
                }

                y += rowHeight + gap;
                index = rowEnd;
            }
        }

        @Override
        public Dimension getPreferredSize() {
            int componentCount = getComponentCount();
            if (componentCount == 0) {
                return new Dimension(0, 0);
            }

            int availableWidth = preferredLayoutWidth();
            int columns = computeColumns(availableWidth);
            int rows = (int) Math.ceil(componentCount / (double) columns);
            int cellWidth = columns <= 0
                    ? 0
                    : Math.max(0, (availableWidth - (gap * (columns - 1))) / columns);
            int height = 0;
            for (int row = 0; row < rows; row++) {
                int rowStart = row * columns;
                int rowEnd = Math.min(componentCount, rowStart + columns);
                int rowHeight = 0;
                for (int index = rowStart; index < rowEnd; index++) {
                    int column = index - rowStart;
                    int x = column * (cellWidth + gap);
                    int width = column == columns - 1 ? Math.max(0, availableWidth - x) : cellWidth;
                    rowHeight = Math.max(rowHeight, preferredSizeForWidth(getComponent(index), width).height);
                }
                height += rowHeight;
                if (row < rows - 1) {
                    height += gap;
                }
            }
            return new Dimension(Math.max(minimumPreferredWidth(columns), availableWidth), height);
        }

        private int computeColumns(int availableWidth) {
            if (availableWidth <= 0) {
                return Math.min(Math.max(1, getComponentCount()), Math.min(maxColumns, 3));
            }
            int columns = Math.max(1, (availableWidth + gap) / (minTileWidth + gap));
            return Math.max(1, Math.min(getComponentCount(), Math.min(maxColumns, columns)));
        }

        @Override
        public Dimension getMinimumSize() {
            int columns = Math.min(Math.max(1, getComponentCount()), Math.min(maxColumns, 1));
            return new Dimension(minimumPreferredWidth(columns), super.getMinimumSize().height);
        }

        private int preferredLayoutWidth() {
            if (getWidth() > 0) {
                return getWidth();
            }
            if (getParent() != null && getParent().getWidth() > 0) {
                return getParent().getWidth();
            }
            int fallbackColumns = Math.min(Math.max(1, getComponentCount()), Math.min(maxColumns, 2));
            return minimumPreferredWidth(fallbackColumns);
        }

        private int minimumPreferredWidth(int columns) {
            if (columns <= 0) {
                return minTileWidth;
            }
            return (columns * minTileWidth) + ((columns - 1) * gap);
        }

        private Dimension preferredSizeForWidth(Component component, int width) {
            if (width <= 0) {
                return component.getPreferredSize();
            }

            Dimension originalSize = component.getSize();
            component.setSize(width, Short.MAX_VALUE);
            Dimension preferredSize = component.getPreferredSize();
            component.setSize(originalSize);
            return preferredSize;
        }
    }

    private static final class ResponsiveAudioKnobPanel extends JPanel {
        private final int knobCount;
        private static final int gap = 10;

        private ResponsiveAudioKnobPanel(int knobCount) {
            super(null);
            this.knobCount = Math.max(1, knobCount);
            setOpaque(false);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    revalidate();
                    if (getParent() != null) {
                        getParent().revalidate();
                    }
                }
            });
        }

        @Override
        public void doLayout() {
            int componentCount = getComponentCount();
            if (componentCount == 0) {
                return;
            }

            int availableWidth = Math.max(0, getWidth());
            int columns = computeColumns(availableWidth);
            int cellWidth = Math.max(0, (availableWidth - (gap * (columns - 1))) / columns);
            int rowHeight = computeRowHeight(cellWidth);

            for (int index = 0; index < componentCount; index++) {
                int row = index / columns;
                int column = index % columns;
                int x = column * (cellWidth + gap);
                int y = row * (rowHeight + gap);
                int width = column == columns - 1 ? Math.max(0, availableWidth - x) : cellWidth;
                Component component = getComponent(index);
                if (component instanceof AudioKnob audioKnob) {
                    audioKnob.SetCompactWidth(width, rowHeight);
                }
                component.setBounds(x, y, width, rowHeight);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            int componentCount = getComponentCount();
            if (componentCount == 0) {
                return new Dimension(0, 0);
            }

            int availableWidth = getParent() == null ? 0 : getParent().getWidth();
            int columns = computeColumns(availableWidth);
            int cellWidth = columns <= 0
                    ? 0
                    : Math.max(0, (availableWidth - (gap * (columns - 1))) / columns);
            int rows = (int) Math.ceil(componentCount / (double) columns);
            return new Dimension(0, (rows * computeRowHeight(cellWidth)) + ((rows - 1) * gap));
        }

        private int computeColumns(int availableWidth) {
            if (availableWidth <= 0) {
                return Math.min(knobCount, 3);
            }

            int minCellWidth;
            if (availableWidth < 300) {
                minCellWidth = 64;
            } else if (availableWidth < 420) {
                minCellWidth = 76;
            } else {
                minCellWidth = 96;
            }

            int fittingColumns = Math.max(1, (availableWidth + gap) / (minCellWidth + gap));
            return Math.max(1, Math.min(knobCount, fittingColumns));
        }

        private int computeRowHeight(int cellWidth) {
            if (cellWidth <= 72) {
                return 76;
            }
            if (cellWidth <= 92) {
                return 88;
            }
            if (cellWidth <= 116) {
                return 98;
            }
            return 112;
        }
    }

    private static final class AudioKnob extends JComponent {
        private final String label;
        private final AudioKnobListener listener;
        private int value;
        private boolean dragging;
        private int dragStartValue;
        private int dragStartY;
        private boolean compactVisuals;

        private AudioKnob(String label, String helperText, int value, AudioKnobListener listener) {
            this.label = label;
            this.listener = listener;
            this.value = clampValue(value);
            setOpaque(false);
            setFocusable(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(helperText);
            setPreferredSize(new Dimension(96, 108));
            setMinimumSize(new Dimension(56, 82));

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    dragging = true;
                    dragStartValue = AudioKnob.this.value;
                    dragStartY = event.getYOnScreen();
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (!dragging) {
                        return;
                    }
                    int delta = dragStartY - event.getYOnScreen();
                    setValueInternal(dragStartValue + delta, true);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (!dragging) {
                        return;
                    }
                    dragging = false;
                    int delta = dragStartY - event.getYOnScreen();
                    setValueInternal(dragStartValue + delta, false);
                }
            };
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
            addMouseWheelListener(event -> setValueInternal(
                    AudioKnob.this.value - (int) Math.round(event.getPreciseWheelRotation() * 4.0),
                    false));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D graphics2d = (Graphics2D) graphics.create();
            graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int tileX = 4;
            int tileY = 4;
            int tileWidth = width - 8;
            int tileHeight = height - 8;
            boolean ultraCompactVisuals = width < 84 || height < 84;
            float labelFontSize = ultraCompactVisuals ? 8f : compactVisuals ? 9f : 11f;
            float valueFontSize = ultraCompactVisuals ? 9f : compactVisuals ? 10f : 13f;

            graphics2d.setColor(Styling.sectionHighlightColour);
            graphics2d.fillRoundRect(tileX, tileY, tileWidth, tileHeight, 22, 22);
            graphics2d.setColor(Styling.sectionHighlightBorderColour);
            graphics2d.drawRoundRect(tileX, tileY, tileWidth, tileHeight, 22, 22);

            graphics2d.setFont(Styling.menuFont.deriveFont(Font.BOLD, labelFontSize));
            FontMetrics labelMetrics = graphics2d.getFontMetrics();
            graphics2d.setColor(Styling.mutedTextColour);
            int labelWidth = labelMetrics.stringWidth(label);
            graphics2d.drawString(label, (width - labelWidth) / 2, ultraCompactVisuals ? 14 : compactVisuals ? 16 : 20);

            int dialDiameter = Math.min(
                    ultraCompactVisuals ? 30 : compactVisuals ? 40 : 50,
                    Math.min(tileWidth - (ultraCompactVisuals ? 12 : compactVisuals ? 18 : 28),
                            tileHeight - (ultraCompactVisuals ? 32 : compactVisuals ? 42 : 56)));
            int dialX = (width - dialDiameter) / 2;
            int dialY = ultraCompactVisuals ? 16 : compactVisuals ? 20 : 28;
            int strokeInset = ultraCompactVisuals ? 3 : compactVisuals ? 4 : 5;

            graphics2d.setStroke(new BasicStroke(
                    ultraCompactVisuals ? 2.5f : compactVisuals ? 3f : 4f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            graphics2d.setColor(new Color(
                    Styling.accentColour.getRed(),
                    Styling.accentColour.getGreen(),
                    Styling.accentColour.getBlue(),
                    48));
            graphics2d.drawArc(dialX - strokeInset, dialY - strokeInset,
                    dialDiameter + (strokeInset * 2), dialDiameter + (strokeInset * 2),
                    225, -270);

            int sweep = (int) Math.round((value / 100.0) * 270.0);
            graphics2d.setColor(Styling.accentColour);
            graphics2d.drawArc(dialX - strokeInset, dialY - strokeInset,
                    dialDiameter + (strokeInset * 2), dialDiameter + (strokeInset * 2),
                    225, -sweep);

            graphics2d.setColor(Styling.surfaceColour);
            graphics2d.fill(new Ellipse2D.Double(dialX, dialY, dialDiameter, dialDiameter));
            graphics2d.setColor(Styling.cardTintBorderColour);
            graphics2d.draw(new Ellipse2D.Double(dialX, dialY, dialDiameter, dialDiameter));

            double angleRadians = Math.toRadians(225.0 - (270.0 * (value / 100.0)));
            double centreX = dialX + (dialDiameter / 2.0);
            double centreY = dialY + (dialDiameter / 2.0);
            double pointerLength = dialDiameter * 0.28;
            double pointerEndX = centreX + Math.cos(angleRadians) * pointerLength;
            double pointerEndY = centreY - Math.sin(angleRadians) * pointerLength;

            graphics2d.setStroke(new BasicStroke(ultraCompactVisuals ? 2f : compactVisuals ? 2.5f : 3f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            graphics2d.setColor(Styling.accentColour);
            graphics2d.draw(new Line2D.Double(centreX, centreY, pointerEndX, pointerEndY));
            graphics2d.fill(new Ellipse2D.Double(
                    centreX - (ultraCompactVisuals ? 2 : compactVisuals ? 2.5 : 3),
                    centreY - (ultraCompactVisuals ? 2 : compactVisuals ? 2.5 : 3),
                    ultraCompactVisuals ? 4 : compactVisuals ? 5 : 6,
                    ultraCompactVisuals ? 4 : compactVisuals ? 5 : 6));

            String valueText = value + "%";
            graphics2d.setFont(Styling.menuFont.deriveFont(Font.BOLD, valueFontSize));
            FontMetrics valueMetrics = graphics2d.getFontMetrics();
            graphics2d.drawString(valueText, (width - valueMetrics.stringWidth(valueText)) / 2,
                    height - (ultraCompactVisuals ? 10 : compactVisuals ? 12 : 18));

            graphics2d.dispose();
        }

        private void SetCompactWidth(int width, int height) {
            compactVisuals = width < 116 || height < 98;
        }

        private void SetValue(int newValue) {
            value = clampValue(newValue);
            repaint();
        }

        private void setValueInternal(int newValue, boolean adjusting) {
            int clampedValue = clampValue(newValue);
            if (clampedValue == value && adjusting) {
                return;
            }
            value = clampedValue;
            repaint();
            listener.valueChanged(value, adjusting);
        }

        private static int clampValue(int value) {
            return Math.max(0, Math.min(100, value));
        }
    }

    private JComponent createWindowPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 14));
        container.setOpaque(false);

        JPanel stack = createFillStackPanel();
        int stackRow = 0;

        JCheckBox fillWindowCheckBox = new JCheckBox(UiText.OptionsWindow.WINDOW_FILL_CHECKBOX, Settings.fillWindowOutput);
        fillWindowCheckBox.setOpaque(false);
        fillWindowCheckBox.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        fillWindowCheckBox.setForeground(accentColour);
        fillWindowCheckBox.addActionListener(event -> {
            Settings.fillWindowOutput = fillWindowCheckBox.isSelected();
            Config.Save();
            if (mainWindow != null) {
                mainWindow.ApplyWindowMode();
            }
        });

        JCheckBox integerScaleCheckBox = new JCheckBox(
                UiText.OptionsWindow.WINDOW_INTEGER_SCALE_CHECKBOX,
                Settings.integerScaleWindowOutput);
        integerScaleCheckBox.setOpaque(false);
        integerScaleCheckBox.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        integerScaleCheckBox.setForeground(accentColour);
        integerScaleCheckBox.addActionListener(event -> {
            Settings.integerScaleWindowOutput = integerScaleCheckBox.isSelected();
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshDisplayBorder();
            }
        });

        JCheckBox serialOutputCheckBox = new JCheckBox(UiText.OptionsWindow.SERIAL_OUTPUT_CHECKBOX,
                Settings.showSerialOutput);
        serialOutputCheckBox.setOpaque(false);
        serialOutputCheckBox.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        serialOutputCheckBox.setForeground(accentColour);
        serialOutputCheckBox.addActionListener(event -> {
            Settings.showSerialOutput = serialOutputCheckBox.isSelected();
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshWindowPanels();
            }
        });

        JPanel optionStack = createFillStackPanel();
        addFillStackRow(optionStack, createSimpleWindowOptionCard(fillWindowCheckBox), 0, 0);
        addFillStackRow(optionStack, createSimpleWindowOptionCard(integerScaleCheckBox), 1, 10);
        addFillStackRow(optionStack, createSimpleWindowOptionCard(serialOutputCheckBox), 2, 10);
        addFillStackRow(stack, optionStack, stackRow++, 0);

        JComboBox<GameArtDisplayMode> gameArtModeSelector = new JComboBox<>(GameArtDisplayMode.values());
        gameArtModeSelector.setSelectedItem(Settings.gameArtDisplayMode);
        gameArtModeSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        gameArtModeSelector.addActionListener(event -> {
            Object selectedItem = gameArtModeSelector.getSelectedItem();
            if (!(selectedItem instanceof GameArtDisplayMode selectedMode)) {
                return;
            }

            Settings.gameArtDisplayMode = selectedMode;
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshWindowPanels();
            }
        });
        addFillStackRow(stack, createSelectorWindowOptionCard(UiText.OptionsWindow.GAME_ART_MODE_LABEL, gameArtModeSelector),
                stackRow++, 10);

        JComboBox<DisplayBorderChoice> borderSelector = new JComboBox<>();
        borderSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));

        JLabel borderSourceValueLabel = createValueLabel("");
        JLabel borderPathValueLabel = createCompactReadoutLabel("");
        JLabel borderCutoutValueLabel = createValueLabel("");
        JLabel borderStatusValueLabel = createCompactReadoutLabel("");
        ImagePreviewSurface borderPreviewSurface = new ImagePreviewSurface(
                UiText.BorderManagerWindow.PREVIEW_UNAVAILABLE,
                280,
                210);
        borderPreviewSurface.setBorder(WindowUiSupport.createLineBorder(Styling.surfaceBorderColour));

        final boolean[] updatingBorderSelector = { false };

        Runnable refreshBorderDetails = () -> {
            DisplayBorderChoice selectedChoice = (DisplayBorderChoice) borderSelector.getSelectedItem();
            LoadedDisplayBorder border = DisplayBorderManager.Resolve(
                    selectedChoice == null ? Settings.displayBorderId : selectedChoice.id());

            DisplayBorderPreviewRenderer.PreviewImages previewImages = DisplayBorderPreviewRenderer.render(border);
            borderPreviewSurface.setImage(previewImages.previewImage());
            borderSourceValueLabel.setText(border.sourceLabel());
            setCompactReadout(borderPathValueLabel, border.sourcePathText().isBlank()
                    ? UiText.OptionsWindow.BORDER_PATH_BUILT_IN
                    : border.sourcePathText());
            borderCutoutValueLabel.setText(border.screenRect() == null
                    ? UiText.BorderManagerWindow.CUTOUT_NOT_AVAILABLE
                    : border.screenRect().width + " x " + border.screenRect().height);
            updateBorderStatusLabel(borderStatusValueLabel);
        };

        Runnable refreshBorderSelector = () -> {
            List<LoadedDisplayBorder> availableBorders = DisplayBorderManager.GetAvailableBorders();
            DefaultComboBoxModel<DisplayBorderChoice> model = new DefaultComboBoxModel<>();
            for (LoadedDisplayBorder border : availableBorders) {
                model.addElement(new DisplayBorderChoice(border.id(), border.displayName()));
            }

            String preferredBorderId = Settings.displayBorderId == null || Settings.displayBorderId.isBlank()
                    ? "none"
                    : Settings.displayBorderId;
            int selectedIndex = 0;
            for (int index = 0; index < model.getSize(); index++) {
                DisplayBorderChoice choice = model.getElementAt(index);
                if (preferredBorderId.equalsIgnoreCase(choice.id())) {
                    selectedIndex = index;
                    break;
                }
            }

            updatingBorderSelector[0] = true;
            try {
                borderSelector.setModel(model);
                if (model.getSize() > 0) {
                    borderSelector.setSelectedIndex(selectedIndex);
                }
            } finally {
                updatingBorderSelector[0] = false;
            }
            refreshBorderDetails.run();
        };

        borderSelector.addActionListener(event -> {
            if (updatingBorderSelector[0]) {
                return;
            }

            Object selectedItem = borderSelector.getSelectedItem();
            if (!(selectedItem instanceof DisplayBorderChoice selectedChoice)) {
                return;
            }

            Settings.displayBorderId = selectedChoice.id();
            Config.Save();
            refreshBorderDetails.run();
            if (mainWindow != null) {
                mainWindow.RefreshDisplayBorder();
            }
        });

        addFillStackRow(stack, createSelectorWindowOptionCard(UiText.OptionsWindow.DISPLAY_BORDER_LABEL, borderSelector),
                stackRow++, 10);

        JPanel borderDetailGrid = createResponsiveGroup(
                220,
                2,
                createFieldCard(UiText.OptionsWindow.BORDER_SOURCE_LABEL, borderSourceValueLabel),
                createFieldCard(UiText.OptionsWindow.BORDER_PATH_LABEL, borderPathValueLabel),
                createFieldCard(UiText.OptionsWindow.BORDER_CUTOUT_LABEL, borderCutoutValueLabel),
                createFieldCard(UiText.OptionsWindow.BORDER_STATUS_LABEL, borderStatusValueLabel));
        JPanel borderPreviewCard = createResponsiveGroup(
                300,
                2,
                createFieldCard(UiText.OptionsWindow.BORDER_PREVIEW_LABEL, borderPreviewSurface),
                borderDetailGrid);
        addFillStackRow(stack, createSimpleWindowOptionCard(borderPreviewCard), stackRow, 10);

        container.add(stack, BorderLayout.CENTER);

        JButton borderManagerButton = createSecondaryButton(UiText.OptionsWindow.OPEN_BORDER_MANAGER_BUTTON);
        borderManagerButton.addActionListener(event -> new DisplayBorderManagerWindow(() -> {
            DisplayBorderManager.Reload();
            refreshBorderSelector.run();
            if (mainWindow != null) {
                mainWindow.RefreshDisplayBorder();
            }
        }));

        JButton resetWindowButton = createSecondaryButton(UiText.OptionsWindow.RESET_WINDOW_BUTTON);
        resetWindowButton.addActionListener(event -> {
            Settings.fillWindowOutput = false;
            Settings.integerScaleWindowOutput = false;
            Settings.showSerialOutput = true;
            Settings.gameArtDisplayMode = GameArtDisplayMode.BOX_ART;
            Settings.displayBorderId = "none";
            fillWindowCheckBox.setSelected(Settings.fillWindowOutput);
            integerScaleCheckBox.setSelected(Settings.integerScaleWindowOutput);
            serialOutputCheckBox.setSelected(Settings.showSerialOutput);
            gameArtModeSelector.setSelectedItem(Settings.gameArtDisplayMode);
            refreshBorderSelector.run();
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshWindowPanels();
                mainWindow.RefreshDisplayBorder();
            }
        });

        JPanel actions = createResponsiveGroup(180, 2, borderManagerButton, resetWindowButton);
        container.add(actions, BorderLayout.SOUTH);
        refreshBorderSelector.run();
        return container;
    }

    private JComponent createDisplayShaderPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 14));
        container.setOpaque(false);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);

        JComboBox<DisplayShaderChoice> shaderSelector = new JComboBox<>();
        shaderSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));

        JTextArea descriptionArea = createWrappingTextArea("");
        descriptionArea.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        descriptionArea.setForeground(mutedText);

        JLabel sourceValueLabel = createValueLabel("");
        JLabel pathValueLabel = createCompactReadoutLabel("");
        JLabel statusValueLabel = createCompactReadoutLabel("");

        final boolean[] updatingShaderSelector = { false };

        Runnable refreshShaderDetails = () -> {
            DisplayShaderChoice selectedChoice = (DisplayShaderChoice) shaderSelector.getSelectedItem();
            LoadedDisplayShader shader = DisplayShaderManager.Resolve(
                    selectedChoice == null ? Settings.displayShaderId : selectedChoice.id());

            String description = shader.description();
            descriptionArea.setText(description == null || description.isBlank()
                    ? UiText.OptionsWindow.SHADER_DESCRIPTION_FALLBACK
                    : description);
            descriptionArea.setCaretPosition(0);
            sourceValueLabel.setText(shader.sourceLabel());
            setCompactReadout(pathValueLabel, shader.sourcePathText().isBlank()
                    ? UiText.OptionsWindow.SHADER_PATH_BUILT_IN
                    : shader.sourcePathText());
            updateShaderStatusLabel(statusValueLabel);
        };

        Runnable refreshShaderSelector = () -> {
            List<LoadedDisplayShader> availableShaders = DisplayShaderManager.GetAvailableShaders();
            DefaultComboBoxModel<DisplayShaderChoice> model = new DefaultComboBoxModel<>();
            for (LoadedDisplayShader shader : availableShaders) {
                model.addElement(new DisplayShaderChoice(shader.id(), shader.displayName()));
            }

            String preferredShaderId = Settings.displayShaderId == null || Settings.displayShaderId.isBlank()
                    ? "none"
                    : Settings.displayShaderId;
            int selectedIndex = 0;
            for (int index = 0; index < model.getSize(); index++) {
                DisplayShaderChoice choice = model.getElementAt(index);
                if (preferredShaderId.equalsIgnoreCase(choice.id())) {
                    selectedIndex = index;
                    break;
                }
            }

            updatingShaderSelector[0] = true;
            try {
                shaderSelector.setModel(model);
                if (model.getSize() > 0) {
                    shaderSelector.setSelectedIndex(selectedIndex);
                }
            } finally {
                updatingShaderSelector[0] = false;
            }
            refreshShaderDetails.run();
        };

        shaderSelector.addActionListener(event -> {
            if (updatingShaderSelector[0]) {
                return;
            }

            Object selectedItem = shaderSelector.getSelectedItem();
            if (!(selectedItem instanceof DisplayShaderChoice selectedChoice)) {
                return;
            }

            Settings.displayShaderId = selectedChoice.id();
            Config.Save();
            refreshShaderDetails.run();
            if (mainWindow != null) {
                mainWindow.RefreshDisplayShader();
            }
        });

        stack.add(createSelectorWindowOptionCard(UiText.OptionsWindow.DISPLAY_SHADER_LABEL, shaderSelector));
        stack.add(Box.createVerticalStrut(10));

        JPanel detailGrid = createResponsiveGroup(
                220,
                2,
                createFieldCard(UiText.OptionsWindow.SHADER_DESCRIPTION_LABEL, descriptionArea),
                createFieldCard(UiText.OptionsWindow.SHADER_SOURCE_LABEL, sourceValueLabel),
                createFieldCard(UiText.OptionsWindow.SHADER_PATH_LABEL, pathValueLabel),
                createFieldCard(UiText.OptionsWindow.SHADER_STATUS_LABEL, statusValueLabel));
        stack.add(createSimpleWindowOptionCard(detailGrid));

        container.add(stack, BorderLayout.CENTER);

        JButton reloadShadersButton = createSecondaryButton(UiText.OptionsWindow.RELOAD_SHADERS_BUTTON);
        reloadShadersButton.addActionListener(event -> {
            DisplayShaderManager.Reload();
            refreshShaderSelector.run();
            if (mainWindow != null) {
                mainWindow.RefreshDisplayShader();
            }
        });

        JButton openFolderButton = createSecondaryButton(UiText.OptionsWindow.OPEN_SHADER_FOLDER_BUTTON);
        openFolderButton.addActionListener(event -> openDirectory(DisplayShaderManager.ShaderDirectory()));

        JButton shaderEditorButton = createSecondaryButton(UiText.OptionsWindow.OPEN_SHADER_EDITOR_BUTTON);
        shaderEditorButton.addActionListener(event -> new ShaderPresetEditorWindow(() -> {
            DisplayShaderManager.Reload();
            refreshShaderSelector.run();
            if (mainWindow != null) {
                mainWindow.RefreshDisplayShader();
            }
        }));

        JButton resetShaderButton = createSecondaryButton(UiText.OptionsWindow.RESET_SHADER_BUTTON);
        resetShaderButton.addActionListener(event -> {
            Settings.displayShaderId = "none";
            Config.Save();
            refreshShaderSelector.run();
            if (mainWindow != null) {
                mainWindow.RefreshDisplayShader();
            }
        });

        JPanel actions = createResponsiveGroup(
                180,
                4,
                shaderEditorButton,
                reloadShadersButton,
                openFolderButton,
                resetShaderButton);
        container.add(actions, BorderLayout.SOUTH);

        refreshShaderSelector.run();
        return container;
    }

    private JComponent createLibraryPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        JComboBox<GameNameBracketDisplayMode> modeSelector = new JComboBox<>(GameNameBracketDisplayMode.values());
        modeSelector.setSelectedItem(Settings.gameNameBracketDisplayMode);
        modeSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        JTextArea helperLabel = createBodyTextArea(Settings.gameNameBracketDisplayMode.Description(), 12f);

        modeSelector.addActionListener(event -> {
            Object selectedItem = modeSelector.getSelectedItem();
            if (selectedItem instanceof GameNameBracketDisplayMode selectedMode) {
                helperLabel.setText(selectedMode.Description());
                Settings.gameNameBracketDisplayMode = selectedMode;
                Config.Save();
                if (mainWindow != null) {
                    mainWindow.RefreshLoadedRomDisplay();
                }
            }
        });

        JSpinner recentMenuLimitSpinner = new JSpinner(new SpinnerNumberModel(Settings.loadRecentMenuLimit, 1, 25, 1));
        recentMenuLimitSpinner.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        recentMenuLimitSpinner.addChangeListener(event -> {
            Object value = recentMenuLimitSpinner.getValue();
            if (!(value instanceof Number numberValue)) {
                return;
            }

            Settings.loadRecentMenuLimit = Math.max(1, Math.min(25, numberValue.intValue()));
            Config.Save();
        });
        JTextArea recentMenuHelperLabel = createBodyTextArea(UiText.OptionsWindow.RECENT_MENU_LIMIT_HELPER, 12f);

        JPanel modeCard = new JPanel(new BorderLayout(0, 10));
        modeCard.setOpaque(false);
        modeCard.add(createFieldCard(UiText.OptionsWindow.LIBRARY_MODE_LABEL, modeSelector), BorderLayout.NORTH);
        modeCard.add(helperLabel, BorderLayout.CENTER);
        panel.add(createSimpleWindowOptionCard(modeCard));
        panel.add(Box.createVerticalStrut(10));

        JPanel recentMenuCard = new JPanel(new BorderLayout(0, 10));
        recentMenuCard.setOpaque(false);
        recentMenuCard.add(createFieldCard(UiText.OptionsWindow.RECENT_MENU_LIMIT_LABEL, recentMenuLimitSpinner),
                BorderLayout.NORTH);
        recentMenuCard.add(recentMenuHelperLabel, BorderLayout.CENTER);
        panel.add(createSimpleWindowOptionCard(recentMenuCard));
        panel.add(Box.createVerticalStrut(10));

        JButton resetLibraryButton = createSecondaryButton(UiText.OptionsWindow.RESET_LIBRARY_BUTTON);
        resetLibraryButton.addActionListener(event -> {
            Settings.ResetLibrary();
            modeSelector.setSelectedItem(Settings.gameNameBracketDisplayMode);
            helperLabel.setText(Settings.gameNameBracketDisplayMode.Description());
            recentMenuLimitSpinner.setValue(Settings.loadRecentMenuLimit);
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshLoadedRomDisplay();
            }
        });
        panel.add(createResponsiveGroup(180, 1, resetLibraryButton));

        return panel;
    }

    private JComponent createSaveDataSection() {
        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setOpaque(false);

        int trackedGameCount = ManagedGameRegistry.GetKnownGames().size();

        JPanel titleCard = new JPanel(new BorderLayout());
        titleCard.setBackground(Styling.sectionHighlightColour);
        titleCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel titleText = createInfoTextBlock(
                UiText.OptionsWindow.SAVE_DATA_TITLE,
                UiText.OptionsWindow.SAVE_DATA_DESCRIPTION,
                16f);

        JLabel badgeLabel = createBadgeLabel(UiText.OptionsWindow.SaveManagerTrackedGamesBadge(trackedGameCount));

        titleCard.add(createResponsiveGroup(260, 2, titleText, badgeLabel), BorderLayout.CENTER);

        JPanel detailsCard = new JPanel(new BorderLayout(0, 14));
        detailsCard.setBackground(Styling.cardTintColour);
        detailsCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel detailsGrid = createResponsiveGroup(
                240,
                2,
                createSaveDataDetailCard(
                UiText.OptionsWindow.SAVE_MANAGER_LAUNCH_TITLE,
                UiText.OptionsWindow.SAVE_MANAGER_LAUNCH_HELPER,
                trackedGameCount == 0
                        ? UiText.OptionsWindow.SAVE_MANAGER_EMPTY_TITLE
                        : UiText.OptionsWindow.SaveManagerTrackedGamesBadge(trackedGameCount)),
                createSaveDataDetailCard(
                UiText.OptionsWindow.SAVE_DATA_MANAGED_PATH_TITLE,
                UiText.OptionsWindow.SAVE_MANAGER_SUBTITLE,
                SaveFileManager.SaveDirectoryPath().toString()));

        detailsCard.add(detailsGrid, BorderLayout.CENTER);

        JButton openManagerButton = createPrimaryButton(UiText.OptionsWindow.SAVE_MANAGER_OPEN_BUTTON);
        openManagerButton.addActionListener(event -> new SaveDataManagerWindow(mainWindow));

        container.add(titleCard, BorderLayout.NORTH);
        container.add(detailsCard, BorderLayout.CENTER);
        container.add(createResponsiveGroup(180, 1, openManagerButton), BorderLayout.SOUTH);
        return container;
    }

    private JPanel createSaveDataDetailCard(String titleText, String helperText, String valueText) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setOpaque(true);
        card.setBackground(new Color(255, 255, 255, 135));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JLabel title = new JLabel(titleText);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        title.setForeground(accentColour);
        card.add(title, BorderLayout.NORTH);

        JPanel valueStack = new JPanel();
        valueStack.setLayout(new BoxLayout(valueStack, BoxLayout.Y_AXIS));
        valueStack.setOpaque(false);

        JTextArea value = createWrappingTextArea(valueText);
        value.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        value.setForeground(accentColour);
        valueStack.add(value);

        if (helperText != null && !helperText.isBlank()) {
            valueStack.add(Box.createVerticalStrut(4));
            JTextArea helper = createBodyTextArea(helperText, 12f);
            valueStack.add(helper);
        }

        card.add(valueStack, BorderLayout.CENTER);
        return card;
    }

    private JComponent createEmulationPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 18));
        container.setOpaque(false);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);
        stack.add(createSaveDataSection());
        stack.add(Box.createVerticalStrut(12));
        stack.add(createDmgBootRomSection());
        stack.add(Box.createVerticalStrut(12));
        stack.add(createCgbBootRomSection());

        container.add(stack, BorderLayout.CENTER);
        return container;
    }

    private JComponent createDmgBootRomSection() {
        return createBootRomSection(new BootRomSectionSpec(
                UiText.OptionsWindow.USE_DMG_BOOT_ROM_CHECKBOX,
                Settings.useBootRom,
                BootRomManager::HasDmgBootRom,
                selected -> Settings.useBootRom = selected,
                UiText.OptionsWindow::DmgBootRomRequiredMessage,
                UiText.OptionsWindow.DMG_BOOT_SEQUENCE_TITLE,
                UiText.OptionsWindow.DMG_BOOT_SEQUENCE_HELPER,
                UiText.OptionsWindow.INSTALLED_BOOT_ROM_TITLE,
                UiText.OptionsWindow.INSTALLED_BOOT_ROM_HELPER,
                UiText.OptionsWindow.MANAGED_PATH_TITLE,
                BootRomManager::DmgBootRomPath,
                UiText.OptionsWindow.INSERT_BOOT_ROM_BUTTON,
                UiText.OptionsWindow.REMOVE_BOOT_ROM_BUTTON,
                BootRomManager::InstallDmgBootRom,
                BootRomManager::RemoveDmgBootRom,
                true));
    }

    private JComponent createCgbBootRomSection() {
        return createBootRomSection(new BootRomSectionSpec(
                UiText.OptionsWindow.USE_CGB_BOOT_ROM_CHECKBOX,
                Settings.useCgbBootRom,
                BootRomManager::HasCgbBootRom,
                selected -> Settings.useCgbBootRom = selected,
                UiText.OptionsWindow::CgbBootRomRequiredMessage,
                UiText.OptionsWindow.CGB_BOOT_SEQUENCE_TITLE,
                UiText.OptionsWindow.CGB_BOOT_SEQUENCE_HELPER,
                UiText.OptionsWindow.INSTALLED_CGB_BOOT_ROM_TITLE,
                UiText.OptionsWindow.INSTALLED_CGB_BOOT_ROM_HELPER,
                UiText.OptionsWindow.MANAGED_CGB_PATH_TITLE,
                BootRomManager::CgbBootRomPath,
                UiText.OptionsWindow.INSERT_CGB_BOOT_ROM_BUTTON,
                UiText.OptionsWindow.REMOVE_CGB_BOOT_ROM_BUTTON,
                BootRomManager::InstallCgbBootRom,
                BootRomManager::RemoveCgbBootRom,
                false));
    }

    private JComponent createBootRomSection(BootRomSectionSpec spec) {
        boolean bootRomInstalled = spec.installedSupplier().getAsBoolean();
        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setOpaque(false);

        JCheckBox useBootRomCheckBox = createBootRomCheckBox(spec, bootRomInstalled);
        JPanel bootCard = createBootRomIntroCard(spec, useBootRomCheckBox);
        JPanel detailsCard = createBootRomDetailsCard(spec, bootRomInstalled);
        JPanel buttonRow = createBootRomButtonRow(spec);

        if (spec.embedButtonsInDetailsCard()) {
            detailsCard.add(buttonRow, BorderLayout.SOUTH);
        } else {
            container.add(buttonRow, BorderLayout.SOUTH);
        }

        container.add(bootCard, BorderLayout.NORTH);
        container.add(detailsCard, BorderLayout.CENTER);
        return container;
    }

    private JCheckBox createBootRomCheckBox(BootRomSectionSpec spec, boolean bootRomInstalled) {
        JCheckBox checkBox = new JCheckBox(spec.checkboxText(), spec.settingEnabled());
        checkBox.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        checkBox.setForeground(accentColour);
        checkBox.setBackground(Styling.sectionHighlightColour);
        checkBox.addActionListener(event -> {
            if (checkBox.isSelected() && !spec.installedSupplier().getAsBoolean()) {
                checkBox.setSelected(false);
                JOptionPane.showMessageDialog(this, spec.requiredMessageSupplier().get(),
                        UiText.OptionsWindow.BOOT_ROM_REQUIRED_TITLE, JOptionPane.WARNING_MESSAGE);
                return;
            }

            spec.settingUpdater().accept(checkBox.isSelected());
            Config.Save();
        });
        checkBox.setEnabled(bootRomInstalled || !checkBox.isSelected());
        return checkBox;
    }

    private JPanel createBootRomIntroCard(BootRomSectionSpec spec, JCheckBox useBootRomCheckBox) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Styling.sectionHighlightColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel textPanel = createInfoTextBlock(spec.bootTitle(), spec.bootHelper(), 16f);

        JPanel toggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toggleWrap.setOpaque(false);
        toggleWrap.add(useBootRomCheckBox);

        card.add(createResponsiveGroup(260, 2, textPanel, toggleWrap), BorderLayout.CENTER);
        return card;
    }

    private JPanel createBootRomDetailsCard(BootRomSectionSpec spec, boolean bootRomInstalled) {
        JPanel detailsCard = new JPanel(new BorderLayout(0, 14));
        detailsCard.setBackground(Styling.cardTintColour);
        detailsCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        detailsCard.add(createBootRomStatusRow(spec.statusTitle(), spec.statusHelper(), bootRomInstalled), BorderLayout.NORTH);
        detailsCard.add(createManagedPathCard(spec.pathTitle(), spec.pathSupplier().get()), BorderLayout.CENTER);
        return detailsCard;
    }

    private JPanel createBootRomStatusRow(String title, String helper, boolean installed) {
        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);

        JPanel statusText = createInfoTextBlock(title, helper, 15f);
        statusRow.add(createResponsiveGroup(240, 2, statusText, createInstallStatusBadge(installed)),
                BorderLayout.CENTER);
        return statusRow;
    }

    private JLabel createInstallStatusBadge(boolean installed) {
        JLabel badge = createBadgeLabel(installed ? UiText.Common.INSTALLED : UiText.Common.MISSING);
        badge.setBackground(installed ? new Color(220, 239, 222) : new Color(244, 233, 217));
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(installed ? new Color(126, 170, 132) : new Color(185, 160, 108), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return badge;
    }

    private JPanel createManagedPathCard(String title, Path path) {
        JPanel pathCard = new JPanel(new BorderLayout(0, 6));
        pathCard.setOpaque(true);
        pathCard.setBackground(new Color(255, 255, 255, 135));
        pathCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JLabel pathTitle = new JLabel(title);
        pathTitle.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        pathTitle.setForeground(accentColour);

        JTextArea pathLabel = new JTextArea(path.toString());
        pathLabel.setEditable(false);
        pathLabel.setFocusable(false);
        pathLabel.setLineWrap(true);
        pathLabel.setWrapStyleWord(true);
        pathLabel.setOpaque(false);
        pathLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        pathLabel.setForeground(mutedText);
        pathLabel.setBorder(BorderFactory.createEmptyBorder());

        pathCard.add(pathTitle, BorderLayout.NORTH);
        pathCard.add(pathLabel, BorderLayout.CENTER);
        return pathCard;
    }

    private JPanel createBootRomButtonRow(BootRomSectionSpec spec) {
        JButton insertButton = createPrimaryButton(spec.insertButtonText());
        insertButton.addActionListener(event -> installBootRom(spec));

        JButton removeButton = createSecondaryButton(spec.removeButtonText());
        removeButton.addActionListener(event -> removeBootRom(spec));
        return createResponsiveGroup(180, 2, insertButton, removeButton);
    }

    private void installBootRom(BootRomSectionSpec spec) {
        File bootRomFile = PromptForBootRomFile();
        if (bootRomFile == null) {
            return;
        }

        try {
            spec.installer().accept(bootRomFile.toPath());
            spec.settingUpdater().accept(true);
            Config.Save();
            reopenWithCurrentTab();
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    UiText.OptionsWindow.BOOT_ROM_INSTALL_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeBootRom(BootRomSectionSpec spec) {
        try {
            spec.remover().run();
            spec.settingUpdater().accept(false);
            Config.Save();
            reopenWithCurrentTab();
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    UiText.OptionsWindow.BOOT_ROM_REMOVE_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(panelBackground);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));

        JButton closeButton = createPrimaryButton(UiText.OptionsWindow.CLOSE_BUTTON);
        closeButton.addActionListener(event -> dispose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        actions.add(closeButton);
        footer.add(actions, BorderLayout.EAST);
        return footer;
    }

    private Border createCardBorder() {
        return WindowUiSupport.createCardBorder(cardBorder, true, 20);
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        label.setForeground(accentColour);
        return label;
    }

    private boolean shouldRenderUiText(String text) {
        return text != null && !text.isBlank();
    }

    private JButton createPrimaryButton(String text) {
        return WindowUiSupport.createPrimaryButton(text, accentColour);
    }

    private JButton createSecondaryButton(String text) {
        return WindowUiSupport.createSecondaryButton(text, accentColour, cardBorder);
    }

    private JLabel createBadgeLabel(String text) {
        return WindowUiSupport.createBadgeLabel(text, accentColour);
    }

    private void chooseColor(int index, String label) {
        Color initialColour = colorPreviews[index] != null
                ? colorPreviews[index].getBackground()
                : (paletteStripPreviews[index] != null ? paletteStripPreviews[index].getBackground()
                        : Settings.CurrentPalette()[index].ToColour());
        Color selectedColor = JColorChooser.showDialog(this, UiText.OptionsWindow.ChooseColorTitle(label),
                initialColour);
        if (selectedColor == null) {
            return;
        }

        updateSettingsColor(index, selectedColor);
        refreshPaletteDetails();
        Config.Save();
    }

    private void chooseGbcPaletteColor(int paletteIndex, int colourIndex) {
        int flatIndex = (paletteIndex * 4) + colourIndex;
        Color initialColour = gbcColorPreviews[flatIndex] == null
                ? Color.WHITE
                : gbcColorPreviews[flatIndex].getBackground();
        Color selectedColor = JColorChooser.showDialog(this, UiText.OptionsWindow.GbcColorChooserTitle(),
                initialColour);
        if (selectedColor == null) {
            return;
        }

        Settings.SetGbcPaletteColour(paletteIndex, colourIndex,
                String.format("#%02X%02X%02X", selectedColor.getRed(), selectedColor.getGreen(),
                        selectedColor.getBlue()));
        refreshPaletteDetails();
        Config.Save();
    }

    private void chooseThemeColor(AppThemeColorRole role) {
        Color selectedColor = JColorChooser.showDialog(this, UiText.OptionsWindow.ChooseColorTitle(role.Label()),
                Settings.CurrentAppTheme().CoreColour(role));
        if (selectedColor == null) {
            return;
        }

        Settings.SetAppThemeColour(role,
                String.format("#%02X%02X%02X", selectedColor.getRed(), selectedColor.getGreen(),
                        selectedColor.getBlue()));
        Config.Save();
        if (mainWindow != null) {
            mainWindow.RefreshTheme();
        }
        reopenWithCurrentTab();
    }

    private void captureAllBindings() {
        for (EmulatorButton button : controlButtons()) {
            if (!captureBinding(button)) {
                break;
            }
        }
    }

    private void captureAllControllerBindings() {
        if (controllerInputService.GetInitialisationError() != null) {
            JOptionPane.showMessageDialog(this, controllerInputService.GetInitialisationError(),
                    UiText.OptionsWindow.CONTROLLER_WINDOW_TITLE, JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (controllerInputService.GetActiveController().isEmpty()) {
            JOptionPane.showMessageDialog(this, UiText.OptionsWindow.CONTROLLER_NO_ACTIVE_DEVICE_MESSAGE,
                    UiText.OptionsWindow.CONTROLLER_WINDOW_TITLE, JOptionPane.WARNING_MESSAGE);
            return;
        }

        for (EmulatorButton button : controlButtons()) {
            if (!captureControllerBinding(button)) {
                break;
            }
        }
    }

    private boolean captureBinding(EmulatorButton button) {
        JDialog dialog = new JDialog(this, UiText.OptionsWindow.RebindDialogTitle(formatControlButtonName(button)), true);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(panelBackground);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBackground(cardBackground);
        content.setBorder(createCardBorder());

        JLabel title = new JLabel(UiText.OptionsWindow.RebindDialogPrompt(formatControlButtonName(button)),
                SwingConstants.CENTER);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 18f));
        title.setForeground(accentColour);

        JLabel helper = new JLabel(UiText.OptionsWindow.PRESS_ESCAPE_TO_CANCEL, SwingConstants.CENTER);
        helper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        helper.setForeground(mutedText);

        content.add(title, BorderLayout.NORTH);
        content.add(helper, BorderLayout.CENTER);
        dialog.add(content, BorderLayout.CENTER);
        dialog.setSize(420, 180);
        dialog.setLocationRelativeTo(this);

        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        final boolean[] removed = { false };
        final boolean[] captured = { false };

        KeyEventDispatcher dispatcher = event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED) {
                return false;
            }

            if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                dialog.dispose();
                return true;
            }

            Settings.inputBindings.SetKeyCode(button, event.getKeyCode());
            captured[0] = true;
            refreshBindingButtons();
            Config.Save();
            dialog.dispose();
            return true;
        };

        Runnable removeDispatcher = () -> {
            if (!removed[0]) {
                focusManager.removeKeyEventDispatcher(dispatcher);
                removed[0] = true;
            }
        };

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                removeDispatcher.run();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                removeDispatcher.run();
            }
        });

        focusManager.addKeyEventDispatcher(dispatcher);
        SwingUtilities.invokeLater(dialog::requestFocusInWindow);
        dialog.setVisible(true);
        removeDispatcher.run();
        return captured[0];
    }

    private void captureShortcut(AppShortcut shortcut) {
        JDialog dialog = new JDialog(this, UiText.OptionsWindow.ShortcutDialogTitle(shortcut.Label()), true);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(panelBackground);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBackground(cardBackground);
        content.setBorder(createCardBorder());

        JLabel title = new JLabel(UiText.OptionsWindow.ShortcutDialogPrompt(shortcut.Label()), SwingConstants.CENTER);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 18f));
        title.setForeground(accentColour);

        JLabel helper = new JLabel(UiText.OptionsWindow.SHORTCUT_CAPTURE_HELPER, SwingConstants.CENTER);
        helper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        helper.setForeground(mutedText);

        content.add(title, BorderLayout.NORTH);
        content.add(helper, BorderLayout.CENTER);
        dialog.add(content, BorderLayout.CENTER);
        dialog.setSize(480, 180);
        dialog.setLocationRelativeTo(this);

        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        final boolean[] removed = { false };

        KeyEventDispatcher dispatcher = event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED) {
                return false;
            }

            if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                dialog.dispose();
                return true;
            }

            if (AppShortcutBindings.IsModifierKey(event.getKeyCode())) {
                return true;
            }

            KeyStroke keyStroke = KeyStroke.getKeyStroke(event.getKeyCode(), event.getModifiersEx());
            Settings.appShortcutBindings.SetKeyStroke(shortcut, keyStroke);
            refreshShortcutButtons();
            if (mainWindow != null) {
                mainWindow.RefreshAppShortcuts();
            }
            Config.Save();
            dialog.dispose();
            return true;
        };

        Runnable removeDispatcher = () -> {
            if (!removed[0]) {
                focusManager.removeKeyEventDispatcher(dispatcher);
                removed[0] = true;
            }
        };

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                removeDispatcher.run();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                removeDispatcher.run();
            }
        });

        focusManager.addKeyEventDispatcher(dispatcher);
        SwingUtilities.invokeLater(dialog::requestFocusInWindow);
        dialog.setVisible(true);
        removeDispatcher.run();
    }

    private void captureControllerShortcut(AppShortcut shortcut) {
        captureControllerInput(
                UiText.OptionsWindow.ShortcutControllerDialogTitle(shortcut.Label()),
                UiText.OptionsWindow.ShortcutControllerDialogPrompt(shortcut.Label()),
                binding -> {
                    Settings.appShortcutControllerBindings.SetBinding(shortcut, binding);
                    refreshShortcutButtons();
                    Config.Save();
                });
    }

    private boolean captureControllerBinding(EmulatorButton button) {
        return captureControllerInput(
                UiText.OptionsWindow.ControllerRebindDialogTitle(formatControlButtonName(button)),
                UiText.OptionsWindow.ControllerRebindDialogPrompt(formatControlButtonName(button)),
                binding -> {
                    Settings.controllerBindings.SetBinding(button, binding);
                    refreshControllerBindingButtons();
                    refreshControllerStatus();
                    Config.Save();
                });
    }

    private boolean captureControllerInput(String dialogTitle, String dialogPrompt, Consumer<ControllerBinding> onCapture) {
        if (controllerInputService.GetInitialisationError() != null) {
            JOptionPane.showMessageDialog(this, controllerInputService.GetInitialisationError(),
                    UiText.OptionsWindow.CONTROLLER_WINDOW_TITLE, JOptionPane.WARNING_MESSAGE);
            return false;
        }

        if (controllerInputService.GetActiveController().isEmpty()) {
            JOptionPane.showMessageDialog(this, UiText.OptionsWindow.CONTROLLER_NO_ACTIVE_DEVICE_MESSAGE,
                    UiText.OptionsWindow.CONTROLLER_WINDOW_TITLE, JOptionPane.WARNING_MESSAGE);
            return false;
        }

        JDialog dialog = new JDialog(this, dialogTitle, true);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(panelBackground);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBackground(cardBackground);
        content.setBorder(createCardBorder());

        JLabel title = new JLabel(dialogPrompt, SwingConstants.CENTER);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 18f));
        title.setForeground(accentColour);

        JLabel helper = new JLabel(UiText.OptionsWindow.CONTROLLER_CAPTURE_HELPER, SwingConstants.CENTER);
        helper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        helper.setForeground(mutedText);

        content.add(title, BorderLayout.NORTH);
        content.add(helper, BorderLayout.CENTER);
        dialog.add(content, BorderLayout.CENTER);
        dialog.setSize(520, 180);
        dialog.setLocationRelativeTo(this);

        Set<ControllerBinding> blockedInputs = new HashSet<>(controllerInputService.PollActiveInputs());
        final boolean[] captured = { false };
        Timer captureTimer = new Timer(25, event -> {
            List<ControllerBinding> activeInputs = controllerInputService.PollActiveInputs();
            blockedInputs.retainAll(activeInputs);

            ControllerBinding candidate = null;
            for (ControllerBinding activeInput : activeInputs) {
                if (!blockedInputs.contains(activeInput)) {
                    candidate = activeInput;
                    break;
                }
            }

            if (candidate == null) {
                return;
            }

            onCapture.accept(candidate);
            captured[0] = true;
            dialog.dispose();
        });

        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                captureTimer.stop();
            }
        });

        captureTimer.start();
        dialog.setVisible(true);
        captureTimer.stop();
        return captured[0];
    }

    private void refreshControllerStatus() {
        if (controllerSelector == null || controllerEnabledCheckBox == null || controllerStatusBadgeLabel == null
                || controllerStatusHelperLabel == null || controllerActiveValueLabel == null
                || controllerLiveInputsArea == null || controllerMappedButtonsArea == null) {
            return;
        }
        if (!isControlsTabSelected()) {
            return;
        }

        List<ControllerInputService.ControllerDevice> devices = controllerInputService.GetAvailableControllers();
        syncControllerSelectorModel(devices);

        updatingControllerUi = true;
        try {
            if (controllerEnabledCheckBox.isSelected() != Settings.controllerInputEnabled) {
                controllerEnabledCheckBox.setSelected(Settings.controllerInputEnabled);
            }
            if (controllerDeadzoneSlider != null && !controllerDeadzoneSlider.getValueIsAdjusting()
                    && controllerDeadzoneSlider.getValue() != Settings.controllerDeadzonePercent) {
                controllerDeadzoneSlider.setValue(Settings.controllerDeadzonePercent);
            }
            if (controllerDeadzoneValueLabel != null) {
                controllerDeadzoneValueLabel.setText(UiText.OptionsWindow.PercentValue(Settings.controllerDeadzonePercent));
            }
        } finally {
            updatingControllerUi = false;
        }

        String error = controllerInputService.GetInitialisationError();
        if (error != null && !error.isBlank()) {
            controllerStatusBadgeLabel.setText(UiText.OptionsWindow.CONTROLLER_STATUS_UNAVAILABLE);
            controllerStatusHelperLabel.setText(error);
            controllerActiveValueLabel.setText(UiText.OptionsWindow.CONTROLLER_NONE_CONNECTED);
            setCompactReadout(controllerLiveInputsArea, UiText.OptionsWindow.CONTROLLER_LIVE_NONE);
            setCompactReadout(controllerMappedButtonsArea, UiText.OptionsWindow.CONTROLLER_MAPPED_NONE);
            return;
        }

        Optional<ControllerInputService.ControllerDevice> activeController = controllerInputService.GetActiveController();
        if (activeController.isPresent()) {
            controllerStatusBadgeLabel.setText(Settings.controllerInputEnabled
                    ? UiText.OptionsWindow.CONTROLLER_STATUS_CONNECTED
                    : UiText.OptionsWindow.CONTROLLER_STATUS_DISABLED);
            controllerStatusHelperLabel.setText(UiText.OptionsWindow.CONTROLLER_STATUS_HELPER);
            controllerActiveValueLabel.setText(activeController.get().displayName());
        } else {
            controllerStatusBadgeLabel.setText(UiText.OptionsWindow.CONTROLLER_STATUS_DISCONNECTED);
            controllerStatusHelperLabel.setText(UiText.OptionsWindow.CONTROLLER_NO_ACTIVE_DEVICE_MESSAGE);
            controllerActiveValueLabel.setText(UiText.OptionsWindow.CONTROLLER_NONE_CONNECTED);
        }

        List<ControllerBinding> activeInputs = activeController.isPresent()
                ? controllerInputService.PollActiveInputs()
                : List.of();
        setCompactReadout(controllerLiveInputsArea, activeInputs.isEmpty()
                ? UiText.OptionsWindow.CONTROLLER_LIVE_NONE
                : JoinControllerBindings(activeInputs));

        if (!Settings.controllerInputEnabled) {
            setCompactReadout(controllerMappedButtonsArea, UiText.OptionsWindow.CONTROLLER_MAPPED_DISABLED);
            return;
        }

        Map<String, String> mappedPressedButtons = new java.util.LinkedHashMap<>();
        for (EmulatorButton button : controllerInputService.PollBoundButtons()) {
            mappedPressedButtons.put(button.id(), formatControlButtonName(button));
        }
        setCompactReadout(controllerMappedButtonsArea, mappedPressedButtons.isEmpty()
                ? UiText.OptionsWindow.CONTROLLER_MAPPED_NONE
                : String.join(", ", mappedPressedButtons.values()));
    }

    private void syncControllerSelectorModel(List<ControllerInputService.ControllerDevice> devices) {
        if (controllerSelector == null) {
            return;
        }

        List<String> deviceEntries = new ArrayList<>();
        for (ControllerInputService.ControllerDevice device : devices) {
            deviceEntries.add(device.id() + "|" + device.displayName());
        }

        boolean popupVisible = controllerSelector.isPopupVisible();
        boolean deviceListChanged = !deviceEntries.equals(lastControllerDeviceEntries)
                || controllerSelector.getModel().getSize() == 0;
        if (deviceListChanged && !popupVisible) {
            DefaultComboBoxModel<ControllerChoice> model = new DefaultComboBoxModel<>();
            model.addElement(new ControllerChoice("", UiText.OptionsWindow.CONTROLLER_AUTO_SELECT));
            for (ControllerInputService.ControllerDevice device : devices) {
                model.addElement(new ControllerChoice(device.id(), device.displayName()));
            }

            updatingControllerUi = true;
            try {
                controllerSelector.setModel(model);
                selectPreferredControllerChoice(model);
            } finally {
                updatingControllerUi = false;
            }
            lastControllerDeviceEntries = List.copyOf(deviceEntries);
            return;
        }

        if (!popupVisible && controllerSelector.getModel().getSize() > 0) {
            updatingControllerUi = true;
            try {
                selectPreferredControllerChoice((DefaultComboBoxModel<ControllerChoice>) controllerSelector.getModel());
            } finally {
                updatingControllerUi = false;
            }
        }
    }

    private void selectPreferredControllerChoice(DefaultComboBoxModel<ControllerChoice> model) {
        String preferredId = Settings.preferredControllerId == null ? "" : Settings.preferredControllerId;
        for (int index = 0; index < model.getSize(); index++) {
            ControllerChoice choice = model.getElementAt(index);
            if (preferredId.equals(choice.id())) {
                if (controllerSelector.getSelectedIndex() != index) {
                    controllerSelector.setSelectedIndex(index);
                }
                return;
            }
        }
        if (controllerSelector.getSelectedIndex() != 0) {
            controllerSelector.setSelectedIndex(0);
        }
    }

    private String JoinControllerBindings(List<ControllerBinding> bindings) {
        List<String> labels = new ArrayList<>();
        for (ControllerBinding binding : bindings) {
            labels.add(binding.ToDisplayText());
        }
        return labels.isEmpty() ? UiText.OptionsWindow.CONTROLLER_LIVE_NONE : String.join(", ", labels);
    }

    private void refreshPaletteDetails() {
        GBColor[] palette = Settings.CurrentPalette();
        for (int i = 0; i < colorPreviews.length; i++) {
            if (paletteStripPreviews[i] != null) {
                paletteStripPreviews[i].setBackground(palette[i].ToColour());
            }
            if (colorPreviews[i] != null) {
                colorPreviews[i].setBackground(palette[i].ToColour());
            }
            if (colorHexLabels[i] != null) {
                colorHexLabels[i].setText(palette[i].ToHex().toUpperCase());
            }
        }

        refreshGbcPaletteDetails(Settings.CurrentGbcBackgroundPalette(), 0);
        refreshGbcPaletteDetails(Settings.CurrentGbcSpritePalette0(), 1);
        refreshGbcPaletteDetails(Settings.CurrentGbcSpritePalette1(), 2);

        AppTheme theme = Settings.CurrentAppTheme();
        for (AppThemeColorRole role : AppThemeColorRole.values()) {
            int index = role.ordinal();
            if (themeStripPreviews[index] != null) {
                themeStripPreviews[index].setBackground(theme.CoreColour(role));
            }
        }
    }

    private void refreshGbcPaletteDetails(GBColor[] palette, int paletteIndex) {
        for (int colourIndex = 0; colourIndex < palette.length; colourIndex++) {
            int flatIndex = (paletteIndex * 4) + colourIndex;
            if (gbcColorPreviews[flatIndex] != null) {
                gbcColorPreviews[flatIndex].setBackground(palette[colourIndex].ToColour());
            }
            if (gbcColorHexLabels[flatIndex] != null) {
                gbcColorHexLabels[flatIndex].setText(palette[colourIndex].ToHex().toUpperCase());
            }
        }
    }

    private void refreshBindingButtons() {
        for (EmulatorButton button : controlButtons()) {
            JButton bindingButton = bindingButtons.get(button);
            if (bindingButton != null) {
                bindingButton.setText(Settings.inputBindings.GetKeyText(button));
            }
        }
    }

    private void refreshControllerBindingButtons() {
        for (EmulatorButton button : controlButtons()) {
            JButton bindingButton = controllerBindingButtons.get(button);
            if (bindingButton != null) {
                bindingButton.setText(Settings.controllerBindings.GetBindingText(button));
            }
        }
    }

    private void refreshShortcutButtons() {
        for (AppShortcut shortcut : AppShortcut.values()) {
            JButton shortcutButton = shortcutButtons.get(shortcut);
            if (shortcutButton != null) {
                shortcutButton.setText(Settings.appShortcutBindings.GetKeyText(shortcut));
            }
            JButton controllerShortcutButton = controllerShortcutButtons.get(shortcut);
            if (controllerShortcutButton != null) {
                controllerShortcutButton.setText(Settings.appShortcutControllerBindings.GetBindingText(shortcut));
            }
        }
    }

    private List<? extends EmulatorButton> controlButtons() {
        return backendProfile().controlButtons();
    }

    private String formatControlButtonName(EmulatorButton button) {
        return backendProfile().controlButtonLabel(button);
    }

    private String controlButtonHelper(EmulatorButton button) {
        return backendProfile().controlButtonHelper(button);
    }

    private String controlBadgeText() {
        return backendProfile().displayName();
    }

    private EmulatorProfile backendProfile() {
        return mainWindow == null ? GBBackends.Current().Profile() : mainWindow.GetBackendProfile();
    }

    private JComponent wrapControllerDeadzoneControls() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.add(controllerDeadzoneSlider, BorderLayout.CENTER);
        panel.add(controllerDeadzoneValueLabel, BorderLayout.EAST);
        return panel;
    }

    private JComponent wrapControllerStatusControls() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.add(controllerStatusBadgeLabel, BorderLayout.NORTH);
        panel.add(controllerStatusHelperLabel, BorderLayout.CENTER);
        return panel;
    }

    private JLabel createCompactReadoutLabel(String text) {
        JLabel label = new JLabel();
        label.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        label.setForeground(accentColour);
        label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        setCompactReadout(label, text);
        return label;
    }

    private JLabel createValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        label.setForeground(accentColour);
        return label;
    }

    private JComponent createFieldCard(String labelText, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        label.setForeground(accentColour);

        panel.add(label, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private JComponent createSimpleWindowOptionCard(JComponent component) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        card.add(component, BorderLayout.CENTER);
        return card;
    }

    private JComponent createSelectorWindowOptionCard(String labelText, JComponent selector) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setOpaque(true);
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        JLabel label = createFieldLabel(labelText);
        card.add(label, BorderLayout.NORTH);
        card.add(selector, BorderLayout.CENTER);
        return card;
    }

    private void updateShaderStatusLabel(JLabel label) {
        List<LoadedDisplayShader> loadedShaders = DisplayShaderManager.GetAvailableShaders();
        List<String> shaderErrors = DisplayShaderManager.GetLoadErrors();
        String statusText = shaderErrors.isEmpty()
                ? UiText.OptionsWindow.ShaderStatusSummary(loadedShaders.size(), 0)
                : UiText.OptionsWindow.ShaderStatusSummary(loadedShaders.size(), shaderErrors.size());
        setCompactReadout(label, statusText);
        if (shaderErrors.isEmpty()) {
            label.setToolTipText(UiText.OptionsWindow.SHADER_STATUS_OK_HELPER);
            return;
        }

        List<String> escapedErrors = new ArrayList<>();
        for (String shaderError : shaderErrors) {
            escapedErrors.add(escapeHtml(shaderError));
        }
        label.setToolTipText("<html>" + String.join("<br>", escapedErrors) + "</html>");
    }

    private void updateBorderStatusLabel(JLabel label) {
        List<LoadedDisplayBorder> loadedBorders = DisplayBorderManager.GetAvailableBorders();
        List<String> borderErrors = DisplayBorderManager.GetLoadErrors();
        String statusText = borderErrors.isEmpty()
                ? UiText.OptionsWindow.BorderStatusSummary(loadedBorders.size(), 0)
                : UiText.OptionsWindow.BorderStatusSummary(loadedBorders.size(), borderErrors.size());
        setCompactReadout(label, statusText);
        if (borderErrors.isEmpty()) {
            label.setToolTipText(UiText.OptionsWindow.BORDER_STATUS_OK_HELPER);
            return;
        }

        List<String> escapedErrors = new ArrayList<>();
        for (String borderError : borderErrors) {
            escapedErrors.add(escapeHtml(borderError));
        }
        label.setToolTipText("<html>" + String.join("<br>", escapedErrors) + "</html>");
    }

    private void setCompactReadout(JLabel label, String text) {
        if (label == null) {
            return;
        }

        String fullText = text == null || text.isBlank() ? UiText.OptionsWindow.CONTROLLER_LIVE_NONE : text;
        String compactText = fullText.length() <= 58 ? fullText : fullText.substring(0, 55) + "...";
        label.setText(compactText);
        label.setToolTipText(compactText.equals(fullText) ? null : fullText);
    }

    private boolean isControlsTabSelected() {
        if (tabs == null) {
            return false;
        }
        int controlsTabIndex = tabs.indexOfTab(UiText.OptionsWindow.TAB_CONTROLS);
        return controlsTabIndex < 0 || tabs.getSelectedIndex() == controlsTabIndex;
    }

    private void updateSettingsColor(int index, Color color) {
        String hex = String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
        Settings.SetPaletteColour(index, hex);
    }

    private void reopenWithCurrentTab() {
        int selectedTab = tabs != null ? tabs.getSelectedIndex() : 0;
        setVisible(false);
        dispose();
        SwingUtilities.invokeLater(() -> new OptionsWindow(mainWindow, selectedTab));
    }

    private String escapeHtml(String value) {
        return WindowUiSupport.escapeHtml(value);
    }

    private File PromptForBootRomFile() {
        FileDialog fileDialog = new FileDialog(this, UiText.OptionsWindow.BOOT_ROM_FILE_DIALOG_TITLE, FileDialog.LOAD);
        fileDialog.setAlwaysOnTop(true);
        fileDialog.setFilenameFilter((directory, name) -> {
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".bin") || lowerName.endsWith(".rom") || lowerName.endsWith(".gb");
        });
        fileDialog.setVisible(true);
        return fileDialog.getFiles().length == 0 ? null : fileDialog.getFiles()[0];
    }

    private void openDirectory(Path path) {
        try {
            Files.createDirectories(path);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(path.toFile());
                return;
            }
            JOptionPane.showMessageDialog(this,
                    UiText.OptionsWindow.ShaderFolderPathMessage(path.toString()),
                    UiText.OptionsWindow.OPEN_SHADER_FOLDER_BUTTON,
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    UiText.OptionsWindow.ShaderFolderOpenFailedMessage(path.toString()),
                    UiText.Common.WARNING_TITLE,
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    @FunctionalInterface
    private interface BootRomInstaller {
        void accept(Path path) throws IOException;
    }

    @FunctionalInterface
    private interface BootRomRemover {
        void run() throws IOException;
    }

    private record BootRomSectionSpec(String checkboxText, boolean settingEnabled, BooleanSupplier installedSupplier,
                                      Consumer<Boolean> settingUpdater, Supplier<String> requiredMessageSupplier,
                                      String bootTitle, String bootHelper, String statusTitle, String statusHelper,
                                      String pathTitle, Supplier<Path> pathSupplier, String insertButtonText,
                                      String removeButtonText, BootRomInstaller installer, BootRomRemover remover,
                                      boolean embedButtonsInDetailsCard) {
    }

    private static final class VerticalScrollPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(visibleRect.height - 32, 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private record ControllerChoice(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record DisplayShaderChoice(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record DisplayBorderChoice(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}

