package com.blackaby.Frontend;

import com.blackaby.Backend.Helpers.GameArtProvider;
import com.blackaby.Backend.Helpers.GameLibraryStore;
import com.blackaby.Backend.Helpers.GameLibraryStore.LibraryEntry;
import com.blackaby.Backend.Helpers.GameMetadataStore;
import com.blackaby.Backend.Platform.EmulatorRuntime;
import com.blackaby.Misc.Config;
import com.blackaby.Misc.GameArtDisplayMode;
import com.blackaby.Misc.RomConsoleFilter;
import com.blackaby.Misc.Settings;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
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
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Browser for loading ROMs and hacks from the managed library.
 */
public final class LibraryWindow extends DuckWindow {

    private enum ViewMode {
        LIST(UiText.LibraryWindow.VIEW_LIST),
        SMALL_ICONS(UiText.LibraryWindow.VIEW_SMALL_ICONS),
        LARGE_ICONS(UiText.LibraryWindow.VIEW_LARGE_ICONS);

        private final String label;

        ViewMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum FilterMode {
        ALL(UiText.LibraryWindow.FILTER_ALL),
        FAVOURITES_ONLY(UiText.LibraryWindow.FILTER_FAVOURITES),
        ROM_HACKS_ONLY(UiText.LibraryWindow.FILTER_ROM_HACKS),
        BASE_ROMS_ONLY(UiText.LibraryWindow.FILTER_BASE_ROMS);

        private final String label;

        FilterMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum SortMode {
        ALPHABETICAL(UiText.LibraryWindow.SORT_ALPHABETICAL),
        RECENTLY_PLAYED(UiText.LibraryWindow.SORT_RECENTLY_PLAYED),
        NOT_PLAYED_FOR_A_WHILE(UiText.LibraryWindow.SORT_NOT_PLAYED_FOR_A_WHILE);

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final int smallArtSize = 64;
    private static final int largeArtSize = 128;
    private static final int largeTilePreferredWidth = 220;
    private static final int largeTileMinWidth = 170;
    private static final int largeTileMaxWidth = 320;
    private static final int detailPreviewWidth = 232;
    private static final int detailPreviewHeight = 174;
    private static final int searchRefreshDelayMillis = 160;

    private final MainWindow mainWindow;
    private final EmulatorRuntime emulation;
    private final Color panelBackground;
    private final Color cardBackground;
    private final Color cardBorder;
    private final Color accentColour;
    private final Color mutedTextColour;
    private final DefaultListModel<LibraryEntry> entryListModel = new DefaultListModel<>();
    private final JLabel detailTitleLabel = new JLabel();
    private final JLabel detailPreviewLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel variantValueLabel = new JLabel();
    private final Map<String, BufferedImage> artCache = new ConcurrentHashMap<>();
    private final Map<String, ImageIcon> scaledArtIconCache = new ConcurrentHashMap<>();
    private final Set<String> artLoadingKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, BufferedImage> titleScreenCache = new ConcurrentHashMap<>();
    private final Map<String, ImageIcon> titleScreenPreviewIconCache = new ConcurrentHashMap<>();
    private final Set<String> titleScreenLoadingKeys = ConcurrentHashMap.newKeySet();
    private final LibraryEntryRenderer entryRenderer;
    private final JPanel largeIconWrapperPanel = new JPanel(new BorderLayout());
    private final JPanel largeIconGridPanel = new JPanel();
    private final Timer searchRefreshTimer;
    private final Font detailPreviewPlaceholderFont = Styling.menuFont.deriveFont(Font.PLAIN, 12f);
    private final Font largePlaceholderFont = Styling.menuFont.deriveFont(Font.BOLD, 11f);

    private JList<LibraryEntry> entryList;
    private JScrollPane listScrollPane;
    private JButton infoButton;
    private JButton loadButton;
    private JButton favouriteButton;
    private ViewMode viewMode = ViewMode.LIST;
    private FilterMode filterMode = FilterMode.ALL;
    private SortMode sortMode = SortMode.ALPHABETICAL;
    private RomConsoleFilter consoleFilter = RomConsoleFilter.ALL;
    private String searchQuery = "";
    private String selectedEntryKey = "";

    public LibraryWindow(MainWindow mainWindow, EmulatorRuntime emulation) {
        super(UiText.LibraryWindow.WINDOW_TITLE, 920, 620, true);
        this.mainWindow = mainWindow;
        this.emulation = emulation;
        this.viewMode = resolveSavedViewMode();
        this.filterMode = resolveSavedFilterMode();
        this.consoleFilter = resolveSavedConsoleFilter();
        this.sortMode = resolveSavedSortMode();
        this.searchQuery = resolveSavedSearchQuery();
        panelBackground = Styling.appBackgroundColour;
        cardBackground = Styling.surfaceColour;
        cardBorder = Styling.surfaceBorderColour;
        accentColour = Styling.accentColour;
        mutedTextColour = Styling.mutedTextColour;
        entryRenderer = new LibraryEntryRenderer();
        searchRefreshTimer = new Timer(searchRefreshDelayMillis, event -> refreshEntryList());
        searchRefreshTimer.setRepeats(false);
        largeIconWrapperPanel.setBackground(Styling.cardTintColour);
        largeIconWrapperPanel.add(largeIconGridPanel, BorderLayout.NORTH);
        largeIconGridPanel.setBackground(Styling.cardTintColour);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(panelBackground);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);

        refreshEntryList();
        setVisible(true);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 10));
        header.setBackground(panelBackground);
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));

        JPanel titleStack = new JPanel(new BorderLayout(0, 6));
        titleStack.setOpaque(false);

        JLabel titleLabel = new JLabel(UiText.LibraryWindow.TITLE);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(accentColour);

        JLabel subtitleLabel = new JLabel(UiText.LibraryWindow.SUBTITLE);
        subtitleLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        subtitleLabel.setForeground(mutedTextColour);

        titleStack.add(titleLabel, BorderLayout.NORTH);
        titleStack.add(subtitleLabel, BorderLayout.CENTER);

        JPanel topRow = new JPanel(new BorderLayout(12, 0));
        topRow.setOpaque(false);

        JPanel actionStack = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionStack.setOpaque(false);

        JButton saveManagerButton = createSecondaryButton(UiText.LibraryWindow.SAVE_MANAGER_BUTTON);
        saveManagerButton.addActionListener(event -> new SaveDataManagerWindow(mainWindow));
        actionStack.add(saveManagerButton);

        JButton refreshButton = createSecondaryButton(UiText.LibraryWindow.REFRESH_BUTTON);
        refreshButton.addActionListener(event -> refreshEntryList());
        actionStack.add(refreshButton);

        JButton closeButton = createSecondaryButton(UiText.LibraryWindow.CLOSE_BUTTON);
        closeButton.addActionListener(event -> dispose());
        actionStack.add(closeButton);

        JPanel viewControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        viewControls.setOpaque(false);

        JLabel filterLabel = new JLabel(UiText.LibraryWindow.FILTER_TITLE);
        filterLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        filterLabel.setForeground(accentColour);
        viewControls.add(filterLabel);

        JComboBox<FilterMode> filterSelector = new JComboBox<>(FilterMode.values());
        filterSelector.setSelectedItem(filterMode);
        filterSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        filterSelector.addActionListener(event -> {
            Object selectedItem = filterSelector.getSelectedItem();
            if (selectedItem instanceof FilterMode selectedMode) {
                applyFilterMode(selectedMode);
            }
        });
        viewControls.add(filterSelector);

        JLabel consoleLabel = new JLabel(UiText.Common.CONSOLE_TITLE);
        consoleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        consoleLabel.setForeground(accentColour);
        viewControls.add(consoleLabel);

        JComboBox<RomConsoleFilter> consoleSelector = new JComboBox<>(RomConsoleFilter.values());
        consoleSelector.setSelectedItem(consoleFilter);
        consoleSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        consoleSelector.addActionListener(event -> {
            Object selectedItem = consoleSelector.getSelectedItem();
            if (selectedItem instanceof RomConsoleFilter selectedConsoleFilter) {
                applyConsoleFilter(selectedConsoleFilter);
            }
        });
        viewControls.add(consoleSelector);

        JLabel sortLabel = new JLabel(UiText.LibraryWindow.SORT_TITLE);
        sortLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        sortLabel.setForeground(accentColour);
        viewControls.add(sortLabel);

        JComboBox<SortMode> sortSelector = new JComboBox<>(SortMode.values());
        sortSelector.setSelectedItem(sortMode);
        sortSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        sortSelector.addActionListener(event -> {
            Object selectedItem = sortSelector.getSelectedItem();
            if (selectedItem instanceof SortMode selectedSortMode) {
                applySortMode(selectedSortMode);
            }
        });
        viewControls.add(sortSelector);

        JLabel searchLabel = new JLabel(UiText.Common.SEARCH_TITLE);
        searchLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        searchLabel.setForeground(accentColour);
        viewControls.add(searchLabel);

        JTextField searchField = new JTextField(searchQuery, 16);
        searchField.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                scheduleSearchQuery(searchField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                scheduleSearchQuery(searchField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                scheduleSearchQuery(searchField.getText());
            }
        });
        viewControls.add(searchField);

        JLabel viewLabel = new JLabel(UiText.LibraryWindow.VIEW_TITLE);
        viewLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        viewLabel.setForeground(accentColour);
        viewControls.add(viewLabel);

        JComboBox<ViewMode> viewSelector = new JComboBox<>(ViewMode.values());
        viewSelector.setSelectedItem(viewMode);
        viewSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        viewSelector.addActionListener(event -> {
            Object selectedItem = viewSelector.getSelectedItem();
            if (selectedItem instanceof ViewMode selectedMode) {
                applyViewMode(selectedMode);
            }
        });
        viewControls.add(viewSelector);

        topRow.add(titleStack, BorderLayout.CENTER);
        topRow.add(actionStack, BorderLayout.EAST);

        header.add(topRow, BorderLayout.NORTH);
        header.add(viewControls, BorderLayout.SOUTH);
        return header;
    }

    private JComponent buildBody() {
        JPanel wrapper = new JPanel(new BorderLayout(16, 0));
        wrapper.setBackground(panelBackground);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));

        JPanel listCard = new JPanel(new BorderLayout(0, 12));
        listCard.setBackground(cardBackground);
        listCard.setBorder(createCardBorder());

        entryList = new JList<>(entryListModel);
        entryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        entryList.setBackground(Styling.cardTintColour);
        entryList.setSelectionBackground(Styling.listSelectionColour);
        entryList.setSelectionForeground(accentColour);
        entryList.setCellRenderer(entryRenderer);

        entryList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                LibraryEntry selectedEntry = entryList.getSelectedValue();
                selectedEntryKey = selectedEntry == null ? "" : selectedEntry.key();
                updateSelectionDetails(selectedEntry);
            }
        });
        entryList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                handleEntryListClick(event);
            }
        });

        listScrollPane = new JScrollPane(entryList);
        listScrollPane.setBorder(BorderFactory.createLineBorder(cardBorder, 1));
        listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listScrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                if (viewMode == ViewMode.LARGE_ICONS) {
                    rebuildLargeIconGrid();
                }
            }
        });
        listCard.add(listScrollPane, BorderLayout.CENTER);

        JPanel detailsCard = new JPanel(new BorderLayout(0, 14));
        detailsCard.setBackground(cardBackground);
        detailsCard.setBorder(createCardBorder());
        detailsCard.setPreferredSize(new Dimension(290, 0));

        JPanel detailContent = new JPanel();
        detailContent.setOpaque(false);
        detailContent.setLayout(new javax.swing.BoxLayout(detailContent, javax.swing.BoxLayout.Y_AXIS));
        detailContent.setAlignmentX(Component.LEFT_ALIGNMENT);

        detailTitleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 20f));
        detailTitleLabel.setForeground(accentColour);
        detailTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailTitleLabel.setMaximumSize(new Dimension(240, Integer.MAX_VALUE));

        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewPanel.setBackground(Styling.displayFrameColour);
        previewPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        previewPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, detailPreviewHeight + 24));
        previewPanel.setPreferredSize(new Dimension(detailPreviewWidth + 20, detailPreviewHeight + 20));
        detailPreviewLabel.setPreferredSize(new Dimension(detailPreviewWidth, detailPreviewHeight));
        detailPreviewLabel.setForeground(mutedTextColour);
        previewPanel.add(detailPreviewLabel, BorderLayout.CENTER);

        JPanel detailGrid = new JPanel(new java.awt.GridLayout(1, 1, 0, 10));
        detailGrid.setOpaque(false);
        detailGrid.add(createDetailRow(UiText.LibraryWindow.VARIANT_TITLE, variantValueLabel));
        detailGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        detailContent.add(detailTitleLabel);
        detailContent.add(javax.swing.Box.createVerticalStrut(12));
        detailContent.add(previewPanel);
        detailContent.add(javax.swing.Box.createVerticalStrut(14));
        detailContent.add(detailGrid);

        detailsCard.add(detailContent, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);

        loadButton = createPrimaryButton(UiText.LibraryWindow.LOAD_BUTTON);
        loadButton.setEnabled(false);
        loadButton.addActionListener(event -> loadSelectedEntry(selectedEntry()));

        favouriteButton = createPrimaryIconButton(UiText.LibraryWindow.FAVOURITE_BUTTON);
        favouriteButton.setEnabled(false);
        favouriteButton.setToolTipText(UiText.LibraryWindow.FAVOURITE_BUTTON_TOOLTIP);
        favouriteButton.addActionListener(event -> toggleFavourite(selectedEntry()));

        infoButton = createPrimaryIconButton(UiText.LibraryWindow.INFO_BUTTON);
        infoButton.setEnabled(false);
        infoButton.setToolTipText(UiText.LibraryWindow.INFO_BUTTON_TOOLTIP);
        infoButton.addActionListener(event -> openInfoWindow(selectedEntry()));

        buttonPanel.add(loadButton);
        buttonPanel.add(favouriteButton);
        buttonPanel.add(infoButton);
        detailsCard.add(buttonPanel, BorderLayout.SOUTH);

        wrapper.add(listCard, BorderLayout.CENTER);
        wrapper.add(detailsCard, BorderLayout.EAST);

        applyViewMode(viewMode);
        return wrapper;
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

    private void refreshEntryList() {
        searchRefreshTimer.stop();
        String previousSelectedKey = selectedEntryKey;
        entryListModel.clear();

        List<LibraryEntry> entries = GameLibraryStore.GetEntries().stream()
                .filter(this::matchesFilterMode)
                .filter(this::matchesConsoleFilter)
                .filter(this::matchesSearchQuery)
                .sorted(resolveSortComparator())
                .toList();

        for (LibraryEntry entry : entries) {
            entryListModel.addElement(entry);
            requestArt(entry);
        }

        if (entryListModel.isEmpty()) {
            selectedEntryKey = "";
            updateSelectionDetails(null);
            if (entryList != null) {
                entryList.clearSelection();
            }
            rebuildLargeIconGrid();
            return;
        }

        int selectedIndex = 0;
        if (previousSelectedKey != null && !previousSelectedKey.isBlank()) {
            for (int index = 0; index < entryListModel.size(); index++) {
                if (entryListModel.get(index).key().equals(previousSelectedKey)) {
                    selectedIndex = index;
                    break;
                }
            }
        }

        LibraryEntry selectedEntry = entryListModel.getElementAt(selectedIndex);
        selectedEntryKey = selectedEntry.key();

        if (viewMode == ViewMode.LARGE_ICONS) {
            if (entryList != null) {
                entryList.clearSelection();
            }
            rebuildLargeIconGrid();
            updateSelectionDetails(selectedEntry);
        } else if (entryList != null) {
            entryList.setSelectedIndex(selectedIndex);
            entryList.ensureIndexIsVisible(selectedIndex);
        } else {
            updateSelectionDetails(selectedEntry);
        }
    }

    private void updateSelectionDetails(LibraryEntry entry) {
        if (entry == null) {
            String emptyText = currentEmptyText();
            detailTitleLabel.setText(asHeadingHtml(emptyText, 240));
            variantValueLabel.setText(asHtml(emptyText));
            setDetailPreviewPlaceholder(UiText.LibraryWindow.TITLE_SCREEN_MISSING);
            if (loadButton != null) {
                loadButton.setEnabled(false);
            }
            if (infoButton != null) {
                infoButton.setEnabled(false);
            }
            if (favouriteButton != null) {
                favouriteButton.setEnabled(false);
                favouriteButton.setText(UiText.LibraryWindow.FAVOURITE_BUTTON);
                favouriteButton.setToolTipText(UiText.LibraryWindow.FAVOURITE_BUTTON_TOOLTIP);
            }
            return;
        }

        detailTitleLabel.setText(asHeadingHtml(resolveDisplayName(entry), 240));
        variantValueLabel.setText(asHtml(UiText.LibraryWindow.VariantLabel(entry.patchNames())));
        updateDetailPreview(entry);
        if (loadButton != null) {
            loadButton.setEnabled(true);
        }
        if (infoButton != null) {
            infoButton.setEnabled(true);
        }
        if (favouriteButton != null) {
            favouriteButton.setEnabled(true);
            favouriteButton.setText(entry.favourite()
                    ? UiText.LibraryWindow.UNFAVOURITE_BUTTON
                    : UiText.LibraryWindow.FAVOURITE_BUTTON);
            favouriteButton.setToolTipText(entry.favourite()
                    ? UiText.LibraryWindow.UNFAVOURITE_BUTTON_TOOLTIP
                    : UiText.LibraryWindow.FAVOURITE_BUTTON_TOOLTIP);
        }
    }

    private void loadSelectedEntry(LibraryEntry entry) {
        if (entry == null || emulation == null) {
            return;
        }

        try {
            emulation.StartEmulation(entry.LoadRom());
            dispose();
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(mainWindow, exception.getMessage(),
                    UiText.GuiActions.LIBRARY_LOAD_ERROR_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleEntryListClick(MouseEvent event) {
        if (entryList == null || viewMode == ViewMode.LARGE_ICONS) {
            return;
        }

        int index = entryList.locationToIndex(event.getPoint());
        if (index < 0 || index >= entryListModel.size()) {
            return;
        }

        Rectangle cellBounds = entryList.getCellBounds(index, index);
        if (cellBounds == null || !cellBounds.contains(event.getPoint())) {
            return;
        }

        LibraryEntry entry = entryListModel.get(index);
        entryList.setSelectedIndex(index);
        selectedEntryKey = entry.key();

        if (event.getClickCount() == 2) {
            loadSelectedEntry(entry);
        }
    }

    private void applyViewMode(ViewMode nextViewMode) {
        viewMode = nextViewMode == null ? ViewMode.LIST : nextViewMode;
        rememberLibraryBrowserState(true);
        if (entryList == null) {
            return;
        }

        switch (viewMode) {
            case LIST -> {
                entryList.setLayoutOrientation(JList.VERTICAL);
                entryList.setVisibleRowCount(-1);
                entryList.setFixedCellHeight(52);
                entryList.setFixedCellWidth(-1);
            }
            case SMALL_ICONS -> {
                entryList.setLayoutOrientation(JList.VERTICAL);
                entryList.setVisibleRowCount(-1);
                entryList.setFixedCellHeight(84);
                entryList.setFixedCellWidth(-1);
            }
            case LARGE_ICONS -> {
                listScrollPane.setViewportView(largeIconWrapperPanel);
                rebuildLargeIconGrid();
                revalidateVisibleSelection();
                return;
            }
        }

        listScrollPane.setViewportView(entryList);

        entryList.revalidate();
        entryList.repaint();
        if (listScrollPane != null) {
            listScrollPane.revalidate();
            listScrollPane.repaint();
        }
        revalidateVisibleSelection();
    }

    private void applyFilterMode(FilterMode nextFilterMode) {
        filterMode = nextFilterMode == null ? FilterMode.ALL : nextFilterMode;
        rememberLibraryBrowserState(true);
        refreshEntryList();
    }

    private void applyConsoleFilter(RomConsoleFilter nextConsoleFilter) {
        consoleFilter = nextConsoleFilter == null ? RomConsoleFilter.ALL : nextConsoleFilter;
        rememberLibraryBrowserState(true);
        refreshEntryList();
    }

    private void applySortMode(SortMode nextSortMode) {
        sortMode = nextSortMode == null ? SortMode.ALPHABETICAL : nextSortMode;
        rememberLibraryBrowserState(true);
        refreshEntryList();
    }

    private void scheduleSearchQuery(String nextSearchQuery) {
        searchQuery = nextSearchQuery == null ? "" : nextSearchQuery.trim();
        rememberLibraryBrowserState(false);
        searchRefreshTimer.restart();
    }

    private boolean matchesFilterMode(LibraryEntry entry) {
        if (entry == null) {
            return false;
        }

        return switch (filterMode) {
            case ALL -> true;
            case FAVOURITES_ONLY -> entry.favourite();
            case ROM_HACKS_ONLY -> entry.patchNames() != null && !entry.patchNames().isEmpty();
            case BASE_ROMS_ONLY -> entry.patchNames() == null || entry.patchNames().isEmpty();
        };
    }

    private boolean matchesConsoleFilter(LibraryEntry entry) {
        return entry != null && consoleFilter.Matches(entry.cgbCompatible());
    }

    private boolean matchesSearchQuery(LibraryEntry entry) {
        if (entry == null || searchQuery.isBlank()) {
            return entry != null;
        }

        return WindowUiSupport.containsIgnoreCase(searchQuery,
                resolveDisplayName(entry),
                entry.sourceName(),
                entry.displayName(),
                entry.headerTitle(),
                String.join(" ", entry.patchNames()));
    }

    private void toggleFavourite(LibraryEntry entry) {
        if (entry == null) {
            return;
        }

        GameLibraryStore.SetFavourite(entry.key(), !entry.favourite());
        refreshEntryList();
    }

    private void openInfoWindow(LibraryEntry entry) {
        if (entry == null) {
            return;
        }

        try {
            new LibraryGameInfoWindow(entry, entry.LoadRom(),
                    () -> refreshArtworkForEntry(entry),
                    this::refreshEntryList);
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), UiText.LibraryWindow.INFO_LOAD_ERROR_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshArtworkForEntry(LibraryEntry entry) {
        if (entry == null) {
            return;
        }

        artCache.remove(entry.key());
        invalidateScaledArt(entry.key());
        titleScreenCache.remove(entry.key());
        titleScreenPreviewIconCache.remove(entry.key());
        artLoadingKeys.remove(entry.key());
        titleScreenLoadingKeys.remove(entry.key());
        requestArt(entry);
        if (selectedEntryKey != null && selectedEntryKey.equals(entry.key())) {
            updateDetailPreview(entry);
        }
        if (viewMode == ViewMode.LARGE_ICONS) {
            rebuildLargeIconGrid();
        } else if (entryList != null) {
            entryList.repaint();
        }
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
                            GameMetadataStore.RememberLibretroTitle(entry.SaveIdentity(),
                                    gameArtResult.matchedGameName());
                        }
                        artCache.put(entry.key(), gameArtResult.image());
                        invalidateScaledArt(entry.key());
                    });
                    artLoadingKeys.remove(entry.key());
                    SwingUtilities.invokeLater(() -> {
                        if (viewMode == ViewMode.LARGE_ICONS) {
                            rebuildLargeIconGrid();
                        } else if (entryList != null) {
                            repaintEntryCell(entry.key());
                        }
                    });
                })
                .exceptionally(exception -> {
                    artLoadingKeys.remove(entry.key());
                    SwingUtilities.invokeLater(() -> {
                        if (viewMode == ViewMode.LARGE_ICONS) {
                            rebuildLargeIconGrid();
                        } else if (entryList != null) {
                            repaintEntryCell(entry.key());
                        }
                    });
                    return null;
                });
    }

    private void updateDetailPreview(LibraryEntry entry) {
        if (entry == null) {
            setDetailPreviewPlaceholder(UiText.LibraryWindow.TITLE_SCREEN_MISSING);
            return;
        }

        BufferedImage cachedImage = titleScreenCache.get(entry.key());
        if (cachedImage != null) {
            setDetailPreviewImage(entry, cachedImage);
            return;
        }

        if (titleScreenLoadingKeys.contains(entry.key())) {
            setDetailPreviewPlaceholder(UiText.LibraryWindow.TITLE_SCREEN_LOADING);
            return;
        }

        setDetailPreviewPlaceholder(UiText.LibraryWindow.TITLE_SCREEN_LOADING);
        requestTitleScreen(entry);
    }

    private void requestTitleScreen(LibraryEntry entry) {
        if (entry == null || titleScreenCache.containsKey(entry.key()) || !titleScreenLoadingKeys.add(entry.key())) {
            return;
        }

        CompletableFuture
                .supplyAsync(() -> GameArtProvider.FindGameArt(entry.ArtDescriptor(), GameArtDisplayMode.TITLE_SCREEN))
                .thenAccept(result -> {
                    result.ifPresent(gameArtResult -> {
                        if (gameArtResult.matchedGameName() != null && !gameArtResult.matchedGameName().isBlank()) {
                            GameMetadataStore.RememberLibretroTitle(entry.SaveIdentity(),
                                    gameArtResult.matchedGameName());
                        }
                        titleScreenCache.put(entry.key(), gameArtResult.image());
                        titleScreenPreviewIconCache.remove(entry.key());
                    });
                    titleScreenLoadingKeys.remove(entry.key());
                    SwingUtilities.invokeLater(() -> refreshDetailPreview(entry));
                })
                .exceptionally(exception -> {
                    titleScreenLoadingKeys.remove(entry.key());
                    SwingUtilities.invokeLater(() -> refreshDetailPreview(entry));
                    return null;
                });
    }

    private void refreshDetailPreview(LibraryEntry entry) {
        if (entryListModel.isEmpty()) {
            return;
        }

        LibraryEntry selectedEntry = selectedEntry();
        if (selectedEntry != null && entry != null && selectedEntry.key().equals(entry.key())) {
            updateSelectionDetails(selectedEntry);
        }
    }

    private void setDetailPreviewPlaceholder(String text) {
        detailPreviewLabel.setIcon(null);
        detailPreviewLabel.setText(text);
        detailPreviewLabel.setFont(detailPreviewPlaceholderFont);
        detailPreviewLabel.setForeground(mutedTextColour);
    }

    private void setDetailPreviewImage(LibraryEntry entry, BufferedImage image) {
        ImageIcon previewIcon = detailPreviewIcon(entry, image);
        detailPreviewLabel.setIcon(previewIcon);
        detailPreviewLabel.setText(previewIcon == null ? UiText.LibraryWindow.TITLE_SCREEN_MISSING : "");
        detailPreviewLabel.setForeground(mutedTextColour);
    }

    private ImageIcon iconFor(LibraryEntry entry, int size) {
        return iconFor(entry, size, size);
    }

    private ImageIcon iconFor(LibraryEntry entry, int maxWidth, int maxHeight) {
        if (entry == null) {
            return null;
        }

        String entryKey = entry.key();
        BufferedImage source = artCache.get(entryKey);
        if (source == null) {
            return null;
        }

        return scaledArtIconCache.computeIfAbsent(
                entryKey + "|" + maxWidth + "x" + maxHeight,
                cacheKey -> {
                    BufferedImage scaled = GameArtScaler.ScaleToFit(source, maxWidth, maxHeight, true);
                    return scaled == null ? null : new ImageIcon(scaled);
                });
    }

    private void rebuildLargeIconGrid() {
        if (largeIconGridPanel == null) {
            return;
        }

        largeIconGridPanel.removeAll();
        largeIconGridPanel.setBackground(Styling.cardTintColour);
        largeIconWrapperPanel.setBackground(Styling.cardTintColour);

        if (viewMode != ViewMode.LARGE_ICONS) {
            largeIconGridPanel.revalidate();
            largeIconGridPanel.repaint();
            largeIconWrapperPanel.revalidate();
            largeIconWrapperPanel.repaint();
            return;
        }

        int availableWidth = listViewportWidth();
        if (availableWidth <= 0) {
            availableWidth = largeTilePreferredWidth;
        }

        int columns = resolveLargeIconColumnCount(availableWidth);
        int cellWidth = Math.max(1, availableWidth / columns);
        int tileSize = largeTileSize(cellWidth);
        int rows = Math.max(1, (int) Math.ceil(entryListModel.size() / (double) columns));

        largeIconGridPanel.setLayout(new GridLayout(0, columns, 0, 0));
        largeIconGridPanel.setPreferredSize(new Dimension(availableWidth, rows * tileSize));

        for (int index = 0; index < entryListModel.size(); index++) {
            LibraryEntry entry = entryListModel.get(index);
            largeIconGridPanel.add(createLargeIconTile(entry, tileSize));
        }

        largeIconGridPanel.revalidate();
        largeIconGridPanel.repaint();
        largeIconWrapperPanel.revalidate();
        largeIconWrapperPanel.repaint();
        if (listScrollPane != null) {
            listScrollPane.revalidate();
            listScrollPane.repaint();
        }
    }

    private JPanel createLargeIconTile(LibraryEntry entry, int tileSize) {
        final boolean[] hovered = { false };
        JPanel tile = new JPanel(new BorderLayout());
        tile.setOpaque(false);
        tile.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tile.setPreferredSize(new Dimension(tileSize, tileSize));
        tile.setToolTipText(resolveDisplayName(entry));

        int contentSize = Math.max(80, tileSize - 16);
        ImageIcon artIcon = iconFor(entry, contentSize, contentSize);
        JPanel contentPanel = new JPanel(new BorderLayout()) {
            @Override
            public void paint(Graphics graphics) {
                super.paint(graphics);
                Graphics2D graphics2d = (Graphics2D) graphics.create();
                boolean selectedState = isSelectedEntry(entry);
                if (hovered[0]) {
                    graphics2d.setColor(new Color(0, 0, 0, 70));
                    graphics2d.fillRect(0, 0, getWidth(), getHeight());
                }
                if (selectedState || hovered[0]) {
                    graphics2d.setColor(
                            selectedState ? Styling.sectionHighlightBorderColour : new Color(255, 255, 255, 90));
                    graphics2d.setStroke(new BasicStroke(selectedState ? 3f : 1.5f));
                    int inset = selectedState ? 2 : 1;
                    int width = Math.max(0, getWidth() - (inset * 2) - 1);
                    int height = Math.max(0, getHeight() - (inset * 2) - 1);
                    graphics2d.drawRect(inset, inset, width, height);
                }
                graphics2d.dispose();
            }
        };
        contentPanel.setOpaque(false);
        contentPanel.setPreferredSize(new Dimension(contentSize, contentSize));

        JComponent contentComponent;
        if (artIcon != null) {
            JLabel artLabel = new JLabel("", SwingConstants.CENTER);
            artLabel.setOpaque(false);
            artLabel.setHorizontalAlignment(SwingConstants.CENTER);
            artLabel.setVerticalAlignment(SwingConstants.CENTER);
            artLabel.setIcon(artIcon);
            artLabel.setText("");
            contentComponent = artLabel;
        } else {
            JPanel placeholderPanel = new JPanel(new BorderLayout());
            placeholderPanel.setOpaque(true);
            placeholderPanel.setBackground(Styling.displayFrameColour);
            placeholderPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1),
                    BorderFactory.createEmptyBorder(12, 10, 12, 10)));
            placeholderPanel.setPreferredSize(new Dimension(contentSize, contentSize));

            JLabel placeholderLabel = new JLabel("", SwingConstants.CENTER);
            placeholderLabel.setHorizontalAlignment(SwingConstants.CENTER);
            placeholderLabel.setVerticalAlignment(SwingConstants.CENTER);
            placeholderLabel.setForeground(mutedTextColour);
            placeholderLabel.setFont(largePlaceholderFont);
            String placeholderTitle = truncateToWidth(
                    resolveDisplayName(entry),
                    placeholderLabel.getFontMetrics(largePlaceholderFont),
                    Math.max(60, contentSize - 28));
            placeholderLabel.setText(artLoadingKeys.contains(entry.key())
                    ? UiText.LibraryWindow.ART_LOADING
                    : asRendererHtml(placeholderTitle, Math.max(60, contentSize - 28)));
            placeholderPanel.add(placeholderLabel, BorderLayout.CENTER);
            contentComponent = placeholderPanel;
        }
        contentPanel.add(contentComponent, BorderLayout.CENTER);

        MouseAdapter tileInteractionHandler = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                selectEntry(entry);
                if (event.getClickCount() == 2) {
                    loadSelectedEntry(entry);
                }
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                hovered[0] = true;
                contentPanel.repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                if (event.getSource() instanceof Component source
                        && isPointerInsideTile(source, event, tile)) {
                    return;
                }
                hovered[0] = false;
                contentPanel.repaint();
            }
        };
        installMouseListenerRecursively(tile, tileInteractionHandler);

        tile.add(contentPanel, BorderLayout.CENTER);
        return tile;
    }

    private void selectEntry(LibraryEntry entry) {
        selectedEntryKey = entry == null ? "" : entry.key();
        if (viewMode != ViewMode.LARGE_ICONS && entryList != null) {
            if (entry == null) {
                entryList.clearSelection();
            } else {
                for (int index = 0; index < entryListModel.size(); index++) {
                    if (entryListModel.get(index).key().equals(entry.key())) {
                        entryList.setSelectedIndex(index);
                        entryList.ensureIndexIsVisible(index);
                        break;
                    }
                }
            }
        } else {
            largeIconGridPanel.repaint();
            updateSelectionDetails(entry);
        }
    }

    private ImageIcon detailPreviewIcon(LibraryEntry entry, BufferedImage image) {
        if (entry == null || image == null) {
            return null;
        }

        return titleScreenPreviewIconCache.computeIfAbsent(
                entry.key(),
                key -> {
                    BufferedImage scaled = GameArtScaler.ScaleToFit(image, detailPreviewWidth, detailPreviewHeight,
                            true);
                    return scaled == null ? null : new ImageIcon(scaled);
                });
    }

    private void invalidateScaledArt(String entryKey) {
        if (entryKey == null || entryKey.isBlank()) {
            return;
        }
        scaledArtIconCache.keySet().removeIf(cacheKey -> cacheKey.startsWith(entryKey + "|"));
    }

    private void repaintEntryCell(String entryKey) {
        if (entryList == null || entryKey == null || entryKey.isBlank()) {
            return;
        }

        for (int index = 0; index < entryListModel.size(); index++) {
            if (!entryKey.equals(entryListModel.get(index).key())) {
                continue;
            }
            Rectangle cellBounds = entryList.getCellBounds(index, index);
            if (cellBounds != null) {
                entryList.repaint(cellBounds);
            } else {
                entryList.repaint();
            }
            return;
        }
    }

    private LibraryEntry selectedEntry() {
        if (selectedEntryKey == null || selectedEntryKey.isBlank()) {
            return entryList == null ? null : entryList.getSelectedValue();
        }

        for (int index = 0; index < entryListModel.size(); index++) {
            LibraryEntry entry = entryListModel.get(index);
            if (entry.key().equals(selectedEntryKey)) {
                return entry;
            }
        }
        return entryList == null ? null : entryList.getSelectedValue();
    }

    private boolean isSelectedEntry(LibraryEntry entry) {
        return entry != null && entry.key().equals(selectedEntryKey);
    }

    private void revalidateVisibleSelection() {
        LibraryEntry selectedEntry = selectedEntry();
        if (selectedEntry == null && !entryListModel.isEmpty()) {
            selectedEntry = entryListModel.getElementAt(0);
            selectedEntryKey = selectedEntry.key();
        }
        if (viewMode != ViewMode.LARGE_ICONS && entryList != null) {
            syncListSelectionFromKey();
        }
        updateSelectionDetails(selectedEntry);
    }

    private void syncListSelectionFromKey() {
        if (entryList == null) {
            return;
        }
        if (selectedEntryKey == null || selectedEntryKey.isBlank()) {
            entryList.clearSelection();
            return;
        }

        for (int index = 0; index < entryListModel.size(); index++) {
            if (entryListModel.get(index).key().equals(selectedEntryKey)) {
                if (entryList.getSelectedIndex() != index) {
                    entryList.setSelectedIndex(index);
                }
                return;
            }
        }

        entryList.clearSelection();
    }

    private int listViewportWidth() {
        if (listScrollPane != null && listScrollPane.getViewport() != null) {
            int width = listScrollPane.getViewport().getExtentSize().width;
            if (width > 0) {
                return width;
            }
        }
        return entryList == null ? 0 : entryList.getWidth();
    }

    private int resolveLargeIconColumnCount(int availableWidth) {
        if (availableWidth <= 0) {
            return 1;
        }

        int minColumns = Math.max(1, (int) Math.floor(availableWidth / (double) largeTileMaxWidth));
        int maxColumns = Math.max(1, availableWidth / largeTileMinWidth);
        int bestColumns = 1;
        double bestScore = Double.MAX_VALUE;

        for (int columns = minColumns; columns <= maxColumns; columns++) {
            double tileWidth = availableWidth / (double) columns;
            if (tileWidth < largeTileMinWidth || tileWidth > largeTileMaxWidth) {
                continue;
            }

            double score = Math.abs(tileWidth - largeTilePreferredWidth);
            if (columns == 1 && availableWidth >= largeTileMinWidth * 2) {
                score += 1000.0;
            }
            if (score < bestScore) {
                bestScore = score;
                bestColumns = columns;
            }
        }

        return Math.max(1, bestColumns);
    }

    private int largeTileSize(int tileWidth) {
        return Math.max(largeTileMinWidth, tileWidth);
    }

    private void installMouseListenerRecursively(Component component, MouseAdapter listener) {
        component.addMouseListener(listener);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                installMouseListenerRecursively(child, listener);
            }
        }
    }

    private boolean isPointerInsideTile(Component source, MouseEvent event, Component tile) {
        return tile.contains(SwingUtilities.convertPoint(source, event.getPoint(), tile));
    }

    private String resolveDisplayName(LibraryEntry entry) {
        if (entry == null) {
            return currentEmptyText();
        }
        String baseName = GameMetadataStore.GetLibretroTitle(entry.SaveIdentity())
                .orElseGet(() -> {
                    if (entry.sourceName() != null && !entry.sourceName().isBlank()) {
                        return entry.sourceName();
                    }
                    return entry.displayName();
                });
        return MainWindow.ApplyGameNameDisplayMode(baseName);
    }

    private String currentEmptyText() {
        if (!searchQuery.isBlank() || consoleFilter != RomConsoleFilter.ALL) {
            return UiText.LibraryWindow.EMPTY_FILTERED;
        }
        return switch (filterMode) {
            case ALL -> UiText.LibraryWindow.EMPTY;
            case FAVOURITES_ONLY -> UiText.LibraryWindow.EMPTY_FAVOURITES;
            case ROM_HACKS_ONLY -> UiText.LibraryWindow.EMPTY_ROM_HACKS;
            case BASE_ROMS_ONLY -> UiText.LibraryWindow.EMPTY_BASE_ROMS;
        };
    }

    private String asHtml(String value) {
        if (value == null || value.isBlank()) {
            return "<html><body style='width: 220px'>" + UiText.LibraryWindow.EMPTY + "</body></html>";
        }
        return "<html><body style='width: 220px'>" + WindowUiSupport.escapeHtml(value) + "</body></html>";
    }

    // Note: Slightly hack-y table workaround to get the text to wrap :P
    // Swing html formatting still uses 3.2, and as such div and bodies are not as
    // respected as a table layout
    private String asHeadingHtml(String value, int width) {
        String text = (value == null || value.isBlank())
                ? UiText.LibraryWindow.EMPTY
                : WindowUiSupport.escapeHtml(value);
        return "<html><table width='" + width + "'><tr><td>" + text + "</td></tr></table></html>";
    }

    // Same thing here for the game tiles
    // Interesting note if you're looking through this code as a dev
    private String asRendererHtml(String value, int width) {
        return "<html><table width='" + Math.max(60, width) + "'><tr><td align='center'>"
                + WindowUiSupport.escapeHtml(value == null ? "" : value)
                + "</td></tr></table></html>";
    }

    private String truncateToWidth(String value, FontMetrics metrics, int maxWidth) {
        if (value == null || value.isBlank() || metrics == null || maxWidth <= 0) {
            return value == null ? "" : value;
        }
        if (metrics.stringWidth(value) <= maxWidth) {
            return value;
        }

        String ellipsis = "...";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            String next = builder.toString() + value.charAt(index);
            if (metrics.stringWidth(next) + ellipsisWidth > maxWidth) {
                break;
            }
            builder.append(value.charAt(index));
        }
        return builder.isEmpty() ? ellipsis : builder + ellipsis;
    }

    private ViewMode resolveSavedViewMode() {
        String configuredMode = Settings.libraryViewMode;
        if (configuredMode == null || configuredMode.isBlank()) {
            return ViewMode.LIST;
        }
        try {
            return ViewMode.valueOf(configuredMode);
        } catch (IllegalArgumentException exception) {
            return ViewMode.LIST;
        }
    }

    private SortMode resolveSavedSortMode() {
        String configuredMode = Settings.librarySortMode;
        if (configuredMode == null || configuredMode.isBlank()) {
            return SortMode.ALPHABETICAL;
        }
        try {
            return SortMode.valueOf(configuredMode);
        } catch (IllegalArgumentException exception) {
            return SortMode.ALPHABETICAL;
        }
    }

    private FilterMode resolveSavedFilterMode() {
        String configuredMode = Settings.libraryFilterMode;
        if (configuredMode == null || configuredMode.isBlank()) {
            return FilterMode.ALL;
        }
        try {
            return FilterMode.valueOf(configuredMode);
        } catch (IllegalArgumentException exception) {
            return FilterMode.ALL;
        }
    }

    private RomConsoleFilter resolveSavedConsoleFilter() {
        String configuredFilter = Settings.libraryConsoleFilter;
        if (configuredFilter == null || configuredFilter.isBlank()) {
            return RomConsoleFilter.ALL;
        }
        try {
            return RomConsoleFilter.valueOf(configuredFilter);
        } catch (IllegalArgumentException exception) {
            return RomConsoleFilter.ALL;
        }
    }

    private String resolveSavedSearchQuery() {
        return Settings.librarySearchQuery == null ? "" : Settings.librarySearchQuery;
    }

    private void rememberLibraryBrowserState(boolean persist) {
        Settings.libraryViewMode = viewMode.name();
        Settings.libraryFilterMode = filterMode.name();
        Settings.libraryConsoleFilter = consoleFilter.name();
        Settings.librarySortMode = sortMode.name();
        Settings.librarySearchQuery = searchQuery == null ? "" : searchQuery;
        if (persist) {
            Config.Save();
        }
    }

    @Override
    public void dispose() {
        searchRefreshTimer.stop();
        rememberLibraryBrowserState(true);
        super.dispose();
    }

    private Comparator<LibraryEntry> resolveSortComparator() {
        Comparator<LibraryEntry> nameComparator = Comparator.comparing(this::resolveDisplayName,
                String.CASE_INSENSITIVE_ORDER);
        return switch (sortMode) {
            case RECENTLY_PLAYED -> Comparator.comparingLong(LibraryEntry::lastPlayedMillis)
                    .reversed()
                    .thenComparing(nameComparator);
            case NOT_PLAYED_FOR_A_WHILE -> Comparator.comparingLong(LibraryEntry::lastPlayedMillis)
                    .thenComparing(nameComparator);
            case ALPHABETICAL -> nameComparator.thenComparing(
                    Comparator.comparingLong(LibraryEntry::lastPlayedMillis).reversed());
        };
    }

    private javax.swing.border.Border createCardBorder() {
        return WindowUiSupport.createCardBorder(cardBorder, false, 18);
    }

    private JButton createPrimaryButton(String text) {
        return WindowUiSupport.createPrimaryButton(text, accentColour);
    }

    private JButton createPrimaryIconButton(String text) {
        return WindowUiSupport.createIconButton(text, accentColour);
    }

    private JButton createSecondaryButton(String text) {
        return WindowUiSupport.createSecondaryButton(text, accentColour, cardBorder);
    }

    private final class LibraryEntryRenderer extends JPanel implements ListCellRenderer<LibraryEntry> {
        private final JPanel artPanel = new JPanel(new BorderLayout());
        private final JLabel artLabel = new JLabel("", SwingConstants.CENTER);
        private final JLabel titleLabel = new JLabel();
        private final JLabel helperLabel = new JLabel();
        private final Font listTitleFont = Styling.menuFont.deriveFont(Font.BOLD, 13f);
        private final Font listHelperFont = Styling.menuFont.deriveFont(Font.PLAIN, 11f);
        private final Font smallIconArtFallbackFont = Styling.menuFont.deriveFont(Font.PLAIN, 10f);
        private final Font largeIconArtFallbackFont = Styling.menuFont.deriveFont(Font.PLAIN, 12f);

        private LibraryEntryRenderer() {
            setOpaque(true);
            artPanel.setOpaque(true);
            artPanel.setBackground(Styling.displayFrameColour);
            artPanel.setBorder(BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1));
            artLabel.setHorizontalAlignment(SwingConstants.CENTER);
            artLabel.setVerticalAlignment(SwingConstants.CENTER);
            artPanel.add(artLabel, BorderLayout.CENTER);

            titleLabel.setForeground(accentColour);
            helperLabel.setForeground(mutedTextColour);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends LibraryEntry> list, LibraryEntry value, int index,
                boolean isSelected, boolean cellHasFocus) {
            removeAll();

            Color background = isSelected ? Styling.listSelectionColour : Styling.cardTintColour;
            setBackground(background);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isSelected ? Styling.sectionHighlightBorderColour : cardBorder, 1,
                            true),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)));

            String gameTitle = resolveDisplayName(value);
            String helperText = value.favourite()
                    ? UiText.LibraryWindow.FAVOURITE_BADGE + " | "
                            + UiText.LibraryWindow.VariantLabel(value.patchNames())
                    : UiText.LibraryWindow.VariantLabel(value.patchNames());

            int availableTextWidth = Math.max(120, list.getWidth()
                    - (viewMode == ViewMode.SMALL_ICONS ? smallArtSize + 72 : 52));
            titleLabel.setText(truncateToWidth(gameTitle, list.getFontMetrics(listTitleFont), availableTextWidth));
            helperLabel.setText(truncateToWidth(helperText, list.getFontMetrics(listHelperFont), availableTextWidth));
            titleLabel.setForeground(accentColour);
            helperLabel.setForeground(mutedTextColour);

            if (viewMode == ViewMode.LIST) {
                setLayout(new BorderLayout(12, 0));
                titleLabel.setFont(listTitleFont);
                helperLabel.setFont(listHelperFont);

                JPanel textStack = new JPanel();
                textStack.setOpaque(false);
                textStack.setLayout(new javax.swing.BoxLayout(textStack, javax.swing.BoxLayout.Y_AXIS));
                textStack.add(titleLabel);
                textStack.add(helperLabel);

                add(textStack, BorderLayout.CENTER);
                return this;
            }

            int artSize = viewMode == ViewMode.SMALL_ICONS ? smallArtSize : largeArtSize;
            ImageIcon artIcon = iconFor(value, artSize);
            artPanel.setPreferredSize(new Dimension(artSize + 12, artSize + 12));

            if (artIcon != null) {
                artLabel.setIcon(artIcon);
                artLabel.setText("");
                artLabel.setForeground(Styling.fpsForegroundColour);
            } else {
                artLabel.setIcon(null);
                artLabel.setText(artLoadingKeys.contains(value.key())
                        ? UiText.LibraryWindow.ART_LOADING
                        : UiText.LibraryWindow.ART_MISSING);
                artLabel.setForeground(mutedTextColour);
                artLabel.setFont(viewMode == ViewMode.SMALL_ICONS
                        ? smallIconArtFallbackFont
                        : largeIconArtFallbackFont);
            }

            if (viewMode == ViewMode.SMALL_ICONS) {
                setLayout(new BorderLayout(12, 0));
                titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
                helperLabel.setHorizontalAlignment(SwingConstants.LEFT);
                titleLabel.setFont(listTitleFont);
                helperLabel.setFont(listHelperFont);

                JPanel textStack = new JPanel();
                textStack.setOpaque(false);
                textStack.setLayout(new javax.swing.BoxLayout(textStack, javax.swing.BoxLayout.Y_AXIS));
                textStack.add(titleLabel);
                textStack.add(helperLabel);

                add(artPanel, BorderLayout.WEST);
                add(textStack, BorderLayout.CENTER);
                return this;
            }

            setLayout(new BorderLayout(0, 8));
            titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
            helperLabel.setHorizontalAlignment(SwingConstants.CENTER);
            titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, viewMode == ViewMode.SMALL_ICONS ? 11f : 13f));
            helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, viewMode == ViewMode.SMALL_ICONS ? 10f : 11f));

            JPanel textStack = new JPanel();
            textStack.setOpaque(false);
            textStack.setLayout(new javax.swing.BoxLayout(textStack, javax.swing.BoxLayout.Y_AXIS));
            textStack.add(titleLabel);
            textStack.add(helperLabel);

            add(artPanel, BorderLayout.CENTER);
            add(textStack, BorderLayout.SOUTH);
            return this;
        }
    }
}
