package com.blackaby.Frontend;

import com.blackaby.Backend.Emulation.Misc.ROM;
import com.blackaby.Backend.Helpers.GameArtProvider;
import com.blackaby.Backend.Helpers.GameLibraryStore;
import com.blackaby.Backend.Helpers.GameLibraryStore.LibraryEntry;
import com.blackaby.Backend.Helpers.GameMetadataStore;
import com.blackaby.Backend.Helpers.LibretroMetadataProvider;
import com.blackaby.Backend.Helpers.LibretroMetadataProvider.LibretroMetadata;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Detailed ROM information window for a library entry.
 */
public final class LibraryGameInfoWindow extends DuckWindow {

    private static final DateTimeFormatter lastPlayedFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final LibraryEntry entry;
    private final ROM rom;
    private final Color panelBackground;
    private final Color cardBackground;
    private final Color cardBorder;
    private final Color accentColour;
    private final Color mutedTextColour;
    private final Runnable artworkUpdatedAction;
    private final Runnable entryDeletedAction;
    private final JLabel publisherValueLabel = new JLabel();
    private final JLabel releaseYearValueLabel = new JLabel();
    private final JLabel databaseValueLabel = new JLabel();
    private final JButton getArtworkButton = new JButton();
    private final JButton deleteRomButton = new JButton();

    public LibraryGameInfoWindow(LibraryEntry entry, ROM rom) {
        this(entry, rom, null, null);
    }

    public LibraryGameInfoWindow(LibraryEntry entry, ROM rom, Runnable artworkUpdatedAction, Runnable entryDeletedAction) {
        super(UiText.LibraryWindow.INFO_WINDOW_TITLE, 760, 640, true);
        this.entry = entry;
        this.rom = rom;
        this.artworkUpdatedAction = artworkUpdatedAction;
        this.entryDeletedAction = entryDeletedAction;
        panelBackground = Styling.appBackgroundColour;
        cardBackground = Styling.surfaceColour;
        cardBorder = Styling.surfaceBorderColour;
        accentColour = Styling.accentColour;
        mutedTextColour = Styling.mutedTextColour;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(panelBackground);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);

        populateLocalDetails();
        requestLibretroMetadata();
        setVisible(true);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setBackground(panelBackground);
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));

        JLabel titleLabel = new JLabel(resolveDisplayName());
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(accentColour);

        JLabel subtitleLabel = new JLabel(UiText.LibraryWindow.INFO_WINDOW_SUBTITLE);
        subtitleLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        subtitleLabel.setForeground(mutedTextColour);

        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subtitleLabel, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildBody() {
        JPanel content = new JPanel();
        content.setBackground(panelBackground);
        content.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));
        content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));

        content.add(createOverviewSection());
        content.add(javax.swing.Box.createVerticalStrut(14));
        content.add(createLibretroSection());
        content.add(javax.swing.Box.createVerticalStrut(14));
        content.add(createStorageSection());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(panelBackground);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private JComponent createOverviewSection() {
        JPanel content = new JPanel(new java.awt.GridLayout(6, 1, 0, 10));
        content.setOpaque(false);
        content.add(createDetailRow(UiText.LibraryWindow.INFO_LAST_PLAYED_TITLE, formatLastPlayed(entry.lastPlayedMillis())));
        content.add(createDetailRow(UiText.LibraryWindow.INFO_TARGET_HARDWARE_TITLE,
                UiText.LibraryWindow.InfoTargetHardware(rom.IsCgbOnly(), rom.IsCgbEnhanced())));
        content.add(createDetailRow(UiText.LibraryWindow.INFO_COMPATIBILITY_TITLE,
                UiText.LibraryWindow.InfoCompatibility(rom.IsCgbOnly(), rom.IsCgbEnhanced())));
        content.add(createDetailRow(UiText.LibraryWindow.INFO_HEADER_TITLE, valueOrUnknown(rom.GetHeaderTitle())));
        content.add(createDetailRow(UiText.LibraryWindow.INFO_MAPPER_TITLE, mapperDisplayName()));
        content.add(createDetailRow(UiText.LibraryWindow.INFO_ROM_SIZE_TITLE, buildRomSizeSummary()));
        return createSectionCard(UiText.LibraryWindow.INFO_OVERVIEW_TITLE, content);
    }

    private JComponent createLibretroSection() {
        publisherValueLabel.setText(asHtml(UiText.LibraryWindow.INFO_LOADING_VALUE));
        releaseYearValueLabel.setText(asHtml(UiText.LibraryWindow.INFO_LOADING_VALUE));
        databaseValueLabel.setText(asHtml(UiText.LibraryWindow.INFO_LOADING_VALUE));

        JPanel content = new JPanel(new java.awt.GridLayout(3, 1, 0, 10));
        content.setOpaque(false);
        content.add(createDetailRow(UiText.LibraryWindow.INFO_PUBLISHER_TITLE, publisherValueLabel));
        content.add(createDetailRow(UiText.LibraryWindow.INFO_RELEASE_YEAR_TITLE, releaseYearValueLabel));
        content.add(createDetailRow(UiText.LibraryWindow.INFO_DATABASE_TITLE, databaseValueLabel));
        return createSectionCard(UiText.LibraryWindow.INFO_LIBRETRO_TITLE, content);
    }

    private JComponent createStorageSection() {
        JLabel pathValueLabel = new JLabel(asHtml(entry.romPath().toString()));
        JLabel saveSupportLabel = new JLabel(asHtml(UiText.LibraryWindow.InfoBatterySave(rom.HasBatteryBackedSave())));

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);

        JPanel details = new JPanel(new java.awt.GridLayout(2, 1, 0, 10));
        details.setOpaque(false);
        details.add(createDetailRow(UiText.LibraryWindow.INFO_PATH_TITLE, pathValueLabel));
        details.add(createDetailRow(UiText.LibraryWindow.INFO_SAVE_SUPPORT_TITLE, saveSupportLabel));

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actionRow.setOpaque(false);

        getArtworkButton.setText(UiText.LibraryWindow.INFO_GET_ARTWORK_BUTTON);
        stylePrimaryButton(getArtworkButton);
        getArtworkButton.addActionListener(event -> requestArtwork());
        actionRow.add(getArtworkButton);

        actionRow.add(javax.swing.Box.createHorizontalStrut(8));

        JButton explorerButton = createPrimaryButton(UiText.LibraryWindow.INFO_OPEN_EXPLORER_BUTTON);
        explorerButton.addActionListener(event -> openInExplorer(entry.romPath()));
        actionRow.add(explorerButton);

        actionRow.add(javax.swing.Box.createHorizontalStrut(8));

        deleteRomButton.setText(UiText.LibraryWindow.INFO_DELETE_ROM_BUTTON);
        styleSecondaryButton(deleteRomButton);
        deleteRomButton.addActionListener(event -> deleteRom());
        actionRow.add(deleteRomButton);

        content.add(details, BorderLayout.CENTER);
        content.add(actionRow, BorderLayout.SOUTH);
        return createSectionCard(UiText.LibraryWindow.INFO_STORAGE_TITLE, content);
    }

    private JPanel createSectionCard(String title, JComponent content) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBackground(cardBackground);
        card.setBorder(createCardBorder());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(accentColour);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel createDetailRow(String title, String value) {
        return createDetailRow(title, new JLabel(asHtml(value)));
    }

    private JPanel createDetailRow(String title, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(accentColour);

        valueLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        valueLabel.setForeground(mutedTextColour);
        valueLabel.setVerticalAlignment(SwingConstants.TOP);

        row.add(titleLabel, BorderLayout.NORTH);
        row.add(valueLabel, BorderLayout.CENTER);
        return row;
    }

    private void populateLocalDetails() {
        if (entry == null || rom == null) {
            publisherValueLabel.setText(asHtml(UiText.LibraryWindow.INFO_UNKNOWN_VALUE));
            releaseYearValueLabel.setText(asHtml(UiText.LibraryWindow.INFO_UNKNOWN_VALUE));
            databaseValueLabel.setText(asHtml(UiText.LibraryWindow.INFO_UNKNOWN_VALUE));
        }
    }

    private void requestLibretroMetadata() {
        if (rom == null) {
            setLibretroMetadata(null);
            return;
        }

        CompletableFuture
                .supplyAsync(() -> LibretroMetadataProvider.FindMetadata(rom).orElse(null))
                .thenAccept(result -> SwingUtilities.invokeLater(() -> setLibretroMetadata(result)))
                .exceptionally(exception -> {
                    SwingUtilities.invokeLater(() -> setLibretroMetadata(null));
                    return null;
                });
    }

    private void setLibretroMetadata(LibretroMetadata metadata) {
        if (metadata == null) {
            publisherValueLabel.setText(asHtml(UiText.LibraryWindow.INFO_UNKNOWN_VALUE));
            releaseYearValueLabel.setText(asHtml(UiText.LibraryWindow.INFO_UNKNOWN_VALUE));
            databaseValueLabel.setText(asHtml(UiText.LibraryWindow.INFO_UNKNOWN_VALUE));
            return;
        }

        publisherValueLabel.setText(asHtml(valueOrUnknown(metadata.publisher())));
        releaseYearValueLabel.setText(asHtml(valueOrUnknown(metadata.releaseYear())));
        databaseValueLabel.setText(asHtml(valueOrUnknown(metadata.databaseName())));
    }

    private void requestArtwork() {
        if (entry == null) {
            return;
        }

        getArtworkButton.setEnabled(false);
        getArtworkButton.setText(UiText.MainWindow.FETCHING_ARTWORK);

        CompletableFuture
                .supplyAsync(() -> GameArtProvider.FindGameArt(entry.ArtDescriptor()))
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    getArtworkButton.setEnabled(true);
                    getArtworkButton.setText(UiText.LibraryWindow.INFO_GET_ARTWORK_BUTTON);

                    if (result.isPresent()) {
                        GameArtProvider.GameArtResult gameArtResult = result.get();
                        if (gameArtResult.matchedGameName() != null && !gameArtResult.matchedGameName().isBlank()) {
                            GameMetadataStore.RememberLibretroTitle(entry.SaveIdentity(), gameArtResult.matchedGameName());
                        }
                        if (artworkUpdatedAction != null) {
                            artworkUpdatedAction.run();
                        }
                        JOptionPane.showMessageDialog(this,
                                UiText.OptionsWindow.InfoArtworkFetchedMessage(resolveDisplayName()));
                        return;
                    }

                    JOptionPane.showMessageDialog(this,
                            UiText.OptionsWindow.InfoArtworkMissingMessage(resolveDisplayName()),
                            UiText.LibraryWindow.INFO_ARTWORK_FETCH_FAILED_TITLE,
                            JOptionPane.INFORMATION_MESSAGE);
                }))
                .exceptionally(exception -> {
                    SwingUtilities.invokeLater(() -> {
                        getArtworkButton.setEnabled(true);
                        getArtworkButton.setText(UiText.LibraryWindow.INFO_GET_ARTWORK_BUTTON);
                        JOptionPane.showMessageDialog(this,
                                exception.getMessage() == null ? UiText.OptionsWindow.InfoArtworkMissingMessage(resolveDisplayName())
                                        : exception.getMessage(),
                                UiText.LibraryWindow.INFO_ARTWORK_FETCH_FAILED_TITLE,
                                JOptionPane.ERROR_MESSAGE);
                    });
                    return null;
                });
    }

    private void deleteRom() {
        if (entry == null) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                UiText.OptionsWindow.InfoDeleteRomConfirmMessage(resolveDisplayName()),
                UiText.LibraryWindow.INFO_DELETE_CONFIRM_TITLE,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            GameLibraryStore.DeleteEntry(entry.key());
            if (entryDeletedAction != null) {
                entryDeletedAction.run();
            }
            JOptionPane.showMessageDialog(this, UiText.OptionsWindow.InfoDeleteRomSuccessMessage(resolveDisplayName()));
            dispose();
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), UiText.LibraryWindow.INFO_DELETE_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openInExplorer(Path path) {
        if (path == null) {
            return;
        }

        try {
            String absolutePath = path.toAbsolutePath().toString();
            new ProcessBuilder("explorer.exe", "/select,", absolutePath).start();
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), UiText.LibraryWindow.INFO_EXPLORER_ERROR_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private String resolveDisplayName() {
        if (entry == null) {
            return UiText.LibraryWindow.INFO_NO_SELECTION;
        }
        String baseName = GameMetadataStore.GetLibretroTitle(entry.SaveIdentity())
                .orElseGet(() -> entry.sourceName() != null && !entry.sourceName().isBlank()
                        ? entry.sourceName()
                        : entry.displayName());
        return MainWindow.ApplyGameNameDisplayMode(baseName == null || baseName.isBlank()
                ? UiText.LibraryWindow.INFO_NO_SELECTION
                : baseName);
    }

    private String mapperDisplayName() {
        if (rom == null || rom.GetMapperType() == null) {
            return UiText.LibraryWindow.INFO_UNKNOWN_VALUE;
        }
        return rom.GetMapperType().name().replace('_', ' ');
    }

    private String buildRomSizeSummary() {
        if (rom == null) {
            return UiText.LibraryWindow.INFO_UNKNOWN_VALUE;
        }
        return UiText.LibraryWindow.InfoRomSizeSummary(
                formatByteSize(rom.ToByteArray().length),
                rom.GetEffectiveRomBankCount());
    }

    private String formatLastPlayed(long lastPlayedMillis) {
        if (lastPlayedMillis <= 0L) {
            return UiText.LibraryWindow.INFO_UNKNOWN_VALUE;
        }
        return lastPlayedFormatter.format(Instant.ofEpochMilli(lastPlayedMillis).atZone(ZoneId.systemDefault()));
    }

    private String formatByteSize(long bytes) {
        return UiText.LibraryWindow.InfoByteSize(bytes);
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? UiText.LibraryWindow.INFO_UNKNOWN_VALUE : value;
    }

    private String asHtml(String value) {
        String resolvedValue = value == null || value.isBlank()
                ? UiText.LibraryWindow.INFO_UNKNOWN_VALUE
                : value;
        return "<html><body style='width: 320px'>" + escapeHtml(resolvedValue) + "</body></html>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        stylePrimaryButton(button);
        return button;
    }

    private void stylePrimaryButton(JButton button) {
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

    private void styleSecondaryButton(JButton button) {
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
    }

    private javax.swing.border.Border createCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardBorder, 1),
                BorderFactory.createEmptyBorder(18, 18, 18, 18));
    }
}
