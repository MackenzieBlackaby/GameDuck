package com.blackaby.Frontend;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;

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
        button.setBorderPainted(false);
        button.setBackground(accentColour);
        button.setForeground(Color.WHITE);
        button.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.primaryButtonBorderColour, 1, true),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
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
        button.setBorderPainted(false);
        button.setBackground(Styling.buttonSecondaryBackground);
        button.setForeground(accentColour);
        button.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColour, 1, true),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
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
        badge.setBackground(new Color(217, 231, 247));
        badge.setForeground(accentColour);
        badge.setFont(Styling.menuFont.deriveFont(Font.BOLD, 11f));
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(160, 186, 216), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return badge;
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
}
