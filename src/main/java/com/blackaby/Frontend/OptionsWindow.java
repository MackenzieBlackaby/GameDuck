package com.blackaby.Frontend;

import com.blackaby.Backend.Emulation.DuckBackend;
import com.blackaby.Backend.Emulation.Graphics.GBColor;
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
import com.blackaby.Misc.AppThemePreset;
import com.blackaby.Misc.BootRomManager;
import com.blackaby.Misc.Config;
import com.blackaby.Misc.ControllerBinding;
import com.blackaby.Misc.GameArtDisplayMode;
import com.blackaby.Misc.GameNameBracketDisplayMode;
import com.blackaby.Misc.Settings;
import com.blackaby.Misc.UiText;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.IOException;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
    private final JPanel[] themeColorPreviews = new JPanel[AppThemeColorRole.values().length];
    private final JLabel[] themeColorHexLabels = new JLabel[AppThemeColorRole.values().length];
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
    private JTextField volumeValueField;

    public OptionsWindow(MainWindow mainWindow) {
        this(mainWindow, 0);
    }

    public OptionsWindow(MainWindow mainWindow, int initialTabIndex) {
        super(UiText.OptionsWindow.WINDOW_TITLE, 820, 720, false);
        this.mainWindow = mainWindow;
        this.initialTabIndex = initialTabIndex;
        this.panelBackground = Styling.appBackgroundColour;
        this.cardBackground = Styling.surfaceColour;
        this.cardBorder = Styling.surfaceBorderColour;
        this.accentColour = Styling.accentColour;
        this.mutedText = Styling.mutedTextColour;
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
        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setBackground(panelBackground);
        header.setBorder(BorderFactory.createEmptyBorder(24, 28, 12, 28));

        JLabel titleLabel = new JLabel(UiText.OptionsWindow.HEADER_TITLE);
        titleLabel.setFont(Styling.titleFont);
        titleLabel.setForeground(accentColour);

        JLabel subtitleLabel = new JLabel(UiText.OptionsWindow.HEADER_SUBTITLE);
        subtitleLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 14f));
        subtitleLabel.setForeground(mutedText);

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
        });
        if (initialTabIndex >= 0 && initialTabIndex < tabs.getTabCount()) {
            tabs.setSelectedIndex(initialTabIndex);
        }
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
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        return content;
    }

    private JPanel createSectionCard(String title, String description, JComponent body) {
        JPanel card = new JPanel(new BorderLayout(0, 18));
        card.setBackground(cardBackground);
        card.setBorder(createCardBorder());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 22f));
        titleLabel.setForeground(accentColour);

        header.add(titleLabel, BorderLayout.NORTH);
        if (shouldRenderUiText(description)) {
            JLabel descriptionLabel = new JLabel(description);
            descriptionLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
            descriptionLabel.setForeground(mutedText);
            header.add(descriptionLabel, BorderLayout.CENTER);
        }

        card.add(header, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
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

        JPanel actionCard = new JPanel(new BorderLayout(14, 0));
        actionCard.setOpaque(false);
        actionCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        JPanel textColumn = new JPanel();
        textColumn.setLayout(new BoxLayout(textColumn, BoxLayout.Y_AXIS));
        textColumn.setOpaque(false);

        JLabel paletteLabel = createFieldLabel(UiText.OptionsWindow.SAVE_CURRENT_PALETTE);
        JLabel helperLabel = new JLabel(UiText.OptionsWindow.SAVE_CURRENT_PALETTE_HELPER);
        helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helperLabel.setForeground(mutedText);

        JTextField paletteNameField = new JTextField();
        paletteNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        paletteNameField.setPreferredSize(new Dimension(240, 38));
        paletteNameField.setFont(Styling.menuFont.deriveFont(13f));
        paletteNameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardBorder, 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        textColumn.add(paletteLabel);
        if (shouldRenderUiText(UiText.OptionsWindow.SAVE_CURRENT_PALETTE_HELPER)) {
            textColumn.add(Box.createVerticalStrut(4));
            textColumn.add(helperLabel);
            textColumn.add(Box.createVerticalStrut(8));
        } else {
            textColumn.add(Box.createVerticalStrut(8));
        }
        textColumn.add(paletteNameField);

        JPanel buttonColumn = new JPanel(new GridLayout(1, 2, 8, 0));
        buttonColumn.setOpaque(false);

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

        buttonColumn.add(savePaletteButton);
        buttonColumn.add(loadPaletteButton);

        actionCard.add(textColumn, BorderLayout.CENTER);
        actionCard.add(buttonColumn, BorderLayout.EAST);

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

    private JComponent createThemePresetsPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        content.add(createThemePreviewBanner());
        content.add(Box.createVerticalStrut(12));

        JPanel grid = new JPanel(new GridLayout(2, 2, 10, 10));
        grid.setOpaque(false);
        for (AppThemePreset preset : AppThemePreset.values()) {
            grid.add(createThemePresetCard(preset));
        }

        content.add(grid);
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

        JPanel paletteGrid = new JPanel(new GridLayout(1, 3, 10, 0));
        paletteGrid.setOpaque(false);
        paletteGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        paletteGrid.add(createGbcPaletteRow(0, UiText.OptionsWindow.GBC_BACKGROUND_PALETTE_TITLE,
                UiText.OptionsWindow.GBC_BACKGROUND_PALETTE_HELPER,
                Settings.CurrentGbcBackgroundPalette()));
        paletteGrid.add(createGbcPaletteRow(1, UiText.OptionsWindow.GBC_SPRITE0_PALETTE_TITLE,
                UiText.OptionsWindow.GBC_SPRITE0_PALETTE_HELPER,
                Settings.CurrentGbcSpritePalette0()));
        paletteGrid.add(createGbcPaletteRow(2, UiText.OptionsWindow.GBC_SPRITE1_PALETTE_TITLE,
                UiText.OptionsWindow.GBC_SPRITE1_PALETTE_HELPER,
                Settings.CurrentGbcSpritePalette1()));

        JPanel actionCard = new JPanel(new BorderLayout(14, 0));
        actionCard.setOpaque(false);
        actionCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        actionCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textColumn = new JPanel();
        textColumn.setLayout(new BoxLayout(textColumn, BoxLayout.Y_AXIS));
        textColumn.setOpaque(false);

        JLabel paletteLabel = createFieldLabel(UiText.OptionsWindow.SAVE_CURRENT_GBC_PALETTE);
        JLabel helperLabel = new JLabel(UiText.OptionsWindow.SAVE_CURRENT_GBC_PALETTE_HELPER);
        helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helperLabel.setForeground(mutedText);

        JTextField paletteNameField = new JTextField();
        paletteNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        paletteNameField.setPreferredSize(new Dimension(240, 38));
        paletteNameField.setFont(Styling.menuFont.deriveFont(13f));
        paletteNameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardBorder, 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        textColumn.add(paletteLabel);
        if (shouldRenderUiText(UiText.OptionsWindow.SAVE_CURRENT_GBC_PALETTE_HELPER)) {
            textColumn.add(Box.createVerticalStrut(4));
            textColumn.add(helperLabel);
            textColumn.add(Box.createVerticalStrut(8));
        } else {
            textColumn.add(Box.createVerticalStrut(8));
        }
        textColumn.add(paletteNameField);

        JPanel buttonColumn = new JPanel(new GridLayout(1, 2, 8, 0));
        buttonColumn.setOpaque(false);

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

        buttonColumn.add(savePaletteButton);
        buttonColumn.add(loadPaletteButton);

        actionCard.add(textColumn, BorderLayout.CENTER);
        actionCard.add(buttonColumn, BorderLayout.EAST);

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
        JPanel toggleCard = new JPanel(new BorderLayout(14, 0));
        toggleCard.setBackground(Styling.sectionHighlightColour);
        toggleCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        JPanel toggleText = new JPanel();
        toggleText.setLayout(new BoxLayout(toggleText, BoxLayout.Y_AXIS));
        toggleText.setOpaque(false);

        JLabel toggleTitle = new JLabel(titleText);
        toggleTitle.setFont(Styling.menuFont.deriveFont(Font.BOLD, 16f));
        toggleTitle.setForeground(accentColour);
        toggleTitle.setHorizontalAlignment(SwingConstants.LEFT);
        toggleTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea toggleHelper = createWrappingTextArea(helperText);
        toggleHelper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        toggleHelper.setForeground(mutedText);
        toggleHelper.setAlignmentX(Component.LEFT_ALIGNMENT);

        toggleText.add(toggleTitle);
        toggleText.add(Box.createVerticalStrut(6));
        toggleText.add(toggleHelper);

        JPanel toggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toggleWrap.setOpaque(false);
        selector.setPreferredSize(new Dimension(220, 34));
        selector.setBackground(Color.WHITE);
        selector.setForeground(accentColour);
        toggleWrap.add(selector);

        toggleCard.add(toggleText, BorderLayout.CENTER);
        toggleCard.add(toggleWrap, BorderLayout.EAST);
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
            JLabel helper = new JLabel(helperText);
            helper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
            helper.setForeground(mutedText);
            text.add(Box.createVerticalStrut(3));
            text.add(helper);
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

    private JComponent createThemeColorsPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JPanel grid = new JPanel(new GridLayout(3, 2, 10, 10));
        grid.setOpaque(false);

        AppTheme currentTheme = Settings.CurrentAppTheme();
        for (AppThemeColorRole role : AppThemeColorRole.values()) {
            grid.add(createThemeColorCard(role, currentTheme));
        }

        content.add(grid);
        content.add(Box.createVerticalStrut(16));

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
        actions.add(resetThemeButton);
        content.add(actions);
        return content;
    }

    private JComponent createThemeLibraryPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JComponent previewBanner = createThemePreviewBanner();
        previewBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(previewBanner);
        content.add(Box.createVerticalStrut(12));

        JPanel actionCard = new JPanel(new BorderLayout(14, 0));
        actionCard.setOpaque(false);
        actionCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        actionCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textColumn = new JPanel();
        textColumn.setLayout(new BoxLayout(textColumn, BoxLayout.Y_AXIS));
        textColumn.setOpaque(false);

        JLabel themeLabel = createFieldLabel(UiText.OptionsWindow.SAVE_CURRENT_THEME);
        JLabel helperLabel = new JLabel(UiText.OptionsWindow.SAVE_CURRENT_THEME_HELPER);
        helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helperLabel.setForeground(mutedText);

        JTextField themeNameField = new JTextField();
        themeNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        themeNameField.setPreferredSize(new Dimension(240, 38));
        themeNameField.setFont(Styling.menuFont.deriveFont(13f));
        themeNameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardBorder, 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        textColumn.add(themeLabel);
        textColumn.add(Box.createVerticalStrut(4));
        textColumn.add(helperLabel);
        textColumn.add(Box.createVerticalStrut(8));
        textColumn.add(themeNameField);

        JPanel buttonColumn = new JPanel(new GridLayout(1, 2, 8, 0));
        buttonColumn.setOpaque(false);

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

        buttonColumn.add(saveThemeButton);
        buttonColumn.add(browseThemesButton);

        actionCard.add(textColumn, BorderLayout.CENTER);
        actionCard.add(buttonColumn, BorderLayout.EAST);

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
        JPanel card = new JPanel(new BorderLayout(16, 0));
        card.setBackground(Styling.sectionHighlightColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));

        JPanel textBlock = new JPanel();
        textBlock.setOpaque(false);
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(UiText.OptionsWindow.INPUT_MAPPER_LAUNCH_TITLE);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 18f));
        titleLabel.setForeground(accentColour);

        JLabel descriptionLabel = new JLabel("<html><body style='width: 460px'>"
                + WindowUiSupport.escapeHtml(UiText.OptionsWindow.INPUT_MAPPER_LAUNCH_DESCRIPTION)
                + "</body></html>");
        descriptionLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        descriptionLabel.setForeground(mutedText);

        textBlock.add(titleLabel);
        textBlock.add(Box.createVerticalStrut(6));
        textBlock.add(descriptionLabel);

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

        card.add(textBlock, BorderLayout.CENTER);
        card.add(actionColumn, BorderLayout.EAST);
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

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(controllerEnabledCheckBox, BorderLayout.WEST);
        topRow.add(refreshControllerButton, BorderLayout.EAST);

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

        JPanel grid = new JPanel(new GridLayout(0, 2, 12, 12));
        grid.setOpaque(false);
        grid.add(createFieldCard(UiText.OptionsWindow.CONTROLLER_SELECTION_LABEL, controllerSelector));
        grid.add(createFieldCard(UiText.OptionsWindow.CONTROLLER_ACTIVE_LABEL, controllerActiveValueLabel));
        grid.add(createFieldCard(UiText.OptionsWindow.CONTROLLER_DEADZONE_LABEL, wrapControllerDeadzoneControls()));
        grid.add(createFieldCard(UiText.OptionsWindow.CONTROLLER_STATUS_LABEL, wrapControllerStatusControls()));

        card.add(topRow, BorderLayout.NORTH);
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JComponent createControllerLiveTesterCard() {
        JPanel card = new JPanel(new GridLayout(1, 2, 12, 0));
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        controllerLiveInputsArea = createCompactReadoutLabel(UiText.OptionsWindow.CONTROLLER_LIVE_NONE);
        controllerMappedButtonsArea = createCompactReadoutLabel(UiText.OptionsWindow.CONTROLLER_MAPPED_NONE);

        card.add(createFieldCard(UiText.OptionsWindow.CONTROLLER_LIVE_INPUTS_LABEL, controllerLiveInputsArea));
        card.add(createFieldCard(UiText.OptionsWindow.CONTROLLER_MAPPED_BUTTONS_LABEL, controllerMappedButtonsArea));
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

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);

        stack.add(createBindingIntroCard(
                UiText.OptionsWindow.WINDOW_SHORTCUTS_TITLE,
                UiText.OptionsWindow.WINDOW_SHORTCUTS_DESCRIPTION,
                UiText.OptionsWindow.WINDOW_SHORTCUTS_BADGE));
        stack.add(Box.createVerticalStrut(10));

        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
        grid.setOpaque(false);

        for (AppShortcut shortcut : AppShortcut.values()) {
            grid.add(createShortcutCard(shortcut));
        }

        stack.add(grid);
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
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JLabel titleLabel = new JLabel(shortcut.Label());
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(accentColour);

        JLabel helperLabel = new JLabel("<html><body style='width: 132px'>" + shortcut.Description() + "</body></html>");
        helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helperLabel.setForeground(mutedText);

        JPanel labelPanel = new JPanel(new BorderLayout(0, 4));
        labelPanel.setOpaque(false);
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(titleLabel, BorderLayout.WEST);
        titleRow.add(createBadgeLabel(UiText.OptionsWindow.APP_BADGE), BorderLayout.EAST);
        labelPanel.add(titleRow, BorderLayout.NORTH);
        labelPanel.add(helperLabel, BorderLayout.CENTER);

        JButton keyboardButton = createPrimaryButton(Settings.appShortcutBindings.GetKeyText(shortcut));
        keyboardButton.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        keyboardButton.setPreferredSize(new Dimension(124, 34));
        keyboardButton.addActionListener(event -> captureShortcut(shortcut));
        shortcutButtons.put(shortcut, keyboardButton);

        JButton controllerButton = createSecondaryButton(Settings.appShortcutControllerBindings.GetBindingText(shortcut));
        controllerButton.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        controllerButton.setPreferredSize(new Dimension(124, 32));
        controllerButton.addActionListener(event -> captureControllerShortcut(shortcut));
        controllerShortcutButtons.put(shortcut, controllerButton);

        JPanel buttonColumn = new JPanel();
        buttonColumn.setLayout(new BoxLayout(buttonColumn, BoxLayout.Y_AXIS));
        buttonColumn.setOpaque(false);
        buttonColumn.add(createShortcutBindingRow(UiText.OptionsWindow.SHORTCUT_KEYBOARD_LABEL, keyboardButton));
        buttonColumn.add(Box.createVerticalStrut(8));
        buttonColumn.add(createShortcutBindingRow(UiText.OptionsWindow.SHORTCUT_CONTROLLER_LABEL, controllerButton));

        card.add(labelPanel, BorderLayout.CENTER);
        card.add(buttonColumn, BorderLayout.EAST);
        return card;
    }

    private JComponent createShortcutBindingRow(String labelText, JButton actionButton) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);

        JLabel rowLabel = new JLabel(labelText, SwingConstants.CENTER);
        rowLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 11f));
        rowLabel.setForeground(mutedText);

        row.add(rowLabel, BorderLayout.NORTH);
        row.add(actionButton, BorderLayout.CENTER);
        return row;
    }

    private JComponent createActionBindingCard(String title, String helperText, String badgeText,
                                               boolean wrapHelperText, String buttonText, Dimension buttonSize,
                                               float buttonFontSize, int verticalPadding, int horizontalPadding,
                                               Runnable action, Consumer<JButton> buttonRegistrar) {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(verticalPadding, horizontalPadding, verticalPadding, horizontalPadding)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(accentColour);

        JLabel helperLabel = new JLabel(wrapHelperText
                ? "<html><body style='width: 132px'>" + helperText + "</body></html>"
                : helperText);
        helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helperLabel.setForeground(mutedText);

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
        labelPanel.add(helperLabel, BorderLayout.CENTER);

        JButton actionButton = createPrimaryButton(buttonText);
        actionButton.setFont(Styling.menuFont.deriveFont(Font.BOLD, buttonFontSize));
        actionButton.setPreferredSize(buttonSize);
        actionButton.addActionListener(event -> action.run());
        buttonRegistrar.accept(actionButton);

        JPanel buttonWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonWrap.setOpaque(false);
        buttonWrap.add(actionButton);

        card.add(labelPanel, BorderLayout.CENTER);
        card.add(buttonWrap, BorderLayout.EAST);
        return card;
    }

    private JComponent createBindingIntroCard(String title, String description, String badgeText) {
        JPanel card = new JPanel(new BorderLayout(14, 0));
        card.setBackground(Styling.sectionHighlightColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel textBlock = new JPanel(new BorderLayout(0, 6));
        textBlock.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(accentColour);

        JLabel descriptionLabel = new JLabel("<html><body style='width: 360px'>" + description + "</body></html>");
        descriptionLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        descriptionLabel.setForeground(mutedText);

        textBlock.add(titleLabel, BorderLayout.NORTH);
        textBlock.add(descriptionLabel, BorderLayout.CENTER);

        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        badgeWrap.setOpaque(false);
        badgeWrap.add(createBadgeLabel(badgeText));

        card.add(textBlock, BorderLayout.CENTER);
        card.add(badgeWrap, BorderLayout.EAST);
        return card;
    }

    private JComponent createPalettePreviewBanner() {
        JPanel card = new JPanel(new BorderLayout(18, 0));
        card.setBackground(Styling.sectionHighlightColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        JPanel textBlock = new JPanel();
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        textBlock.setOpaque(false);

        JLabel title = new JLabel(UiText.OptionsWindow.ACTIVE_DMG_PALETTE_TITLE);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 16f));
        title.setForeground(accentColour);

        JLabel helper = new JLabel(UiText.OptionsWindow.ACTIVE_DMG_PALETTE_HELPER);
        helper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helper.setForeground(mutedText);

        textBlock.add(title);
        textBlock.add(Box.createVerticalStrut(4));
        textBlock.add(helper);

        JPanel swatchStrip = new JPanel(new GridLayout(1, 4, 8, 0));
        swatchStrip.setOpaque(false);

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

        card.add(textBlock, BorderLayout.CENTER);
        card.add(swatchStrip, BorderLayout.EAST);
        return card;
    }

    private JComponent createThemePreviewBanner() {
        JPanel card = new JPanel(new BorderLayout(18, 0));
        card.setBackground(Styling.sectionHighlightColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        JPanel textBlock = new JPanel();
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        textBlock.setOpaque(false);

        JLabel title = new JLabel(UiText.OptionsWindow.ACTIVE_APP_THEME_TITLE);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 16f));
        title.setForeground(accentColour);

        JLabel helper = new JLabel(UiText.OptionsWindow.ACTIVE_APP_THEME_HELPER);
        helper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helper.setForeground(mutedText);

        textBlock.add(title);
        textBlock.add(Box.createVerticalStrut(4));
        textBlock.add(helper);

        JPanel swatchStrip = new JPanel(new GridLayout(1, AppThemeColorRole.values().length, 6, 0));
        swatchStrip.setOpaque(false);

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

        card.add(textBlock, BorderLayout.CENTER);
        card.add(swatchStrip, BorderLayout.EAST);
        return card;
    }

    private JComponent createThemePresetCard(AppThemePreset preset) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JLabel title = new JLabel(preset.Label());
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        title.setForeground(accentColour);

        JLabel helper = new JLabel("<html><body style='width: 220px'>" + preset.Description() + "</body></html>");
        helper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helper.setForeground(mutedText);

        JPanel swatches = new JPanel(new GridLayout(1, AppThemeColorRole.values().length, 6, 0));
        swatches.setOpaque(false);
        AppTheme theme = preset.Theme();
        for (AppThemeColorRole role : AppThemeColorRole.values()) {
            JPanel swatch = new JPanel();
            swatch.setPreferredSize(new Dimension(28, 28));
            swatch.setBackground(theme.CoreColour(role));
            swatch.setBorder(BorderFactory.createLineBorder(new Color(58, 92, 132, 60), 1, true));
            swatches.add(swatch);
        }

        JButton applyButton = createSecondaryButton(UiText.OptionsWindow.APPLY_THEME_BUTTON);
        applyButton.addActionListener(event -> {
            Settings.ApplyAppTheme(preset.Theme());
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshTheme();
            }
            reopenWithCurrentTab();
        });

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(helper);

        card.add(header, BorderLayout.NORTH);
        card.add(swatches, BorderLayout.CENTER);
        card.add(applyButton, BorderLayout.SOUTH);
        return card;
    }

    private JComponent createThemeColorCard(AppThemeColorRole role, AppTheme theme) {
        JPanel card = new JPanel(new BorderLayout(10, 0));
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JLabel title = new JLabel(role.Label());
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        title.setForeground(accentColour);

        JLabel helper = new JLabel(role.Description());
        helper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helper.setForeground(mutedText);

        JPanel preview = new JPanel(new BorderLayout());
        preview.setPreferredSize(new Dimension(58, 58));
        preview.setMinimumSize(new Dimension(58, 58));
        preview.setBackground(theme.CoreColour(role));
        preview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(58, 92, 132, 45), 1, true),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        themeColorPreviews[role.ordinal()] = preview;

        JLabel hexLabel = new JLabel(theme.CoreHex(role));
        hexLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 11f));
        hexLabel.setForeground(accentColour);
        hexLabel.setOpaque(true);
        hexLabel.setBackground(new Color(255, 255, 255, 180));
        hexLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(160, 186, 216), 1, true),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        themeColorHexLabels[role.ordinal()] = hexLabel;

        JPanel hexWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        hexWrap.setOpaque(false);
        hexWrap.add(hexLabel);
        preview.add(hexWrap, BorderLayout.SOUTH);

        JButton chooseButton = createSecondaryButton(UiText.OptionsWindow.CHOOSE_BUTTON);
        chooseButton.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        chooseButton.setPreferredSize(new Dimension(92, 34));
        chooseButton.addActionListener(event -> chooseThemeColor(role));

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);
        details.add(title);
        details.add(Box.createVerticalStrut(2));
        details.add(helper);

        JPanel rightColumn = new JPanel();
        rightColumn.setLayout(new BoxLayout(rightColumn, BoxLayout.Y_AXIS));
        rightColumn.setOpaque(false);
        preview.setAlignmentX(Component.CENTER_ALIGNMENT);
        chooseButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightColumn.add(preview);
        rightColumn.add(Box.createVerticalStrut(8));
        rightColumn.add(chooseButton);

        card.add(details, BorderLayout.CENTER);
        card.add(rightColumn, BorderLayout.EAST);
        return card;
    }

    private JComponent createSoundPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 18));
        container.setOpaque(false);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);

        JCheckBox[] channelMuteCheckBoxes = new JCheckBox[4];
        JSlider[] channelVolumeSliders = new JSlider[4];
        JLabel[] channelVolumeLabels = new JLabel[4];
        DefaultListModel<AudioEnhancementPreset> enhancementChainModel = new DefaultListModel<>();
        for (AudioEnhancementPreset preset : Settings.CurrentAudioEnhancementChain()) {
            enhancementChainModel.addElement(preset);
        }

        JCheckBox soundEnabledCheckBox = new JCheckBox(UiText.OptionsWindow.SOUND_ENABLED_CHECKBOX,
                Settings.soundEnabled);
        soundEnabledCheckBox.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        soundEnabledCheckBox.setForeground(accentColour);
        soundEnabledCheckBox.setBackground(Styling.sectionHighlightColour);
        soundEnabledCheckBox.addActionListener(event -> {
            Settings.soundEnabled = soundEnabledCheckBox.isSelected();
            Config.Save();
        });

        JPanel outputCard = new JPanel(new BorderLayout(14, 0));
        outputCard.setBackground(Styling.sectionHighlightColour);
        outputCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel outputText = new JPanel();
        outputText.setLayout(new BoxLayout(outputText, BoxLayout.Y_AXIS));
        outputText.setOpaque(false);

        JLabel outputTitle = new JLabel(UiText.OptionsWindow.PLAYBACK_TITLE);
        outputTitle.setFont(Styling.menuFont.deriveFont(Font.BOLD, 16f));
        outputTitle.setForeground(accentColour);

        JLabel outputHelper = new JLabel(
                "<html><body style='width: 360px'>" + UiText.OptionsWindow.PLAYBACK_HELPER + "</body></html>");
        outputHelper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        outputHelper.setForeground(mutedText);

        outputText.add(outputTitle);
        outputText.add(Box.createVerticalStrut(6));
        outputText.add(outputHelper);

        JPanel toggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toggleWrap.setOpaque(false);
        toggleWrap.add(soundEnabledCheckBox);

        outputCard.add(outputText, BorderLayout.CENTER);
        outputCard.add(toggleWrap, BorderLayout.EAST);
        stack.add(outputCard);
        stack.add(Box.createVerticalStrut(10));

        JPanel volumeCard = new JPanel(new BorderLayout(0, 14));
        volumeCard.setBackground(Styling.cardTintColour);
        volumeCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel volumeHeader = new JPanel(new BorderLayout(12, 0));
        volumeHeader.setOpaque(false);

        JPanel volumeText = new JPanel();
        volumeText.setLayout(new BoxLayout(volumeText, BoxLayout.Y_AXIS));
        volumeText.setOpaque(false);

        JLabel volumeTitle = new JLabel(UiText.OptionsWindow.MASTER_VOLUME_TITLE);
        volumeTitle.setFont(Styling.menuFont.deriveFont(Font.BOLD, 15f));
        volumeTitle.setForeground(accentColour);

        JLabel volumeHelper = new JLabel(UiText.OptionsWindow.MASTER_VOLUME_HELPER);
        volumeHelper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        volumeHelper.setForeground(mutedText);

        volumeText.add(volumeTitle);
        volumeText.add(Box.createVerticalStrut(4));
        volumeText.add(volumeHelper);

        volumeValueField = new JTextField(UiText.OptionsWindow.PercentValue(Settings.masterVolume));
        volumeValueField.setFont(Styling.menuFont.deriveFont(Font.BOLD, 18f));
        volumeValueField.setForeground(accentColour);
        volumeValueField.setOpaque(true);
        volumeValueField.setBackground(Styling.surfaceColour);
        volumeValueField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        volumeValueField.setHorizontalAlignment(SwingConstants.CENTER);
        FontMetrics volumeMetrics = volumeValueField.getFontMetrics(volumeValueField.getFont());
        Dimension volumeBadgeSize = new Dimension(volumeMetrics.stringWidth("100%") + 28,
                volumeMetrics.getHeight() + 20);
        volumeValueField.setPreferredSize(volumeBadgeSize);
        volumeValueField.setMinimumSize(volumeBadgeSize);

        volumeHeader.add(volumeText, BorderLayout.CENTER);
        volumeHeader.add(volumeValueField, BorderLayout.EAST);

        JSlider volumeSlider = new JSlider(0, 100, Settings.masterVolume);
        volumeSlider.setOpaque(false);
        volumeSlider.setForeground(accentColour);
        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setMinorTickSpacing(5);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintLabels(true);

        JPanel rangeRow = new JPanel(new BorderLayout());
        rangeRow.setOpaque(false);

        JLabel lowLabel = new JLabel(UiText.OptionsWindow.QUIET_LABEL);
        lowLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 11f));
        lowLabel.setForeground(mutedText);

        JLabel highLabel = new JLabel(UiText.OptionsWindow.LOUD_LABEL);
        highLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 11f));
        highLabel.setForeground(mutedText);

        rangeRow.add(lowLabel, BorderLayout.WEST);
        rangeRow.add(highLabel, BorderLayout.EAST);

        volumeSlider.addChangeListener(event -> {
            Settings.masterVolume = volumeSlider.getValue();
            refreshVolumeLabel();
            if (!volumeSlider.getValueIsAdjusting()) {
                Config.Save();
            }
        });

        volumeValueField.addActionListener(event -> commitVolumeInput(volumeSlider));
        volumeValueField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                commitVolumeInput(volumeSlider);
            }
        });

        volumeCard.add(volumeHeader, BorderLayout.NORTH);
        volumeCard.add(volumeSlider, BorderLayout.CENTER);
        volumeCard.add(rangeRow, BorderLayout.SOUTH);
        stack.add(volumeCard);
        stack.add(Box.createVerticalStrut(10));

        JPanel channelCard = new JPanel(new BorderLayout(0, 12));
        channelCard.setBackground(Styling.cardTintColour);
        channelCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        JPanel channelHeader = new JPanel(new BorderLayout());
        channelHeader.setOpaque(false);

        JLabel channelTitle = new JLabel(UiText.OptionsWindow.CHANNEL_MIXER_TITLE);
        channelTitle.setFont(Styling.menuFont.deriveFont(Font.BOLD, 15f));
        channelTitle.setForeground(accentColour);

        JLabel channelHelper = new JLabel(UiText.OptionsWindow.CHANNEL_MIXER_HELPER);
        channelHelper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        channelHelper.setForeground(mutedText);

        JPanel channelText = new JPanel();
        channelText.setLayout(new BoxLayout(channelText, BoxLayout.Y_AXIS));
        channelText.setOpaque(false);
        channelText.add(channelTitle);
        channelText.add(Box.createVerticalStrut(4));
        channelText.add(channelHelper);

        channelHeader.add(channelText, BorderLayout.CENTER);

        JPanel channelGrid = new JPanel(new GridBagLayout());
        channelGrid.setOpaque(false);
        GridBagConstraints channelGbc = new GridBagConstraints();
        channelGbc.gridy = 0;
        channelGbc.insets = new Insets(0, 0, 8, 0);
        channelGbc.fill = GridBagConstraints.HORIZONTAL;
        channelGbc.weightx = 1.0;

        for (int channelIndex = 0; channelIndex < 4; channelIndex++) {
            channelGrid.add(
                    createChannelMixerRow(channelIndex, channelMuteCheckBoxes, channelVolumeSliders,
                            channelVolumeLabels),
                    channelGbc);
            channelGbc.gridy++;
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
            volumeSlider.setValue(Settings.masterVolume);
            refreshVolumeLabel();
            for (int channelIndex = 0; channelIndex < 4; channelIndex++) {
                if (channelMuteCheckBoxes[channelIndex] != null) {
                    channelMuteCheckBoxes[channelIndex].setSelected(Settings.IsChannelMuted(channelIndex));
                }
                if (channelVolumeSliders[channelIndex] != null) {
                    channelVolumeSliders[channelIndex].setValue(Settings.GetChannelVolume(channelIndex));
                }
                refreshChannelVolumeLabel(channelIndex, channelVolumeLabels);
            }
            enhancementChainModel.clear();
            enhancementEnabledCheckBox.setSelected(Settings.IsAudioEnhancementChainEnabled());
            Config.Save();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        actions.setOpaque(false);
        actions.add(resetSoundButton);
        container.add(actions, BorderLayout.SOUTH);

        return container;
    }

    private JComponent createAudioEnhancementCard(DefaultListModel<AudioEnhancementPreset> enhancementChainModel,
            JCheckBox enhancementEnabledCheckBox) {
        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setBackground(Styling.cardTintColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel title = new JLabel(UiText.OptionsWindow.AUDIO_ENHANCEMENTS_TITLE);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 15f));
        title.setForeground(accentColour);

        JLabel helper = new JLabel(
                "<html><body style='width: 520px'>" + UiText.OptionsWindow.AUDIO_ENHANCEMENTS_HELPER
                        + "</body></html>");
        helper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helper.setForeground(mutedText);

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(helper);

        JPanel topRow = new JPanel(new BorderLayout(8, 0));
        topRow.setOpaque(false);
        topRow.add(header, BorderLayout.CENTER);

        JPanel toggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toggleWrap.setOpaque(false);
        toggleWrap.add(enhancementEnabledCheckBox);
        topRow.add(toggleWrap, BorderLayout.EAST);

        JPanel addCard = new JPanel(new BorderLayout(12, 0));
        addCard.setOpaque(true);
        addCard.setBackground(new Color(255, 255, 255, 135));
        addCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JPanel addText = new JPanel();
        addText.setLayout(new BoxLayout(addText, BoxLayout.Y_AXIS));
        addText.setOpaque(false);

        JLabel addTitle = new JLabel(UiText.OptionsWindow.ADD_PRESET_TITLE);
        addTitle.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        addTitle.setForeground(accentColour);

        JComboBox<AudioEnhancementPreset> presetSelector = new JComboBox<>(AudioEnhancementPreset.values());
        presetSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        presetSelector.setBackground(Styling.surfaceColour);
        presetSelector.setForeground(accentColour);
        presetSelector.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel presetDescription = new JLabel();
        presetDescription.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        presetDescription.setForeground(mutedText);
        updateAudioEnhancementDescription(presetSelector, presetDescription);
        presetSelector.addActionListener(event -> updateAudioEnhancementDescription(presetSelector, presetDescription));

        addText.add(addTitle);
        addText.add(Box.createVerticalStrut(8));
        addText.add(presetSelector);
        addText.add(Box.createVerticalStrut(8));
        addText.add(presetDescription);

        JButton addButton = createSecondaryButton(UiText.OptionsWindow.ADD_TO_CHAIN_BUTTON);
        addButton.addActionListener(event -> {
            Object selectedPreset = presetSelector.getSelectedItem();
            if (!(selectedPreset instanceof AudioEnhancementPreset enhancementPreset)) {
                return;
            }

            enhancementChainModel.addElement(enhancementPreset);
            applyAudioEnhancementModel(enhancementChainModel);
        });

        JPanel addButtonWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        addButtonWrap.setOpaque(false);
        addButtonWrap.add(addButton);

        addCard.add(addText, BorderLayout.CENTER);
        addCard.add(addButtonWrap, BorderLayout.EAST);

        JList<AudioEnhancementPreset> chainList = new JList<>(enhancementChainModel);
        chainList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chainList.setBackground(Styling.surfaceColour);
        chainList.setForeground(accentColour);
        chainList.setSelectionBackground(Styling.listSelectionColour);
        chainList.setSelectionForeground(accentColour);
        chainList.setFixedCellHeight(30);
        chainList.setBorder(BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true));
        chainList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
                        cellHasFocus);
                if (value instanceof AudioEnhancementPreset preset) {
                    label.setText(UiText.OptionsWindow.AudioChainItemLabel(index, preset.Label()));
                }
                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return label;
            }
        });

        JScrollPane chainScrollPane = new JScrollPane(chainList);
        chainScrollPane.setBorder(BorderFactory.createEmptyBorder());
        chainScrollPane.getViewport().setBackground(Styling.surfaceColour);
        chainScrollPane.setPreferredSize(new Dimension(0, 128));

        JPanel chainControls = new JPanel(new GridLayout(4, 1, 0, 8));
        chainControls.setOpaque(false);

        JButton moveUpButton = createSecondaryButton(UiText.OptionsWindow.MOVE_UP_BUTTON);
        moveUpButton.addActionListener(event -> moveAudioEnhancement(enhancementChainModel, chainList, -1));

        JButton moveDownButton = createSecondaryButton(UiText.OptionsWindow.MOVE_DOWN_BUTTON);
        moveDownButton.addActionListener(event -> moveAudioEnhancement(enhancementChainModel, chainList, 1));

        JButton removeButton = createSecondaryButton(UiText.OptionsWindow.REMOVE_BUTTON);
        removeButton.addActionListener(event -> {
            int selectedIndex = chainList.getSelectedIndex();
            if (selectedIndex < 0) {
                return;
            }

            enhancementChainModel.remove(selectedIndex);
            if (!enhancementChainModel.isEmpty()) {
                chainList.setSelectedIndex(Math.min(selectedIndex, enhancementChainModel.size() - 1));
            }
            applyAudioEnhancementModel(enhancementChainModel);
        });

        JButton clearButton = createSecondaryButton(UiText.OptionsWindow.CLEAR_CHAIN_BUTTON);
        clearButton.addActionListener(event -> {
            if (enhancementChainModel.isEmpty()) {
                return;
            }

            enhancementChainModel.clear();
            applyAudioEnhancementModel(enhancementChainModel);
        });

        chainControls.add(moveUpButton);
        chainControls.add(moveDownButton);
        chainControls.add(removeButton);
        chainControls.add(clearButton);

        JPanel chainCard = new JPanel(new BorderLayout(12, 0));
        chainCard.setOpaque(false);

        JPanel chainText = new JPanel();
        chainText.setLayout(new BoxLayout(chainText, BoxLayout.Y_AXIS));
        chainText.setOpaque(false);

        JLabel chainTitle = new JLabel(UiText.OptionsWindow.ACTIVE_CHAIN_TITLE);
        chainTitle.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        chainTitle.setForeground(accentColour);

        JLabel chainHelper = new JLabel(UiText.OptionsWindow.ACTIVE_CHAIN_HELPER);
        chainHelper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        chainHelper.setForeground(mutedText);

        chainText.add(chainTitle);
        if (shouldRenderUiText(UiText.OptionsWindow.ACTIVE_CHAIN_HELPER)) {
            chainText.add(Box.createVerticalStrut(4));
            chainText.add(chainHelper);
        }

        JPanel chainPanel = new JPanel(new BorderLayout(0, 8));
        chainPanel.setOpaque(false);
        chainPanel.add(chainText, BorderLayout.NORTH);
        chainPanel.add(chainScrollPane, BorderLayout.CENTER);

        chainCard.add(chainPanel, BorderLayout.CENTER);
        chainCard.add(chainControls, BorderLayout.EAST);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.add(addCard);
        body.add(Box.createVerticalStrut(12));
        body.add(chainCard);

        card.add(topRow, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent createChannelMixerRow(int channelIndex, JCheckBox[] channelMuteCheckBoxes,
            JSlider[] channelVolumeSliders, JLabel[] channelVolumeLabels) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, 8);
        gbc.anchor = GridBagConstraints.CENTER;

        JLabel channelLabel = new JLabel(channelName(channelIndex));
        channelLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        channelLabel.setForeground(accentColour);
        channelLabel.setPreferredSize(new Dimension(78, 24));
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        row.add(channelLabel, gbc);

        JCheckBox muteCheckBox = new JCheckBox(UiText.OptionsWindow.MUTE_CHECKBOX,
                Settings.IsChannelMuted(channelIndex));
        muteCheckBox.setOpaque(false);
        muteCheckBox.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        muteCheckBox.setForeground(accentColour);
        muteCheckBox.addActionListener(event -> {
            Settings.SetChannelMuted(channelIndex, muteCheckBox.isSelected());
            Config.Save();
        });
        channelMuteCheckBoxes[channelIndex] = muteCheckBox;
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        row.add(muteCheckBox, gbc);

        JSlider channelSlider = new JSlider(0, 100, Settings.GetChannelVolume(channelIndex));
        channelSlider.setOpaque(false);
        channelSlider.setFocusable(false);
        channelSlider.setMajorTickSpacing(25);
        channelSlider.setMinorTickSpacing(5);
        channelSlider.setPaintTicks(false);
        channelSlider.setPaintLabels(false);
        channelSlider.addChangeListener(event -> {
            Settings.SetChannelVolume(channelIndex, channelSlider.getValue());
            refreshChannelVolumeLabel(channelIndex, channelVolumeLabels);
            if (!channelSlider.getValueIsAdjusting()) {
                Config.Save();
            }
        });
        channelVolumeSliders[channelIndex] = channelSlider;
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        row.add(channelSlider, gbc);

        JLabel valueLabel = new JLabel();
        valueLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        valueLabel.setForeground(accentColour);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        valueLabel.setPreferredSize(new Dimension(40, 24));
        channelVolumeLabels[channelIndex] = valueLabel;
        refreshChannelVolumeLabel(channelIndex, channelVolumeLabels);
        gbc.gridx = 3;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 4, 0, 0);
        row.add(valueLabel, gbc);

        return row;
    }

    private void refreshChannelVolumeLabel(int channelIndex, JLabel[] channelVolumeLabels) {
        JLabel label = channelVolumeLabels[channelIndex];
        if (label != null) {
            label.setText(UiText.OptionsWindow.ChannelVolumeLabel(Settings.GetChannelVolume(channelIndex)));
        }
    }

    private String channelName(int channelIndex) {
        return UiText.OptionsWindow.ChannelName(channelIndex);
    }

    private void updateAudioEnhancementDescription(JComboBox<AudioEnhancementPreset> presetSelector,
            JLabel descriptionLabel) {
        Object selectedPreset = presetSelector.getSelectedItem();
        if (descriptionLabel == null) {
            return;
        }

        if (selectedPreset instanceof AudioEnhancementPreset enhancementPreset) {
            descriptionLabel.setText("<html>" + enhancementPreset.Description() + "</html>");
        } else {
            descriptionLabel.setText("<html>" + UiText.OptionsWindow.PRESET_DESCRIPTION_PLACEHOLDER + "</html>");
        }
    }

    private void applyAudioEnhancementModel(DefaultListModel<AudioEnhancementPreset> enhancementChainModel) {
        List<AudioEnhancementPreset> chain = new ArrayList<>();
        for (int index = 0; index < enhancementChainModel.size(); index++) {
            chain.add(enhancementChainModel.getElementAt(index));
        }
        Settings.SetAudioEnhancementChain(chain);
        Config.Save();
    }

    private void moveAudioEnhancement(DefaultListModel<AudioEnhancementPreset> enhancementChainModel,
            JList<AudioEnhancementPreset> chainList, int direction) {
        int selectedIndex = chainList.getSelectedIndex();
        if (selectedIndex < 0) {
            return;
        }

        int targetIndex = selectedIndex + direction;
        if (targetIndex < 0 || targetIndex >= enhancementChainModel.size()) {
            return;
        }

        AudioEnhancementPreset selectedPreset = enhancementChainModel.getElementAt(selectedIndex);
        enhancementChainModel.remove(selectedIndex);
        enhancementChainModel.add(targetIndex, selectedPreset);
        chainList.setSelectedIndex(targetIndex);
        applyAudioEnhancementModel(enhancementChainModel);
    }

    private JComponent createWindowPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 14));
        container.setOpaque(false);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);

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
        stack.add(createSimpleWindowOptionCard(fillWindowCheckBox));
        stack.add(Box.createVerticalStrut(10));

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
        stack.add(createSimpleWindowOptionCard(integerScaleCheckBox));
        stack.add(Box.createVerticalStrut(10));

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
        stack.add(createSimpleWindowOptionCard(serialOutputCheckBox));
        stack.add(Box.createVerticalStrut(10));

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
        stack.add(createSelectorWindowOptionCard(UiText.OptionsWindow.GAME_ART_MODE_LABEL, gameArtModeSelector));
        stack.add(Box.createVerticalStrut(10));

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

        stack.add(createSelectorWindowOptionCard(UiText.OptionsWindow.DISPLAY_BORDER_LABEL, borderSelector));
        stack.add(Box.createVerticalStrut(10));

        JPanel borderPreviewCard = new JPanel(new BorderLayout(10, 0));
        borderPreviewCard.setOpaque(false);
        borderPreviewCard.add(createFieldCard(UiText.OptionsWindow.BORDER_PREVIEW_LABEL, borderPreviewSurface),
                BorderLayout.WEST);

        JPanel borderDetailStack = new JPanel();
        borderDetailStack.setLayout(new BoxLayout(borderDetailStack, BoxLayout.Y_AXIS));
        borderDetailStack.setOpaque(false);
        borderDetailStack.add(createFieldCard(UiText.OptionsWindow.BORDER_SOURCE_LABEL, borderSourceValueLabel));
        borderDetailStack.add(Box.createVerticalStrut(8));
        borderDetailStack.add(createFieldCard(UiText.OptionsWindow.BORDER_PATH_LABEL, borderPathValueLabel));
        borderDetailStack.add(Box.createVerticalStrut(8));
        borderDetailStack.add(createFieldCard(UiText.OptionsWindow.BORDER_CUTOUT_LABEL, borderCutoutValueLabel));
        borderDetailStack.add(Box.createVerticalStrut(8));
        borderDetailStack.add(createFieldCard(UiText.OptionsWindow.BORDER_STATUS_LABEL, borderStatusValueLabel));
        borderPreviewCard.add(borderDetailStack, BorderLayout.CENTER);
        stack.add(createSimpleWindowOptionCard(borderPreviewCard));

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

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        actions.setOpaque(false);
        actions.add(borderManagerButton);
        actions.add(resetWindowButton);
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

        JPanel detailStack = new JPanel();
        detailStack.setLayout(new BoxLayout(detailStack, BoxLayout.Y_AXIS));
        detailStack.setOpaque(false);
        detailStack.add(createFieldCard(UiText.OptionsWindow.SHADER_DESCRIPTION_LABEL, descriptionArea));
        detailStack.add(Box.createVerticalStrut(8));
        detailStack.add(createFieldCard(UiText.OptionsWindow.SHADER_SOURCE_LABEL, sourceValueLabel));
        detailStack.add(Box.createVerticalStrut(8));
        detailStack.add(createFieldCard(UiText.OptionsWindow.SHADER_PATH_LABEL, pathValueLabel));
        detailStack.add(Box.createVerticalStrut(8));
        detailStack.add(createFieldCard(UiText.OptionsWindow.SHADER_STATUS_LABEL, statusValueLabel));
        stack.add(createSimpleWindowOptionCard(detailStack));

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

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        actions.setOpaque(false);
        actions.add(shaderEditorButton);
        actions.add(reloadShadersButton);
        actions.add(openFolderButton);
        actions.add(resetShaderButton);
        container.add(actions, BorderLayout.SOUTH);

        refreshShaderSelector.run();
        return container;
    }

    private JComponent createLibraryPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = baseConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        JLabel modeLabel = createFieldLabel(UiText.OptionsWindow.LIBRARY_MODE_LABEL);
        panel.add(modeLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(8, 0, 12, 0);
        JComboBox<GameNameBracketDisplayMode> modeSelector = new JComboBox<>(GameNameBracketDisplayMode.values());
        modeSelector.setSelectedItem(Settings.gameNameBracketDisplayMode);
        modeSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        modeSelector.addActionListener(event -> {
            Object selectedItem = modeSelector.getSelectedItem();
            if (!(selectedItem instanceof GameNameBracketDisplayMode selectedMode)) {
                return;
            }

            Settings.gameNameBracketDisplayMode = selectedMode;
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshLoadedRomDisplay();
            }
        });
        panel.add(modeSelector, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 16, 0);
        JLabel helperLabel = new JLabel("<html>" + Settings.gameNameBracketDisplayMode.Description() + "</html>");
        helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        helperLabel.setForeground(mutedText);
        panel.add(helperLabel, gbc);

        modeSelector.addActionListener(event -> {
            Object selectedItem = modeSelector.getSelectedItem();
            if (selectedItem instanceof GameNameBracketDisplayMode selectedMode) {
                helperLabel.setText("<html>" + selectedMode.Description() + "</html>");
            }
        });

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 0);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1.0;
        JLabel recentMenuLimitLabel = createFieldLabel(UiText.OptionsWindow.RECENT_MENU_LIMIT_LABEL);
        panel.add(recentMenuLimitLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(8, 0, 12, 0);
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
        panel.add(recentMenuLimitSpinner, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 16, 0);
        JLabel recentMenuHelperLabel = new JLabel(UiText.OptionsWindow.RECENT_MENU_LIMIT_HELPER);
        recentMenuHelperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        recentMenuHelperLabel.setForeground(mutedText);
        panel.add(recentMenuHelperLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(12, 0, 0, 0);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.weightx = 0.0;
        JButton resetLibraryButton = createSecondaryButton(UiText.OptionsWindow.RESET_LIBRARY_BUTTON);
        resetLibraryButton.addActionListener(event -> {
            Settings.ResetLibrary();
            modeSelector.setSelectedItem(Settings.gameNameBracketDisplayMode);
            helperLabel.setText("<html>" + Settings.gameNameBracketDisplayMode.Description() + "</html>");
            recentMenuLimitSpinner.setValue(Settings.loadRecentMenuLimit);
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshLoadedRomDisplay();
            }
        });
        panel.add(resetLibraryButton, gbc);

        return panel;
    }

    private JComponent createSaveDataSection() {
        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setOpaque(false);

        int trackedGameCount = ManagedGameRegistry.GetKnownGames().size();

        JPanel titleCard = new JPanel(new BorderLayout(14, 0));
        titleCard.setBackground(Styling.sectionHighlightColour);
        titleCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel titleText = new JPanel();
        titleText.setLayout(new BoxLayout(titleText, BoxLayout.Y_AXIS));
        titleText.setOpaque(false);

        JLabel titleLabel = new JLabel(UiText.OptionsWindow.SAVE_DATA_TITLE);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(accentColour);

        JLabel helperLabel = new JLabel(
                "<html><body style='width: 360px'>" + UiText.OptionsWindow.SAVE_DATA_DESCRIPTION + "</body></html>");
        helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helperLabel.setForeground(mutedText);

        titleText.add(titleLabel);
        titleText.add(Box.createVerticalStrut(6));
        titleText.add(helperLabel);

        JLabel badgeLabel = createBadgeLabel(UiText.OptionsWindow.SaveManagerTrackedGamesBadge(trackedGameCount));

        titleCard.add(titleText, BorderLayout.CENTER);
        titleCard.add(badgeLabel, BorderLayout.EAST);

        JPanel detailsCard = new JPanel(new BorderLayout(0, 14));
        detailsCard.setBackground(Styling.cardTintColour);
        detailsCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel detailsStack = new JPanel();
        detailsStack.setLayout(new BoxLayout(detailsStack, BoxLayout.Y_AXIS));
        detailsStack.setOpaque(false);

        detailsStack.add(createSaveDataDetailCard(
                UiText.OptionsWindow.SAVE_MANAGER_LAUNCH_TITLE,
                UiText.OptionsWindow.SAVE_MANAGER_LAUNCH_HELPER,
                trackedGameCount == 0
                        ? UiText.OptionsWindow.SAVE_MANAGER_EMPTY_TITLE
                        : UiText.OptionsWindow.SaveManagerTrackedGamesBadge(trackedGameCount)));
        detailsStack.add(Box.createVerticalStrut(10));

        detailsStack.add(createSaveDataDetailCard(
                UiText.OptionsWindow.SAVE_DATA_MANAGED_PATH_TITLE,
                UiText.OptionsWindow.SAVE_MANAGER_SUBTITLE,
                SaveFileManager.SaveDirectoryPath().toString()));

        detailsCard.add(detailsStack, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);

        JButton openManagerButton = createPrimaryButton(UiText.OptionsWindow.SAVE_MANAGER_OPEN_BUTTON);
        openManagerButton.addActionListener(event -> new SaveDataManagerWindow(mainWindow));
        actions.add(openManagerButton);

        container.add(titleCard, BorderLayout.NORTH);
        container.add(detailsCard, BorderLayout.CENTER);
        container.add(actions, BorderLayout.SOUTH);
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

        JLabel value = new JLabel("<html>" + escapeHtml(valueText) + "</html>");
        value.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        value.setForeground(accentColour);
        valueStack.add(value);

        if (helperText != null && !helperText.isBlank()) {
            valueStack.add(Box.createVerticalStrut(4));
            JLabel helper = new JLabel("<html>" + helperText + "</html>");
            helper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
            helper.setForeground(mutedText);
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
        JPanel card = new JPanel(new BorderLayout(14, 0));
        card.setBackground(Styling.sectionHighlightColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel titleLabel = new JLabel(spec.bootTitle());
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(accentColour);

        JLabel helperLabel = new JLabel("<html><body style='width: 360px'>" + spec.bootHelper() + "</body></html>");
        helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helperLabel.setForeground(mutedText);

        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(6));
        textPanel.add(helperLabel);

        JPanel toggleWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toggleWrap.setOpaque(false);
        toggleWrap.add(useBootRomCheckBox);

        card.add(textPanel, BorderLayout.CENTER);
        card.add(toggleWrap, BorderLayout.EAST);
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
        JPanel statusRow = new JPanel(new BorderLayout(12, 0));
        statusRow.setOpaque(false);

        JPanel statusText = new JPanel();
        statusText.setLayout(new BoxLayout(statusText, BoxLayout.Y_AXIS));
        statusText.setOpaque(false);

        JLabel statusTitle = new JLabel(title);
        statusTitle.setFont(Styling.menuFont.deriveFont(Font.BOLD, 15f));
        statusTitle.setForeground(accentColour);
        statusText.add(statusTitle);
        if (shouldRenderUiText(helper)) {
            JLabel statusHelper = new JLabel(helper);
            statusHelper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
            statusHelper.setForeground(mutedText);
            statusText.add(Box.createVerticalStrut(4));
            statusText.add(statusHelper);
        }

        statusRow.add(statusText, BorderLayout.CENTER);
        statusRow.add(createInstallStatusBadge(installed), BorderLayout.EAST);
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
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonRow.setOpaque(false);

        JButton insertButton = createPrimaryButton(spec.insertButtonText());
        insertButton.addActionListener(event -> installBootRom(spec));
        buttonRow.add(insertButton);

        JButton removeButton = createSecondaryButton(spec.removeButtonText());
        removeButton.addActionListener(event -> removeBootRom(spec));
        buttonRow.add(removeButton);
        return buttonRow;
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
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 16));
        footer.setBackground(panelBackground);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 20, 8, 20));

        JButton closeButton = createPrimaryButton(UiText.OptionsWindow.CLOSE_BUTTON);
        closeButton.addActionListener(event -> dispose());
        footer.add(closeButton);
        return footer;
    }

    private GridBagConstraints baseConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 12, 12);
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
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
            if (themeColorPreviews[index] != null) {
                themeColorPreviews[index].setBackground(theme.CoreColour(role));
            }
            if (themeColorHexLabels[index] != null) {
                themeColorHexLabels[index].setText(theme.CoreHex(role));
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

    private void refreshVolumeLabel() {
        if (volumeValueField != null) {
            volumeValueField.setText(UiText.OptionsWindow.PercentValue(Settings.masterVolume));
        }
    }

    private void commitVolumeInput(JSlider volumeSlider) {
        if (volumeValueField == null || volumeSlider == null) {
            return;
        }

        String rawText = volumeValueField.getText();
        String numericText = rawText == null ? "" : rawText.replace("%", "").trim();
        int value;

        try {
            value = Integer.parseInt(numericText);
        } catch (NumberFormatException exception) {
            refreshVolumeLabel();
            return;
        }

        value = Math.max(0, Math.min(100, value));
        Settings.masterVolume = value;
        if (volumeSlider.getValue() != value) {
            volumeSlider.setValue(value);
        }
        refreshVolumeLabel();
        Config.Save();
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
        return mainWindow == null ? DuckBackend.instance.Profile() : mainWindow.GetBackendProfile();
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
