# GameDuck Frontend Optimization Recommendations

## Overview

This document captures frontend inefficiencies and improvement recommendations for GameDuck’s Swing UI. It focuses on leaks, high CPU usage, and resource management in the frontend package.

---

## 1. Cleanup and resource release

### MainWindow

- `DebugLogger.AddSerialListener(serialOutputListener)` is registered on startup.
- There is no corresponding `DebugLogger.RemoveSerialListener(serialOutputListener)` on window close.
- If the main window is recreated, listeners can accumulate and leak.

### InputRouter

- `InputRouter.Install()` registers a global `KeyEventDispatcher`.
- There is no `uninstall()` or lifecycle cleanup.
- This can keep input routing active after the window is closed.

### Executor and timer cleanup

- `InputRouter` creates a `ScheduledExecutorService` via `controllerPollingExecutor` and never shuts it down.
- `DebugLogger` creates a `serialDispatchExecutor` and never shuts it down.
- Any long-lived timer/executor should be stopped when the UI closes.

---

## 2. Controller polling inefficiency

### InputRouter polling frequency

- The controller poller runs every `8ms` using `scheduleAtFixedRate`.
- That is roughly 125Hz, which is too aggressive for UI input polling.
- This increases CPU usage unnecessarily.

### Suggested improvements

- Reduce polling frequency to `16ms` or `33ms`.
- Pause or significantly slow polling when the main window is not active.
- Resume normal polling only when the window regains focus.

---

## 3. Reflection overhead in ControllerInputService

### Expensive repeated reflection

- `Invoke(target, methodName)` calls `getMethod(...)` on each invocation.
- This happens during controller polling and component value reads.
- Repeated reflective lookup per poll is expensive.

### Suggested improvements

- Cache `Method` references for common operations such as `poll`, `getComponents`, `getPollData`, `getName`, etc.
- Store these method references in `ComponentHandle` or `ControllerHandle` when controllers are discovered.
- Avoid reflection in the hot path if possible.

---

## 4. Display rendering and buffer management

### DuckDisplay hot path issues

- The display renderer uses multiple large frame buffers and copies data every frame.
- `presentFrame()` and `RenderImageBufferLocked()` frequently copy full frame buffers.
- `initializeRenderBuffers(...)` allocates new arrays whenever shader scale or shader state changes.
- Nearest-neighbor expansion in `prepareShaderSource(...)` is expensive at full frame size.

### Suggested improvements

- Optimize the no-shader path to avoid extra buffer copies.
- Reuse existing buffers when the frame resolution and render scale remain unchanged.
- Reduce lock hold times in the hot rendering path.
- If possible, use the image raster directly for the final output instead of copying into `paintBuffer`.

---

## 5. DebugLogger threading

### Executor lifecycle

- `DebugLogger` uses a daemon executor to dispatch serial output.
- There is no shutdown method or cleanup path.

### Suggested improvements

- Add a `Shutdown()` method to close the executor cleanly on app exit.
- Ensure any registered listeners are removed before shutdown.

---

## 6. Recommended fix priorities

### High priority

1. Add cleanup for `InputRouter` (uninstall dispatcher, shutdown executor).
2. Remove `MainWindow` serial listener on dispose.
3. Stop timers/executors on window/dialog close.
4. Lower controller polling frequency.

### Medium priority

5. Cache reflection methods in `ControllerInputService`.
6. Avoid controller polling when the window is unfocused.

### Lower priority

7. Improve `DuckDisplay` buffer reuse and render path.
8. Add `DebugLogger.shutdown()`.

---

## 7. Implementation notes

### InputRouter changes

- Add `public void Uninstall()`.
- In `Uninstall()`:
    - remove dispatcher from `KeyboardFocusManager`
    - shutdown `controllerPollingExecutor` with a timeout
    - clear pressed state if needed
- Call `inputRouter.Uninstall()` from `MainWindow` close handler.

### MainWindow close handler

- Add `DebugLogger.RemoveSerialListener(serialOutputListener)` before disposing.
- Ensure `displayStatsTimer.stop()` is already called.

### ControllerInputService

- Create caches for reflective methods.
- Resolve methods once during controller discovery.
- Replace repeated `getMethod` calls with cached `Method` lookup.

---

## Summary

These changes reduce frontend CPU load and eliminate common resource leaks:

- avoid globally leaking listeners and dispatchers
- stop unnecessary high-frequency background polling
- avoid repeated reflection in the controller hot path
- reuse render buffers instead of reallocating frequently
- cleanly shut down background executors

Implementing these recommendations will make the frontend more efficient and stable without changing core emulator behavior.
