package com.blackaby.Frontend;

import com.blackaby.Misc.UiText;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Tab composition kept separate from the settings window and its event handlers. */
final class OptionsTabSections {
    private OptionsTabSections() {}

    @FunctionalInterface
    interface CardFactory {
        JPanel create(String title, String description, JComponent body);
    }

    private static JComponent section(Supplier<JPanel> container, CardFactory cards,
            Supplier<JComponent> body, String title, String description, boolean glue) {
        JPanel content = container.get();
        content.add(cards.create(title, description, body.get()));
        if (glue) {
            content.add(Box.createVerticalGlue());
        }
        return content;
    }

    static JComponent palette(Supplier<JPanel> container, CardFactory cards, Supplier<JComponent> body) {
        return section(container, cards, body, UiText.OptionsWindow.SECTION_PALETTE_TITLE,
                UiText.OptionsWindow.SECTION_PALETTE_DESCRIPTION, true);
    }

    static JComponent controls(Supplier<JPanel> container, CardFactory cards, Supplier<JComponent> body) {
        return section(container, cards, body, UiText.OptionsWindow.SECTION_CONTROLS_TITLE,
                UiText.OptionsWindow.SECTION_CONTROLS_DESCRIPTION, true);
    }

    static JComponent sound(Supplier<JPanel> container, CardFactory cards, Supplier<JComponent> body) {
        return section(container, cards, body, UiText.OptionsWindow.SECTION_SOUND_TITLE,
                UiText.OptionsWindow.SECTION_SOUND_DESCRIPTION, false);
    }

    static JComponent emulation(Supplier<JPanel> container, CardFactory cards, Supplier<JComponent> body) {
        return section(container, cards, body, UiText.OptionsWindow.SECTION_EMULATION_TITLE,
                UiText.OptionsWindow.SECTION_EMULATION_DESCRIPTION, false);
    }

    static JComponent window(Supplier<JPanel> container, CardFactory cards, Supplier<JComponent> body) {
        return section(container, cards, body, UiText.OptionsWindow.SECTION_WINDOW_TITLE,
                UiText.OptionsWindow.SECTION_WINDOW_DESCRIPTION, false);
    }

    static JComponent library(Supplier<JPanel> container, CardFactory cards, Supplier<JComponent> body) {
        return section(container, cards, body, UiText.OptionsWindow.SECTION_LIBRARY_TITLE,
                UiText.OptionsWindow.SECTION_LIBRARY_DESCRIPTION, false);
    }

    static JComponent theme(Supplier<JPanel> container, CardFactory cards, Supplier<JComponent> body) {
        return section(container, cards, body, UiText.OptionsWindow.SECTION_THEME_LIBRARY_TITLE,
                UiText.OptionsWindow.SECTION_THEME_LIBRARY_DESCRIPTION, false);
    }

}
