package com.blackaby.Frontend;

import com.blackaby.Backend.Helpers.LibretroCheatProvider;
import com.blackaby.Backend.Platform.EmulatorCheat;
import com.blackaby.Backend.Platform.EmulatorGame;
import com.blackaby.Backend.Platform.EmulatorRuntime;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Dedicated editor for the currently loaded game's cheat list.
 */
public final class CheatManagerWindow extends DuckWindow {

    private final MainWindow mainWindow;
    private final EmulatorRuntime emulation;
    private final EmulatorGame loadedGame;
    private final Color panelBackground = Styling.appBackgroundColour;
    private final Color cardBackground = Styling.surfaceColour;
    private final Color cardBorder = Styling.surfaceBorderColour;
    private final Color accentColour = Styling.accentColour;
    private final Color mutedText = Styling.mutedTextColour;

    private final DefaultListModel<EmulatorCheat> cheatListModel = new DefaultListModel<>();
    private final JList<EmulatorCheat> cheatList = new JList<>(cheatListModel);
    private final JLabel cheatCountBadgeLabel = WindowUiSupport.createBadgeLabel(
            UiText.CheatManagerWindow.CheatCountBadge(0),
            Styling.accentColour);
    private final JLabel loadedGameValueLabel = new JLabel();
    private final JLabel statusLabel = new JLabel(UiText.CheatManagerWindow.STATUS_READY);
    private final JTextField nameField = new JTextField();
    private final JTextField addressField = new JTextField();
    private final JTextField valueField = new JTextField();
    private final JTextField compareField = new JTextField();
    private final JCheckBox enabledCheckBox = new JCheckBox(UiText.CheatManagerWindow.ENABLED_LABEL, true);

    private JButton newButton;
    private JButton saveButton;
    private JButton deleteButton;

    public CheatManagerWindow(MainWindow mainWindow, EmulatorRuntime emulation) {
        super(UiText.CheatManagerWindow.WINDOW_TITLE, 980, 680, true);
        this.mainWindow = mainWindow;
        this.emulation = emulation;
        this.loadedGame = emulation.GetLoadedGame();

        if (!emulation.HasLoadedGame()) {
            JOptionPane.showMessageDialog(mainWindow,
                    UiText.CheatManagerWindow.NO_GAME_LOADED,
                    UiText.CheatManagerWindow.WINDOW_TITLE,
                    JOptionPane.WARNING_MESSAGE);
            dispose();
            return;
        }

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(panelBackground);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        configureFields();
        reloadCheats(null);
        setVisible(true);
        requestLibretroCheatImport();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(panelBackground);
        header.setBorder(BorderFactory.createEmptyBorder(20, 22, 14, 22));

        JPanel textBlock = new JPanel(new BorderLayout(0, 6));
        textBlock.setOpaque(false);

        JLabel titleLabel = new JLabel(UiText.CheatManagerWindow.TITLE);
        titleLabel.setFont(Styling.titleFont);
        titleLabel.setForeground(accentColour);

        JLabel subtitleLabel = new JLabel(UiText.CheatManagerWindow.SUBTITLE);
        subtitleLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        subtitleLabel.setForeground(mutedText);

        textBlock.add(titleLabel, BorderLayout.NORTH);
        textBlock.add(subtitleLabel, BorderLayout.CENTER);

        JPanel badgePanel = new JPanel(new BorderLayout());
        badgePanel.setOpaque(false);
        badgePanel.add(cheatCountBadgeLabel, BorderLayout.NORTH);

        header.add(textBlock, BorderLayout.CENTER);
        header.add(badgePanel, BorderLayout.EAST);
        return header;
    }

    private JComponent buildBody() {
        JPanel wrapper = new JPanel(new BorderLayout(16, 0));
        wrapper.setBackground(panelBackground);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));

        JPanel listCard = new JPanel(new BorderLayout(0, 12));
        listCard.setBackground(cardBackground);
        listCard.setBorder(createCardBorder());
        listCard.setPreferredSize(new Dimension(340, 0));

        listCard.add(createSectionHeader(
                UiText.CheatManagerWindow.LIST_TITLE,
                UiText.CheatManagerWindow.LIST_HELPER), BorderLayout.NORTH);

        cheatList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cheatList.setBackground(Styling.cardTintColour);
        cheatList.setSelectionBackground(Styling.listSelectionColour);
        cheatList.setSelectionForeground(accentColour);
        cheatList.setCellRenderer(new CheatEntryRenderer());
        cheatList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                populateEditor(cheatList.getSelectedValue());
            }
        });

        JScrollPane listScrollPane = new JScrollPane(cheatList);
        listScrollPane.setBorder(BorderFactory.createLineBorder(cardBorder, 1));
        listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listCard.add(listScrollPane, BorderLayout.CENTER);

        JPanel editorCard = new JPanel(new BorderLayout(0, 14));
        editorCard.setBackground(cardBackground);
        editorCard.setBorder(createCardBorder());
        editorCard.add(createSectionHeader(
                UiText.CheatManagerWindow.EDITOR_TITLE,
                UiText.CheatManagerWindow.EDITOR_HELPER), BorderLayout.NORTH);
        editorCard.add(buildEditorForm(), BorderLayout.CENTER);
        editorCard.add(buildActionRow(), BorderLayout.SOUTH);

        wrapper.add(listCard, BorderLayout.WEST);
        wrapper.add(editorCard, BorderLayout.CENTER);
        return wrapper;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(panelBackground);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));

        statusLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        statusLabel.setForeground(mutedText);
        footer.add(statusLabel, BorderLayout.WEST);
        return footer;
    }

    private JComponent buildEditorForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0.0;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, 12, 12);

        form.add(createFieldLabel(UiText.CheatManagerWindow.LOADED_GAME_TITLE), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        loadedGameValueLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 14f));
        loadedGameValueLabel.setForeground(accentColour);
        form.add(loadedGameValueLabel, constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0.0;
        constraints.fill = GridBagConstraints.NONE;
        form.add(createFieldLabel(UiText.CheatManagerWindow.NAME_LABEL), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(nameField, constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0.0;
        constraints.fill = GridBagConstraints.NONE;
        form.add(createFieldLabel(UiText.CheatManagerWindow.ADDRESS_LABEL), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(wrapField(addressField, UiText.CheatManagerWindow.ADDRESS_HELPER), constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0.0;
        constraints.fill = GridBagConstraints.NONE;
        form.add(createFieldLabel(UiText.CheatManagerWindow.VALUE_LABEL), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(wrapField(valueField, UiText.CheatManagerWindow.VALUE_HELPER), constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0.0;
        constraints.fill = GridBagConstraints.NONE;
        form.add(createFieldLabel(UiText.CheatManagerWindow.COMPARE_LABEL), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(wrapField(compareField, UiText.CheatManagerWindow.COMPARE_HINT), constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0.0;
        constraints.fill = GridBagConstraints.NONE;
        form.add(createFieldLabel(UiText.CheatManagerWindow.ENABLED_LABEL), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        enabledCheckBox.setOpaque(false);
        enabledCheckBox.setForeground(accentColour);
        enabledCheckBox.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        form.add(enabledCheckBox, constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(6, 0, 0, 0);
        form.add(new JPanel(), constraints);

        return form;
    }

    private JComponent buildActionRow() {
        JPanel actions = new JPanel(new GridLayout(1, 3, 10, 0));
        actions.setOpaque(false);

        newButton = createSecondaryButton(UiText.CheatManagerWindow.NEW_BUTTON);
        newButton.addActionListener(event -> startNewCheat());

        saveButton = createPrimaryButton(UiText.CheatManagerWindow.SAVE_BUTTON);
        saveButton.addActionListener(event -> saveCurrentCheat());

        deleteButton = createSecondaryButton(UiText.CheatManagerWindow.DELETE_BUTTON);
        deleteButton.addActionListener(event -> deleteSelectedCheat());

        actions.add(newButton);
        actions.add(saveButton);
        actions.add(deleteButton);
        return actions;
    }

    private void configureFields() {
        styleTextField(nameField);
        styleTextField(addressField);
        styleTextField(valueField);
        styleTextField(compareField);
    }

    private void reloadCheats(String preferredKey) {
        loadedGameValueLabel.setText(mainWindow.GetCurrentLoadedRomDisplayName());
        cheatListModel.clear();
        List<EmulatorCheat> cheats = emulation.DescribeCurrentCheats();
        for (EmulatorCheat cheat : cheats) {
            cheatListModel.addElement(cheat);
        }

        cheatCountBadgeLabel.setText(UiText.CheatManagerWindow.CheatCountBadge(cheatListModel.size()));
        if (cheatListModel.isEmpty()) {
            cheatList.clearSelection();
            populateEditor(null);
            return;
        }

        if (preferredKey != null && !preferredKey.isBlank()) {
            for (int index = 0; index < cheatListModel.size(); index++) {
                if (preferredKey.equals(cheatListModel.get(index).key())) {
                    cheatList.setSelectedIndex(index);
                    cheatList.ensureIndexIsVisible(index);
                    return;
                }
            }
        }

        cheatList.setSelectedIndex(0);
        cheatList.ensureIndexIsVisible(0);
    }

    private void requestLibretroCheatImport() {
        if (loadedGame == null) {
            return;
        }

        CompletableFuture<LibretroCheatProvider.AutoImportResult> importTask =
                LibretroCheatProvider.AutoImportCheatsAsync(loadedGame);
        if (!importTask.isDone()) {
            statusLabel.setText(UiText.CheatManagerWindow.STATUS_SYNCING_LIBRETRO);
        }

        importTask
                .thenAccept(result -> SwingUtilities.invokeLater(() -> applyLibretroImportResult(result)))
                .exceptionally(exception -> {
                    SwingUtilities.invokeLater(() -> {
                        if (isDisplayable()) {
                            statusLabel.setText(UiText.CheatManagerWindow.STATUS_READY);
                        }
                    });
                    return null;
                });
    }

    private void applyLibretroImportResult(LibretroCheatProvider.AutoImportResult result) {
        if (!isDisplayable() || result == null) {
            return;
        }
        if (!isSameLoadedGame(mainWindow.GetCurrentLoadedGame())) {
            return;
        }

        switch (result.status()) {
            case IMPORTED -> {
                reloadCheats(null);
                statusLabel.setText(UiText.CheatManagerWindow.LibretroImportedMessage(
                        result.importedCount(),
                        result.matchedGameName()));
            }
            case UNCHANGED, ALREADY_IMPORTED, NOT_FOUND -> {
                if (cheatListModel.isEmpty()) {
                    statusLabel.setText(UiText.CheatManagerWindow.EMPTY_EDITOR);
                } else {
                    statusLabel.setText(UiText.CheatManagerWindow.STATUS_READY);
                }
            }
        }
    }

    private boolean isSameLoadedGame(EmulatorGame otherGame) {
        return loadedGame != null
                && otherGame != null
                && Objects.equals(loadedGame.sourcePath(), otherGame.sourcePath())
                && Objects.equals(loadedGame.sourceName(), otherGame.sourceName())
                && Objects.equals(loadedGame.displayName(), otherGame.displayName())
                && Objects.equals(loadedGame.patchSourcePaths(), otherGame.patchSourcePaths())
                && Objects.equals(loadedGame.patchNames(), otherGame.patchNames());
    }

    private void populateEditor(EmulatorCheat cheat) {
        if (cheat == null) {
            nameField.setText("");
            addressField.setText("");
            valueField.setText("");
            compareField.setText("");
            enabledCheckBox.setSelected(true);
            deleteButton.setEnabled(false);
            if (cheatListModel.isEmpty()) {
                statusLabel.setText(UiText.CheatManagerWindow.EMPTY_EDITOR);
            }
            return;
        }

        nameField.setText(cheat.label());
        addressField.setText(formatAddress(cheat.address()));
        valueField.setText(formatByte(cheat.value()));
        compareField.setText(cheat.compareValue() == null ? "" : formatByte(cheat.compareValue()));
        enabledCheckBox.setSelected(cheat.enabled());
        deleteButton.setEnabled(true);
        statusLabel.setText(UiText.CheatManagerWindow.STATUS_READY);
    }

    private void startNewCheat() {
        cheatList.clearSelection();
        nameField.setText("");
        addressField.setText("");
        valueField.setText("");
        compareField.setText("");
        enabledCheckBox.setSelected(true);
        deleteButton.setEnabled(false);
        statusLabel.setText(UiText.CheatManagerWindow.STATUS_NEW);
        nameField.requestFocusInWindow();
    }

    private void saveCurrentCheat() {
        if (!emulation.HasLoadedGame()) {
            JOptionPane.showMessageDialog(this,
                    UiText.CheatManagerWindow.NO_GAME_LOADED,
                    UiText.CheatManagerWindow.WINDOW_TITLE,
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            EmulatorCheat selectedCheat = cheatList.getSelectedValue();
            String key = selectedCheat == null || selectedCheat.key().isBlank()
                    ? generateDraftKey()
                    : selectedCheat.key();

            int address = parseHex(addressField.getText(), "Address", UiText.CheatManagerWindow.ADDRESS_HELPER, 0xFFFF);
            int value = parseHex(valueField.getText(), "Value", UiText.CheatManagerWindow.VALUE_HELPER, 0xFF);
            Integer compareValue = compareField.getText().trim().isBlank()
                    ? null
                    : Integer.valueOf(parseHex(compareField.getText(), "Compare", UiText.CheatManagerWindow.VALUE_HELPER, 0xFF));
            String label = nameField.getText().trim().isBlank()
                    ? UiText.CheatManagerWindow.DefaultCheatName(address, value)
                    : nameField.getText().trim();

            EmulatorCheat updatedCheat = new EmulatorCheat(
                    key,
                    label,
                    address,
                    compareValue,
                    value,
                    enabledCheckBox.isSelected());

            List<EmulatorCheat> cheats = currentCheatsFromModel();
            int selectedIndex = cheatList.getSelectedIndex();
            if (selectedIndex >= 0) {
                cheats.set(selectedIndex, updatedCheat);
            } else {
                cheats.add(updatedCheat);
            }

            emulation.UpdateCurrentCheats(cheats);
            reloadCheats(updatedCheat.key());
            statusLabel.setText(UiText.CheatManagerWindow.STATUS_SAVED);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.CheatManagerWindow.VALIDATION_ERROR_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedCheat() {
        EmulatorCheat selectedCheat = cheatList.getSelectedValue();
        if (selectedCheat == null) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                UiText.CheatManagerWindow.DeleteConfirmMessage(selectedCheat.label()),
                UiText.CheatManagerWindow.DELETE_CONFIRM_TITLE,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            List<EmulatorCheat> cheats = currentCheatsFromModel();
            cheats.removeIf(cheat -> selectedCheat.key().equals(cheat.key()));
            emulation.UpdateCurrentCheats(cheats);
            reloadCheats(null);
            statusLabel.setText(UiText.CheatManagerWindow.STATUS_DELETED);
        } catch (IllegalStateException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.CheatManagerWindow.WINDOW_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<EmulatorCheat> currentCheatsFromModel() {
        List<EmulatorCheat> cheats = new ArrayList<>(cheatListModel.size());
        for (int index = 0; index < cheatListModel.size(); index++) {
            cheats.add(cheatListModel.getElementAt(index));
        }
        return cheats;
    }

    private int parseHex(String text, String fieldName, String expectedFormat, int maxValue) {
        String cleaned = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
        if (cleaned.startsWith("0X")) {
            cleaned = cleaned.substring(2);
        }
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException(UiText.CheatManagerWindow.MissingHexMessage(fieldName, expectedFormat));
        }
        if (!cleaned.matches("[0-9A-F]+")) {
            throw new IllegalArgumentException(UiText.CheatManagerWindow.InvalidHexMessage(fieldName, expectedFormat));
        }

        try {
            int value = Integer.parseInt(cleaned, 16);
            if (value < 0 || value > maxValue) {
                throw new IllegalArgumentException(UiText.CheatManagerWindow.InvalidHexMessage(fieldName, expectedFormat));
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(UiText.CheatManagerWindow.InvalidHexMessage(fieldName, expectedFormat));
        }
    }

    private String generateDraftKey() {
        return "cheat-" + Long.toHexString(System.nanoTime());
    }

    private String formatAddress(int address) {
        return String.format("%04X", address & 0xFFFF);
    }

    private String formatByte(int value) {
        return String.format("%02X", value & 0xFF);
    }

    private JPanel wrapField(JTextField field, String helperText) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.add(field, BorderLayout.NORTH);

        JLabel helperLabel = new JLabel(helperText);
        helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 11f));
        helperLabel.setForeground(mutedText);
        panel.add(helperLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        label.setForeground(accentColour);
        return label;
    }

    private JPanel createSectionHeader(String title, String helperText) {
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(accentColour);

        JLabel helperLabel = new JLabel(helperText);
        helperLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helperLabel.setForeground(mutedText);

        header.add(titleLabel, BorderLayout.NORTH);
        header.add(helperLabel, BorderLayout.CENTER);
        return header;
    }

    private JButton createPrimaryButton(String text) {
        return WindowUiSupport.createPrimaryButton(text, accentColour);
    }

    private JButton createSecondaryButton(String text) {
        return WindowUiSupport.createSecondaryButton(text, accentColour, cardBorder);
    }

    private void styleTextField(JTextField field) {
        field.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        field.setForeground(accentColour);
        field.setBackground(Styling.surfaceColour);
        field.setCaretColor(accentColour);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(cardBorder, 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    }

    private javax.swing.border.Border createCardBorder() {
        return WindowUiSupport.createCardBorder(cardBorder, false, 18);
    }

    private final class CheatEntryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof EmulatorCheat cheat) {
                String summary = cheat.compareValue() == null
                        ? String.format("%04X -> %02X", cheat.address(), cheat.value())
                        : String.format("%04X: %02X if %02X", cheat.address(), cheat.value(), cheat.compareValue());
                String statusPrefix = cheat.enabled() ? "[On] " : "[Off] ";
                label.setText("<html><b>" + escapeHtml(statusPrefix + cheat.label())
                        + "</b><br><span style='font-size:10px;'>"
                        + escapeHtml(summary)
                        + "</span></html>");
            } else {
                label.setText("");
            }
            label.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            label.setVerticalAlignment(SwingConstants.CENTER);
            return label;
        }

        private String escapeHtml(String value) {
            return value == null ? "" : value
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        }
    }
}
