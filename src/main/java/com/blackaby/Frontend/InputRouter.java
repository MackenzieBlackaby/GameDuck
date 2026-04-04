package com.blackaby.Frontend;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Backend.Helpers.GUIActions;
import com.blackaby.Backend.Platform.EmulatorButton;
import com.blackaby.Backend.Platform.EmulatorProfile;
import com.blackaby.Backend.Platform.EmulatorRuntime;
import com.blackaby.Misc.AppShortcut;
import com.blackaby.Misc.AppShortcutBindings;
import com.blackaby.Misc.Settings;

import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Routes host keyboard input to the emulated joypad while the main window has
 * focus.
 */
public final class InputRouter implements KeyEventDispatcher {

    private static final long shutdownAwaitMillis = 200L;

    private record ControlButtonState(String buttonId, GBButton gameBoyButton) {
    }

    private final MainWindow mainWindow;
    private final EmulatorRuntime emulation;
    private final EmulatorProfile profile;
    private final ControllerInputService controllerInputService;
    private final Object inputStateLock = new Object();
    private final Object controllerPollingLock = new Object();
    private final Set<Integer> pressedKeyCodes = new HashSet<>();
    private final Set<Integer> consumedShortcutKeyCodes = new HashSet<>();
    private final Set<String> keyboardPressedButtons = new HashSet<>();
    private final Set<String> controllerPressedButtons = new HashSet<>();
    private final EnumSet<AppShortcut> controllerPressedShortcuts = EnumSet.noneOf(AppShortcut.class);
    private final List<ControlButtonState> controlButtons;
    private final ScheduledExecutorService controllerPollingExecutor = Executors.newSingleThreadScheduledExecutor(run -> {
        Thread thread = new Thread(run, "gameduck-controller-input");
        thread.setDaemon(true);
        return thread;
    });
    private final WindowAdapter windowLifecycleListener = new WindowAdapter() {
        @Override
        public void windowGainedFocus(WindowEvent event) {
            windowFocused = true;
            RefreshControllerPollingState();
        }

        @Override
        public void windowLostFocus(WindowEvent event) {
            windowFocused = false;
            RefreshControllerPollingState();
        }

        @Override
        public void windowClosing(WindowEvent event) {
            windowFocused = false;
            RefreshControllerPollingState();
        }

        @Override
        public void windowClosed(WindowEvent event) {
            windowFocused = false;
            RefreshControllerPollingState();
        }
    };
    private ScheduledFuture<?> controllerPollingTask;
    private volatile boolean routedInputActive;
    private volatile boolean installed;
    private volatile boolean uninstalled;
    private volatile boolean windowFocused;

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
        this.controllerInputService = ControllerInputService.Shared();
        this.windowFocused = mainWindow != null && mainWindow.isActive();

        List<ControlButtonState> buttonStates = new ArrayList<>();
        for (EmulatorButton button : profile.controlButtons()) {
            buttonStates.add(new ControlButtonState(button.id(), GBButton.FromId(button.id())));
        }
        this.controlButtons = List.copyOf(buttonStates);
    }

    /**
     * Registers the router with the current keyboard focus manager.
     */
    public void Install() {
        if (installed || uninstalled) {
            return;
        }

        installed = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
        mainWindow.addWindowFocusListener(windowLifecycleListener);
        mainWindow.addWindowListener(windowLifecycleListener);
        RefreshControllerPollingState();
    }

    /**
     * Removes the router from host input routing and stops controller polling.
     */
    public void Uninstall() {
        if (uninstalled) {
            return;
        }

        uninstalled = true;
        installed = false;
        windowFocused = false;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
        mainWindow.removeWindowFocusListener(windowLifecycleListener);
        mainWindow.removeWindowListener(windowLifecycleListener);

        synchronized (controllerPollingLock) {
            if (controllerPollingTask != null) {
                controllerPollingTask.cancel(false);
                controllerPollingTask = null;
            }
        }

        ClearAllInputStates();
        controllerPollingExecutor.shutdownNow();
        try {
            controllerPollingExecutor.awaitTermination(shutdownAwaitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Re-evaluates whether controller polling should be running.
     */
    public void RefreshControllerPollingState() {
        boolean shouldPoll = ShouldPollControllerInput();
        synchronized (controllerPollingLock) {
            if (uninstalled || controllerPollingExecutor.isShutdown()) {
                return;
            }

            if (!shouldPoll) {
                if (controllerPollingTask != null) {
                    controllerPollingTask.cancel(false);
                    controllerPollingTask = null;
                }
            } else if (controllerPollingTask == null || controllerPollingTask.isDone()) {
                controllerPollingTask = controllerPollingExecutor.schedule(this::RunControllerPollingCycle, 0L,
                        TimeUnit.MILLISECONDS);
            }
        }

        if (!shouldPoll && routedInputActive) {
            ClearAllInputStates();
            routedInputActive = false;
        }
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

    private void RunControllerPollingCycle() {
        synchronized (controllerPollingLock) {
            controllerPollingTask = null;
        }

        if (!ShouldPollControllerInput()) {
            return;
        }

        PollControllerInput();

        synchronized (controllerPollingLock) {
            if (uninstalled || controllerPollingExecutor.isShutdown() || !ShouldPollControllerInput()) {
                return;
            }
            controllerPollingTask = controllerPollingExecutor.schedule(this::RunControllerPollingCycle,
                    Settings.controllerPollingMode.PollIntervalMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void PollControllerInput() {
        if (!ShouldRouteInput()) {
            if (routedInputActive) {
                ClearAllInputStates();
                routedInputActive = false;
            }
            return;
        }

        routedInputActive = true;
        ControllerInputService.ControllerPollSnapshot controllerPollSnapshot = controllerInputService.PollInputSnapshot();
        ApplyControllerState(controllerPollSnapshot.boundButtons());
        ApplyControllerShortcutState(controllerPollSnapshot.pressedShortcuts());
    }

    private void SetKeyboardButtonState(EmulatorButton button, boolean pressed) {
        String buttonId = button.id();
        synchronized (inputStateLock) {
            boolean changed = pressed
                    ? keyboardPressedButtons.add(buttonId)
                    : keyboardPressedButtons.remove(buttonId);
            if (!changed) {
                return;
            }
            ApplyCombinedState(buttonId);
        }
    }

    private void ApplyControllerState(EnumSet<GBButton> pressedButtons) {
        synchronized (inputStateLock) {
            for (ControlButtonState button : controlButtons) {
                boolean nextPressed = button.gameBoyButton() != null && pressedButtons.contains(button.gameBoyButton());
                boolean currentlyPressed = controllerPressedButtons.contains(button.buttonId());
                if (nextPressed == currentlyPressed) {
                    continue;
                }
                if (nextPressed) {
                    controllerPressedButtons.add(button.buttonId());
                } else {
                    controllerPressedButtons.remove(button.buttonId());
                }
                ApplyCombinedState(button.buttonId());
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
            for (ControlButtonState button : controlButtons) {
                emulation.SetButtonPressed(button.buttonId(), false);
            }
        }
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
        return installed
                && !uninstalled
                && windowFocused
                && mainWindow.isDisplayable()
                && activeWindow == mainWindow;
    }

    private boolean ShouldPollControllerInput() {
        return installed
                && !uninstalled
                && Settings.controllerInputEnabled
                && windowFocused
                && mainWindow.isDisplayable();
    }
}
