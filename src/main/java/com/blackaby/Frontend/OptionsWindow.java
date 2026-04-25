package com.blackaby.Frontend;

import com.blackaby.Backend.Platform.BackendRegistry;
import com.blackaby.Backend.GB.Graphics.GBColor;
import com.blackaby.Backend.Platform.EmulatorButton;
import com.blackaby.Backend.Platform.EmulatorProfile;
import com.blackaby.Backend.Helpers.SaveFileManager;
import com.blackaby.Frontend.Borders.DisplayBorderManager;
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
import com.blackaby.Misc.ControllerPollingMode;
import com.blackaby.Misc.GameArtDisplayMode;
import com.blackaby.Misc.GameDuckDataBundleManager;
import com.blackaby.Misc.GameNameBracketDisplayMode;
import com.blackaby.Misc.NonGbcColourMode;
import com.blackaby.Misc.Settings;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.AbstractButton;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hosts the application options window.
 * <p>
 * The window groups palette editing, control rebinding, sound settings, window
 * layout, and host theme changes in one place.
 */
public class OptionsWindow extends DuckWindow {

    private enum DmgPaletteModeOption {
        CUSTOM_PALETTE(UiText.OptionsWindow.DMG_PALETTE_MODE_CUSTOM, NonGbcColourMode.CUSTOM_PALETTE),
        GBC_COLOURISATION(UiText.OptionsWindow.DMG_PALETTE_MODE_GBC, NonGbcColourMode.GBC_COLOURISATION),
        GBC_ORIGINAL(UiText.OptionsWindow.DMG_PALETTE_MODE_GB, NonGbcColourMode.GB_ORIGINAL);

        private final String label;
        private final NonGbcColourMode mode;

        DmgPaletteModeOption(String label, NonGbcColourMode mode) {
            this.label = label;
            this.mode = mode;
        }

        private static DmgPaletteModeOption fromSetting(NonGbcColourMode mode) {
            return switch (mode == null ? NonGbcColourMode.GB_ORIGINAL : mode) {
                case CUSTOM_PALETTE -> CUSTOM_PALETTE;
                case GBC_COLOURISATION -> GBC_COLOURISATION;
                default -> GBC_ORIGINAL;
            };
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
    private final JPanel[] gbPaletteEditorSwatches = new JPanel[4];
    private final JPanel[] gbcCompatibilityPaletteSwatches = new JPanel[4];
    private final JPanel[] themeStripPreviews = new JPanel[AppThemeColorRole.values().length];
    private final Map<EmulatorButton, JButton> keyboardBindingButtons = new HashMap<>();
    private final Map<EmulatorButton, JButton> controllerBindingButtons = new HashMap<>();
    private final EnumMap<AppShortcut, JButton> shortcutButtons = new EnumMap<>(AppShortcut.class);
    private final EnumMap<AppShortcut, JButton> controllerShortcutButtons = new EnumMap<>(AppShortcut.class);
    private final MainWindow mainWindow;
    private final int initialTabIndex;
    private JTabbedPane tabs;
    private JComboBox<DmgPaletteModeOption> dmgPaletteModeSelector;
    private JComboBox<GbcCompatiblePaletteModeOption> gbcCompatiblePaletteModeSelector;
    private JComboBox<ControllerChoice> controllerSelector;
    private JComboBox<ControllerPollingMode> controllerPollingModeSelector;
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
    private final AtomicBoolean controllerStatusPollQueued = new AtomicBoolean();
    private volatile ControllerInputService.ControllerLiveSnapshot latestControllerLiveSnapshot;

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
        WindowUiSupport.applyComponentTheme(tabs);
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
                createUnifiedPalettePanel()));
        content.add(Box.createVerticalGlue());
        return content;
    }

    private JComponent buildControlsTab() {
        JPanel content = createVerticalContentPanel();
        content.add(createSectionCard(
                UiText.OptionsWindow.SECTION_CONTROLS_TITLE,
                UiText.OptionsWindow.SECTION_CONTROLS_DESCRIPTION,
                createControlsPanel()));
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
                createUnifiedThemePanel()));
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

    private JComponent createUnifiedPalettePanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        JPanel gbSectionBody = createPaletteSectionBodyPanel();
        dmgPaletteModeSelector = new JComboBox<>(DmgPaletteModeOption.values());
        configureCompactPaletteSelector(dmgPaletteModeSelector);
        dmgPaletteModeSelector.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        dmgPaletteModeSelector.setSelectedItem(DmgPaletteModeOption.fromSetting(Settings.nonGbcColourMode));

        Runnable syncGbSection = () -> {
            clearSwatchReferences(gbPaletteEditorSwatches);
            gbSectionBody.removeAll();
            gbSectionBody.add(createFieldCard(UiText.OptionsWindow.GBC_NON_CGB_MODE_TITLE, dmgPaletteModeSelector));

            DmgPaletteModeOption selectedMode = dmgPaletteModeSelector
                    .getSelectedItem() instanceof DmgPaletteModeOption option
                            ? option
                            : DmgPaletteModeOption.GBC_ORIGINAL;
            if (selectedMode == DmgPaletteModeOption.CUSTOM_PALETTE) {
                gbSectionBody.add(Box.createVerticalStrut(8));
                gbSectionBody.add(createCompactWindowOptionCard(
                        createPaletteEditorWorkflow(
                                createDmgPaletteEditor(gbPaletteEditorSwatches),
                                createSavedPaletteMenuButton(UiText.OptionsWindow.SAVED_PALETTE_LABEL, false))));
            } else if (selectedMode == DmgPaletteModeOption.GBC_COLOURISATION) {
                gbSectionBody.add(Box.createVerticalStrut(8));
                gbSectionBody.add(createCompactWindowOptionCard(
                        createPaletteEditorWorkflow(
                                createGbcPaletteMatrix(),
                                createSavedPaletteMenuButton(UiText.OptionsWindow.SAVED_GBC_PALETTE_LABEL, true))));
            }

            gbSectionBody.revalidate();
            gbSectionBody.repaint();
        };

        dmgPaletteModeSelector.addActionListener(event -> {
            Object selectedItem = dmgPaletteModeSelector.getSelectedItem();
            if (selectedItem instanceof DmgPaletteModeOption selectedMode) {
                Settings.nonGbcColourMode = selectedMode.mode;
            }
            syncGbSection.run();
            Config.Save();
        });
        syncGbSection.run();

        JPanel gbcSectionBody = createPaletteSectionBodyPanel();
        gbcCompatiblePaletteModeSelector = new JComboBox<>(GbcCompatiblePaletteModeOption.values());
        configureCompactPaletteSelector(gbcCompatiblePaletteModeSelector);
        gbcCompatiblePaletteModeSelector.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        gbcCompatiblePaletteModeSelector.setSelectedItem(
                GbcCompatiblePaletteModeOption.fromSetting(Settings.preferDmgModeForGbcCompatibleGames));

        Runnable syncGbcSection = () -> {
            clearSwatchReferences(gbcCompatibilityPaletteSwatches);
            gbcSectionBody.removeAll();
            gbcSectionBody
                    .add(createFieldCard(UiText.OptionsWindow.GBC_COMPATIBLE_MODE_TITLE,
                            gbcCompatiblePaletteModeSelector));

            GbcCompatiblePaletteModeOption selectedMode = gbcCompatiblePaletteModeSelector
                    .getSelectedItem() instanceof GbcCompatiblePaletteModeOption option
                            ? option
                            : GbcCompatiblePaletteModeOption.FULL_COLOUR;
            if (selectedMode == GbcCompatiblePaletteModeOption.GB_PALETTE_ON_COMPATIBLE_GAMES) {
                gbcSectionBody.add(Box.createVerticalStrut(8));
                gbcSectionBody.add(createCompactWindowOptionCard(
                        createPaletteEditorWorkflow(
                                createDmgPaletteEditor(gbcCompatibilityPaletteSwatches),
                                createSavedPaletteMenuButton(UiText.OptionsWindow.SAVED_PALETTE_LABEL, false))));
            }

            gbcSectionBody.revalidate();
            gbcSectionBody.repaint();
        };

        gbcCompatiblePaletteModeSelector.addActionListener(event -> {
            Object selectedItem = gbcCompatiblePaletteModeSelector.getSelectedItem();
            if (selectedItem instanceof GbcCompatiblePaletteModeOption selectedMode) {
                Settings.preferDmgModeForGbcCompatibleGames = selectedMode.preferDmgModeForCompatibleGames;
            }
            syncGbcSection.run();
            Config.Save();
        });
        syncGbcSection.run();

        JPanel toolsSectionBody = createPaletteSectionBodyPanel();
        JButton importGbPalettesButton = createSecondaryButton(UiText.OptionsWindow.IMPORT_GB_PALETTES_BUTTON);
        configureCompactPaletteButton(importGbPalettesButton, 154);
        importGbPalettesButton.addActionListener(event -> importSavedPalettes(false));

        JButton importGbcPalettesButton = createSecondaryButton(UiText.OptionsWindow.IMPORT_GBC_PALETTES_BUTTON);
        configureCompactPaletteButton(importGbcPalettesButton, 160);
        importGbcPalettesButton.addActionListener(event -> importSavedPalettes(true));

        JButton resetGbSettingsButton = createSecondaryButton(UiText.OptionsWindow.RESET_GB_SETTINGS_BUTTON);
        configureCompactPaletteButton(resetGbSettingsButton, 148);
        resetGbSettingsButton.addActionListener(event -> {
            boolean preserveGbcCompatibleMode = Settings.preferDmgModeForGbcCompatibleGames;
            Settings.ResetPalette();
            Settings.ResetGbcPaletteMode();
            Settings.preferDmgModeForGbcCompatibleGames = preserveGbcCompatibleMode;
            if (dmgPaletteModeSelector != null) {
                dmgPaletteModeSelector.setSelectedItem(DmgPaletteModeOption.fromSetting(Settings.nonGbcColourMode));
            }
            refreshPaletteDetails();
            syncGbSection.run();
            Config.Save();
        });

        JButton resetGbcSettingsButton = createSecondaryButton(UiText.OptionsWindow.RESET_GBC_SETTINGS_BUTTON);
        configureCompactPaletteButton(resetGbcSettingsButton, 154);
        resetGbcSettingsButton.addActionListener(event -> {
            Settings.preferDmgModeForGbcCompatibleGames = false;
            if (gbcCompatiblePaletteModeSelector != null) {
                gbcCompatiblePaletteModeSelector.setSelectedItem(
                        GbcCompatiblePaletteModeOption.fromSetting(Settings.preferDmgModeForGbcCompatibleGames));
            }
            refreshPaletteDetails();
            syncGbcSection.run();
            Config.Save();
        });

        toolsSectionBody.add(importGbPalettesButton);
        toolsSectionBody.add(Box.createVerticalStrut(6));
        toolsSectionBody.add(importGbcPalettesButton);
        toolsSectionBody.add(Box.createVerticalStrut(10));
        toolsSectionBody.add(resetGbSettingsButton);
        toolsSectionBody.add(Box.createVerticalStrut(6));
        toolsSectionBody.add(resetGbcSettingsButton);

        JComponent gbSection = createCompactDisclosurePanel(
                UiText.OptionsWindow.GB_SETTINGS_SECTION_TITLE,
                createCompactWindowOptionCard(gbSectionBody),
                true);
        gbSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(gbSection);
        content.add(Box.createVerticalStrut(8));

        JComponent gbcSection = createCompactDisclosurePanel(
                UiText.OptionsWindow.GBC_SETTINGS_SECTION_TITLE,
                createCompactWindowOptionCard(gbcSectionBody),
                false);
        gbcSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(gbcSection);
        content.add(Box.createVerticalStrut(8));

        JComponent toolsSection = createCompactDisclosurePanel(
                UiText.OptionsWindow.PALETTE_TOOLS_SECTION_TITLE,
                createCompactWindowOptionCard(toolsSectionBody),
                false);
        toolsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(toolsSection);
        return content;
    }

    private JPanel createPaletteSectionBodyPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        return panel;
    }

    private JComponent createPaletteEditorWorkflow(JComponent editor, JButton savedPaletteButton) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(editor);
        content.add(Box.createVerticalStrut(8));
        savedPaletteButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(savedPaletteButton);
        return content;
    }

    private JComponent createDmgPaletteEditor(JPanel[] targetSwatches) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JPanel swatchStrip = new JPanel(new GridLayout(1, 4, 6, 0));
        swatchStrip.setOpaque(false);

        GBColor[] palette = Settings.CurrentPalette();
        String[] toneNames = UiText.OptionsWindow.DMG_TONE_NAMES;
        for (int index = 0; index < palette.length; index++) {
            JPanel swatch = new JPanel();
            swatch.setPreferredSize(new Dimension(40, 36));
            swatch.setBackground(palette[index].ToColour());
            swatch.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(58, 92, 132, 60), 1, true),
                    BorderFactory.createEmptyBorder(1, 1, 1, 1)));
            swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            swatch.setToolTipText(UiText.OptionsWindow.EditPaletteToneTooltip(toneNames[index]));
            final int paletteIndex = index;
            swatch.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    chooseColor(paletteIndex, UiText.OptionsWindow.PaletteToneColorLabel(toneNames[paletteIndex]));
                }
            });
            targetSwatches[index] = swatch;
            swatchStrip.add(swatch);
        }

        content.add(swatchStrip);
        return content;
    }

    private JButton createSavedPaletteMenuButton(String label, boolean gbcPalette) {
        JButton button = createSecondaryButton(label);
        configureCompactPaletteButton(button, gbcPalette ? 148 : 120);
        button.addActionListener(event -> showSavedPaletteMenu(button, gbcPalette));
        return button;
    }

    private void showSavedPaletteMenu(JButton anchor, boolean gbcPalette) {
        JPopupMenu menu = new JPopupMenu();
        List<String> paletteNames = gbcPalette ? Config.GetSavedGbcPaletteNames() : Config.GetSavedPaletteNames();

        JMenu loadMenu = new JMenu(UiText.PaletteManager.LOAD_BUTTON);
        loadMenu.setEnabled(!paletteNames.isEmpty());
        for (String paletteName : paletteNames) {
            JMenuItem loadItem = new JMenuItem(paletteName);
            loadItem.addActionListener(event -> {
                boolean loaded = gbcPalette ? Config.LoadGbcPalette(paletteName) : Config.LoadPalette(paletteName);
                if (loaded) {
                    refreshPaletteDetails();
                }
            });
            loadMenu.add(loadItem);
        }

        JMenuItem saveItem = new JMenuItem(
                gbcPalette ? UiText.OptionsWindow.SAVE_CURRENT_GBC_PALETTE : UiText.OptionsWindow.SAVE_CURRENT_PALETTE);
        saveItem.addActionListener(event -> savePaletteWithPrompt(gbcPalette));

        JMenu deleteMenu = new JMenu(UiText.PaletteManager.DELETE_BUTTON);
        deleteMenu.setEnabled(!paletteNames.isEmpty());
        for (String paletteName : paletteNames) {
            JMenuItem deleteItem = new JMenuItem(paletteName);
            deleteItem.addActionListener(event -> deleteSavedPalette(gbcPalette, paletteName));
            deleteMenu.add(deleteItem);
        }

        menu.add(loadMenu);
        menu.add(saveItem);
        menu.add(deleteMenu);
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void savePaletteWithPrompt(boolean gbcPalette) {
        String title = gbcPalette
                ? UiText.OptionsWindow.SAVE_CURRENT_GBC_PALETTE
                : UiText.OptionsWindow.SAVE_CURRENT_PALETTE;
        String name = JOptionPane.showInputDialog(this, UiText.OptionsWindow.PaletteNamePrompt(), title,
                JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }

        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            JOptionPane.showMessageDialog(this, UiText.OptionsWindow.PaletteNameRequiredMessage(),
                    UiText.Common.WARNING_TITLE,
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (gbcPalette) {
            Config.SaveGbcPalette(trimmedName);
        } else {
            Config.SavePalette(trimmedName);
        }
        JOptionPane.showMessageDialog(this, UiText.OptionsWindow.PaletteSavedMessage(trimmedName));
    }

    private void deleteSavedPalette(boolean gbcPalette, String paletteName) {
        int result = JOptionPane.showConfirmDialog(this,
                UiText.PaletteManager.DeleteConfirmMessage(gbcPalette, paletteName),
                UiText.PaletteManager.DeleteConfirmTitle(gbcPalette),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        if (gbcPalette) {
            Config.DeleteGbcPalette(paletteName);
        } else {
            Config.DeletePalette(paletteName);
        }
    }

    private void clearSwatchReferences(JPanel[] swatches) {
        for (int index = 0; index < swatches.length; index++) {
            swatches[index] = null;
        }
    }

    private JComponent createGbcPaletteMatrix() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(createCompactGbcPaletteRow(0, UiText.OptionsWindow.GBC_BACKGROUND_PALETTE_TITLE,
                Settings.CurrentGbcBackgroundPalette()));
        content.add(Box.createVerticalStrut(6));
        content.add(createCompactGbcPaletteRow(1, UiText.OptionsWindow.GBC_SPRITE0_PALETTE_TITLE,
                Settings.CurrentGbcSpritePalette0()));
        content.add(Box.createVerticalStrut(6));
        content.add(createCompactGbcPaletteRow(2, UiText.OptionsWindow.GBC_SPRITE1_PALETTE_TITLE,
                Settings.CurrentGbcSpritePalette1()));
        return content;
    }

    private JComponent createCompactGbcPaletteRow(int paletteIndex, String titleText, GBColor[] palette) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);

        JLabel title = createFieldLabel(titleText);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        title.setPreferredSize(new Dimension(120, 24));

        JPanel swatchGrid = new JPanel(new GridLayout(1, 4, 4, 0));
        swatchGrid.setOpaque(false);
        for (int colourIndex = 0; colourIndex < palette.length; colourIndex++) {
            swatchGrid.add(createGbcPaletteSwatch(paletteIndex, titleText, colourIndex, palette[colourIndex]));
        }

        row.add(title, BorderLayout.WEST);
        row.add(swatchGrid, BorderLayout.CENTER);
        return row;
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

    private JComponent createGbcPaletteSwatch(int paletteIndex, String paletteTitle, int colourIndex, GBColor colour) {
        int flatIndex = (paletteIndex * 4) + colourIndex;
        MouseAdapter chooseListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                chooseGbcPaletteColor(paletteIndex, colourIndex);
            }
        };

        JPanel swatch = new JPanel();
        swatch.setPreferredSize(new Dimension(24, 24));
        swatch.setBackground(colour.ToColour());
        swatch.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(58, 92, 132, 45), 1, true),
                BorderFactory.createEmptyBorder(1, 1, 1, 1)));
        swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        swatch.setToolTipText(UiText.OptionsWindow.ChooseColorTitle(
                paletteTitle + " " + UiText.OptionsWindow.GbcPaletteButtonLabel(colourIndex)));
        swatch.addMouseListener(chooseListener);
        gbcColorPreviews[flatIndex] = swatch;
        return swatch;
    }

    private JComponent createUnifiedThemePanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JComponent coloursSection = createCompactDisclosurePanel(
                UiText.OptionsWindow.THEME_COLOURS_SECTION_TITLE,
                createCompactWindowOptionCard(createThemeColourSectionBody()),
                true);
        coloursSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(coloursSection);
        content.add(Box.createVerticalStrut(8));

        JComponent toolsSection = createCompactDisclosurePanel(
                UiText.OptionsWindow.THEME_TOOLS_SECTION_TITLE,
                createCompactWindowOptionCard(createThemeToolsSectionBody()),
                false);
        toolsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(toolsSection);
        return content;
    }

    private JPanel createThemeColourSectionBody() {
        JPanel body = createPaletteSectionBodyPanel();
        body.add(createThemeColourStrip());
        return body;
    }

    private JComponent createThemeColourStrip() {
        JPanel content = new JPanel(new GridLayout(1, AppThemeColorRole.values().length, 6, 0));
        content.setOpaque(false);
        for (AppThemeColorRole role : AppThemeColorRole.values()) {
            content.add(createThemeColourTile(role));
        }
        return content;
    }

    private JComponent createThemeColourTile(AppThemeColorRole role) {
        JPanel tile = new JPanel(new BorderLayout(0, 6));
        tile.setOpaque(true);
        tile.setBackground(Styling.cardTintColour);
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        JLabel label = new JLabel(role.Label(), SwingConstants.CENTER);
        label.setFont(Styling.menuFont.deriveFont(Font.BOLD, 11f));
        label.setForeground(accentColour);

        JPanel swatch = new JPanel();
        swatch.setPreferredSize(new Dimension(44, 40));
        swatch.setBackground(Settings.CurrentAppTheme().CoreColour(role));
        swatch.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(58, 92, 132, 60), 1, true),
                BorderFactory.createEmptyBorder(3, 3, 3, 3)));
        swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        swatch.setToolTipText(role.Description());
        swatch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                chooseThemeColor(role);
            }
        });

        tile.add(label, BorderLayout.NORTH);
        tile.add(swatch, BorderLayout.CENTER);
        return tile;
    }

    private JPanel createThemeToolsSectionBody() {
        JPanel body = createPaletteSectionBodyPanel();

        JTextField themeNameField = new JTextField();
        configureCompactPaletteField(themeNameField);
        body.add(createFieldCard(UiText.OptionsWindow.SAVE_CURRENT_THEME, themeNameField));
        body.add(Box.createVerticalStrut(8));

        JButton saveThemeButton = createPrimaryButton(UiText.OptionsWindow.SAVE_THEME_BUTTON);
        configureCompactPaletteButton(saveThemeButton, 100);
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
        configureCompactPaletteButton(browseThemesButton, 100);
        browseThemesButton.addActionListener(event -> new ThemeManager(() -> {
            if (mainWindow != null) {
                mainWindow.RefreshTheme();
            }
            reopenWithCurrentTab();
        }));

        JButton resetThemeButton = createSecondaryButton(UiText.OptionsWindow.RESET_THEME_BUTTON);
        configureCompactPaletteButton(resetThemeButton, 100);
        resetThemeButton.addActionListener(event -> {
            Settings.ResetAppTheme();
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshTheme();
            }
            reopenWithCurrentTab();
        });

        body.add(createResponsiveGroup(104, 3, saveThemeButton, browseThemesButton, resetThemeButton));
        return body;
    }

    private JComponent createControlsPanel() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.add(createControllerHubCard());
        container.add(Box.createVerticalStrut(10));
        container.add(createBindingListSection(
                UiText.OptionsWindow.PLAYER_CONTROLS_TITLE,
                UiText.OptionsWindow.PLAYER_CONTROLS_DESCRIPTION,
                createControlBindingList(),
                true));
        container.add(Box.createVerticalStrut(8));
        container.add(createBindingListSection(
                UiText.OptionsWindow.WINDOW_SHORTCUTS_TITLE,
                UiText.OptionsWindow.WINDOW_SHORTCUTS_DESCRIPTION,
                createShortcutBindingList(),
                false));
        return container;
    }

    private JComponent createControllerHubCard() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

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
            if (mainWindow != null) {
                mainWindow.RefreshControllerInputRouting();
            }
            refreshControllerStatus();
        });

        JButton refreshControllerButton = createSecondaryButton(UiText.OptionsWindow.CONTROLLER_REFRESH_BUTTON);
        configureCompactPaletteButton(refreshControllerButton, 132);
        refreshControllerButton.addActionListener(event -> {
            refreshControllerButton.setEnabled(false);
            CompletableFuture.runAsync(controllerInputService::RefreshControllers)
                    .whenComplete((ignored, exception) -> SwingUtilities.invokeLater(() -> {
                        refreshControllerButton.setEnabled(true);
                        refreshKnownControllerDevices();
                        requestControllerStatusPoll();
                    }));
        });

        controllerSelector = new JComboBox<>();
        configureCompactPaletteSelector(controllerSelector);
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
                if (mainWindow != null) {
                    mainWindow.RefreshControllerInputRouting();
                }
                latestControllerLiveSnapshot = null;
                requestControllerStatusPoll();
                refreshControllerStatus();
            }
        });

        controllerPollingModeSelector = new JComboBox<>(ControllerPollingMode.values());
        configureCompactPaletteSelector(controllerPollingModeSelector);
        controllerPollingModeSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        controllerPollingModeSelector.setSelectedItem(Settings.controllerPollingMode);
        controllerPollingModeSelector.addActionListener(event -> {
            if (updatingControllerUi) {
                return;
            }

            ControllerPollingMode selectedMode = (ControllerPollingMode) controllerPollingModeSelector
                    .getSelectedItem();
            if (selectedMode == null || selectedMode == Settings.controllerPollingMode) {
                return;
            }

            Settings.controllerPollingMode = selectedMode;
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshControllerInputRouting();
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
                controllerDeadzoneValueLabel
                        .setText(UiText.OptionsWindow.PercentValue(Settings.controllerDeadzonePercent));
            }
            if (!controllerDeadzoneSlider.getValueIsAdjusting()) {
                Config.Save();
            }
        });
        controllerDeadzoneValueLabel = createValueLabel(
                UiText.OptionsWindow.PercentValue(Settings.controllerDeadzonePercent));

        controllerLiveInputsArea = createCompactReadoutLabel(UiText.OptionsWindow.CONTROLLER_LIVE_NONE);
        controllerMappedButtonsArea = createCompactReadoutLabel(UiText.OptionsWindow.CONTROLLER_MAPPED_NONE);
        content.add(createResponsiveGroup(180, 2, controllerEnabledCheckBox, refreshControllerButton));
        content.add(Box.createVerticalStrut(8));
        content.add(createResponsiveGroup(
                240,
                2,
                createFieldCard(UiText.OptionsWindow.CONTROLLER_SELECTION_LABEL, controllerSelector),
                createFieldCard(UiText.OptionsWindow.CONTROLLER_POLLING_MODE_LABEL, controllerPollingModeSelector)));
        content.add(Box.createVerticalStrut(8));
        content.add(createFieldCard(UiText.OptionsWindow.CONTROLLER_DEADZONE_LABEL, wrapControllerDeadzoneControls()));
        content.add(Box.createVerticalStrut(8));
        content.add(createCompactDisclosurePanel(
                UiText.OptionsWindow.CONTROLLER_DETAILS_TITLE,
                createControllerDetailRows(),
                false));
        return createCompactWindowOptionCard(content);
    }

    private JComponent createControllerDetailRows() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(createFieldCard(UiText.OptionsWindow.CONTROLLER_LIVE_INPUTS_LABEL, controllerLiveInputsArea));
        content.add(Box.createVerticalStrut(6));
        content.add(createFieldCard(UiText.OptionsWindow.CONTROLLER_MAPPED_BUTTONS_LABEL, controllerMappedButtonsArea));
        return content;
    }

    private JComponent createBindingListSection(String title, String description, JComponent body, boolean expanded) {
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);

        JButton toggleButton = new JButton();
        toggleButton.setBorder(BorderFactory.createEmptyBorder());
        toggleButton.setContentAreaFilled(false);
        toggleButton.setFocusPainted(false);
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
        toggleButton.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        toggleButton.setForeground(accentColour);

        JTextArea descriptionLabel = createBodyTextArea(description, 11f);

        JPanel bodyWrap = new JPanel(new BorderLayout());
        bodyWrap.setOpaque(false);
        bodyWrap.add(body, BorderLayout.CENTER);
        bodyWrap.setVisible(expanded);

        Runnable syncState = () -> toggleButton.setText(title + (bodyWrap.isVisible() ? "  -" : "  +"));
        syncState.run();
        toggleButton.addActionListener(event -> {
            bodyWrap.setVisible(!bodyWrap.isVisible());
            syncState.run();
            content.revalidate();
            content.repaint();
        });

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(toggleButton);
        if (shouldRenderUiText(description)) {
            header.add(Box.createVerticalStrut(2));
            header.add(descriptionLabel);
        }

        content.add(header, BorderLayout.NORTH);
        content.add(bodyWrap, BorderLayout.CENTER);
        return createCompactWindowOptionCard(content);
    }

    private JComponent createCompactDisclosurePanel(String title, JComponent body, boolean expanded) {
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setOpaque(false);

        JButton toggleButton = new JButton();
        toggleButton.setBorder(BorderFactory.createEmptyBorder());
        toggleButton.setContentAreaFilled(false);
        toggleButton.setFocusPainted(false);
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
        toggleButton.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        toggleButton.setForeground(accentColour);

        JPanel bodyWrap = new JPanel(new BorderLayout());
        bodyWrap.setOpaque(false);
        bodyWrap.add(body, BorderLayout.CENTER);
        bodyWrap.setVisible(expanded);

        Runnable syncState = () -> toggleButton.setText(title + (bodyWrap.isVisible() ? "  -" : "  +"));
        syncState.run();
        toggleButton.addActionListener(event -> {
            bodyWrap.setVisible(!bodyWrap.isVisible());
            syncState.run();
            content.revalidate();
            content.repaint();
        });

        content.add(toggleButton, BorderLayout.NORTH);
        content.add(bodyWrap, BorderLayout.CENTER);
        return content;
    }

    private JComponent createControlBindingList() {
        keyboardBindingButtons.clear();
        controllerBindingButtons.clear();

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(createBindingListHeader());
        content.add(Box.createVerticalStrut(4));

        boolean firstRow = true;
        for (EmulatorButton button : backendProfile().controlButtons()) {
            if (!firstRow) {
                content.add(Box.createVerticalStrut(2));
            }
            content.add(createControlBindingRow(button));
            firstRow = false;
        }

        content.add(Box.createVerticalStrut(8));
        JButton resetKeyboardControlsButton = createSecondaryButton(UiText.OptionsWindow.RESET_CONTROLS_BUTTON);
        configureCompactPaletteButton(resetKeyboardControlsButton, 124);
        resetKeyboardControlsButton.addActionListener(event -> {
            Settings.ResetControls();
            refreshKeyboardBindingButtons();
            Config.Save();
        });

        JButton resetControllerControlsButton = createSecondaryButton(UiText.OptionsWindow.CONTROLLER_RESET_BUTTON);
        configureCompactPaletteButton(resetControllerControlsButton, 150);
        resetControllerControlsButton.addActionListener(event -> {
            Settings.ResetControllerControls();
            refreshControllerBindingButtons();
            if (mainWindow != null) {
                mainWindow.RefreshControllerInputRouting();
            }
            latestControllerLiveSnapshot = null;
            requestControllerStatusPoll();
            refreshControllerStatus();
            Config.Save();
        });

        JPanel actions = createResponsiveGroup(160, 2, resetKeyboardControlsButton, resetControllerControlsButton);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(actions);
        return content;
    }

    private JComponent createShortcutBindingList() {
        shortcutButtons.clear();
        controllerShortcutButtons.clear();

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(createBindingListHeader());
        content.add(Box.createVerticalStrut(4));

        boolean firstRow = true;
        for (AppShortcut shortcut : AppShortcut.values()) {
            if (!firstRow) {
                content.add(Box.createVerticalStrut(2));
            }
            content.add(createShortcutListRow(shortcut));
            firstRow = false;
        }

        content.add(Box.createVerticalStrut(8));
        JButton resetShortcutsButton = createSecondaryButton(UiText.OptionsWindow.RESET_SHORTCUTS_BUTTON);
        configureCompactPaletteButton(resetShortcutsButton, 156);
        resetShortcutsButton.addActionListener(event -> {
            Settings.ResetAppShortcuts();
            refreshShortcutButtons();
            if (mainWindow != null) {
                mainWindow.RefreshAppShortcuts();
            }
            Config.Save();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.add(resetShortcutsButton);
        content.add(actions);
        return content;
    }

    private JComponent createBindingListHeader() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(58, 92, 132, 28)));

        JLabel actionLabel = new JLabel("Action");
        actionLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 11f));
        actionLabel.setForeground(mutedText);

        JPanel columns = new JPanel(new GridLayout(1, 2, 6, 0));
        columns.setOpaque(false);
        JLabel keyboardLabel = new JLabel(UiText.OptionsWindow.SHORTCUT_KEYBOARD_LABEL, SwingConstants.CENTER);
        keyboardLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 10f));
        keyboardLabel.setForeground(mutedText);
        JLabel controllerLabel = new JLabel(UiText.OptionsWindow.SHORTCUT_CONTROLLER_LABEL, SwingConstants.CENTER);
        controllerLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 10f));
        controllerLabel.setForeground(mutedText);
        columns.add(keyboardLabel);
        columns.add(controllerLabel);

        JPanel columnsWrap = new JPanel(new BorderLayout());
        columnsWrap.setOpaque(false);
        columnsWrap.setPreferredSize(new Dimension(204, 20));
        columnsWrap.add(columns, BorderLayout.CENTER);

        header.add(actionLabel, BorderLayout.CENTER);
        header.add(columnsWrap, BorderLayout.EAST);
        return header;
    }

    private JComponent createControlBindingRow(EmulatorButton button) {
        JButton keyboardButton = createPrimaryButton(Settings.inputBindings.GetKeyText(backendProfile().backendId(), button));
        configureCompactPaletteButton(keyboardButton, 96);
        keyboardButton.addActionListener(event -> captureKeyboardBinding(button));
        keyboardBindingButtons.put(button, keyboardButton);

        JButton controllerButton = createSecondaryButton(Settings.controllerBindings.GetBindingText(backendProfile().backendId(), button));
        configureCompactPaletteButton(controllerButton, 102);
        controllerButton.addActionListener(event -> captureControllerBinding(button));
        controllerBindingButtons.put(button, controllerButton);

        return createMinimalBindingRow(
                formatControlButtonName(button),
                backendProfile().controlButtonHelper(button),
                keyboardButton,
                controllerButton);
    }

    private JComponent createShortcutListRow(AppShortcut shortcut) {
        JButton keyboardButton = createPrimaryButton(Settings.appShortcutBindings.GetKeyText(shortcut));
        configureCompactPaletteButton(keyboardButton, 96);
        keyboardButton.addActionListener(event -> captureShortcut(shortcut));
        shortcutButtons.put(shortcut, keyboardButton);

        JButton controllerButton = createSecondaryButton(
                Settings.appShortcutControllerBindings.GetBindingText(shortcut));
        configureCompactPaletteButton(controllerButton, 102);
        controllerButton.addActionListener(event -> captureControllerShortcut(shortcut));
        controllerShortcutButtons.put(shortcut, controllerButton);

        return createMinimalBindingRow(shortcut.Label(), shortcut.Description(), keyboardButton, controllerButton);
    }

    private JComponent createMinimalBindingRow(String titleText, String helperText, JButton keyboardButton,
            JButton controllerButton) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(58, 92, 132, 18)),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)));

        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        titleLabel.setForeground(accentColour);
        if (shouldRenderUiText(helperText)) {
            titleLabel.setToolTipText(helperText);
        }

        JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
        buttons.setOpaque(false);
        buttons.setPreferredSize(new Dimension(204, 26));
        buttons.add(keyboardButton);
        buttons.add(controllerButton);

        row.add(titleLabel, BorderLayout.CENTER);
        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    private JComponent createSoundPanel() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);

        JCheckBox[] channelMuteCheckBoxes = new JCheckBox[4];
        AudioKnob[] channelVolumeKnobs = new AudioKnob[4];
        AudioKnob[] masterVolumeKnobHolder = new AudioKnob[1];
        DefaultListModel<AudioEnhancementSetting> enhancementChainModel = new DefaultListModel<>();
        for (AudioEnhancementSetting setting : Settings.CurrentAudioEnhancementChain()) {
            enhancementChainModel.addElement(setting);
        }

        JCheckBox muteCheckBox = new JCheckBox(UiText.OptionsWindow.MUTE_CHECKBOX, !Settings.soundEnabled);
        muteCheckBox.setOpaque(false);
        muteCheckBox.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 11f));
        muteCheckBox.setForeground(accentColour);
        muteCheckBox.setBorder(BorderFactory.createEmptyBorder());
        muteCheckBox.addActionListener(event -> {
            Settings.soundEnabled = !muteCheckBox.isSelected();
            Config.Save();
            mainWindow.GetEmulation().ResetTransientAudioState();
        });

        JPanel masterFooter = new JPanel();
        masterFooter.setLayout(new BoxLayout(masterFooter, BoxLayout.Y_AXIS));
        masterFooter.setOpaque(false);
        muteCheckBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        masterFooter.add(muteCheckBox);

        JComponent masterKnobTile = createMixerKnobTile(
                UiText.OptionsWindow.MASTER_VOLUME_TITLE,
                UiText.OptionsWindow.MASTER_VOLUME_HELPER,
                Settings.masterVolume,
                (newValue, adjusting) -> {
                    Settings.masterVolume = newValue;
                    if (!adjusting) {
                        Config.Save();
                    }
                },
                masterVolumeKnobHolder,
                0,
                masterFooter);

        JPanel channelGrid = new ResponsiveTileGridPanel(108, 5);
        channelGrid.setOpaque(false);
        channelGrid.add(masterKnobTile);

        for (int channelIndex = 0; channelIndex < 4; channelIndex++) {
            channelGrid.add(createChannelMixerKnobTile(
                    channelIndex,
                    channelMuteCheckBoxes,
                    channelVolumeKnobs));
        }

        JPanel channelBody = new JPanel(new BorderLayout(0, 8));
        channelBody.setOpaque(false);
        channelBody.add(createInfoTextBlock(
                UiText.OptionsWindow.CHANNEL_MIXER_TITLE,
                UiText.OptionsWindow.MASTER_VOLUME_HELPER + " " + UiText.OptionsWindow.CHANNEL_MIXER_HELPER,
                14f), BorderLayout.NORTH);
        channelBody.add(channelGrid, BorderLayout.CENTER);
        container.add(createCompactWindowOptionCard(channelBody));
        container.add(Box.createVerticalStrut(8));

        JCheckBox enhancementEnabledCheckBox = new JCheckBox(UiText.OptionsWindow.AUDIO_ENHANCEMENTS_ENABLED_CHECKBOX,
                Settings.IsAudioEnhancementChainEnabled());
        enhancementEnabledCheckBox.setOpaque(false);
        enhancementEnabledCheckBox.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        enhancementEnabledCheckBox.setForeground(accentColour);
        enhancementEnabledCheckBox.setBorder(BorderFactory.createEmptyBorder());

        JButton openChainEditorButton = createSecondaryButton(UiText.OptionsWindow.OPEN_AUDIO_CHAIN_EDITOR_BUTTON);
        configureCompactPaletteButton(openChainEditorButton, 124);
        openChainEditorButton.setVisible(enhancementEnabledCheckBox.isSelected());
        openChainEditorButton.addActionListener(event -> openAudioEnhancementEditor(enhancementChainModel));

        enhancementEnabledCheckBox.addActionListener(event -> {
            boolean enabled = enhancementEnabledCheckBox.isSelected();
            Settings.SetAudioEnhancementChainEnabled(enabled);
            Config.Save();
            mainWindow.GetEmulation().ResetTransientAudioState();
            openChainEditorButton.setVisible(enabled);
            openChainEditorButton.getParent().revalidate();
            openChainEditorButton.getParent().repaint();
        });

        JPanel enhancementCard = new JPanel(new BorderLayout(0, 8));
        enhancementCard.setOpaque(false);
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(openChainEditorButton);
        enhancementCard.add(createResponsiveGroup(
                260,
                2,
                createInfoTextBlock(
                        UiText.OptionsWindow.AUDIO_ENHANCEMENTS_TITLE,
                        UiText.OptionsWindow.AUDIO_ENHANCEMENTS_HELPER,
                        14f),
                buttonRow), BorderLayout.CENTER);

        JPanel enhancementToggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        enhancementToggleWrap.setOpaque(false);
        enhancementToggleWrap.add(enhancementEnabledCheckBox);
        enhancementCard.add(enhancementToggleWrap, BorderLayout.NORTH);
        container.add(createCompactWindowOptionCard(enhancementCard));
        return container;
    }

    private void openAudioEnhancementEditor(DefaultListModel<AudioEnhancementSetting> enhancementChainModel) {
        JDialog dialog = new JDialog(this, UiText.OptionsWindow.AUDIO_CHAIN_EDITOR_TITLE, false);
        dialog.setUndecorated(true);
        dialog.setLayout(new BorderLayout());
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().setBackground(panelBackground);

        ListDataListener listener = new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent event) {
            }

            @Override
            public void intervalRemoved(ListDataEvent event) {
            }

            @Override
            public void contentsChanged(ListDataEvent event) {
            }
        };
        enhancementChainModel.addListDataListener(listener);

        JScrollPane scrollPane = new JScrollPane(createAudioEnhancementCard(enhancementChainModel));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(panelBackground);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel shell = new JPanel(new BorderLayout(0, 0));
        shell.setOpaque(true);
        shell.setBackground(Styling.appBackgroundColour);
        shell.setBorder(WindowUiSupport.createLineBorder(Styling.surfaceBorderColour));

        JPanel titleBar = new JPanel(new BorderLayout(8, 0));
        titleBar.setOpaque(true);
        titleBar.setBackground(Styling.surfaceColour);
        titleBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Styling.surfaceBorderColour),
                BorderFactory.createEmptyBorder(8, 12, 8, 10)));

        JLabel titleLabel = new JLabel(UiText.OptionsWindow.AUDIO_CHAIN_EDITOR_TITLE);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12.5f));
        titleLabel.setForeground(Styling.accentColour);

        JButton closeButton = createCompactCloseGlyphButton(UiText.OptionsWindow.CLOSE_BUTTON, 34, 24);
        closeButton.addActionListener(event -> dialog.dispose());

        JPanel titleActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        titleActions.setOpaque(false);
        titleActions.add(closeButton);

        titleBar.add(titleLabel, BorderLayout.CENTER);
        titleBar.add(titleActions, BorderLayout.EAST);

        final Point[] dragAnchor = { null };
        final Point[] startLocation = { null };
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                dragAnchor[0] = event.getLocationOnScreen();
                startLocation[0] = dialog.getLocation();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragAnchor[0] == null || startLocation[0] == null || !SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                Point current = event.getLocationOnScreen();
                dialog.setLocation(
                        startLocation[0].x + (current.x - dragAnchor[0].x),
                        startLocation[0].y + (current.y - dragAnchor[0].y));
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragAnchor[0] = null;
                startLocation[0] = null;
            }
        };
        titleBar.addMouseListener(dragAdapter);
        titleBar.addMouseMotionListener(dragAdapter);
        titleLabel.addMouseListener(dragAdapter);
        titleLabel.addMouseMotionListener(dragAdapter);

        shell.add(titleBar, BorderLayout.NORTH);
        shell.add(scrollPane, BorderLayout.CENTER);
        dialog.setContentPane(shell);
        WindowUiSupport.applyComponentTheme(dialog);
        dialog.setSize(860, 720);
        dialog.setLocationRelativeTo(this);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                enhancementChainModel.removeListDataListener(listener);
            }
        });
        dialog.setVisible(true);
    }

    private JComponent createAudioEnhancementCard(DefaultListModel<AudioEnhancementSetting> enhancementChainModel) {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setBackground(Styling.surfaceColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel header = createResponsiveGroup(
                280,
                1,
                createInfoTextBlock(
                        UiText.OptionsWindow.AUDIO_ENHANCEMENTS_TITLE,
                        UiText.OptionsWindow.AUDIO_ENHANCEMENTS_HELPER,
                        15f));

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

        JButton removeButton = createCompactCloseGlyphButton(UiText.OptionsWindow.REMOVE_BUTTON, 34, 28);
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
        JCheckBox muteCheckBox = new JCheckBox(UiText.OptionsWindow.MUTE_CHECKBOX,
                Settings.IsChannelMuted(channelIndex));
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
            effectTitle
                    .setFont(Styling.menuFont.deriveFont(Font.BOLD, nextUltraCompact ? 11f : nextCompact ? 12f : 13f));
            effectDescription
                    .setFont(Styling.menuFont.deriveFont(Font.PLAIN, nextUltraCompact ? 10f : nextCompact ? 11f : 12f));
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

    static final class ResponsiveLayoutState {
        private int columns = -1;
        private int geometryKey = -1;
        private int widthBucket = -1;

        boolean update(int columns, int geometryKey, int widthBucket) {
            if (this.columns == columns
                    && this.geometryKey == geometryKey
                    && this.widthBucket == widthBucket) {
                return false;
            }
            this.columns = columns;
            this.geometryKey = geometryKey;
            this.widthBucket = widthBucket;
            return true;
        }

        void clear() {
            columns = -1;
            geometryKey = -1;
            widthBucket = -1;
        }
    }

    private static final class ResponsiveTileGridPanel extends JPanel {
        private final int minTileWidth;
        private final int maxColumns;
        private final int gap = 10;
        private final ResponsiveLayoutState layoutState = new ResponsiveLayoutState();

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
                    int availableWidth = Math.max(0, getWidth());
                    int columns = computeColumns(availableWidth);
                    int cellWidth = columns <= 0
                            ? 0
                            : Math.max(0, (availableWidth - (gap * (columns - 1))) / columns);
                    int widthBucket = cellWidth <= 0 ? 0 : Math.max(1, cellWidth / 24);
                    if (layoutState.update(columns, widthBucket, widthBucket)) {
                        revalidate();
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
        private final ResponsiveLayoutState layoutState = new ResponsiveLayoutState();

        private ResponsiveAudioKnobPanel(int knobCount) {
            super(null);
            this.knobCount = Math.max(1, knobCount);
            setOpaque(false);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    int availableWidth = Math.max(0, getWidth());
                    int columns = computeColumns(availableWidth);
                    int cellWidth = columns <= 0
                            ? 0
                            : Math.max(0, (availableWidth - (gap * (columns - 1))) / columns);
                    int rowHeight = computeRowHeight(cellWidth);
                    if (layoutState.update(columns, rowHeight, rowHeight)) {
                        revalidate();
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

            graphics2d.setStroke(
                    new BasicStroke(ultraCompactVisuals ? 2f : compactVisuals ? 2.5f : 3f, BasicStroke.CAP_ROUND,
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
            boolean nextCompactVisuals = width < 116 || height < 98;
            if (compactVisuals != nextCompactVisuals) {
                compactVisuals = nextCompactVisuals;
                repaint();
            }
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

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JCheckBox fillWindowCheckBox = new JCheckBox(UiText.OptionsWindow.WINDOW_FILL_CHECKBOX,
                Settings.fillWindowOutput);
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

        JCheckBox frameBlendingEnabledCheckBox = new JCheckBox(
                UiText.OptionsWindow.DISPLAY_FRAME_BLENDING_CHECKBOX,
                Settings.enableFrameBlending);
        frameBlendingEnabledCheckBox.setOpaque(false);
        frameBlendingEnabledCheckBox.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        frameBlendingEnabledCheckBox.setForeground(accentColour);
        frameBlendingEnabledCheckBox.addActionListener(event -> {
            Settings.enableFrameBlending = frameBlendingEnabledCheckBox.isSelected();
            Config.Save();
        });

        JCheckBox showDisplayFpsCheckBox = new JCheckBox(
                UiText.OptionsWindow.SHOW_DISPLAY_FPS_CHECKBOX,
                Settings.showDisplayFps);
        showDisplayFpsCheckBox.setOpaque(false);
        showDisplayFpsCheckBox.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        showDisplayFpsCheckBox.setForeground(accentColour);
        showDisplayFpsCheckBox.addActionListener(event -> {
            Settings.showDisplayFps = showDisplayFpsCheckBox.isSelected();
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshDisplayStats();
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

        JCheckBox gameNotesCheckBox = new JCheckBox(UiText.OptionsWindow.GAME_NOTES_CHECKBOX,
                Settings.showGameNotes);
        gameNotesCheckBox.setOpaque(false);
        gameNotesCheckBox.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        gameNotesCheckBox.setForeground(accentColour);
        gameNotesCheckBox.addActionListener(event -> {
            Settings.showGameNotes = gameNotesCheckBox.isSelected();
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshWindowPanels();
            }
        });

        JComboBox<GameArtDisplayMode> gameArtModeSelector = new JComboBox<>(GameArtDisplayMode.values());
        gameArtModeSelector.setSelectedItem(Settings.gameArtDisplayMode);
        gameArtModeSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        gameArtModeSelector.setBackground(Styling.surfaceColour);
        gameArtModeSelector.setForeground(accentColour);
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

        JSpinner readyPageRecentLimitSpinner = new JSpinner(
                new SpinnerNumberModel(Settings.readyPageRecentGameLimit, 1, 5, 1));
        readyPageRecentLimitSpinner.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        readyPageRecentLimitSpinner.addChangeListener(event -> {
            Object value = readyPageRecentLimitSpinner.getValue();
            if (!(value instanceof Number numberValue)) {
                return;
            }

            Settings.readyPageRecentGameLimit = Math.max(1, Math.min(5, numberValue.intValue()));
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshReadyPageRecentGames();
            }
        });
        JTextArea readyPageRecentHelperLabel = createBodyTextArea(UiText.OptionsWindow.READY_PAGE_RECENT_LIMIT_HELPER,
                12f);

        JComboBox<DisplayBorderChoice> borderSelector = new JComboBox<>();
        borderSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        borderSelector.setBackground(Styling.surfaceColour);
        borderSelector.setForeground(accentColour);

        final boolean[] updatingBorderSelector = { false };

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
            if (mainWindow != null) {
                mainWindow.RefreshDisplayBorder();
            }
        });

        JComboBox<DisplayShaderChoice> shaderSelector = new JComboBox<>();
        shaderSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        shaderSelector.setBackground(Styling.surfaceColour);
        shaderSelector.setForeground(accentColour);

        final boolean[] updatingShaderSelector = { false };

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
            if (mainWindow != null) {
                mainWindow.RefreshDisplayShader();
            }
        });

        JButton borderManagerButton = createSecondaryButton(UiText.OptionsWindow.OPEN_BORDER_MANAGER_BUTTON);
        borderManagerButton.addActionListener(event -> new DisplayBorderManagerWindow(() -> {
            DisplayBorderManager.Reload();
            refreshBorderSelector.run();
            if (mainWindow != null) {
                mainWindow.RefreshDisplayBorder();
            }
        }));

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

        JButton resetWindowButton = createSecondaryButton(UiText.OptionsWindow.RESET_WINDOW_BUTTON);
        resetWindowButton.addActionListener(event -> {
            Settings.fillWindowOutput = false;
            Settings.integerScaleWindowOutput = false;
            Settings.showSerialOutput = true;
            Settings.showGameNotes = true;
            Settings.enableFrameBlending = true;
            Settings.showDisplayFps = true;
            Settings.gameArtDisplayMode = GameArtDisplayMode.BOX_ART;
            Settings.displayBorderId = "none";
            Settings.readyPageRecentGameLimit = 3;
            fillWindowCheckBox.setSelected(Settings.fillWindowOutput);
            integerScaleCheckBox.setSelected(Settings.integerScaleWindowOutput);
            frameBlendingEnabledCheckBox.setSelected(Settings.enableFrameBlending);
            showDisplayFpsCheckBox.setSelected(Settings.showDisplayFps);
            serialOutputCheckBox.setSelected(Settings.showSerialOutput);
            gameNotesCheckBox.setSelected(Settings.showGameNotes);
            gameArtModeSelector.setSelectedItem(Settings.gameArtDisplayMode);
            readyPageRecentLimitSpinner.setValue(Settings.readyPageRecentGameLimit);
            refreshBorderSelector.run();
            refreshShaderSelector.run();
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshWindowPanels();
                mainWindow.RefreshReadyPageRecentGames();
                mainWindow.RefreshDisplayStats();
                mainWindow.RefreshDisplayBorder();
                mainWindow.RefreshDisplayShader();
            }
        });

        JPanel readyPageRecentCard = new JPanel(new BorderLayout(0, 10));
        readyPageRecentCard.setOpaque(false);
        readyPageRecentCard.add(createFieldCard(UiText.OptionsWindow.READY_PAGE_RECENT_LIMIT_LABEL,
                readyPageRecentLimitSpinner), BorderLayout.NORTH);
        readyPageRecentCard.add(readyPageRecentHelperLabel, BorderLayout.CENTER);

        JPanel layoutOptionsGrid = createResponsiveGroup(
                300,
                2,
                createSimpleWindowOptionCard(fillWindowCheckBox),
                createSimpleWindowOptionCard(integerScaleCheckBox),
                createSimpleWindowOptionCard(frameBlendingEnabledCheckBox),
                createSimpleWindowOptionCard(showDisplayFpsCheckBox),
                createSimpleWindowOptionCard(serialOutputCheckBox),
                createSimpleWindowOptionCard(gameNotesCheckBox),
                createSelectorWindowOptionCard(UiText.OptionsWindow.GAME_ART_MODE_LABEL, gameArtModeSelector),
                createSimpleWindowOptionCard(readyPageRecentCard),
                createSelectorWindowOptionCard(UiText.OptionsWindow.DISPLAY_BORDER_LABEL, borderSelector),
                createSelectorWindowOptionCard(UiText.OptionsWindow.DISPLAY_SHADER_LABEL, shaderSelector));
        content.add(layoutOptionsGrid);
        content.add(Box.createVerticalStrut(12));

        JPanel actions = createResponsiveGroup(
                180,
                4,
                borderManagerButton,
                shaderEditorButton,
                resetShaderButton,
                resetWindowButton);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(actions);

        container.add(content, BorderLayout.CENTER);
        refreshBorderSelector.run();
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

    private JComponent createEmulationPanel() {
        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);
        stack.add(createCompactDataSection());
        stack.add(Box.createVerticalStrut(12));
        stack.add(createCompactBootRomSection());
        return stack;
    }

    private JComponent createCompactDataSection() {
        JPanel card = createInsetSurfaceCard(12);
        JPanel header = createInfoTextBlock(
                "Managers",
                "Open save data or save state tools.",
                15f);

        JButton openManagerButton = createPrimaryButton(UiText.OptionsWindow.SAVE_MANAGER_OPEN_BUTTON);
        configureCompactPaletteButton(openManagerButton, 154);
        openManagerButton.addActionListener(event -> new SaveDataManagerWindow(mainWindow));

        JButton openStateManagerButton = createSecondaryButton(UiText.OptionsWindow.SAVE_STATE_MANAGER_OPEN_BUTTON);
        configureCompactPaletteButton(openStateManagerButton, 154);
        openStateManagerButton.addActionListener(event -> new SaveStateManagerWindow(mainWindow));

        JTextArea pathLabel = createBodyTextArea(SaveFileManager.SaveDirectoryPath().toString(), 11f);
        pathLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        pathLabel.setForeground(mutedText);

        JPanel footerCard = createInsetSurfaceCard(4);
        JLabel title = new JLabel(UiText.OptionsWindow.SAVE_DATA_MANAGED_PATH_TITLE);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 11f));
        title.setForeground(accentColour);
        footerCard.add(title, BorderLayout.NORTH);
        footerCard.add(pathLabel, BorderLayout.CENTER);

        JPanel backupCard = createInsetSurfaceCard(8);
        backupCard.add(createInfoTextBlock(
                UiText.OptionsWindow.DATA_BACKUP_TITLE,
                UiText.OptionsWindow.DATA_BACKUP_HELPER,
                13f), BorderLayout.NORTH);

        JButton backupButton = createPrimaryButton(UiText.OptionsWindow.DATA_BACKUP_BUTTON);
        configureCompactPaletteButton(backupButton, 154);
        backupButton.addActionListener(event -> triggerDataBackup());

        JButton restoreButton = createSecondaryButton(UiText.OptionsWindow.DATA_RESTORE_BUTTON);
        configureCompactPaletteButton(restoreButton, 154);
        restoreButton.addActionListener(event -> triggerDataRestore());
        backupCard.add(createResponsiveGroup(160, 2, backupButton, restoreButton), BorderLayout.CENTER);

        JPanel resetCard = createInsetSurfaceCard(8);
        resetCard.add(createInfoTextBlock(
                UiText.OptionsWindow.DATA_RESET_TITLE,
                UiText.OptionsWindow.DATA_RESET_HELPER,
                13f), BorderLayout.NORTH);

        JButton resetPreferencesButton = createSecondaryButton(UiText.OptionsWindow.DATA_RESET_PREFERENCES_BUTTON);
        configureCompactPaletteButton(resetPreferencesButton, 170);
        resetPreferencesButton.addActionListener(event -> confirmAndRunDataAction(
                UiText.OptionsWindow.DATA_RESET_PREFERENCES_CONFIRM_TITLE,
                UiText.OptionsWindow.DataResetPreferencesConfirmMessage(),
                this::resetPreferencesData,
                UiText.OptionsWindow.DATA_RESET_FAILED_TITLE));

        JButton resetShadersButton = createSecondaryButton(UiText.OptionsWindow.DATA_RESET_SHADERS_BUTTON);
        configureCompactPaletteButton(resetShadersButton, 170);
        resetShadersButton.addActionListener(event -> confirmAndRunDataAction(
                UiText.OptionsWindow.DATA_RESET_SHADERS_CONFIRM_TITLE,
                UiText.OptionsWindow.DataResetShadersConfirmMessage(),
                this::resetShaderData,
                UiText.OptionsWindow.DATA_RESET_FAILED_TITLE));

        JButton deleteLibraryButton = createSecondaryButton(UiText.OptionsWindow.DATA_DELETE_LIBRARY_BUTTON);
        configureCompactPaletteButton(deleteLibraryButton, 170);
        deleteLibraryButton.addActionListener(event -> confirmAndRunDataAction(
                UiText.OptionsWindow.DATA_DELETE_LIBRARY_CONFIRM_TITLE,
                UiText.OptionsWindow.DataDeleteLibraryConfirmMessage(),
                this::deleteLibraryData,
                UiText.OptionsWindow.DATA_DELETE_FAILED_TITLE));

        JButton deleteSaveDataButton = createSecondaryButton(UiText.OptionsWindow.DATA_DELETE_SAVE_DATA_BUTTON);
        configureCompactPaletteButton(deleteSaveDataButton, 170);
        deleteSaveDataButton.addActionListener(event -> confirmAndRunDataAction(
                UiText.OptionsWindow.DATA_DELETE_SAVE_DATA_CONFIRM_TITLE,
                UiText.OptionsWindow.DataDeleteSaveDataConfirmMessage(),
                this::deleteAllSaveData,
                UiText.OptionsWindow.DATA_DELETE_FAILED_TITLE));

        JButton deleteEverythingButton = createSecondaryButton(UiText.OptionsWindow.DATA_DELETE_EVERYTHING_BUTTON);
        configureCompactPaletteButton(deleteEverythingButton, 170);
        deleteEverythingButton.addActionListener(event -> confirmAndRunDataAction(
                UiText.OptionsWindow.DATA_DELETE_EVERYTHING_CONFIRM_TITLE,
                UiText.OptionsWindow.DataDeleteEverythingConfirmMessage(),
                this::deleteEverythingData,
                UiText.OptionsWindow.DATA_DELETE_FAILED_TITLE));
        resetCard.add(createResponsiveGroup(170, 2,
                resetPreferencesButton,
                resetShadersButton,
                deleteLibraryButton,
                deleteSaveDataButton,
                deleteEverythingButton), BorderLayout.CENTER);

        card.add(header, BorderLayout.NORTH);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(createResponsiveGroup(160, 2, openManagerButton, openStateManagerButton));
        content.add(Box.createVerticalStrut(10));
        content.add(backupCard);
        content.add(Box.createVerticalStrut(10));
        content.add(resetCard);

        card.add(content, BorderLayout.CENTER);
        card.add(footerCard, BorderLayout.SOUTH);
        return card;
    }

    private JComponent createCompactBootRomSection() {
        JPanel card = createInsetSurfaceCard(12);
        card.add(createInfoTextBlock("Boot ROMs", "Optional startup ROMs for DMG and CGB.", 15f), BorderLayout.NORTH);

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);
        rows.add(createCompactBootRomRow(new BootRomSectionSpec(
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
                true)));
        rows.add(Box.createVerticalStrut(10));
        rows.add(createCompactBootRomRow(new BootRomSectionSpec(
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
                false)));
        card.add(rows, BorderLayout.CENTER);
        return card;
    }

    private JComponent createCompactBootRomRow(BootRomSectionSpec spec) {
        boolean installed = spec.installedSupplier().getAsBoolean();
        JCheckBox toggle = createBootRomCheckBox(spec, installed);

        JPanel row = new JPanel(new BorderLayout(0, 10));
        row.setOpaque(true);
        row.setBackground(Styling.cardTintColour);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JPanel header = createResponsiveGroup(
                260,
                2,
                createInfoTextBlock(spec.bootTitle(), spec.bootHelper(), 14f),
                wrapTrailingComponent(createResponsiveGroup(
                        120,
                        1,
                        createInstallStatusBadge(installed),
                        toggle)));

        JTextArea pathLabel = createBodyTextArea(spec.pathSupplier().get().toString(), 11f);
        pathLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        pathLabel.setForeground(mutedText);

        JButton insertButton = createPrimaryButton(spec.insertButtonText());
        configureCompactPaletteButton(insertButton, 148);
        insertButton.addActionListener(event -> installBootRom(spec));

        JButton removeButton = createSecondaryButton(spec.removeButtonText());
        configureCompactPaletteButton(removeButton, 148);
        removeButton.addActionListener(event -> removeBootRom(spec));

        row.add(header, BorderLayout.NORTH);
        row.add(pathLabel, BorderLayout.CENTER);
        row.add(createResponsiveGroup(160, 2, insertButton, removeButton), BorderLayout.SOUTH);
        return row;
    }

    private JPanel wrapTrailingComponent(JComponent component) {
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        wrap.setOpaque(false);
        wrap.add(component);
        return wrap;
    }

    private JCheckBox createBootRomCheckBox(BootRomSectionSpec spec, boolean bootRomInstalled) {
        JCheckBox checkBox = new JCheckBox(spec.checkboxText(), spec.settingEnabled());
        checkBox.setOpaque(false);
        checkBox.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        checkBox.setForeground(accentColour);
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

    private JLabel createInstallStatusBadge(boolean installed) {
        JLabel badge = createBadgeLabel(installed ? UiText.Common.INSTALLED : UiText.Common.MISSING);
        badge.setBackground(installed ? Styling.sectionHighlightColour : Styling.buttonSecondaryBackground);
        badge.setForeground(installed ? accentColour : mutedText);
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        installed ? Styling.sectionHighlightBorderColour : Styling.surfaceBorderColour,
                        1,
                        true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return badge;
    }

    private JPanel createInsetSurfaceCard(int verticalGap) {
        JPanel card = new JPanel(new BorderLayout(0, verticalGap));
        card.setOpaque(true);
        card.setBackground(Styling.surfaceColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.surfaceBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        return card;
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
        Color selectedColor = showDefaultColorChooser(UiText.OptionsWindow.ChooseColorTitle(label), initialColour);
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
        Color selectedColor = showDefaultColorChooser(UiText.OptionsWindow.GbcColorChooserTitle(), initialColour);
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
        Color selectedColor = showDefaultColorChooser(
                UiText.OptionsWindow.ChooseColorTitle(role.Label()),
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

    private Color showDefaultColorChooser(String title, Color initialColour) {
        LookAndFeel previousLookAndFeel = UIManager.getLookAndFeel();
        boolean switchedLookAndFeel = false;
        String[] colourKeys = {
                "ColorChooser.background",
                "ColorChooser.foreground",
                "Panel.background",
                "Panel.foreground",
                "TabbedPane.background",
                "TabbedPane.foreground",
                "Label.background",
                "Label.foreground",
                "TextField.background",
                "TextField.foreground",
                "TextField.caretForeground",
                "TextArea.background",
                "TextArea.foreground",
                "ComboBox.background",
                "ComboBox.foreground",
                "List.background",
                "List.foreground",
                "List.selectionBackground",
                "List.selectionForeground",
                "Button.background",
                "Button.foreground",
                "Slider.background",
                "Slider.foreground"
        };
        Map<String, Object> previousColours = new HashMap<>();
        try {
            String crossPlatformLookAndFeel = UIManager.getCrossPlatformLookAndFeelClassName();
            if (previousLookAndFeel == null
                    || !previousLookAndFeel.getClass().getName().equals(crossPlatformLookAndFeel)) {
                UIManager.setLookAndFeel(crossPlatformLookAndFeel);
                switchedLookAndFeel = true;
            }

            for (String key : colourKeys) {
                previousColours.put(key, UIManager.get(key));
            }
            UIManager.put("ColorChooser.background", Color.WHITE);
            UIManager.put("ColorChooser.foreground", Color.BLACK);
            UIManager.put("Panel.background", Color.WHITE);
            UIManager.put("Panel.foreground", Color.BLACK);
            UIManager.put("TabbedPane.background", Color.WHITE);
            UIManager.put("TabbedPane.foreground", Color.BLACK);
            UIManager.put("Label.background", Color.WHITE);
            UIManager.put("Label.foreground", Color.BLACK);
            UIManager.put("TextField.background", Color.WHITE);
            UIManager.put("TextField.foreground", Color.BLACK);
            UIManager.put("TextField.caretForeground", Color.BLACK);
            UIManager.put("TextArea.background", Color.WHITE);
            UIManager.put("TextArea.foreground", Color.BLACK);
            UIManager.put("ComboBox.background", Color.WHITE);
            UIManager.put("ComboBox.foreground", Color.BLACK);
            UIManager.put("List.background", Color.WHITE);
            UIManager.put("List.foreground", Color.BLACK);
            UIManager.put("List.selectionBackground", new Color(214, 228, 255));
            UIManager.put("List.selectionForeground", Color.BLACK);
            UIManager.put("Button.background", new Color(240, 240, 240));
            UIManager.put("Button.foreground", Color.BLACK);
            UIManager.put("Slider.background", Color.WHITE);
            UIManager.put("Slider.foreground", Color.BLACK);

            JColorChooser chooser = new JColorChooser(initialColour);
            final Color[] selectedColour = { null };
            JDialog dialog = JColorChooser.createDialog(
                    this,
                    title,
                    true,
                    chooser,
                    event -> selectedColour[0] = chooser.getColor(),
                    null);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
            return selectedColour[0];
        } catch (Exception exception) {
            return JColorChooser.showDialog(this, title, initialColour);
        } finally {
            for (Map.Entry<String, Object> entry : previousColours.entrySet()) {
                UIManager.put(entry.getKey(), entry.getValue());
            }
            if (switchedLookAndFeel && previousLookAndFeel != null) {
                try {
                    UIManager.setLookAndFeel(previousLookAndFeel);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean captureKeyboardBinding(EmulatorButton button) {
        JDialog dialog = new JDialog(this, UiText.OptionsWindow.RebindDialogTitle(formatControlButtonName(button)),
                true);
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

            Settings.inputBindings.SetKeyCode(backendProfile().backendId(), button, event.getKeyCode());
            refreshKeyboardBindingButtons();
            captured[0] = true;
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
                    Settings.controllerBindings.SetBinding(backendProfile().backendId(), button, binding);
                    refreshControllerBindingButtons();
                    refreshControllerStatus();
                    Config.Save();
                });
    }

    private boolean captureControllerInput(String dialogTitle, String dialogPrompt,
            Consumer<ControllerBinding> onCapture) {
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
                controllerDeadzoneValueLabel
                        .setText(UiText.OptionsWindow.PercentValue(Settings.controllerDeadzonePercent));
            }
            if (controllerPollingModeSelector != null
                    && controllerPollingModeSelector.getSelectedItem() != Settings.controllerPollingMode) {
                controllerPollingModeSelector.setSelectedItem(Settings.controllerPollingMode);
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

        ControllerInputService.ControllerLiveSnapshot liveSnapshot = latestControllerLiveSnapshot;
        if (liveSnapshot == null) {
            controllerStatusBadgeLabel.setText(UiText.OptionsWindow.CONTROLLER_STATUS_DISCONNECTED);
            controllerStatusHelperLabel.setText(UiText.OptionsWindow.CONTROLLER_STATUS_HELPER);
            controllerActiveValueLabel.setText(UiText.OptionsWindow.CONTROLLER_NONE_CONNECTED);
            setCompactReadout(controllerLiveInputsArea, UiText.OptionsWindow.CONTROLLER_LIVE_NONE);
            setCompactReadout(controllerMappedButtonsArea, Settings.controllerInputEnabled
                    ? UiText.OptionsWindow.CONTROLLER_MAPPED_NONE
                    : UiText.OptionsWindow.CONTROLLER_MAPPED_DISABLED);
            requestControllerStatusPoll();
            return;
        }

        Optional<ControllerInputService.ControllerDevice> activeController = liveSnapshot.activeController();
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
                ? liveSnapshot.activeInputs()
                : List.of();
        setCompactReadout(controllerLiveInputsArea, activeInputs.isEmpty()
                ? UiText.OptionsWindow.CONTROLLER_LIVE_NONE
                : JoinControllerBindings(activeInputs));

        if (!Settings.controllerInputEnabled) {
            setCompactReadout(controllerMappedButtonsArea, UiText.OptionsWindow.CONTROLLER_MAPPED_DISABLED);
            return;
        }

        Map<String, String> mappedPressedButtons = new java.util.LinkedHashMap<>();
        for (String buttonId : liveSnapshot.boundButtons()) {
            EmulatorButton button = controlButtonById(buttonId);
            mappedPressedButtons.put(buttonId, button == null ? buttonId : formatControlButtonName(button));
        }
        setCompactReadout(controllerMappedButtonsArea, mappedPressedButtons.isEmpty()
                ? UiText.OptionsWindow.CONTROLLER_MAPPED_NONE
                : String.join(", ", mappedPressedButtons.values()));
        requestControllerStatusPoll();
    }

    private void requestControllerStatusPoll() {
        if (!isControlsTabSelected() || !controllerStatusPollQueued.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture
                .supplyAsync(() -> controllerInputService.PollLiveSnapshot(backendProfile()))
                .whenComplete((snapshot, exception) -> SwingUtilities.invokeLater(() -> {
                    controllerStatusPollQueued.set(false);
                    if (exception == null && snapshot != null) {
                        latestControllerLiveSnapshot = snapshot;
                        refreshKnownControllerDevices();
                        applyControllerLiveSnapshot(snapshot);
                    }
                }));
    }

    private void applyControllerLiveSnapshot(ControllerInputService.ControllerLiveSnapshot liveSnapshot) {
        latestControllerLiveSnapshot = liveSnapshot;
        if (controllerStatusBadgeLabel == null || controllerStatusHelperLabel == null
                || controllerActiveValueLabel == null || controllerLiveInputsArea == null
                || controllerMappedButtonsArea == null) {
            return;
        }

        Optional<ControllerInputService.ControllerDevice> activeController = liveSnapshot.activeController();
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
                ? liveSnapshot.activeInputs()
                : List.of();
        setCompactReadout(controllerLiveInputsArea, activeInputs.isEmpty()
                ? UiText.OptionsWindow.CONTROLLER_LIVE_NONE
                : JoinControllerBindings(activeInputs));

        if (!Settings.controllerInputEnabled) {
            setCompactReadout(controllerMappedButtonsArea, UiText.OptionsWindow.CONTROLLER_MAPPED_DISABLED);
            return;
        }

        Map<String, String> mappedPressedButtons = new java.util.LinkedHashMap<>();
        for (String buttonId : liveSnapshot.boundButtons()) {
            EmulatorButton button = controlButtonById(buttonId);
            mappedPressedButtons.put(buttonId, button == null ? buttonId : formatControlButtonName(button));
        }
        setCompactReadout(controllerMappedButtonsArea, mappedPressedButtons.isEmpty()
                ? UiText.OptionsWindow.CONTROLLER_MAPPED_NONE
                : String.join(", ", mappedPressedButtons.values()));
    }

    private void refreshKnownControllerDevices() {
        syncControllerSelectorModel(controllerInputService.GetAvailableControllers());
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
            if (gbPaletteEditorSwatches[i] != null) {
                gbPaletteEditorSwatches[i].setBackground(palette[i].ToColour());
            }
            if (gbcCompatibilityPaletteSwatches[i] != null) {
                gbcCompatibilityPaletteSwatches[i].setBackground(palette[i].ToColour());
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

    private void refreshKeyboardBindingButtons() {
        for (EmulatorButton button : backendProfile().controlButtons()) {
            JButton bindingButton = keyboardBindingButtons.get(button);
            if (bindingButton != null) {
                bindingButton.setText(Settings.inputBindings.GetKeyText(backendProfile().backendId(), button));
            }
        }
    }

    private void refreshControllerBindingButtons() {
        for (EmulatorButton button : backendProfile().controlButtons()) {
            JButton bindingButton = controllerBindingButtons.get(button);
            if (bindingButton != null) {
                bindingButton.setText(Settings.controllerBindings.GetBindingText(backendProfile().backendId(), button));
            }
        }
    }

    private String formatControlButtonName(EmulatorButton button) {
        return backendProfile().controlButtonLabel(button);
    }

    private EmulatorButton controlButtonById(String buttonId) {
        if (buttonId == null || buttonId.isBlank()) {
            return null;
        }
        for (EmulatorButton button : backendProfile().controlButtons()) {
            if (buttonId.equals(button.id())) {
                return button;
            }
        }
        return null;
    }

    private EmulatorProfile backendProfile() {
        return mainWindow == null ? BackendRegistry.Default().Profile() : mainWindow.GetBackendProfile();
    }

    private JComponent wrapControllerDeadzoneControls() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.add(controllerDeadzoneSlider, BorderLayout.CENTER);
        panel.add(controllerDeadzoneValueLabel, BorderLayout.EAST);
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

    private JComponent createCompactWindowOptionCard(JComponent component) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
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

    private void configureCompactPaletteField(JTextField field) {
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        field.setPreferredSize(new Dimension(220, 32));
        field.setFont(Styling.menuFont.deriveFont(12f));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardBorder, 1, true),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
    }

    private void configureCompactPaletteSelector(JComboBox<?> selector) {
        selector.setPreferredSize(new Dimension(220, 30));
        selector.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
    }

    private void configureCompactPaletteButton(AbstractButton button, int width) {
        button.setFont(Styling.menuFont.deriveFont(Font.BOLD, 11f));
        button.setPreferredSize(new Dimension(width, 26));
    }

    private void configureCompactIconButton(AbstractButton button, int width, int height) {
        button.setFont(Styling.menuFont.deriveFont(Font.BOLD, 11f));
        button.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        button.setPreferredSize(new Dimension(width, height));
        button.setMinimumSize(new Dimension(width, height));
        button.setMaximumSize(new Dimension(width, height));
    }

    private JButton createCompactCloseGlyphButton(String tooltipText, int width, int height) {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D graphics2d = (Graphics2D) graphics.create();
                graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2d.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics2d.setColor(getForeground());
                int centreX = getWidth() / 2;
                int centreY = getHeight() / 2;
                graphics2d.drawLine(centreX - 4, centreY - 4, centreX + 4, centreY + 4);
                graphics2d.drawLine(centreX + 4, centreY - 4, centreX - 4, centreY + 4);
                graphics2d.dispose();
            }
        };
        WindowUiSupport.styleSecondaryButton(button, accentColour, cardBorder);
        button.setText("");
        button.setToolTipText(tooltipText);
        configureCompactIconButton(button, width, height);
        return button;
    }

    private void importSavedPalettes(boolean gbcPalette) {
        File importFile = promptForPaletteImportFile(gbcPalette);
        if (importFile == null) {
            return;
        }

        try {
            var mergeResult = gbcPalette
                    ? Config.ImportGbcPalettes(importFile.toPath())
                    : Config.ImportPalettes(importFile.toPath());
            JOptionPane.showMessageDialog(this,
                    UiText.PaletteManager.ImportSuccessMessage(
                            gbcPalette,
                            mergeResult.importedCount(),
                            mergeResult.duplicateCount()));
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.PaletteManager.IMPORT_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
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

    private void triggerDataBackup() {
        File backupFile = PromptForDataBundleFile(UiText.OptionsWindow.DATA_BUNDLE_SAVE_DIALOG_TITLE, FileDialog.SAVE);
        if (backupFile == null) {
            return;
        }

        Path backupPath = backupFile.toPath();
        if (!backupPath.getFileName().toString().toLowerCase().endsWith(GameDuckDataBundleManager.bundleExtension)) {
            backupPath = backupPath.resolveSibling(backupPath.getFileName() + GameDuckDataBundleManager.bundleExtension);
        }

        try {
            GameDuckDataBundleManager.CreateBackup(backupPath);
            JOptionPane.showMessageDialog(this,
                    UiText.OptionsWindow.DataBackupSuccessMessage(backupPath.toString()));
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.OptionsWindow.DATA_BUNDLE_BACKUP_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void triggerDataRestore() {
        File backupFile = PromptForDataBundleFile(UiText.OptionsWindow.DATA_BUNDLE_RESTORE_DIALOG_TITLE, FileDialog.LOAD);
        if (backupFile == null) {
            return;
        }

        try {
            GameDuckDataBundleManager.RestoreBackup(backupFile.toPath());
            if (mainWindow != null) {
                mainWindow.RefreshLibraryCollections();
                mainWindow.RefreshLoadedRomDisplay();
                mainWindow.RefreshWindowPanels();
                mainWindow.RefreshDisplayShader();
                mainWindow.RefreshDisplayBorder();
            }
            JOptionPane.showMessageDialog(this,
                    UiText.OptionsWindow.DataRestoreSuccessMessage(backupFile.getAbsolutePath()));
            reopenWithCurrentTab();
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.OptionsWindow.DATA_BUNDLE_RESTORE_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void confirmAndRunDataAction(String title, String message, DataAction action, String errorTitle) {
        int result = JOptionPane.showConfirmDialog(
                this,
                message,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            action.run();
            reopenWithCurrentTab();
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), errorTitle, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resetPreferencesData() throws IOException {
        GameDuckDataBundleManager.ResetPreferences();
        if (mainWindow != null) {
            mainWindow.RefreshLoadedRomDisplay();
            mainWindow.RefreshWindowPanels();
            mainWindow.RefreshDisplayShader();
            mainWindow.RefreshDisplayBorder();
        }
    }

    private void resetShaderData() throws IOException {
        GameDuckDataBundleManager.ResetShaders();
        if (mainWindow != null) {
            mainWindow.RefreshDisplayShader();
        }
    }

    private void deleteLibraryData() throws IOException {
        GameDuckDataBundleManager.DeleteLibrary();
        if (mainWindow != null) {
            mainWindow.RefreshLibraryCollections();
        }
    }

    private void deleteAllSaveData() throws IOException {
        GameDuckDataBundleManager.DeleteAllSaveData();
    }

    private void deleteEverythingData() throws IOException {
        GameDuckDataBundleManager.DeleteEverything();
        if (mainWindow != null) {
            mainWindow.RefreshLibraryCollections();
            mainWindow.RefreshLoadedRomDisplay();
            mainWindow.RefreshWindowPanels();
            mainWindow.RefreshDisplayShader();
            mainWindow.RefreshDisplayBorder();
        }
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

    private File PromptForDataBundleFile(String title, int mode) {
        FileDialog fileDialog = new FileDialog(this, title, mode);
        fileDialog.setAlwaysOnTop(true);
        fileDialog.setFile("*" + GameDuckDataBundleManager.bundleExtension);
        fileDialog.setFilenameFilter((directory, name) -> name.toLowerCase().endsWith(GameDuckDataBundleManager.bundleExtension));
        fileDialog.setVisible(true);
        return fileDialog.getFiles().length == 0 ? null : fileDialog.getFiles()[0];
    }

    private File promptForPaletteImportFile(boolean gbcPalette) {
        FileDialog fileDialog = new FileDialog(this,
                UiText.PaletteManager.ImportDialogTitle(gbcPalette),
                FileDialog.LOAD);
        fileDialog.setAlwaysOnTop(true);
        fileDialog.setFilenameFilter((directory, name) -> name.toLowerCase().endsWith(".json"));
        fileDialog.setVisible(true);
        return fileDialog.getFiles().length == 0 ? null : fileDialog.getFiles()[0];
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

    @FunctionalInterface
    private interface DataAction {
        void run() throws IOException;
    }
}
