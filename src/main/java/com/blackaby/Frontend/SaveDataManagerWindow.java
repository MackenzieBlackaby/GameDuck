package com.blackaby.Frontend;

import com.blackaby.Backend.Helpers.GameArtProvider;
import com.blackaby.Backend.Helpers.GameMetadataStore;
import com.blackaby.Backend.Helpers.ManagedGameRegistry;
import com.blackaby.Backend.Helpers.ManagedGameRegistry.StoredGame;
import com.blackaby.Backend.Helpers.SaveFileManager;
import com.blackaby.Backend.Platform.EmulatorRuntime;
import com.blackaby.Misc.RomConsoleFilter;
import com.blackaby.Misc.UiText;
import com.blackaby.Misc.RomDataFilter;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dedicated save manager that lists every tracked battery-backed game.
 */
public final class SaveDataManagerWindow extends AbstractSaveManagerWindow<StoredGame> {

    private static final DateTimeFormatter saveTimestampFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final JLabel managedPathValueLabel = new JLabel();
    private final JLabel saveSizesValueLabel = new JLabel();
    private final JLabel saveFilesValueLabel = new JLabel();
    private final JLabel detailArtLabel = createArtLabel();
    private final JLabel detailGameNameLabel = new JLabel(UiText.OptionsWindow.SAVE_MANAGER_EMPTY_TITLE);

    private JButton importButton;
    private JButton exportButton;
    private JButton deleteButton;

    public SaveDataManagerWindow(MainWindow mainWindow) {
        super(UiText.OptionsWindow.SAVE_MANAGER_WINDOW_TITLE, 980, 700, mainWindow);
        initialiseWindow(UiText.OptionsWindow.SAVE_MANAGER_WINDOW_TITLE,
                UiText.OptionsWindow.SAVE_MANAGER_SUBTITLE,
                buildBody());
    }

    @Override
    protected List<JComponent> createHeaderActions() {
        return List.of();
    }

    @Override
    protected String trackedGamesBadgeText(int totalCount) {
        return UiText.OptionsWindow.SaveManagerTrackedGamesBadge(totalCount);
    }

    @Override
    protected List<StoredGame> loadEntries() {
        List<StoredGame> allGames = ManagedGameRegistry.GetKnownGames();
        if (dataFilter == RomDataFilter.HAS_DATA) {
            return allGames.stream()
                    .filter(game -> SaveFileManager.DescribeSaveFiles(game.saveIdentity()).HasExistingFiles())
                    .toList();
        }
        return ManagedGameRegistry.GetKnownGames();
    }

    @Override
    protected String entryKey(StoredGame entry) {
        return entry == null ? null : entry.key();
    }

    @Override
    protected boolean matchesConsoleFilter(StoredGame game, RomConsoleFilter filter) {
        return game != null && filter.Matches(game.cgbCompatible());
    }

    @Override
    protected boolean matchesSearchQuery(StoredGame game, String query) {
        if (game == null || query.isBlank()) {
            return game != null;
        }

        SaveFileManager.SaveIdentity saveIdentity = game.saveIdentity();
        return containsIgnoreCase(query,
                resolveGameDisplayName(saveIdentity),
                saveIdentity == null ? null : saveIdentity.sourceName(),
                saveIdentity == null ? null : saveIdentity.displayName(),
                game.headerTitle(),
                saveIdentity == null ? null : String.join(" ", saveIdentity.patchNames()));
    }

    @Override
    protected GameArtProvider.GameArtDescriptor artDescriptor(StoredGame game) {
        if (game == null) {
            return null;
        }

        SaveFileManager.SaveIdentity saveIdentity = game.saveIdentity();
        return new GameArtProvider.GameArtDescriptor(
                saveIdentity.sourcePath(),
                saveIdentity.sourceName(),
                saveIdentity.displayName(),
                game.headerTitle());
    }

    @Override
    protected void rememberMatchedGameName(StoredGame game, String matchedGameName) {
        if (game != null && matchedGameName != null && !matchedGameName.isBlank()) {
            GameMetadataStore.RememberLibretroTitle(game.saveIdentity(), matchedGameName);
        }
    }

    @Override
    protected void updateSelectionDetails(StoredGame game) {
        if (game == null) {
            detailGameNameLabel.setText(asTitleHtml(currentEmptyTitle(), 280));
            managedPathValueLabel.setText(asHtml(currentEmptyHelper(), 240, UiText.LibraryWindow.EMPTY));
            saveSizesValueLabel
                    .setText(asHtml(UiText.OptionsWindow.SAVE_DETAILS_NONE, 240, UiText.LibraryWindow.EMPTY));
            saveFilesValueLabel
                    .setText(asHtml(UiText.OptionsWindow.SAVE_DETAILS_NONE, 240, UiText.LibraryWindow.EMPTY));
            updateArtLabel(detailArtLabel, null, DETAIL_ART_WIDTH - 16, DETAIL_ART_HEIGHT - 16, 12f);
            setActionButtonsEnabled(false, false);
            return;
        }

        SaveFileManager.SaveIdentity saveIdentity = game.saveIdentity();
        SaveFileManager.SaveFileSummary saveSummary = SaveFileManager.DescribeSaveFiles(saveIdentity);
        boolean liveSession = isLiveSession(game);
        long liveSizeBytes = liveSession ? currentEmulation().SnapshotSaveData().length : -1L;

        detailGameNameLabel.setText(asTitleHtml(resolveGameDisplayName(saveIdentity), 280));
        managedPathValueLabel.setText(
                asHtml(SaveFileManager.PreferredSavePath(saveIdentity).toString(), 240, UiText.LibraryWindow.EMPTY));
        saveSizesValueLabel.setText(asHtml(liveSession
                ? UiText.OptionsWindow.SaveManagerLiveSizeSummary(
                        UiText.OptionsWindow.FormatByteSize(liveSizeBytes),
                        UiText.OptionsWindow.FormatByteSize(game.expectedSaveSizeBytes()))
                : UiText.OptionsWindow.FormatByteSize(game.expectedSaveSizeBytes()), 240, UiText.LibraryWindow.EMPTY));
        saveFilesValueLabel.setText(asHtml(buildSaveFilesText(saveSummary), 240, UiText.LibraryWindow.EMPTY));
        updateArtLabel(detailArtLabel, game, DETAIL_ART_WIDTH - 16, DETAIL_ART_HEIGHT - 16, 12f);

        setActionButtonsEnabled(true, liveSession || saveSummary.HasExistingFiles());
    }

    private JComponent buildBody() {
        JPanel wrapper = new JPanel(new BorderLayout(16, 0));
        wrapper.setBackground(panelBackground);
        wrapper.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 20, 20, 20));

        JPanel detailsCard = new JPanel(new BorderLayout(0, 14));
        detailsCard.setBackground(cardBackground);
        detailsCard.setBorder(createCardBorder());
        detailsCard.setPreferredSize(new Dimension(330, 0));

        detailGameNameLabel.setFont(Styling.menuFont.deriveFont(java.awt.Font.BOLD, 20f));
        detailGameNameLabel.setForeground(accentColour);

        JPanel detailsHeader = new JPanel(new BorderLayout(0, 10));
        detailsHeader.setOpaque(false);
        detailsHeader.add(detailGameNameLabel, BorderLayout.NORTH);
        detailsHeader.add(createArtPanel(detailArtLabel, DETAIL_ART_WIDTH, DETAIL_ART_HEIGHT), BorderLayout.CENTER);

        managedPathValueLabel.setVerticalAlignment(SwingConstants.TOP);
        saveSizesValueLabel.setVerticalAlignment(SwingConstants.TOP);
        saveFilesValueLabel.setVerticalAlignment(SwingConstants.TOP);

        JPanel detailGrid = new JPanel(new java.awt.GridLayout(3, 1, 0, 10));
        detailGrid.setOpaque(false);
        detailGrid.add(createDetailRow(UiText.OptionsWindow.SAVE_DATA_MANAGED_PATH_TITLE, managedPathValueLabel));
        detailGrid.add(createDetailRow(UiText.OptionsWindow.SAVE_MANAGER_SIZE_SUMMARY_TITLE, saveSizesValueLabel));
        detailGrid.add(createDetailRow(UiText.OptionsWindow.SAVE_DATA_EXISTING_FILES_TITLE, saveFilesValueLabel));

        JPanel buttonPanel = new JPanel(new java.awt.GridLayout(1, 3, 10, 0));
        buttonPanel.setOpaque(false);

        importButton = createPrimaryButton(UiText.OptionsWindow.SAVE_DATA_IMPORT_BUTTON);
        importButton.addActionListener(event -> importSaveData(selectedEntry()));
        exportButton = createPrimaryButton(UiText.OptionsWindow.SAVE_DATA_EXPORT_BUTTON);
        exportButton.addActionListener(event -> exportSaveData(selectedEntry()));
        deleteButton = createPrimaryButton(UiText.OptionsWindow.SAVE_DATA_DELETE_BUTTON);
        deleteButton.addActionListener(event -> deleteSaveData(selectedEntry()));

        buttonPanel.add(importButton);
        buttonPanel.add(exportButton);
        buttonPanel.add(deleteButton);

        JPanel actionsSection = new JPanel(new BorderLayout(0, 8));
        actionsSection.setOpaque(false);
        actionsSection.add(createSectionHeader(
                UiText.OptionsWindow.SAVE_MANAGER_ACTIONS_TITLE,
                UiText.OptionsWindow.SAVE_MANAGER_ACTIONS_HELPER), BorderLayout.NORTH);
        actionsSection.add(buttonPanel, BorderLayout.SOUTH);

        detailsCard.add(detailsHeader, BorderLayout.NORTH);
        detailsCard.add(detailGrid, BorderLayout.CENTER);
        detailsCard.add(actionsSection, BorderLayout.SOUTH);

        wrapper.add(createGameListCard(new SaveEntryRenderer(), 0), BorderLayout.CENTER);
        wrapper.add(detailsCard, BorderLayout.EAST);
        return wrapper;
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

    private void importSaveData(StoredGame game) {
        if (game == null) {
            return;
        }

        File importFile = chooseLoadFile(UiText.OptionsWindow.SAVE_IMPORT_DIALOG_TITLE, ".sav");
        if (importFile == null) {
            return;
        }

        try {
            int importedBytes = isLiveSession(game)
                    ? currentEmulation().ImportSaveData(importFile.toPath())
                    : SaveFileManager.ImportSave(game.saveIdentity(), importFile.toPath()).length;
            JOptionPane.showMessageDialog(this,
                    UiText.OptionsWindow.SaveImportSuccessMessage(resolveGameDisplayName(game.saveIdentity()),
                            importedBytes));
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

        Path defaultPath = SaveFileManager.PreferredSavePath(game.saveIdentity());
        File exportFile = chooseSaveFile(UiText.OptionsWindow.SAVE_EXPORT_DIALOG_TITLE, defaultPath, ".sav");
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
        return mainWindow != null
                && mainWindow.GetCurrentLoadedRom() != null
                && game != null
                && game.key().equals(ManagedGameRegistry.BuildGameKey(mainWindow.GetCurrentLoadedRom()));
    }

    private boolean isLiveSession(StoredGame game) {
        EmulatorRuntime emulation = currentEmulation();
        return isCurrentGame(game) && emulation != null && emulation.CanManageSaveData();
    }

    private EmulatorRuntime currentEmulation() {
        return mainWindow == null ? null : mainWindow.GetEmulation();
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

        return saveSummary.existingFiles().stream()
                .map(entry -> UiText.OptionsWindow.SaveFileEntrySummary(
                        entry.label(),
                        entry.sizeBytes(),
                        formatSaveTimestamp(entry.lastModified())))
                .collect(Collectors.joining(" | "));
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

    private String buildListHelperText(StoredGame game) {
        SaveFileManager.SaveFileSummary saveSummary = SaveFileManager.DescribeSaveFiles(game.saveIdentity());
        if (isLiveSession(game)) {
            return UiText.OptionsWindow.SAVE_MANAGER_CURRENT_GAME_BADGE;
        }
        return saveSummary.HasExistingFiles()
                ? UiText.OptionsWindow.SAVE_DATA_READY_BADGE
                : UiText.OptionsWindow.SAVE_DATA_EMPTY_BADGE;
    }

    private String formatSaveTimestamp(FileTime lastModified) {
        if (lastModified == null || lastModified.toMillis() <= 0L) {
            return UiText.OptionsWindow.SAVE_DETAILS_UNKNOWN_TIME;
        }
        return saveTimestampFormatter.format(lastModified.toInstant().atZone(ZoneId.systemDefault()));
    }

    private final class SaveEntryRenderer extends ArtListRenderer {
        @Override
        protected boolean truncateText() {
            return true;
        }

        @Override
        protected String titleText(StoredGame value) {
            return resolveGameDisplayName(value.saveIdentity());
        }

        @Override
        protected String helperText(StoredGame value) {
            String helperText = buildListHelperText(value);
            return value.saveIdentity().patchNames().isEmpty()
                    ? helperText
                    : helperText + " | " + UiText.LibraryWindow.VariantLabel(value.saveIdentity().patchNames());
        }
    }
}
