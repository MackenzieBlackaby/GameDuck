package com.blackaby.Misc;

import com.blackaby.Backend.Emulation.Peripherals.DuckJoypad;
import com.blackaby.Backend.Platform.EmulatorButton;

import java.util.EnumMap;

/**
 * Stores controller bindings for the emulated Game Boy buttons.
 */
public final class ControllerBindings {

    private final EnumMap<DuckJoypad.Button, ControllerBinding> bindings = new EnumMap<>(DuckJoypad.Button.class);

    /**
     * Creates a binding set initialised with the default controller map.
     */
    public ControllerBindings() {
        ResetToDefaults();
    }

    /**
     * Restores the default controller map.
     */
    public synchronized void ResetToDefaults() {
        bindings.clear();
        bindings.put(DuckJoypad.Button.UP, ControllerBinding.Pov(ControllerBinding.Direction.UP));
        bindings.put(DuckJoypad.Button.DOWN, ControllerBinding.Pov(ControllerBinding.Direction.DOWN));
        bindings.put(DuckJoypad.Button.LEFT, ControllerBinding.Pov(ControllerBinding.Direction.LEFT));
        bindings.put(DuckJoypad.Button.RIGHT, ControllerBinding.Pov(ControllerBinding.Direction.RIGHT));
        bindings.put(DuckJoypad.Button.A, ControllerBinding.Button("0"));
        bindings.put(DuckJoypad.Button.B, ControllerBinding.Button("1"));
        bindings.put(DuckJoypad.Button.START, ControllerBinding.Button("7"));
        bindings.put(DuckJoypad.Button.SELECT, ControllerBinding.Button("6"));
    }

    /**
     * Returns the controller binding assigned to an emulated button.
     *
     * @param button button to inspect
     * @return assigned controller binding
     */
    public synchronized ControllerBinding GetBinding(DuckJoypad.Button button) {
        return bindings.get(button);
    }

    public synchronized ControllerBinding GetBinding(EmulatorButton button) {
        return button instanceof DuckJoypad.Button joypadButton ? GetBinding(joypadButton) : null;
    }

    /**
     * Returns the assigned controller binding as readable text.
     *
     * @param button button to inspect
     * @return display text for the assigned input
     */
    public synchronized String GetBindingText(DuckJoypad.Button button) {
        ControllerBinding binding = GetBinding(button);
        return binding == null ? "Unbound" : binding.ToDisplayText();
    }

    public synchronized String GetBindingText(EmulatorButton button) {
        ControllerBinding binding = GetBinding(button);
        return binding == null ? "Unbound" : binding.ToDisplayText();
    }

    /**
     * Assigns a new controller binding to an emulated button.
     *
     * @param button  button to update
     * @param binding replacement controller binding
     */
    public synchronized void SetBinding(DuckJoypad.Button button, ControllerBinding binding) {
        if (binding == null) {
            return;
        }

        ControllerBinding currentBinding = GetBinding(button);
        DuckJoypad.Button existingButton = GetButtonForBinding(binding);
        bindings.put(button, binding);

        if (existingButton != null && existingButton != button) {
            bindings.put(existingButton, currentBinding);
        }
    }

    public synchronized void SetBinding(EmulatorButton button, ControllerBinding binding) {
        if (button instanceof DuckJoypad.Button joypadButton) {
            SetBinding(joypadButton, binding);
        }
    }

    /**
     * Finds the emulated button already using the supplied binding.
     *
     * @param binding binding to look up
     * @return matching button, or {@code null} if none is bound
     */
    public synchronized DuckJoypad.Button GetButtonForBinding(ControllerBinding binding) {
        if (binding == null) {
            return null;
        }

        for (DuckJoypad.Button button : DuckJoypad.Button.values()) {
            if (binding.equals(bindings.get(button))) {
                return button;
            }
        }
        return null;
    }
}
