package com.blackaby.Misc;

import com.blackaby.Backend.Emulation.Peripherals.DuckJoypad;

import java.awt.event.KeyEvent;
import java.util.EnumMap;

/**
 * Stores the keyboard map for the emulated Game Boy buttons.
 */
public final class InputBindings {

    private final EnumMap<DuckJoypad.Button, Integer> bindings = new EnumMap<>(DuckJoypad.Button.class);

    /**
     * Creates a binding set initialised with the default controls.
     */
    public InputBindings() {
        ResetToDefaults();
    }

    /**
     * Restores the default control map.
     */
    public synchronized void ResetToDefaults() {
        bindings.clear();
        bindings.put(DuckJoypad.Button.UP, KeyEvent.VK_UP);
        bindings.put(DuckJoypad.Button.DOWN, KeyEvent.VK_DOWN);
        bindings.put(DuckJoypad.Button.LEFT, KeyEvent.VK_LEFT);
        bindings.put(DuckJoypad.Button.RIGHT, KeyEvent.VK_RIGHT);
        bindings.put(DuckJoypad.Button.A, KeyEvent.VK_X);
        bindings.put(DuckJoypad.Button.B, KeyEvent.VK_Z);
        bindings.put(DuckJoypad.Button.START, KeyEvent.VK_ENTER);
        bindings.put(DuckJoypad.Button.SELECT, KeyEvent.VK_BACK_SPACE);
    }

    /**
     * Returns the key code assigned to an emulated button.
     *
     * @param button button to inspect
     * @return assigned host key code
     */
    public synchronized int GetKeyCode(DuckJoypad.Button button) {
        return bindings.getOrDefault(button, KeyEvent.VK_UNDEFINED);
    }

    /**
     * Returns the assigned key as readable text.
     *
     * @param button button to inspect
     * @return display text for the assigned key
     */
    public synchronized String GetKeyText(DuckJoypad.Button button) {
        int keyCode = GetKeyCode(button);
        if (keyCode == KeyEvent.VK_UNDEFINED) {
            return "Unbound";
        }
        return KeyEvent.getKeyText(keyCode);
    }

    /**
     * Assigns a new host key to an emulated button.
     *
     * @param button button to update
     * @param keyCode replacement host key code
     */
    public synchronized void SetKeyCode(DuckJoypad.Button button, int keyCode) {
        if (keyCode == KeyEvent.VK_UNDEFINED) {
            return;
        }

        int currentKeyCode = GetKeyCode(button);
        DuckJoypad.Button existingButton = GetButtonForKeyCode(keyCode);
        bindings.put(button, keyCode);

        if (existingButton != null && existingButton != button) {
            bindings.put(existingButton, currentKeyCode);
        }
    }

    /**
     * Finds the emulated button already using a host key.
     *
     * @param keyCode host key code to look up
     * @return matching button, or {@code null} if none is bound
     */
    public synchronized DuckJoypad.Button GetButtonForKeyCode(int keyCode) {
        for (DuckJoypad.Button button : DuckJoypad.Button.values()) {
            if (bindings.getOrDefault(button, KeyEvent.VK_UNDEFINED) == keyCode) {
                return button;
            }
        }
        return null;
    }

    @Deprecated public synchronized void resetToDefaults() { ResetToDefaults(); }
    @Deprecated public synchronized int getKeyCode(DuckJoypad.Button button) { return GetKeyCode(button); }
    @Deprecated public synchronized String getKeyText(DuckJoypad.Button button) { return GetKeyText(button); }
    @Deprecated public synchronized void setKeyCode(DuckJoypad.Button button, int keyCode) { SetKeyCode(button, keyCode); }
    @Deprecated public synchronized DuckJoypad.Button getButtonForKeyCode(int keyCode) { return GetButtonForKeyCode(keyCode); }
}
