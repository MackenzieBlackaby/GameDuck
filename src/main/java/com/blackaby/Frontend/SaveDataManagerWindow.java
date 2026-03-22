package com.blackaby.Frontend;

import com.blackaby.Backend.Emulation.DuckEmulation;
import com.blackaby.Backend.Helpers.GameArtProvider;
import com.blackaby.Backend.Helpers.GameMetadataStore;
import com.blackaby.Backend.Helpers.ManagedGameRegistry;
import com.blackaby.Backend.Helpers.ManagedGameRegistry.StoredGame;
import com.blackaby.Backend.Helpers.SaveFileManager;
import com.blackaby.Misc.RomConsoleFilter;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dedicated save manager that lists every tracked battery-backed game.
 */
public final class SaveDataManagerWindow extends DuckWindow {

    private static final int listArtSize = 56;
    private static final int detailArtWidth = 176;
    private static final int detailArtHeight = 176;
    private static final DateTimeFormatter saveTimestampFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final MainWindow mainWindow;
    private final Color panelBackground;
    private final Color cardBackground;
    private final Color cardBorder;
    private final Color accentColour;
    private final Color mutedText;
    private final DefaultListModel<StoredGame> gameListModel = new DefaultListModel<>();
    private final Map<String, BufferedImage> artCache = new ConcurrentHashMap<>();
    private final Set<String> artLoadingKeys = ConcurrentHashMap.newKeySet();
    private final JLabel managedPathValueLabel = new JLabel();
    private final JLabel saveSizesValueLabel = new JLabel();
    private final JLabel saveFilesValueLabel = new JLabel();
    private final JLabel detailArtLabel = createArtLabel();
    private final JLabel detailGameNameLabel = new JLabel(UiText.OptionsWindow.SAVE_MANAGER_EMPTY_TITLE);

    private JLabel trackedGamesBadgeLabel;
    private JList<StoredGame> gameList;
    private JScrollPane listScrollPane;
    private JButton importButton;
    private JButton exportButton;
    private JButton deleteButton;
    private RomConsoleFilter consoleFilter = RomConsoleFilter.ALL;
    private String searchQuery = "";

    public SaveDataManagerWindow(MainWindow mainWindow) {
        super(UiText.OptionsWindow.SAVE_MANAGER_WINDOW_TITLE, 980, 700, true);
        this.mainWindow = mainWindow;
        panelBackground = Styling.appBackgroundColour;
        cardBackground = Styling.surfaceColour;
        cardBorder = Styling.surfaceBorderColour;
        accentColour = Styling.accentColour;
        mutedText = Styling.mutedTextColour;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(panelBackground);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);

        refreshGameList();
        setVisible(true);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 10));
        header.setBackground(panelBackground);
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));

        JPanel titleStack = new JPanel(new BorderLayout(0, 6));
        titleStack.setOpaque(false);

        JLabel titleLabel = new JLabel(UiText.OptionsWindow.SAVE_MANAGER_WINDOW_TITLE);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(accentColour);

        JLabel subtitleLabel = new JLabel(UiText.OptionsWindow.SAVE_MANAGER_SUBTITLE);
        subtitleLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        subtitleLabel.setForeground(mutedText);

        titleStack.add(titleLabel, BorderLayout.NORTH);
        titleStack.add(subtitleLabel, BorderLayout.CENTER);

        JPanel topRow = new JPanel(new BorderLayout(12, 0));
        topRow.setOpaque(false);

        JPanel actionStack = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionStack.setOpaque(false);

        trackedGamesBadgeLabel = createBadgeLabel(UiText.OptionsWindow.SaveManagerTrackedGamesBadge(0));

        JButton saveStateManagerButton = createSecondaryButton(UiText.OptionsWindow.SAVE_STATE_MANAGER_OPEN_BUTTON);
        saveStateManagerButton.addActionListener(event -> new SaveStateManagerWindow(mainWindow));

        JButton refreshButton = createSecondaryButton(UiText.OptionsWindow.SAVE_MANAGER_REFRESH_BUTTON);
        refreshButton.addActionListener(event -> refreshGameList());

        actionStack.add(trackedGamesBadgeLabel);
        actionStack.add(saveStateManagerButton);
        actionStack.add(refreshButton);

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterRow.setOpaque(false);

        JLabel consoleLabel = new JLabel(UiText.Common.CONSOLE_TITLE);
        consoleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        consoleLabel.setForeground(accentColour);
        filterRow.add(consoleLabel);

        javax.swing.JComboBox<RomConsoleFilter> consoleSelector = new javax.swing.JComboBox<>(RomConsoleFilter.values());
        consoleSelector.setSelectedItem(consoleFilter);
        consoleSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        consoleSelector.addActionListener(event -> {
            Object selectedItem = consoleSelector.getSelectedItem();
            if (selectedItem instanceof RomConsoleFilter selectedConsoleFilter) {
                applyConsoleFilter(selectedConsoleFilter);
            }
        });
        filterRow.add(consoleSelector);

        JLabel searchLabel = new JLabel(UiText.Common.SEARCH_TITLE);
        searchLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        searchLabel.setForeground(accentColour);
        filterRow.add(searchLabel);

        JTextField searchField = new JTextField(searchQuery, 16);
        searchField.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                applySearchQuery(searchField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                applySearchQuery(searchField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                applySearchQuery(searchField.getText());
            }
        });
        filterRow.add(searchField);

        topRow.add(titleStack, BorderLayout.CENTER);
        topRow.add(actionStack, BorderLayout.EAST);

        header.add(topRow, BorderLayout.NORTH);
        header.add(filterRow, BorderLayout.SOUTH);
        return header;
    }

    private JComponent buildBody() {
        JPanel wrapper = new JPanel(new BorderLayout(16, 0));
        wrapper.setBackground(panelBackground);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));

        JPanel listCard = new JPanel(new BorderLayout(0, 12));
        listCard.setBackground(cardBackground);
        listCard.setBorder(createCardBorder());

        gameList = new JList<>(gameListModel);
        gameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        gameList.setBackground(Styling.cardTintColour);
        gameList.setSelectionBackground(Styling.listSelectionColour);
        gameList.setSelectionForeground(accentColour);
        gameList.setFixedCellHeight(80);
        gameList.setCellRenderer(new SaveEntryRenderer());
        gameList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateSelectionDetails(gameList.getSelectedValue());
            }
        });

        listScrollPane = new JScrollPane(gameList);
        listScrollPane.setBorder(BorderFactory.createLineBorder(cardBorder, 1));
        listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listCard.add(listScrollPane, BorderLayout.CENTER);

        JPanel detailsCard = new JPanel(new BorderLayout(0, 14));
        detailsCard.setBackground(cardBackground);
        detailsCard.setBorder(createCardBorder());
        detailsCard.setPreferredSize(new Dimension(330, 0));

        detailGameNameLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 20f));
        detailGameNameLabel.setForeground(accentColour);

        JPanel detailsHeader = new JPanel(new BorderLayout(0, 10));
        detailsHeader.setOpaque(false);
        detailsHeader.add(detailGameNameLabel, BorderLayout.NORTH);

        JPanel artPanel = new JPanel(new BorderLayout());
        artPanel.setOpaque(true);
        artPanel.setBackground(Styling.displayFrameColour);
        artPanel.setBorder(BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1));
        artPanel.setPreferredSize(new Dimension(detailArtWidth, detailArtHeight));
        artPanel.add(detailArtLabel, BorderLayout.CENTER);
        detailsHeader.add(artPanel, BorderLayout.CENTER);

        managedPathValueLabel.setVerticalAlignment(SwingConstants.TOP);
        saveSizesValueLabel.setVerticalAlignment(SwingConstants.TOP);
        saveFilesValueLabel.setVerticalAlignment(SwingConstants.TOP);

        JPanel detailGrid = new JPanel(new java.awt.GridLayout(3, 1, 0, 10));
        detailGrid.setOpaque(false);
        detailGrid.add(createDetailRow(UiText.OptionsWindow.SAVE_DATA_MANAGED_PATH_TITLE, managedPathValueLabel));
        detailGrid.add(createDetailRow(UiText.OptionsWindow.SAVE_MANAGER_SIZE_SUMMARY_TITLE, saveSizesValueLabel));
        detailGrid.add(createDetailRow(UiText.OptionsWindow.SAVE_DATA_EXISTING_FILES_TITLE, saveFilesValueLabel));

        JPanel actionsSection = new JPanel(new BorderLayout(0, 8));
        actionsSection.setOpaque(false);

        JLabel actionsTitleLabel = new JLabel(UiText.OptionsWindow.SAVE_MANAGER_ACTIONS_TITLE);
        actionsTitleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        actionsTitleLabel.setForeground(accentColour);

        JLabel actionsHelperLabel = new JLabel(UiText.OptionsWindow.SAVE_MANAGER_ACTIONS_HELPER);
        actionsHelperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        actionsHelperLabel.setForeground(mutedText);

        JPanel buttonPanel = new JPanel(new java.awt.GridLayout(1, 3, 10, 0));
        buttonPanel.setOpaque(false);

        importButton = createPrimaryButton(UiText.OptionsWindow.SAVE_DATA_IMPORT_BUTTON);
        importButton.addActionListener(event -> importSaveData(gameList.getSelectedValue()));

        exportButton = createPrimaryButton(UiText.OptionsWindow.SAVE_DATA_EXPORT_BUTTON);
        exportButton.addActionListener(event -> exportSaveData(gameList.getSelectedValue()));

        deleteButton = createPrimaryButton(UiText.OptionsWindow.SAVE_DATA_DELETE_BUTTON);
        deleteButton.addActionListener(event -> deleteSaveData(gameList.getSelectedValue()));

        buttonPanel.add(importButton);
        buttonPanel.add(exportButton);
        buttonPanel.add(deleteButton);

        actionsSection.add(actionsTitleLabel, BorderLayout.NORTH);
        actionsSection.add(actionsHelperLabel, BorderLayout.CENTER);
        actionsSection.add(buttonPanel, BorderLayout.SOUTH);

        detailsCard.add(detailsHeader, BorderLayout.NORTH);
        detailsCard.add(detailGrid, BorderLayout.CENTER);
        detailsCard.add(actionsSection, BorderLayout.SOUTH);

        wrapper.add(listCard, BorderLayout.CENTER);
        wrapper.add(detailsCard, BorderLayout.EAST);
        return wrapper;
    }

    private JPanel createDetailRow(String title, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(accentColour);

        valueLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        valueLabel.setForeground(mutedText);

        row.add(titleLabel, BorderLayout.NORTH);
        row.add(valueLabel, BorderLayout.CENTER);
        return row;
    }

    private void refreshGameList() {
        StoredGame selectedGame = gameList == null ? null : gameList.getSelectedValue();
        gameListModel.clear();

        List<StoredGame> allGames = ManagedGameRegistry.GetKnownGames();
        if (trackedGamesBadgeLabel != null) {
            trackedGamesBadgeLabel.setText(UiText.OptionsWindow.SaveManagerTrackedGamesBadge(allGames.size()));
        }

        List<StoredGame> games = allGames.stream()
                .filter(this::matchesConsoleFilter)
                .filter(this::matchesSearchQuery)
                .toList();

        for (StoredGame game : games) {
            gameListModel.addElement(game);
            requestArt(game);
        }

        if (gameListModel.isEmpty()) {
            updateSelectionDetails(null);
            if (gameList != null) {
                gameList.clearSelection();
                gameList.setEnabled(false);
            }
            return;
        }

        if (gameList != null) {
            gameList.setEnabled(true);
        }

        int selectedIndex = 0;
        if (selectedGame != null) {
            for (int index = 0; index < gameListModel.size(); index++) {
                if (gameListModel.get(index).key().equals(selectedGame.key())) {
                    selectedIndex = index;
                    break;
                }
            }
        }

        if (gameList != null) {
            gameList.setSelectedIndex(selectedIndex);
            gameList.ensureIndexIsVisible(selectedIndex);
        } else {
            updateSelectionDetails(gameListModel.getElementAt(selectedIndex));
        }
    }

    private void updateSelectionDetails(StoredGame game) {
        if (game == null) {
            detailGameNameLabel.setText(currentEmptyTitle());
            managedPathValueLabel.setText(asHtml(currentEmptyHelper()));
            saveSizesValueLabel.setText(asHtml(UiText.OptionsWindow.SAVE_DETAILS_NONE));
            saveFilesValueLabel.setText(asHtml(UiText.OptionsWindow.SAVE_DETAILS_NONE));
            detailArtLabel.setIcon(null);
            detailArtLabel.setText(UiText.LibraryWindow.ART_MISSING);
            detailArtLabel.setForeground(mutedText);
            setActionButtonsEnabled(false, false);
            return;
        }

        SaveFileManager.SaveIdentity saveIdentity = game.saveIdentity();
        SaveFileManager.SaveFileSummary saveSummary = SaveFileManager.DescribeSaveFiles(saveIdentity);
        boolean liveSession = isLiveSession(game);
        long liveSizeBytes = liveSession ? currentEmulation().SnapshotSaveData().length : -1L;

        detailGameNameLabel.setText(resolveGameDisplayName(saveIdentity));
        managedPathValueLabel.setText(asHtml(SaveFileManager.PreferredSavePath(saveIdentity).toString()));
        saveSizesValueLabel.setText(asHtml(liveSession
                ? UiText.OptionsWindow.SaveManagerLiveSizeSummary(
                        UiText.OptionsWindow.FormatByteSize(liveSizeBytes),
                        UiText.OptionsWindow.FormatByteSize(game.expectedSaveSizeBytes()))
                : UiText.OptionsWindow.FormatByteSize(game.expectedSaveSizeBytes())));
        saveFilesValueLabel.setText(asHtml(buildSaveFilesText(saveSummary)));

        BufferedImage art = artCache.get(game.key());
        if (art != null) {
            BufferedImage scaled = GameArtScaler.ScaleToFit(art, detailArtWidth - 16, detailArtHeight - 16, true);
            detailArtLabel.setIcon(scaled == null ? null : new ImageIcon(scaled));
            detailArtLabel.setText("");
            detailArtLabel.setForeground(Styling.fpsForegroundColour);
        } else {
            detailArtLabel.setIcon(null);
            detailArtLabel.setText(artLoadingKeys.contains(game.key())
                    ? UiText.LibraryWindow.ART_LOADING
                    : UiText.LibraryWindow.ART_MISSING);
            detailArtLabel.setForeground(mutedText);
        }

        setActionButtonsEnabled(true, liveSession || saveSummary.HasExistingFiles());
    }

    private void setActionButtonsEnabled(boolean allowImport, boolean allowExistingSaveActions) {
        if (importButton != null) {
            importButton.setEnabled(allowImport);
        }
        if (exportButton != null) {
            exportButton.setEnabled(allowExistingSaveActions);
        }
        if (deleteButton != null) {
            deleteButton.setEnabled(allowExistingSaveActions);
        }
    }

    private JLabel createArtLabel() {
        JLabel label = new JLabel(UiText.LibraryWindow.ART_LOADING, SwingConstants.CENTER);
        label.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        label.setForeground(mutedText);
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        return label;
    }

    private void requestArt(StoredGame game) {
        if (game == null || artCache.containsKey(game.key()) || !artLoadingKeys.add(game.key())) {
            return;
        }

        SaveFileManager.SaveIdentity saveIdentity = game.saveIdentity();
        GameArtProvider.GameArtDescriptor descriptor = new GameArtProvider.GameArtDescriptor(
                saveIdentity.sourcePath(),
                saveIdentity.sourceName(),
                saveIdentity.displayName(),
                game.headerTitle());

        CompletableFuture
                .supplyAsync(() -> GameArtProvider.FindGameArt(descriptor))
                .thenAccept(result -> {
                    result.ifPresent(gameArtResult -> {
                        if (gameArtResult.matchedGameName() != null && !gameArtResult.matchedGameName().isBlank()) {
                            GameMetadataStore.RememberLibretroTitle(saveIdentity, gameArtResult.matchedGameName());
                        }
                        artCache.put(game.key(), gameArtResult.image());
                    });
                    artLoadingKeys.remove(game.key());
                    SwingUtilities.invokeLater(() -> {
                        if (gameList != null) {
                            gameList.repaint();
                            if (game.equals(gameList.getSelectedValue())) {
                                updateSelectionDetails(game);
                            }
                        }
                    });
                })
                .exceptionally(exception -> {
                    artLoadingKeys.remove(game.key());
                    SwingUtilities.invokeLater(() -> {
                        if (gameList != null) {
                            gameList.repaint();
                            if (game.equals(gameList.getSelectedValue())) {
                                updateSelectionDetails(game);
                            }
                        }
                    });
                    return null;
                });
    }

    private void importSaveData(StoredGame game) {
        if (game == null) {
            return;
        }

        File importFile = promptForSaveImportFile();
        if (importFile == null) {
            return;
        }

        try {
            int importedBytes;
            if (isLiveSession(game)) {
                importedBytes = currentEmulation().ImportSaveData(importFile.toPath());
            } else {
                importedBytes = SaveFileManager.ImportSave(game.saveIdentity(), importFile.toPath()).length;
            }

            JOptionPane.showMessageDialog(this,
                    UiText.OptionsWindow.SaveImportSuccessMessage(resolveGameDisplayName(game.saveIdentity()), importedBytes));
            refreshGameList();
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), UiText.OptionsWindow.SAVE_IMPORT_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportSaveData(StoredGame game) {
        if (game == null) {
            return;
        }

        File exportFile = promptForSaveExportFile(game.saveIdentity());
        if (exportFile == null) {
            return;
        }

        try {
            if (isLiveSession(game)) {
                currentEmulation().ExportSaveData(exportFile.toPath());
            } else {
                SaveFileManager.ExportSave(game.saveIdentity(), exportFile.toPath());
            }

            JOptionPane.showMessageDialog(this,
                    UiText.OptionsWindow.SaveExportSuccessMessage(exportFile.getAbsolutePath()));
            refreshGameList();
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), UiText.OptionsWindow.SAVE_EXPORT_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSaveData(StoredGame game) {
        if (game == null) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                UiText.OptionsWindow.SaveDeleteConfirmMessage(resolveGameDisplayName(game.saveIdentity())),
                UiText.OptionsWindow.SAVE_DELETE_CONFIRM_TITLE,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            if (isLiveSession(game)) {
                currentEmulation().DeleteSaveData();
            } else {
                SaveFileManager.DeleteSave(game.saveIdentity());
            }

            JOptionPane.showMessageDialog(this,
                    UiText.OptionsWindow.SaveDeletedMessage(resolveGameDisplayName(game.saveIdentity())));
            refreshGameList();
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), UiText.OptionsWindow.SAVE_DELETE_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean isCurrentGame(StoredGame game) {
        if (mainWindow == null || mainWindow.GetCurrentLoadedRom() == null || game == null) {
            return false;
        }
        return game.key().equals(ManagedGameRegistry.BuildGameKey(mainWindow.GetCurrentLoadedRom()));
    }

    private boolean isLiveSession(StoredGame game) {
        DuckEmulation emulation = currentEmulation();
        return isCurrentGame(game) && emulation != null && emulation.CanManageSaveData();
    }

    private DuckEmulation currentEmulation() {
        return mainWindow == null ? null : mainWindow.GetEmulation();
    }

    private void applyConsoleFilter(RomConsoleFilter nextConsoleFilter) {
        consoleFilter = nextConsoleFilter == null ? RomConsoleFilter.ALL : nextConsoleFilter;
        refreshGameList();
    }

    private void applySearchQuery(String nextSearchQuery) {
        searchQuery = nextSearchQuery == null ? "" : nextSearchQuery.trim();
        refreshGameList();
    }

    private boolean matchesConsoleFilter(StoredGame game) {
        return game != null && consoleFilter.Matches(game.cgbCompatible());
    }

    private boolean matchesSearchQuery(StoredGame game) {
        if (game == null || searchQuery.isBlank()) {
            return game != null;
        }

        SaveFileManager.SaveIdentity saveIdentity = game.saveIdentity();
        return containsIgnoreCase(searchQuery,
                resolveGameDisplayName(saveIdentity),
                saveIdentity == null ? null : saveIdentity.sourceName(),
                saveIdentity == null ? null : saveIdentity.displayName(),
                game.headerTitle(),
                saveIdentity == null ? null : String.join(" ", saveIdentity.patchNames()));
    }

    private String resolveGameDisplayName(SaveFileManager.SaveIdentity saveIdentity) {
        String baseName = GameMetadataStore.GetLibretroTitle(saveIdentity)
                .orElseGet(() -> {
                    String sourceName = saveIdentity.sourceName();
                    if (sourceName != null && !sourceName.isBlank()) {
                        return sourceName;
                    }
                    String displayName = saveIdentity.displayName();
                    return displayName == null || displayName.isBlank()
                            ? UiText.OptionsWindow.SAVE_DATA_NO_ROM_TITLE
                            : displayName;
                });
        return MainWindow.ApplyGameNameDisplayMode(baseName);
    }

    private String buildSaveFilesText(SaveFileManager.SaveFileSummary saveSummary) {
        if (saveSummary == null || !saveSummary.HasExistingFiles()) {
            return UiText.OptionsWindow.SAVE_DETAILS_NONE;
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < saveSummary.existingFiles().size(); index++) {
            SaveFileManager.SaveFileEntry entry = saveSummary.existingFiles().get(index);
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(UiText.OptionsWindow.SaveFileEntrySummary(
                    entry.label(),
                    entry.sizeBytes(),
                    formatSaveTimestamp(entry.lastModified())));
        }
        return builder.toString();
    }

    private String currentEmptyTitle() {
        return hasActiveFilters()
                ? UiText.OptionsWindow.SAVE_MANAGER_FILTERED_EMPTY_TITLE
                : UiText.OptionsWindow.SAVE_MANAGER_EMPTY_TITLE;
    }

    private String currentEmptyHelper() {
        return hasActiveFilters()
                ? UiText.OptionsWindow.SAVE_MANAGER_FILTERED_EMPTY_HELPER
                : UiText.OptionsWindow.SAVE_MANAGER_EMPTY_HELPER;
    }

    private boolean hasActiveFilters() {
        return !searchQuery.isBlank() || consoleFilter != RomConsoleFilter.ALL;
    }

    private String buildListHelperText(StoredGame game) {
        SaveFileManager.SaveFileSummary saveSummary = SaveFileManager.DescribeSaveFiles(game.saveIdentity());
        if (isLiveSession(game)) {
            return UiText.OptionsWindow.SAVE_MANAGER_CURRENT_GAME_BADGE;
        }
        if (saveSummary.HasExistingFiles()) {
            return UiText.OptionsWindow.SAVE_DATA_READY_BADGE;
        }
        return UiText.OptionsWindow.SAVE_DATA_EMPTY_BADGE;
    }

    private String formatSaveTimestamp(FileTime lastModified) {
        if (lastModified == null || lastModified.toMillis() <= 0L) {
            return UiText.OptionsWindow.SAVE_DETAILS_UNKNOWN_TIME;
        }
        return saveTimestampFormatter.format(lastModified.toInstant().atZone(ZoneId.systemDefault()));
    }

    private File promptForSaveImportFile() {
        FileDialog fileDialog = new FileDialog(this, UiText.OptionsWindow.SAVE_IMPORT_DIALOG_TITLE, FileDialog.LOAD);
        fileDialog.setAlwaysOnTop(true);
        fileDialog.setFilenameFilter((directory, name) -> name.toLowerCase().endsWith(".sav"));
        fileDialog.setVisible(true);
        return fileDialog.getFiles().length == 0 ? null : fileDialog.getFiles()[0];
    }

    private File promptForSaveExportFile(SaveFileManager.SaveIdentity saveIdentity) {
        FileDialog fileDialog = new FileDialog(this, UiText.OptionsWindow.SAVE_EXPORT_DIALOG_TITLE, FileDialog.SAVE);
        fileDialog.setAlwaysOnTop(true);

        Path defaultPath = saveIdentity == null
                ? SaveFileManager.SaveDirectoryPath().resolve("game.sav")
                : SaveFileManager.PreferredSavePath(saveIdentity);
        if (defaultPath.getParent() != null) {
            fileDialog.setDirectory(defaultPath.getParent().toString());
        }
        fileDialog.setFile(defaultPath.getFileName().toString());
        fileDialog.setVisible(true);

        if (fileDialog.getFiles().length == 0) {
            return null;
        }

        File selectedFile = fileDialog.getFiles()[0];
        if (selectedFile == null) {
            return null;
        }

        String selectedName = selectedFile.getName();
        if (!selectedName.toLowerCase().endsWith(".sav")) {
            selectedFile = new File(selectedFile.getParentFile(), selectedName + ".sav");
        }
        return selectedFile;
    }

    private String asHtml(String value) {
        if (value == null || value.isBlank()) {
            return "<html><body style='width: 240px'>" + UiText.LibraryWindow.EMPTY + "</body></html>";
        }
        return "<html><body style='width: 240px'>" + escapeHtml(value) + "</body></html>";
    }

    private boolean containsIgnoreCase(String query, String... candidates) {
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

    private JButton createPrimaryButton(String text) {
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

    private JButton createSecondaryButton(String text) {
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

    private JLabel createBadgeLabel(String text) {
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

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private javax.swing.border.Border createCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardBorder, 1),
                BorderFactory.createEmptyBorder(18, 18, 18, 18));
    }

    private final class SaveEntryRenderer extends JPanel implements ListCellRenderer<StoredGame> {
        private final JPanel artPanel = new JPanel(new BorderLayout());
        private final JLabel artLabel = createArtLabel();
        private final JLabel titleLabel = new JLabel();
        private final JLabel helperLabel = new JLabel();

        private SaveEntryRenderer() {
            setOpaque(true);
            artPanel.setOpaque(true);
            artPanel.setBackground(Styling.displayFrameColour);
            artPanel.setBorder(BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1));
            artPanel.add(artLabel, BorderLayout.CENTER);
            titleLabel.setForeground(accentColour);
            helperLabel.setForeground(mutedText);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends StoredGame> list, StoredGame value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            removeAll();

            Color background = isSelected ? Styling.listSelectionColour : Styling.cardTintColour;
            setBackground(background);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isSelected ? Styling.sectionHighlightBorderColour : cardBorder, 1, true),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)));

            BufferedImage art = artCache.get(value.key());
            if (art != null) {
                BufferedImage scaled = GameArtScaler.ScaleToFit(art, listArtSize, listArtSize, true);
                artLabel.setIcon(scaled == null ? null : new ImageIcon(scaled));
                artLabel.setText("");
                artLabel.setForeground(Styling.fpsForegroundColour);
            } else {
                artLabel.setIcon(null);
                artLabel.setText(artLoadingKeys.contains(value.key())
                        ? UiText.LibraryWindow.ART_LOADING
                        : UiText.LibraryWindow.ART_MISSING);
                artLabel.setForeground(mutedText);
                artLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 10f));
            }
            artPanel.setPreferredSize(new Dimension(listArtSize + 12, listArtSize + 12));

            titleLabel.setText(resolveGameDisplayName(value.saveIdentity()));
            titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));

            String helperText = buildListHelperText(value);
            if (!value.saveIdentity().patchNames().isEmpty()) {
                helperText = helperText + " | " + UiText.LibraryWindow.VariantLabel(value.saveIdentity().patchNames());
            }
            helperLabel.setText(helperText);
            helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 11f));

            JPanel textStack = new JPanel();
            textStack.setOpaque(false);
            textStack.setLayout(new javax.swing.BoxLayout(textStack, javax.swing.BoxLayout.Y_AXIS));
            textStack.add(titleLabel);
            textStack.add(helperLabel);

            setLayout(new BorderLayout(12, 0));
            add(artPanel, BorderLayout.WEST);
            add(textStack, BorderLayout.CENTER);
            return this;
        }
    }
}
