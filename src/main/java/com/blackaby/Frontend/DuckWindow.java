package com.blackaby.Frontend;

import javax.swing.*;
import java.awt.*;

/**
 * This class is a base class for all windows in GameDuck.
 * It has some default properties for all windows, and customisable
 * properties passed in as parameters.
 */
public class DuckWindow extends JFrame {
    /**
     * Constructor for DuckWindow.
     * 
     * @param title     The title of the window.
     * 
     * @param width     The width of the window.
     * 
     * @param height    The height of the window.
     * 
     * @param resizable Whether the window is resizable.
     * 
     * @return A new DuckWindow object.
     */
    public DuckWindow(String title, int width, int height, boolean resizable) {
        super();
        setSize(width, height);
        setResizable(resizable);
        getContentPane().setBackground(Styling.BACKGROUND_COLOR);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setTitle(title);
        setLocationRelativeTo(null);
        setIconImage(new ImageIcon("src/main/resources/duck.png").getImage());
    }

    /**
     * Constructor for DuckWindow with default values.
     * 
     * @return A new DuckWindow object.
     */
    public DuckWindow() {
        this("GameDuck", 800, 600, true);
    }

    /**
     * Constructor for DuckWindow with default values.
     * 
     * @param title The title of the window.
     * 
     * @return A new DuckWindow object.
     */
    public DuckWindow(String title) {
        this(title, 800, 600, true);
    }

    /**
     * Constructor for DuckWindow with default values.
     * 
     * @param title  The title of the window.
     * 
     * @param width  The width of the window.
     * 
     * @param height The height of the window.
     * 
     * @return A new DuckWindow object.
     */
    public DuckWindow(String title, int width, int height) {
        this(title, width, height, true);
    }
}
