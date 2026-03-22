package com.blackaby.Frontend;

import com.blackaby.Backend.Emulation.DuckEmulation;
import com.blackaby.Backend.Helpers.GUIActions;
import com.blackaby.Backend.Emulation.Peripherals.DuckJoypad;
import com.blackaby.Misc.AppShortcut;
import com.blackaby.Misc.AppShortcutBindings;
import com.blackaby.Misc.Settings;

import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Routes host keyboard input to the emulated joypad while the main window has
 * focus.
 */
public final class InputRouter implements KeyEventDispatcher {

    private final MainWindow mainWindow;
    private final DuckEmulation emulation;
    private final Set<Integer> pressedKeyCodes = new HashSet<>();
    private final Set<Integer> consumedShortcutKeyCodes = new HashSet<>();

    /**
     * Creates an input router bound to the main window and emulator instance.
     *
     * @param mainWindow owning main window
     * @param emulation running emulator
     */
    public InputRouter(MainWindow mainWindow, DuckEmulation emulation) {
        this.mainWindow = mainWindow;
        this.emulation = emulation;
    }

    /**
     * Registers the router with the current keyboard focus manager.
     */
    public void Install() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!ShouldRouteInput()) {
            pressedKeyCodes.clear();
            consumedShortcutKeyCodes.clear();
            return false;
        }

        if (HandleAppShortcut(event)) {
            return true;
        }

        DuckJoypad.Button button = Settings.inputBindings.GetButtonForKeyCode(event.getKeyCode());
        if (button == null) {
            return false;
        }

        if (event.getID() == KeyEvent.KEY_PRESSED) {
            emulation.SetButtonPressed(button, true);
        } else if (event.getID() == KeyEvent.KEY_RELEASED) {
            emulation.SetButtonPressed(button, false);
        }

        return false;
    }

    private boolean HandleAppShortcut(KeyEvent event) {
        if (event.getID() == KeyEvent.KEY_TYPED || event.getKeyCode() == KeyEvent.VK_UNDEFINED) {
            return false;
        }

        if (event.getID() == KeyEvent.KEY_PRESSED) {
            boolean repeatedPress = !pressedKeyCodes.add(event.getKeyCode());
            AppShortcut shortcut = FindShortcut(event);
            if (shortcut == null) {
                return false;
            }

            consumedShortcutKeyCodes.add(event.getKeyCode());
            if (!repeatedPress) {
                TriggerShortcut(shortcut);
            }
            return true;
        }

        if (event.getID() == KeyEvent.KEY_RELEASED) {
            pressedKeyCodes.remove(event.getKeyCode());

            if (FindShortcut(event) != null) {
                consumedShortcutKeyCodes.remove(event.getKeyCode());
                return true;
            }

            return consumedShortcutKeyCodes.remove(event.getKeyCode());
        }

        return false;
    }

    private AppShortcut FindShortcut(KeyEvent event) {
        KeyStroke keyStroke = AppShortcutBindings.Normalise(
                KeyStroke.getKeyStroke(event.getKeyCode(), event.getModifiersEx()));
        if (keyStroke == null) {
            return null;
        }
        return Settings.appShortcutBindings.GetShortcutForKeyStroke(keyStroke);
    }

    private void TriggerShortcut(AppShortcut shortcut) {
        new GUIActions(mainWindow, shortcut.Action(), emulation)
                .actionPerformed(new ActionEvent(mainWindow, ActionEvent.ACTION_PERFORMED, shortcut.name()));
    }

    /**
     * Returns whether host input should currently be sent to the emulator.
     *
     * @return {@code true} when the main window is the active window
     */
    private boolean ShouldRouteInput() {
        Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return activeWindow == mainWindow;
    }

    @Deprecated
    public void install() {
        Install();
    }

    @Deprecated
    private boolean shouldRouteInput() {
        return ShouldRouteInput();
    }
}
