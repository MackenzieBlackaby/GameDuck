# Backend Architecture Summary

## Overview

The backend is split into:
- a generic host API under `src/main/java/com/blackaby/Backend/Platform`
- a concrete Game Boy implementation under `src/main/java/com/blackaby/Backend/GB`

This design keeps the frontend backend-neutral while allowing the emulator runtime to provide game-specific behavior.

---

## Host-facing backend API

### `EmulatorBackend`
- Located in `Backend/Platform/EmulatorBackend.java`
- Factory interface for a concrete emulator backend
- Methods:
  - `Profile()`
  - `CreateRuntime(host, display)`

### `EmulatorProfile`
- Located in `Backend/Platform/EmulatorProfile.java`
- Contains backend metadata:
  - backend ID
  - display name
  - display spec
  - capabilities
  - control buttons
  - supported game and patch file extensions

### `EmulatorRuntime`
- Located in `Backend/Platform/EmulatorRuntime.java`
- Main runtime contract consumed by the frontend
- Provides lifecycle and emulator controls:
  - start, pause, restart, stop
  - save/load states
  - save data management
  - cheats
  - input handling
  - patch application

### `EmulatorHost`
- Located in `Backend/Platform/EmulatorHost.java`
- UI callback interface used by the runtime
- Methods:
  - `SetSubtitle(...)`
  - `SetLoadedGame(...)`
  - `LoadGameArt(...)`
  - `ClearGameArt()`

### Data models
- `EmulatorGame` — stable identity for a loaded/tracked game
- `EmulatorCheat` — backend-neutral cheat definition
- `EmulatorStateSlot` — save-state slot metadata

---

## Game Boy backend implementation

### `DuckBackend`
- Located in `Backend/GB/DuckBackend.java`
- Singleton `DuckBackend.instance`
- Implements `EmulatorBackend`
- Returns a `DuckProfile` with Game Boy-specific settings:
  - `backendId`: `duck-game-boy`
  - `displayName`: `Game Boy`
  - native resolution: `160x144`
  - host target: `640x576`
  - supported extensions: `.gb`, `.gbc`, `.ips`
  - buttons: UP, DOWN, LEFT, RIGHT, A, B, START, SELECT

### `GBBackends`
- Located in `Backend/GB/GBBackends.java`
- Static access to current backend
- Currently returns `DuckBackend.instance`

---

## Runtime engine

### `DuckEmulation`
- Located in `Backend/GB/DuckEmulation.java`
- Implements `EmulatorRuntime` and `Runnable`
- Core responsibilities:
  - load ROMs / apply IPS patches
  - create hardware components
  - initialize boot state
  - run the main emulation loop
  - manage pause/resume
  - handle save states and save data
  - apply cheats
  - update frontend through host callbacks

### Hardware components
- `DuckCPU`
- `DuckMemory`
- `DuckTimer`
- `DuckPPU`
- `DuckAPU`
- `DuckJoypad`
- `CheatEngine`

### Timing and execution
- Uses a dedicated thread for emulation
- Converts elapsed real time into emulator cycles
- Executes CPU fetch/decode/execute
- Steps hardware every cycle:
  - timer
  - DMA
  - serial
  - PPU
  - audio
- Uses `parkNanos` / `yield` / spin-wait to maintain timing

---

## Frontend integration

The frontend uses the backend via:
- `MainWindow`
  - gets backend from `GBBackends.Current()`
  - creates runtime with `backend.CreateRuntime(this, display)`
- `DuckDisplay`
  - display surface used by runtime
- `InputRouter`
  - forwards input to `runtime.SetButtonPressed(...)`
- various windows
  - `OptionsWindow`
  - `CheatManagerWindow`
  - `SaveStateManagerWindow`
  - use backend profile and runtime methods

---

## Persistence helpers

Under `Backend/Helpers`, supporting code handles:
- save file management
- quick state save/load
- managed game registry
- cheat storage

---

## Summary

The backend architecture is:
1. `Backend.Platform` defines the host API
2. `Backend.GB` implements the Game Boy backend
3. `DuckEmulation` drives the emulator loop and hardware state
4. the frontend interacts with the runtime through the platform API

This keeps the emulator backend isolated from the desktop UI while allowing the UI to work with backend-specific features through a common interface.