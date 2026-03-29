package com.blackaby.Frontend;

import com.blackaby.Backend.Helpers.GameArtProvider;
import com.blackaby.Misc.RomConsoleFilter;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

abstract class AbstractSaveManagerWindow<T> extends DuckWindow {

    protected static final int LIST_ART_SIZE = 56;
    protected static final int DETAIL_ART_WIDTH = 176;
    protected static final int DETAIL_ART_HEIGHT = 176;
    private static final int SEARCH_REFRESH_DELAY_MILLIS = 160;

    protected final MainWindow mainWindow;
    protected final Color panelBackground = Styling.appBackgroundColour;
    protected final Color cardBackground = Styling.surfaceColour;
    protected final Color cardBorder = Styling.surfaceBorderColour;
    protected final Color accentColour = Styling.accentColour;
    protected final Color mutedText = Styling.mutedTextColour;

    private final DefaultListModel<T> gameListModel = new DefaultListModel<>();
    private final Map<String, BufferedImage> artCache = new ConcurrentHashMap<>();
    private final Map<String, ImageIcon> scaledArtIconCache = new ConcurrentHashMap<>();
    private final Set<String> artLoadingKeys = ConcurrentHashMap.newKeySet();
    private final Timer searchRefreshTimer;

    private JList<T> gameList;
    private JLabel trackedGamesBadgeLabel;
    private RomConsoleFilter consoleFilter = RomConsoleFilter.ALL;
    private String searchQuery = "";

    protected AbstractSaveManagerWindow(String windowTitle, int width, int height, MainWindow mainWindow) {
        super(windowTitle, width, height, true);
        this.mainWindow = mainWindow;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(panelBackground);
        searchRefreshTimer = new Timer(SEARCH_REFRESH_DELAY_MILLIS, event -> refreshGameList());
        searchRefreshTimer.setRepeats(false);
    }

    protected final void initialiseWindow(String headerTitle, String headerSubtitle, JComponent body) {
        add(buildHeader(headerTitle, headerSubtitle), BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        refreshGameList();
        setVisible(true);
    }

    protected List<JComponent> createHeaderActions() {
        return List.of();
    }

    protected abstract String trackedGamesBadgeText(int totalCount);

    protected abstract List<T> loadEntries();

    protected abstract String entryKey(T entry);

    protected abstract boolean matchesConsoleFilter(T entry, RomConsoleFilter filter);

    protected abstract boolean matchesSearchQuery(T entry, String query);

    protected abstract GameArtProvider.GameArtDescriptor artDescriptor(T entry);

    protected abstract void rememberMatchedGameName(T entry, String matchedGameName);

    protected abstract void updateSelectionDetails(T entry);

    protected final DefaultListModel<T> gameListModel() {
        return gameListModel;
    }

    protected final JList<T> gameList() {
        return gameList;
    }

    protected final T selectedEntry() {
        return gameList == null ? null : gameList.getSelectedValue();
    }

    protected final RomConsoleFilter consoleFilter() {
        return consoleFilter;
    }

    protected final String searchQuery() {
        return searchQuery;
    }

    protected final boolean hasActiveFilters() {
        return !searchQuery.isBlank() || consoleFilter != RomConsoleFilter.ALL;
    }

    protected final void refreshGameList() {
        searchRefreshTimer.stop();
        T selected = selectedEntry();
        gameListModel.clear();

        List<T> allEntries = loadEntries();
        if (trackedGamesBadgeLabel != null) {
            trackedGamesBadgeLabel.setText(trackedGamesBadgeText(allEntries.size()));
        }

        allEntries.stream()
                .filter(entry -> matchesConsoleFilter(entry, consoleFilter))
                .filter(entry -> matchesSearchQuery(entry, searchQuery))
                .forEach(entry -> {
                    gameListModel.addElement(entry);
                    requestArt(entry);
                });

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
        if (selected != null) {
            for (int index = 0; index < gameListModel.size(); index++) {
                if (Objects.equals(entryKey(gameListModel.get(index)), entryKey(selected))) {
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

    protected final JPanel createGameListCard(ListCellRenderer<? super T> renderer, int preferredWidth) {
        JPanel listCard = new JPanel(new BorderLayout(0, 12));
        listCard.setBackground(cardBackground);
        listCard.setBorder(createCardBorder());
        if (preferredWidth > 0) {
            listCard.setPreferredSize(new Dimension(preferredWidth, 0));
        }

        gameList = new JList<>(gameListModel);
        gameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        gameList.setBackground(Styling.cardTintColour);
        gameList.setSelectionBackground(Styling.listSelectionColour);
        gameList.setSelectionForeground(accentColour);
        gameList.setFixedCellHeight(80);
        gameList.setCellRenderer(renderer);
        gameList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateSelectionDetails(gameList.getSelectedValue());
            }
        });

        JScrollPane scrollPane = new JScrollPane(gameList);
        scrollPane.setBorder(BorderFactory.createLineBorder(cardBorder, 1));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listCard.add(scrollPane, BorderLayout.CENTER);
        return listCard;
    }

    protected final void repaintGameList() {
        if (gameList != null) {
            gameList.repaint();
        }
    }

    protected final BufferedImage cachedArt(T entry) {
        return entry == null ? null : artCache.get(entryKey(entry));
    }

    protected final boolean isArtLoading(T entry) {
        return entry != null && artLoadingKeys.contains(entryKey(entry));
    }

    protected final JLabel createArtLabel() {
        JLabel label = new JLabel(UiText.LibraryWindow.ART_LOADING, SwingConstants.CENTER);
        label.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        label.setForeground(mutedText);
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        return label;
    }

    protected final void updateArtLabel(JLabel label, T entry, int width, int height, float fallbackFontSize) {
        BufferedImage art = cachedArt(entry);
        if (art != null) {
            label.setIcon(scaledArtIcon(entry, width, height));
            label.setText("");
            label.setForeground(Styling.fpsForegroundColour);
            return;
        }

        label.setIcon(null);
        label.setText(entry != null && isArtLoading(entry)
                ? UiText.LibraryWindow.ART_LOADING
                : UiText.LibraryWindow.ART_MISSING);
        label.setForeground(mutedText);
        label.setFont(Styling.menuFont.deriveFont(Font.PLAIN, fallbackFontSize));
    }

    protected final JPanel createArtPanel(JLabel artLabel, int width, int height) {
        JPanel artPanel = new JPanel(new BorderLayout());
        artPanel.setOpaque(true);
        artPanel.setBackground(Styling.displayFrameColour);
        artPanel.setBorder(BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1));
        artPanel.setPreferredSize(new Dimension(width, height));
        artPanel.add(artLabel, BorderLayout.CENTER);
        return artPanel;
    }

    protected final JPanel createDetailRow(String title, Component valueComponent) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);

        JLabel titleLabel = createSectionLabel(title, true);
        if (valueComponent instanceof JLabel valueLabel) {
            valueLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
            valueLabel.setForeground(mutedText);
            valueLabel.setVerticalAlignment(SwingConstants.TOP);
        }

        row.add(titleLabel, BorderLayout.NORTH);
        row.add(valueComponent, BorderLayout.CENTER);
        return row;
    }

    protected final JComponent createSectionHeader(String title, String helper) {
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        header.add(createSectionLabel(title, true), BorderLayout.NORTH);
        if (helper != null && !helper.isBlank()) {
            header.add(createSectionLabel(helper, false), BorderLayout.CENTER);
        }
        return header;
    }

    protected final JButton createPrimaryButton(String text) {
        return WindowUiSupport.createPrimaryButton(text, accentColour);
    }

    protected final JButton createSecondaryButton(String text) {
        return WindowUiSupport.createSecondaryButton(text, accentColour, cardBorder);
    }

    protected final Border createCardBorder() {
        return WindowUiSupport.createCardBorder(cardBorder, false, 18);
    }

    @Override
    public void dispose() {
        searchRefreshTimer.stop();
        super.dispose();
    }

    protected final String asHtml(String value, int width, String fallback) {
        String body = value == null || value.isBlank() ? fallback : WindowUiSupport.escapeHtml(value);
        return "<html><body style='width: " + width + "px'>" + body + "</body></html>";
    }

    protected final String asTitleHtml(String value, int width) {
        return "<html><body style='width: " + width + "px'>"
                + WindowUiSupport.escapeHtml(value == null ? "" : value)
                + "</body></html>";
    }

    protected final String truncateToWidth(String value, FontMetrics metrics, int maxWidth) {
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
            String next = builder + String.valueOf(value.charAt(index));
            if (metrics.stringWidth(next) + ellipsisWidth > maxWidth) {
                break;
            }
            builder.append(value.charAt(index));
        }
        return builder.isEmpty() ? ellipsis : builder + ellipsis;
    }

    protected final boolean containsIgnoreCase(String query, String... candidates) {
        return WindowUiSupport.containsIgnoreCase(query, candidates);
    }

    protected final String safeText(String value) {
        return value == null ? "" : value;
    }

    protected final File chooseLoadFile(String title, String extension) {
        FileDialog dialog = new FileDialog(this, title, FileDialog.LOAD);
        dialog.setAlwaysOnTop(true);
        String lowerExtension = extension.toLowerCase();
        dialog.setFilenameFilter((directory, name) -> name.toLowerCase().endsWith(lowerExtension));
        dialog.setVisible(true);
        return dialog.getFiles().length == 0 ? null : dialog.getFiles()[0];
    }

    protected final File chooseSaveFile(String title, Path defaultPath, String extension) {
        FileDialog dialog = new FileDialog(this, title, FileDialog.SAVE);
        dialog.setAlwaysOnTop(true);
        if (defaultPath != null) {
            if (defaultPath.getParent() != null) {
                dialog.setDirectory(defaultPath.getParent().toString());
            }
            dialog.setFile(defaultPath.getFileName().toString());
        }
        dialog.setVisible(true);

        if (dialog.getFiles().length == 0) {
            return null;
        }

        File selectedFile = dialog.getFiles()[0];
        if (selectedFile == null) {
            return null;
        }

        String lowerExtension = extension.toLowerCase();
        if (!selectedFile.getName().toLowerCase().endsWith(lowerExtension)) {
            selectedFile = new File(selectedFile.getParentFile(), selectedFile.getName() + extension);
        }
        return selectedFile;
    }

    protected abstract class ArtListRenderer extends JPanel implements ListCellRenderer<T> {
        private final JPanel artPanel = new JPanel(new BorderLayout());
        private final JLabel artLabel = createArtLabel();
        private final JLabel titleLabel = new JLabel();
        private final JLabel helperLabel = new JLabel();

        protected ArtListRenderer() {
            setOpaque(true);
            artPanel.setOpaque(true);
            artPanel.setBackground(Styling.displayFrameColour);
            artPanel.setBorder(BorderFactory.createLineBorder(Styling.displayFrameBorderColour, 1));
            artPanel.add(artLabel, BorderLayout.CENTER);
            titleLabel.setForeground(accentColour);
            helperLabel.setForeground(mutedText);
        }

        protected boolean truncateText() {
            return false;
        }

        protected abstract String titleText(T value);

        protected abstract String helperText(T value);

        @Override
        public Component getListCellRendererComponent(JList<? extends T> list, T value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            removeAll();

            setBackground(isSelected ? Styling.listSelectionColour : Styling.cardTintColour);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isSelected ? Styling.sectionHighlightBorderColour : cardBorder, 1, true),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)));

            updateArtLabel(artLabel, value, LIST_ART_SIZE, LIST_ART_SIZE, 10f);
            artPanel.setPreferredSize(new Dimension(LIST_ART_SIZE + 12, LIST_ART_SIZE + 12));

            Font titleFont = Styling.menuFont.deriveFont(Font.BOLD, 13f);
            Font helperFont = Styling.menuFont.deriveFont(Font.PLAIN, 11f);
            String title = titleText(value);
            String helper = helperText(value);
            if (truncateText()) {
                int availableTextWidth = Math.max(120, list.getWidth() - LIST_ART_SIZE - 72);
                title = truncateToWidth(title, list.getFontMetrics(titleFont), availableTextWidth);
                helper = truncateToWidth(helper, list.getFontMetrics(helperFont), availableTextWidth);
            }

            titleLabel.setText(title);
            titleLabel.setFont(titleFont);
            helperLabel.setText(helper);
            helperLabel.setFont(helperFont);

            JPanel textStack = new JPanel();
            textStack.setOpaque(false);
            textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
            textStack.add(titleLabel);
            textStack.add(helperLabel);

            setLayout(new BorderLayout(12, 0));
            add(artPanel, BorderLayout.WEST);
            add(textStack, BorderLayout.CENTER);
            return this;
        }
    }

    private JComponent buildHeader(String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout(0, 10));
        header.setBackground(panelBackground);
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));

        JPanel titleStack = new JPanel(new BorderLayout(0, 6));
        titleStack.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(accentColour);
        titleStack.add(titleLabel, BorderLayout.NORTH);

        if (subtitle != null && !subtitle.isBlank()) {
            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
            subtitleLabel.setForeground(mutedText);
            titleStack.add(subtitleLabel, BorderLayout.CENTER);
        }

        JPanel topRow = new JPanel(new BorderLayout(12, 0));
        topRow.setOpaque(false);

        JPanel actionStack = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionStack.setOpaque(false);
        trackedGamesBadgeLabel = createBadgeLabel(trackedGamesBadgeText(0));
        actionStack.add(trackedGamesBadgeLabel);
        createHeaderActions().forEach(actionStack::add);

        JButton refreshButton = createSecondaryButton(UiText.OptionsWindow.SAVE_MANAGER_REFRESH_BUTTON);
        refreshButton.addActionListener(event -> refreshGameList());
        actionStack.add(refreshButton);

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterRow.setOpaque(false);
        filterRow.add(createSectionLabel(UiText.Common.CONSOLE_TITLE, true));

        JComboBox<RomConsoleFilter> consoleSelector = new JComboBox<>(RomConsoleFilter.values());
        consoleSelector.setSelectedItem(consoleFilter);
        consoleSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        consoleSelector.addActionListener(event -> {
            Object selectedItem = consoleSelector.getSelectedItem();
            if (selectedItem instanceof RomConsoleFilter selectedConsoleFilter) {
                consoleFilter = selectedConsoleFilter;
                refreshGameList();
            }
        });
        filterRow.add(consoleSelector);

        filterRow.add(createSectionLabel(UiText.Common.SEARCH_TITLE, true));

        JTextField searchField = new JTextField(searchQuery, 16);
        searchField.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateSearch();
            }

            private void updateSearch() {
                searchQuery = searchField.getText() == null ? "" : searchField.getText().trim();
                searchRefreshTimer.restart();
            }
        });
        filterRow.add(searchField);

        topRow.add(titleStack, BorderLayout.CENTER);
        topRow.add(actionStack, BorderLayout.EAST);

        header.add(topRow, BorderLayout.NORTH);
        header.add(filterRow, BorderLayout.SOUTH);
        return header;
    }

    private JLabel createBadgeLabel(String text) {
        return WindowUiSupport.createBadgeLabel(text, accentColour);
    }

    private JLabel createSectionLabel(String text, boolean bold) {
        JLabel label = new JLabel(text);
        label.setFont(Styling.menuFont.deriveFont(bold ? Font.BOLD : Font.PLAIN, 12f));
        label.setForeground(bold ? accentColour : mutedText);
        return label;
    }

    private void requestArt(T entry) {
        if (entry == null) {
            return;
        }

        String key = entryKey(entry);
        if (key == null || artCache.containsKey(key) || !artLoadingKeys.add(key)) {
            return;
        }

        GameArtProvider.GameArtDescriptor descriptor = artDescriptor(entry);
        if (descriptor == null) {
            artLoadingKeys.remove(key);
            return;
        }

        CompletableFuture
                .supplyAsync(() -> GameArtProvider.FindGameArt(descriptor))
                .thenAccept(result -> {
                    result.ifPresent(gameArtResult -> {
                        if (gameArtResult.matchedGameName() != null && !gameArtResult.matchedGameName().isBlank()) {
                            rememberMatchedGameName(entry, gameArtResult.matchedGameName());
                        }
                        artCache.put(key, gameArtResult.image());
                        invalidateScaledArt(key);
                    });
                    finishArtLoad(entry, key);
                })
                .exceptionally(exception -> {
                    finishArtLoad(entry, key);
                    return null;
                });
    }

    private void finishArtLoad(T entry, String key) {
        artLoadingKeys.remove(key);
        SwingUtilities.invokeLater(() -> {
            repaintGameListCell(key);
            if (entry != null && Objects.equals(entry, selectedEntry())) {
                updateSelectionDetails(entry);
            }
        });
    }

    private ImageIcon scaledArtIcon(T entry, int width, int height) {
        String key = entryKey(entry);
        if (key == null) {
            return null;
        }

        return scaledArtIconCache.computeIfAbsent(
                key + "|" + width + "x" + height,
                cacheKey -> {
                    BufferedImage art = artCache.get(key);
                    if (art == null) {
                        return null;
                    }
                    BufferedImage scaled = GameArtScaler.ScaleToFit(art, width, height, true);
                    return scaled == null ? null : new ImageIcon(scaled);
                });
    }

    private void invalidateScaledArt(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        scaledArtIconCache.keySet().removeIf(cacheKey -> cacheKey.startsWith(key + "|"));
    }

    private void repaintGameListCell(String key) {
        if (gameList == null || key == null || key.isBlank()) {
            repaintGameList();
            return;
        }

        for (int index = 0; index < gameListModel.size(); index++) {
            if (!Objects.equals(key, entryKey(gameListModel.get(index)))) {
                continue;
            }
            java.awt.Rectangle cellBounds = gameList.getCellBounds(index, index);
            if (cellBounds != null) {
                gameList.repaint(cellBounds);
            } else {
                gameList.repaint();
            }
            return;
        }

        gameList.repaint();
    }

}
