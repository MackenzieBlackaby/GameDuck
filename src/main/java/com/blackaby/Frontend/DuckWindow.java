package com.blackaby.Frontend;

import javax.swing.*;
import java.awt.*;

/**
 * This class is a base class for all windows in GameDuck.
 * It has some default properties for all windows, and customisable
 * properties passed in as parameters.
 */
public class DuckWindow extends JFrame {

    private boolean isFullscreen = false;
    private boolean isMaximised = false;
    private Dimension screenSize;
    private Point location;
    private Dimension sizeBeforeMax;
    private Point locationBeforeMax;
    private String title;

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
        this.title = title;
        setLocationRelativeTo(null);
        setIconImage(new ImageIcon("src/main/resources/duck.png").getImage());
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        sizeBeforeMax = Toolkit.getDefaultToolkit().getScreenSize();
        locationBeforeMax = getLocation();
        location = getLocation();
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

    public void toggleMaximise() {
        if (isMaximised) {
            setExtendedState(JFrame.NORMAL);
            setSize(sizeBeforeMax);
            setLocation(locationBeforeMax);
        } else {
            sizeBeforeMax = getSize();
            locationBeforeMax = getLocation();
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
        isMaximised = !isMaximised;
    }

    public void toggleFullScreen() {
        GraphicsDevice g = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (isFullscreen) {
            // Exit fullscreen
            g.setFullScreenWindow(null);

            dispose();
            setUndecorated(false);

            setSize(screenSize);
            setLocation(location);
            setVisible(true);
        } else {
            screenSize = getSize();
            location = getLocation();
            dispose();
            setUndecorated(true);
            setVisible(true);
            g.setFullScreenWindow(this);
        }

        isFullscreen = !isFullscreen;
    }

    public void subtitle(String... subtitle) {
        StringBuilder sb = new StringBuilder(title);
        for (String s : subtitle) {
            sb.append(" - ").append(s);
        }
        setTitle(sb.toString());
    }

}
