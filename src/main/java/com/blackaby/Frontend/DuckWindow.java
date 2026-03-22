package com.blackaby.Frontend;

import com.blackaby.Misc.UiText;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;

/**
 * Base window class for the GameDuck desktop UI.
 * <p>
 * The class provides the shared frame styling, icon, sizing defaults, and the
 * common maximise and fullscreen behaviour used by the host windows.
 */
public class DuckWindow extends JFrame {

    private boolean fullscreen;
    private boolean maximised;
    private Dimension windowedSize;
    private Point windowedLocation;
    private Dimension sizeBeforeMaximise;
    private Point locationBeforeMaximise;
    private final String baseTitle;

    /**
     * Creates a window with explicit dimensions and resize behaviour.
     *
     * @param title window title
     * @param width initial width in pixels
     * @param height initial height in pixels
     * @param resizable whether the frame can be resized by the user
     */
    public DuckWindow(String title, int width, int height, boolean resizable) {
        super();
        setSize(width, height);
        setResizable(resizable);
        getContentPane().setBackground(Styling.backgroundColour);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setTitle(title);
        baseTitle = title;
        setLocationRelativeTo(null);
        setIconImage(new ImageIcon("src/main/resources/duck.png").getImage());
        windowedSize = Toolkit.getDefaultToolkit().getScreenSize();
        sizeBeforeMaximise = Toolkit.getDefaultToolkit().getScreenSize();
        locationBeforeMaximise = getLocation();
        windowedLocation = getLocation();
    }

    /**
     * Creates a standard resizable window with the default title and size.
     */
    public DuckWindow() {
        this(UiText.Common.APP_NAME, 800, 600, true);
    }

    /**
     * Creates a standard resizable window with a custom title.
     *
     * @param title window title
     */
    public DuckWindow(String title) {
        this(title, 800, 600, true);
    }

    /**
     * Creates a standard resizable window with a custom size.
     *
     * @param title window title
     * @param width initial width in pixels
     * @param height initial height in pixels
     */
    public DuckWindow(String title, int width, int height) {
        this(title, width, height, true);
    }

    /**
     * Toggles the maximised state of the frame.
     */
    public void ToggleMaximise() {
        if (maximised) {
            setExtendedState(JFrame.NORMAL);
            setSize(sizeBeforeMaximise);
            setLocation(locationBeforeMaximise);
        } else {
            sizeBeforeMaximise = getSize();
            locationBeforeMaximise = getLocation();
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
        maximised = !maximised;
    }

    /**
     * Toggles exclusive fullscreen mode for the frame.
     */
    public void ToggleFullScreen() {
        GraphicsDevice graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (fullscreen) {
            graphicsDevice.setFullScreenWindow(null);
            dispose();
            setUndecorated(false);
            setSize(windowedSize);
            setLocation(windowedLocation);
            setVisible(true);
        } else {
            windowedSize = getSize();
            windowedLocation = getLocation();
            dispose();
            setUndecorated(true);
            setVisible(true);
            graphicsDevice.setFullScreenWindow(this);
        }

        fullscreen = !fullscreen;
    }

    /**
     * Updates the frame title by appending state text to the base title.
     *
     * @param subtitleParts title suffix parts in display order
     */
    public void SetSubtitle(String... subtitleParts) {
        StringBuilder builder = new StringBuilder(baseTitle);
        for (String subtitlePart : subtitleParts) {
            builder.append(" - ").append(subtitlePart);
        }
        String nextTitle = builder.toString();
        if (SwingUtilities.isEventDispatchThread()) {
            setTitle(nextTitle);
        } else {
            SwingUtilities.invokeLater(() -> setTitle(nextTitle));
        }
    }

    @Deprecated
    public void toggleMaximise() {
        ToggleMaximise();
    }

    @Deprecated
    public void toggleFullScreen() {
        ToggleFullScreen();
    }

    @Deprecated
    public void subtitle(String... subtitleParts) {
        SetSubtitle(subtitleParts);
    }
}
