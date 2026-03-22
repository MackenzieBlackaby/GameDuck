package com.blackaby.Misc;

import com.blackaby.Backend.Helpers.GUIActions.Action;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Enumerates the configurable shortcuts for host window actions.
 */
public enum AppShortcut {
    OPTIONS(UiText.AppShortcuts.OPTIONS_LABEL, UiText.AppShortcuts.OPTIONS_DESCRIPTION, Action.OPTIONS, KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0)),
    EXIT(UiText.AppShortcuts.EXIT_LABEL, UiText.AppShortcuts.EXIT_DESCRIPTION, Action.EXIT,
            KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK)),
    OPEN_GAME(UiText.AppShortcuts.OPEN_GAME_LABEL, UiText.AppShortcuts.OPEN_GAME_DESCRIPTION, Action.LOADROM,
            KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK)),
    PAUSE_GAME(UiText.AppShortcuts.PAUSE_GAME_LABEL, UiText.AppShortcuts.PAUSE_GAME_DESCRIPTION, Action.PAUSEGAME,
            KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK)),
    CLOSE_GAME(UiText.AppShortcuts.CLOSE_GAME_LABEL, UiText.AppShortcuts.CLOSE_GAME_DESCRIPTION, Action.CLOSEGAME,
            KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK)),
    SAVE_STATE(UiText.AppShortcuts.SAVE_STATE_LABEL, UiText.AppShortcuts.SAVE_STATE_DESCRIPTION, Action.SAVESTATE,
            KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK)),
    LOAD_STATE(UiText.AppShortcuts.LOAD_STATE_LABEL, UiText.AppShortcuts.LOAD_STATE_DESCRIPTION, Action.LOADSTATE,
            KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK)),
    TOGGLE_FULL_VIEW(UiText.AppShortcuts.TOGGLE_FULL_VIEW_LABEL, UiText.AppShortcuts.TOGGLE_FULL_VIEW_DESCRIPTION, Action.FULL_VIEW,
            KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0)),
    TOGGLE_FULLSCREEN(UiText.AppShortcuts.TOGGLE_FULLSCREEN_LABEL, UiText.AppShortcuts.TOGGLE_FULLSCREEN_DESCRIPTION, Action.FULLSCREEN,
            KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0)),
    TOGGLE_MAXIMISE(UiText.AppShortcuts.TOGGLE_MAXIMISE_LABEL, UiText.AppShortcuts.TOGGLE_MAXIMISE_DESCRIPTION, Action.MAXIMISE,
            KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0)),
    TOGGLE_FRAME_COUNTER(UiText.AppShortcuts.TOGGLE_FRAME_COUNTER_LABEL, UiText.AppShortcuts.TOGGLE_FRAME_COUNTER_DESCRIPTION, Action.FRAMECOUNTER,
            KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));

    private final String label;
    private final String description;
    private final Action action;
    private final KeyStroke defaultKeyStroke;

    AppShortcut(String label, String description, Action action, KeyStroke defaultKeyStroke) {
        this.label = label;
        this.description = description;
        this.action = action;
        this.defaultKeyStroke = defaultKeyStroke;
    }

    /**
     * Returns the label shown for the shortcut in the UI.
     *
     * @return shortcut label
     */
    public String Label() {
        return label;
    }

    /**
     * Returns the explanatory text for the shortcut.
     *
     * @return shortcut description
     */
    public String Description() {
        return description;
    }

    /**
     * Returns the host action triggered by the shortcut.
     *
     * @return action tied to the shortcut
     */
    public Action Action() {
        return action;
    }

    /**
     * Returns the default key stroke for the shortcut.
     *
     * @return default key stroke
     */
    public KeyStroke DefaultKeyStroke() {
        return defaultKeyStroke;
    }

    @Deprecated public String label() { return Label(); }
    @Deprecated public String description() { return Description(); }
    @Deprecated public Action action() { return Action(); }
    @Deprecated public KeyStroke defaultKeyStroke() { return DefaultKeyStroke(); }
}
