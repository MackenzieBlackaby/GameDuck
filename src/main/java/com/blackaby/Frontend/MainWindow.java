package com.blackaby.Frontend;

import com.blackaby.Backend.GB.GBBackendManager;
import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.Helpers.GameMetadataStore;
import com.blackaby.Backend.Helpers.GUIActions;
import com.blackaby.Backend.Helpers.GUIActions.Action;
import com.blackaby.Backend.Helpers.GameArtProvider;
import com.blackaby.Backend.Helpers.GameArtProvider.GameArtResult;
import com.blackaby.Backend.Helpers.GameLibraryStore;
import com.blackaby.Backend.Helpers.QuickStateManager;
import com.blackaby.Backend.Helpers.GameNotesStore;
import com.blackaby.Backend.Platform.EmulatorBackend;
import com.blackaby.Backend.Platform.EmulatorGame;
import com.blackaby.Backend.Platform.EmulatorHost;
import com.blackaby.Backend.Platform.EmulatorRuntime;
import com.blackaby.Backend.Platform.EmulatorStateSlot;
import com.blackaby.Misc.AppShortcut;
import com.blackaby.Misc.Config;
import com.blackaby.Misc.GameArtDisplayMode;
import com.blackaby.Misc.GameNameBracketDisplayMode;
import com.blackaby.Misc.Settings;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JTextArea;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.border.Border;
import java.awt.BasicStroke;
import java.awt.CardLayout;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.Cursor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hosts the main emulator window and its desktop controls.
 * <p>
 * The window owns the display surface, menu bar, status strips, and the quick
 * actions used to drive the active emulator instance.
 */
public class MainWindow extends DuckWindow implements EmulatorHost {

    private static final String DISPLAY_STAGE_EMPTY = "empty";
    private static final String DISPLAY_STAGE_ACTIVE = "active";
    private static final int gameArtPreviewWidth = 280;
    private static final int gameArtPreviewHeight = 220;
    private static final int recentGameTileMinSize = 150;
    private static final int recentGameTileMaxSize = 230;
    private static final int displayStatsRefreshMillis = 500;
    private static final DateTimeFormatter saveStateTimestampFormatter = DateTimeFormatter
            .ofPattern("dd MMM yyyy HH:mm");

    private final EmulatorBackend backend;
    private final DuckDisplay display;
    private final EmulatorRuntime emulation;
    private final InputRouter inputRouter;
    private final List<JButton> headerButtons = new ArrayList<>();
    private final EnumMap<Action, JMenuItem> menuItemsByAction = new EnumMap<>(Action.class);
    private final AtomicInteger gameArtRequestVersion = new AtomicInteger();
    private final AtomicBoolean serialAppendQueued = new AtomicBoolean();
    private final Map<String, CachedGameArt> cachedGameArt = new ConcurrentHashMap<>();
    private final StringBuilder pendingSerialAppend = new StringBuilder();
    private final DebugLogger.SerialListener serialOutputListener = new DebugLogger.SerialListener() {
        @Override
        public void SerialOutputAppended(String text) {
            AppendSerialOutput(text);
        }

        @Override
        public void SerialOutputCleared() {
            ClearSerialOutput();
        }
    };
    private JButton fullViewButton;
    private JMenu recentGamesMenu;
    private JMenu saveStateMenu;
    private JMenu loadStateMenu;
    private JMenuItem cheatManagerMenuItem;
    private final JMenuItem[] saveStateSlotItems = new JMenuItem[QuickStateManager.maxSlot + 1];
    private final JMenuItem[] loadStateSlotItems = new JMenuItem[QuickStateManager.maxSlot + 1];
    private final Timer displayStatsTimer;
    private volatile String displayedGameArtCacheKey;
    private volatile String lastDisplayStatsText = UiText.MainWindow.DISPLAY_HINT;
    private volatile boolean recentGamesMenuDirty = true;
    private volatile int cachedRecentGamesMenuLimit = -1;

    private JMenuBar menuBar;
    private JLabel romLabel;
    private JLabel stateLabel;
    private JLabel displayTitleLabel;
    private JLabel displayHintLabel;
    private JLabel serialTitleLabel;
    private JLabel serialHintLabel;
    private JLabel gameNotesTitleLabel;
    private JLabel gameNotesHintLabel;
    private JLabel gameArtTitleLabel;
    private JLabel gameArtHintLabel;
    private JLabel gameArtLabel;
    private JPanel embeddedDisplaySurface;
    private JPanel displayWrapper;
    private JPanel displayCard;
    private JPanel displayStagePanel;
    private JPanel displayEmptyStateContent;
    private JPanel recentGamesPanel;
    private JPanel serialCard;
    private JPanel serialSectionPanel;
    private JPanel gameNotesPanel;
    private JPanel gameArtPanel;
    private JPanel gameArtPreviewPanel;
    private JComponent sidePanelSpacer;
    private JComponent notesArtSpacer;
    private JPanel labelRow;
    private JPanel displayFrame;
    private JPanel statusBar;
    private JTextArea serialOutputArea;
    private JTextArea gameNotesArea;
    private JScrollPane serialOutputScrollPane;
    private JScrollPane gameNotesScrollPane;
    private JButton gameNotesEditButton;
    private EmulatorGame currentLoadedGame;
    private boolean romNameLookupPending;
    private boolean gameNotesEditing;

    private final String[][] menuItems = UiText.MainWindow.MENU_ITEMS;

    private final Action[][] menuActions = {
            { Action.OPTIONS, Action.EXIT },
            { Action.LIBRARY, Action.LOADROM, Action.LOADIPS, Action.PAUSEGAME, Action.RESETGAME, Action.CLOSEGAME,
                    Action.SAVESTATE, Action.LOADSTATE },
            { Action.FULL_VIEW, Action.FULLSCREEN, Action.MAXIMISE },
            { Action.ABOUT }
    };

    /**
     * Creates the main host window and initialises the attached emulator.
     */
    public MainWindow() {
        super(UiText.MainWindow.WINDOW_TITLE);

        backend = GBBackendManager.Current();
        display = new DuckDisplay(backend.Profile().displaySpec());
        emulation = backend.CreateRuntime(this, display);
        inputRouter = new InputRouter(this, emulation, backend.Profile());
        inputRouter.Install();

        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(Styling.appBackgroundColour);

        displayWrapper = (JPanel) BuildDisplaySection();
        statusBar = (JPanel) BuildStatusBar();

        add(displayWrapper, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        menuBar = new JMenuBar();
        menuBar.setBackground(Styling.surfaceColour);
        menuBar.setForeground(Styling.accentColour);
        menuBar.setFont(Styling.menuFont);
        menuBar.setBorder(createMenuBarBorder());

        for (int index = 0; index < menuItems.length; index++) {
            if (UiText.MainWindow.GAME_MENU_TITLE.equals(menuItems[index][0])) {
                menuBar.add(BuildGameMenu());
            } else {
                AddMenu(menuBar, menuItems[index], menuActions[index]);
            }
        }

        setJMenuBar(menuBar);
        DebugLogger.AddSerialListener(serialOutputListener);
        SetSerialOutput(DebugLogger.GetSerialOutput());
        displayStatsTimer = new Timer(displayStatsRefreshMillis, event -> RefreshDisplayStats());
        displayStatsTimer.start();
        RefreshAppShortcuts();
        ApplyWindowMode();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                SaveCurrentGameNotes();
                displayStatsTimer.stop();
                DebugLogger.RemoveSerialListener(serialOutputListener);
                inputRouter.Uninstall();
                DebugLogger.Shutdown();
                emulation.StopEmulation();
                display.Shutdown();
                dispose();
                System.exit(0);
            }
        });
        setVisible(true);
        updateDisplayStage();
    }

    private JComponent BuildDisplaySection() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(24, 24, 0, 24));

        JPanel content = new JPanel(new BorderLayout(18, 0));
        content.setOpaque(false);

        displayCard = new JPanel(new BorderLayout(0, 16));
        displayCard.setBackground(Styling.surfaceColour);
        displayCard.setBorder(createSurfaceCardBorder());

        labelRow = new JPanel(new BorderLayout());
        labelRow.setOpaque(false);

        displayTitleLabel = new JLabel(UiText.MainWindow.DISPLAY_TITLE);
        displayTitleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 16f));
        displayTitleLabel.setForeground(Styling.accentColour);

        displayHintLabel = new JLabel(UiText.MainWindow.DISPLAY_HINT);
        displayHintLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        displayHintLabel.setForeground(Styling.mutedTextColour);

        labelRow.add(displayTitleLabel, BorderLayout.WEST);
        labelRow.add(displayHintLabel, BorderLayout.EAST);

        displayFrame = new JPanel(new BorderLayout());
        displayFrame.setBackground(Styling.displayFrameColour);
        displayFrame.setBorder(createDisplayFrameBorder());
        displayFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                UpdateRecentGameTileSizes();
            }
        });

        embeddedDisplaySurface = new JPanel(new BorderLayout());
        embeddedDisplaySurface.setOpaque(false);
        embeddedDisplaySurface.add(display, BorderLayout.CENTER);

        displayStagePanel = new JPanel(new CardLayout());
        displayStagePanel.setOpaque(false);
        displayStagePanel.add(BuildDisplayEmptyState(), DISPLAY_STAGE_EMPTY);
        displayStagePanel.add(embeddedDisplaySurface, DISPLAY_STAGE_ACTIVE);

        displayFrame.add(displayStagePanel, BorderLayout.CENTER);

        displayCard.add(labelRow, BorderLayout.NORTH);
        displayCard.add(displayFrame, BorderLayout.CENTER);
        serialCard = (JPanel) BuildSerialOutputPanel();

        content.add(displayCard, BorderLayout.CENTER);
        content.add(serialCard, BorderLayout.EAST);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    private JComponent BuildDisplayEmptyState() {
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        panel.setOpaque(true);
        panel.setBackground(Styling.displayFrameColour);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(34, 28, 34, 28));
        displayEmptyStateContent = content;

        JLabel logoLabel = new JLabel(AppAssets.HeaderLogoIcon(72));
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel(UiText.MainWindow.DISPLAY_EMPTY_TITLE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(Styling.titleFont.deriveFont(Font.BOLD, 28f));
        title.setForeground(Styling.accentColour);

        JLabel helper = new JLabel("<html><div style='text-align:center;width:320px;'>"
                + WindowUiSupport.escapeHtml(UiText.MainWindow.DISPLAY_EMPTY_HELPER)
                + "</div></html>");
        helper.setAlignmentX(Component.CENTER_ALIGNMENT);
        helper.setHorizontalAlignment(SwingConstants.CENTER);
        helper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 14f));
        helper.setForeground(Styling.mutedTextColour);

        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 10, 0));
        actions.setOpaque(false);
        actions.add(CreateHeaderButton(UiText.MainWindow.BUTTON_OPEN_ROM, Action.LOADROM, true));
        actions.add(CreateHeaderButton(UiText.MainWindow.BUTTON_OPEN_ROM_HACK, Action.LOADIPS));
        actions.add(CreateHeaderButton(UiText.MainWindow.BUTTON_LIBRARY, Action.LIBRARY));

        recentGamesPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 12, 0));
        recentGamesPanel.setOpaque(false);
        recentGamesPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        if (logoLabel.getIcon() != null) {
            content.add(logoLabel);
            content.add(javax.swing.Box.createVerticalStrut(16));
        }
        content.add(title);
        content.add(javax.swing.Box.createVerticalStrut(10));
        content.add(helper);
        content.add(javax.swing.Box.createVerticalStrut(18));
        content.add(actions);
        content.add(javax.swing.Box.createVerticalStrut(16));
        content.add(recentGamesPanel);

        panel.add(content);
        return panel;
    }

    private void RefreshFullViewRecentGames() {
        if (recentGamesPanel == null) {
            return;
        }

        int recentGameLimit = Math.max(1, Math.min(5, Settings.readyPageRecentGameLimit));
        recentGamesPanel.removeAll();
        List<GameLibraryStore.LibraryEntry> recentEntries = uniqueRecentEntries(recentGameLimit);
        recentGamesPanel.setVisible(!recentEntries.isEmpty());
        int tileSize = CalculateRecentGameTileSize();
        for (GameLibraryStore.LibraryEntry entry : recentEntries) {
            recentGamesPanel.add(new RecentGameTile(entry, tileSize));
        }
        recentGamesPanel.revalidate();
        recentGamesPanel.repaint();
        if (displayFrame != null) {
            displayFrame.repaint();
        }
    }

    private List<GameLibraryStore.LibraryEntry> uniqueRecentEntries(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<GameLibraryStore.LibraryEntry> uniqueEntries = new ArrayList<>(limit);
        for (GameLibraryStore.LibraryEntry entry : GameLibraryStore.GetRecentEntries(limit * 2)) {
            boolean alreadyAdded = uniqueEntries.stream()
                    .anyMatch(existingEntry -> Objects.equals(existingEntry.key(), entry.key())
                            || SameGameIdentity(existingEntry, entry));
            if (alreadyAdded) {
                continue;
            }
            uniqueEntries.add(entry);
            if (uniqueEntries.size() >= limit) {
                break;
            }
        }
        return uniqueEntries;
    }

    private void UpdateRecentGameTileSizes() {
        if (recentGamesPanel == null) {
            return;
        }

        int tileSize = CalculateRecentGameTileSize();
        for (Component component : recentGamesPanel.getComponents()) {
            if (component instanceof RecentGameTile recentGameTile) {
                recentGameTile.UpdateTileSize(tileSize);
            }
        }
        recentGamesPanel.revalidate();
        recentGamesPanel.repaint();
    }

    public void RefreshReadyPageRecentGames() {
        RefreshFullViewRecentGames();
    }

    private int CalculateRecentGameTileSize() {
        int width = displayFrame == null || displayFrame.getWidth() <= 0 ? getWidth() : displayFrame.getWidth();
        int height = displayFrame == null || displayFrame.getHeight() <= 0 ? getHeight() : displayFrame.getHeight();
        int recentGameLimit = Math.max(1, Math.min(5, Settings.readyPageRecentGameLimit));
        int widthDrivenSize = Math.max(recentGameTileMinSize, (width - 160) / recentGameLimit);
        int heightDrivenSize = Math.max(recentGameTileMinSize, height / 4);
        return Math.max(recentGameTileMinSize,
                Math.min(recentGameTileMaxSize, Math.min(widthDrivenSize, heightDrivenSize)));
    }

    private JComponent BuildSerialOutputPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setPreferredSize(new Dimension(320, 0));
        panel.setBackground(Styling.surfaceColour);
        panel.setBorder(createSurfaceCardBorder());

        serialSectionPanel = new JPanel(new BorderLayout(0, 14));
        serialSectionPanel.setOpaque(false);

        JPanel titleRow = new JPanel(new BorderLayout(0, 4));
        titleRow.setOpaque(false);

        serialTitleLabel = new JLabel(UiText.MainWindow.SERIAL_TITLE);
        serialTitleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 16f));
        serialTitleLabel.setForeground(Styling.accentColour);

        serialHintLabel = new JLabel(UiText.MainWindow.SERIAL_HINT);
        serialHintLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        serialHintLabel.setForeground(Styling.mutedTextColour);

        titleRow.add(serialTitleLabel, BorderLayout.NORTH);
        titleRow.add(serialHintLabel, BorderLayout.CENTER);

        serialOutputArea = new JTextArea();
        serialOutputArea.setEditable(false);
        serialOutputArea.setFocusable(false);
        serialOutputArea.setLineWrap(true);
        serialOutputArea.setWrapStyleWord(true);
        serialOutputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        serialOutputArea.setBackground(Styling.displayFrameColour);
        serialOutputArea.setForeground(Styling.fpsForegroundColour);
        serialOutputArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        serialOutputScrollPane = new JScrollPane(serialOutputArea);
        serialOutputScrollPane.setBorder(WindowUiSupport.createLineBorder(Styling.displayFrameBorderColour));
        serialOutputScrollPane.getViewport().setBackground(Styling.displayFrameColour);
        serialOutputScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        serialSectionPanel.add(titleRow, BorderLayout.NORTH);
        serialSectionPanel.add(serialOutputScrollPane, BorderLayout.CENTER);

        gameNotesPanel = (JPanel) BuildGameNotesPanel();
        gameArtPanel = (JPanel) BuildGameArtPanel();
        sidePanelSpacer = CreateSidePanelSpacer();
        notesArtSpacer = CreateSidePanelSpacer();

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        serialSectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidePanelSpacer.setAlignmentX(Component.LEFT_ALIGNMENT);
        gameNotesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        notesArtSpacer.setAlignmentX(Component.LEFT_ALIGNMENT);
        gameArtPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(serialSectionPanel);
        body.add(sidePanelSpacer);
        body.add(gameNotesPanel);
        body.add(notesArtSpacer);
        body.add(gameArtPanel);

        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private JComponent CreateSidePanelSpacer() {
        JPanel spacerPanel = new JPanel();
        spacerPanel.setOpaque(false);
        spacerPanel.setPreferredSize(new Dimension(0, 14));
        spacerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        spacerPanel.setMinimumSize(new Dimension(0, 14));
        return spacerPanel;
    }

    private JComponent BuildGameNotesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.setOpaque(false);

        JPanel titleBlock = new JPanel(new BorderLayout(0, 4));
        titleBlock.setOpaque(false);

        gameNotesTitleLabel = new JLabel(UiText.MainWindow.GAME_NOTES_TITLE);
        gameNotesTitleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 15f));
        gameNotesTitleLabel.setForeground(Styling.accentColour);

        gameNotesHintLabel = new JLabel(UiText.MainWindow.GAME_NOTES_EMPTY_HINT);
        gameNotesHintLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        gameNotesHintLabel.setForeground(Styling.mutedTextColour);

        titleBlock.add(gameNotesTitleLabel, BorderLayout.NORTH);
        titleBlock.add(gameNotesHintLabel, BorderLayout.CENTER);

        gameNotesEditButton = new JButton(UiText.MainWindow.GAME_NOTES_EDIT_BUTTON);
        WindowUiSupport.styleSecondaryButton(gameNotesEditButton, Styling.accentColour, Styling.surfaceBorderColour);
        gameNotesEditButton.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        gameNotesEditButton.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        gameNotesEditButton.setToolTipText(UiText.MainWindow.GAME_NOTES_EDIT_TOOLTIP);
        gameNotesEditButton.setEnabled(false);
        gameNotesEditButton.addActionListener(event -> {
            if (gameNotesEditing) {
                SetGameNotesEditing(false, true);
            } else {
                SetGameNotesEditing(true, false);
            }
        });

        titleRow.add(titleBlock, BorderLayout.CENTER);
        titleRow.add(gameNotesEditButton, BorderLayout.EAST);

        gameNotesArea = new JTextArea();
        gameNotesArea.setEditable(false);
        gameNotesArea.setFocusable(false);
        gameNotesArea.setLineWrap(false);
        gameNotesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        gameNotesArea.setBackground(Styling.displayFrameColour);
        gameNotesArea.setForeground(Styling.fpsForegroundColour);
        gameNotesArea.setCaretColor(Styling.fpsForegroundColour);
        gameNotesArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        gameNotesScrollPane = new JScrollPane(gameNotesArea);
        gameNotesScrollPane.setBorder(WindowUiSupport.createLineBorder(Styling.displayFrameBorderColour));
        gameNotesScrollPane.getViewport().setBackground(Styling.displayFrameColour);
        gameNotesScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        gameNotesScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        gameNotesScrollPane.setPreferredSize(new Dimension(gameArtPreviewWidth, 160));

        panel.add(titleRow, BorderLayout.NORTH);
        panel.add(gameNotesScrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JComponent BuildGameArtPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        JPanel titleRow = new JPanel(new BorderLayout(0, 4));
        titleRow.setOpaque(false);

        gameArtTitleLabel = new JLabel(UiText.MainWindow.GAME_ART_TITLE);
        gameArtTitleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 15f));
        gameArtTitleLabel.setForeground(Styling.accentColour);

        gameArtHintLabel = new JLabel(UiText.MainWindow.GAME_ART_HINT_DEFAULT);
        gameArtHintLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        gameArtHintLabel.setForeground(Styling.mutedTextColour);

        titleRow.add(gameArtTitleLabel, BorderLayout.NORTH);
        titleRow.add(gameArtHintLabel, BorderLayout.CENTER);

        gameArtLabel = new JLabel(UiText.MainWindow.GAME_ART_LOAD_PROMPT, SwingConstants.CENTER);
        gameArtLabel.setVerticalAlignment(SwingConstants.CENTER);
        gameArtLabel.setHorizontalTextPosition(SwingConstants.CENTER);
        gameArtLabel.setVerticalTextPosition(SwingConstants.CENTER);
        gameArtLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        gameArtLabel.setForeground(Styling.mutedTextColour);
        gameArtLabel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        gameArtPreviewPanel = new JPanel(new BorderLayout());
        gameArtPreviewPanel.setBackground(Styling.displayFrameColour);
        gameArtPreviewPanel.setBorder(WindowUiSupport.createLineBorder(Styling.displayFrameBorderColour));
        gameArtPreviewPanel.setPreferredSize(new Dimension(gameArtPreviewWidth, gameArtPreviewHeight));
        gameArtPreviewPanel.add(gameArtLabel, BorderLayout.CENTER);

        panel.add(titleRow, BorderLayout.NORTH);
        panel.add(gameArtPreviewPanel, BorderLayout.CENTER);
        return panel;
    }

    private JComponent BuildStatusBar() {
        JPanel nextStatusBar = new JPanel(new BorderLayout(12, 0));
        nextStatusBar.setBackground(Styling.statusBackgroundColour);
        nextStatusBar.setBorder(createStatusBarBorder());

        romLabel = new JLabel(UiText.MainWindow.NO_ROM_LOADED);
        romLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        romLabel.setForeground(Styling.accentColour);

        stateLabel = new JLabel(UiText.Common.READY);
        stateLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        stateLabel.setForeground(Styling.mutedTextColour);
        stateLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        nextStatusBar.add(romLabel, BorderLayout.WEST);
        nextStatusBar.add(stateLabel, BorderLayout.EAST);
        return nextStatusBar;
    }

    private JButton CreateHeaderButton(String text, Action action) {
        return CreateHeaderButton(text, action, false);
    }

    private JButton CreateHeaderButton(String text, Action action, boolean primary) {
        JButton button = new JButton(text);
        button.putClientProperty("gameduck.header.primary", primary);
        if (primary) {
            WindowUiSupport.stylePrimaryButton(button, Styling.accentColour);
        } else {
            styleHeaderButton(button);
        }
        button.addActionListener(new GUIActions(this, action, emulation));
        headerButtons.add(button);
        return button;
    }

    private void styleHeaderButton(JButton button) {
        WindowUiSupport.styleSecondaryButton(button, Styling.accentColour, Styling.surfaceBorderColour);
        button.setBorder(BorderFactory.createEmptyBorder(10, 17, 10, 17));
    }

    private Border createMenuBarBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Styling.surfaceBorderColour),
                BorderFactory.createEmptyBorder(4, 8, 4, 8));
    }

    private Border createStatusBarBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Styling.surfaceBorderColour),
                BorderFactory.createEmptyBorder(12, 24, 12, 24));
    }

    private Border createSurfaceCardBorder() {
        return WindowUiSupport.createCardBorder(Styling.surfaceBorderColour, false, 18);
    }

    private Border createDisplayFrameBorder() {
        return WindowUiSupport.createCardBorder(Styling.displayFrameBorderColour, false, 18);
    }

    private void applyForeground(java.awt.Color colour, JLabel... labels) {
        for (JLabel label : labels) {
            if (label != null) {
                label.setForeground(colour);
            }
        }
    }

    private void AddMenuItem(JMenu menu, String item, Action action) {
        JMenuItem menuItem = new JMenuItem(item);
        menuItem.addActionListener(new GUIActions(this, action, emulation));
        ConfigureMenuItem(menuItem);
        menuItemsByAction.put(action, menuItem);
        menu.add(menuItem);
    }

    private void AddMenu(JMenuBar currentMenuBar, String[] items, Action[] actions) {
        JMenu menu = new JMenu(items[0]);
        String[] menuEntries = new String[items.length - 1];
        System.arraycopy(items, 1, menuEntries, 0, menuEntries.length);

        ConfigureMenu(menu);

        for (int index = 0, actionIndex = 0; index < menuEntries.length; index++) {
            if (menuEntries[index].isEmpty()) {
                AddMenuDivider(menu);
            } else {
                AddMenuItem(menu, menuEntries[index], actions[actionIndex]);
                actionIndex++;
            }
        }

        ConfigureMenuTree(menu);
        currentMenuBar.add(menu);
    }

    private JMenu BuildGameMenu() {
        JMenu menu = new JMenu(UiText.MainWindow.GAME_MENU_TITLE);
        ConfigureMenu(menu);

        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_LIBRARY, Action.LIBRARY);
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_OPEN_ROM, Action.LOADROM);
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_OPEN_IPS_PATCH, Action.LOADIPS);
        recentGamesMenu = CreateRecentGamesMenu();
        menu.add(recentGamesMenu);
        AddMenuDivider(menu);
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_PAUSE_GAME, Action.PAUSEGAME);
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_RESET_GAME, Action.RESETGAME);
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_CLOSE_GAME, Action.CLOSEGAME);
        if (backend.Profile().capabilities().supportsCheats()) {
            cheatManagerMenuItem = new JMenuItem(UiText.MainWindow.GAME_MENU_CHEATS);
            ConfigureMenuItem(cheatManagerMenuItem);
            cheatManagerMenuItem.addActionListener(event -> new CheatManagerWindow(this, emulation));
            menu.add(cheatManagerMenuItem);
        }
        AddMenuDivider(menu);

        JMenuItem saveStateManagerItem = new JMenuItem(UiText.MainWindow.GAME_MENU_SAVE_STATE_MANAGER);
        ConfigureMenuItem(saveStateManagerItem);
        saveStateManagerItem.addActionListener(event -> new SaveStateManagerWindow(this));
        menu.add(saveStateManagerItem);

        AddMenuDivider(menu);
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_QUICK_SAVE, Action.SAVESTATE);
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_QUICK_LOAD, Action.LOADSTATE);
        menu.add(CreateStateSlotMenu(UiText.MainWindow.GAME_MENU_SAVE_STATE, true));
        menu.add(CreateStateSlotMenu(UiText.MainWindow.GAME_MENU_LOAD_STATE, false));

        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent event) {
                RefreshSaveStateMenus();
                RefreshRecentGamesMenu();
            }

            @Override
            public void menuDeselected(MenuEvent event) {
            }

            @Override
            public void menuCanceled(MenuEvent event) {
            }
        });
        ConfigureMenuTree(menu);
        return menu;
    }

    private JMenu CreateStateSlotMenu(String title, boolean saveMenu) {
        JMenu menu = new JMenu(title);
        ConfigureMenu(menu);

        for (int slot = QuickStateManager.quickSlot; slot <= QuickStateManager.maxSlot; slot++) {
            JMenuItem menuItem = new JMenuItem();
            ConfigureMenuItem(menuItem);
            menuItem.addActionListener(new GUIActions(this,
                    saveMenu ? Action.SAVESTATE : Action.LOADSTATE,
                    emulation,
                    slot));

            if (saveMenu) {
                saveStateSlotItems[slot] = menuItem;
            } else {
                loadStateSlotItems[slot] = menuItem;
            }

            if (slot == QuickStateManager.quickSlot) {
                menu.add(menuItem);
                AddMenuDivider(menu);
            } else {
                menu.add(menuItem);
            }
        }

        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent event) {
                RefreshSaveStateMenus();
            }

            @Override
            public void menuDeselected(MenuEvent event) {
            }

            @Override
            public void menuCanceled(MenuEvent event) {
            }
        });

        if (saveMenu) {
            saveStateMenu = menu;
        } else {
            loadStateMenu = menu;
        }
        ConfigureMenuTree(menu);
        return menu;
    }

    private JMenu CreateRecentGamesMenu() {
        JMenu menu = new JMenu(UiText.MainWindow.GAME_MENU_LOAD_RECENT);
        ConfigureMenu(menu);
        RefreshRecentGamesMenu(menu);
        ConfigureMenuTree(menu);
        return menu;
    }

    /**
     * Reapplies the configured application shortcuts to menus and helper text.
     */
    public void RefreshAppShortcuts() {
        Runnable refresh = () -> {
            for (AppShortcut shortcut : AppShortcut.values()) {
                JMenuItem menuItem = menuItemsByAction.get(shortcut.Action());
                if (menuItem != null) {
                    menuItem.setAccelerator(Settings.appShortcutBindings.GetKeyStroke(shortcut));
                }
            }
            RefreshSaveStateMenus();
        };

        if (SwingUtilities.isEventDispatchThread()) {
            refresh.run();
        } else {
            SwingUtilities.invokeLater(refresh);
        }
    }

    public void RefreshControllerInputRouting() {
        if (inputRouter != null) {
            inputRouter.RefreshControllerPollingState();
        }
    }

    /**
     * Toggles the chrome-light display mode and persists the choice.
     */
    public void ToggleFullView() {
        Settings.fillWindowOutput = !Settings.fillWindowOutput;
        Config.Save();
        ApplyWindowMode();
    }

    @Override
    public void SetSubtitle(String... subtitleParts) {
        String[] titleParts = subtitleParts == null ? new String[0] : subtitleParts.clone();
        if (titleParts.length > 0) {
            titleParts[0] = ApplyGameNameDisplayMode(titleParts[0]);
        }
        super.SetSubtitle(titleParts);

        String stateText = subtitleParts != null && subtitleParts.length > 1
                ? subtitleParts[1]
                : UiText.Common.READY;
        Runnable update = () -> {
            if (stateLabel != null) {
                stateLabel.setText(CleanStateText(stateText));
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private String CleanStateText(String stateText) {
        if (stateText == null || stateText.isBlank()) {
            return UiText.Common.READY;
        }
        return stateText.startsWith(":") ? stateText.substring(1).trim() : stateText.trim();
    }

    /**
     * Rebuilds the window chrome for the current display layout setting.
     */
    public void ApplyWindowMode() {
        Runnable apply = () -> {
            boolean fillWindow = Settings.fillWindowOutput;
            boolean showSerial = Settings.showSerialOutput;
            boolean showGameNotes = Settings.showGameNotes;
            boolean showGameArt = Settings.gameArtDisplayMode != GameArtDisplayMode.NONE;
            SyncFullViewActionState(fillWindow);
            if ((!showGameNotes || fillWindow) && gameNotesEditing) {
                SetGameNotesEditing(false, true);
            }

            getContentPane().setBackground(fillWindow ? Styling.displayBackgroundColour : Styling.appBackgroundColour);
            if (statusBar != null) {
                statusBar.setVisible(!fillWindow);
            }
            if (labelRow != null) {
                labelRow.setVisible(!fillWindow);
            }
            if (displayEmptyStateContent != null) {
                displayEmptyStateContent.setVisible(fillWindow);
            }
            if (serialCard != null) {
                serialCard.setVisible(!fillWindow && (showSerial || showGameNotes || showGameArt));
            }
            if (serialSectionPanel != null) {
                serialSectionPanel.setVisible(showSerial);
            }
            if (gameNotesPanel != null) {
                gameNotesPanel.setVisible(showGameNotes);
            }
            if (gameArtPanel != null) {
                gameArtPanel.setVisible(showGameArt);
            }
            if (sidePanelSpacer != null) {
                sidePanelSpacer.setVisible(showSerial && (showGameNotes || showGameArt));
            }
            if (notesArtSpacer != null) {
                notesArtSpacer.setVisible(showGameNotes && showGameArt);
            }
            if (displayWrapper != null) {
                displayWrapper.setOpaque(true);
                displayWrapper
                        .setBackground(fillWindow ? Styling.displayBackgroundColour : Styling.appBackgroundColour);
                displayWrapper.setBorder(fillWindow
                        ? BorderFactory.createEmptyBorder()
                        : BorderFactory.createEmptyBorder(24, 24, 0, 24));
            }
            if (displayCard != null) {
                displayCard.setBackground(fillWindow ? Styling.displayBackgroundColour : Styling.surfaceColour);
                displayCard.setBorder(fillWindow ? BorderFactory.createEmptyBorder() : createSurfaceCardBorder());
            }
            if (displayFrame != null) {
                displayFrame.setBackground(Styling.displayFrameColour);
                displayFrame.setBorder(fillWindow ? BorderFactory.createEmptyBorder() : createDisplayFrameBorder());
            }
            if (!fillWindow && showSerial) {
                SetSerialOutput(DebugLogger.GetSerialOutput());
            } else {
                synchronized (pendingSerialAppend) {
                    pendingSerialAppend.setLength(0);
                }
                serialAppendQueued.set(false);
            }
            updateDisplayStage();
            revalidate();
            repaint();
        };

        if (SwingUtilities.isEventDispatchThread()) {
            apply.run();
        } else {
            SwingUtilities.invokeLater(apply);
        }
    }

    private void SyncFullViewActionState(boolean fillWindow) {
        if (fullViewButton != null) {
            fullViewButton.setText(UiText.MainWindow.FullViewButtonLabel(fillWindow));
        }

        JMenuItem fullViewMenuItem = menuItemsByAction.get(Action.FULL_VIEW);
        if (fullViewMenuItem != null) {
            fullViewMenuItem.setText(UiText.MainWindow.FullViewMenuLabel(fillWindow));
        }
    }

    /**
     * Reapplies the current host theme to the visible main window widgets.
     */
    public void RefreshTheme() {
        Runnable refresh = () -> {
            getContentPane().setBackground(Styling.appBackgroundColour);

            if (menuBar != null) {
                menuBar.setBackground(Styling.surfaceColour);
                menuBar.setForeground(Styling.accentColour);
                menuBar.setBorder(createMenuBarBorder());
                for (int index = 0; index < menuBar.getMenuCount(); index++) {
                    JMenu menu = menuBar.getMenu(index);
                    if (menu != null) {
                        ConfigureMenuTree(menu);
                    }
                }
            }

            applyForeground(Styling.accentColour, displayTitleLabel, serialTitleLabel, gameNotesTitleLabel,
                    gameArtTitleLabel, romLabel);
            applyForeground(Styling.mutedTextColour, displayHintLabel, serialHintLabel, gameNotesHintLabel,
                    gameArtHintLabel, stateLabel);
            if (gameArtLabel != null) {
                gameArtLabel.setForeground(
                        gameArtLabel.getIcon() == null ? Styling.mutedTextColour : Styling.fpsForegroundColour);
            }
            if (statusBar != null) {
                statusBar.setBackground(Styling.statusBackgroundColour);
                statusBar.setBorder(createStatusBarBorder());
            }
            if (serialCard != null) {
                serialCard.setBackground(Styling.surfaceColour);
                serialCard.setBorder(createSurfaceCardBorder());
            }
            if (serialOutputArea != null) {
                serialOutputArea.setBackground(Styling.displayFrameColour);
                serialOutputArea.setForeground(Styling.fpsForegroundColour);
            }
            if (serialOutputScrollPane != null) {
                serialOutputScrollPane.setBorder(WindowUiSupport.createLineBorder(Styling.displayFrameBorderColour));
                serialOutputScrollPane.getViewport().setBackground(Styling.displayFrameColour);
            }
            if (gameNotesArea != null) {
                gameNotesArea.setBackground(Styling.displayFrameColour);
                gameNotesArea.setForeground(Styling.fpsForegroundColour);
                gameNotesArea.setCaretColor(Styling.fpsForegroundColour);
            }
            if (gameNotesScrollPane != null) {
                gameNotesScrollPane.setBorder(WindowUiSupport.createLineBorder(Styling.displayFrameBorderColour));
                gameNotesScrollPane.getViewport().setBackground(Styling.displayFrameColour);
            }
            if (gameNotesEditButton != null) {
                WindowUiSupport.styleSecondaryButton(gameNotesEditButton, Styling.accentColour,
                        Styling.surfaceBorderColour);
                gameNotesEditButton.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            }
            if (gameArtPreviewPanel != null) {
                gameArtPreviewPanel.setBackground(Styling.displayFrameColour);
                gameArtPreviewPanel.setBorder(WindowUiSupport.createLineBorder(Styling.displayFrameBorderColour));
            }

            for (JButton button : headerButtons) {
                if (Boolean.TRUE.equals(button.getClientProperty("gameduck.header.primary"))) {
                    WindowUiSupport.stylePrimaryButton(button, Styling.accentColour);
                } else {
                    styleHeaderButton(button);
                }
            }

            ApplyWindowMode();
            RefreshSaveStateMenus();
            repaint();
        };

        if (SwingUtilities.isEventDispatchThread()) {
            refresh.run();
        } else {
            SwingUtilities.invokeLater(refresh);
        }
    }

    /**
     * Updates the status bar with the preferred ROM title.
     *
     * @param rom active ROM
     */
    public void SetLoadedRom(GBRom rom) {
        SetLoadedGame(rom, true);
    }

    /**
     * Updates the status bar with either a resolved libretro title or a temporary
     * loading state.
     *
     * @param rom           active ROM
     * @param allowFallback whether filename fallback should be used
     */
    @Override
    public void SetLoadedGame(EmulatorGame game) {
        SetLoadedGame(game, true);
    }

    @Override
    public void SetLoadedGame(EmulatorGame game, boolean allowFallback) {
        boolean gameChanged = !SameGameIdentity(currentLoadedGame, game);
        if (gameChanged && gameNotesEditing) {
            SetGameNotesEditing(false, true);
        }
        currentLoadedGame = game;
        recentGamesMenuDirty = true;
        romNameLookupPending = game != null && !allowFallback && GameMetadataStore.GetLibretroTitle(game).isEmpty();
        String romText = ResolveDisplayedRomName(game, allowFallback);
        Runnable update = () -> {
            if (romLabel != null) {
                romLabel.setText(romText);
            }
            if (gameChanged || !gameNotesEditing) {
                LoadGameNotesForCurrentGame();
            }
            RefreshSaveStateMenus();
            updateDisplayStage();
        };

        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    /**
     * Reapplies the active library naming settings to the current ROM label.
     */
    public void RefreshLoadedRomDisplay() {
        SetLoadedGame(currentLoadedGame, !romNameLookupPending);
    }

    private String ResolveDisplayedRomName(EmulatorGame game, boolean allowFallback) {
        if (game == null) {
            return UiText.MainWindow.NO_ROM_LOADED;
        }

        Optional<String> libretroTitle = GameMetadataStore.GetLibretroTitle(game);
        if (libretroTitle.isPresent()) {
            return ApplyGameNameDisplayMode(libretroTitle.get());
        }

        if (!allowFallback) {
            return UiText.MainWindow.RETRIEVING_GAME_NAME;
        }

        String sourceName = game.sourceName();
        if (sourceName != null && !sourceName.isBlank()) {
            return ApplyGameNameDisplayMode(sourceName);
        }

        return game.displayName() == null || game.displayName().isBlank()
                ? UiText.MainWindow.NO_ROM_LOADED
                : ApplyGameNameDisplayMode(game.displayName());
    }

    public static String ApplyGameNameDisplayMode(String name) {
        if (name == null || name.isBlank()) {
            return UiText.MainWindow.NO_ROM_LOADED;
        }

        String formattedName = name;
        GameNameBracketDisplayMode mode = Settings.gameNameBracketDisplayMode;
        if (mode == GameNameBracketDisplayMode.ROUND || mode == GameNameBracketDisplayMode.BOTH) {
            formattedName = formattedName.replaceAll("\\s*\\([^)]*\\)", "");
        }
        if (mode == GameNameBracketDisplayMode.SQUARE || mode == GameNameBracketDisplayMode.BOTH) {
            formattedName = formattedName.replaceAll("\\s*\\[[^\\]]*\\]", "");
        }

        formattedName = formattedName.replaceAll("\\s+", " ").trim();
        return formattedName.isBlank() ? name.trim() : formattedName;
    }

    private void RefreshSaveStateMenus() {
        Runnable refresh = () -> {
            if (saveStateMenu != null) {
                saveStateMenu.setText(UiText.MainWindow.GAME_MENU_SAVE_STATE);
            }
            if (loadStateMenu != null) {
                loadStateMenu.setText(UiText.MainWindow.GAME_MENU_LOAD_STATE);
            }
            if (cheatManagerMenuItem != null) {
                cheatManagerMenuItem.setEnabled(currentLoadedGame != null);
            }

            List<EmulatorStateSlot> slots = emulation.DescribeCurrentStateSlots();
            boolean hasLoadedRom = currentLoadedGame != null;

            for (EmulatorStateSlot slotInfo : slots) {
                int slot = slotInfo.slot();
                String timestampText = slotInfo.exists() ? FormatSaveStateTimestamp(slotInfo.lastModified()) : "";
                String label = UiText.MainWindow.SaveStateSlotLabel(slot, timestampText);

                JMenuItem saveMenuItem = saveStateSlotItems[slot];
                if (saveMenuItem != null) {
                    saveMenuItem.setText(label);
                    saveMenuItem.setEnabled(hasLoadedRom);
                }

                JMenuItem loadMenuItem = loadStateSlotItems[slot];
                if (loadMenuItem != null) {
                    loadMenuItem.setText(label);
                    loadMenuItem.setEnabled(hasLoadedRom && slotInfo.exists());
                }
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            refresh.run();
        } else {
            SwingUtilities.invokeLater(refresh);
        }
    }

    private String FormatSaveStateTimestamp(FileTime lastModified) {
        if (lastModified == null || lastModified.toMillis() <= 0L) {
            return "";
        }
        return saveStateTimestampFormatter.format(lastModified.toInstant().atZone(ZoneId.systemDefault()));
    }

    private void AppendSerialOutput(String text) {
        if (text == null || text.isEmpty() || !shouldUpdateSerialOutputUi()) {
            return;
        }

        synchronized (pendingSerialAppend) {
            pendingSerialAppend.append(text);
        }

        if (!serialAppendQueued.compareAndSet(false, true)) {
            return;
        }

        SwingUtilities.invokeLater(this::FlushPendingSerialOutput);
    }

    private void SetSerialOutput(String text) {
        Runnable set = () -> {
            if (serialOutputArea == null || !shouldUpdateSerialOutputUi()) {
                return;
            }
            serialOutputArea.setText(text == null ? "" : text);
            serialOutputArea.setCaretPosition(serialOutputArea.getDocument().getLength());
        };

        if (SwingUtilities.isEventDispatchThread()) {
            set.run();
        } else {
            SwingUtilities.invokeLater(set);
        }
    }

    private void ClearSerialOutput() {
        synchronized (pendingSerialAppend) {
            pendingSerialAppend.setLength(0);
        }
        serialAppendQueued.set(false);
        if (shouldUpdateSerialOutputUi()) {
            SetSerialOutput("");
        }
    }

    private void LoadGameNotesForCurrentGame() {
        if (gameNotesArea == null) {
            return;
        }

        if (gameNotesEditing) {
            SetGameNotesEditing(false, true);
        }

        String notes = currentLoadedGame == null ? "" : GameNotesStore.Load(currentLoadedGame);
        gameNotesArea.setText(notes);
        gameNotesArea.setCaretPosition(0);
        if (gameNotesHintLabel != null) {
            gameNotesHintLabel.setText(currentLoadedGame == null
                    ? UiText.MainWindow.GAME_NOTES_EMPTY_HINT
                    : UiText.MainWindow.GAME_NOTES_HINT);
        }
        if (gameNotesEditButton != null) {
            gameNotesEditButton.setEnabled(currentLoadedGame != null);
            gameNotesEditButton.setText(UiText.MainWindow.GAME_NOTES_EDIT_BUTTON);
            gameNotesEditButton.setToolTipText(UiText.MainWindow.GAME_NOTES_EDIT_TOOLTIP);
        }
        gameNotesArea.setEditable(false);
        gameNotesArea.setFocusable(false);
    }

    private void SetGameNotesEditing(boolean editing, boolean save) {
        if (gameNotesArea == null) {
            return;
        }
        if (editing && currentLoadedGame == null) {
            return;
        }

        if (!editing && save) {
            SaveCurrentGameNotes();
        }

        gameNotesEditing = editing;
        gameNotesArea.setEditable(editing);
        gameNotesArea.setFocusable(editing);
        if (gameNotesEditButton != null) {
            gameNotesEditButton.setText(editing
                    ? UiText.MainWindow.GAME_NOTES_SAVE_BUTTON
                    : UiText.MainWindow.GAME_NOTES_EDIT_BUTTON);
            gameNotesEditButton.setToolTipText(editing
                    ? UiText.MainWindow.GAME_NOTES_SAVE_TOOLTIP
                    : UiText.MainWindow.GAME_NOTES_EDIT_TOOLTIP);
        }
        inputRouter.SetKeyboardInputEnabled(!editing);
        if (editing) {
            gameNotesArea.requestFocusInWindow();
        } else {
            requestFocusInWindow();
        }
    }

    private void SaveCurrentGameNotes() {
        if (currentLoadedGame == null || gameNotesArea == null) {
            return;
        }
        GameNotesStore.Save(currentLoadedGame, ReadGameNotesText());
    }

    private String ReadGameNotesText() {
        if (gameNotesArea == null) {
            return "";
        }
        if (SwingUtilities.isEventDispatchThread()) {
            return gameNotesArea.getText();
        }

        String[] text = { "" };
        try {
            SwingUtilities.invokeAndWait(() -> text[0] = gameNotesArea.getText());
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return text[0];
    }

    private boolean SameGameIdentity(EmulatorGame left, EmulatorGame right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.sourcePath(), right.sourcePath())
                && Objects.equals(left.sourceName(), right.sourceName())
                && Objects.equals(left.displayName(), right.displayName())
                && Objects.equals(left.patchNames(), right.patchNames());
    }

    private void FlushPendingSerialOutput() {
        String text;
        synchronized (pendingSerialAppend) {
            text = pendingSerialAppend.toString();
            pendingSerialAppend.setLength(0);
        }

        serialAppendQueued.set(false);
        if (text.isEmpty() || !shouldUpdateSerialOutputUi()) {
            return;
        }

        serialOutputArea.append(text);
        serialOutputArea.setCaretPosition(serialOutputArea.getDocument().getLength());

        synchronized (pendingSerialAppend) {
            if (pendingSerialAppend.length() > 0 && serialAppendQueued.compareAndSet(false, true)) {
                SwingUtilities.invokeLater(this::FlushPendingSerialOutput);
            }
        }
    }

    private void RefreshRecentGamesMenu() {
        Runnable refresh = () -> RefreshRecentGamesMenu(recentGamesMenu);
        if (SwingUtilities.isEventDispatchThread()) {
            refresh.run();
        } else {
            SwingUtilities.invokeLater(refresh);
        }
    }

    private void RefreshRecentGamesMenu(JMenu menu) {
        if (menu == null) {
            return;
        }

        if (!recentGamesMenuDirty && cachedRecentGamesMenuLimit == Settings.loadRecentMenuLimit) {
            return;
        }

        recentGamesMenuDirty = false;
        cachedRecentGamesMenuLimit = Settings.loadRecentMenuLimit;
        menu.removeAll();
        List<GameLibraryStore.LibraryEntry> recentEntries = GameLibraryStore
                .GetRecentEntries(Settings.loadRecentMenuLimit);
        if (recentEntries.isEmpty()) {
            JMenuItem emptyItem = new JMenuItem(UiText.MainWindow.GAME_MENU_LOAD_RECENT_EMPTY);
            ConfigureMenuItem(emptyItem);
            emptyItem.setEnabled(false);
            menu.add(emptyItem);
        } else {
            for (GameLibraryStore.LibraryEntry entry : recentEntries) {
                JMenuItem entryItem = new JMenuItem(ResolveDisplayedRomName(entry, true));
                ConfigureMenuItem(entryItem);
                entryItem.addActionListener(event -> LoadLibraryEntry(entry));
                menu.add(entryItem);
            }
        }

        AddMenuDivider(menu);
        JMenuItem clearRecentItem = new JMenuItem(UiText.MainWindow.GAME_MENU_CLEAR_RECENT);
        ConfigureMenuItem(clearRecentItem);
        clearRecentItem.setEnabled(!recentEntries.isEmpty());
        clearRecentItem.addActionListener(event -> {
            GameLibraryStore.ClearRecentHistory();
            recentGamesMenuDirty = true;
            RefreshRecentGamesMenu();
        });
        menu.add(clearRecentItem);
        ConfigureMenuTree(menu);
    }

    private void ConfigureMenuItem(JMenuItem menuItem) {
        if (menuItem == null) {
            return;
        }
        menuItem.setFont(Styling.menuFont);
        menuItem.setForeground(Styling.accentColour);
        menuItem.setBackground(Styling.surfaceColour);
        menuItem.setOpaque(true);
        menuItem.setContentAreaFilled(true);
        menuItem.setMargin(new Insets(6, 12, 6, 12));
        menuItem.setBorder(BorderFactory.createEmptyBorder());
        menuItem.setBorderPainted(false);
        WindowUiSupport.styleMenuItem(menuItem);
    }

    private void AddMenuDivider(JMenu menu) {
        if (menu == null) {
            return;
        }
        menu.add(new MenuDivider());
    }

    private void ConfigureMenu(JMenu menu) {
        if (menu == null) {
            return;
        }
        menu.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        menu.setForeground(Styling.accentColour);
        menu.setBackground(Styling.surfaceColour);
        menu.setOpaque(true);
        if (menu.getPopupMenu() != null) {
            WindowUiSupport.stylePopupMenu(menu.getPopupMenu());
        }
        WindowUiSupport.styleMenu(menu);
    }

    private void ConfigureMenuTree(JMenu menu) {
        if (menu == null) {
            return;
        }

        ConfigureMenu(menu);
        JPopupMenu popupMenu = menu.getPopupMenu();
        if (popupMenu != null) {
            WindowUiSupport.stylePopupMenu(popupMenu);
        }

        for (Component item : menu.getMenuComponents()) {
            ConfigureMenuComponent(item);
        }
    }

    private void ConfigureMenuComponent(Component component) {
        if (component == null) {
            return;
        }

        if (component instanceof JMenu submenu) {
            ConfigureMenuTree(submenu);
            return;
        }

        if (component instanceof JMenuItem menuItem) {
            ConfigureMenuItem(menuItem);
            return;
        }

        if (component instanceof JPopupMenu.Separator popupSeparator) {
            WindowUiSupport.styleMenuSeparator(popupSeparator);
            return;
        }

        if (component instanceof JSeparator separator) {
            separator.setOpaque(false);
            separator.setBackground(Styling.surfaceColour);
            separator.setForeground(Styling.surfaceBorderColour);
        }
    }

    private static final class MenuDivider extends JComponent {
        private static final int dividerHeight = 11;
        private static final int horizontalInset = 12;

        private MenuDivider() {
            setOpaque(true);
            setBackground(Styling.surfaceColour);
            setPreferredSize(new Dimension(0, dividerHeight));
            setMinimumSize(new Dimension(0, dividerHeight));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D graphics2d = (Graphics2D) graphics.create();
            graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2d.setColor(Styling.surfaceColour);
            graphics2d.fillRect(0, 0, getWidth(), getHeight());
            graphics2d.setColor(Styling.surfaceBorderColour);
            int y = getHeight() / 2;
            int startX = Math.min(horizontalInset, Math.max(0, getWidth() / 2));
            int endX = Math.max(startX, getWidth() - horizontalInset - 1);
            graphics2d.drawLine(startX, y, endX, y);
            graphics2d.dispose();
        }
    }

    private void LoadLibraryEntry(GameLibraryStore.LibraryEntry entry) {
        if (entry == null) {
            return;
        }

        try {
            emulation.StartEmulation(entry.LoadRom());
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), UiText.GuiActions.LIBRARY_LOAD_ERROR_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Begins an asynchronous lookup for the supplied ROM artwork.
     *
     * @param rom active ROM
     */
    @Override
    public void LoadGameArt(EmulatorGame game) {
        GameArtDisplayMode displayMode = Settings.gameArtDisplayMode;
        if (game == null || displayMode == GameArtDisplayMode.NONE) {
            ClearGameArt();
            return;
        }

        int requestVersion = gameArtRequestVersion.incrementAndGet();
        String cacheKey = BuildGameArtCacheKey(game, displayMode);
        CachedGameArt cachedPreview = cacheKey == null ? null : cachedGameArt.get(cacheKey);
        if (cachedPreview != null) {
            ApplyCachedGameArt(requestVersion, cacheKey, cachedPreview);
            return;
        }

        SetGameArtPlaceholder(UiText.MainWindow.FETCHING_ARTWORK,
                UiText.MainWindow.LookingUpArtwork(displayMode.Label(), game.displayName()));

        CompletableFuture
                .supplyAsync(() -> PrepareGameArtResult(game, displayMode))
                .thenAccept(result -> ApplyGameArtResult(game, cacheKey, requestVersion, result))
                .exceptionally(exception -> {
                    ApplyGameArtResult(game, cacheKey, requestVersion, PreparedGameArtResult.empty());
                    return null;
                });
    }

    /**
     * Clears the currently displayed artwork and invalidates pending requests.
     */
    public void ClearGameArt() {
        gameArtRequestVersion.incrementAndGet();
        if (Settings.gameArtDisplayMode == GameArtDisplayMode.NONE) {
            SetGameArtPlaceholder(UiText.MainWindow.GAME_ART_LOAD_PROMPT,
                    UiText.MainWindow.GAME_ART_DISABLED_HINT);
            return;
        }
        SetGameArtPlaceholder(UiText.MainWindow.GAME_ART_LOAD_PROMPT, Settings.gameArtDisplayMode.SourceLabel());
    }

    /**
     * Returns the emulator controller attached to the window.
     *
     * @return emulator controller
     */
    public EmulatorRuntime GetEmulation() {
        return emulation;
    }

    public EmulatorBackend GetBackend() {
        return backend;
    }

    public com.blackaby.Backend.Platform.EmulatorProfile GetBackendProfile() {
        return backend.Profile();
    }

    public EmulatorGame GetCurrentLoadedGame() {
        return currentLoadedGame;
    }

    /**
     * Returns the currently loaded ROM shown by the main window.
     *
     * @return loaded ROM or {@code null}
     */
    public GBRom GetCurrentLoadedRom() {
        return currentLoadedGame instanceof GBRom rom ? rom : null;
    }

    /**
     * Returns the current ROM label using the active display-name settings.
     *
     * @return displayed ROM name
     */
    public String GetCurrentLoadedRomDisplayName() {
        return ResolveDisplayedRomName(currentLoadedGame, !romNameLookupPending);
    }

    private PreparedGameArtResult PrepareGameArtResult(EmulatorGame game, GameArtDisplayMode displayMode) {
        Optional<GameArtResult> result = GameArtProvider.FindGameArt(game, displayMode);
        if (result.isEmpty()) {
            return PreparedGameArtResult.empty();
        }

        GameArtResult gameArtResult = result.get();
        BufferedImage scaledImage = GameArtScaler.ScaleToFit(
                gameArtResult.image(),
                gameArtPreviewWidth - 24,
                gameArtPreviewHeight - 24,
                true);
        if (scaledImage == null) {
            return PreparedGameArtResult.empty();
        }

        return new PreparedGameArtResult(
                new CachedGameArt(new ImageIcon(scaledImage), gameArtResult.sourceLabel()),
                gameArtResult.matchedGameName());
    }

    private void ApplyGameArtResult(EmulatorGame game, String cacheKey, int requestVersion,
            PreparedGameArtResult result) {
        Runnable apply = () -> {
            if (gameArtRequestVersion.get() != requestVersion) {
                return;
            }

            if (result.preview() != null) {
                if (result.matchedGameName() != null && !result.matchedGameName().isBlank()) {
                    GameMetadataStore.RememberLibretroTitle(game, result.matchedGameName());
                    SetLoadedGame(game);
                }
                if (cacheKey != null) {
                    cachedGameArt.put(cacheKey, result.preview());
                }
                ShowGameArt(cacheKey, result.preview());
                return;
            }

            SetGameArtPlaceholder(UiText.MainWindow.NO_ARTWORK_FOUND,
                    UiText.MainWindow.NO_ARTWORK_HINT);
            SetLoadedGame(game, true);
        };

        if (SwingUtilities.isEventDispatchThread()) {
            apply.run();
        } else {
            SwingUtilities.invokeLater(apply);
        }
    }

    private void SetGameArtPlaceholder(String text, String hintText) {
        Runnable update = () -> {
            if (gameArtLabel == null || gameArtHintLabel == null) {
                return;
            }
            displayedGameArtCacheKey = null;
            gameArtLabel.setIcon(null);
            gameArtLabel.setText(text);
            gameArtLabel.setForeground(Styling.mutedTextColour);
            gameArtHintLabel.setText(hintText);
        };

        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private void ApplyCachedGameArt(int requestVersion, String cacheKey, CachedGameArt preview) {
        Runnable apply = () -> {
            if (gameArtRequestVersion.get() == requestVersion) {
                ShowGameArt(cacheKey, preview);
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            apply.run();
        } else {
            SwingUtilities.invokeLater(apply);
        }
    }

    private void ShowGameArt(String cacheKey, CachedGameArt preview) {
        if (gameArtLabel == null || gameArtHintLabel == null || preview == null) {
            return;
        }
        if (cacheKey != null && cacheKey.equals(displayedGameArtCacheKey)) {
            return;
        }

        displayedGameArtCacheKey = cacheKey;
        gameArtLabel.setIcon(preview.icon());
        gameArtLabel.setText("");
        gameArtLabel.setForeground(Styling.fpsForegroundColour);
        gameArtHintLabel.setText(preview.sourceLabel());
    }

    private String BuildGameArtCacheKey(EmulatorGame game, GameArtDisplayMode displayMode) {
        if (game == null || displayMode == null || displayMode == GameArtDisplayMode.NONE) {
            return null;
        }
        return displayMode.name() + "|" +
                String.valueOf(game.sourcePath()) + "|" +
                String.valueOf(game.sourceName()) + "|" +
                String.valueOf(game.displayName()) + "|" +
                String.valueOf(game.headerTitle());
    }

    /**
     * Reapplies the current window-panel settings and reloads artwork when needed.
     */
    public void RefreshWindowPanels() {
        ApplyWindowMode();
        if (Settings.gameArtDisplayMode == GameArtDisplayMode.NONE || currentLoadedGame == null) {
            ClearGameArt();
        } else {
            LoadGameArt(currentLoadedGame);
        }
    }

    /**
     * Reapplies the currently selected display shader to the visible frame.
     */
    public void RefreshDisplayShader() {
        if (display != null) {
            display.RefreshShader();
        }
    }

    /**
     * Reapplies the currently selected display border to the visible frame.
     */
    public void RefreshDisplayBorder() {
        if (display != null) {
            display.RefreshBorder();
        }
    }

    void RefreshDisplayStats() {
        if (displayHintLabel == null || display == null) {
            return;
        }

        String nextText = UiText.MainWindow.DISPLAY_HINT;
        if (Settings.showDisplayFps) {
            DuckDisplay.PresentationStats stats = display.SnapshotPresentationStats();
            if (stats != null && stats.paintedFps() > 0.0) {
                nextText = String.format("%s  %.1f fps  %.2f ms",
                        UiText.MainWindow.DISPLAY_HINT,
                        stats.paintedFps(),
                        stats.averageFrameTimeMs());
            }
        }

        if (!nextText.equals(lastDisplayStatsText)) {
            lastDisplayStatsText = nextText;
            if (!Settings.fillWindowOutput) {
                displayHintLabel.setText(nextText);
            }
        }
    }

    private boolean shouldUpdateSerialOutputUi() {
        return serialOutputArea != null
                && Settings.showSerialOutput
                && !Settings.fillWindowOutput;
    }

    private void updateDisplayStage() {
        if (displayStagePanel == null) {
            return;
        }

        CardLayout layout = (CardLayout) displayStagePanel.getLayout();
        boolean showEmptyState = currentLoadedGame == null;
        layout.show(displayStagePanel, showEmptyState ? DISPLAY_STAGE_EMPTY : DISPLAY_STAGE_ACTIVE);
        if (showEmptyState) {
            SwingUtilities.invokeLater(this::RefreshFullViewRecentGames);
        }
    }

    private String asCenteredHtml(String value, int width) {
        return "<html><table width='" + Math.max(60, width) + "'><tr><td align='center'>"
                + WindowUiSupport.escapeHtml(value == null ? "" : value)
                + "</td></tr></table></html>";
    }

    private String truncateToWidth(String value, java.awt.FontMetrics metrics, int maxWidth) {
        if (value == null || value.isBlank() || metrics == null || maxWidth <= 0) {
            return value == null ? "" : value;
        }
        if (metrics.stringWidth(value) <= maxWidth) {
            return value;
        }

        String ellipsis = "...";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char next = value.charAt(index);
            if (metrics.stringWidth(builder.toString() + next) + ellipsisWidth > maxWidth) {
                break;
            }
            builder.append(next);
        }
        return builder.isEmpty() ? ellipsis : builder + ellipsis;
    }

    private final class RecentGameTile extends JPanel {
        private final GameLibraryStore.LibraryEntry entry;
        private final JLabel displayLabel = new JLabel("", SwingConstants.CENTER);
        private final JPanel contentPanel = new JPanel(new BorderLayout()) {
            @Override
            public void paint(Graphics graphics) {
                super.paint(graphics);
                if (!hovered) {
                    return;
                }

                Graphics2D graphics2d = (Graphics2D) graphics.create();
                graphics2d.setColor(new Color(0, 0, 0, 70));
                graphics2d.fillRect(0, 0, getWidth(), getHeight());
                graphics2d.setColor(new Color(255, 255, 255, 90));
                graphics2d.setStroke(new BasicStroke(1.5f));
                int inset = 1;
                int width = Math.max(0, getWidth() - (inset * 2) - 1);
                int height = Math.max(0, getHeight() - (inset * 2) - 1);
                graphics2d.drawRect(inset, inset, width, height);
                graphics2d.dispose();
            }
        };
        private final Font placeholderFont = Styling.menuFont.deriveFont(Font.BOLD, 13f);
        private BufferedImage artImage;
        private int tileSize;
        private boolean hovered;

        private RecentGameTile(GameLibraryStore.LibraryEntry entry, int tileSize) {
            super(new BorderLayout());
            this.entry = entry;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(ResolveDisplayedRomName(entry, true));

            contentPanel.setOpaque(false);

            displayLabel.setOpaque(true);
            displayLabel.setBackground(Styling.displayFrameColour);
            displayLabel.setForeground(Styling.mutedTextColour);
            displayLabel.setFont(placeholderFont);
            displayLabel.setHorizontalAlignment(SwingConstants.CENTER);
            displayLabel.setVerticalAlignment(SwingConstants.CENTER);
            displayLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)));
            contentPanel.add(displayLabel, BorderLayout.CENTER);
            add(contentPanel, BorderLayout.CENTER);

            MouseAdapter interactionHandler = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    LoadLibraryEntry(RecentGameTile.this.entry);
                }

                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    contentPanel.repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    contentPanel.repaint();
                }
            };
            addMouseListener(interactionHandler);
            contentPanel.addMouseListener(interactionHandler);
            displayLabel.addMouseListener(interactionHandler);

            UpdateTileSize(tileSize);
            LoadRecentGameArt();
        }

        private void UpdateTileSize(int tileSize) {
            if (this.tileSize == tileSize) {
                return;
            }

            this.tileSize = tileSize;
            int contentSize = Math.max(80, tileSize - 16);
            Dimension tileDimension = new Dimension(tileSize, tileSize);
            Dimension contentDimension = new Dimension(contentSize, contentSize);
            setPreferredSize(tileDimension);
            setMinimumSize(tileDimension);
            contentPanel.setPreferredSize(contentDimension);
            displayLabel.setPreferredSize(contentDimension);
            revalidate();
            RefreshVisuals();
        }

        private void SetFallbackTitle() {
            String displayName = ResolveDisplayedRomName(entry, true);
            String truncatedName = truncateToWidth(displayName, displayLabel.getFontMetrics(placeholderFont),
                    Math.max(60, tileSize - 44));
            displayLabel.setIcon(null);
            displayLabel.setText(asCenteredHtml(truncatedName, Math.max(60, tileSize - 44)));
        }

        private void LoadRecentGameArt() {
            CompletableFuture
                    .supplyAsync(() -> GameArtProvider.FindGameArt(entry.ArtDescriptor()))
                    .thenAccept(result -> SwingUtilities.invokeLater(() -> ApplyRecentGameArt(result)))
                    .exceptionally(exception -> null);
        }

        private void ApplyRecentGameArt(Optional<GameArtResult> result) {
            if (result == null || result.isEmpty()) {
                SetFallbackTitle();
                return;
            }

            artImage = result.get().image();
            RefreshVisuals();
        }

        private void RefreshVisuals() {
            if (artImage == null) {
                SetFallbackTitle();
                return;
            }

            BufferedImage scaledImage = GameArtScaler.ScaleToFit(
                    artImage,
                    Math.max(80, tileSize - 28),
                    Math.max(80, tileSize - 28),
                    true);
            if (scaledImage == null) {
                SetFallbackTitle();
                return;
            }

            displayLabel.setIcon(new ImageIcon(scaledImage));
            displayLabel.setText("");
        }
    }

    private record CachedGameArt(ImageIcon icon, String sourceLabel) {
    }

    private record PreparedGameArtResult(CachedGameArt preview, String matchedGameName) {
        private static PreparedGameArtResult empty() {
            return new PreparedGameArtResult(null, null);
        }
    }

}
