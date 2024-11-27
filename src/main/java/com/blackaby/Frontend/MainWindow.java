package com.blackaby.Frontend;

import javax.swing.*;

import com.blackaby.Backend.Emulation.DuckEmulation;
import com.blackaby.Backend.Helpers.GUIActions;
import com.blackaby.Backend.Helpers.GUIActions.Action;

import java.awt.*;

/**
 * This class is the main window for the GameDuck application.
 * It is the first window that the user sees when they open the application and
 * contains the display.
 * It is a subclass of DuckWindow.
 */
public class MainWindow extends DuckWindow {

    private DuckDisplay display;
    private DuckEmulation emulation;

    /**
     * The menu items for the menu bar.
     */
    private final String[][] menuItems = {
            { "File", "Open Game", "Pause Game", "Close Game", "", "Save State", "Load State", "", "Options", "Exit",
                    "" },
            { "Game", "" },
            { "View", "Fullscreen", "Windowed", "" },
            { "Help", "Tutorial", "About", "", }
    };
    private final Action menuActions[][] = {
            { Action.LOADROM, Action.PAUSEGAME, Action.CLOSEGAME, Action.SAVESTATE, Action.LOADSTATE, Action.OPTIONS,
                    Action.EXIT },
            { Action.DEBUG },
            { Action.FULLSCREEN, Action.WINDOWED },
            { Action.TUTORIAL, Action.ABOUT }
    };

    /**
     * Constructor for MainWindow.
     * 
     * @return A new MainWindow object.
     */
    public MainWindow() {
        super("GameDuck");

        // Main section of the window
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();

        getContentPane().setBackground(Styling.DISPLAY_BACKGROUND_COLOR);

        // Display
        display = new DuckDisplay();
        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER;
        add(display, c);

        // Attach the emulation
        emulation = new DuckEmulation(display);

        // Initialising the menu bar
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(Styling.BACKGROUND_COLOR);
        menuBar.setForeground(Styling.MENU_FOREGROUND_COLOR);
        menuBar.setFont(Styling.MENU_FONT);
        for (int i = 0; i < menuItems.length; i++) {
            addMenu(menuBar, menuItems[i], menuActions[i]);
        }
        setJMenuBar(menuBar);

        setVisible(true);
    }

    /**
     * Adds a menu item to a menu.
     * Simplifies the creation of menu items.
     * 
     * @param menu The menu to add the item to.
     * @param item The name of the item to add.
     */
    private void addMenuItem(JMenu menu, String item, Action action) {
        JMenuItem menuItem = new JMenuItem(item);
        menuItem.addActionListener(new GUIActions(action, emulation));
        menuItem.setFont(Styling.MENU_FONT);
        menu.add(menuItem);
    }

    /**
     * Adds a menu to the menu bar.
     * Simplifies the creation of menus by.
     * 
     * @param menuBar The menu bar to add the menu to.
     * @param items   The title of the menu in items[0] and the rest of the items to
     *                add to the menu. Breaks designated by empty strings.
     */
    private void addMenu(JMenuBar menuBar, String[] items, Action[] actions) {
        JMenu menu = new JMenu(items[0]);
        // remove the first item from items
        String[] tempItems = new String[items.length - 1];
        System.arraycopy(items, 1, tempItems, 0, tempItems.length);
        items = tempItems;
        menu.setBackground(Styling.MENU_BACKGROUND_COLOR);
        menu.setForeground(Styling.MENU_FOREGROUND_COLOR);
        menu.setFont(Styling.MENU_FONT);
        for (int i = 0, actionCount = 0; i < items.length; i++) {
            // If the item is a empty string add a separator
            if (items[i].equals("")) {
                menu.addSeparator();
            } else {
                addMenuItem(menu, items[i], actions[actionCount]);
                actionCount++;
            }
        }
        menuBar.add(menu);
    }
}
