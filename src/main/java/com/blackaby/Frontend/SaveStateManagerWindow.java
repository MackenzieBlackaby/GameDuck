package com.blackaby.Frontend;

import com.blackaby.Backend.Emulation.Misc.ROM;
import com.blackaby.Backend.Helpers.GameArtProvider;
import com.blackaby.Backend.Helpers.GameLibraryStore;
import com.blackaby.Backend.Helpers.GameLibraryStore.LibraryEntry;
import com.blackaby.Backend.Helpers.GameMetadataStore;
import com.blackaby.Backend.Helpers.QuickStateManager;
import com.blackaby.Misc.RomConsoleFilter;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
import java.awt.GridLayout;
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
 * Dedicated manager for save-state slots across tracked library ROMs.
 */
public final class SaveStateManagerWindow extends DuckWindow {

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
    private final DefaultListModel<LibraryEntry> gameListModel = new DefaultListModel<>();
    private final DefaultListModel<QuickStateManager.StateSlotInfo> slotListModel = new DefaultListModel<>();
    private final DefaultComboBoxModel<Integer> moveSlotModel = new DefaultComboBoxModel<>();
    private final Map<String, BufferedImage> artCache = new ConcurrentHashMap<>();
    private final Set<String> artLoadingKeys = ConcurrentHashMap.newKeySet();
    private final JLabel detailArtLabel = createArtLabel();
    private final JLabel detailGameNameLabel = new JLabel(UiText.OptionsWindow.SAVE_STATE_MANAGER_EMPTY_TITLE);
    private final JLabel slotPathValueLabel = new JLabel();
    private final JLabel slotSummaryValueLabel = new JLabel();

    private JLabel trackedGamesBadgeLabel;
    private JList<LibraryEntry> gameList;
    private JList<QuickStateManager.StateSlotInfo> slotList;
    private JButton importButton;
    private JButton exportButton;
    private JButton deleteButton;
    private JButton deleteAllButton;
    private JButton moveButton;
    private JComboBox<Integer> moveTargetCombo;
    private RomConsoleFilter consoleFilter = RomConsoleFilter.ALL;
    private String searchQuery = "";

    public SaveStateManagerWindow(MainWindow mainWindow) {
        super(UiText.OptionsWindow.SAVE_STATE_MANAGER_WINDOW_TITLE, 1120, 760, true);
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

        JLabel titleLabel = new JLabel(UiText.OptionsWindow.SAVE_STATE_MANAGER_WINDOW_TITLE);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(accentColour);

        JLabel subtitleLabel = new JLabel(UiText.OptionsWindow.SAVE_STATE_MANAGER_SUBTITLE);
        subtitleLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        subtitleLabel.setForeground(mutedText);

        titleStack.add(titleLabel, BorderLayout.NORTH);
        titleStack.add(subtitleLabel, BorderLayout.CENTER);

        JPanel topRow = new JPanel(new BorderLayout(12, 0));
        topRow.setOpaque(false);

        JPanel actionStack = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionStack.setOpaque(false);

        trackedGamesBadgeLabel = createBadgeLabel(UiText.OptionsWindow.SaveStateManagerTrackedGamesBadge(0));

        JButton refreshButton = createSecondaryButton(UiText.OptionsWindow.SAVE_MANAGER_REFRESH_BUTTON);
        refreshButton.addActionListener(event -> refreshGameList());

        actionStack.add(trackedGamesBadgeLabel);
        actionStack.add(refreshButton);

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterRow.setOpaque(false);

        JLabel consoleLabel = new JLabel(UiText.Common.CONSOLE_TITLE);
        consoleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        consoleLabel.setForeground(accentColour);
        filterRow.add(consoleLabel);

        JComboBox<RomConsoleFilter> consoleSelector = new JComboBox<>(RomConsoleFilter.values());
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
        listCard.setPreferredSize(new Dimension(360, 0));

        gameList = new JList<>(gameListModel);
        gameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        gameList.setBackground(Styling.cardTintColour);
        gameList.setSelectionBackground(Styling.listSelectionColour);
        gameList.setSelectionForeground(accentColour);
        gameList.setFixedCellHeight(80);
        gameList.setCellRenderer(new GameEntryRenderer());
        gameList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateSelectionDetails(gameList.getSelectedValue());
            }
        });

        JScrollPane listScrollPane = new JScrollPane(gameList);
        listScrollPane.setBorder(BorderFactory.createLineBorder(cardBorder, 1));
        listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listCard.add(listScrollPane, BorderLayout.CENTER);

        JPanel detailsCard = new JPanel(new BorderLayout(0, 14));
        detailsCard.setBackground(cardBackground);
        detailsCard.setBorder(createCardBorder());

        detailGameNameLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 20f));
        detailGameNameLabel.setForeground(accentColour);

        JPanel detailsHeader = new JPanel(new BorderLayout(16, 0));
        detailsHeader.setOpaque(false);

        JPanel artPanel = new JPanel(new BorderLayout());
        artPanel.setOpaque(true);
        artPanel.setBackground(Styling.displayFrameColour);
        artPanel.setBorder(BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1));
        artPanel.setPreferredSize(new Dimension(detailArtWidth, detailArtHeight));
        artPanel.add(detailArtLabel, BorderLayout.CENTER);

        JPanel headerText = new JPanel(new BorderLayout(0, 10));
        headerText.setOpaque(false);
        headerText.add(detailGameNameLabel, BorderLayout.NORTH);
        headerText.add(createDetailRow(UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_SUMMARY_TITLE, slotSummaryValueLabel),
                BorderLayout.CENTER);

        detailsHeader.add(artPanel, BorderLayout.WEST);
        detailsHeader.add(headerText, BorderLayout.CENTER);

        JPanel slotsSection = new JPanel(new BorderLayout(0, 8));
        slotsSection.setOpaque(false);

        JLabel slotsTitleLabel = new JLabel(UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_TITLE);
        slotsTitleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        slotsTitleLabel.setForeground(accentColour);

        JLabel slotsHelperLabel = new JLabel(UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_HELPER);
        slotsHelperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        slotsHelperLabel.setForeground(mutedText);

        JPanel slotsTitleStack = new JPanel(new BorderLayout(0, 4));
        slotsTitleStack.setOpaque(false);
        slotsTitleStack.add(slotsTitleLabel, BorderLayout.NORTH);
        slotsTitleStack.add(slotsHelperLabel, BorderLayout.CENTER);

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
        slotScrollPane.setBorder(BorderFactory.createLineBorder(cardBorder, 1));
        slotScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        slotsSection.add(slotsTitleStack, BorderLayout.NORTH);
        slotsSection.add(slotScrollPane, BorderLayout.CENTER);

        JPanel bottomSection = new JPanel(new BorderLayout(0, 14));
        bottomSection.setOpaque(false);
        bottomSection.add(createDetailRow(UiText.OptionsWindow.SAVE_STATE_MANAGER_PATH_TITLE, slotPathValueLabel),
                BorderLayout.NORTH);

        JPanel actionsSection = new JPanel(new BorderLayout(0, 8));
        actionsSection.setOpaque(false);

        JLabel actionsTitleLabel = new JLabel(UiText.OptionsWindow.SAVE_STATE_MANAGER_ACTIONS_TITLE);
        actionsTitleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        actionsTitleLabel.setForeground(accentColour);

        JLabel actionsHelperLabel = new JLabel(UiText.OptionsWindow.SAVE_STATE_MANAGER_ACTIONS_HELPER);
        actionsHelperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        actionsHelperLabel.setForeground(mutedText);

        JPanel topButtonRow = new JPanel(new GridLayout(1, 4, 10, 0));
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

        JPanel moveSection = new JPanel(new BorderLayout(0, 8));
        moveSection.setOpaque(false);

        JLabel moveTitleLabel = new JLabel(UiText.OptionsWindow.SAVE_STATE_MANAGER_MOVE_TITLE);
        moveTitleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        moveTitleLabel.setForeground(accentColour);

        JLabel moveHelperLabel = new JLabel(UiText.OptionsWindow.SAVE_STATE_MANAGER_MOVE_HELPER);
        moveHelperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        moveHelperLabel.setForeground(mutedText);

        JPanel moveHeader = new JPanel(new BorderLayout(0, 4));
        moveHeader.setOpaque(false);
        moveHeader.add(moveTitleLabel, BorderLayout.NORTH);
        moveHeader.add(moveHelperLabel, BorderLayout.CENTER);

        JPanel moveRow = new JPanel(new BorderLayout(10, 0));
        moveRow.setOpaque(false);

        moveTargetCombo = new JComboBox<>(moveSlotModel);
        moveTargetCombo.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));

        moveButton = createPrimaryButton(UiText.OptionsWindow.SAVE_STATE_MANAGER_MOVE_BUTTON);
        moveButton.addActionListener(event -> moveSelectedState());

        moveRow.add(createDetailRow(UiText.OptionsWindow.SAVE_STATE_MANAGER_TARGET_SLOT_LABEL, moveTargetCombo), BorderLayout.CENTER);
        moveRow.add(moveButton, BorderLayout.EAST);

        moveSection.add(moveHeader, BorderLayout.NORTH);
        moveSection.add(moveRow, BorderLayout.CENTER);

        actionsSection.add(actionsTitleLabel, BorderLayout.NORTH);
        actionsSection.add(actionsHelperLabel, BorderLayout.CENTER);
        actionsSection.add(topButtonRow, BorderLayout.SOUTH);

        bottomSection.add(actionsSection, BorderLayout.CENTER);
        bottomSection.add(moveSection, BorderLayout.SOUTH);

        detailsCard.add(detailsHeader, BorderLayout.NORTH);
        detailsCard.add(slotsSection, BorderLayout.CENTER);
        detailsCard.add(bottomSection, BorderLayout.SOUTH);

        wrapper.add(listCard, BorderLayout.WEST);
        wrapper.add(detailsCard, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createDetailRow(String title, Component valueComponent) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(accentColour);

        if (valueComponent instanceof JLabel valueLabel) {
            valueLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
            valueLabel.setForeground(mutedText);
            valueLabel.setVerticalAlignment(SwingConstants.TOP);
        }

        row.add(titleLabel, BorderLayout.NORTH);
        row.add(valueComponent, BorderLayout.CENTER);
        return row;
    }

    private void refreshGameList() {
        LibraryEntry selectedGame = gameList == null ? null : gameList.getSelectedValue();
        int selectedSlot = slotList == null || slotList.getSelectedValue() == null
                ? QuickStateManager.quickSlot
                : slotList.getSelectedValue().slot();
        gameListModel.clear();

        List<LibraryEntry> allEntries = GameLibraryStore.GetEntries();
        trackedGamesBadgeLabel.setText(UiText.OptionsWindow.SaveStateManagerTrackedGamesBadge(allEntries.size()));

        List<LibraryEntry> entries = allEntries.stream()
                .filter(this::matchesConsoleFilter)
                .filter(this::matchesSearchQuery)
                .toList();

        for (LibraryEntry entry : entries) {
            gameListModel.addElement(entry);
            requestArt(entry);
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

        selectSlot(selectedSlot);
    }

    private void updateSelectionDetails(LibraryEntry entry) {
        if (entry == null) {
            detailGameNameLabel.setText(currentEmptyTitle());
            slotSummaryValueLabel.setText(currentEmptyHelper());
            slotPathValueLabel.setText(asHtml(UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_PATH_MISSING));
            detailArtLabel.setIcon(null);
            detailArtLabel.setText(UiText.LibraryWindow.ART_MISSING);
            detailArtLabel.setForeground(mutedText);
            slotListModel.clear();
            moveSlotModel.removeAllElements();
            setActionButtonsEnabled(false, false, false, false);
            return;
        }

        detailGameNameLabel.setText(resolveGameDisplayName(entry));
        updateSlotList(entry, QuickStateManager.quickSlot);

        BufferedImage art = artCache.get(entry.key());
        if (art != null) {
            BufferedImage scaled = GameArtScaler.ScaleToFit(art, detailArtWidth - 16, detailArtHeight - 16, true);
            detailArtLabel.setIcon(scaled == null ? null : new ImageIcon(scaled));
            detailArtLabel.setText("");
            detailArtLabel.setForeground(Styling.fpsForegroundColour);
        } else {
            detailArtLabel.setIcon(null);
            detailArtLabel.setText(artLoadingKeys.contains(entry.key())
                    ? UiText.LibraryWindow.ART_LOADING
                    : UiText.LibraryWindow.ART_MISSING);
            detailArtLabel.setForeground(mutedText);
        }
    }

    private void updateSlotList(LibraryEntry entry, int preferredSlot) {
        slotListModel.clear();
        QuickStateManager.QuickStateIdentity identity = identityFor(entry);
        List<QuickStateManager.StateSlotInfo> slots = QuickStateManager.DescribeSlots(identity);
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
        LibraryEntry entry = gameList == null ? null : gameList.getSelectedValue();
        QuickStateManager.StateSlotInfo slotInfo = slotList == null ? null : slotList.getSelectedValue();
        if (entry == null || slotInfo == null) {
            slotPathValueLabel.setText(asHtml(UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_PATH_MISSING));
            moveSlotModel.removeAllElements();
            setActionButtonsEnabled(false, false, false, false);
            return;
        }

        if (slotInfo.exists()) {
            slotPathValueLabel.setText(asHtml(slotInfo.path().toString()));
        } else {
            slotPathValueLabel.setText(asHtml(UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_PATH_MISSING));
        }

        moveSlotModel.removeAllElements();
        for (int slot = QuickStateManager.quickSlot; slot <= QuickStateManager.maxSlot; slot++) {
            if (slot != slotInfo.slot()) {
                moveSlotModel.addElement(slot);
            }
        }

        boolean hasGame = entry != null;
        boolean hasSlotFile = slotInfo.exists();
        boolean hasAnyState = countFilledSlots(entry) > 0;
        boolean canMove = hasSlotFile && moveSlotModel.getSize() > 0;
        setActionButtonsEnabled(hasGame, hasSlotFile, hasAnyState, canMove);
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

    private JLabel createArtLabel() {
        JLabel label = new JLabel(UiText.LibraryWindow.ART_LOADING, SwingConstants.CENTER);
        label.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        label.setForeground(mutedText);
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        return label;
    }

    private void applyConsoleFilter(RomConsoleFilter nextConsoleFilter) {
        consoleFilter = nextConsoleFilter == null ? RomConsoleFilter.ALL : nextConsoleFilter;
        refreshGameList();
    }

    private void applySearchQuery(String nextSearchQuery) {
        searchQuery = nextSearchQuery == null ? "" : nextSearchQuery.trim();
        refreshGameList();
    }

    private boolean matchesConsoleFilter(LibraryEntry entry) {
        return entry != null && consoleFilter.Matches(entry.cgbCompatible());
    }

    private boolean matchesSearchQuery(LibraryEntry entry) {
        if (entry == null || searchQuery.isBlank()) {
            return entry != null;
        }

        return containsIgnoreCase(searchQuery,
                resolveGameDisplayName(entry),
                entry.sourceName(),
                entry.displayName(),
                entry.headerTitle(),
                String.join(" ", entry.patchNames()));
    }

    private void requestArt(LibraryEntry entry) {
        if (entry == null || artCache.containsKey(entry.key()) || !artLoadingKeys.add(entry.key())) {
            return;
        }

        CompletableFuture
                .supplyAsync(() -> GameArtProvider.FindGameArt(entry.ArtDescriptor()))
                .thenAccept(result -> {
                    result.ifPresent(gameArtResult -> {
                        if (gameArtResult.matchedGameName() != null && !gameArtResult.matchedGameName().isBlank()) {
                            GameMetadataStore.RememberLibretroTitle(entry.SaveIdentity(), gameArtResult.matchedGameName());
                        }
                        artCache.put(entry.key(), gameArtResult.image());
                    });
                    artLoadingKeys.remove(entry.key());
                    SwingUtilities.invokeLater(() -> {
                        if (gameList != null) {
                            gameList.repaint();
                            if (entry.equals(gameList.getSelectedValue())) {
                                updateSelectionDetails(entry);
                            }
                        }
                    });
                })
                .exceptionally(exception -> {
                    artLoadingKeys.remove(entry.key());
                    SwingUtilities.invokeLater(() -> {
                        if (gameList != null) {
                            gameList.repaint();
                            if (entry.equals(gameList.getSelectedValue())) {
                                updateSelectionDetails(entry);
                            }
                        }
                    });
                    return null;
                });
    }

    private void importSelectedState() {
        LibraryEntry entry = selectedEntry();
        QuickStateManager.StateSlotInfo slotInfo = selectedSlot();
        if (entry == null || slotInfo == null) {
            return;
        }

        File importFile = promptForStateImportFile();
        if (importFile == null) {
            return;
        }

        try {
            QuickStateManager.ImportState(identityFor(entry), slotInfo.slot(), importFile.toPath());
            JOptionPane.showMessageDialog(this,
                    UiText.OptionsWindow.SaveStateImportSuccessMessage(resolveGameDisplayName(entry), slotLabel(slotInfo.slot())));
            updateSlotList(entry, slotInfo.slot());
            if (gameList != null) {
                gameList.repaint();
            }
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

        File exportFile = promptForStateExportFile(identityFor(entry), slotInfo.slot());
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
            if (gameList != null) {
                gameList.repaint();
            }
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
            if (gameList != null) {
                gameList.repaint();
            }
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
            if (gameList != null) {
                gameList.repaint();
            }
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    UiText.OptionsWindow.SAVE_STATE_MANAGER_MOVE_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private LibraryEntry selectedEntry() {
        return gameList == null ? null : gameList.getSelectedValue();
    }

    private QuickStateManager.StateSlotInfo selectedSlot() {
        return slotList == null ? null : slotList.getSelectedValue();
    }

    private Integer selectedTargetSlot() {
        return moveTargetCombo == null ? null : (Integer) moveTargetCombo.getSelectedItem();
    }

    private QuickStateManager.QuickStateIdentity identityFor(LibraryEntry entry) {
        if (entry == null) {
            return null;
        }
        return new QuickStateManager.QuickStateIdentity(
                entry.sourcePath(),
                entry.sourceName(),
                entry.displayName(),
                entry.patchNames());
    }

    private boolean isCurrentGame(LibraryEntry entry) {
        ROM currentRom = mainWindow == null ? null : mainWindow.GetCurrentLoadedRom();
        if (entry == null || currentRom == null) {
            return false;
        }

        return safeText(entry.sourcePath()).equals(safeText(currentRom.GetSourcePath()))
                && safeText(entry.sourceName()).equals(safeText(currentRom.GetSourceName()))
                && safeText(entry.displayName()).equals(safeText(currentRom.GetName()))
                && entry.patchNames().equals(currentRom.GetPatchNames());
    }

    private String resolveGameDisplayName(LibraryEntry entry) {
        String baseName = GameMetadataStore.GetLibretroTitle(entry.SaveIdentity())
                .orElseGet(() -> {
                    if (entry.sourceName() != null && !entry.sourceName().isBlank()) {
                        return entry.sourceName();
                    }
                    return entry.displayName();
                });
        return MainWindow.ApplyGameNameDisplayMode(baseName);
    }

    private String buildGameHelperText(LibraryEntry entry) {
        int filledSlots = countFilledSlots(entry);
        String badge = isCurrentGame(entry)
                ? UiText.OptionsWindow.SAVE_STATE_MANAGER_CURRENT_GAME_BADGE
                : filledSlots > 0
                        ? UiText.OptionsWindow.SAVE_STATE_MANAGER_READY_BADGE
                        : UiText.OptionsWindow.SAVE_STATE_MANAGER_NO_SLOT_BADGE;

        String helper = badge + " | " + UiText.OptionsWindow.SaveStateSlotSummary(filledSlots);
        if (!entry.patchNames().isEmpty()) {
            helper += " | " + UiText.LibraryWindow.VariantLabel(entry.patchNames());
        }
        return helper;
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

    private boolean hasActiveFilters() {
        return !searchQuery.isBlank() || consoleFilter != RomConsoleFilter.ALL;
    }

    private int countFilledSlots(LibraryEntry entry) {
        int count = 0;
        for (QuickStateManager.StateSlotInfo slotInfo : QuickStateManager.DescribeSlots(identityFor(entry))) {
            if (slotInfo.exists()) {
                count++;
            }
        }
        return count;
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

    private File promptForStateImportFile() {
        FileDialog fileDialog = new FileDialog(this, UiText.OptionsWindow.SAVE_STATE_MANAGER_IMPORT_DIALOG_TITLE, FileDialog.LOAD);
        fileDialog.setAlwaysOnTop(true);
        fileDialog.setFilenameFilter((directory, name) -> name.toLowerCase().endsWith(".gqs"));
        fileDialog.setVisible(true);
        return fileDialog.getFiles().length == 0 ? null : fileDialog.getFiles()[0];
    }

    private File promptForStateExportFile(QuickStateManager.QuickStateIdentity identity, int slot) {
        FileDialog fileDialog = new FileDialog(this, UiText.OptionsWindow.SAVE_STATE_MANAGER_EXPORT_DIALOG_TITLE, FileDialog.SAVE);
        fileDialog.setAlwaysOnTop(true);

        Path defaultPath = QuickStateManager.QuickStatePath(identity, slot);
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
        if (!selectedName.toLowerCase().endsWith(".gqs")) {
            selectedFile = new File(selectedFile.getParentFile(), selectedName + ".gqs");
        }
        return selectedFile;
    }

    private String asHtml(String value) {
        if (value == null || value.isBlank()) {
            return "<html><body style='width: 320px'>" + UiText.OptionsWindow.SAVE_STATE_MANAGER_SLOT_PATH_MISSING + "</body></html>";
        }
        return "<html><body style='width: 320px'>" + escapeHtml(value) + "</body></html>";
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

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private javax.swing.border.Border createCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardBorder, 1),
                BorderFactory.createEmptyBorder(18, 18, 18, 18));
    }

    private final class GameEntryRenderer extends JPanel implements ListCellRenderer<LibraryEntry> {
        private final JPanel artPanel = new JPanel(new BorderLayout());
        private final JLabel artLabel = createArtLabel();
        private final JLabel titleLabel = new JLabel();
        private final JLabel helperLabel = new JLabel();

        private GameEntryRenderer() {
            setOpaque(true);
            artPanel.setOpaque(true);
            artPanel.setBackground(Styling.displayFrameColour);
            artPanel.setBorder(BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1));
            artPanel.add(artLabel, BorderLayout.CENTER);
            titleLabel.setForeground(accentColour);
            helperLabel.setForeground(mutedText);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends LibraryEntry> list, LibraryEntry value, int index,
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

            titleLabel.setText(resolveGameDisplayName(value));
            titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));

            helperLabel.setText(buildGameHelperText(value));
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
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isSelected ? Styling.sectionHighlightBorderColour : cardBorder, 1, true),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));

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
