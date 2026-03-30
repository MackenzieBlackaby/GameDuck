package com.blackaby.Frontend;

import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.JWindow;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Base window class for the GameDuck desktop UI.
 * <p>
 * The class provides the shared frame styling, icon, sizing defaults, and the
 * common maximise and fullscreen behaviour used by the host windows.
 */
public class DuckWindow extends JFrame {

    private static final int TITLE_BAR_HEIGHT = 40;
    private static final int RESIZE_MARGIN = 6;
    private static final int DIR_NORTH = 1;
    private static final int DIR_SOUTH = 2;
    private static final int DIR_WEST = 4;
    private static final int DIR_EAST = 8;
    private static final Dimension TITLE_BUTTON_SIZE = new Dimension(34, 24);
    private static final int SNAP_MAXIMISE_THRESHOLD = 2;
    private static final int SNAP_EDGE_THRESHOLD = 24;
    private static final int SNAP_PREVIEW_INSET = 10;
    private static final int DRAG_RESTORE_THRESHOLD = 6;

    private final JPanel shellPanel;
    private final JPanel windowChromePanel;
    private final JPanel menuBarHost;
    private final JPanel titleBarPanel;
    private final JPanel bodyPanel;
    private final JLabel titleIconLabel;
    private final JLabel titleLabel;
    private final JButton minimiseButton;
    private final JButton maximiseButton;
    private final JButton closeButton;
    private final ResizeHandlePanel northResizeHandle;
    private final ResizeHandlePanel southResizeHandle;
    private final ResizeHandlePanel westResizeHandle;
    private final ResizeHandlePanel eastResizeHandle;
    private final String baseTitle;

    private enum WindowControl {
        MINIMISE,
        MAXIMISE,
        CLOSE
    }

    private enum SnapZone {
        NONE,
        LEFT_HALF,
        RIGHT_HALF,
        MAXIMISE
    }

    private JMenuBar attachedMenuBar;
    private boolean fullscreen;
    private boolean maximised;
    private boolean maximisedBeforeFullscreen;
    private SnapZone snappedZone = SnapZone.NONE;
    private Dimension windowedSize;
    private Point windowedLocation;
    private Dimension sizeBeforeMaximise;
    private Point locationBeforeMaximise;
    private Rectangle restoreBounds;
    private Point titleDragAnchorOnScreen;
    private Point titleDragWindowLocation;
    private boolean titleDragMoved;
    private float titleDragHorizontalRatio = 0.5f;
    private int titleDragVerticalOffset = TITLE_BAR_HEIGHT / 2;
    private Rectangle resizeStartBounds;
    private Point resizeStartOnScreen;
    private JWindow snapPreviewWindow;
    private final MouseAdapter windowDragAdapter = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent event) {
            if (!SwingUtilities.isLeftMouseButton(event) || fullscreen) {
                return;
            }
            titleDragAnchorOnScreen = event.getLocationOnScreen();
            titleDragWindowLocation = getLocation();
            titleDragMoved = false;
            titleDragHorizontalRatio = getWidth() <= 0
                    ? 0.5f
                    : Math.max(0f, Math.min(1f, event.getX() / (float) getWidth()));
            titleDragVerticalOffset = Math.max(0, event.getY());
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            if (!SwingUtilities.isLeftMouseButton(event) || titleDragAnchorOnScreen == null
                    || titleDragWindowLocation == null || fullscreen) {
                return;
            }
            Point currentOnScreen = event.getLocationOnScreen();
            if ((maximised || isSnapped()) && shouldRestoreWindowForDragging(currentOnScreen)) {
                RestoreWindowForDragging(currentOnScreen);
            }
            if (maximised || isSnapped()) {
                return;
            }
            int deltaX = currentOnScreen.x - titleDragAnchorOnScreen.x;
            int deltaY = currentOnScreen.y - titleDragAnchorOnScreen.y;
            titleDragMoved = titleDragMoved || deltaX != 0 || deltaY != 0;
            setLocation(titleDragWindowLocation.x + deltaX, titleDragWindowLocation.y + deltaY);
            showSnapPreview(ResolveSnapBounds(ResolveSnapZone(currentOnScreen), currentOnScreen));
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            Point releasePoint = event.getLocationOnScreen();
            SnapZone snapZone = titleDragMoved ? ResolveSnapZone(releasePoint) : SnapZone.NONE;
            hideSnapPreview();
            if (titleDragMoved) {
                ApplySnapZone(snapZone, releasePoint);
                if (!maximised && !isSnapped()) {
                    storeRestoreBounds(getBounds());
                }
            }
            titleDragAnchorOnScreen = null;
            titleDragWindowLocation = null;
            titleDragMoved = false;
        }

        @Override
        public void mouseClicked(MouseEvent event) {
            if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event) && isResizable()) {
                ToggleMaximise();
            }
        }
    };

    /**
     * Creates a window with explicit dimensions and resize behaviour.
     *
     * @param title     window title
     * @param width     initial width in pixels
     * @param height    initial height in pixels
     * @param resizable whether the frame can be resized by the user
     */
    public DuckWindow(String title, int width, int height, boolean resizable) {
        super();
        setUndecorated(true);

        titleBarPanel = buildTitleBar();
        menuBarHost = new JPanel(new BorderLayout());
        menuBarHost.setOpaque(true);
        menuBarHost.setVisible(false);

        windowChromePanel = new JPanel();
        windowChromePanel.setLayout(new BoxLayout(windowChromePanel, BoxLayout.Y_AXIS));
        windowChromePanel.setOpaque(true);
        windowChromePanel.add(titleBarPanel);
        windowChromePanel.add(menuBarHost);

        bodyPanel = new JPanel(new BorderLayout());
        bodyPanel.setOpaque(true);

        JPanel windowSurface = new JPanel(new BorderLayout());
        windowSurface.setOpaque(true);
        windowSurface.add(windowChromePanel, BorderLayout.NORTH);
        windowSurface.add(bodyPanel, BorderLayout.CENTER);

        northResizeHandle = new ResizeHandlePanel(DIR_NORTH);
        southResizeHandle = new ResizeHandlePanel(DIR_SOUTH);
        westResizeHandle = new ResizeHandlePanel(DIR_WEST);
        eastResizeHandle = new ResizeHandlePanel(DIR_EAST);

        shellPanel = new JPanel(new BorderLayout());
        shellPanel.setOpaque(true);
        shellPanel.add(northResizeHandle, BorderLayout.NORTH);
        shellPanel.add(southResizeHandle, BorderLayout.SOUTH);
        shellPanel.add(westResizeHandle, BorderLayout.WEST);
        shellPanel.add(eastResizeHandle, BorderLayout.EAST);
        shellPanel.add(windowSurface, BorderLayout.CENTER);
        super.setContentPane(shellPanel);

        setSize(width, height);
        setResizable(resizable);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        baseTitle = title;

        titleIconLabel = new JLabel();
        titleLabel = new JLabel();
        minimiseButton = createTitleButton(WindowControl.MINIMISE);
        maximiseButton = createTitleButton(WindowControl.MAXIMISE);
        closeButton = createTitleButton(WindowControl.CLOSE);
        initialiseTitleBar();

        applyWindowChromeTheme();
        setTitle(title);
        setLocationRelativeTo(null);
        List<Image> windowIcons = AppAssets.WindowIcons();
        if (!windowIcons.isEmpty()) {
            setIconImages(windowIcons);
            titleIconLabel.setIcon(AppAssets.HeaderLogoIcon(18));
        } else {
            titleIconLabel.setVisible(false);
        }
        windowedSize = getSize();
        sizeBeforeMaximise = getSize();
        locationBeforeMaximise = getLocation();
        windowedLocation = getLocation();
        restoreBounds = getBounds();

        installTitleBarInteractions();
        addWindowStateListener(event -> {
            maximised = isFrameMaximised();
            updateWindowChromeState();
        });
        updateWindowChromeState();
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
     * @param title  window title
     * @param width  initial width in pixels
     * @param height initial height in pixels
     */
    public DuckWindow(String title, int width, int height) {
        this(title, width, height, true);
    }

    @Override
    public Container getContentPane() {
        return bodyPanel == null ? super.getContentPane() : bodyPanel;
    }

    @Override
    public void setJMenuBar(JMenuBar menuBar) {
        if (menuBarHost == null) {
            super.setJMenuBar(menuBar);
            return;
        }

        menuBarHost.removeAll();
        attachedMenuBar = menuBar;
        if (menuBar != null) {
            menuBarHost.add(menuBar, BorderLayout.CENTER);
            installWindowDragInteractions(menuBar);
        }
        updateWindowChromeState();
        menuBarHost.revalidate();
        menuBarHost.repaint();
    }

    @Override
    public JMenuBar getJMenuBar() {
        return attachedMenuBar != null ? attachedMenuBar : super.getJMenuBar();
    }

    @Override
    public void setResizable(boolean resizable) {
        super.setResizable(resizable);
        updateWindowChromeState();
    }

    @Override
    public void setTitle(String title) {
        super.setTitle(title);
        if (titleLabel != null) {
            String nextTitle = title == null ? "" : title;
            titleLabel.setText(nextTitle);
            titleLabel.setToolTipText(nextTitle);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            WindowUiSupport.applyComponentTheme(this);
            applyWindowChromeTheme();
        }
    }

    /**
     * Toggles the maximised state of the frame.
     */
    public void ToggleMaximise() {
        if (!isResizable() || fullscreen) {
            return;
        }

        if (maximised) {
            RestoreWindowedBounds();
        } else {
            MaximiseWindow(resolveWindowScreenPoint());
        }
        updateWindowChromeState();
    }

    /**
     * Toggles exclusive fullscreen mode for the frame.
     */
    public void ToggleFullScreen() {
        GraphicsDevice graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        hideSnapPreview();

        if (fullscreen) {
            graphicsDevice.setFullScreenWindow(null);
            fullscreen = false;
            windowChromePanel.setVisible(true);
            if (windowedSize != null) {
                setSize(windowedSize);
            }
            if (windowedLocation != null) {
                setLocation(windowedLocation);
            }
            if (maximisedBeforeFullscreen) {
                MaximiseWindow(resolveWindowScreenPoint());
            }
        } else {
            maximisedBeforeFullscreen = maximised;
            windowedSize = maximised && sizeBeforeMaximise != null ? sizeBeforeMaximise : getSize();
            windowedLocation = maximised && locationBeforeMaximise != null ? locationBeforeMaximise : getLocation();
            if (maximised) {
                setExtendedState(JFrame.NORMAL);
                maximised = false;
            }
            fullscreen = true;
            windowChromePanel.setVisible(false);
            graphicsDevice.setFullScreenWindow(this);
        }

        applyWindowChromeTheme();
        updateWindowChromeState();
        revalidate();
        repaint();
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

    void ApplyWindowChromeTheme() {
        applyWindowChromeTheme();
    }

    private JPanel buildTitleBar() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setPreferredSize(new Dimension(0, TITLE_BAR_HEIGHT));
        panel.setMinimumSize(new Dimension(0, TITLE_BAR_HEIGHT));
        panel.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 10));
        return panel;
    }

    private void initialiseTitleBar() {
        JPanel titleArea = new JPanel(new BorderLayout(8, 0));
        titleArea.setOpaque(false);
        titleArea.add(titleIconLabel, BorderLayout.WEST);
        titleArea.add(titleLabel, BorderLayout.CENTER);

        JPanel controlArea = new JPanel();
        controlArea.setLayout(new BoxLayout(controlArea, BoxLayout.X_AXIS));
        controlArea.setOpaque(false);
        controlArea.add(minimiseButton);
        controlArea.add(maximiseButton);
        controlArea.add(closeButton);

        titleBarPanel.add(titleArea, BorderLayout.CENTER);
        titleBarPanel.add(controlArea, BorderLayout.EAST);

        minimiseButton.addActionListener(event -> setState(JFrame.ICONIFIED));
        maximiseButton.addActionListener(event -> ToggleMaximise());
        closeButton.addActionListener(event -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
    }

    private JButton createTitleButton(WindowControl control) {
        JButton button = new WindowControlButton(control);
        button.setFocusable(false);
        button.setAlignmentY(CENTER_ALIGNMENT);
        button.setMaximumSize(TITLE_BUTTON_SIZE);
        button.setMinimumSize(TITLE_BUTTON_SIZE);
        button.setPreferredSize(TITLE_BUTTON_SIZE);
        return button;
    }

    private void styleTitleButton(JButton button) {
        WindowUiSupport.styleSecondaryButton(button, Styling.accentColour, Styling.surfaceBorderColour);
        button.setFont(Styling.menuFont.deriveFont(java.awt.Font.BOLD, 11.5f));
        button.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        button.setMaximumSize(TITLE_BUTTON_SIZE);
        button.setMinimumSize(TITLE_BUTTON_SIZE);
        button.setPreferredSize(TITLE_BUTTON_SIZE);
    }

    private void installTitleBarInteractions() {
        installWindowDragInteractions(titleBarPanel);
        installWindowDragInteractions(titleLabel);
        installWindowDragInteractions(titleIconLabel);
        installWindowDragInteractions(menuBarHost);
    }

    private void applyWindowChromeTheme() {
        shellPanel.setBackground(Styling.appBackgroundColour);
        windowChromePanel.setBackground(Styling.surfaceColour);
        menuBarHost.setBackground(Styling.surfaceColour);
        titleBarPanel.setOpaque(true);
        titleBarPanel.setBackground(Styling.surfaceColour);
        titleBarPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Styling.surfaceBorderColour),
                BorderFactory.createEmptyBorder(7, 12, 7, 10)));

        bodyPanel.setBackground(Styling.appBackgroundColour);
        titleLabel.setForeground(Styling.accentColour);
        titleLabel.setFont(Styling.menuFont.deriveFont(java.awt.Font.BOLD, 12.5f));
        titleIconLabel.setOpaque(false);

        styleTitleButton(minimiseButton);
        styleTitleButton(maximiseButton);
        styleTitleButton(closeButton);

        if (attachedMenuBar != null) {
            attachedMenuBar.setOpaque(true);
            attachedMenuBar.setBackground(Styling.surfaceColour);
            attachedMenuBar.setForeground(Styling.mutedTextColour);
            WindowUiSupport.applyComponentTheme(attachedMenuBar);
        }

        JRootPane rootPane = getRootPane();
        if (rootPane != null) {
            rootPane.setOpaque(true);
            rootPane.setBackground(Styling.appBackgroundColour);
            rootPane.setBorder(fullscreen
                    ? BorderFactory.createEmptyBorder()
                    : WindowUiSupport.createLineBorder(Styling.surfaceBorderColour));
            if (rootPane.getLayeredPane() != null) {
                rootPane.getLayeredPane().setOpaque(true);
                rootPane.getLayeredPane().setBackground(Styling.appBackgroundColour);
            }
        }

        updateWindowChromeState();
    }

    private void updateWindowChromeState() {
        if (maximiseButton == null) {
            return;
        }

        boolean canMaximise = isResizable() && !fullscreen;
        maximiseButton.setVisible(canMaximise);
        maximiseButton.setEnabled(canMaximise);
        maximiseButton.setToolTipText(maximised ? "Restore" : "Maximise");
        if (maximiseButton instanceof WindowControlButton maximiseControlButton) {
            maximiseControlButton.setRestoreGlyph(maximised);
        }
        minimiseButton.setVisible(!fullscreen);
        closeButton.setVisible(!fullscreen);
        menuBarHost.setVisible(!fullscreen && attachedMenuBar != null);
        windowChromePanel.setVisible(!fullscreen);
        updateResizeHandleState();
    }

    private void updateResizeHandleState() {
        boolean allowResize = canResizeWindow();
        northResizeHandle.setPreferredSize(new Dimension(0, allowResize ? RESIZE_MARGIN : 0));
        southResizeHandle.setPreferredSize(new Dimension(0, allowResize ? RESIZE_MARGIN : 0));
        westResizeHandle.setPreferredSize(new Dimension(allowResize ? RESIZE_MARGIN : 0, 0));
        eastResizeHandle.setPreferredSize(new Dimension(allowResize ? RESIZE_MARGIN : 0, 0));
        northResizeHandle.setCursor(Cursor.getDefaultCursor());
        southResizeHandle.setCursor(Cursor.getDefaultCursor());
        westResizeHandle.setCursor(Cursor.getDefaultCursor());
        eastResizeHandle.setCursor(Cursor.getDefaultCursor());
        northResizeHandle.revalidate();
        southResizeHandle.revalidate();
        westResizeHandle.revalidate();
        eastResizeHandle.revalidate();
    }

    private boolean canResizeWindow() {
        return isResizable() && !fullscreen && !maximised && !isSnapped();
    }

    private boolean isFrameMaximised() {
        return (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
    }

    private void installWindowDragInteractions(java.awt.Component component) {
        if (component == null || component instanceof JButton) {
            return;
        }
        if (component instanceof javax.swing.JComponent themedComponent
                && Boolean.TRUE.equals(themedComponent.getClientProperty("duckwindow.dragInstalled"))) {
            return;
        }
        component.addMouseListener(windowDragAdapter);
        component.addMouseMotionListener(windowDragAdapter);
        if (component instanceof javax.swing.JComponent themedComponent) {
            themedComponent.putClientProperty("duckwindow.dragInstalled", Boolean.TRUE);
        }
    }

    private void MaximiseWindow(Point screenPoint) {
        if (!isResizable() || fullscreen || maximised) {
            return;
        }
        if (!isSnapped() || restoreBounds == null) {
            storeRestoreBounds(getBounds());
        }
        snappedZone = SnapZone.NONE;
        setMaximizedBounds(resolveWorkAreaBounds(screenPoint));
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        maximised = true;
    }

    private void RestoreWindowedBounds() {
        setExtendedState(JFrame.NORMAL);
        setMaximizedBounds(null);
        Rectangle bounds = restoreBounds;
        if (bounds != null && !bounds.isEmpty()) {
            setBounds(bounds);
        } else {
            if (sizeBeforeMaximise != null) {
                setSize(sizeBeforeMaximise);
            }
            if (locationBeforeMaximise != null) {
                setLocation(locationBeforeMaximise);
            }
        }
        maximised = false;
        snappedZone = SnapZone.NONE;
        hideSnapPreview();
    }

    private Point resolveWindowScreenPoint() {
        Rectangle bounds = getBounds();
        if (!bounds.isEmpty()) {
            return new Point(bounds.x + Math.max(0, bounds.width / 2),
                    bounds.y + Math.max(0, bounds.height / 2));
        }
        return getLocation();
    }

    private Rectangle resolveWorkAreaBounds(Point screenPoint) {
        GraphicsConfiguration graphicsConfiguration = resolveGraphicsConfiguration(screenPoint);
        Rectangle bounds = new Rectangle(graphicsConfiguration.getBounds());
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);
        bounds.x += screenInsets.left;
        bounds.y += screenInsets.top;
        bounds.width -= screenInsets.left + screenInsets.right;
        bounds.height -= screenInsets.top + screenInsets.bottom;
        return bounds;
    }

    private GraphicsConfiguration resolveGraphicsConfiguration(Point screenPoint) {
        if (screenPoint != null) {
            for (GraphicsDevice graphicsDevice : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                GraphicsConfiguration graphicsConfiguration = graphicsDevice.getDefaultConfiguration();
                if (graphicsConfiguration.getBounds().contains(screenPoint)) {
                    return graphicsConfiguration;
                }
            }
        }

        GraphicsConfiguration graphicsConfiguration = getGraphicsConfiguration();
        if (graphicsConfiguration != null) {
            return graphicsConfiguration;
        }

        return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
    }

    private boolean isSnapped() {
        return snappedZone != SnapZone.NONE;
    }

    private boolean shouldRestoreWindowForDragging(Point currentOnScreen) {
        if (currentOnScreen == null || titleDragAnchorOnScreen == null || (!maximised && !isSnapped())) {
            return false;
        }
        return Math.abs(currentOnScreen.x - titleDragAnchorOnScreen.x) >= DRAG_RESTORE_THRESHOLD
                || Math.abs(currentOnScreen.y - titleDragAnchorOnScreen.y) >= DRAG_RESTORE_THRESHOLD;
    }

    private void RestoreWindowForDragging(Point currentOnScreen) {
        Rectangle bounds = restoreBounds;
        if (bounds == null || bounds.isEmpty()) {
            bounds = new Rectangle(getX(), getY(), Math.max(640, getWidth()), Math.max(400, getHeight()));
        }

        int anchorX = Math.round(bounds.width * titleDragHorizontalRatio);
        int anchorY = Math.max(0, Math.min(titleDragVerticalOffset, Math.max(0, bounds.height - 1)));
        int nextX = currentOnScreen.x - anchorX;
        int nextY = currentOnScreen.y - anchorY;

        if (maximised) {
            setExtendedState(JFrame.NORMAL);
            setMaximizedBounds(null);
            maximised = false;
        }
        snappedZone = SnapZone.NONE;
        setBounds(nextX, nextY, bounds.width, bounds.height);
        titleDragAnchorOnScreen = currentOnScreen;
        titleDragWindowLocation = getLocation();
        titleDragMoved = true;
        updateWindowChromeState();
    }

    private SnapZone ResolveSnapZone(Point screenPoint) {
        if (screenPoint == null || !isResizable() || fullscreen) {
            return SnapZone.NONE;
        }

        Rectangle bounds = resolveWorkAreaBounds(screenPoint);
        if (screenPoint.y <= bounds.y + Math.max(SNAP_MAXIMISE_THRESHOLD, SNAP_EDGE_THRESHOLD)) {
            return SnapZone.MAXIMISE;
        }
        if (screenPoint.x <= bounds.x + SNAP_EDGE_THRESHOLD) {
            return SnapZone.LEFT_HALF;
        }
        if (screenPoint.x >= bounds.x + bounds.width - SNAP_EDGE_THRESHOLD) {
            return SnapZone.RIGHT_HALF;
        }
        return SnapZone.NONE;
    }

    private Rectangle ResolveSnapBounds(SnapZone snapZone, Point screenPoint) {
        if (snapZone == null || snapZone == SnapZone.NONE) {
            return null;
        }

        Rectangle workArea = resolveWorkAreaBounds(screenPoint);
        return switch (snapZone) {
            case LEFT_HALF -> new Rectangle(workArea.x, workArea.y, workArea.width / 2, workArea.height);
            case RIGHT_HALF -> new Rectangle(workArea.x + (workArea.width / 2), workArea.y,
                    workArea.width - (workArea.width / 2), workArea.height);
            case MAXIMISE -> workArea;
            case NONE -> null;
        };
    }

    private void ApplySnapZone(SnapZone snapZone, Point screenPoint) {
        if (snapZone == null || snapZone == SnapZone.NONE) {
            return;
        }
        if (snapZone == SnapZone.MAXIMISE) {
            MaximiseWindow(screenPoint);
            return;
        }

        if (!isSnapped() && !maximised) {
            storeRestoreBounds(getBounds());
        }

        Rectangle snapBounds = ResolveSnapBounds(snapZone, screenPoint);
        if (snapBounds == null || snapBounds.isEmpty()) {
            return;
        }

        setExtendedState(JFrame.NORMAL);
        setMaximizedBounds(null);
        maximised = false;
        snappedZone = snapZone;
        setBounds(snapBounds);
        updateWindowChromeState();
    }

    private void storeRestoreBounds(Rectangle bounds) {
        if (bounds == null || bounds.isEmpty()) {
            return;
        }
        restoreBounds = new Rectangle(bounds);
        sizeBeforeMaximise = restoreBounds.getSize();
        locationBeforeMaximise = restoreBounds.getLocation();
    }

    private void showSnapPreview(Rectangle bounds) {
        if (bounds == null || bounds.isEmpty()) {
            hideSnapPreview();
            return;
        }

        Rectangle previewBounds = new Rectangle(bounds);
        previewBounds.grow(-SNAP_PREVIEW_INSET, -SNAP_PREVIEW_INSET);
        if (previewBounds.isEmpty()) {
            previewBounds = bounds;
        }

        if (snapPreviewWindow == null) {
            snapPreviewWindow = createSnapPreviewWindow();
        }
        snapPreviewWindow.setBounds(previewBounds);
        if (!snapPreviewWindow.isVisible()) {
            snapPreviewWindow.setVisible(true);
        }
    }

    private void hideSnapPreview() {
        if (snapPreviewWindow != null) {
            snapPreviewWindow.setVisible(false);
        }
    }

    private JWindow createSnapPreviewWindow() {
        JWindow previewWindow = new JWindow(this);
        previewWindow.setFocusableWindowState(false);
        previewWindow.setBackground(new Color(0, 0, 0, 0));
        previewWindow.setContentPane(new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D graphics2d = (Graphics2D) graphics.create();
                graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2d.setColor(new Color(Styling.accentColour.getRed(),
                        Styling.accentColour.getGreen(),
                        Styling.accentColour.getBlue(),
                        54));
                graphics2d.fillRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), 18, 18);
                graphics2d.setColor(new Color(Styling.accentColour.getRed(),
                        Styling.accentColour.getGreen(),
                        Styling.accentColour.getBlue(),
                        170));
                graphics2d.drawRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), 18, 18);
                graphics2d.dispose();
            }
        });
        previewWindow.getContentPane().setBackground(new Color(0, 0, 0, 0));
        return previewWindow;
    }

    private int cursorForDirection(int directionMask) {
        return switch (directionMask) {
            case DIR_NORTH -> Cursor.N_RESIZE_CURSOR;
            case DIR_SOUTH -> Cursor.S_RESIZE_CURSOR;
            case DIR_WEST -> Cursor.W_RESIZE_CURSOR;
            case DIR_EAST -> Cursor.E_RESIZE_CURSOR;
            case DIR_NORTH | DIR_WEST -> Cursor.NW_RESIZE_CURSOR;
            case DIR_NORTH | DIR_EAST -> Cursor.NE_RESIZE_CURSOR;
            case DIR_SOUTH | DIR_WEST -> Cursor.SW_RESIZE_CURSOR;
            case DIR_SOUTH | DIR_EAST -> Cursor.SE_RESIZE_CURSOR;
            default -> Cursor.DEFAULT_CURSOR;
        };
    }

    private void startResize(int directionMask, MouseEvent event) {
        if (!canResizeWindow()) {
            return;
        }
        resizeStartBounds = getBounds();
        resizeStartOnScreen = event.getLocationOnScreen();
    }

    private void performResize(int directionMask, MouseEvent event) {
        if (!canResizeWindow() || resizeStartBounds == null || resizeStartOnScreen == null) {
            return;
        }

        Point currentOnScreen = event.getLocationOnScreen();
        int deltaX = currentOnScreen.x - resizeStartOnScreen.x;
        int deltaY = currentOnScreen.y - resizeStartOnScreen.y;
        Rectangle nextBounds = new Rectangle(resizeStartBounds);

        if ((directionMask & DIR_WEST) != 0) {
            nextBounds.x += deltaX;
            nextBounds.width -= deltaX;
        }
        if ((directionMask & DIR_EAST) != 0) {
            nextBounds.width += deltaX;
        }
        if ((directionMask & DIR_NORTH) != 0) {
            nextBounds.y += deltaY;
            nextBounds.height -= deltaY;
        }
        if ((directionMask & DIR_SOUTH) != 0) {
            nextBounds.height += deltaY;
        }

        Dimension minimumSize = getMinimumSize();
        if (minimumSize != null) {
            if (nextBounds.width < minimumSize.width) {
                if ((directionMask & DIR_WEST) != 0) {
                    nextBounds.x = resizeStartBounds.x + resizeStartBounds.width - minimumSize.width;
                }
                nextBounds.width = minimumSize.width;
            }
            if (nextBounds.height < minimumSize.height) {
                if ((directionMask & DIR_NORTH) != 0) {
                    nextBounds.y = resizeStartBounds.y + resizeStartBounds.height - minimumSize.height;
                }
                nextBounds.height = minimumSize.height;
            }
        }

        setBounds(nextBounds);
    }

    private static final class WindowControlButton extends JButton {
        private final WindowControl control;
        private boolean restoreGlyph;

        private WindowControlButton(WindowControl control) {
            this.control = control;
            setText("");
            setOpaque(true);
        }

        private void setRestoreGlyph(boolean restoreGlyph) {
            if (this.restoreGlyph != restoreGlyph) {
                this.restoreGlyph = restoreGlyph;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D graphics2d = (Graphics2D) graphics.create();
            graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2d.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics2d.setColor(getForeground());

            int centreX = getWidth() / 2;
            int centreY = getHeight() / 2;
            switch (control) {
                case MINIMISE -> graphics2d.drawLine(centreX - 5, centreY + 4, centreX + 5, centreY + 4);
                case MAXIMISE -> {
                    if (restoreGlyph) {
                        graphics2d.drawRect(centreX - 4, centreY - 3, 8, 7);
                        graphics2d.drawRect(centreX - 2, centreY - 5, 8, 7);
                    } else {
                        graphics2d.drawRect(centreX - 5, centreY - 4, 10, 8);
                    }
                }
                case CLOSE -> {
                    graphics2d.drawLine(centreX - 4, centreY - 4, centreX + 4, centreY + 4);
                    graphics2d.drawLine(centreX + 4, centreY - 4, centreX - 4, centreY + 4);
                }
            }
            graphics2d.dispose();
        }
    }

    private final class ResizeHandlePanel extends JPanel {

        private final int baseDirection;
        private int activeDirection;

        private ResizeHandlePanel(int baseDirection) {
            this.baseDirection = baseDirection;
            setOpaque(false);
            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent event) {
                    setCursor(Cursor.getPredefinedCursor(cursorForDirection(resolveDirection(event))));
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event)) {
                        return;
                    }
                    activeDirection = resolveDirection(event);
                    startResize(activeDirection, event);
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    performResize(activeDirection, event);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    activeDirection = 0;
                    resizeStartBounds = null;
                    resizeStartOnScreen = null;
                    setCursor(Cursor.getDefaultCursor());
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    if (activeDirection == 0) {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            };
            addMouseListener(adapter);
            addMouseMotionListener(adapter);
        }

        private int resolveDirection(MouseEvent event) {
            int direction = baseDirection;
            if (baseDirection == DIR_NORTH || baseDirection == DIR_SOUTH) {
                if (event.getX() <= RESIZE_MARGIN) {
                    direction |= DIR_WEST;
                } else if (event.getX() >= getWidth() - RESIZE_MARGIN) {
                    direction |= DIR_EAST;
                }
            }
            return direction;
        }
    }
}
