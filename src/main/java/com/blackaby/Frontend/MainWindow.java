package com.blackaby.Frontend;

import com.blackaby.Backend.Emulation.DuckEmulation;
import com.blackaby.Backend.Emulation.Misc.ROM;
import com.blackaby.Backend.Helpers.GameMetadataStore;
import com.blackaby.Backend.Helpers.GUIActions;
import com.blackaby.Backend.Helpers.GUIActions.Action;
import com.blackaby.Backend.Helpers.GameArtProvider;
import com.blackaby.Backend.Helpers.GameArtProvider.GameArtResult;
import com.blackaby.Backend.Helpers.QuickStateManager;
import com.blackaby.Misc.AppShortcut;
import com.blackaby.Misc.Config;
import com.blackaby.Misc.GameArtDisplayMode;
import com.blackaby.Misc.GameNameBracketDisplayMode;
import com.blackaby.Misc.Settings;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hosts the main emulator window and its desktop controls.
 * <p>
 * The window owns the display surface, menu bar, status strips, and the quick
 * actions used to drive the active emulator instance.
 */
public class MainWindow extends DuckWindow {

    private static final int gameArtPreviewWidth = 280;
    private static final int gameArtPreviewHeight = 220;
    private static final DateTimeFormatter saveStateTimestampFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final DuckDisplay display;
    private final DuckEmulation emulation;
    private final InputRouter inputRouter;
    private final List<JButton> headerButtons = new ArrayList<>();
    private final EnumMap<Action, JMenuItem> menuItemsByAction = new EnumMap<>(Action.class);
    private final AtomicInteger gameArtRequestVersion = new AtomicInteger();
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
    private JMenu saveStateMenu;
    private JMenu loadStateMenu;
    private final JMenuItem[] saveStateSlotItems = new JMenuItem[QuickStateManager.maxSlot + 1];
    private final JMenuItem[] loadStateSlotItems = new JMenuItem[QuickStateManager.maxSlot + 1];

    private JMenuBar menuBar;
    private JLabel fpsLabel;
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
    private JPanel displayOverlay;
    private JPanel statusBar;
    private JTextArea serialOutputArea;
    private JScrollPane serialOutputScrollPane;
    private ROM currentLoadedRom;
    private boolean romNameLookupPending;

    private final String[][] menuItems = UiText.MainWindow.MENU_ITEMS;

    private final Action[][] menuActions = {
            { Action.OPTIONS, Action.EXIT },
            { Action.LIBRARY, Action.LOADROM, Action.LOADIPS, Action.PAUSEGAME, Action.RESETGAME, Action.CLOSEGAME,
                    Action.SAVESTATE, Action.LOADSTATE },
            { Action.FULL_VIEW, Action.FULLSCREEN, Action.MAXIMISE, Action.FRAMECOUNTER },
            { Action.ABOUT }
    };

    /**
     * Creates the main host window and initialises the attached emulator.
     */
    public MainWindow() {
        super(UiText.MainWindow.WINDOW_TITLE);

        display = new DuckDisplay();
        emulation = new DuckEmulation(this, display);
        inputRouter = new InputRouter(this, emulation);
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
        menuBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Styling.surfaceBorderColour),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

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
        RefreshAppShortcuts();
        ApplyWindowMode();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
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
        displayCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.surfaceBorderColour, 1),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));

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
        displayFrame.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));

        fpsLabel = new JLabel(UiText.MainWindow.FpsLabel(0));
        fpsLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        fpsLabel.setForeground(Styling.fpsForegroundColour);
        fpsLabel.setOpaque(true);
        fpsLabel.setBackground(Styling.fpsBackgroundColour);
        fpsLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        fpsLabel.setVisible(false);

        displayOverlay = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
        displayOverlay.setOpaque(false);
        displayOverlay.add(fpsLabel);

        JPanel displaySurface = new JPanel(new BorderLayout());
        displaySurface.setOpaque(false);
        displaySurface.add(display, BorderLayout.CENTER);

        displayFrame.add(displaySurface, BorderLayout.CENTER);
        displayFrame.add(displayOverlay, BorderLayout.NORTH);

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
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.surfaceBorderColour, 1),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));

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
        serialOutputScrollPane.setBorder(BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1));
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
        gameArtPreviewPanel.setBorder(BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1));
        gameArtPreviewPanel.setPreferredSize(new Dimension(gameArtPreviewWidth, gameArtPreviewHeight));
        gameArtPreviewPanel.add(gameArtLabel, BorderLayout.CENTER);

        panel.add(titleRow, BorderLayout.NORTH);
        panel.add(gameArtPreviewPanel, BorderLayout.CENTER);
        return panel;
    }

    private JComponent BuildStatusBar() {
        JPanel nextStatusBar = new JPanel(new BorderLayout(12, 0));
        nextStatusBar.setBackground(Styling.statusBackgroundColour);
        nextStatusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Styling.surfaceBorderColour),
                BorderFactory.createEmptyBorder(12, 24, 12, 24)));

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
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(Styling.buttonSecondaryBackground);
        button.setForeground(Styling.accentColour);
        button.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.surfaceBorderColour, 1),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        button.addActionListener(new GUIActions(this, action, emulation));
        headerButtons.add(button);
        return button;
    }

    private void AddMenuItem(JMenu menu, String item, Action action) {
        JMenuItem menuItem = new JMenuItem(item);
        menuItem.addActionListener(new GUIActions(this, action, emulation));
        menuItem.setFont(Styling.menuFont);
        menuItem.setForeground(Styling.accentColour);
        menuItemsByAction.put(action, menuItem);
        menu.add(menuItem);
    }

    private void AddMenu(JMenuBar currentMenuBar, String[] items, Action[] actions) {
        JMenu menu = new JMenu(items[0]);
        String[] menuEntries = new String[items.length - 1];
        System.arraycopy(items, 1, menuEntries, 0, menuEntries.length);

        menu.setBackground(Styling.surfaceColour);
        menu.setForeground(Styling.accentColour);
        menu.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));

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
        menu.setBackground(Styling.surfaceColour);
        menu.setForeground(Styling.accentColour);
        menu.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));

        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_LIBRARY, Action.LIBRARY);
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_OPEN_ROM, Action.LOADROM);
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_OPEN_IPS_PATCH, Action.LOADIPS);
        menu.addSeparator();
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_PAUSE_GAME, Action.PAUSEGAME);
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_RESET_GAME, Action.RESETGAME);
        AddMenuItem(menu, UiText.MainWindow.GAME_MENU_CLOSE_GAME, Action.CLOSEGAME);
        menu.addSeparator();

        JMenuItem saveStateManagerItem = new JMenuItem(UiText.MainWindow.GAME_MENU_SAVE_STATE_MANAGER);
        saveStateManagerItem.setFont(Styling.menuFont);
        saveStateManagerItem.setForeground(Styling.accentColour);
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
        menu.setFont(Styling.menuFont);
        menu.setForeground(Styling.accentColour);

        for (int slot = QuickStateManager.quickSlot; slot <= QuickStateManager.maxSlot; slot++) {
            JMenuItem menuItem = new JMenuItem();
            menuItem.setFont(Styling.menuFont);
            menuItem.setForeground(Styling.accentColour);
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
     * Updates the on-screen frame counter.
     *
     * @param frames frame count collected over the last second
     */
    public void UpdateFrameCounter(int frames) {
        Runnable update = () -> {
            fpsLabel.setText(UiText.MainWindow.FpsLabel(frames));
            stateLabel.setText(frames > 0 ? UiText.MainWindow.StatusBarFps(frames) : UiText.Common.RUNNING);
        };

        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    /**
     * Toggles the FPS badge on the display card.
     */
    public void ToggleFrameCounter() {
        Runnable toggle = () -> fpsLabel.setVisible(!fpsLabel.isVisible());
        if (SwingUtilities.isEventDispatchThread()) {
            toggle.run();
        } else {
            SwingUtilities.invokeLater(toggle);
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
        super.SetSubtitle(subtitleParts);

        String stateText = subtitleParts.length > 1 ? subtitleParts[1] : UiText.Common.READY;
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
                displayCard.setBorder(fillWindow
                        ? BorderFactory.createEmptyBorder()
                        : BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(Styling.surfaceBorderColour, 1),
                                BorderFactory.createEmptyBorder(18, 18, 18, 18)));
            }
            if (displayFrame != null) {
                displayFrame.setBackground(Styling.displayFrameColour);
                displayFrame.setBorder(fillWindow
                        ? BorderFactory.createEmptyBorder()
                        : BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1),
                                BorderFactory.createEmptyBorder(18, 18, 18, 18)));
            }
            if (displayOverlay != null) {
                displayOverlay.setBorder(fillWindow
                        ? BorderFactory.createEmptyBorder(12, 0, 0, 12)
                        : BorderFactory.createEmptyBorder());
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
                menuBar.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, Styling.surfaceBorderColour),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)));
                for (int index = 0; index < menuBar.getMenuCount(); index++) {
                    JMenu menu = menuBar.getMenu(index);
                    if (menu != null) {
                        menu.setBackground(Styling.surfaceColour);
                        menu.setForeground(Styling.accentColour);
                        for (Component item : menu.getMenuComponents()) {
                            if (item instanceof JMenuItem menuItem) {
                                menuItem.setForeground(Styling.accentColour);
                                menuItem.setBackground(Styling.surfaceColour);
                            }
                        }
                    }
                }
            }

            if (headerPanel != null) {
                headerPanel.setBackground(Styling.appBackgroundColour);
            }
            if (titleLabel != null) {
                titleLabel.setForeground(Styling.accentColour);
            }
            if (subtitleLabel != null) {
                subtitleLabel.setForeground(Styling.mutedTextColour);
            }
            if (displayTitleLabel != null) {
                displayTitleLabel.setForeground(Styling.accentColour);
            }
            if (displayHintLabel != null) {
                displayHintLabel.setForeground(Styling.mutedTextColour);
            }
            if (serialTitleLabel != null) {
                serialTitleLabel.setForeground(Styling.accentColour);
            }
            if (serialHintLabel != null) {
                serialHintLabel.setForeground(Styling.mutedTextColour);
            }
            if (gameArtTitleLabel != null) {
                gameArtTitleLabel.setForeground(Styling.accentColour);
            }
            if (gameArtHintLabel != null) {
                gameArtHintLabel.setForeground(Styling.mutedTextColour);
            }
            if (gameArtLabel != null) {
                gameArtLabel.setForeground(
                        gameArtLabel.getIcon() == null ? Styling.mutedTextColour : Styling.fpsForegroundColour);
            }
            if (romLabel != null) {
                romLabel.setForeground(Styling.accentColour);
            }
            if (stateLabel != null) {
                stateLabel.setForeground(Styling.mutedTextColour);
            }
            if (statusBar != null) {
                statusBar.setBackground(Styling.statusBackgroundColour);
                statusBar.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 0, 0, 0, Styling.surfaceBorderColour),
                        BorderFactory.createEmptyBorder(12, 24, 12, 24)));
            }
            if (serialCard != null) {
                serialCard.setBackground(Styling.surfaceColour);
                serialCard.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Styling.surfaceBorderColour, 1),
                        BorderFactory.createEmptyBorder(18, 18, 18, 18)));
            }
            if (serialOutputArea != null) {
                serialOutputArea.setBackground(Styling.displayFrameColour);
                serialOutputArea.setForeground(Styling.fpsForegroundColour);
            }
            if (serialOutputScrollPane != null) {
                serialOutputScrollPane.setBorder(BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1));
                serialOutputScrollPane.getViewport().setBackground(Styling.displayFrameColour);
            }
            if (gameArtPreviewPanel != null) {
                gameArtPreviewPanel.setBackground(Styling.displayFrameColour);
                gameArtPreviewPanel.setBorder(BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1));
            }

            for (JButton button : headerButtons) {
                button.setBackground(Styling.buttonSecondaryBackground);
                button.setForeground(Styling.accentColour);
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Styling.surfaceBorderColour, 1),
                        BorderFactory.createEmptyBorder(8, 14, 8, 14)));
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
        SetLoadedRom(rom, true);
    }

    /**
     * Updates the status bar with either a resolved libretro title or a temporary
     * loading state.
     *
     * @param rom           active ROM
     * @param allowFallback whether filename fallback should be used
     */
    public void SetLoadedRom(ROM rom, boolean allowFallback) {
        currentLoadedRom = rom;
        romNameLookupPending = rom != null && !allowFallback && GameMetadataStore.GetLibretroTitle(rom).isEmpty();
        String romText = ResolveDisplayedRomName(rom, allowFallback);
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
        SetLoadedRom(currentLoadedRom, !romNameLookupPending);
    }

    private String ResolveDisplayedRomName(ROM rom, boolean allowFallback) {
        if (rom == null) {
            return UiText.MainWindow.NO_ROM_LOADED;
        }

        Optional<String> libretroTitle = GameMetadataStore.GetLibretroTitle(rom);
        if (libretroTitle.isPresent()) {
            return ApplyGameNameDisplayMode(libretroTitle.get());
        }

        if (!allowFallback) {
            return UiText.MainWindow.RETRIEVING_GAME_NAME;
        }

        String sourceName = rom.GetSourceName();
        if (sourceName != null && !sourceName.isBlank()) {
            return ApplyGameNameDisplayMode(sourceName);
        }

        return rom.GetName() == null || rom.GetName().isBlank()
                ? UiText.MainWindow.NO_ROM_LOADED
                : ApplyGameNameDisplayMode(rom.GetName());
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

            List<QuickStateManager.StateSlotInfo> slots = QuickStateManager.DescribeSlots(currentLoadedRom);
            boolean hasLoadedRom = currentLoadedRom != null;

            for (QuickStateManager.StateSlotInfo slotInfo : slots) {
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
        Runnable append = () -> {
            if (serialOutputArea == null || text == null || text.isEmpty()) {
                return;
            }
            serialOutputArea.append(text);
            serialOutputArea.setCaretPosition(serialOutputArea.getDocument().getLength());
        };

        if (SwingUtilities.isEventDispatchThread()) {
            append.run();
        } else {
            SwingUtilities.invokeLater(append);
        }
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
        SetSerialOutput("");
    }

    /**
     * Begins an asynchronous lookup for the supplied ROM artwork.
     *
     * @param rom active ROM
     */
    public void LoadGameArt(ROM rom) {
        if (rom == null || Settings.gameArtDisplayMode == GameArtDisplayMode.NONE) {
            ClearGameArt();
            return;
        }

        int requestVersion = gameArtRequestVersion.incrementAndGet();
        SetGameArtPlaceholder(UiText.MainWindow.FETCHING_ARTWORK,
                UiText.MainWindow.LookingUpArtwork(Settings.gameArtDisplayMode.Label(), rom.GetName()));

        CompletableFuture
                .supplyAsync(() -> GameArtProvider.FindGameArt(rom, Settings.gameArtDisplayMode))
                .thenAccept(result -> ApplyGameArtResult(rom, requestVersion, result))
                .exceptionally(exception -> {
                    ApplyGameArtResult(rom, requestVersion, Optional.empty());
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
    public DuckEmulation GetEmulation() {
        return emulation;
    }

    /**
     * Returns the currently loaded ROM shown by the main window.
     *
     * @return loaded ROM or {@code null}
     */
    public ROM GetCurrentLoadedRom() {
        return currentLoadedRom;
    }

    /**
     * Returns the current ROM label using the active display-name settings.
     *
     * @return displayed ROM name
     */
    public String GetCurrentLoadedRomDisplayName() {
        return ResolveDisplayedRomName(currentLoadedRom, !romNameLookupPending);
    }

    private void ApplyGameArtResult(ROM rom, int requestVersion, Optional<GameArtResult> result) {
        Runnable apply = () -> {
            if (gameArtRequestVersion.get() != requestVersion) {
                return;
            }

            if (result.isPresent()) {
                if (result.get().matchedGameName() != null && !result.get().matchedGameName().isBlank()) {
                    GameMetadataStore.RememberLibretroTitle(rom, result.get().matchedGameName());
                    SetLoadedRom(rom);
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
            SetLoadedRom(rom, true);
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
        if (Settings.gameArtDisplayMode == GameArtDisplayMode.NONE || currentLoadedRom == null) {
            ClearGameArt();
        } else {
            LoadGameArt(currentLoadedRom);
        }
    }

    @Deprecated
    public void refreshAppShortcuts() {
        RefreshAppShortcuts();
    }

    @Deprecated
    public void updateFrameCounter(int frames) {
        UpdateFrameCounter(frames);
    }

    @Deprecated
    public void toggleFrameCounter() {
        ToggleFrameCounter();
    }

    @Deprecated
    public void toggleFullView() {
        ToggleFullView();
    }

    @Deprecated
    public void subtitle(String... subtitleParts) {
        SetSubtitle(subtitleParts);
    }

    @Deprecated
    public void applyWindowMode() {
        ApplyWindowMode();
    }

    @Deprecated
    public void refreshWindowPanels() {
        RefreshWindowPanels();
    }

    @Deprecated
    public void refreshTheme() {
        RefreshTheme();
    }

    @Deprecated
    public void setLoadedRom(ROM rom) {
        SetLoadedRom(rom);
    }

    @Deprecated
    public void refreshLoadedRomDisplay() {
        RefreshLoadedRomDisplay();
    }

    @Deprecated
    public void loadGameArt(ROM rom) {
        LoadGameArt(rom);
    }

    @Deprecated
    public void clearGameArt() {
        ClearGameArt();
    }

    @Deprecated
    public DuckEmulation getEmulation() {
        return GetEmulation();
    }

    @Deprecated
    public ROM getCurrentLoadedRom() {
        return GetCurrentLoadedRom();
    }

    @Deprecated
    public String getCurrentLoadedRomDisplayName() {
        return GetCurrentLoadedRomDisplayName();
    }
}
