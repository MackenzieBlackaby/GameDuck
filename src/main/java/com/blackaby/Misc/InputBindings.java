package com.blackaby.Misc;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Backend.Platform.EmulatorButton;

import java.awt.event.KeyEvent;
import java.util.EnumMap;

/**
 * Stores the keyboard map for the emulated Game Boy buttons.
 */
public final class InputBindings {

    private final EnumMap<GBButton, Integer> bindings = new EnumMap<>(GBButton.class);

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
        bindings.put(GBButton.UP, KeyEvent.VK_UP);
        bindings.put(GBButton.DOWN, KeyEvent.VK_DOWN);
        bindings.put(GBButton.LEFT, KeyEvent.VK_LEFT);
        bindings.put(GBButton.RIGHT, KeyEvent.VK_RIGHT);
        bindings.put(GBButton.A, KeyEvent.VK_X);
        bindings.put(GBButton.B, KeyEvent.VK_Z);
        bindings.put(GBButton.START, KeyEvent.VK_ENTER);
        bindings.put(GBButton.SELECT, KeyEvent.VK_BACK_SPACE);
    }

    /**
     * Returns the key code assigned to an emulated button.
     *
     * @param button button to inspect
     * @return assigned host key code
     */
    public synchronized int GetKeyCode(GBButton button) {
        return bindings.getOrDefault(button, KeyEvent.VK_UNDEFINED);
    }

    public synchronized int GetKeyCode(EmulatorButton button) {
        return button instanceof GBButton joypadButton
                ? GetKeyCode(joypadButton)
                : KeyEvent.VK_UNDEFINED;
    }

    /**
     * Returns the assigned key as readable text.
     *
     * @param button button to inspect
     * @return display text for the assigned key
     */
    public synchronized String GetKeyText(GBButton button) {
        int keyCode = GetKeyCode(button);
        if (keyCode == KeyEvent.VK_UNDEFINED) {
            return "Unbound";
        }
        return KeyEvent.getKeyText(keyCode);
    }

    public synchronized String GetKeyText(EmulatorButton button) {
        int keyCode = GetKeyCode(button);
        return keyCode == KeyEvent.VK_UNDEFINED ? "Unbound" : KeyEvent.getKeyText(keyCode);
    }

    /**
     * Assigns a new host key to an emulated button.
     *
     * @param button button to update
     * @param keyCode replacement host key code
     */
    public synchronized void SetKeyCode(GBButton button, int keyCode) {
        if (keyCode == KeyEvent.VK_UNDEFINED) {
            return;
        }

        int currentKeyCode = GetKeyCode(button);
        GBButton existingButton = GetButtonForKeyCode(keyCode);
        bindings.put(button, keyCode);

        if (existingButton != null && existingButton != button) {
            bindings.put(existingButton, currentKeyCode);
        }
    }

    public synchronized void SetKeyCode(EmulatorButton button, int keyCode) {
        if (button instanceof GBButton joypadButton) {
            SetKeyCode(joypadButton, keyCode);
        }
    }

    /**
     * Finds the emulated button already using a host key.
     *
     * @param keyCode host key code to look up
     * @return matching button, or {@code null} if none is bound
     */
    public synchronized GBButton GetButtonForKeyCode(int keyCode) {
        for (GBButton button : GBButton.values()) {
            if (bindings.getOrDefault(button, KeyEvent.VK_UNDEFINED) == keyCode) {
                return button;
            }
        }
        return null;
    }

    public synchronized EmulatorButton GetButtonForKeyCode(Iterable<? extends EmulatorButton> buttons, int keyCode) {
        if (buttons == null) {
            return null;
        }
        for (EmulatorButton button : buttons) {
            if (GetKeyCode(button) == keyCode) {
                return button;
            }
        }
        return null;
    }

}


