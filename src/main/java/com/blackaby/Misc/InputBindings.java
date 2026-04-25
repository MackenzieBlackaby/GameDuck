package com.blackaby.Misc;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.Platform.EmulatorButton;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores keyboard bindings for backend-defined emulator buttons.
 */
public final class InputBindings {

    private final Map<String, Integer> bindings = new HashMap<>();

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
        SeedGbDefaults();
    }

    /**
     * Returns the key code assigned to an emulated button.
     *
     * @param backendId backend identifier
     * @param button button to inspect
     * @return assigned host key code
     */
    public synchronized int GetKeyCode(String backendId, EmulatorButton button) {
        if (button == null) {
            return KeyEvent.VK_UNDEFINED;
        }
        return bindings.getOrDefault(Key(backendId, button.id()), KeyEvent.VK_UNDEFINED);
    }

    public synchronized int GetKeyCode(GBButton button) {
        return GetKeyCode(GBRom.systemId, button);
    }

    public synchronized int GetKeyCode(EmulatorButton button) {
        return GetKeyCode(GBRom.systemId, button);
    }

    /**
     * Returns the assigned key as readable text.
     *
     * @param backendId backend identifier
     * @param button button to inspect
     * @return display text for the assigned key
     */
    public synchronized String GetKeyText(String backendId, EmulatorButton button) {
        int keyCode = GetKeyCode(backendId, button);
        return keyCode == KeyEvent.VK_UNDEFINED ? "Unbound" : KeyEvent.getKeyText(keyCode);
    }

    public synchronized String GetKeyText(GBButton button) {
        return GetKeyText(GBRom.systemId, button);
    }

    public synchronized String GetKeyText(EmulatorButton button) {
        return GetKeyText(GBRom.systemId, button);
    }

    /**
     * Assigns a new host key to an emulated button.
     *
     * @param backendId backend identifier
     * @param button button to update
     * @param keyCode replacement host key code
     */
    public synchronized void SetKeyCode(String backendId, EmulatorButton button, int keyCode) {
        if (button == null || keyCode == KeyEvent.VK_UNDEFINED) {
            return;
        }

        String bindingKey = Key(backendId, button.id());
        int currentKeyCode = bindings.getOrDefault(bindingKey, KeyEvent.VK_UNDEFINED);
        EmulatorButton existingButton = GetButtonForKeyCodeForBackend(backendId, null, keyCode);
        bindings.put(bindingKey, keyCode);

        if (existingButton != null && !existingButton.id().equals(button.id())) {
            bindings.put(Key(backendId, existingButton.id()), currentKeyCode);
        }
    }

    public synchronized void SetKeyCode(GBButton button, int keyCode) {
        SetKeyCode(GBRom.systemId, button, keyCode);
    }

    public synchronized void SetKeyCode(EmulatorButton button, int keyCode) {
        SetKeyCode(GBRom.systemId, button, keyCode);
    }

    /**
     * Finds the emulated button already using a host key.
     *
     * @param buttons buttons available on the active backend
     * @param backendId backend identifier
     * @param keyCode host key code to look up
     * @return matching button, or {@code null} if none is bound
     */
    public synchronized EmulatorButton GetButtonForKeyCode(Iterable<? extends EmulatorButton> buttons, String backendId,
            int keyCode) {
        if (buttons == null) {
            return null;
        }
        for (EmulatorButton button : buttons) {
            if (GetKeyCode(backendId, button) == keyCode) {
                return button;
            }
        }
        return null;
    }

    public synchronized EmulatorButton GetButtonForKeyCode(Iterable<? extends EmulatorButton> buttons, int keyCode) {
        return GetButtonForKeyCode(buttons, GBRom.systemId, keyCode);
    }

    private EmulatorButton GetButtonForKeyCodeForBackend(String backendId, Iterable<? extends EmulatorButton> buttons,
            int keyCode) {
        if (buttons != null) {
            return GetButtonForKeyCode(buttons, backendId, keyCode);
        }

        if (GBRom.systemId.equals(backendId)) {
            for (GBButton button : GBButton.values()) {
                if (GetKeyCode(backendId, button) == keyCode) {
                    return button;
                }
            }
        }

        return null;
    }

    private void SeedGbDefaults() {
        bindings.put(Key(GBRom.systemId, GBButton.UP.id()), KeyEvent.VK_UP);
        bindings.put(Key(GBRom.systemId, GBButton.DOWN.id()), KeyEvent.VK_DOWN);
        bindings.put(Key(GBRom.systemId, GBButton.LEFT.id()), KeyEvent.VK_LEFT);
        bindings.put(Key(GBRom.systemId, GBButton.RIGHT.id()), KeyEvent.VK_RIGHT);
        bindings.put(Key(GBRom.systemId, GBButton.A.id()), KeyEvent.VK_X);
        bindings.put(Key(GBRom.systemId, GBButton.B.id()), KeyEvent.VK_Z);
        bindings.put(Key(GBRom.systemId, GBButton.START.id()), KeyEvent.VK_ENTER);
        bindings.put(Key(GBRom.systemId, GBButton.SELECT.id()), KeyEvent.VK_BACK_SPACE);
    }

    private String Key(String backendId, String buttonId) {
        return (backendId == null ? "" : backendId) + "|" + (buttonId == null ? "" : buttonId);
    }
}
