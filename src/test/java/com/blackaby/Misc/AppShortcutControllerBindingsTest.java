package com.blackaby.Misc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AppShortcutControllerBindingsTest {

    @Test
    void defaultsAreUnbound() {
        AppShortcutControllerBindings bindings = new AppShortcutControllerBindings();

        for (AppShortcut shortcut : AppShortcut.values()) {
            assertNull(bindings.GetBinding(shortcut));
            assertEquals("Unbound", bindings.GetBindingText(shortcut));
        }
    }

    @Test
    void assigningExistingBindingSwapsShortcuts() {
        AppShortcutControllerBindings bindings = new AppShortcutControllerBindings();
        ControllerBinding openBinding = ControllerBinding.Button("2");
        ControllerBinding closeBinding = ControllerBinding.Button("3");

        bindings.SetBinding(AppShortcut.OPEN_GAME, openBinding);
        bindings.SetBinding(AppShortcut.CLOSE_GAME, closeBinding);
        bindings.SetBinding(AppShortcut.CLOSE_GAME, openBinding);

        assertEquals(openBinding, bindings.GetBinding(AppShortcut.CLOSE_GAME));
        assertEquals(closeBinding, bindings.GetBinding(AppShortcut.OPEN_GAME));
    }

    @Test
    void configRoundTripRestoresBinding() {
        AppShortcutControllerBindings bindings = new AppShortcutControllerBindings();
        ControllerBinding binding = ControllerBinding.Axis("rx", true);

        bindings.SetBinding(AppShortcut.PAUSE_GAME, binding);

        AppShortcutControllerBindings reloadedBindings = new AppShortcutControllerBindings();
        reloadedBindings.LoadFromConfigValue(AppShortcut.PAUSE_GAME, bindings.ToConfigValue(AppShortcut.PAUSE_GAME));

        assertEquals(binding, reloadedBindings.GetBinding(AppShortcut.PAUSE_GAME));
        assertEquals(binding.ToDisplayText(), reloadedBindings.GetBindingText(AppShortcut.PAUSE_GAME));
    }
}
