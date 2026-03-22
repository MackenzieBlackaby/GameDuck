package com.blackaby.Misc;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EnumMap;

/**
 * Stores the host keyboard map for application shortcuts.
 * <p>
 * Reassigning one shortcut to a key already in use swaps the bindings so the
 * map stays unique without silently dropping an action.
 */
public final class AppShortcutBindings {

    private final EnumMap<AppShortcut, KeyStroke> bindings = new EnumMap<>(AppShortcut.class);

    /**
     * Creates a binding set initialised with the default shortcuts.
     */
    public AppShortcutBindings() {
        ResetToDefaults();
    }

    /**
     * Restores the default shortcut map.
     */
    public synchronized void ResetToDefaults() {
        bindings.clear();
        for (AppShortcut shortcut : AppShortcut.values()) {
            bindings.put(shortcut, shortcut.DefaultKeyStroke());
        }
    }

    /**
     * Returns the active key stroke for a shortcut.
     *
     * @param shortcut shortcut to read
     * @return assigned key stroke, or {@code null} if none is set
     */
    public synchronized KeyStroke GetKeyStroke(AppShortcut shortcut) {
        return bindings.get(shortcut);
    }

    /**
     * Returns the active key stroke formatted for display.
     *
     * @param shortcut shortcut to read
     * @return readable key label
     */
    public synchronized String GetKeyText(AppShortcut shortcut) {
        return FormatKeyStroke(GetKeyStroke(shortcut));
    }

    /**
     * Assigns a new key stroke to a shortcut.
     *
     * @param shortcut shortcut to update
     * @param keyStroke replacement key stroke
     */
    public synchronized void SetKeyStroke(AppShortcut shortcut, KeyStroke keyStroke) {
        if (shortcut == null || keyStroke == null || keyStroke.getKeyCode() == KeyEvent.VK_UNDEFINED) {
            return;
        }

        KeyStroke normalised = Normalise(keyStroke);
        if (normalised == null) {
            return;
        }

        KeyStroke current = GetKeyStroke(shortcut);
        AppShortcut existingShortcut = GetShortcutForKeyStroke(normalised);
        bindings.put(shortcut, normalised);

        if (existingShortcut != null && existingShortcut != shortcut && current != null) {
            bindings.put(existingShortcut, current);
        }
    }

    /**
     * Finds the shortcut already assigned to a key stroke.
     *
     * @param keyStroke key stroke to look up
     * @return matching shortcut, or {@code null} if none is assigned
     */
    public synchronized AppShortcut GetShortcutForKeyStroke(KeyStroke keyStroke) {
        if (keyStroke == null) {
            return null;
        }

        KeyStroke normalised = Normalise(keyStroke);
        for (AppShortcut shortcut : AppShortcut.values()) {
            if (normalised.equals(bindings.get(shortcut))) {
                return shortcut;
            }
        }
        return null;
    }

    /**
     * Serialises a shortcut binding for the config file.
     *
     * @param shortcut shortcut to serialise
     * @return compact config value
     */
    public synchronized String ToConfigValue(AppShortcut shortcut) {
        KeyStroke keyStroke = GetKeyStroke(shortcut);
        if (keyStroke == null) {
            return "";
        }
        return keyStroke.getKeyCode() + ":" + keyStroke.getModifiers();
    }

    /**
     * Loads one shortcut binding from a config value.
     *
     * @param shortcut shortcut to update
     * @param value persisted value to parse
     */
    public synchronized void LoadFromConfigValue(AppShortcut shortcut, String value) {
        if (shortcut == null || value == null || value.isBlank()) {
            return;
        }

        String[] parts = value.split(":");
        if (parts.length != 2) {
            return;
        }

        try {
            int keyCode = Integer.parseInt(parts[0]);
            int modifiers = Integer.parseInt(parts[1]);
            SetKeyStroke(shortcut, KeyStroke.getKeyStroke(keyCode, modifiers));
        } catch (NumberFormatException ignored) {
        }
    }

    /**
     * Normalises a key stroke before it is stored.
     *
     * @param keyStroke key stroke to normalise
     * @return normalised key stroke, or {@code null} if it is unusable
     */
    public static KeyStroke Normalise(KeyStroke keyStroke) {
        if (keyStroke == null || keyStroke.getKeyCode() == KeyEvent.VK_UNDEFINED) {
            return null;
        }
        return KeyStroke.getKeyStroke(keyStroke.getKeyCode(), keyStroke.getModifiers());
    }

    /**
     * Returns whether a key code is a modifier key on its own.
     *
     * @param keyCode key code to inspect
     * @return {@code true} if the code is only a modifier
     */
    public static boolean IsModifierKey(int keyCode) {
        return keyCode == KeyEvent.VK_SHIFT
                || keyCode == KeyEvent.VK_CONTROL
                || keyCode == KeyEvent.VK_ALT
                || keyCode == KeyEvent.VK_ALT_GRAPH
                || keyCode == KeyEvent.VK_META;
    }

    /**
     * Formats a key stroke for display in the UI.
     *
     * @param keyStroke key stroke to format
     * @return readable shortcut text
     */
    public static String FormatKeyStroke(KeyStroke keyStroke) {
        if (keyStroke == null) {
            return "Unbound";
        }

        String modifiers = InputEvent.getModifiersExText(keyStroke.getModifiers());
        String keyText = KeyEvent.getKeyText(keyStroke.getKeyCode());

        if (modifiers == null || modifiers.isBlank()) {
            return keyText;
        }
        return modifiers + "+" + keyText;
    }

    @Deprecated public synchronized void resetToDefaults() { ResetToDefaults(); }
    @Deprecated public synchronized KeyStroke getKeyStroke(AppShortcut shortcut) { return GetKeyStroke(shortcut); }
    @Deprecated public synchronized String getKeyText(AppShortcut shortcut) { return GetKeyText(shortcut); }
    @Deprecated public synchronized void setKeyStroke(AppShortcut shortcut, KeyStroke keyStroke) { SetKeyStroke(shortcut, keyStroke); }
    @Deprecated public synchronized AppShortcut getShortcutForKeyStroke(KeyStroke keyStroke) { return GetShortcutForKeyStroke(keyStroke); }
    @Deprecated public synchronized String toConfigValue(AppShortcut shortcut) { return ToConfigValue(shortcut); }
    @Deprecated public synchronized void loadFromConfigValue(AppShortcut shortcut, String value) { LoadFromConfigValue(shortcut, value); }
    @Deprecated public static KeyStroke normalize(KeyStroke keyStroke) { return Normalise(keyStroke); }
    @Deprecated public static boolean isModifierKey(int keyCode) { return IsModifierKey(keyCode); }
    @Deprecated public static String formatKeyStroke(KeyStroke keyStroke) { return FormatKeyStroke(keyStroke); }
}
