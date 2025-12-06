package com.blackaby.Frontend;

import com.blackaby.Misc.Settings;
import com.blackaby.OldBackEnd.Emulation.Graphics.GBColor;
import com.blackaby.Misc.Config;

import javax.swing.*;
import java.awt.*;

/**
 * This class represents the options window of the application.
 * It allows the user to change the color palette and save/load palettes.
 */
public class OptionsWindow extends DuckWindow {
    private final JPanel[] colorPreviews = new JPanel[4];

    /**
     * Constructor for the OptionsWindow.
     * Sets up the layout, components, and event listeners.
     */
    public OptionsWindow() {
        super("Options", 600, 600, false);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        String title = "<html><h1>Options</h1></html>";
        JLabel titleLabel = new JLabel(title);
        titleLabel.setVerticalAlignment(JLabel.CENTER);
        titleLabel.setHorizontalAlignment(JLabel.CENTER);
        titleLabel.setFont(Styling.TITLE_FONT);
        titleLabel.setForeground(Styling.MENU_FOREGROUND_COLOR);
        add(titleLabel, BorderLayout.NORTH);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(Styling.BACKGROUND_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel paletteLabel = new JLabel("Palette Name:");
        paletteLabel.setForeground(Styling.MENU_FOREGROUND_COLOR);

        JTextField paletteNameField = new JTextField();
        paletteNameField.setPreferredSize(new Dimension(200, 30));

        JButton savePaletteButton = new JButton("Save Palette");
        savePaletteButton.addActionListener(_ -> {
            String name = paletteNameField.getText().trim();
            if (!name.isEmpty()) {
                Config.savePalette(name);
                JOptionPane.showMessageDialog(this, "Palette \"" + name + "\" saved.");
            } else {
                JOptionPane.showMessageDialog(this, "Please enter a name for the palette.", "Warning",
                        JOptionPane.WARNING_MESSAGE);
            }
        });

        JButton loadPaletteButton = new JButton("Load Palette");
        loadPaletteButton.addActionListener(_ -> new PaletteManager());

        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.LINE_END;
        mainPanel.add(paletteLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(paletteNameField, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(savePaletteButton, gbc);

        gbc.gridx = 4;
        mainPanel.add(loadPaletteButton, gbc);

        String[] colorNames = { "Background Color", "Light Color", "Medium Color", "Dark Color" };
        GBColor[] currentColors = {
                Settings.GB_COLOR_0_OBJ,
                Settings.GB_COLOR_1_OBJ,
                Settings.GB_COLOR_2_OBJ,
                Settings.GB_COLOR_3_OBJ
        };

        for (int i = 0; i < colorNames.length; i++) {
            JLabel colorLabel = new JLabel(colorNames[i] + ":");
            colorLabel.setForeground(Styling.MENU_FOREGROUND_COLOR);

            JPanel colorPreview = new JPanel();
            colorPreview.setPreferredSize(new Dimension(60, 30));
            colorPreview.setMinimumSize(new Dimension(60, 30));
            colorPreview.setBackground(currentColors[i].toColor());
            colorPreviews[i] = colorPreview;

            JButton colorButton = new JButton("Choose Color");
            int index = i;
            colorButton.addActionListener(_ -> {
                Color selectedColor = JColorChooser.showDialog(this, "Choose " + colorNames[index],
                        colorPreview.getBackground());
                if (selectedColor != null) {
                    colorPreview.setBackground(selectedColor);
                    updateSettingsColor(index, selectedColor);
                }
            });

            gbc.gridy++;
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.LINE_END;
            mainPanel.add(colorLabel, gbc);

            gbc.gridx = 1;
            gbc.anchor = GridBagConstraints.LINE_START;
            mainPanel.add(colorButton, gbc);

            gbc.gridx = 2;
            gbc.gridwidth = 1;
            mainPanel.add(colorPreview, gbc);
        }

        JButton resetButton = new JButton("Reset to Default");
        resetButton.addActionListener(_ -> {
            Settings.reset();
            GBColor[] defaults = {
                    Settings.GB_COLOR_0_OBJ,
                    Settings.GB_COLOR_1_OBJ,
                    Settings.GB_COLOR_2_OBJ,
                    Settings.GB_COLOR_3_OBJ
            };
            for (int i = 0; i < colorPreviews.length; i++) {
                colorPreviews[i].setBackground(defaults[i].toColor());
            }
        });

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 5;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(resetButton, gbc);

        add(mainPanel, BorderLayout.CENTER);
        setVisible(true);
    }

    /**
     * Updates the settings color based on the selected color.
     *
     * @param index The index of the color to update (0-3).
     * @param color The new color to set.
     */
    private void updateSettingsColor(int index, Color color) {
        String hex = String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
        switch (index) {
            case 0 -> Settings.GB_COLOR_0_OBJ = new GBColor(hex);
            case 1 -> Settings.GB_COLOR_1_OBJ = new GBColor(hex);
            case 2 -> Settings.GB_COLOR_2_OBJ = new GBColor(hex);
            case 3 -> Settings.GB_COLOR_3_OBJ = new GBColor(hex);
        }
    }
}
