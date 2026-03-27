package com.blackaby.Misc;

import java.util.EnumMap;

/**
 * Stores controller bindings for host window shortcuts.
 */
public final class AppShortcutControllerBindings {

    private final EnumMap<AppShortcut, ControllerBinding> bindings = new EnumMap<>(AppShortcut.class);

    /**
     * Creates a controller shortcut binding set initialised with default values.
     */
    public AppShortcutControllerBindings() {
        ResetToDefaults();
    }

    /**
     * Restores the default controller shortcut map.
     */
    public synchronized void ResetToDefaults() {
        bindings.clear();
        for (AppShortcut shortcut : AppShortcut.values()) {
            bindings.put(shortcut, null);
        }
    }

    /**
     * Returns the active controller binding for a shortcut.
     *
     * @param shortcut shortcut to read
     * @return assigned controller binding, or {@code null} if none is set
     */
    public synchronized ControllerBinding GetBinding(AppShortcut shortcut) {
        return bindings.get(shortcut);
    }

    /**
     * Returns the assigned controller binding as readable text.
     *
     * @param shortcut shortcut to inspect
     * @return display text for the assigned input
     */
    public synchronized String GetBindingText(AppShortcut shortcut) {
        ControllerBinding binding = GetBinding(shortcut);
        return binding == null ? "Unbound" : binding.ToDisplayText();
    }

    /**
     * Assigns a new controller binding to a shortcut.
     *
     * @param shortcut shortcut to update
     * @param binding replacement controller binding
     */
    public synchronized void SetBinding(AppShortcut shortcut, ControllerBinding binding) {
        if (shortcut == null || binding == null) {
            return;
        }

        ControllerBinding currentBinding = GetBinding(shortcut);
        AppShortcut existingShortcut = GetShortcutForBinding(binding);
        bindings.put(shortcut, binding);

        if (existingShortcut != null && existingShortcut != shortcut) {
            bindings.put(existingShortcut, currentBinding);
        }
    }

    /**
     * Finds the shortcut already using the supplied binding.
     *
     * @param binding binding to look up
     * @return matching shortcut, or {@code null} if none is bound
     */
    public synchronized AppShortcut GetShortcutForBinding(ControllerBinding binding) {
        if (binding == null) {
            return null;
        }

        for (AppShortcut shortcut : AppShortcut.values()) {
            if (binding.equals(bindings.get(shortcut))) {
                return shortcut;
            }
        }
        return null;
    }

    /**
     * Serialises a shortcut controller binding for the config file.
     *
     * @param shortcut shortcut to serialise
     * @return compact config value
     */
    public synchronized String ToConfigValue(AppShortcut shortcut) {
        ControllerBinding binding = GetBinding(shortcut);
        return binding == null ? "" : binding.ToConfigValue();
    }

    /**
     * Loads one controller shortcut binding from a config value.
     *
     * @param shortcut shortcut to update
     * @param value persisted value to parse
     */
    public synchronized void LoadFromConfigValue(AppShortcut shortcut, String value) {
        ControllerBinding binding = ControllerBinding.FromConfigValue(value);
        if (shortcut != null && binding != null) {
            SetBinding(shortcut, binding);
        }
    }
}
