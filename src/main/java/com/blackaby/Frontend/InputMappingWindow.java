package com.blackaby.Frontend;

import com.blackaby.Backend.GB.GBBackends;
import com.blackaby.Backend.Platform.EmulatorButton;
import com.blackaby.Backend.Platform.EmulatorProfile;
import com.blackaby.Misc.Config;
import com.blackaby.Misc.ControllerBinding;
import com.blackaby.Misc.Settings;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Dedicated visual rebinding window for keyboard and controller mappings.
 */
public final class InputMappingWindow extends JDialog {

    private static final Dimension minimumWindowSize = new Dimension(900, 700);
    private static final Dimension canvasSize = new Dimension(820, 500);
    private static final Dimension calloutSize = new Dimension(136, 72);

    private enum InputMode {
        KEYBOARD,
        CONTROLLER
    }

    private final MainWindow mainWindow;
    private final ControllerInputService controllerInputService = ControllerInputService.Shared();
    private final Color panelBackground = Styling.appBackgroundColour;
    private final Color cardBackground = Styling.surfaceColour;
    private final Color cardBorder = Styling.surfaceBorderColour;
    private final Color accentColour = Styling.accentColour;
    private final Color mutedText = Styling.mutedTextColour;
    private final Map<EmulatorButton, JButton> keyboardBindingButtons = new HashMap<>();
    private final Map<EmulatorButton, JButton> controllerBindingButtons = new HashMap<>();
    private final List<JComponent> controllerCaptureComponents = new ArrayList<>();

    private JLabel controllerStatusBadgeLabel;
    private JLabel controllerStatusHelperLabel;
    private JButton controllerRebindAllButton;
    private Timer controllerRefreshTimer;

    public InputMappingWindow(Window owner, MainWindow mainWindow) {
        super(owner, UiText.OptionsWindow.INPUT_MAPPER_WINDOW_TITLE, ModalityType.MODELESS);
        this.mainWindow = mainWindow;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        getContentPane().setBackground(panelBackground);
        setLayout(new BorderLayout(0, 0));
        List<java.awt.Image> windowIcons = AppAssets.WindowIcons();
        if (!windowIcons.isEmpty()) {
            setIconImages(windowIcons);
        }

        add(buildHeader(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);
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

        pack();
        setSize(Math.max(getWidth(), minimumWindowSize.width), Math.max(getHeight(), minimumWindowSize.height));
        setLocationRelativeTo(owner);
        refreshControllerStatus();
        setVisible(true);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setBackground(panelBackground);
        header.setBorder(BorderFactory.createEmptyBorder(22, 24, 10, 24));

        JLabel titleLabel = new JLabel(UiText.OptionsWindow.INPUT_MAPPER_WINDOW_HEADER);
        titleLabel.setFont(Styling.titleFont.deriveFont(30f));
        titleLabel.setForeground(accentColour);

        JLabel subtitleLabel = new JLabel(UiText.OptionsWindow.INPUT_MAPPER_WINDOW_SUBTITLE);
        subtitleLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 14f));
        subtitleLabel.setForeground(mutedText);

        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subtitleLabel, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildContent() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        tabs.setBackground(panelBackground);
        tabs.setForeground(accentColour);
        tabs.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        tabs.addTab(UiText.OptionsWindow.INPUT_MAPPER_KEYBOARD_TAB, createModePanel(InputMode.KEYBOARD));
        tabs.addTab(UiText.OptionsWindow.INPUT_MAPPER_CONTROLLER_TAB, createModePanel(InputMode.CONTROLLER));
        return tabs;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setBackground(panelBackground);
        footer.setBorder(BorderFactory.createEmptyBorder(10, 20, 16, 20));

        JButton closeButton = createPrimaryButton(UiText.OptionsWindow.CLOSE_BUTTON);
        closeButton.addActionListener(event -> dispose());
        footer.add(closeButton);
        return footer;
    }

    private JComponent createModePanel(InputMode mode) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(panelBackground);
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        content.add(createModeHeroCard(mode));
        content.add(Box.createVerticalStrut(14));
        content.add(createCanvasCard(mode));
        return content;
    }

    private JComponent createModeHeroCard(InputMode mode) {
        JPanel card = new JPanel(new BorderLayout(16, 0));
        card.setBackground(Styling.sectionHighlightColour);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textBlock = new JPanel();
        textBlock.setOpaque(false);
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(mode == InputMode.KEYBOARD
                ? UiText.OptionsWindow.KEYBOARD_MAPPER_TITLE
                : UiText.OptionsWindow.CONTROLLER_MAPPER_TITLE);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 17f));
        titleLabel.setForeground(accentColour);

        JLabel descriptionLabel = new JLabel(wrapHtml(
                mode == InputMode.KEYBOARD
                        ? UiText.OptionsWindow.KEYBOARD_MAPPER_DESCRIPTION
                        : UiText.OptionsWindow.CONTROLLER_MAPPER_DESCRIPTION,
                420));
        descriptionLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        descriptionLabel.setForeground(mutedText);

        textBlock.add(titleLabel);
        textBlock.add(Box.createVerticalStrut(6));
        textBlock.add(descriptionLabel);

        if (mode == InputMode.CONTROLLER) {
            textBlock.add(Box.createVerticalStrut(10));
            textBlock.add(createControllerStatusBanner());
        }

        JPanel actionColumn = new JPanel();
        actionColumn.setOpaque(false);
        actionColumn.setLayout(new BoxLayout(actionColumn, BoxLayout.Y_AXIS));

        if (mode == InputMode.CONTROLLER) {
            JButton refreshButton = createSecondaryButton(UiText.OptionsWindow.CONTROLLER_REFRESH_BUTTON);
            refreshButton.addActionListener(event -> {
                controllerInputService.RefreshControllers();
                refreshControllerStatus();
            });
            refreshButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
            actionColumn.add(refreshButton);
            actionColumn.add(Box.createVerticalStrut(8));
        }

        JButton rebindAllButton = createPrimaryButton(mode == InputMode.KEYBOARD
                ? UiText.OptionsWindow.REBIND_ALL_CONTROLS_BUTTON
                : UiText.OptionsWindow.CONTROLLER_REBIND_ALL_BUTTON);
        rebindAllButton.addActionListener(event -> {
            if (mode == InputMode.KEYBOARD) {
                captureAllKeyboardBindings();
            } else {
                captureAllControllerBindings();
            }
        });
        rebindAllButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
        actionColumn.add(rebindAllButton);

        if (mode == InputMode.CONTROLLER) {
            controllerRebindAllButton = rebindAllButton;
            controllerCaptureComponents.add(rebindAllButton);
        }

        actionColumn.add(Box.createVerticalStrut(8));

        JButton resetButton = createSecondaryButton(mode == InputMode.KEYBOARD
                ? UiText.OptionsWindow.RESET_CONTROLS_BUTTON
                : UiText.OptionsWindow.CONTROLLER_RESET_BUTTON);
        resetButton.addActionListener(event -> {
            if (mode == InputMode.KEYBOARD) {
                Settings.ResetControls();
                refreshKeyboardBindingButtons();
            } else {
                Settings.ResetControllerControls();
                refreshControllerBindingButtons();
            }
            Config.Save();
        });
        resetButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
        actionColumn.add(resetButton);

        card.add(textBlock, BorderLayout.CENTER);
        card.add(actionColumn, BorderLayout.EAST);
        return card;
    }

    private JComponent createControllerStatusBanner() {
        JPanel banner = new JPanel(new BorderLayout(10, 0));
        banner.setOpaque(false);

        JPanel textStack = new JPanel();
        textStack.setOpaque(false);
        textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));

        controllerStatusBadgeLabel = createBadgeLabel(UiText.OptionsWindow.CONTROLLER_STATUS_DISCONNECTED);
        controllerStatusBadgeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        controllerStatusHelperLabel = new JLabel();
        controllerStatusHelperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        controllerStatusHelperLabel.setForeground(mutedText);
        controllerStatusHelperLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textStack.add(controllerStatusBadgeLabel);
        textStack.add(Box.createVerticalStrut(6));
        textStack.add(controllerStatusHelperLabel);

        banner.add(textStack, BorderLayout.CENTER);
        return banner;
    }

    private JComponent createCanvasCard(InputMode mode) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(cardBackground);
        card.setBorder(createCardBorder());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        MapperCanvas canvas = new MapperCanvas(mode);
        card.add(canvas, BorderLayout.CENTER);

        JLabel helperLabel = new JLabel(UiText.OptionsWindow.INPUT_MAPPER_CANVAS_HINT);
        helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helperLabel.setForeground(mutedText);
        helperLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        card.add(helperLabel, BorderLayout.SOUTH);
        return card;
    }

    private Border createCardBorder() {
        return WindowUiSupport.createCardBorder(cardBorder, true, 18);
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

    private void captureAllKeyboardBindings() {
        for (EmulatorButton button : controlButtons()) {
            if (!captureKeyboardBinding(button)) {
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

    private boolean captureKeyboardBinding(EmulatorButton button) {
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

        java.awt.KeyEventDispatcher dispatcher = event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED) {
                return false;
            }

            if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                dialog.dispose();
                return true;
            }

            Settings.inputBindings.SetKeyCode(button, event.getKeyCode());
            captured[0] = true;
            refreshKeyboardBindingButtons();
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
            public void windowClosed(WindowEvent event) {
                removeDispatcher.run();
            }

            @Override
            public void windowClosing(WindowEvent event) {
                removeDispatcher.run();
            }
        });

        focusManager.addKeyEventDispatcher(dispatcher);
        SwingUtilities.invokeLater(dialog::requestFocusInWindow);
        dialog.setVisible(true);
        removeDispatcher.run();
        return captured[0];
    }

    private boolean captureControllerBinding(EmulatorButton button) {
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

        JDialog dialog = new JDialog(this,
                UiText.OptionsWindow.ControllerRebindDialogTitle(formatControlButtonName(button)),
                true);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(panelBackground);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBackground(cardBackground);
        content.setBorder(createCardBorder());

        JLabel title = new JLabel(UiText.OptionsWindow.ControllerRebindDialogPrompt(formatControlButtonName(button)),
                SwingConstants.CENTER);
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

            Settings.controllerBindings.SetBinding(button, candidate);
            captured[0] = true;
            refreshControllerBindingButtons();
            refreshControllerStatus();
            Config.Save();
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

    private void refreshKeyboardBindingButtons() {
        for (EmulatorButton button : controlButtons()) {
            JButton bindingButton = keyboardBindingButtons.get(button);
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

    private void refreshControllerStatus() {
        if (controllerStatusBadgeLabel == null || controllerStatusHelperLabel == null) {
            return;
        }

        String error = controllerInputService.GetInitialisationError();
        Optional<ControllerInputService.ControllerDevice> activeController = controllerInputService.GetActiveController();
        boolean runtimeAvailable = error == null || error.isBlank();

        if (!runtimeAvailable) {
            controllerStatusBadgeLabel.setText(UiText.OptionsWindow.CONTROLLER_STATUS_UNAVAILABLE);
            controllerStatusHelperLabel.setText(wrapHtml(error, 420));
            setControllerCaptureEnabled(false);
            return;
        }

        if (activeController.isPresent()) {
            controllerStatusBadgeLabel.setText(Settings.controllerInputEnabled
                    ? UiText.OptionsWindow.CONTROLLER_STATUS_CONNECTED
                    : UiText.OptionsWindow.CONTROLLER_STATUS_DISABLED);
            controllerStatusHelperLabel.setText(wrapHtml(activeController.get().displayName(), 420));
            setControllerCaptureEnabled(true);
            return;
        }

        controllerStatusBadgeLabel.setText(UiText.OptionsWindow.CONTROLLER_STATUS_DISCONNECTED);
        controllerStatusHelperLabel.setText(wrapHtml(UiText.OptionsWindow.CONTROLLER_NO_ACTIVE_DEVICE_MESSAGE, 420));
        setControllerCaptureEnabled(false);
    }

    private void setControllerCaptureEnabled(boolean enabled) {
        for (JComponent component : controllerCaptureComponents) {
            component.setEnabled(enabled);
        }
        if (controllerRebindAllButton != null) {
            controllerRebindAllButton.setEnabled(enabled);
        }
    }

    private List<? extends EmulatorButton> controlButtons() {
        return backendProfile().controlButtons();
    }

    private String formatControlButtonName(EmulatorButton button) {
        return backendProfile().controlButtonLabel(button);
    }

    private EmulatorProfile backendProfile() {
        return mainWindow == null ? GBBackends.Current().Profile() : mainWindow.GetBackendProfile();
    }

    private String wrapHtml(String text, int width) {
        return "<html><body style='width: " + width + "px'>" + WindowUiSupport.escapeHtml(text) + "</body></html>";
    }

    private final class MapperCanvas extends JPanel {

        private final InputMode mode;
        private final Map<EmulatorButton, BindingCallout> callouts = new HashMap<>();
        private EmulatorButton highlightedButton;

        private MapperCanvas(InputMode mode) {
            this.mode = mode;
            setOpaque(false);
            setLayout(null);
            setPreferredSize(canvasSize);

            for (EmulatorButton button : controlButtons()) {
                BindingCallout callout = new BindingCallout(button, mode);
                callouts.put(button, callout);
                add(callout);
            }
        }

        @Override
        public void doLayout() {
            super.doLayout();
            int width = getWidth();
            int height = getHeight();
            for (Map.Entry<EmulatorButton, BindingCallout> entry : callouts.entrySet()) {
                entry.getValue().setBounds(calloutBounds(entry.getKey(), width, height));
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            paintBackdrop(g2);
            Rectangle body = bodyBounds();
            paintGameBoy(g2, body);
            paintConnectionLines(g2, body);

            g2.dispose();
        }

        private void paintBackdrop(Graphics2D g2) {
            int width = getWidth();
            int height = getHeight();
            g2.setPaint(new GradientPaint(0, 0,
                    new Color(255, 255, 255, 14),
                    width, height,
                    new Color(accentColour.getRed(), accentColour.getGreen(), accentColour.getBlue(), 20)));
            g2.fillRect(0, 0, width, height);
        }

        private void paintConnectionLines(Graphics2D g2, Rectangle body) {
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (Map.Entry<EmulatorButton, BindingCallout> entry : callouts.entrySet()) {
                EmulatorButton button = entry.getKey();
                Point anchorPoint = buttonAnchor(button, body);
                Rectangle calloutBounds = entry.getValue().getBounds();
                Point endpoint = nearestCalloutEdge(calloutBounds, anchorPoint);

                Color lineColour = isHighlighted(button)
                        ? accentColour
                        : new Color(accentColour.getRed(), accentColour.getGreen(), accentColour.getBlue(), 138);
                g2.setColor(lineColour);
                g2.drawLine(anchorPoint.x, anchorPoint.y, endpoint.x, endpoint.y);
                paintArrowHead(g2, anchorPoint, endpoint);
            }
        }

        private void paintArrowHead(Graphics2D g2, Point start, Point end) {
            double angle = Math.atan2(end.y - start.y, end.x - start.x);
            int arrowLength = 11;
            int arrowWidth = 5;

            Path2D.Double arrow = new Path2D.Double();
            arrow.moveTo(end.x, end.y);
            arrow.lineTo(
                    end.x - arrowLength * Math.cos(angle - Math.atan2(arrowWidth, arrowLength)),
                    end.y - arrowLength * Math.sin(angle - Math.atan2(arrowWidth, arrowLength)));
            arrow.moveTo(end.x, end.y);
            arrow.lineTo(
                    end.x - arrowLength * Math.cos(angle + Math.atan2(arrowWidth, arrowLength)),
                    end.y - arrowLength * Math.sin(angle + Math.atan2(arrowWidth, arrowLength)));
            g2.draw(arrow);
        }

        private void paintGameBoy(Graphics2D g2, Rectangle body) {
            RoundRectangle2D.Float shadow = new RoundRectangle2D.Float(
                    body.x + 10f, body.y + 12f, body.width, body.height, 38f, 38f);
            g2.setColor(new Color(0, 0, 0, 34));
            g2.fill(shadow);

            RoundRectangle2D.Float shell = new RoundRectangle2D.Float(
                    body.x, body.y, body.width, body.height, 38f, 38f);
            g2.setPaint(new GradientPaint(body.x, body.y,
                    new Color(241, 237, 225),
                    body.x + body.width, body.y + body.height,
                    new Color(223, 220, 209)));
            g2.fill(shell);
            g2.setColor(new Color(176, 173, 161));
            g2.setStroke(new BasicStroke(2f));
            g2.draw(shell);

            Rectangle screenBezel = new Rectangle(
                    body.x + (int) (body.width * 0.12),
                    body.y + (int) (body.height * 0.1),
                    (int) (body.width * 0.76),
                    (int) (body.height * 0.27));
            RoundRectangle2D.Float bezel = new RoundRectangle2D.Float(
                    screenBezel.x, screenBezel.y, screenBezel.width, screenBezel.height, 20f, 20f);
            g2.setColor(new Color(74, 80, 88));
            g2.fill(bezel);

            Rectangle screen = new Rectangle(
                    screenBezel.x + 26,
                    screenBezel.y + 22,
                    screenBezel.width - 52,
                    screenBezel.height - 44);
            g2.setPaint(new GradientPaint(screen.x, screen.y,
                    new Color(145, 176, 118),
                    screen.x + screen.width, screen.y + screen.height,
                    new Color(91, 127, 72)));
            g2.fillRoundRect(screen.x, screen.y, screen.width, screen.height, 12, 12);
            g2.setColor(new Color(62, 82, 52));
            g2.drawRoundRect(screen.x, screen.y, screen.width, screen.height, 12, 12);

            paintDpad(g2, body);
            paintActionButtons(g2, body);
            paintStartSelect(g2, body);
        }

        private void paintDpad(Graphics2D g2, Rectangle body) {
            int centerX = body.x + (int) (body.width * 0.29);
            int centerY = body.y + (int) (body.height * 0.64);
            int armLength = 34;
            int thickness = 24;
            Color dark = new Color(76, 78, 83);
            Color highlight = accentColour.darker();

            g2.setColor(isHighlighted("UP") ? highlight : dark);
            g2.fillRoundRect(centerX - (thickness / 2), centerY - armLength - (thickness / 2),
                    thickness, armLength + thickness, 10, 10);

            g2.setColor(isHighlighted("DOWN") ? highlight : dark);
            g2.fillRoundRect(centerX - (thickness / 2), centerY - (thickness / 2),
                    thickness, armLength + thickness, 10, 10);

            g2.setColor(isHighlighted("LEFT") ? highlight : dark);
            g2.fillRoundRect(centerX - armLength - (thickness / 2), centerY - (thickness / 2),
                    armLength + thickness, thickness, 10, 10);

            g2.setColor(isHighlighted("RIGHT") ? highlight : dark);
            g2.fillRoundRect(centerX - (thickness / 2), centerY - (thickness / 2),
                    armLength + thickness, thickness, 10, 10);
        }

        private void paintActionButtons(Graphics2D g2, Rectangle body) {
            int aX = body.x + (int) (body.width * 0.68);
            int aY = body.y + (int) (body.height * 0.52);
            int bX = body.x + (int) (body.width * 0.55);
            int bY = body.y + (int) (body.height * 0.61);
            int diameter = 44;
            Color base = new Color(Math.min(255, accentColour.getRed() + 18),
                    Math.max(80, accentColour.getGreen() - 8),
                    Math.max(80, accentColour.getBlue() - 4));
            Color highlight = new Color(Math.min(255, base.getRed() + 18),
                    Math.min(255, base.getGreen() + 18),
                    Math.min(255, base.getBlue() + 18));

            g2.setColor(isHighlighted("A") ? highlight : base);
            g2.fill(new Ellipse2D.Float(aX, aY, diameter, diameter));
            g2.setColor(new Color(110, 49, 62));
            g2.draw(new Ellipse2D.Float(aX, aY, diameter, diameter));

            g2.setColor(isHighlighted("B") ? highlight : base.darker());
            g2.fill(new Ellipse2D.Float(bX, bY, diameter, diameter));
            g2.setColor(new Color(110, 49, 62));
            g2.draw(new Ellipse2D.Float(bX, bY, diameter, diameter));
        }

        private void paintStartSelect(Graphics2D g2, Rectangle body) {
            int selectX = body.x + (int) (body.width * 0.37);
            int startX = body.x + (int) (body.width * 0.55);
            int y = body.y + (int) (body.height * 0.74);
            Color buttonColour = new Color(128, 132, 140);

            g2.setColor(isHighlighted("SELECT") ? accentColour : buttonColour);
            g2.fillRoundRect(selectX, y, 58, 14, 14, 14);
            g2.setColor(new Color(92, 95, 101));
            g2.drawRoundRect(selectX, y, 58, 14, 14, 14);

            g2.setColor(isHighlighted("START") ? accentColour : buttonColour);
            g2.fillRoundRect(startX, y, 58, 14, 14, 14);
            g2.setColor(new Color(92, 95, 101));
            g2.drawRoundRect(startX, y, 58, 14, 14, 14);
        }

        private boolean isHighlighted(EmulatorButton button) {
            return highlightedButton != null && highlightedButton.equals(button);
        }

        private boolean isHighlighted(String buttonId) {
            return highlightedButton != null && highlightedButton.id().equalsIgnoreCase(buttonId);
        }

        private Rectangle bodyBounds() {
            int width = getWidth();
            int height = getHeight();
            int bodyWidth = Math.min(330, width - 330);
            int bodyHeight = Math.min(460, height - 36);
            int bodyX = (width - bodyWidth) / 2;
            int bodyY = Math.max(14, (height - bodyHeight) / 2 - 12);
            return new Rectangle(bodyX, bodyY, bodyWidth, bodyHeight);
        }

        private Rectangle calloutBounds(EmulatorButton button, int width, int height) {
            String id = button.id().toUpperCase(Locale.ROOT);
            return switch (id) {
                case "UP" -> new Rectangle((int) (width * 0.10), (int) (height * 0.08), calloutSize.width,
                        calloutSize.height);
                case "LEFT" -> new Rectangle((int) (width * 0.03), (int) (height * 0.36), calloutSize.width,
                        calloutSize.height);
                case "DOWN" -> new Rectangle((int) (width * 0.10), (int) (height * 0.70), calloutSize.width,
                        calloutSize.height);
                case "RIGHT" -> new Rectangle((int) (width * 0.21), (int) (height * 0.23), calloutSize.width,
                        calloutSize.height);
                case "A" -> new Rectangle((int) (width * 0.72), (int) (height * 0.10), calloutSize.width,
                        calloutSize.height);
                case "B" -> new Rectangle((int) (width * 0.76), (int) (height * 0.36), calloutSize.width,
                        calloutSize.height);
                case "SELECT" -> new Rectangle((int) (width * 0.30), (int) (height * 0.80), calloutSize.width,
                        calloutSize.height);
                case "START" -> new Rectangle((int) (width * 0.54), (int) (height * 0.80), calloutSize.width,
                        calloutSize.height);
                default -> new Rectangle((width - calloutSize.width) / 2, height - calloutSize.height - 12,
                        calloutSize.width, calloutSize.height);
            };
        }

        private Point buttonAnchor(EmulatorButton button, Rectangle body) {
            String id = button.id().toUpperCase(Locale.ROOT);
            int dpadCenterX = body.x + (int) (body.width * 0.29);
            int dpadCenterY = body.y + (int) (body.height * 0.64);
            int dpadArmLength = 34;
            int aCenterX = body.x + (int) (body.width * 0.68) + 22;
            int aCenterY = body.y + (int) (body.height * 0.52) + 22;
            int bCenterX = body.x + (int) (body.width * 0.55) + 22;
            int bCenterY = body.y + (int) (body.height * 0.61) + 22;
            int selectCenterX = body.x + (int) (body.width * 0.37) + 29;
            int startCenterX = body.x + (int) (body.width * 0.55) + 29;
            int startSelectY = body.y + (int) (body.height * 0.74) + 7;
            return switch (id) {
                case "UP" -> new Point(dpadCenterX, dpadCenterY - dpadArmLength);
                case "LEFT" -> new Point(dpadCenterX - dpadArmLength, dpadCenterY);
                case "DOWN" -> new Point(dpadCenterX, dpadCenterY + dpadArmLength);
                case "RIGHT" -> new Point(dpadCenterX + dpadArmLength, dpadCenterY);
                case "A" -> new Point(aCenterX, aCenterY);
                case "B" -> new Point(bCenterX, bCenterY);
                case "SELECT" -> new Point(selectCenterX, startSelectY);
                case "START" -> new Point(startCenterX, startSelectY);
                default -> new Point(body.x + body.width / 2, body.y + body.height / 2);
            };
        }

        private Point nearestCalloutEdge(Rectangle callout, Point origin) {
            int centerX = callout.x + (callout.width / 2);
            int centerY = callout.y + (callout.height / 2);
            int deltaX = origin.x - centerX;
            int deltaY = origin.y - centerY;

            if (Math.abs(deltaX) > Math.abs(deltaY)) {
                return new Point(deltaX >= 0 ? callout.x + callout.width : callout.x, centerY);
            }
            return new Point(centerX, deltaY >= 0 ? callout.y + callout.height : callout.y);
        }

        private void setHighlightedButton(EmulatorButton button) {
            highlightedButton = button;
            repaint();
        }

        private final class BindingCallout extends JPanel {

            private BindingCallout(EmulatorButton button, InputMode mode) {
                setOpaque(true);
                setBackground(Styling.cardTintColour);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Styling.cardTintBorderColour, 1, true),
                        BorderFactory.createEmptyBorder(8, 8, 8, 8)));
                setLayout(new BorderLayout(0, 8));

                JLabel titleLabel = new JLabel(formatControlButtonName(button), SwingConstants.CENTER);
                titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 11f));
                titleLabel.setForeground(accentColour);

                JButton actionButton = createPrimaryButton(mode == InputMode.KEYBOARD
                        ? Settings.inputBindings.GetKeyText(button)
                        : Settings.controllerBindings.GetBindingText(button));
                actionButton.setPreferredSize(new Dimension(116, 30));
                actionButton.setFont(Styling.menuFont.deriveFont(Font.BOLD, 11f));
                actionButton.addActionListener(event -> {
                    if (mode == InputMode.KEYBOARD) {
                        captureKeyboardBinding(button);
                    } else {
                        captureControllerBinding(button);
                    }
                });

                add(titleLabel, BorderLayout.NORTH);
                add(actionButton, BorderLayout.SOUTH);

                installHover(button, this);
                installHover(button, titleLabel);
                installHover(button, actionButton);

                if (mode == InputMode.KEYBOARD) {
                    keyboardBindingButtons.put(button, actionButton);
                } else {
                    controllerBindingButtons.put(button, actionButton);
                    controllerCaptureComponents.add(actionButton);
                }
            }

            private void installHover(EmulatorButton button, Component component) {
                component.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent event) {
                        setHighlightedButton(button);
                    }

                    @Override
                    public void mouseExited(MouseEvent event) {
                        setHighlightedButton(null);
                    }
                });
            }
        }
    }
}

