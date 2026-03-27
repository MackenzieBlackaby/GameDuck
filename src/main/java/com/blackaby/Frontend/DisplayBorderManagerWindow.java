package com.blackaby.Frontend;

import com.blackaby.Frontend.Borders.DisplayBorderManager;
import com.blackaby.Frontend.Borders.DisplayBorderPreviewRenderer;
import com.blackaby.Frontend.Borders.LoadedDisplayBorder;
import com.blackaby.Misc.Settings;
import com.blackaby.Misc.UiText;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * GUI manager for PNG display borders.
 */
public final class DisplayBorderManagerWindow extends DuckWindow {

    private final Runnable onBordersChanged;
    private final DefaultListModel<LoadedDisplayBorder> borderListModel = new DefaultListModel<>();
    private final JList<LoadedDisplayBorder> borderList = new JList<>(borderListModel);
    private final JLabel nameValueLabel = new JLabel();
    private final JLabel sourceValueLabel = new JLabel();
    private final JLabel pathValueLabel = new JLabel();
    private final JLabel cutoutValueLabel = new JLabel();
    private final JLabel statusValueLabel = new JLabel();
    private final JLabel previewStatusLabel = new JLabel();
    private final ImagePreviewSurface sourcePreviewSurface = new ImagePreviewSurface(
            UiText.BorderManagerWindow.PREVIEW_UNAVAILABLE,
            280,
            210);
    private final ImagePreviewSurface outputPreviewSurface = new ImagePreviewSurface(
            UiText.BorderManagerWindow.PREVIEW_UNAVAILABLE,
            280,
            210);
    private boolean updatingSelection;

    public DisplayBorderManagerWindow(Runnable onBordersChanged) {
        super(UiText.BorderManagerWindow.WINDOW_TITLE, 1080, 720, true);
        this.onBordersChanged = onBordersChanged;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Styling.appBackgroundColour);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);

        refreshBorderList(Settings.displayBorderId);
        setVisible(true);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setBackground(Styling.appBackgroundColour);
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));

        JLabel titleLabel = new JLabel(UiText.BorderManagerWindow.TITLE);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(Styling.accentColour);

        JLabel subtitleLabel = new JLabel(UiText.BorderManagerWindow.SUBTITLE);
        subtitleLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        subtitleLabel.setForeground(Styling.mutedTextColour);

        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subtitleLabel, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildBody() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Styling.appBackgroundColour);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildBorderListCard(), buildDetailCard());
        splitPane.setResizeWeight(0.28);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setOpaque(false);

        wrapper.add(splitPane, BorderLayout.CENTER);
        return wrapper;
    }

    private JComponent buildBorderListCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(Styling.surfaceColour);
        card.setBorder(WindowUiSupport.createCardBorder(Styling.surfaceBorderColour, false, 18));

        JLabel titleLabel = new JLabel(UiText.BorderManagerWindow.BORDER_LIST_TITLE);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Styling.accentColour);
        card.add(titleLabel, BorderLayout.NORTH);

        borderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        borderList.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        borderList.setFixedCellHeight(32);
        borderList.setBackground(Styling.cardTintColour);
        borderList.setSelectionBackground(Styling.listSelectionColour);
        borderList.setSelectionForeground(Styling.accentColour);
        borderList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof LoadedDisplayBorder border) {
                    label.setText(border.displayName() + "  |  " + border.sourceLabel());
                }
                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return label;
            }
        });
        borderList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !updatingSelection) {
                refreshSelectedBorder();
            }
        });

        JScrollPane scrollPane = new JScrollPane(borderList);
        scrollPane.setBorder(WindowUiSupport.createLineBorder(Styling.surfaceBorderColour));
        card.add(scrollPane, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);

        JButton importButton = WindowUiSupport.createSecondaryButton(
                UiText.BorderManagerWindow.IMPORT_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        importButton.addActionListener(event -> importBorder());

        JButton deleteButton = WindowUiSupport.createSecondaryButton(
                UiText.BorderManagerWindow.DELETE_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        deleteButton.addActionListener(event -> deleteSelectedBorder());

        actions.add(importButton);
        actions.add(deleteButton);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildDetailCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(Styling.surfaceColour);
        card.setBorder(WindowUiSupport.createCardBorder(Styling.surfaceBorderColour, false, 18));

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        content.add(buildPreviewCard(), BorderLayout.NORTH);
        content.add(buildMetadataCard(), BorderLayout.CENTER);
        content.add(buildActionsPanel(), BorderLayout.SOUTH);

        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildPreviewCard() {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Styling.cardTintColour);
        card.setBorder(WindowUiSupport.createCardBorder(Styling.surfaceBorderColour, false, 14));

        JPanel text = new JPanel();
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        text.setOpaque(false);

        JLabel title = new JLabel(UiText.BorderManagerWindow.PREVIEW_TITLE);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        title.setForeground(Styling.accentColour);

        JLabel helper = new JLabel(UiText.BorderManagerWindow.PREVIEW_HELPER);
        helper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helper.setForeground(Styling.mutedTextColour);

        text.add(title);
        text.add(javax.swing.Box.createVerticalStrut(4));
        text.add(helper);

        JPanel previewGrid = new JPanel(new java.awt.GridLayout(1, 2, 10, 0));
        previewGrid.setOpaque(false);
        previewGrid.add(createPreviewPane(UiText.BorderManagerWindow.SOURCE_PREVIEW_LABEL, sourcePreviewSurface));
        previewGrid.add(createPreviewPane(UiText.BorderManagerWindow.OUTPUT_PREVIEW_LABEL, outputPreviewSurface));

        previewStatusLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        previewStatusLabel.setForeground(Styling.mutedTextColour);

        card.add(text, BorderLayout.NORTH);
        card.add(previewGrid, BorderLayout.CENTER);
        card.add(previewStatusLabel, BorderLayout.SOUTH);
        return card;
    }

    private JComponent createPreviewPane(String titleText, ImagePreviewSurface surface) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        JLabel title = new JLabel(titleText);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        title.setForeground(Styling.accentColour);

        surface.setBorder(WindowUiSupport.createLineBorder(Styling.surfaceBorderColour));
        panel.add(title, BorderLayout.NORTH);
        panel.add(surface, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildMetadataCard() {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Styling.cardTintColour);
        card.setBorder(WindowUiSupport.createCardBorder(Styling.surfaceBorderColour, false, 14));

        JPanel fields = new JPanel();
        fields.setLayout(new javax.swing.BoxLayout(fields, javax.swing.BoxLayout.Y_AXIS));
        fields.setOpaque(false);

        nameValueLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        nameValueLabel.setForeground(Styling.accentColour);
        sourceValueLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        sourceValueLabel.setForeground(Styling.accentColour);
        pathValueLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        pathValueLabel.setForeground(Styling.accentColour);
        cutoutValueLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        cutoutValueLabel.setForeground(Styling.accentColour);
        statusValueLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        statusValueLabel.setForeground(Styling.mutedTextColour);

        fields.add(createFieldCard(UiText.BorderManagerWindow.NAME_LABEL, nameValueLabel));
        fields.add(javax.swing.Box.createVerticalStrut(8));
        fields.add(createFieldCard(UiText.BorderManagerWindow.SOURCE_LABEL, sourceValueLabel));
        fields.add(javax.swing.Box.createVerticalStrut(8));
        fields.add(createFieldCard(UiText.BorderManagerWindow.PATH_LABEL, pathValueLabel));
        fields.add(javax.swing.Box.createVerticalStrut(8));
        fields.add(createFieldCard(UiText.BorderManagerWindow.CUTOUT_LABEL, cutoutValueLabel));
        fields.add(javax.swing.Box.createVerticalStrut(8));
        fields.add(createFieldCard(UiText.BorderManagerWindow.STATUS_LABEL, statusValueLabel));

        card.add(fields, BorderLayout.NORTH);
        return card;
    }

    private JComponent buildActionsPanel() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton exportButton = WindowUiSupport.createSecondaryButton(
                UiText.BorderManagerWindow.EXPORT_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        exportButton.addActionListener(event -> exportSelectedBorder());

        JButton reloadButton = WindowUiSupport.createSecondaryButton(
                UiText.BorderManagerWindow.RELOAD_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        reloadButton.addActionListener(event -> {
            LoadedDisplayBorder selectedBorder = borderList.getSelectedValue();
            reloadBorderLibrary();
            refreshBorderList(selectedBorder == null ? null : selectedBorder.id());
        });

        JButton revealFolderButton = WindowUiSupport.createSecondaryButton(
                UiText.BorderManagerWindow.REVEAL_FOLDER_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        revealFolderButton.addActionListener(event -> revealBorderFolder());

        JButton closeButton = WindowUiSupport.createSecondaryButton(
                UiText.BorderManagerWindow.CLOSE_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        closeButton.addActionListener(event -> dispose());

        actions.add(exportButton);
        actions.add(reloadButton);
        actions.add(revealFolderButton);
        actions.add(closeButton);
        return actions;
    }

    private JComponent createFieldCard(String labelText, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        label.setForeground(Styling.accentColour);

        panel.add(label, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private void refreshBorderList(String preferredBorderId) {
        borderListModel.clear();
        List<LoadedDisplayBorder> availableBorders = DisplayBorderManager.GetAvailableBorders();
        for (LoadedDisplayBorder border : availableBorders) {
            borderListModel.addElement(border);
        }

        if (borderListModel.isEmpty()) {
            refreshSelectedBorder();
            setStatus(UiText.BorderManagerWindow.EMPTY);
            return;
        }

        int selectedIndex = 0;
        if (preferredBorderId != null && !preferredBorderId.isBlank()) {
            for (int index = 0; index < borderListModel.size(); index++) {
                if (borderListModel.get(index).id().equalsIgnoreCase(preferredBorderId)) {
                    selectedIndex = index;
                    break;
                }
            }
        }

        updatingSelection = true;
        try {
            borderList.setSelectedIndex(selectedIndex);
        } finally {
            updatingSelection = false;
        }
        refreshSelectedBorder();
    }

    private void refreshSelectedBorder() {
        LoadedDisplayBorder selectedBorder = borderList.getSelectedValue();
        if (selectedBorder == null) {
            nameValueLabel.setText("");
            sourceValueLabel.setText("");
            pathValueLabel.setText("");
            cutoutValueLabel.setText(UiText.BorderManagerWindow.CUTOUT_NOT_AVAILABLE);
            sourcePreviewSurface.setImage(null);
            outputPreviewSurface.setImage(null);
            previewStatusLabel.setText(UiText.BorderManagerWindow.EMPTY);
            return;
        }

        DisplayBorderPreviewRenderer.PreviewImages previewImages = DisplayBorderPreviewRenderer.render(selectedBorder);
        nameValueLabel.setText(selectedBorder.displayName());
        sourceValueLabel.setText(selectedBorder.sourceLabel());
        pathValueLabel.setText(selectedBorder.sourcePathText().isBlank()
                ? UiText.BorderManagerWindow.BUILT_IN_PATH
                : selectedBorder.sourcePathText());
        cutoutValueLabel.setText(selectedBorder.screenRect() == null
                ? UiText.BorderManagerWindow.CUTOUT_NOT_AVAILABLE
                : selectedBorder.screenRect().width + " x " + selectedBorder.screenRect().height);
        sourcePreviewSurface.setImage(previewImages.sourceImage());
        outputPreviewSurface.setImage(previewImages.previewImage());
        previewStatusLabel.setText(selectedBorder.image() == null
                ? UiText.BorderManagerWindow.PreviewStatus(0, 0)
                : UiText.BorderManagerWindow.PreviewStatus(
                        selectedBorder.image().getWidth(),
                        selectedBorder.image().getHeight()));
        updateStatusLabel();
    }

    private void importBorder() {
        FileDialog fileDialog = new FileDialog(this, UiText.BorderManagerWindow.IMPORT_DIALOG_TITLE, FileDialog.LOAD);
        fileDialog.setAlwaysOnTop(true);
        fileDialog.setFilenameFilter((directory, name) -> name.toLowerCase().endsWith(".png"));
        fileDialog.setVisible(true);
        if (fileDialog.getFiles().length == 0) {
            return;
        }

        File importFile = fileDialog.getFiles()[0];
        if (importFile == null) {
            return;
        }

        try {
            DisplayBorderManager.InspectExternalBorder(importFile.toPath());
            Path destinationPath = resolveImportDestination(importFile.toPath());
            Files.createDirectories(destinationPath.getParent());
            Files.copy(importFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
            reloadBorderLibrary();
            refreshBorderList(DisplayBorderManager.InspectExternalBorder(destinationPath).id());
            setStatus(UiText.BorderManagerWindow.ImportedStatus(destinationPath.getFileName().toString()));
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.BorderManagerWindow.IMPORT_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportSelectedBorder() {
        LoadedDisplayBorder selectedBorder = borderList.getSelectedValue();
        if (selectedBorder == null || selectedBorder.image() == null) {
            return;
        }

        FileDialog fileDialog = new FileDialog(this, UiText.BorderManagerWindow.EXPORT_DIALOG_TITLE, FileDialog.SAVE);
        fileDialog.setAlwaysOnTop(true);
        fileDialog.setFile(selectedBorder.id() + ".png");
        fileDialog.setVisible(true);
        if (fileDialog.getFile() == null || fileDialog.getFile().isBlank()) {
            return;
        }

        try {
            Path destinationPath = resolveExportPath(fileDialog.getDirectory(), fileDialog.getFile());
            Files.createDirectories(destinationPath.getParent());
            ImageIO.write(selectedBorder.image(), "png", destinationPath.toFile());
            setStatus(UiText.BorderManagerWindow.ExportedStatus(destinationPath.toString()));
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.BorderManagerWindow.EXPORT_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedBorder() {
        LoadedDisplayBorder selectedBorder = borderList.getSelectedValue();
        if (selectedBorder == null) {
            return;
        }
        if (selectedBorder.sourcePath() == null) {
            JOptionPane.showMessageDialog(this,
                    UiText.BorderManagerWindow.BUILT_IN_DELETE_WARNING,
                    UiText.Common.WARNING_TITLE,
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                UiText.BorderManagerWindow.DeleteConfirmMessage(selectedBorder.displayName()),
                UiText.BorderManagerWindow.DELETE_CONFIRM_TITLE,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            Files.deleteIfExists(selectedBorder.sourcePath());
            reloadBorderLibrary();
            refreshBorderList(null);
            setStatus(UiText.BorderManagerWindow.DeletedStatus(selectedBorder.displayName()));
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.BorderManagerWindow.DELETE_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reloadBorderLibrary() {
        DisplayBorderManager.Reload();
        if (onBordersChanged != null) {
            onBordersChanged.run();
        }
        updateStatusLabel();
    }

    private void revealBorderFolder() {
        try {
            Files.createDirectories(DisplayBorderManager.BorderDirectory());
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(DisplayBorderManager.BorderDirectory().toFile());
            }
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.BorderManagerWindow.OPEN_FOLDER_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateStatusLabel() {
        List<String> loadErrors = DisplayBorderManager.GetLoadErrors();
        statusValueLabel.setText(loadErrors.isEmpty()
                ? UiText.BorderManagerWindow.STATUS_OK
                : UiText.BorderManagerWindow.ReloadWarningStatus(loadErrors.size()));
        statusValueLabel.setToolTipText(loadErrors.isEmpty() ? null : String.join("\n", loadErrors));
    }

    private void setStatus(String text) {
        statusValueLabel.setText(text == null ? "" : text);
        statusValueLabel.setToolTipText(null);
    }

    private Path resolveImportDestination(Path importPath) {
        String fileName = importPath == null ? "" : importPath.getFileName().toString();
        if (fileName.isBlank() || !fileName.toLowerCase().endsWith(".png")) {
            throw new IllegalArgumentException("Only .png display borders can be imported.");
        }

        Path borderDirectory = DisplayBorderManager.BorderDirectory();
        Path candidate = borderDirectory.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }

        String baseName = fileName.substring(0, fileName.length() - 4);
        for (int index = 2; index < 1000; index++) {
            candidate = borderDirectory.resolve(baseName + "-" + index + ".png");
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unable to find a free filename for the imported border.");
    }

    private Path resolveExportPath(String directory, String fileName) {
        String sanitizedFileName = fileName == null ? "" : fileName.trim();
        if (!sanitizedFileName.toLowerCase().endsWith(".png")) {
            sanitizedFileName = sanitizedFileName + ".png";
        }
        return Path.of(directory == null || directory.isBlank() ? "." : directory, sanitizedFileName)
                .toAbsolutePath()
                .normalize();
    }
}
