package com.blackaby.Frontend;

import javax.swing.*;

import com.blackaby.Backend.Emulation.DuckEmulation;

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
            { "File", "Open Game", "Close Game", "", "Save State", "Load State", "", "Options", "Exit", "" },
            { "Game", "" },
            { "View", "Fullscreen", "Windowed", "" },
            { "Help", "Tutorial", "About", "", }
    };

    /**
     * Constructor for MainWindow.
     * 
     * @return A new MainWindow object.
     */
    public MainWindow() {
        super("GameDuck");
        // Initialising the menu bar
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(Styling.BACKGROUND_COLOR);
        menuBar.setForeground(Styling.MENU_FOREGROUND_COLOR);
        menuBar.setFont(Styling.MENU_FONT);
        for (String[] items : menuItems) {
            addMenu(menuBar, items);
        }
        setJMenuBar(menuBar);

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

        // Begin the emulation
        emulation = new DuckEmulation(display);
        emulation.startEmulation("test.rom");

        setVisible(true);
    }

    /**
     * Adds a menu item to a menu.
     * Simplifies the creation of menu items.
     * 
     * @param menu The menu to add the item to.
     * @param item The name of the item to add.
     */
    private void addMenuItem(JMenu menu, String item) {
        JMenuItem menuItem = new JMenuItem(item);
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
    private void addMenu(JMenuBar menuBar, String[] items) {
        JMenu menu = new JMenu(items[0]);
        menu.setBackground(Styling.MENU_BACKGROUND_COLOR);
        menu.setForeground(Styling.MENU_FOREGROUND_COLOR);
        menu.setFont(Styling.MENU_FONT);
        for (int i = 1; i < items.length; i++) {
            // If the item is a empty string add a separator
            if (items[i].equals("")) {
                menu.addSeparator();
            } else {
                addMenuItem(menu, items[i]);
            }
        }
        menuBar.add(menu);
    }
}
