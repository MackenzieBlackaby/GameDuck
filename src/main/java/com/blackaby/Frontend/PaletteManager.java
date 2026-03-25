package com.blackaby.Frontend;

import com.blackaby.Misc.Config;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Browser for loading and deleting saved DMG and GBC palettes.
 */
public final class PaletteManager extends DuckWindow {

    public enum PaletteKind {
        DMG,
        GBC
    }

    private final DefaultListModel<String> paletteListModel = new DefaultListModel<>();
    private final PaletteKind paletteKind;
    private final Runnable onPaletteChanged;

    /**
     * Creates the palette browser.
     *
     * @param onPaletteChanged callback fired after a palette is loaded
     */
    public PaletteManager(Runnable onPaletteChanged) {
        this(PaletteKind.DMG, onPaletteChanged);
    }

    /**
     * Creates the palette browser for the requested palette type.
     *
     * @param paletteKind      saved palette library to browse
     * @param onPaletteChanged callback fired after a palette is loaded
     */
    public PaletteManager(PaletteKind paletteKind, Runnable onPaletteChanged) {
        super(UiText.PaletteManager.WindowTitle(paletteKind == PaletteKind.GBC), 460, 360, false);
        this.paletteKind = paletteKind;
        this.onPaletteChanged = onPaletteChanged;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Styling.appBackgroundColour);

        add(BuildHeader(), BorderLayout.NORTH);
        add(BuildBody(), BorderLayout.CENTER);

        setVisible(true);
    }

    private JComponent BuildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setBackground(Styling.appBackgroundColour);
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));

        boolean gbcPalette = isGbcPalette();

        JLabel titleLabel = new JLabel(UiText.PaletteManager.Title(gbcPalette));
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(Styling.accentColour);

        JLabel subtitleLabel = new JLabel(UiText.PaletteManager.Subtitle(gbcPalette));
        subtitleLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        subtitleLabel.setForeground(Styling.mutedTextColour);

        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subtitleLabel, BorderLayout.CENTER);
        return header;
    }

    private JComponent BuildBody() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 16));
        wrapper.setBackground(Styling.appBackgroundColour);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));

        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setBackground(Styling.surfaceColour);
        card.setBorder(WindowUiSupport.createCardBorder(Styling.surfaceBorderColour, false, 18));

        JList<String> paletteList = new JList<>(paletteListModel);
        paletteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        paletteList.setFont(Styling.menuFont.deriveFont(13f));
        paletteList.setFixedCellHeight(30);
        paletteList.setBackground(Styling.cardTintColour);
        paletteList.setSelectionBackground(Styling.listSelectionColour);
        paletteList.setSelectionForeground(Styling.accentColour);

        RefreshPaletteList();

        if (!paletteListModel.isEmpty()) {
            paletteList.setSelectedIndex(0);
        }

        JScrollPane scrollPane = new JScrollPane(paletteList);
        scrollPane.setBorder(WindowUiSupport.createLineBorder(Styling.surfaceBorderColour));
        card.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);

        JButton importButton = WindowUiSupport.createSecondaryButton(
                UiText.PaletteManager.IMPORT_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        importButton.addActionListener(event -> importPalettes(paletteList));

        JButton deleteButton = WindowUiSupport.createSecondaryButton(
                UiText.PaletteManager.DELETE_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        deleteButton.addActionListener(event -> {
            String selectedPalette = paletteList.getSelectedValue();
            if (selectedPalette == null) {
                return;
            }

            int result = JOptionPane.showConfirmDialog(this,
                    UiText.PaletteManager.DeleteConfirmMessage(isGbcPalette(), selectedPalette),
                    UiText.PaletteManager.DeleteConfirmTitle(isGbcPalette()),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                DeletePalette(selectedPalette);
                RefreshPaletteList();
                if (!paletteListModel.isEmpty()) {
                    paletteList.setSelectedIndex(0);
                }
            }
        });

        JButton loadButton = WindowUiSupport.createPrimaryButton(UiText.PaletteManager.LOAD_BUTTON, Styling.accentColour);
        loadButton.addActionListener(event -> {
            String selectedPalette = paletteList.getSelectedValue();
            if (selectedPalette == null) {
                return;
            }

            if (LoadPalette(selectedPalette) && onPaletteChanged != null) {
                onPaletteChanged.run();
            }
            dispose();
        });

        buttonPanel.add(importButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(loadButton);
        card.add(buttonPanel, BorderLayout.SOUTH);

        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    private void RefreshPaletteList() {
        paletteListModel.clear();
        List<String> paletteNames = isGbcPalette()
                ? Config.GetSavedGbcPaletteNames()
                : Config.GetSavedPaletteNames();
        for (String paletteName : paletteNames) {
            paletteListModel.addElement(paletteName);
        }
    }

    private boolean LoadPalette(String paletteName) {
        return isGbcPalette()
                ? Config.LoadGbcPalette(paletteName)
                : Config.LoadPalette(paletteName);
    }

    private void DeletePalette(String paletteName) {
        if (isGbcPalette()) {
            Config.DeleteGbcPalette(paletteName);
            return;
        }
        Config.DeletePalette(paletteName);
    }

    private boolean isGbcPalette() {
        return paletteKind == PaletteKind.GBC;
    }

    private void importPalettes(JList<String> paletteList) {
        File importFile = promptForPaletteImportFile();
        if (importFile == null) {
            return;
        }

        try {
            var mergeResult = isGbcPalette()
                    ? Config.ImportGbcPalettes(importFile.toPath())
                    : Config.ImportPalettes(importFile.toPath());
            RefreshPaletteList();
            if (!paletteListModel.isEmpty()) {
                paletteList.setSelectedIndex(0);
            }
            JOptionPane.showMessageDialog(this,
                    UiText.PaletteManager.ImportSuccessMessage(
                            isGbcPalette(),
                            mergeResult.importedCount(),
                            mergeResult.duplicateCount()));
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.PaletteManager.IMPORT_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private File promptForPaletteImportFile() {
        FileDialog fileDialog = new FileDialog(this,
                UiText.PaletteManager.ImportDialogTitle(isGbcPalette()),
                FileDialog.LOAD);
        fileDialog.setAlwaysOnTop(true);
        fileDialog.setFilenameFilter((directory, name) -> name.toLowerCase().endsWith(".json"));
        fileDialog.setVisible(true);
        return fileDialog.getFiles().length == 0 ? null : fileDialog.getFiles()[0];
    }
}
