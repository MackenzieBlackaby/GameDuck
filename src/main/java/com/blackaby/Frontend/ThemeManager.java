package com.blackaby.Frontend;

import com.blackaby.Misc.Config;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;

/**
 * Browser for loading and deleting saved host themes.
 */
public final class ThemeManager extends DuckWindow {

    private final Color panelBackground;
    private final Color cardBackground;
    private final Color cardBorder;
    private final Color accentColour;
    private final Color mutedTextColour;
    private final DefaultListModel<String> themeListModel = new DefaultListModel<>();
    private final Runnable onThemeChanged;

    /**
     * Creates the theme browser.
     *
     * @param onThemeChanged callback fired after a theme is loaded
     */
    public ThemeManager(Runnable onThemeChanged) {
        super(UiText.ThemeManager.WINDOW_TITLE, 460, 360, false);
        this.onThemeChanged = onThemeChanged;
        panelBackground = Styling.appBackgroundColour;
        cardBackground = Styling.surfaceColour;
        cardBorder = Styling.surfaceBorderColour;
        accentColour = Styling.accentColour;
        mutedTextColour = Styling.mutedTextColour;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(panelBackground);

        add(BuildHeader(), BorderLayout.NORTH);
        add(BuildBody(), BorderLayout.CENTER);

        setVisible(true);
    }

    private JComponent BuildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setBackground(panelBackground);
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));

        JLabel titleLabel = new JLabel(UiText.ThemeManager.TITLE);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(accentColour);

        JLabel subtitleLabel = new JLabel(UiText.ThemeManager.SUBTITLE);
        subtitleLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        subtitleLabel.setForeground(mutedTextColour);

        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subtitleLabel, BorderLayout.CENTER);
        return header;
    }

    private JComponent BuildBody() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 16));
        wrapper.setBackground(panelBackground);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));

        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setBackground(cardBackground);
        card.setBorder(CreateCardBorder());

        JList<String> themeList = new JList<>(themeListModel);
        themeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        themeList.setFont(Styling.menuFont.deriveFont(13f));
        themeList.setFixedCellHeight(30);
        themeList.setBackground(Styling.cardTintColour);
        themeList.setSelectionBackground(Styling.listSelectionColour);
        themeList.setSelectionForeground(accentColour);

        RefreshThemeList();

        if (!themeListModel.isEmpty()) {
            themeList.setSelectedIndex(0);
        }

        JScrollPane scrollPane = new JScrollPane(themeList);
        scrollPane.setBorder(BorderFactory.createLineBorder(cardBorder, 1));
        card.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);

        JButton deleteButton = CreateSecondaryButton(UiText.ThemeManager.DELETE_BUTTON);
        deleteButton.addActionListener(event -> {
            String selectedTheme = themeList.getSelectedValue();
            if (selectedTheme == null) {
                return;
            }

            int result = JOptionPane.showConfirmDialog(this,
                    UiText.ThemeManager.DeleteConfirmMessage(selectedTheme), UiText.ThemeManager.DELETE_CONFIRM_TITLE,
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                Config.DeleteTheme(selectedTheme);
                RefreshThemeList();
                if (!themeListModel.isEmpty()) {
                    themeList.setSelectedIndex(0);
                }
            }
        });

        JButton loadButton = CreatePrimaryButton(UiText.ThemeManager.LOAD_BUTTON);
        loadButton.addActionListener(event -> {
            String selectedTheme = themeList.getSelectedValue();
            if (selectedTheme == null) {
                return;
            }

            if (Config.LoadTheme(selectedTheme) && onThemeChanged != null) {
                onThemeChanged.run();
            }
            dispose();
        });

        buttonPanel.add(deleteButton);
        buttonPanel.add(loadButton);
        card.add(buttonPanel, BorderLayout.SOUTH);

        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    private void RefreshThemeList() {
        themeListModel.clear();
        List<String> themeNames = Config.GetSavedThemeNames();
        for (String themeName : themeNames) {
            themeListModel.addElement(themeName);
        }
    }

    private Border CreateCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardBorder, 1),
                BorderFactory.createEmptyBorder(18, 18, 18, 18));
    }

    private JButton CreatePrimaryButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setBackground(accentColour);
        button.setForeground(Color.WHITE);
        button.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.primaryButtonBorderColour, 1, true),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        return button;
    }

    private JButton CreateSecondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setBackground(Styling.buttonSecondaryBackground);
        button.setForeground(accentColour);
        button.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardBorder, 1, true),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        return button;
    }
}
