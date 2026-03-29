package com.blackaby.Frontend;

import com.blackaby.Backend.GB.GBBackends;
import com.blackaby.Backend.GB.Misc.ROM;
import com.blackaby.Backend.Helpers.GameMetadataStore;
import com.blackaby.Backend.Helpers.GUIActions;
import com.blackaby.Backend.Helpers.GUIActions.Action;
import com.blackaby.Backend.Helpers.GameArtProvider;
import com.blackaby.Backend.Helpers.GameArtProvider.GameArtResult;
import com.blackaby.Backend.Helpers.GameLibraryStore;
import com.blackaby.Backend.Helpers.QuickStateManager;
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
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JTextArea;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hosts the main emulator window and its desktop controls.
 * <p>
 * The window owns the display surface, menu bar, status strips, and the quick
 * actions used to drive the active emulator instance.
 */
public class MainWindow extends DuckWindow implements EmulatorHost {

    private static final int gameArtPreviewWidth = 280;
    private static final int gameArtPreviewHeight = 220;
    private static final DateTimeFormatter saveStateTimestampFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final EmulatorBackend backend;
    private final DuckDisplay display;
    private final EmulatorRuntime emulation;
    private final InputRouter inputRouter;
    private final List<JButton> headerButtons = new ArrayList<>();
    private final EnumMap<Action, JMenuItem> menuItemsByAction = new EnumMap<>(Action.class);
    private final AtomicInteger gameArtRequestVersion = new AtomicInteger();
    private final AtomicBoolean serialAppendQueued = new AtomicBoolean();
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

    private JMenuBar menuBar;
    private JLabel romLabel;
    private JLabel stateLabel;
    private JLabel titleLabel;
    private JLabel subtitleLabel;
    private JLabel displayTitleLabel;
    private JLabel displayHintLabel;
    private JLabel serialTitleLabel;
    private JLabel serialHintLabel;
    private JLabel gameArtTitleLabel;
    private JLabel gameArtHintLabel;
    private JLabel gameArtLabel;
    private JPanel headerPanel;
    private JPanel displayWrapper;
    private JPanel displayCard;
    private JPanel serialCard;
    private JPanel serialSectionPanel;
    private JPanel gameArtPanel;
    private JPanel gameArtPreviewPanel;
    private JComponent sidePanelSpacer;
    private JPanel labelRow;
    private JPanel displayFrame;
    private JPanel statusBar;
    private JTextArea serialOutputArea;
    private JScrollPane serialOutputScrollPane;
    private EmulatorGame currentLoadedGame;
    private boolean romNameLookupPending;

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

        backend = GBBackends.Current();
        display = new DuckDisplay(backend.Profile().displaySpec());
        emulation = backend.CreateRuntime(this, display);
        inputRouter = new InputRouter(this, emulation, backend.Profile());
        inputRouter.Install();

        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(Styling.appBackgroundColour);

        headerPanel = (JPanel) BuildHeader();
        displayWrapper = (JPanel) BuildDisplaySection();
        statusBar = (JPanel) BuildStatusBar();

        add(headerPanel, BorderLayout.NORTH);
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
        displayStatsTimer = new Timer(250, event -> RefreshDisplayStats());
        displayStatsTimer.start();
        RefreshAppShortcuts();
        ApplyWindowMode();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                displayStatsTimer.stop();
                emulation.StopEmulation();
                dispose();
                System.exit(0);
            }
        });
        setVisible(true);
    }

    private JComponent BuildHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(Styling.appBackgroundColour);
        header.setBorder(BorderFactory.createEmptyBorder(24, 26, 18, 26));

        JPanel titleBlock = new JPanel(new BorderLayout(0, 6));
        titleBlock.setOpaque(false);

        titleLabel = new JLabel(UiText.MainWindow.HEADER_TITLE);
        titleLabel.setFont(Styling.titleFont);
        titleLabel.setForeground(Styling.accentColour);

        subtitleLabel = new JLabel(UiText.MainWindow.HEADER_SUBTITLE);
        subtitleLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 14f));
        subtitleLabel.setForeground(Styling.mutedTextColour);

        titleBlock.add(titleLabel, BorderLayout.NORTH);
        titleBlock.add(subtitleLabel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(CreateHeaderButton(UiText.MainWindow.BUTTON_OPEN_ROM, Action.LOADROM));
        actions.add(CreateHeaderButton(UiText.MainWindow.BUTTON_LIBRARY, Action.LIBRARY));
        actions.add(CreateHeaderButton(UiText.MainWindow.BUTTON_OPEN_IPS_PATCH, Action.LOADIPS));
        actions.add(CreateHeaderButton(UiText.MainWindow.BUTTON_OPTIONS, Action.OPTIONS));

        header.add(titleBlock, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JComponent BuildDisplaySection() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 24));

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

        JPanel displaySurface = new JPanel(new BorderLayout());
        displaySurface.setOpaque(false);
        displaySurface.add(display, BorderLayout.CENTER);

        displayFrame.add(displaySurface, BorderLayout.CENTER);

        displayCard.add(labelRow, BorderLayout.NORTH);
        displayCard.add(displayFrame, BorderLayout.CENTER);
        serialCard = (JPanel) BuildSerialOutputPanel();

        content.add(displayCard, BorderLayout.CENTER);
        content.add(serialCard, BorderLayout.EAST);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
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

        gameArtPanel = (JPanel) BuildGameArtPanel();
        JPanel spacerPanel = new JPanel();
        spacerPanel.setOpaque(false);
        spacerPanel.setPreferredSize(new Dimension(0, 14));
        spacerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        spacerPanel.setMinimumSize(new Dimension(0, 14));
        sidePanelSpacer = spacerPanel;

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        serialSectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidePanelSpacer.setAlignmentX(Component.LEFT_ALIGNMENT);
        gameArtPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(serialSectionPanel);
        body.add(sidePanelSpacer);
        body.add(gameArtPanel);

        panel.add(body, BorderLayout.CENTER);
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
        JButton button = new JButton(text);
        styleHeaderButton(button);
        button.addActionListener(new GUIActions(this, action, emulation));
        headerButtons.add(button);
        return button;
    }

    private void styleHeaderButton(JButton button) {
        WindowUiSupport.styleSecondaryButton(button, Styling.accentColour, Styling.surfaceBorderColour);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.surfaceBorderColour, 1),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
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
                menu.addSeparator();
            } else {
                AddMenuItem(menu, menuEntries[index], actions[actionIndex]);
                actionIndex++;
            }
        }

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
        menu.addSeparator();
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_PAUSE_GAME, Action.PAUSEGAME);
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_RESET_GAME, Action.RESETGAME);
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_CLOSE_GAME, Action.CLOSEGAME);
        if (backend.Profile().capabilities().supportsCheats()) {
            cheatManagerMenuItem = new JMenuItem(UiText.MainWindow.GAME_MENU_CHEATS);
            ConfigureMenuItem(cheatManagerMenuItem);
            cheatManagerMenuItem.addActionListener(event -> new CheatManagerWindow(this, emulation));
            menu.add(cheatManagerMenuItem);
        }
        menu.addSeparator();

        JMenuItem saveStateManagerItem = new JMenuItem(UiText.MainWindow.GAME_MENU_SAVE_STATE_MANAGER);
        ConfigureMenuItem(saveStateManagerItem);
        saveStateManagerItem.addActionListener(event -> new SaveStateManagerWindow(this));
        menu.add(saveStateManagerItem);

        menu.addSeparator();
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
                menu.addSeparator();
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
        return menu;
    }

    private JMenu CreateRecentGamesMenu() {
        JMenu menu = new JMenu(UiText.MainWindow.GAME_MENU_LOAD_RECENT);
        ConfigureMenu(menu);
        RefreshRecentGamesMenu(menu);
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
            boolean showGameArt = Settings.gameArtDisplayMode != GameArtDisplayMode.NONE;
            SyncFullViewActionState(fillWindow);

            getContentPane().setBackground(fillWindow ? Styling.displayBackgroundColour : Styling.appBackgroundColour);
            if (headerPanel != null) {
                headerPanel.setVisible(!fillWindow);
            }
            if (statusBar != null) {
                statusBar.setVisible(!fillWindow);
            }
            if (labelRow != null) {
                labelRow.setVisible(!fillWindow);
            }
            if (serialCard != null) {
                serialCard.setVisible(!fillWindow && (showSerial || showGameArt));
            }
            if (serialSectionPanel != null) {
                serialSectionPanel.setVisible(showSerial);
            }
            if (gameArtPanel != null) {
                gameArtPanel.setVisible(showGameArt);
            }
            if (sidePanelSpacer != null) {
                sidePanelSpacer.setVisible(showSerial && showGameArt);
            }
            if (displayWrapper != null) {
                displayWrapper.setOpaque(true);
                displayWrapper
                        .setBackground(fillWindow ? Styling.displayBackgroundColour : Styling.appBackgroundColour);
                displayWrapper.setBorder(fillWindow
                        ? BorderFactory.createEmptyBorder()
                        : BorderFactory.createEmptyBorder(0, 24, 0, 24));
            }
            if (displayCard != null) {
                displayCard.setBackground(fillWindow ? Styling.displayBackgroundColour : Styling.surfaceColour);
                displayCard.setBorder(fillWindow ? BorderFactory.createEmptyBorder() : createSurfaceCardBorder());
            }
            if (displayFrame != null) {
                displayFrame.setBackground(Styling.displayFrameColour);
                displayFrame.setBorder(fillWindow ? BorderFactory.createEmptyBorder() : createDisplayFrameBorder());
            }
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
                        ConfigureMenu(menu);
                        for (Component item : menu.getMenuComponents()) {
                            if (item instanceof JMenuItem menuItem) {
                                ConfigureMenuItem(menuItem);
                            }
                        }
                    }
                }
            }

            if (headerPanel != null) {
                headerPanel.setBackground(Styling.appBackgroundColour);
            }
            applyForeground(Styling.accentColour, titleLabel, displayTitleLabel, serialTitleLabel, gameArtTitleLabel,
                    romLabel);
            applyForeground(Styling.mutedTextColour, subtitleLabel, displayHintLabel, serialHintLabel, gameArtHintLabel,
                    stateLabel);
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
            if (gameArtPreviewPanel != null) {
                gameArtPreviewPanel.setBackground(Styling.displayFrameColour);
                gameArtPreviewPanel.setBorder(WindowUiSupport.createLineBorder(Styling.displayFrameBorderColour));
            }

            for (JButton button : headerButtons) {
                styleHeaderButton(button);
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
    public void SetLoadedRom(ROM rom) {
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
        currentLoadedGame = game;
        romNameLookupPending = game != null && !allowFallback && GameMetadataStore.GetLibretroTitle(game).isEmpty();
        String romText = ResolveDisplayedRomName(game, allowFallback);
        Runnable update = () -> {
            if (romLabel != null) {
                romLabel.setText(romText);
            }
            RefreshSaveStateMenus();
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
        if (serialOutputArea == null || text == null || text.isEmpty()) {
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
            if (serialOutputArea == null) {
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
        SetSerialOutput("");
    }

    private void FlushPendingSerialOutput() {
        String text;
        synchronized (pendingSerialAppend) {
            text = pendingSerialAppend.toString();
            pendingSerialAppend.setLength(0);
        }

        serialAppendQueued.set(false);
        if (serialOutputArea == null || text.isEmpty()) {
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

        menu.removeAll();
        List<GameLibraryStore.LibraryEntry> recentEntries = GameLibraryStore.GetRecentEntries(Settings.loadRecentMenuLimit);
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

        menu.addSeparator();
        JMenuItem clearRecentItem = new JMenuItem(UiText.MainWindow.GAME_MENU_CLEAR_RECENT);
        ConfigureMenuItem(clearRecentItem);
        clearRecentItem.setEnabled(!recentEntries.isEmpty());
        clearRecentItem.addActionListener(event -> {
            GameLibraryStore.ClearRecentHistory();
            RefreshRecentGamesMenu();
        });
        menu.add(clearRecentItem);
    }

    private void ConfigureMenuItem(JMenuItem menuItem) {
        if (menuItem == null) {
            return;
        }
        menuItem.setFont(Styling.menuFont);
        menuItem.setForeground(Styling.accentColour);
        menuItem.setBackground(Styling.surfaceColour);
        menuItem.setOpaque(true);
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
            menu.getPopupMenu().setBackground(Styling.surfaceColour);
            menu.getPopupMenu().setBorder(WindowUiSupport.createLineBorder(Styling.surfaceBorderColour));
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
        if (game == null || Settings.gameArtDisplayMode == GameArtDisplayMode.NONE) {
            ClearGameArt();
            return;
        }

        int requestVersion = gameArtRequestVersion.incrementAndGet();
        SetGameArtPlaceholder(UiText.MainWindow.FETCHING_ARTWORK,
                UiText.MainWindow.LookingUpArtwork(Settings.gameArtDisplayMode.Label(), game.displayName()));

        CompletableFuture
                .supplyAsync(() -> GameArtProvider.FindGameArt(game, Settings.gameArtDisplayMode))
                .thenAccept(result -> ApplyGameArtResult(game, requestVersion, result))
                .exceptionally(exception -> {
                    ApplyGameArtResult(game, requestVersion, Optional.empty());
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
    public ROM GetCurrentLoadedRom() {
        return currentLoadedGame instanceof ROM rom ? rom : null;
    }

    /**
     * Returns the current ROM label using the active display-name settings.
     *
     * @return displayed ROM name
     */
    public String GetCurrentLoadedRomDisplayName() {
        return ResolveDisplayedRomName(currentLoadedGame, !romNameLookupPending);
    }

    private void ApplyGameArtResult(EmulatorGame game, int requestVersion, Optional<GameArtResult> result) {
        Runnable apply = () -> {
            if (gameArtRequestVersion.get() != requestVersion) {
                return;
            }

            if (result.isPresent()) {
                if (result.get().matchedGameName() != null && !result.get().matchedGameName().isBlank()) {
                    GameMetadataStore.RememberLibretroTitle(game, result.get().matchedGameName());
                    SetLoadedGame(game);
                }
                BufferedImage scaledImage = GameArtScaler.ScaleToFit(result.get().image(), gameArtPreviewWidth - 24,
                        gameArtPreviewHeight - 24, true);
                gameArtLabel.setIcon(new ImageIcon(scaledImage));
                gameArtLabel.setText("");
                gameArtLabel.setForeground(Styling.fpsForegroundColour);
                gameArtHintLabel.setText(result.get().sourceLabel());
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

    private void RefreshDisplayStats() {
        if (displayHintLabel == null || display == null) {
            return;
        }

        DuckDisplay.PresentationStats stats = display.SnapshotPresentationStats();
        if (stats == null || stats.paintedFps() <= 0.0) {
            displayHintLabel.setText(UiText.MainWindow.DISPLAY_HINT);
            return;
        }

        displayHintLabel.setText(String.format("%s  %.1f fps  %.2f ms",
                UiText.MainWindow.DISPLAY_HINT,
                stats.paintedFps(),
                stats.averageFrameTimeMs()));
    }
}

