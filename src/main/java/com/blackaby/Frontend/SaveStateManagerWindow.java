package com.blackaby.Frontend;

import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.Helpers.GameArtProvider;
import com.blackaby.Backend.Helpers.GameLibraryStore;
import com.blackaby.Backend.Helpers.GameLibraryStore.LibraryEntry;
import com.blackaby.Backend.Helpers.GameMetadataStore;
import com.blackaby.Backend.Helpers.QuickStateManager;
import com.blackaby.Misc.RomConsoleFilter;
import com.blackaby.Misc.UiText;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dedicated manager for save-state slots across tracked library ROMs.
 */
public final class SaveStateManagerWindow extends AbstractSaveManagerWindow<LibraryEntry> {

    private static final DateTimeFormatter saveTimestampFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final DefaultListModel<QuickStateManager.StateSlotInfo> slotListModel = new DefaultListModel<>();
    private final DefaultComboBoxModel<Integer> moveSlotModel = new DefaultComboBoxModel<>();
    private final JLabel detailArtLabel = createArtLabel();
    private final JLabel detailGameNameLabel = new JLabel(UiText.OptionsWindow.SAVE_STATE_MANAGER_EMPTY_TITLE);
    private final JLabel slotPathValueLabel = new JLabel();
    private final JLabel slotSummaryValueLabel = new JLabel();

    private JList<QuickStateManager.StateSlotInfo> slotList;
    private JButton importButton;
    private JButton exportButton;
    private JButton deleteButton;
    private JButton deleteAllButton;
    private JButton moveButton;
    private JComboBox<Integer> moveTargetCombo;

    public SaveStateManagerWindow(MainWindow mainWindow) {
        super(UiText.OptionsWindow.SAVE_STATE_MANAGER_WINDOW_TITLE, 1120, 760, mainWindow);
        initialiseWindow(UiText.OptionsWindow.SAVE_STATE_MANAGER_WINDOW_TITLE,
                UiText.OptionsWindow.SAVE_STATE_MANAGER_SUBTITLE,
                buildBody());
    }

    @Override
    protected String trackedGamesBadgeText(int totalCount) {
        return UiText.OptionsWindow.SaveStateManagerTrackedGamesBadge(totalCount);
    }

    @Override
    protected List<LibraryEntry> loadEntries() {
        return GameLibraryStore.GetEntries();
    }

    @Override
    protected String entryKey(LibraryEntry entry) {
        return entry == null ? null : entry.key();
    }

    @Override
    protected boolean matchesConsoleFilter(LibraryEntry entry, RomConsoleFilter filter) {
        return entry != null && filter.Matches(entry.cgbCompatible());
    }

    @Override
    protected boolean matchesSearchQuery(LibraryEntry entry, String query) {
        if (entry == null || query.isBlank()) {
            return entry != null;
        }

        return containsIgnoreCase(query,
                resolveGameDisplayName(entry),
                entry.sourceName(),
                entry.displayName(),
                entry.headerTitle(),
                String.join(" ", entry.patchNames()));
    }

    @Override
    protected GameArtProvider.GameArtDescriptor artDescriptor(LibraryEntry entry) {
        return entry == null ? null : entry.ArtDescriptor();
    }

    @Override
    protected void rememberMatchedGameName(LibraryEntry entry, String matchedGameName) {
        if (entry != null && matchedGameName != null && !matchedGameName.isBlank()) {
            GameMetadataStore.RememberLibretroTitle(entry.SaveIdentity(), matchedGameName);
        }
    }

    @Override
    protected void updateSelectionDetails(LibraryEntry entry) {
        if (entry == null) {
            detailGameNameLabel.setText(currentEmptyTitle());
            slotSummaryValueLabel.setText(currentEmptyHelper());
            slotPathValueLabel.setText(asHtml(UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_PATH_MISSING, 320,
                    UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_PATH_MISSING));
            slotListModel.clear();
            moveSlotModel.removeAllElements();
            updateArtLabel(detailArtLabel, null, DETAIL_ART_WIDTH - 16, DETAIL_ART_HEIGHT - 16, 12f);
            setActionButtonsEnabled(false, false, false, false);
            return;
        }

        detailGameNameLabel.setText(resolveGameDisplayName(entry));
        updateSlotList(entry, QuickStateManager.quickSlot);
        updateArtLabel(detailArtLabel, entry, DETAIL_ART_WIDTH - 16, DETAIL_ART_HEIGHT - 16, 12f);
    }

    private JComponent buildBody() {
        JPanel wrapper = new JPanel(new BorderLayout(16, 0));
        wrapper.setBackground(panelBackground);
        wrapper.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 20, 20, 20));

        JPanel detailsCard = new JPanel(new BorderLayout(0, 14));
        detailsCard.setBackground(cardBackground);
        detailsCard.setBorder(createCardBorder());

        detailGameNameLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 20f));
        detailGameNameLabel.setForeground(accentColour);

        JPanel headerText = new JPanel(new BorderLayout(0, 10));
        headerText.setOpaque(false);
        headerText.add(detailGameNameLabel, BorderLayout.NORTH);
        headerText.add(createDetailRow(UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_SUMMARY_TITLE, slotSummaryValueLabel),
                BorderLayout.CENTER);

        JPanel detailsHeader = new JPanel(new BorderLayout(16, 0));
        detailsHeader.setOpaque(false);
        detailsHeader.add(createArtPanel(detailArtLabel, DETAIL_ART_WIDTH, DETAIL_ART_HEIGHT), BorderLayout.WEST);
        detailsHeader.add(headerText, BorderLayout.CENTER);

        slotList = new JList<>(slotListModel);
        slotList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        slotList.setBackground(Styling.cardTintColour);
        slotList.setSelectionBackground(Styling.listSelectionColour);
        slotList.setSelectionForeground(accentColour);
        slotList.setFixedCellHeight(60);
        slotList.setCellRenderer(new SlotEntryRenderer());
        slotList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateSlotDetails();
            }
        });

        JScrollPane slotScrollPane = new JScrollPane(slotList);
        slotScrollPane.setBorder(javax.swing.BorderFactory.createLineBorder(cardBorder, 1));
        slotScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel slotsSection = new JPanel(new BorderLayout(0, 8));
        slotsSection.setOpaque(false);
        slotsSection.add(createSectionHeader(
                UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_TITLE,
                UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_HELPER), BorderLayout.NORTH);
        slotsSection.add(slotScrollPane, BorderLayout.CENTER);

        JPanel topButtonRow = new JPanel(new java.awt.GridLayout(1, 4, 10, 0));
        topButtonRow.setOpaque(false);

        importButton = createPrimaryButton(UiText.OptionsWindow.SAVE_STATE_MANAGER_IMPORT_BUTTON);
        importButton.addActionListener(event -> importSelectedState());
        exportButton = createPrimaryButton(UiText.OptionsWindow.SAVE_STATE_MANAGER_EXPORT_BUTTON);
        exportButton.addActionListener(event -> exportSelectedState());
        deleteButton = createPrimaryButton(UiText.OptionsWindow.SAVE_STATE_MANAGER_DELETE_BUTTON);
        deleteButton.addActionListener(event -> deleteSelectedState());
        deleteAllButton = createPrimaryButton(UiText.OptionsWindow.SAVE_STATE_MANAGER_DELETE_ALL_BUTTON);
        deleteAllButton.addActionListener(event -> deleteAllStatesForSelectedGame());

        topButtonRow.add(importButton);
        topButtonRow.add(exportButton);
        topButtonRow.add(deleteButton);
        topButtonRow.add(deleteAllButton);

        JPanel actionsSection = new JPanel(new BorderLayout(0, 8));
        actionsSection.setOpaque(false);
        actionsSection.add(createSectionHeader(
                UiText.OptionsWindow.SAVE_STATE_MANAGER_ACTIONS_TITLE,
                UiText.OptionsWindow.SAVE_STATE_MANAGER_ACTIONS_HELPER), BorderLayout.NORTH);
        actionsSection.add(topButtonRow, BorderLayout.SOUTH);

        moveTargetCombo = new JComboBox<>(moveSlotModel);
        moveTargetCombo.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));

        moveButton = createPrimaryButton(UiText.OptionsWindow.SAVE_STATE_MANAGER_MOVE_BUTTON);
        moveButton.addActionListener(event -> moveSelectedState());

        JPanel moveRow = new JPanel(new BorderLayout(10, 0));
        moveRow.setOpaque(false);
        moveRow.add(createDetailRow(UiText.OptionsWindow.SAVE_STATE_MANAGER_TARGET_SLOT_LABEL, moveTargetCombo),
                BorderLayout.CENTER);
        moveRow.add(moveButton, BorderLayout.EAST);

        JPanel moveSection = new JPanel(new BorderLayout(0, 8));
        moveSection.setOpaque(false);
        moveSection.add(createSectionHeader(
                UiText.OptionsWindow.SAVE_STATE_MANAGER_MOVE_TITLE,
                UiText.OptionsWindow.SAVE_STATE_MANAGER_MOVE_HELPER), BorderLayout.NORTH);
        moveSection.add(moveRow, BorderLayout.CENTER);

        JPanel bottomSection = new JPanel(new BorderLayout(0, 14));
        bottomSection.setOpaque(false);
        bottomSection.add(createDetailRow(UiText.OptionsWindow.SAVE_STATE_MANAGER_PATH_TITLE, slotPathValueLabel),
                BorderLayout.NORTH);
        bottomSection.add(actionsSection, BorderLayout.CENTER);
        bottomSection.add(moveSection, BorderLayout.SOUTH);

        detailsCard.add(detailsHeader, BorderLayout.NORTH);
        detailsCard.add(slotsSection, BorderLayout.CENTER);
        detailsCard.add(bottomSection, BorderLayout.SOUTH);

        wrapper.add(createGameListCard(new GameEntryRenderer(), 360), BorderLayout.WEST);
        wrapper.add(detailsCard, BorderLayout.CENTER);
        return wrapper;
    }

    private void updateSlotList(LibraryEntry entry, int preferredSlot) {
        slotListModel.clear();
        List<QuickStateManager.StateSlotInfo> slots = QuickStateManager.DescribeSlots(identityFor(entry));
        int filledSlots = 0;
        for (QuickStateManager.StateSlotInfo slotInfo : slots) {
            slotListModel.addElement(slotInfo);
            if (slotInfo.exists()) {
                filledSlots++;
            }
        }
        slotSummaryValueLabel.setText(UiText.OptionsWindow.SaveStateSlotSummary(filledSlots));
        selectSlot(preferredSlot);
    }

    private void selectSlot(int preferredSlot) {
        if (slotList == null || slotListModel.isEmpty()) {
            updateSlotDetails();
            return;
        }

        int index = Math.max(0, Math.min(slotListModel.size() - 1, preferredSlot));
        slotList.setSelectedIndex(index);
        slotList.ensureIndexIsVisible(index);
        updateSlotDetails();
    }

    private void updateSlotDetails() {
        LibraryEntry entry = selectedEntry();
        QuickStateManager.StateSlotInfo slotInfo = selectedSlot();
        if (entry == null || slotInfo == null) {
            slotPathValueLabel.setText(asHtml(UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_PATH_MISSING, 320,
                    UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_PATH_MISSING));
            moveSlotModel.removeAllElements();
            setActionButtonsEnabled(false, false, false, false);
            return;
        }

        slotPathValueLabel.setText(asHtml(
                slotInfo.exists() ? slotInfo.path().toString() : UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_PATH_MISSING,
                320,
                UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_PATH_MISSING));

        moveSlotModel.removeAllElements();
        for (int slot = QuickStateManager.quickSlot; slot <= QuickStateManager.maxSlot; slot++) {
            if (slot != slotInfo.slot()) {
                moveSlotModel.addElement(slot);
            }
        }

        boolean hasSlotFile = slotInfo.exists();
        setActionButtonsEnabled(true, hasSlotFile, countFilledSlots(entry) > 0, hasSlotFile && moveSlotModel.getSize() > 0);
    }

    private void setActionButtonsEnabled(boolean allowImport, boolean allowExistingStateActions,
                                         boolean allowDeleteAll, boolean allowMove) {
        if (importButton != null) {
            importButton.setEnabled(allowImport);
        }
        if (exportButton != null) {
            exportButton.setEnabled(allowExistingStateActions);
        }
        if (deleteButton != null) {
            deleteButton.setEnabled(allowExistingStateActions);
        }
        if (deleteAllButton != null) {
            deleteAllButton.setEnabled(allowDeleteAll);
        }
        if (moveButton != null) {
            moveButton.setEnabled(allowMove);
        }
        if (moveTargetCombo != null) {
            moveTargetCombo.setEnabled(allowMove);
        }
    }

    private void importSelectedState() {
        LibraryEntry entry = selectedEntry();
        QuickStateManager.StateSlotInfo slotInfo = selectedSlot();
        if (entry == null || slotInfo == null) {
            return;
        }

        File importFile = chooseLoadFile(UiText.OptionsWindow.SAVE_STATE_MANAGER_IMPORT_DIALOG_TITLE, ".gqs");
        if (importFile == null) {
            return;
        }

        try {
            QuickStateManager.ImportState(identityFor(entry), slotInfo.slot(), importFile.toPath());
            JOptionPane.showMessageDialog(this,
                    UiText.OptionsWindow.SaveStateImportSuccessMessage(resolveGameDisplayName(entry), slotLabel(slotInfo.slot())));
            updateSlotList(entry, slotInfo.slot());
            repaintGameList();
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    UiText.OptionsWindow.SAVE_STATE_MANAGER_IMPORT_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportSelectedState() {
        LibraryEntry entry = selectedEntry();
        QuickStateManager.StateSlotInfo slotInfo = selectedSlot();
        if (entry == null || slotInfo == null || !slotInfo.exists()) {
            return;
        }

        File exportFile = chooseSaveFile(
                UiText.OptionsWindow.SAVE_STATE_MANAGER_EXPORT_DIALOG_TITLE,
                QuickStateManager.QuickStatePath(identityFor(entry), slotInfo.slot()),
                ".gqs");
        if (exportFile == null) {
            return;
        }

        try {
            QuickStateManager.ExportState(identityFor(entry), slotInfo.slot(), exportFile.toPath());
            JOptionPane.showMessageDialog(this,
                    UiText.OptionsWindow.SaveStateExportSuccessMessage(exportFile.getAbsolutePath()));
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    UiText.OptionsWindow.SAVE_STATE_MANAGER_EXPORT_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedState() {
        LibraryEntry entry = selectedEntry();
        QuickStateManager.StateSlotInfo slotInfo = selectedSlot();
        if (entry == null || slotInfo == null || !slotInfo.exists()) {
            return;
        }

        String gameName = resolveGameDisplayName(entry);
        String selectedSlotLabel = slotLabel(slotInfo.slot());
        int result = JOptionPane.showConfirmDialog(this,
                UiText.OptionsWindow.SaveStateDeleteConfirmMessage(gameName, selectedSlotLabel),
                UiText.OptionsWindow.SAVE_STATE_MANAGER_DELETE_CONFIRM_TITLE,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            QuickStateManager.DeleteState(identityFor(entry), slotInfo.slot());
            JOptionPane.showMessageDialog(this,
                    UiText.OptionsWindow.SaveStateDeletedMessage(gameName, selectedSlotLabel));
            updateSlotList(entry, slotInfo.slot());
            repaintGameList();
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    UiText.OptionsWindow.SAVE_STATE_MANAGER_DELETE_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteAllStatesForSelectedGame() {
        LibraryEntry entry = selectedEntry();
        if (entry == null || countFilledSlots(entry) == 0) {
            return;
        }

        String gameName = resolveGameDisplayName(entry);
        int result = JOptionPane.showConfirmDialog(this,
                UiText.OptionsWindow.SaveStateDeleteAllConfirmMessage(gameName),
                UiText.OptionsWindow.SAVE_STATE_MANAGER_DELETE_ALL_CONFIRM_TITLE,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            QuickStateManager.DeleteAllStates(identityFor(entry));
            JOptionPane.showMessageDialog(this, UiText.OptionsWindow.SaveStateDeletedAllMessage(gameName));
            updateSlotList(entry, QuickStateManager.quickSlot);
            repaintGameList();
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    UiText.OptionsWindow.SAVE_STATE_MANAGER_DELETE_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void moveSelectedState() {
        LibraryEntry entry = selectedEntry();
        QuickStateManager.StateSlotInfo slotInfo = selectedSlot();
        Integer targetSlot = selectedTargetSlot();
        if (entry == null || slotInfo == null || !slotInfo.exists() || targetSlot == null) {
            return;
        }

        try {
            QuickStateManager.MoveState(identityFor(entry), slotInfo.slot(), targetSlot);
            JOptionPane.showMessageDialog(this,
                    UiText.OptionsWindow.SaveStateMoveSuccessMessage(
                            resolveGameDisplayName(entry),
                            slotLabel(slotInfo.slot()),
                            slotLabel(targetSlot)));
            updateSlotList(entry, targetSlot);
            repaintGameList();
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    UiText.OptionsWindow.SAVE_STATE_MANAGER_MOVE_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private QuickStateManager.StateSlotInfo selectedSlot() {
        return slotList == null ? null : slotList.getSelectedValue();
    }

    private Integer selectedTargetSlot() {
        return moveTargetCombo == null ? null : (Integer) moveTargetCombo.getSelectedItem();
    }

    private QuickStateManager.QuickStateIdentity identityFor(LibraryEntry entry) {
        return entry == null
                ? null
                : new QuickStateManager.QuickStateIdentity(
                        entry.sourcePath(),
                        entry.sourceName(),
                        entry.displayName(),
                        entry.patchNames());
    }

    private boolean isCurrentGame(LibraryEntry entry) {
        GBRom currentRom = mainWindow == null ? null : mainWindow.GetCurrentLoadedRom();
        return entry != null
                && currentRom != null
                && safeText(entry.sourcePath()).equals(safeText(currentRom.GetSourcePath()))
                && safeText(entry.sourceName()).equals(safeText(currentRom.GetSourceName()))
                && safeText(entry.displayName()).equals(safeText(currentRom.GetName()))
                && entry.patchNames().equals(currentRom.GetPatchNames());
    }

    private String resolveGameDisplayName(LibraryEntry entry) {
        String baseName = GameMetadataStore.GetLibretroTitle(entry.SaveIdentity())
                .orElseGet(() -> entry.sourceName() != null && !entry.sourceName().isBlank()
                        ? entry.sourceName()
                        : entry.displayName());
        return MainWindow.ApplyGameNameDisplayMode(baseName);
    }

    private String buildGameHelperText(LibraryEntry entry) {
        int filledSlots = countFilledSlots(entry);
        String helper = (isCurrentGame(entry)
                ? UiText.OptionsWindow.SAVE_STATE_MANAGER_CURRENT_GAME_BADGE
                : filledSlots > 0
                        ? UiText.OptionsWindow.SAVE_STATE_MANAGER_READY_BADGE
                        : UiText.OptionsWindow.SAVE_STATE_MANAGER_NO_SLOT_BADGE)
                + " | " + UiText.OptionsWindow.SaveStateSlotSummary(filledSlots);
        return entry.patchNames().isEmpty()
                ? helper
                : helper + " | " + UiText.LibraryWindow.VariantLabel(entry.patchNames());
    }

    private String currentEmptyTitle() {
        return hasActiveFilters()
                ? UiText.OptionsWindow.SAVE_STATE_MANAGER_FILTERED_EMPTY_TITLE
                : UiText.OptionsWindow.SAVE_STATE_MANAGER_EMPTY_TITLE;
    }

    private String currentEmptyHelper() {
        return hasActiveFilters()
                ? UiText.OptionsWindow.SAVE_STATE_MANAGER_FILTERED_EMPTY_HELPER
                : UiText.OptionsWindow.SAVE_STATE_MANAGER_EMPTY_HELPER;
    }

    private int countFilledSlots(LibraryEntry entry) {
        return (int) QuickStateManager.DescribeSlots(identityFor(entry)).stream()
                .filter(QuickStateManager.StateSlotInfo::exists)
                .count();
    }

    private String slotLabel(int slot) {
        return UiText.MainWindow.SaveStateSlotLabel(slot, "");
    }

    private String formatSaveTimestamp(FileTime lastModified) {
        if (lastModified == null || lastModified.toMillis() <= 0L) {
            return UiText.OptionsWindow.SAVE_DETAILS_UNKNOWN_TIME;
        }
        return saveTimestampFormatter.format(lastModified.toInstant().atZone(ZoneId.systemDefault()));
    }

    private final class GameEntryRenderer extends ArtListRenderer {
        @Override
        protected String titleText(LibraryEntry value) {
            return resolveGameDisplayName(value);
        }

        @Override
        protected String helperText(LibraryEntry value) {
            return buildGameHelperText(value);
        }
    }

    private final class SlotEntryRenderer extends JPanel implements ListCellRenderer<QuickStateManager.StateSlotInfo> {
        private final JLabel titleLabel = new JLabel();
        private final JLabel helperLabel = new JLabel();

        private SlotEntryRenderer() {
            setOpaque(true);
            titleLabel.setForeground(accentColour);
            helperLabel.setForeground(mutedText);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends QuickStateManager.StateSlotInfo> list,
                                                      QuickStateManager.StateSlotInfo value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            removeAll();

            Color background = isSelected ? Styling.listSelectionColour : Styling.cardTintColour;
            setBackground(background);
            setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createLineBorder(
                            isSelected ? Styling.sectionHighlightBorderColour : cardBorder, 1, true),
                    javax.swing.BorderFactory.createEmptyBorder(8, 10, 8, 10)));

            String timestamp = value.exists() ? formatSaveTimestamp(value.lastModified()) : "";
            titleLabel.setText(UiText.MainWindow.SaveStateSlotLabel(value.slot(), timestamp));
            titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));

            helperLabel.setText(value.exists()
                    ? UiText.OptionsWindow.SaveStateSlotStatus(true) + " | " + value.path().getFileName()
                    : UiText.OptionsWindow.SaveStateSlotStatus(false));
            helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 11f));

            setLayout(new BorderLayout(0, 4));
            add(titleLabel, BorderLayout.NORTH);
            add(helperLabel, BorderLayout.CENTER);
            return this;
        }
    }
}

