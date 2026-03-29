package com.blackaby.Frontend;

import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SwingConstants;
import javax.swing.JTabbedPane;
import javax.swing.RootPaneContainer;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.plaf.basic.BasicMenuBarUI;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicMenuUI;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.border.Border;
import java.awt.Adjustable;
import java.awt.Component;
import java.awt.Container;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.FontMetrics;

final class WindowUiSupport {

    private WindowUiSupport() {
    }

    static JButton createPrimaryButton(String text, Color accentColour) {
        JButton button = new JButton(text);
        stylePrimaryButton(button, accentColour);
        return button;
    }

    static void stylePrimaryButton(JButton button, Color accentColour) {
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setRolloverEnabled(true);
        button.setBackground(accentColour);
        button.setForeground(Color.WHITE);
        button.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.primaryButtonBorderColour, 1, true),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        if (!(button.getUI() instanceof ThemedButtonUI)) {
            button.setUI(new ThemedButtonUI());
        }
    }

    static JButton createSecondaryButton(String text, Color accentColour, Color borderColour) {
        JButton button = new JButton(text);
        styleSecondaryButton(button, accentColour, borderColour);
        return button;
    }

    static void styleSecondaryButton(JButton button, Color accentColour, Color borderColour) {
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setRolloverEnabled(true);
        button.setBackground(Styling.buttonSecondaryBackground);
        button.setForeground(accentColour);
        button.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColour, 1, true),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        if (!(button.getUI() instanceof ThemedButtonUI)) {
            button.setUI(new ThemedButtonUI());
        }
    }

    static Border createCardBorder(Color borderColour, boolean rounded, int padding) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColour, 1, rounded),
                BorderFactory.createEmptyBorder(padding, padding, padding, padding));
    }

    static Border createLineBorder(Color borderColour) {
        return BorderFactory.createLineBorder(borderColour, 1);
    }

    static JLabel createBadgeLabel(String text, Color accentColour) {
        JLabel badge = new JLabel(text);
        badge.setOpaque(true);
        badge.setBackground(Styling.sectionHighlightColour);
        badge.setForeground(accentColour);
        badge.setFont(Styling.menuFont.deriveFont(Font.BOLD, 11f));
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.sectionHighlightBorderColour, 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return badge;
    }

    static void applyComponentTheme(Component component) {
        if (component == null) {
            return;
        }

        if (component instanceof RootPaneContainer rootPaneContainer) {
            JRootPane rootPane = rootPaneContainer.getRootPane();
            if (rootPane != null) {
                rootPane.setOpaque(true);
                rootPane.setBackground(Styling.appBackgroundColour);
                rootPane.setBorder(createLineBorder(Styling.surfaceBorderColour));
                JLayeredPane layeredPane = rootPane.getLayeredPane();
                if (layeredPane != null) {
                    layeredPane.setOpaque(true);
                    layeredPane.setBackground(Styling.appBackgroundColour);
                }
                Container contentPane = rootPane.getContentPane();
                if (contentPane instanceof JComponent contentComponent) {
                    contentComponent.setOpaque(true);
                    contentComponent.setBackground(Styling.appBackgroundColour);
                }
            }
        }

        if (component instanceof DuckWindow duckWindow) {
            duckWindow.ApplyWindowChromeTheme();
        }

        if (component instanceof JMenuBar menuBar) {
            menuBar.setOpaque(true);
            menuBar.setBackground(Styling.surfaceColour);
            menuBar.setForeground(Styling.accentColour);
            if (!(menuBar.getUI() instanceof ThemedMenuBarUI)) {
                menuBar.setUI(new ThemedMenuBarUI());
            }
        }
        if (component instanceof JMenu menu) {
            styleMenu(menu);
        }
        if (component instanceof JPopupMenu popupMenu) {
            stylePopupMenu(popupMenu);
        }
        if (component instanceof JMenuItem menuItem) {
            styleMenuItem(menuItem);
        }
        if (component instanceof JPopupMenu.Separator separator) {
            styleMenuSeparator(separator);
        }
        if (component instanceof JScrollPane scrollPane) {
            styleScrollPane(scrollPane);
        }
        if (component instanceof JScrollBar scrollBar) {
            styleScrollBar(scrollBar);
        }
        if (component instanceof JComboBox<?> comboBox) {
            styleComboBox(comboBox);
        }
        if (component instanceof JSpinner spinner) {
            styleSpinner(spinner);
        }
        if (component instanceof JTabbedPane tabbedPane) {
            styleTabbedPane(tabbedPane);
        }
        if (component instanceof JList<?> list) {
            list.setBackground(Styling.surfaceColour);
            list.setForeground(Styling.mutedTextColour);
            list.setSelectionBackground(Styling.listSelectionColour);
            list.setSelectionForeground(Styling.accentColour);
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyComponentTheme(child);
            }
        }
    }

    static void styleMenu(JMenu menu) {
        if (menu == null) {
            return;
        }

        menu.setOpaque(true);
        menu.setBackground(Styling.surfaceColour);
        menu.setForeground(Styling.accentColour);
        menu.setBorderPainted(false);
        if (!(menu.getUI() instanceof ThemedMenuUI)) {
            menu.setUI(new ThemedMenuUI());
        }
        if (menu.getPopupMenu() != null) {
            stylePopupMenu(menu.getPopupMenu());
        }
    }

    static void stylePopupMenu(JPopupMenu popupMenu) {
        if (popupMenu == null) {
            return;
        }

        popupMenu.setOpaque(true);
        popupMenu.setBackground(Styling.surfaceColour);
        popupMenu.setBorder(createLineBorder(Styling.surfaceBorderColour));
        if (!(popupMenu.getUI() instanceof ThemedPopupMenuUI)) {
            popupMenu.setUI(new ThemedPopupMenuUI());
        }
    }

    static void styleMenuItem(JMenuItem menuItem) {
        if (menuItem == null) {
            return;
        }

        menuItem.setOpaque(true);
        menuItem.setBackground(Styling.surfaceColour);
        menuItem.setForeground(Styling.accentColour);
        menuItem.setBorderPainted(false);
        if (menuItem instanceof JMenu) {
            return;
        }
        if (!(menuItem.getUI() instanceof ThemedMenuItemUI)) {
            menuItem.setUI(new ThemedMenuItemUI());
        }
    }

    static void styleMenuSeparator(JPopupMenu.Separator separator) {
        if (separator == null) {
            return;
        }

        separator.setOpaque(false);
        separator.setBackground(Styling.surfaceColour);
        separator.setForeground(Styling.surfaceBorderColour);
        separator.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        separator.setPreferredSize(new Dimension(0, 9));
    }

    private static void styleScrollPane(JScrollPane scrollPane) {
        JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
        JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();
        if (verticalScrollBar != null) {
            verticalScrollBar.setBackground(Styling.surfaceColour);
            styleScrollBar(verticalScrollBar);
        }
        if (horizontalScrollBar != null) {
            horizontalScrollBar.setBackground(Styling.surfaceColour);
            styleScrollBar(horizontalScrollBar);
        }
        Component upperRight = scrollPane.getCorner(JScrollPane.UPPER_RIGHT_CORNER);
        if (upperRight == null) {
            upperRight = createScrollCorner();
            scrollPane.setCorner(JScrollPane.UPPER_RIGHT_CORNER, upperRight);
        }
        Component lowerRight = scrollPane.getCorner(JScrollPane.LOWER_RIGHT_CORNER);
        if (lowerRight == null) {
            lowerRight = createScrollCorner();
            scrollPane.setCorner(JScrollPane.LOWER_RIGHT_CORNER, lowerRight);
        }
        upperRight.setBackground(Styling.surfaceColour);
        lowerRight.setBackground(Styling.surfaceColour);
    }

    private static JComponent createScrollCorner() {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        return panel;
    }

    private static void styleScrollBar(JScrollBar scrollBar) {
        if (scrollBar == null) {
            return;
        }
        scrollBar.setOpaque(true);
        scrollBar.setBackground(Styling.surfaceColour);
        scrollBar.setForeground(Styling.accentColour);
        scrollBar.setBorder(BorderFactory.createEmptyBorder());
        if (!(scrollBar.getUI() instanceof ThemedScrollBarUI)) {
            scrollBar.setUI(new ThemedScrollBarUI());
        }
    }

    private static void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setOpaque(true);
        comboBox.setBackground(Styling.surfaceColour);
        comboBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.surfaceBorderColour, 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 2)));
        if (!(comboBox.getUI() instanceof ThemedComboBoxUI)) {
            comboBox.setUI(new ThemedComboBoxUI());
        }
    }

    private static void styleSpinner(JSpinner spinner) {
        spinner.setOpaque(true);
        spinner.setBackground(Styling.surfaceColour);
        spinner.setBorder(BorderFactory.createLineBorder(Styling.surfaceBorderColour, 1, true));
        if (!(spinner.getUI() instanceof ThemedSpinnerUI)) {
            spinner.setUI(new ThemedSpinnerUI());
        }
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            defaultEditor.getTextField().setBackground(Styling.surfaceColour);
            defaultEditor.getTextField().setForeground(Styling.mutedTextColour);
            defaultEditor.getTextField().setCaretColor(Styling.accentColour);
            defaultEditor.getTextField().setSelectionColor(Styling.accentColour);
            defaultEditor.getTextField().setSelectedTextColor(Styling.surfaceColour);
            defaultEditor.getTextField().setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        }
    }

    private static void styleTabbedPane(JTabbedPane tabbedPane) {
        tabbedPane.setOpaque(true);
        tabbedPane.setBackground(Styling.appBackgroundColour);
        tabbedPane.setForeground(Styling.accentColour);
        if (!(tabbedPane.getUI() instanceof ThemedTabbedPaneUI)) {
            tabbedPane.setUI(new ThemedTabbedPaneUI());
        }
    }

    private static JButton createThemedArrowButton(int direction) {
        BasicArrowButton button = new BasicArrowButton(
                direction,
                Styling.buttonSecondaryBackground,
                Styling.surfaceBorderColour,
                Styling.displayFrameBorderColour,
                Styling.sectionHighlightBorderColour);
        button.setOpaque(true);
        button.setBackground(Styling.buttonSecondaryBackground);
        button.setForeground(Styling.accentColour);
        button.setBorder(BorderFactory.createLineBorder(Styling.surfaceBorderColour, 1));
        button.setFocusPainted(false);
        return button;
    }

    private static final class ThemedButtonUI extends BasicButtonUI {
        @Override
        public void update(Graphics graphics, JComponent component) {
            if (!(component instanceof AbstractButton button)) {
                super.update(graphics, component);
                return;
            }

            Graphics2D graphics2d = (Graphics2D) graphics.create();
            graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2d.setColor(resolveBackground(button));
            graphics2d.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 12, 12);
            graphics2d.dispose();
            paint(graphics, component);
        }

        @Override
        protected void paintFocus(Graphics graphics, AbstractButton button, Rectangle viewRect,
                                  Rectangle textRect, Rectangle iconRect) {
            // Border + background contrast is enough for these controls.
        }

        private Color resolveBackground(AbstractButton button) {
            Color base = button.getBackground();
            ButtonModel model = button.getModel();
            if (!button.isEnabled()) {
                return blend(base, Styling.appBackgroundColour, 0.45f);
            }
            if (model.isPressed() || model.isSelected()) {
                return blend(base, Styling.displayFrameBorderColour, 0.28f);
            }
            if (model.isRollover()) {
                return blend(base, Color.WHITE, 0.08f);
            }
            return base;
        }
    }

    private static final class ThemedScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void installComponents() {
            super.installComponents();
            ensureButtons();
        }

        @Override
        protected void configureScrollBarColors() {
            thumbColor = Styling.cardTintBorderColour;
            thumbDarkShadowColor = Styling.displayFrameBorderColour;
            thumbHighlightColor = Styling.sectionHighlightBorderColour;
            thumbLightShadowColor = Styling.sectionHighlightColour;
            trackColor = Styling.surfaceColour;
            trackHighlightColor = Styling.buttonSecondaryBackground;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createThemedArrowButton(orientation);
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createThemedArrowButton(orientation);
        }

        @Override
        public void layoutContainer(Container scrollbarContainer) {
            ensureButtons();
            try {
                super.layoutContainer(scrollbarContainer);
            } catch (NullPointerException exception) {
                ensureButtons();
                if (decrButton == null || incrButton == null) {
                    return;
                }
                super.layoutContainer(scrollbarContainer);
            }
        }

        @Override
        protected void layoutVScrollbar(JScrollBar scrollbar) {
            ensureButtons();
            super.layoutVScrollbar(scrollbar);
        }

        @Override
        protected void layoutHScrollbar(JScrollBar scrollbar) {
            ensureButtons();
            super.layoutHScrollbar(scrollbar);
        }

        @Override
        protected void paintTrack(Graphics graphics, JComponent component, Rectangle trackBounds) {
            Graphics2D graphics2d = (Graphics2D) graphics.create();
            graphics2d.setColor(Styling.surfaceColour);
            graphics2d.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
            graphics2d.setColor(Styling.surfaceBorderColour);
            graphics2d.drawRect(trackBounds.x, trackBounds.y, Math.max(0, trackBounds.width - 1),
                    Math.max(0, trackBounds.height - 1));
            graphics2d.dispose();
        }

        @Override
        protected void paintThumb(Graphics graphics, JComponent component, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }

            Graphics2D graphics2d = (Graphics2D) graphics.create();
            graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2d.setColor(Styling.cardTintBorderColour);
            int arc = scrollbar.getOrientation() == Adjustable.VERTICAL ? thumbBounds.width : thumbBounds.height;
            graphics2d.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2,
                    Math.max(0, thumbBounds.width - 4), Math.max(0, thumbBounds.height - 4), arc, arc);
            graphics2d.setColor(Styling.sectionHighlightBorderColour);
            graphics2d.drawRoundRect(thumbBounds.x + 2, thumbBounds.y + 2,
                    Math.max(0, thumbBounds.width - 5), Math.max(0, thumbBounds.height - 5), arc, arc);
            graphics2d.dispose();
        }

        @Override
        protected Dimension getMinimumThumbSize() {
            return scrollbar != null && scrollbar.getOrientation() == Adjustable.VERTICAL
                    ? new Dimension(12, 28)
                    : new Dimension(28, 12);
        }

        private int decreaseDirection() {
            return scrollbar != null && scrollbar.getOrientation() == Adjustable.VERTICAL
                    ? SwingConstants.NORTH
                    : SwingConstants.WEST;
        }

        private int increaseDirection() {
            return scrollbar != null && scrollbar.getOrientation() == Adjustable.VERTICAL
                    ? SwingConstants.SOUTH
                    : SwingConstants.EAST;
        }

        private void ensureButtons() {
            if (scrollbar == null) {
                return;
            }
            if (decrButton == null) {
                decrButton = createDecreaseButton(decreaseDirection());
            }
            if (decrButton.getParent() != scrollbar) {
                scrollbar.add(decrButton);
            }
            if (incrButton == null) {
                incrButton = createIncreaseButton(increaseDirection());
            }
            if (incrButton.getParent() != scrollbar) {
                scrollbar.add(incrButton);
            }
        }
    }

    private static final class ThemedComboBoxUI extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            JButton button = createThemedArrowButton(SwingConstants.SOUTH);
            button.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Styling.surfaceBorderColour));
            return button;
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox) {
                @Override
                protected JScrollPane createScroller() {
                    JScrollPane scroller = super.createScroller();
                    styleScrollPane(scroller);
                    scroller.setBorder(createLineBorder(Styling.surfaceBorderColour));
                    return scroller;
                }
            };
            popup.setBorder(createLineBorder(Styling.surfaceBorderColour));
            popup.setBackground(Styling.surfaceColour);
            popup.getList().setBackground(Styling.surfaceColour);
            popup.getList().setForeground(Styling.mutedTextColour);
            popup.getList().setSelectionBackground(Styling.listSelectionColour);
            popup.getList().setSelectionForeground(Styling.accentColour);
            return popup;
        }
    }

    private static final class ThemedSpinnerUI extends BasicSpinnerUI {
        @Override
        protected Component createNextButton() {
            JButton button = createThemedArrowButton(SwingConstants.NORTH);
            installNextButtonListeners(button);
            return button;
        }

        @Override
        protected Component createPreviousButton() {
            JButton button = createThemedArrowButton(SwingConstants.SOUTH);
            installPreviousButtonListeners(button);
            return button;
        }
    }

    private static final class ThemedTabbedPaneUI extends BasicTabbedPaneUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            highlight = Styling.sectionHighlightBorderColour;
            lightHighlight = Styling.sectionHighlightColour;
            shadow = Styling.surfaceBorderColour;
            darkShadow = Styling.displayFrameBorderColour;
            contentBorderInsets = new Insets(1, 1, 1, 1);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            tabAreaInsets = new Insets(6, 0, 0, 0);
            tabInsets = new Insets(8, 14, 8, 14);
        }

        @Override
        public void paint(Graphics graphics, JComponent component) {
            graphics.setColor(Styling.appBackgroundColour);
            graphics.fillRect(0, 0, component.getWidth(), component.getHeight());
            super.paint(graphics, component);
        }

        @Override
        protected void paintTabArea(Graphics graphics, int tabPlacement, int selectedIndex) {
            graphics.setColor(Styling.appBackgroundColour);
            graphics.fillRect(0, 0, tabPane.getWidth(), calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight));
            super.paintTabArea(graphics, tabPlacement, selectedIndex);
        }

        @Override
        protected void paintTabBackground(Graphics graphics, int tabPlacement, int tabIndex,
                                          int x, int y, int w, int h, boolean isSelected) {
            graphics.setColor(isSelected ? Styling.surfaceColour : Styling.buttonSecondaryBackground);
            graphics.fillRect(x, y, w, h);
        }

        @Override
        protected void paintTabBorder(Graphics graphics, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
            graphics.setColor(isSelected ? Styling.sectionHighlightBorderColour : Styling.surfaceBorderColour);
            graphics.drawRect(x, y, w - 1, h - 1);
        }

        @Override
        protected void paintFocusIndicator(Graphics graphics, int tabPlacement, Rectangle[] rects,
                                           int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {
            // Intentionally no focus ring; selected border is enough in this theme.
        }

        @Override
        protected void paintText(Graphics graphics, int tabPlacement, Font font, FontMetrics metrics,
                                 int tabIndex, String title, Rectangle textRect, boolean isSelected) {
            graphics.setFont(font);
            graphics.setColor(isSelected ? Styling.accentColour : Styling.mutedTextColour);
            int mnemonicIndex = tabPane.getDisplayedMnemonicIndexAt(tabIndex);
            javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(
                    graphics,
                    title,
                    mnemonicIndex,
                    textRect.x,
                    textRect.y + metrics.getAscent());
        }

        @Override
        protected void paintContentBorderTopEdge(Graphics graphics, int tabPlacement, int selectedIndex,
                                                 int x, int y, int w, int h) {
            graphics.setColor(Styling.surfaceBorderColour);
            graphics.drawLine(x, y, x + w - 1, y);
        }

        @Override
        protected void paintContentBorderLeftEdge(Graphics graphics, int tabPlacement, int selectedIndex,
                                                  int x, int y, int w, int h) {
            graphics.setColor(Styling.surfaceBorderColour);
            graphics.drawLine(x, y, x, y + h - 1);
        }

        @Override
        protected void paintContentBorderRightEdge(Graphics graphics, int tabPlacement, int selectedIndex,
                                                   int x, int y, int w, int h) {
            graphics.setColor(Styling.surfaceBorderColour);
            graphics.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
        }

        @Override
        protected void paintContentBorderBottomEdge(Graphics graphics, int tabPlacement, int selectedIndex,
                                                    int x, int y, int w, int h) {
            graphics.setColor(Styling.surfaceBorderColour);
            graphics.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
        }
    }

    private static final class ThemedMenuBarUI extends BasicMenuBarUI {
        @Override
        public void paint(Graphics graphics, JComponent component) {
            Graphics2D graphics2d = (Graphics2D) graphics.create();
            graphics2d.setColor(Styling.surfaceColour);
            graphics2d.fillRect(0, 0, component.getWidth(), component.getHeight());
            graphics2d.dispose();
        }
    }

    private static final class ThemedPopupMenuUI extends BasicPopupMenuUI {
        @Override
        public void paint(Graphics graphics, JComponent component) {
            Graphics2D graphics2d = (Graphics2D) graphics.create();
            graphics2d.setColor(Styling.surfaceColour);
            graphics2d.fillRect(0, 0, component.getWidth(), component.getHeight());
            graphics2d.dispose();
        }
    }

    private static final class ThemedMenuItemUI extends BasicMenuItemUI {
        @Override
        protected void paintBackground(Graphics graphics, JMenuItem menuItem, Color backgroundColor) {
            Graphics2D graphics2d = (Graphics2D) graphics.create();
            graphics2d.setColor(resolveMenuRowBackground(menuItem));
            graphics2d.fillRect(0, 0, menuItem.getWidth(), menuItem.getHeight());
            graphics2d.dispose();
        }
    }

    private static final class ThemedMenuUI extends BasicMenuUI {
        @Override
        protected void paintBackground(Graphics graphics, JMenuItem menuItem, Color backgroundColor) {
            Graphics2D graphics2d = (Graphics2D) graphics.create();
            graphics2d.setColor(resolveMenuRowBackground(menuItem));
            graphics2d.fillRect(0, 0, menuItem.getWidth(), menuItem.getHeight());
            graphics2d.dispose();
        }
    }

    static boolean containsIgnoreCase(String query, String... candidates) {
        String normalisedQuery = query == null ? "" : query.trim().toLowerCase();
        if (normalisedQuery.isBlank()) {
            return true;
        }

        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase().contains(normalisedQuery)) {
                return true;
            }
        }
        return false;
    }

    static String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static Color blend(Color base, Color overlay, float overlayWeight) {
        float clampedWeight = Math.max(0f, Math.min(1f, overlayWeight));
        float baseWeight = 1f - clampedWeight;
        int red = Math.round((base.getRed() * baseWeight) + (overlay.getRed() * clampedWeight));
        int green = Math.round((base.getGreen() * baseWeight) + (overlay.getGreen() * clampedWeight));
        int blue = Math.round((base.getBlue() * baseWeight) + (overlay.getBlue() * clampedWeight));
        return new Color(red, green, blue);
    }

    private static Color resolveMenuRowBackground(JMenuItem menuItem) {
        if (menuItem == null || !menuItem.isEnabled()) {
            return Styling.surfaceColour;
        }

        ButtonModel model = menuItem.getModel();
        boolean selected = model.isArmed() || model.isSelected();
        if (menuItem instanceof JMenu menu && menu.getParent() instanceof JMenuBar) {
            if (selected || model.isRollover()) {
                return blend(Styling.surfaceColour, Styling.sectionHighlightColour, 0.65f);
            }
            return Styling.surfaceColour;
        }
        return selected ? Styling.listSelectionColour : Styling.surfaceColour;
    }
}
