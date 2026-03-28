package com.blackaby.Misc;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Backend.Platform.EmulatorButton;

import java.util.EnumMap;

/**
 * Stores controller bindings for the emulated Game Boy buttons.
 */
public final class ControllerBindings {

    private final EnumMap<GBButton, ControllerBinding> bindings = new EnumMap<>(GBButton.class);

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
        bindings.put(GBButton.UP, ControllerBinding.Pov(ControllerBinding.Direction.UP));
        bindings.put(GBButton.DOWN, ControllerBinding.Pov(ControllerBinding.Direction.DOWN));
        bindings.put(GBButton.LEFT, ControllerBinding.Pov(ControllerBinding.Direction.LEFT));
        bindings.put(GBButton.RIGHT, ControllerBinding.Pov(ControllerBinding.Direction.RIGHT));
        bindings.put(GBButton.A, ControllerBinding.Button("0"));
        bindings.put(GBButton.B, ControllerBinding.Button("1"));
        bindings.put(GBButton.START, ControllerBinding.Button("7"));
        bindings.put(GBButton.SELECT, ControllerBinding.Button("6"));
    }

    /**
     * Returns the controller binding assigned to an emulated button.
     *
     * @param button button to inspect
     * @return assigned controller binding
     */
    public synchronized ControllerBinding GetBinding(GBButton button) {
        return bindings.get(button);
    }

    public synchronized ControllerBinding GetBinding(EmulatorButton button) {
        return button instanceof GBButton joypadButton ? GetBinding(joypadButton) : null;
    }

    /**
     * Returns the assigned controller binding as readable text.
     *
     * @param button button to inspect
     * @return display text for the assigned input
     */
    public synchronized String GetBindingText(GBButton button) {
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
    public synchronized void SetBinding(GBButton button, ControllerBinding binding) {
        if (binding == null) {
            return;
        }

        ControllerBinding currentBinding = GetBinding(button);
        GBButton existingButton = GetButtonForBinding(binding);
        bindings.put(button, binding);

        if (existingButton != null && existingButton != button) {
            bindings.put(existingButton, currentBinding);
        }
    }

    public synchronized void SetBinding(EmulatorButton button, ControllerBinding binding) {
        if (button instanceof GBButton joypadButton) {
            SetBinding(joypadButton, binding);
        }
    }

    /**
     * Finds the emulated button already using the supplied binding.
     *
     * @param binding binding to look up
     * @return matching button, or {@code null} if none is bound
     */
    public synchronized GBButton GetButtonForBinding(ControllerBinding binding) {
        if (binding == null) {
            return null;
        }

        for (GBButton button : GBButton.values()) {
            if (binding.equals(bindings.get(button))) {
                return button;
            }
        }
        return null;
    }
}


