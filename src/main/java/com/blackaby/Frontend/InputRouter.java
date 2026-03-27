package com.blackaby.Frontend;

import com.blackaby.Backend.Helpers.GUIActions;
import com.blackaby.Backend.Platform.EmulatorButton;
import com.blackaby.Backend.Platform.EmulatorProfile;
import com.blackaby.Backend.Platform.EmulatorRuntime;
import com.blackaby.Misc.AppShortcut;
import com.blackaby.Misc.AppShortcutBindings;
import com.blackaby.Misc.ControllerBinding;
import com.blackaby.Misc.Settings;

import javax.swing.SwingUtilities;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Set;

/**
 * Routes host keyboard input to the emulated joypad while the main window has
 * focus.
 */
public final class InputRouter implements KeyEventDispatcher {

    private final MainWindow mainWindow;
    private final EmulatorRuntime emulation;
    private final EmulatorProfile profile;
    private final ControllerInputService controllerInputService = ControllerInputService.Shared();
    private final Object inputStateLock = new Object();
    private final Set<Integer> pressedKeyCodes = new HashSet<>();
    private final Set<Integer> consumedShortcutKeyCodes = new HashSet<>();
    private final Set<String> keyboardPressedButtons = new HashSet<>();
    private final Set<String> controllerPressedButtons = new HashSet<>();
    private final Set<String> polledControllerButtonIds = new HashSet<>();
    private final EnumSet<AppShortcut> controllerPressedShortcuts = EnumSet.noneOf(AppShortcut.class);
    private final ScheduledExecutorService controllerPollingExecutor = Executors.newSingleThreadScheduledExecutor(run -> {
        Thread thread = new Thread(run, "gameduck-controller-input");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean routedInputActive;

    /**
     * Creates an input router bound to the main window and emulator instance.
     *
     * @param mainWindow owning main window
     * @param emulation running emulator
     */
    public InputRouter(MainWindow mainWindow, EmulatorRuntime emulation, EmulatorProfile profile) {
        this.mainWindow = mainWindow;
        this.emulation = emulation;
        this.profile = profile;
        controllerPollingExecutor.scheduleAtFixedRate(this::PollControllerInput, 0L, 8L, TimeUnit.MILLISECONDS);
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
            ClearAllInputStates();
            return false;
        }

        if (HandleAppShortcut(event)) {
            return true;
        }

        EmulatorButton button = Settings.inputBindings.GetButtonForKeyCode(profile.controlButtons(), event.getKeyCode());
        if (button == null) {
            return false;
        }

        if (event.getID() == KeyEvent.KEY_PRESSED) {
            SetKeyboardButtonState(button, true);
        } else if (event.getID() == KeyEvent.KEY_RELEASED) {
            SetKeyboardButtonState(button, false);
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
        Runnable action = () -> new GUIActions(mainWindow, shortcut.Action(), emulation)
                .actionPerformed(new ActionEvent(mainWindow, ActionEvent.ACTION_PERFORMED, shortcut.name()));
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    private void PollControllerInput() {
        boolean shouldRouteInput = ShouldRouteInput();
        if (!shouldRouteInput) {
            if (routedInputActive) {
                ClearAllInputStates();
                routedInputActive = false;
            }
            return;
        }

        routedInputActive = true;
        ApplyControllerState(profile.controlButtons(), PollControllerButtonIds());
        ApplyControllerShortcutState(PollControllerShortcuts());
    }

    private void SetKeyboardButtonState(EmulatorButton button, boolean pressed) {
        String buttonId = button.id();
        synchronized (inputStateLock) {
            if (pressed) {
                keyboardPressedButtons.add(buttonId);
            } else {
                keyboardPressedButtons.remove(buttonId);
            }
            ApplyCombinedState(buttonId);
        }
    }

    private void ApplyControllerState(List<? extends EmulatorButton> buttons, Set<String> pressedButtons) {
        synchronized (inputStateLock) {
            for (EmulatorButton button : buttons) {
                String buttonId = button.id();
                boolean nextPressed = pressedButtons.contains(buttonId);
                boolean currentlyPressed = controllerPressedButtons.contains(buttonId);
                if (nextPressed == currentlyPressed) {
                    continue;
                }
                if (nextPressed) {
                    controllerPressedButtons.add(buttonId);
                } else {
                    controllerPressedButtons.remove(buttonId);
                }
                ApplyCombinedState(buttonId);
            }
        }
    }

    private void ApplyCombinedState(String buttonId) {
        emulation.SetButtonPressed(buttonId,
                keyboardPressedButtons.contains(buttonId) || controllerPressedButtons.contains(buttonId));
    }

    private void ClearAllInputStates() {
        synchronized (inputStateLock) {
            pressedKeyCodes.clear();
            consumedShortcutKeyCodes.clear();
            keyboardPressedButtons.clear();
            controllerPressedButtons.clear();
            controllerPressedShortcuts.clear();
            for (EmulatorButton button : profile.controlButtons()) {
                emulation.SetButtonPressed(button.id(), false);
            }
        }
    }

    private Set<String> PollControllerButtonIds() {
        polledControllerButtonIds.clear();
        for (EmulatorButton button : controllerInputService.PollBoundButtons()) {
            polledControllerButtonIds.add(button.id());
        }
        return polledControllerButtonIds;
    }

    private EnumSet<AppShortcut> PollControllerShortcuts() {
        EnumSet<AppShortcut> pressedShortcuts = EnumSet.noneOf(AppShortcut.class);
        if (!Settings.controllerInputEnabled) {
            return pressedShortcuts;
        }

        Set<ControllerBinding> activeInputs = Set.copyOf(controllerInputService.PollActiveInputs());
        for (AppShortcut shortcut : AppShortcut.values()) {
            ControllerBinding binding = Settings.appShortcutControllerBindings.GetBinding(shortcut);
            if (binding != null && activeInputs.contains(binding)) {
                pressedShortcuts.add(shortcut);
            }
        }
        return pressedShortcuts;
    }

    private void ApplyControllerShortcutState(EnumSet<AppShortcut> pressedShortcuts) {
        List<AppShortcut> shortcutsToTrigger = new ArrayList<>();
        synchronized (inputStateLock) {
            for (AppShortcut shortcut : AppShortcut.values()) {
                boolean nextPressed = pressedShortcuts.contains(shortcut);
                boolean currentlyPressed = controllerPressedShortcuts.contains(shortcut);
                if (nextPressed == currentlyPressed) {
                    continue;
                }

                if (nextPressed) {
                    controllerPressedShortcuts.add(shortcut);
                    shortcutsToTrigger.add(shortcut);
                } else {
                    controllerPressedShortcuts.remove(shortcut);
                }
            }
        }

        for (AppShortcut shortcut : shortcutsToTrigger) {
            TriggerShortcut(shortcut);
        }
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
}
