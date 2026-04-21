# GameDuck

## Overview

GameDuck is a Game Boy / Colour emulator suite, complete with a fully featured emulator and desktop frontend, written in Java with Swing.

It started as my final year project at Lancaster University and evolved past that into a fully fledged Game Boy emulation suite. The repo contains the emulator core itself, the desktop UI wrapped around it, save-data and save-state management, ROM library features, ROM-hack loading tools, boot ROM support, palette and theme customisation, display shaders, serial output logging, per-game notes, and a duck name and logo in honour of the Lancaster University ducks :)

The project is currently aimed at original Game Boy and Game Boy Color software. The desktop UI is deliberately designed to be easy to use and feature-rich in a convenient way. I know swing UI looks somewhat archaic compared to RetroArch, but the defining feature of this is that it runs on Java, and therefore can run natively on any device that can run a JVM.

## Changelog

### V 0.4.0

- Major display pipeline work to reduce flicker, latency, and unnecessary repainting.
- Optimised display caching to squeeze out more performance from the Swing renderer.
- Removed the heaviest experimental shader passes until they are ready for release.
- Added controller polling modes so input can be tuned for latency or CPU use.
- Improved controller discovery, naming, and routing to avoid slow rescans during normal UI use.
- Fixed MBC3 RTC reload behaviour and made old RTC saves advance from the current time properly.
- Added per-game notes in the main window.
- Added recent-game artwork buttons to the Ready to Play page, with a configurable limit in Window options.
- Continued options window and main UI cleanup.
- Added more focused regression tests across controller input, display rendering, library UI, options layout, memory, RTC, and notes.

### V 0.3.2

- First packaged Windows x64 app-image release.
- Added release tooling for a bundled runtime, executable launcher, zip output, and SHA256 checksum generation.
- Updated the packaged release folder to include the project README, licence, demo ROMs, and portable working folders.

### V 0.3.0

- Support for game cheats! Cheats can be automatically downloaded for each ROM, toggled, and edited.
- Complete redesign of the user interface for a more modern visual appeal.
- Implementation of a proper dark mode.
- Smooth visuals via frame blending and a toggleable FPS counter in options.
- New dedicated button and view for opening ROM hacks with intro screens.
- Map application shortcuts directly to controller buttons.

### V 0.2.0

- Full implementation of CGB double-speed logic.
- Proper hardware-accurate DMA behavior.
- Edit and save custom GBC-style palettes.
- Palettes now live in JSON files for easy sharing and backup.
- Added support for external palette files and a bundled sample library.
- "Get Artwork" retry button and direct ROM deletion from the info window.
- Added full test suite for major emulator subsystems.

### V 0.1.1

- Basic controller support has been added.
- It supports a variety of controllers using standard input systems such as XInput and DirectInput on Windows, and the standard input APIs on Linux and Mac.
- The controller configuration UI allows you to map buttons to the Game Boy buttons.
- The emulator can detect connected controllers and switch between them seamlessly.
- Shortcut mapping and "map all" functionality is coming soon.

### V 0.1.0

- A OOP-based emulator core under `Backend/Emulation`, split into CPU, memory, graphics, peripherals, and various other classes. Basically the heart of the entire emulation.
- A Swing desktop application under `Frontend`, including the main window, library browser, ROM information view, options window, theme manager, palette manager, save manager, and save-state manager.
- Extra code under `Backend/Helpers` for battery saves, managed save states, game library storage, libretro metadata, and artwork lookups.
- Shared settings and user-facing text under `Misc`.

## What is planned by the time of V 1.0

- SGB Support
- Library and Config migration (To make updating easy)
- 2 Emulations running simultaneously with link cable support between them.
- Dual controller support for split-screen style play

## Project layout

`src/main/java/com/blackaby` is split into four parts.

- `Backend/Emulation` is the emulator core. This is where the CPU, decoder, instruction logic, memory map, PPU, APU, timer, joypad handling, ROM loading, and mapper implementations live.
- `Backend/Helpers` is the persistence and tooling layer around the emulator. This covers save files, quick states, the managed ROM library, metadata caching, artwork fetching, and other file-based helpers.
- `Frontend` is the desktop application. It owns the windows, menus, display panel, input routing, styling, and UI flow for loading ROMs and managing data.
- `Misc` contains shared configuration, settings, enums, theme data, input bindings, boot ROM handling, and the centralised UI text.

At the top level there are also a few working directories used by the app itself.

- `library/` stores managed copies of ROMs that have been loaded into the in-app library.
- `saves/` stores cartridge save data.
- `saves/notes/` stores plain-text per-game notes.
- `quickstates/` stores `.gqs` save-state files.
- `cache/` stores downloaded game artwork and cached metadata.
- `docs/` holds the generated Javadoc.

## Build and run

GameDuck currently targets Java 22 and builds with Maven.

### Building with powershell

Building with powershell is straightforward thanks to the `tools.ps1` script. To load the commands in the script, run in the root dir of the project:

```powershell
. ./tools.ps1
```

From there, you can use the commands:

- `clean` to perform a complete clean, compile and run cycle.
- `run` to run the desktop app without cleaning or recompiling first.
- `release` to build a windows exe package of the app with a built in JRE.
- `doc` to generate the javadoc for the project.

### Building with bash

To build the project, run:

```bash
mvn compile
```

To run the desktop app, compile in the same invocation so Maven does not launch against stale `target/classes` output:

```bash
mvn compile exec:java
```

If you still hit a `ClassNotFoundException` or `NoClassDefFoundError` after source changes, clear stale output first:

```bash
mvn clean compile exec:java
```

To run the tests:

```bash
mvn test
```

To regenerate the Javadoc in `docs/javadoc/` run the command:

```bash
javadoc -d docs/javadoc -sourcepath src/main/java -subpackages com.blackaby
```

## Files written outside the repo

Not everything lives in the working tree.

- The main settings file is written to `~/.gameduck/config.properties`.
- Palettes are written to `~/.gameduck/palettes.json`.
- Installed boot ROMs are kept in `~/.gameduck/`.

That split is deliberate. Runtime data tied to the current checkout stays in the project folders, while configuration and boot ROMs live in the user profile.

## Notes on ROMs and boot ROMs

This repo does not include commercial ROMs or Nintendo boot ROMs.

If you want the emulator to boot through the original startup sequence, you need to source and install your own non-copyright boot ROM files through the application. The code validates the expected GB and GBC boot ROM sizes before using them.

## License

GameDuck source code is licensed under MPL-2.0.
