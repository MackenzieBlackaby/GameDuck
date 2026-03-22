package com.blackaby.Frontend;

import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Small information window describing the project.
 */
public final class AboutWindow extends DuckWindow {

    private static final Dimension minimumWindowSize = new Dimension(640, 680);
    private static final Dimension licenseViewportSize = new Dimension(560, 300);

    /**
     * Creates the about window and fills it with the current project details.
     */
    public AboutWindow() {
        super(UiText.AboutWindow.WINDOW_TITLE, minimumWindowSize.width, minimumWindowSize.height, false);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Styling.appBackgroundColour);

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        content.add(BuildSummaryCard(), BorderLayout.NORTH);
        content.add(BuildLicenseCard(), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);

        pack();
        setSize(Math.max(getWidth(), minimumWindowSize.width), Math.max(getHeight(), minimumWindowSize.height));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel BuildSummaryCard() {
        JPanel card = CreateCard(new BorderLayout(18, 0));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.surfaceBorderColour, 1),
                BorderFactory.createEmptyBorder(22, 22, 22, 22)));

        JLabel logoLabel = new JLabel(AppAssets.AboutLogoIcon(96));
        logoLabel.setVerticalAlignment(JLabel.TOP);
        card.add(logoLabel, BorderLayout.WEST);

        JPanel textColumn = new JPanel();
        textColumn.setOpaque(false);
        textColumn.setLayout(new javax.swing.BoxLayout(textColumn, javax.swing.BoxLayout.Y_AXIS));

        JLabel titleLabel = CreateLabel(UiText.Common.APP_NAME, Styling.titleFont.deriveFont(28f), Styling.accentColour);
        JLabel versionLabel = CreateLabel(UiText.AboutWindow.VERSION,
                Styling.menuFont.deriveFont(Font.BOLD, 13f), Styling.mutedTextColour);
        JLabel summaryLabel = CreateLabel(UiText.AboutWindow.SUMMARY,
                Styling.menuFont.deriveFont(Font.BOLD, 15f), Styling.accentColour);
        JLabel authorLabel = CreateLabel(UiText.AboutWindow.AUTHOR, Styling.menuFont.deriveFont(14f),
                Styling.mutedTextColour);
        JLabel projectLabel = CreateLabel(UiText.AboutWindow.PROJECT_NOTE, Styling.menuFont.deriveFont(14f),
                Styling.mutedTextColour);
        JLabel legalLabel = CreateLabel(UiText.AboutWindow.LEGAL_NOTE, Styling.menuFont.deriveFont(Font.ITALIC, 13f),
                Styling.mutedTextColour);

        textColumn.add(titleLabel);
        textColumn.add(versionLabel);
        textColumn.add(javax.swing.Box.createRigidArea(new Dimension(0, 10)));
        textColumn.add(summaryLabel);
        textColumn.add(javax.swing.Box.createRigidArea(new Dimension(0, 8)));
        textColumn.add(authorLabel);
        textColumn.add(projectLabel);
        textColumn.add(javax.swing.Box.createRigidArea(new Dimension(0, 12)));
        textColumn.add(legalLabel);

        card.add(textColumn, BorderLayout.CENTER);
        return card;
    }

    private JPanel BuildLicenseCard() {
        JPanel card = CreateCard(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.surfaceBorderColour, 1),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)));

        JPanel titleRow = new JPanel(new BorderLayout(0, 4));
        titleRow.setOpaque(false);

        JLabel titleLabel = CreateLabel(UiText.AboutWindow.LICENSE_TITLE,
                Styling.menuFont.deriveFont(Font.BOLD, 16f), Styling.accentColour);
        JLabel hintLabel = CreateLabel(UiText.AboutWindow.LICENSE_HINT,
                Styling.menuFont.deriveFont(12f), Styling.mutedTextColour);
        titleRow.add(titleLabel, BorderLayout.NORTH);
        titleRow.add(hintLabel, BorderLayout.CENTER);

        JTextArea licenseArea = new JTextArea(LoadLicenseText());
        licenseArea.setEditable(false);
        licenseArea.setFocusable(false);
        licenseArea.setLineWrap(true);
        licenseArea.setWrapStyleWord(true);
        licenseArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        licenseArea.setBackground(Styling.displayFrameColour);
        licenseArea.setForeground(Styling.fpsForegroundColour);
        licenseArea.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        licenseArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(licenseArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1));
        scrollPane.getViewport().setBackground(Styling.displayFrameColour);
        scrollPane.setPreferredSize(licenseViewportSize);

        card.add(titleRow, BorderLayout.NORTH);
        card.add(scrollPane, BorderLayout.CENTER);
        return card;
    }

    private JPanel CreateCard(BorderLayout layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(Styling.surfaceColour);
        return panel;
    }

    private JLabel CreateLabel(String text, Font font, java.awt.Color colour) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(colour);
        return label;
    }

    private String LoadLicenseText() {
        try (InputStream stream = AboutWindow.class.getResourceAsStream("/LICENSE.txt")) {
            if (stream == null) {
                return UiText.AboutWindow.LICENSE_LOAD_ERROR;
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            return UiText.AboutWindow.LICENSE_LOAD_ERROR;
        }
    }
}
