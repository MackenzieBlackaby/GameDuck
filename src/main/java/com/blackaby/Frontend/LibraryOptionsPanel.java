package com.blackaby.Frontend;

import com.blackaby.Misc.Config;
import com.blackaby.Misc.GameNameBracketDisplayMode;
import com.blackaby.Misc.Settings;
import com.blackaby.Misc.UiText;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.*;

/** Library-specific settings controls and persistence. */
final class LibraryOptionsPanel {
    private LibraryOptionsPanel() {}

    static JComponent create(MainWindow mainWindow, BiFunction<String, Float, JTextArea> bodyText,
            BiFunction<String, JComponent, JComponent> fieldCard, Function<JComponent, JComponent> optionCard,
            Function<String, JButton> secondaryButton, Function<JButton, JComponent> buttonGroup) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        JComboBox<GameNameBracketDisplayMode> modeSelector = new JComboBox<>(GameNameBracketDisplayMode.values());
        modeSelector.setSelectedItem(Settings.gameNameBracketDisplayMode);
        modeSelector.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        JTextArea helperLabel = bodyText.apply(Settings.gameNameBracketDisplayMode.Description(), 12f);

        modeSelector.addActionListener(event -> {
            Object selectedItem = modeSelector.getSelectedItem();
            if (selectedItem instanceof GameNameBracketDisplayMode selectedMode) {
                helperLabel.setText(selectedMode.Description());
                Settings.gameNameBracketDisplayMode = selectedMode;
                Config.Save();
                if (mainWindow != null) {
                    mainWindow.RefreshLoadedRomDisplay();
                }
            }
        });

        JSpinner recentMenuLimitSpinner = new JSpinner(new SpinnerNumberModel(Settings.loadRecentMenuLimit, 1, 25, 1));
        recentMenuLimitSpinner.setFont(Styling.menuFont.deriveFont(Font.PLAIN, 13f));
        recentMenuLimitSpinner.addChangeListener(event -> {
            Object value = recentMenuLimitSpinner.getValue();
            if (!(value instanceof Number numberValue)) {
                return;
            }

            Settings.loadRecentMenuLimit = Math.max(1, Math.min(25, numberValue.intValue()));
            Config.Save();
        });
        JTextArea recentMenuHelperLabel = bodyText.apply(UiText.OptionsWindow.RECENT_MENU_LIMIT_HELPER, 12f);

        JPanel modeCard = new JPanel(new BorderLayout(0, 10));
        modeCard.setOpaque(false);
        modeCard.add(fieldCard.apply(UiText.OptionsWindow.LIBRARY_MODE_LABEL, modeSelector), BorderLayout.NORTH);
        modeCard.add(helperLabel, BorderLayout.CENTER);
        panel.add(optionCard.apply(modeCard));
        panel.add(Box.createVerticalStrut(10));

        JPanel recentMenuCard = new JPanel(new BorderLayout(0, 10));
        recentMenuCard.setOpaque(false);
        recentMenuCard.add(fieldCard.apply(UiText.OptionsWindow.RECENT_MENU_LIMIT_LABEL, recentMenuLimitSpinner),
                BorderLayout.NORTH);
        recentMenuCard.add(recentMenuHelperLabel, BorderLayout.CENTER);
        panel.add(optionCard.apply(recentMenuCard));
        panel.add(Box.createVerticalStrut(10));

        JButton resetLibraryButton = secondaryButton.apply(UiText.OptionsWindow.RESET_LIBRARY_BUTTON);
        resetLibraryButton.addActionListener(event -> {
            Settings.ResetLibrary();
            modeSelector.setSelectedItem(Settings.gameNameBracketDisplayMode);
            helperLabel.setText(Settings.gameNameBracketDisplayMode.Description());
            recentMenuLimitSpinner.setValue(Settings.loadRecentMenuLimit);
            Config.Save();
            if (mainWindow != null) {
                mainWindow.RefreshLoadedRomDisplay();
            }
        });
        panel.add(buttonGroup.apply(resetLibraryButton));

        return panel;
    }

}
