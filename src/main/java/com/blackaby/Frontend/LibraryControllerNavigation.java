package com.blackaby.Frontend;

import com.blackaby.Misc.Settings;
import java.awt.Window;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Focus-scoped library input using the existing configured Game Boy bindings. */
final class LibraryControllerNavigation implements AutoCloseable {
    private final Window window;
    private final Consumer<String> action;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "gameduck-library-controller");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean polling = new AtomicBoolean();
    private final Timer timer;
    private Set<String> previous = Set.of();
    private boolean armed;
    private boolean closed;

    LibraryControllerNavigation(Window window, Consumer<String> action) {
        this.window = window;
        this.action = action;
        timer = new Timer(75, event -> poll());
        window.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override public void windowLostFocus(java.awt.event.WindowEvent event) {
                armed = false;
                previous = Set.of();
            }
        });
    }

    void start() {
        timer.start();
    }

    private void poll() {
        if (closed || !window.isActive() || !Settings.controllerInputEnabled) {
            armed = false;
            previous = Set.of();
            return;
        }
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        worker.execute(() -> {
            try {
                Set<String> pressed = ControllerInputService.Shared().PollBoundButtons();
                SwingUtilities.invokeLater(() -> accept(pressed));
            } finally {
                polling.set(false);
            }
        });
    }

    private void accept(Set<String> pressed) {
        if (closed || !window.isActive() || !Settings.controllerInputEnabled) {
            armed = false;
            previous = Set.of();
            return;
        }
        // Require a neutral sample after focus changes, avoiding accidental launches.
        if (!armed) {
            armed = pressed.isEmpty();
            previous = pressed;
            return;
        }
        Set<String> old = previous;
        previous = pressed;
        for (String button : new String[] {"B", "A", "START", "SELECT", "UP", "DOWN", "LEFT", "RIGHT"}) {
            if (pressed.contains(button) && !old.contains(button)) {
                action.accept(button);
                break;
            }
        }
    }

    @Override public void close() {
        closed = true;
        timer.stop();
        worker.shutdownNow();
    }
}
