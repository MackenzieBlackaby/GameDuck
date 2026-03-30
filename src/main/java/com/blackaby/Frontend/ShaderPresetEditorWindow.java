package com.blackaby.Frontend;

import com.blackaby.Frontend.Shaders.DisplayShaderManager;
import com.blackaby.Frontend.Shaders.ShaderPresetDocument;
import com.blackaby.Frontend.Shaders.ShaderPresetDocument.ShaderParameterDefinition;
import com.blackaby.Frontend.Shaders.ShaderPresetDocument.ShaderPassConfig;
import com.blackaby.Frontend.Shaders.ShaderPresetDocument.ShaderPassType;
import com.blackaby.Frontend.Shaders.ShaderPreviewRenderer;
import com.blackaby.Misc.UiText;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GUI editor and preset manager for JSON display shaders.
 */
public final class ShaderPresetEditorWindow extends DuckWindow {

    private final Runnable onShadersChanged;
    private final DefaultListModel<PresetFileEntry> presetListModel = new DefaultListModel<>();
    private final DefaultListModel<ShaderPassConfig> passChainModel = new DefaultListModel<>();
    private final JTextField fileNameField = new JTextField();
    private final JTextField shaderIdField = new JTextField();
    private final JTextField shaderNameField = new JTextField();
    private final JTextField descriptionField = new JTextField();
    private final JSpinner renderScaleSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 6, 1));
    private final JLabel statusValueLabel = new JLabel();
    private final JLabel selectedPassDescriptionLabel = new JLabel(UiText.ShaderEditorWindow.PASS_PARAMETERS_EMPTY);
    private final JLabel previewStatusLabel = new JLabel();
    private final JList<PresetFileEntry> presetList = new JList<>(presetListModel);
    private final JList<ShaderPassConfig> passChainList = new JList<>(passChainModel);
    private final JComboBox<ShaderPassType> addPassSelector = new JComboBox<>(ShaderPassType.values());
    private final JLabel addPassDescriptionLabel = new JLabel();
    private final JPanel passParameterPanel = new JPanel(new GridBagLayout());
    private final ImagePreviewSurface sourcePreviewSurface = new ImagePreviewSurface(
            UiText.ShaderEditorWindow.PREVIEW_UNAVAILABLE,
            280,
            210);
    private final ImagePreviewSurface outputPreviewSurface = new ImagePreviewSurface(
            UiText.ShaderEditorWindow.PREVIEW_UNAVAILABLE,
            280,
            210);
    private final AtomicInteger previewRequestVersion = new AtomicInteger();
    private final Timer previewRefreshTimer;
    private boolean updatingSelection;
    private boolean updatingEditor;

    public ShaderPresetEditorWindow(Runnable onShadersChanged) {
        super(UiText.ShaderEditorWindow.WINDOW_TITLE, 1180, 760, true);
        this.onShadersChanged = onShadersChanged;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Styling.appBackgroundColour);
        previewRefreshTimer = new Timer(140, event -> refreshPreviewAsync());
        previewRefreshTimer.setRepeats(false);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);

        attachFieldListeners();
        refreshPresetList(null);
        setVisible(true);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setBackground(Styling.appBackgroundColour);
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));

        JLabel titleLabel = new JLabel(UiText.ShaderEditorWindow.TITLE);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(Styling.accentColour);

        JLabel subtitleLabel = new JLabel(UiText.ShaderEditorWindow.SUBTITLE);
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

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildPresetListCard(), buildEditorCard());
        splitPane.setResizeWeight(0.26);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setOpaque(false);

        wrapper.add(splitPane, BorderLayout.CENTER);
        return wrapper;
    }

    private JComponent buildPresetListCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(Styling.surfaceColour);
        card.setBorder(WindowUiSupport.createCardBorder(Styling.surfaceBorderColour, false, 18));

        JLabel titleLabel = new JLabel(UiText.ShaderEditorWindow.PRESET_LIST_TITLE);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Styling.accentColour);
        card.add(titleLabel, BorderLayout.NORTH);

        presetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        presetList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        presetList.setFixedCellHeight(28);
        presetList.setBackground(Styling.cardTintColour);
        presetList.setSelectionBackground(Styling.listSelectionColour);
        presetList.setSelectionForeground(Styling.accentColour);
        presetList.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || updatingSelection) {
                return;
            }
            loadSelectedPreset();
        });

        JScrollPane scrollPane = new JScrollPane(presetList);
        scrollPane.setBorder(WindowUiSupport.createLineBorder(Styling.surfaceBorderColour));
        card.add(scrollPane, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);

        JButton newButton = WindowUiSupport.createSecondaryButton(
                UiText.ShaderEditorWindow.NEW_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        newButton.addActionListener(event -> {
            presetList.clearSelection();
            loadTemplateIntoEditor(suggestNewPresetPath());
        });

        JButton importButton = WindowUiSupport.createSecondaryButton(
                UiText.ShaderEditorWindow.IMPORT_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        importButton.addActionListener(event -> importPreset());

        JButton deleteButton = WindowUiSupport.createSecondaryButton(
                UiText.ShaderEditorWindow.DELETE_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        deleteButton.addActionListener(event -> deleteSelectedPreset());

        actions.add(newButton);
        actions.add(importButton);
        actions.add(deleteButton);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildEditorCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(Styling.surfaceColour);
        card.setBorder(WindowUiSupport.createCardBorder(Styling.surfaceBorderColour, false, 18));

        JLabel titleLabel = new JLabel(UiText.ShaderEditorWindow.EDITOR_TITLE);
        titleLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Styling.accentColour);
        card.add(titleLabel, BorderLayout.NORTH);

        JPanel editorContent = new JPanel(new BorderLayout(0, 12));
        editorContent.setOpaque(false);
        editorContent.add(buildMetadataPanel(), BorderLayout.NORTH);
        editorContent.add(buildEditorSplitPane(), BorderLayout.CENTER);
        editorContent.add(buildActionsPanel(), BorderLayout.SOUTH);

        card.add(editorContent, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildMetadataPanel() {
        JPanel stack = new JPanel();
        stack.setLayout(new javax.swing.BoxLayout(stack, javax.swing.BoxLayout.Y_AXIS));
        stack.setOpaque(false);

        JPanel topRow = new JPanel(new BorderLayout(10, 0));
        topRow.setOpaque(false);

        JPanel fileFieldCard = createLabeledFieldPanel(
                UiText.ShaderEditorWindow.FILE_NAME_LABEL,
                fileNameField);
        JPanel statusCard = new JPanel(new BorderLayout(0, 6));
        statusCard.setOpaque(false);

        JLabel statusLabel = new JLabel(UiText.ShaderEditorWindow.STATUS_LABEL);
        statusLabel.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        statusLabel.setForeground(Styling.accentColour);
        statusValueLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        statusValueLabel.setForeground(Styling.mutedTextColour);
        statusValueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        statusCard.add(statusLabel, BorderLayout.NORTH);
        statusCard.add(statusValueLabel, BorderLayout.CENTER);
        statusCard.setPreferredSize(new Dimension(200, 48));

        topRow.add(fileFieldCard, BorderLayout.CENTER);
        topRow.add(statusCard, BorderLayout.EAST);

        JPanel metadataGrid = new JPanel(new GridLayout(2, 2, 10, 10));
        metadataGrid.setOpaque(false);
        metadataGrid.add(createLabeledFieldPanel(UiText.ShaderEditorWindow.SHADER_ID_LABEL, shaderIdField));
        metadataGrid.add(createLabeledFieldPanel(UiText.ShaderEditorWindow.SHADER_NAME_LABEL, shaderNameField));
        metadataGrid.add(createLabeledFieldPanel(UiText.ShaderEditorWindow.DESCRIPTION_LABEL, descriptionField));
        metadataGrid.add(createLabeledSpinnerPanel(UiText.ShaderEditorWindow.RENDER_SCALE_LABEL, renderScaleSpinner));

        stack.add(topRow);
        stack.add(javax.swing.Box.createVerticalStrut(10));
        stack.add(metadataGrid);
        return stack;
    }

    private JComponent buildEditorSplitPane() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildChainEditorCard(),
                buildInspectorCard());
        splitPane.setResizeWeight(0.48);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setOpaque(false);
        return splitPane;
    }

    private JComponent buildChainEditorCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setOpaque(false);

        JPanel addCard = new JPanel(new BorderLayout(12, 0));
        addCard.setBackground(Styling.cardTintColour);
        addCard.setBorder(WindowUiSupport.createCardBorder(Styling.surfaceBorderColour, false, 14));

        JPanel addText = new JPanel();
        addText.setLayout(new javax.swing.BoxLayout(addText, javax.swing.BoxLayout.Y_AXIS));
        addText.setOpaque(false);

        JLabel addTitle = new JLabel(UiText.ShaderEditorWindow.ADD_PASS_TITLE);
        addTitle.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        addTitle.setForeground(Styling.accentColour);

        addPassSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        addPassSelector.setBackground(Styling.surfaceColour);
        addPassSelector.setForeground(Styling.accentColour);
        addPassSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
                        cellHasFocus);
                if (value instanceof ShaderPassType passType) {
                    label.setText(passType.label());
                }
                return label;
            }
        });
        addPassSelector.addActionListener(event -> updateAddPassDescription());

        addPassDescriptionLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        addPassDescriptionLabel.setForeground(Styling.mutedTextColour);

        addText.add(addTitle);
        addText.add(javax.swing.Box.createVerticalStrut(8));
        addText.add(addPassSelector);
        addText.add(javax.swing.Box.createVerticalStrut(8));
        addText.add(addPassDescriptionLabel);

        JButton addButton = WindowUiSupport.createSecondaryButton(
                UiText.ShaderEditorWindow.ADD_TO_CHAIN_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        addButton.addActionListener(event -> addSelectedPassType());

        JPanel addButtonWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        addButtonWrap.setOpaque(false);
        addButtonWrap.add(addButton);

        addCard.add(addText, BorderLayout.CENTER);
        addCard.add(addButtonWrap, BorderLayout.EAST);

        JPanel chainCard = new JPanel(new BorderLayout(12, 0));
        chainCard.setBackground(Styling.cardTintColour);
        chainCard.setBorder(WindowUiSupport.createCardBorder(Styling.surfaceBorderColour, false, 14));

        JPanel chainText = new JPanel();
        chainText.setLayout(new javax.swing.BoxLayout(chainText, javax.swing.BoxLayout.Y_AXIS));
        chainText.setOpaque(false);

        JLabel chainTitle = new JLabel(UiText.ShaderEditorWindow.CHAIN_TITLE);
        chainTitle.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        chainTitle.setForeground(Styling.accentColour);

        JLabel chainHelper = new JLabel(UiText.ShaderEditorWindow.CHAIN_HELPER);
        chainHelper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        chainHelper.setForeground(Styling.mutedTextColour);

        chainText.add(chainTitle);
        chainText.add(javax.swing.Box.createVerticalStrut(4));
        chainText.add(chainHelper);

        passChainList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        passChainList.setBackground(Styling.surfaceColour);
        passChainList.setForeground(Styling.accentColour);
        passChainList.setSelectionBackground(Styling.listSelectionColour);
        passChainList.setSelectionForeground(Styling.accentColour);
        passChainList.setFixedCellHeight(32);
        passChainList.setBorder(WindowUiSupport.createLineBorder(Styling.surfaceBorderColour));
        passChainList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
                        cellHasFocus);
                if (value instanceof ShaderPassConfig passConfig) {
                    label.setText(UiText.ShaderEditorWindow.PassChainItemLabel(index, passConfig.type().label()));
                }
                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return label;
            }
        });
        passChainList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                rebuildPassParameterPanel();
            }
        });

        JScrollPane chainScrollPane = new JScrollPane(passChainList);
        chainScrollPane.setBorder(BorderFactory.createEmptyBorder());
        chainScrollPane.getViewport().setBackground(Styling.surfaceColour);
        chainScrollPane.setPreferredSize(new Dimension(0, 220));

        JPanel chainPanel = new JPanel(new BorderLayout(0, 8));
        chainPanel.setOpaque(false);
        chainPanel.add(chainText, BorderLayout.NORTH);
        chainPanel.add(chainScrollPane, BorderLayout.CENTER);

        JPanel chainControls = new JPanel(new GridLayout(4, 1, 0, 8));
        chainControls.setOpaque(false);

        JButton moveUpButton = WindowUiSupport.createSecondaryButton(
                UiText.ShaderEditorWindow.MOVE_UP_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        moveUpButton.addActionListener(event -> moveSelectedPass(-1));

        JButton moveDownButton = WindowUiSupport.createSecondaryButton(
                UiText.ShaderEditorWindow.MOVE_DOWN_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        moveDownButton.addActionListener(event -> moveSelectedPass(1));

        JButton removeButton = WindowUiSupport.createSecondaryButton(
                UiText.ShaderEditorWindow.REMOVE_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        removeButton.addActionListener(event -> removeSelectedPass());

        JButton clearButton = WindowUiSupport.createSecondaryButton(
                UiText.ShaderEditorWindow.CLEAR_CHAIN_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        clearButton.addActionListener(event -> clearPassChain());

        chainControls.add(moveUpButton);
        chainControls.add(moveDownButton);
        chainControls.add(removeButton);
        chainControls.add(clearButton);

        chainCard.add(chainPanel, BorderLayout.CENTER);
        chainCard.add(chainControls, BorderLayout.EAST);

        JPanel body = new JPanel();
        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.add(addCard);
        body.add(javax.swing.Box.createVerticalStrut(12));
        body.add(chainCard);

        card.add(body, BorderLayout.NORTH);
        return card;
    }

    private JComponent buildInspectorCard() {
        JPanel inspector = new JPanel();
        inspector.setLayout(new javax.swing.BoxLayout(inspector, javax.swing.BoxLayout.Y_AXIS));
        inspector.setOpaque(false);
        inspector.add(buildPreviewCard());
        inspector.add(javax.swing.Box.createVerticalStrut(12));
        inspector.add(buildPassInspectorCard());
        return inspector;
    }

    private JComponent buildPreviewCard() {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Styling.cardTintColour);
        card.setBorder(WindowUiSupport.createCardBorder(Styling.surfaceBorderColour, false, 14));

        JPanel text = new JPanel();
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        text.setOpaque(false);

        JLabel title = new JLabel(UiText.ShaderEditorWindow.PREVIEW_TITLE);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        title.setForeground(Styling.accentColour);

        JLabel helper = new JLabel(UiText.ShaderEditorWindow.PREVIEW_HELPER);
        helper.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        helper.setForeground(Styling.mutedTextColour);

        text.add(title);
        text.add(javax.swing.Box.createVerticalStrut(4));
        text.add(helper);

        JPanel previewGrid = new JPanel(new GridLayout(1, 2, 10, 0));
        previewGrid.setOpaque(false);
        previewGrid.add(createPreviewPane(UiText.ShaderEditorWindow.SOURCE_PREVIEW_LABEL, sourcePreviewSurface));
        previewGrid.add(createPreviewPane(UiText.ShaderEditorWindow.OUTPUT_PREVIEW_LABEL, outputPreviewSurface));

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

    private JComponent buildPassInspectorCard() {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Styling.cardTintColour);
        card.setBorder(WindowUiSupport.createCardBorder(Styling.surfaceBorderColour, false, 14));

        JLabel title = new JLabel(UiText.ShaderEditorWindow.PASS_PARAMETERS_TITLE);
        title.setFont(Styling.menuFont.deriveFont(Font.BOLD, 13f));
        title.setForeground(Styling.accentColour);

        selectedPassDescriptionLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
        selectedPassDescriptionLabel.setForeground(Styling.mutedTextColour);

        JPanel header = new JPanel();
        header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(title);
        header.add(javax.swing.Box.createVerticalStrut(4));
        header.add(selectedPassDescriptionLabel);

        passParameterPanel.setOpaque(false);
        JScrollPane parameterScrollPane = new JScrollPane(passParameterPanel);
        parameterScrollPane.setBorder(BorderFactory.createEmptyBorder());
        parameterScrollPane.getViewport().setBackground(Styling.cardTintColour);
        parameterScrollPane.setPreferredSize(new Dimension(0, 210));

        card.add(header, BorderLayout.NORTH);
        card.add(parameterScrollPane, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildActionsPanel() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton reloadButton = WindowUiSupport.createSecondaryButton(
                UiText.ShaderEditorWindow.RELOAD_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        reloadButton.addActionListener(event -> {
            PresetFileEntry selectedEntry = presetList.getSelectedValue();
            reloadShaderLibrary();
            refreshPresetList(selectedEntry == null ? null : selectedEntry.relativePath());
        });

        JButton openFolderButton = WindowUiSupport.createSecondaryButton(
                UiText.ShaderEditorWindow.OPEN_FOLDER_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        openFolderButton.addActionListener(event -> openShaderDirectory());

        JButton closeButton = WindowUiSupport.createSecondaryButton(
                UiText.ShaderEditorWindow.CLOSE_BUTTON,
                Styling.accentColour,
                Styling.surfaceBorderColour);
        closeButton.addActionListener(event -> dispose());

        JButton saveButton = WindowUiSupport.createPrimaryButton(
                UiText.ShaderEditorWindow.SAVE_BUTTON,
                Styling.accentColour);
        saveButton.addActionListener(event -> saveCurrentPreset());

        actions.add(reloadButton);
        actions.add(openFolderButton);
        actions.add(closeButton);
        actions.add(saveButton);
        return actions;
    }

    private JPanel createLabeledFieldPanel(String labelText, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        label.setForeground(Styling.accentColour);

        field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(label, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createLabeledSpinnerPanel(String labelText, JSpinner spinner) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
        label.setForeground(Styling.accentColour);

        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            defaultEditor.getTextField().setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        }

        panel.add(label, BorderLayout.NORTH);
        panel.add(spinner, BorderLayout.CENTER);
        return panel;
    }

    private void attachFieldListeners() {
        DocumentListener dirtyListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                handleEditorChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                handleEditorChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                handleEditorChanged();
            }
        };

        fileNameField.getDocument().addDocumentListener(dirtyListener);
        shaderIdField.getDocument().addDocumentListener(dirtyListener);
        shaderNameField.getDocument().addDocumentListener(dirtyListener);
        descriptionField.getDocument().addDocumentListener(dirtyListener);
        renderScaleSpinner.addChangeListener(event -> handleEditorChanged());
        updateAddPassDescription();
    }

    private void refreshPresetList(String preferredRelativePath) {
        presetListModel.clear();
        List<PresetFileEntry> presetFiles = findPresetFiles();
        for (PresetFileEntry entry : presetFiles) {
            presetListModel.addElement(entry);
        }

        if (presetListModel.isEmpty()) {
            loadTemplateIntoEditor(suggestNewPresetPath());
            setStatus(UiText.ShaderEditorWindow.EMPTY);
            return;
        }

        int selectedIndex = 0;
        if (preferredRelativePath != null && !preferredRelativePath.isBlank()) {
            for (int index = 0; index < presetListModel.size(); index++) {
                if (presetListModel.get(index).relativePath().equalsIgnoreCase(preferredRelativePath)) {
                    selectedIndex = index;
                    break;
                }
            }
        }

        updatingSelection = true;
        try {
            presetList.setSelectedIndex(selectedIndex);
        } finally {
            updatingSelection = false;
        }
        loadSelectedPreset();
    }

    private List<PresetFileEntry> findPresetFiles() {
        List<PresetFileEntry> entries = new ArrayList<>();
        Path shaderDirectory = DisplayShaderManager.ShaderDirectory();
        try {
            Files.createDirectories(shaderDirectory);
            try (var files = Files.walk(shaderDirectory)) {
                files.filter(path -> Files.isRegularFile(path)
                        && path.getFileName().toString().toLowerCase().endsWith(".json"))
                        .sorted(Comparator.comparing(path -> shaderDirectory.relativize(path).toString().toLowerCase()))
                        .forEach(path -> entries.add(new PresetFileEntry(
                                shaderDirectory.relativize(path).toString().replace('\\', '/'),
                                path)));
            }
        } catch (IOException exception) {
            setStatus(exception.getMessage());
        }
        return entries;
    }

    private void loadSelectedPreset() {
        PresetFileEntry selectedEntry = presetList.getSelectedValue();
        if (selectedEntry == null) {
            return;
        }

        try {
            ShaderPresetDocument document = ShaderPresetDocument.fromJson(
                    Files.readString(selectedEntry.absolutePath(), StandardCharsets.UTF_8));
            applyDocumentToEditor(selectedEntry.relativePath(), document, selectedEntry.relativePath());
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.ShaderEditorWindow.LOAD_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadTemplateIntoEditor(Path suggestedPath) {
        String relativePath = suggestedPath == null
                ? suggestNewPresetPath().getFileName().toString()
                : DisplayShaderManager.ShaderDirectory().relativize(suggestedPath).toString().replace('\\', '/');
        applyDocumentToEditor(
                relativePath,
                ShaderPresetDocument.createDefault(relativePath),
                UiText.ShaderEditorWindow.TEMPLATE_STATUS);
    }

    private void applyDocumentToEditor(String relativePath, ShaderPresetDocument document, String statusText) {
        updatingEditor = true;
        try {
            fileNameField.setText(relativePath == null ? "" : relativePath);
            shaderIdField.setText(document.id());
            shaderNameField.setText(document.name());
            descriptionField.setText(document.description());
            renderScaleSpinner.setValue(document.renderScale());

            passChainModel.clear();
            for (ShaderPassConfig pass : document.passes()) {
                passChainModel.addElement(pass);
            }

            if (!passChainModel.isEmpty()) {
                passChainList.setSelectedIndex(0);
            } else {
                passChainList.clearSelection();
            }
            setStatus(statusText);
        } finally {
            updatingEditor = false;
        }

        rebuildPassParameterPanel();
        refreshPreviewAsync();
    }

    private void saveCurrentPreset() {
        String relativePath = normaliseRelativeFileName(fileNameField.getText());
        if (relativePath == null) {
            JOptionPane.showMessageDialog(this,
                    UiText.ShaderEditorWindow.FILE_NAME_REQUIRED,
                    UiText.ShaderEditorWindow.SAVE_FAILED_TITLE,
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            ShaderPresetDocument document = buildDocumentForSave();
            Path targetPath = resolvePresetPath(relativePath);
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, document.toPrettyJson(), StandardCharsets.UTF_8);
            reloadShaderLibrary();
            refreshPresetList(relativePath);
            setStatus(UiText.ShaderEditorWindow.SavedStatus(relativePath));
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.ShaderEditorWindow.SAVE_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.ShaderEditorWindow.SAVE_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importPreset() {
        FileDialog fileDialog = new FileDialog(this, UiText.ShaderEditorWindow.IMPORT_DIALOG_TITLE, FileDialog.LOAD);
        fileDialog.setAlwaysOnTop(true);
        fileDialog.setFilenameFilter((directory, name) -> name.toLowerCase().endsWith(".json"));
        fileDialog.setVisible(true);
        if (fileDialog.getFiles().length == 0) {
            return;
        }

        File importFile = fileDialog.getFiles()[0];
        if (importFile == null) {
            return;
        }

        try {
            ShaderPresetDocument.fromJson(Files.readString(importFile.toPath(), StandardCharsets.UTF_8));
            Path destinationPath = resolveImportDestination(importFile.toPath());
            Files.createDirectories(destinationPath.getParent());
            Files.copy(importFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
            reloadShaderLibrary();
            String relativePath = DisplayShaderManager.ShaderDirectory()
                    .relativize(destinationPath)
                    .toString()
                    .replace('\\', '/');
            refreshPresetList(relativePath);
            setStatus(UiText.ShaderEditorWindow.ImportedStatus(relativePath));
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.ShaderEditorWindow.IMPORT_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedPreset() {
        PresetFileEntry selectedEntry = presetList.getSelectedValue();
        if (selectedEntry == null) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                UiText.ShaderEditorWindow.DeleteConfirmMessage(selectedEntry.relativePath()),
                UiText.ShaderEditorWindow.DELETE_CONFIRM_TITLE,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            Files.deleteIfExists(selectedEntry.absolutePath());
            reloadShaderLibrary();
            refreshPresetList(null);
            setStatus(UiText.ShaderEditorWindow.DeletedStatus(selectedEntry.relativePath()));
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.ShaderEditorWindow.DELETE_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reloadShaderLibrary() {
        DisplayShaderManager.Reload();
        if (onShadersChanged != null) {
            onShadersChanged.run();
        }
        List<String> loadErrors = DisplayShaderManager.GetLoadErrors();
        if (!loadErrors.isEmpty()) {
            setStatus(UiText.ShaderEditorWindow.ReloadWarningStatus(loadErrors.size()));
        }
    }

    private void openShaderDirectory() {
        try {
            Files.createDirectories(DisplayShaderManager.ShaderDirectory());
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(DisplayShaderManager.ShaderDirectory().toFile());
            }
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage(),
                    UiText.ShaderEditorWindow.OPEN_FOLDER_FAILED_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleEditorChanged() {
        if (updatingEditor) {
            return;
        }
        setStatus(UiText.ShaderEditorWindow.UNSAVED_STATUS);
        schedulePreviewRefresh();
    }

    private void updateAddPassDescription() {
        Object selectedPass = addPassSelector.getSelectedItem();
        if (selectedPass instanceof ShaderPassType passType) {
            addPassDescriptionLabel.setText(passType.description());
        } else {
            addPassDescriptionLabel.setText(UiText.ShaderEditorWindow.PASS_DESCRIPTION_PLACEHOLDER);
        }
    }

    private void addSelectedPassType() {
        Object selectedPass = addPassSelector.getSelectedItem();
        if (!(selectedPass instanceof ShaderPassType passType)) {
            return;
        }

        ShaderPassConfig passConfig = passType.createDefaultPass();
        alignPassToCurrentRenderScale(passConfig);
        passChainModel.addElement(passConfig);
        passChainList.setSelectedIndex(passChainModel.size() - 1);
        handleEditorChanged();
    }

    private void alignPassToCurrentRenderScale(ShaderPassConfig passConfig) {
        int currentRenderScale = ((Number) renderScaleSpinner.getValue()).intValue();
        if (currentRenderScale <= 0) {
            return;
        }

        if (passConfig.hasParameter("cellWidth")) {
            passConfig.setValue("cellWidth", currentRenderScale);
        }
        if (passConfig.hasParameter("cellHeight")) {
            passConfig.setValue("cellHeight", currentRenderScale);
        }
        if (passConfig.hasParameter("rowSpacing")) {
            passConfig.setValue("rowSpacing", currentRenderScale);
        }
        if (passConfig.hasParameter("columnSpacing")) {
            passConfig.setValue("columnSpacing", currentRenderScale);
        }
        if (passConfig.hasParameter("spacing")) {
            passConfig.setValue("spacing", currentRenderScale);
        }
    }

    private void moveSelectedPass(int direction) {
        int selectedIndex = passChainList.getSelectedIndex();
        if (selectedIndex < 0) {
            return;
        }

        int targetIndex = selectedIndex + direction;
        if (targetIndex < 0 || targetIndex >= passChainModel.size()) {
            return;
        }

        ShaderPassConfig selectedPass = passChainModel.getElementAt(selectedIndex);
        passChainModel.remove(selectedIndex);
        passChainModel.add(targetIndex, selectedPass);
        passChainList.setSelectedIndex(targetIndex);
        handleEditorChanged();
    }

    private void removeSelectedPass() {
        int selectedIndex = passChainList.getSelectedIndex();
        if (selectedIndex < 0) {
            return;
        }

        passChainModel.remove(selectedIndex);
        if (!passChainModel.isEmpty()) {
            passChainList.setSelectedIndex(Math.min(selectedIndex, passChainModel.size() - 1));
        }
        handleEditorChanged();
        rebuildPassParameterPanel();
    }

    private void clearPassChain() {
        if (passChainModel.isEmpty()) {
            return;
        }

        passChainModel.clear();
        passChainList.clearSelection();
        handleEditorChanged();
        rebuildPassParameterPanel();
    }

    private void rebuildPassParameterPanel() {
        passParameterPanel.removeAll();

        ShaderPassConfig selectedPass = passChainList.getSelectedValue();
        if (selectedPass == null) {
            selectedPassDescriptionLabel.setText(UiText.ShaderEditorWindow.PASS_PARAMETERS_EMPTY);
            GridBagConstraints emptyGbc = new GridBagConstraints();
            emptyGbc.gridx = 0;
            emptyGbc.gridy = 0;
            emptyGbc.weightx = 1.0;
            emptyGbc.anchor = GridBagConstraints.WEST;

            JLabel emptyLabel = new JLabel(UiText.ShaderEditorWindow.PASS_PARAMETERS_EMPTY);
            emptyLabel.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 12f));
            emptyLabel.setForeground(Styling.mutedTextColour);
            passParameterPanel.add(emptyLabel, emptyGbc);
        } else {
            selectedPassDescriptionLabel.setText(selectedPass.type().description());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.insets = new java.awt.Insets(0, 0, 10, 10);
            gbc.anchor = GridBagConstraints.WEST;

            for (ShaderParameterDefinition parameter : selectedPass.type().parameters()) {
                JLabel label = new JLabel(parameter.label());
                label.setFont(Styling.menuFont.deriveFont(Font.BOLD, 12f));
                label.setForeground(Styling.accentColour);
                gbc.gridx = 0;
                gbc.weightx = 0.0;
                gbc.fill = GridBagConstraints.NONE;
                passParameterPanel.add(label, gbc);

                JSpinner spinner = createParameterSpinner(selectedPass, parameter);
                gbc.gridx = 1;
                gbc.weightx = 1.0;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                passParameterPanel.add(spinner, gbc);
                gbc.gridy++;
            }
        }

        GridBagConstraints spacer = new GridBagConstraints();
        spacer.gridx = 0;
        spacer.gridy = Math.max(1, passParameterPanel.getComponentCount());
        spacer.weighty = 1.0;
        spacer.fill = GridBagConstraints.VERTICAL;
        passParameterPanel.add(new JPanel(), spacer);

        passParameterPanel.revalidate();
        passParameterPanel.repaint();
    }

    private JSpinner createParameterSpinner(ShaderPassConfig selectedPass, ShaderParameterDefinition parameter) {
        Number initialValue = selectedPass.value(parameter.key());
        SpinnerNumberModel model = parameter.valueType() == ShaderPresetDocument.ParameterValueType.INTEGER
                ? new SpinnerNumberModel(initialValue.intValue(), (int) parameter.minimum(), (int) parameter.maximum(),
                        (int) parameter.step())
                : new SpinnerNumberModel(initialValue.doubleValue(), parameter.minimum(), parameter.maximum(),
                        parameter.step());
        JSpinner spinner = new JSpinner(model);
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            defaultEditor.getTextField().setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        }
        spinner.addChangeListener(event -> {
            selectedPass.setValue(parameter.key(), (Number) spinner.getValue());
            passChainList.repaint();
            handleEditorChanged();
        });
        return spinner;
    }

    private void schedulePreviewRefresh() {
        previewRefreshTimer.restart();
    }

    private void refreshPreviewAsync() {
        previewRefreshTimer.stop();
        int requestVersion = previewRequestVersion.incrementAndGet();
        ShaderPresetDocument previewDocument;
        try {
            previewDocument = buildDocumentForPreview();
        } catch (Exception exception) {
            applyPreviewResult(requestVersion, new PreviewRenderResult(null, null, exception.getMessage()));
            return;
        }

        CompletableFuture
                .supplyAsync(() -> renderPreview(previewDocument))
                .thenAccept(result -> SwingUtilities.invokeLater(() -> applyPreviewResult(requestVersion, result)));
    }

    private PreviewRenderResult renderPreview(ShaderPresetDocument previewDocument) {
        try {
            return new PreviewRenderResult(
                    previewDocument,
                    ShaderPreviewRenderer.render(previewDocument),
                    null);
        } catch (Exception exception) {
            return new PreviewRenderResult(previewDocument, null, exception.getMessage());
        }
    }

    private void applyPreviewResult(int requestVersion, PreviewRenderResult result) {
        if (previewRequestVersion.get() != requestVersion) {
            return;
        }

        if (result == null || result.errorText() != null) {
            sourcePreviewSurface.setImage(null);
            outputPreviewSurface.setImage(null);
            previewStatusLabel.setText(result == null || result.errorText() == null ? "" : result.errorText());
            return;
        }

        ShaderPreviewRenderer.PreviewImages previewImages = result.previewImages();
        sourcePreviewSurface.setImage(previewImages.sourceImage());
        outputPreviewSurface.setImage(previewImages.previewImage());
        previewStatusLabel.setText(UiText.ShaderEditorWindow.PreviewStatus(
                result.document().renderScale(),
                passChainModel.size()));
    }

    private ShaderPresetDocument buildDocumentForPreview() {
        String id = shaderIdField.getText().trim();
        String name = shaderNameField.getText().trim();
        return new ShaderPresetDocument(
                id.isBlank() ? "preview_shader" : id,
                name.isBlank() ? "Preview Shader" : name,
                descriptionField.getText(),
                ((Number) renderScaleSpinner.getValue()).intValue(),
                currentPassChain());
    }

    private ShaderPresetDocument buildDocumentForSave() {
        return new ShaderPresetDocument(
                shaderIdField.getText().trim(),
                shaderNameField.getText().trim(),
                descriptionField.getText(),
                ((Number) renderScaleSpinner.getValue()).intValue(),
                currentPassChain());
    }

    private List<ShaderPassConfig> currentPassChain() {
        List<ShaderPassConfig> passes = new ArrayList<>(passChainModel.size());
        for (int index = 0; index < passChainModel.size(); index++) {
            passes.add(passChainModel.get(index));
        }
        return passes;
    }

    private void setStatus(String text) {
        statusValueLabel.setText(text == null ? "" : text);
    }

    private Path suggestNewPresetPath() {
        Path shaderDirectory = DisplayShaderManager.ShaderDirectory();
        int index = 1;
        while (true) {
            String fileName = index == 1 ? "custom-shader.json" : "custom-shader-" + index + ".json";
            Path candidate = shaderDirectory.resolve(fileName);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    private String normaliseRelativeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }

        String trimmed = fileName.trim().replace('\\', '/');
        if (trimmed.isBlank() || !trimmed.toLowerCase().endsWith(".json")) {
            return null;
        }
        if (trimmed.startsWith("/") || trimmed.contains("..")) {
            throw new IllegalArgumentException("Preset file must stay inside the shader folder.");
        }
        return trimmed;
    }

    private Path resolvePresetPath(String relativePath) {
        Path shaderDirectory = DisplayShaderManager.ShaderDirectory().toAbsolutePath().normalize();
        Path targetPath = shaderDirectory.resolve(relativePath).normalize();
        if (!targetPath.startsWith(shaderDirectory)) {
            throw new IllegalArgumentException("Preset file must stay inside the shader folder.");
        }
        return targetPath;
    }

    private Path resolveImportDestination(Path importPath) {
        String fileName = importPath == null ? "" : importPath.getFileName().toString();
        if (fileName.isBlank() || !fileName.toLowerCase().endsWith(".json")) {
            throw new IllegalArgumentException("Only .json shader presets can be imported.");
        }

        Path shaderDirectory = DisplayShaderManager.ShaderDirectory();
        Path candidate = shaderDirectory.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }

        String baseName = fileName.substring(0, fileName.length() - 5);
        for (int index = 2; index < 1000; index++) {
            candidate = shaderDirectory.resolve(baseName + "-" + index + ".json");
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unable to find a free filename for the imported preset.");
    }

    @Override
    public void dispose() {
        previewRefreshTimer.stop();
        previewRequestVersion.incrementAndGet();
        super.dispose();
    }

    private record PresetFileEntry(String relativePath, Path absolutePath) {
        @Override
        public String toString() {
            return relativePath;
        }
    }

    private record PreviewRenderResult(ShaderPresetDocument document,
            ShaderPreviewRenderer.PreviewImages previewImages,
            String errorText) {
    }
}
