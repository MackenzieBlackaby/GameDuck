package com.blackaby.Misc;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.Platform.EmulatorButton;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores controller bindings for backend-defined emulator buttons.
 */
public final class ControllerBindings {

    private final Map<String, ControllerBinding> bindings = new HashMap<>();
    private volatile long version = 1L;

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
        SeedGbDefaults();
        version++;
    }

    /**
     * Returns the controller binding assigned to an emulated button.
     *
     * @param backendId backend identifier
     * @param button button to inspect
     * @return assigned controller binding
     */
    public synchronized ControllerBinding GetBinding(String backendId, EmulatorButton button) {
        return button == null ? null : bindings.get(Key(backendId, button.id()));
    }

    public synchronized ControllerBinding GetBinding(GBButton button) {
        return GetBinding(GBRom.systemId, button);
    }

    public synchronized ControllerBinding GetBinding(EmulatorButton button) {
        return GetBinding(GBRom.systemId, button);
    }

    /**
     * Returns the assigned controller binding as readable text.
     *
     * @param backendId backend identifier
     * @param button button to inspect
     * @return display text for the assigned input
     */
    public synchronized String GetBindingText(String backendId, EmulatorButton button) {
        ControllerBinding binding = GetBinding(backendId, button);
        return binding == null ? "Unbound" : binding.ToDisplayText();
    }

    public synchronized String GetBindingText(GBButton button) {
        return GetBindingText(GBRom.systemId, button);
    }

    public synchronized String GetBindingText(EmulatorButton button) {
        return GetBindingText(GBRom.systemId, button);
    }

    /**
     * Assigns a new controller binding to an emulated button.
     *
     * @param backendId backend identifier
     * @param button button to update
     * @param binding replacement controller binding
     */
    public synchronized void SetBinding(String backendId, EmulatorButton button, ControllerBinding binding) {
        if (button == null || binding == null) {
            return;
        }

        String bindingKey = Key(backendId, button.id());
        ControllerBinding currentBinding = bindings.get(bindingKey);
        String existingButtonId = GetButtonIdForBinding(backendId, binding);
        bindings.put(bindingKey, binding);

        if (existingButtonId != null && !existingButtonId.equals(button.id())) {
            bindings.put(Key(backendId, existingButtonId), currentBinding);
        }
        version++;
    }

    public synchronized void SetBinding(GBButton button, ControllerBinding binding) {
        SetBinding(GBRom.systemId, button, binding);
    }

    public synchronized void SetBinding(EmulatorButton button, ControllerBinding binding) {
        SetBinding(GBRom.systemId, button, binding);
    }

    /**
     * Finds the emulated button already using the supplied binding.
     *
     * @param backendId backend identifier
     * @param buttons active backend buttons
     * @param binding binding to look up
     * @return matching button, or {@code null} if none is bound
     */
    public synchronized EmulatorButton GetButtonForBinding(String backendId, Iterable<? extends EmulatorButton> buttons,
            ControllerBinding binding) {
        if (binding == null || buttons == null) {
            return null;
        }

        for (EmulatorButton button : buttons) {
            if (binding.equals(GetBinding(backendId, button))) {
                return button;
            }
        }
        return null;
    }

    public synchronized GBButton GetButtonForBinding(ControllerBinding binding) {
        String buttonId = GetButtonIdForBinding(GBRom.systemId, binding);
        return GBButton.FromId(buttonId);
    }

    public synchronized Map<String, ControllerBinding> SnapshotBindings(String backendId) {
        Map<String, ControllerBinding> snapshot = new HashMap<>();
        String prefix = (backendId == null ? "" : backendId) + "|";
        for (Map.Entry<String, ControllerBinding> entry : bindings.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                snapshot.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return Map.copyOf(snapshot);
    }

    public synchronized Map<String, ControllerBinding> SnapshotBindings() {
        return SnapshotBindings(GBRom.systemId);
    }

    public long Version() {
        return version;
    }

    private String GetButtonIdForBinding(String backendId, ControllerBinding binding) {
        if (binding == null) {
            return null;
        }

        String prefix = (backendId == null ? "" : backendId) + "|";
        for (Map.Entry<String, ControllerBinding> entry : bindings.entrySet()) {
            if (entry.getKey().startsWith(prefix) && binding.equals(entry.getValue())) {
                return entry.getKey().substring(prefix.length());
            }
        }
        return null;
    }

    private void SeedGbDefaults() {
        bindings.put(Key(GBRom.systemId, GBButton.UP.id()), ControllerBinding.Pov(ControllerBinding.Direction.UP));
        bindings.put(Key(GBRom.systemId, GBButton.DOWN.id()), ControllerBinding.Pov(ControllerBinding.Direction.DOWN));
        bindings.put(Key(GBRom.systemId, GBButton.LEFT.id()), ControllerBinding.Pov(ControllerBinding.Direction.LEFT));
        bindings.put(Key(GBRom.systemId, GBButton.RIGHT.id()), ControllerBinding.Pov(ControllerBinding.Direction.RIGHT));
        bindings.put(Key(GBRom.systemId, GBButton.A.id()), ControllerBinding.Button("0"));
        bindings.put(Key(GBRom.systemId, GBButton.B.id()), ControllerBinding.Button("1"));
        bindings.put(Key(GBRom.systemId, GBButton.START.id()), ControllerBinding.Button("7"));
        bindings.put(Key(GBRom.systemId, GBButton.SELECT.id()), ControllerBinding.Button("6"));
    }

    private String Key(String backendId, String buttonId) {
        return (backendId == null ? "" : backendId) + "|" + (buttonId == null ? "" : buttonId);
    }
}
