# GameDuck

GameDuck is my Game Boy / Colour emulator and desktop frontend, written in Java with Swing.

It started as my final year project at Lancaster University and evolved past that into a fully fledged Game Boy emulation suite. The repo contains the emulator core itself, the desktop UI wrapped around it, save-data and save-state management, ROM library features, ROM-hack loading tools, boot ROM support, palette and theme customisation, serial output logging, and a duck name and logo in honour of the Lancaster University ducks :)

The project is aimed at original Game Boy and Game Boy Color software. The desktop UI is deliberately designed to be easy to use and feature-rich in a convenient way. I know swing UI looks somewhat archaic compared to RetroArch, but the defining feature of this is that it runs on Java, and therefore can run natively on any device that can run a JVM.

## What is in here at V 0.1

- A OOP-based emulator core under `Backend/Emulation`, split into CPU, memory, graphics, peripherals, and various other classes. Basically the heart of the entire emulation.
- A Swing desktop application under `Frontend`, including the main window, library browser, ROM information view, options window, theme manager, palette manager, save manager, and save-state manager.
- Extra code under `Backend/Helpers` for battery saves, managed save states, game library storage, libretro metadata, and artwork lookups.
- Shared settings and user-facing text under `Misc`.

## What is planned by the time of V 1.0

- SGB Support
- Controller support
- Library and Config migration (To make updating easy)
- Prettier UI (Hopefully)
- 2 Emulations running simultaneously with link cable support between them.
- Dual controller support for split-screen style play
- Shaders and overlays
- Fixes for certain effects that relied on hardware screen ghosting

## Main features

- Runs `.gb`, `.gbc`, and compatible patched ROMs.
- Loads IPS patches on top of a base ROM WITHOUT needing to modify the original ROM to run the ROMhack (Both the ROM and IPS file are stored separately in the library, with separate save data. The ROMhack is patched onto the ROM at launch)
- Supports save data management for compatible carts.
- Supports managed quick saves and numbered save-state slots.
- Copies and tracks played ROMs to a local library with a "favourites" playlist
- Pulls box art, title screens, and screenshots from libretro and caches them locally.
- Lets you switch palettes, edit colours, and save custom GB palettes.
- Allows for GB -> GBC colourisation and GBC -> GB emulation (For ROMs that worked on both models).
- Full feature switching between 
- Theme customisation.
- Supports configurable controls and application shortcuts.
- Can use installed GB and GBC boot ROMs (Not provided here for copyright reasons).
- Shows live serial output and writes debug logs to disk.

## Project layout

`src/main/java/com/blackaby` is split into four parts.

- `Backend/Emulation` is the emulator core. This is where the CPU, decoder, instruction logic, memory map, PPU, APU, timer, joypad handling, ROM loading, and mapper implementations live.
- `Backend/Helpers` is the persistence and tooling layer around the emulator. This covers save files, quick states, the managed ROM library, metadata caching, artwork fetching, and other file-based helpers.
- `Frontend` is the desktop application. It owns the windows, menus, display panel, input routing, styling, and UI flow for loading ROMs and managing data.
- `Misc` contains shared configuration, settings, enums, theme data, input bindings, boot ROM handling, and the centralised UI text.

At the top level there are also a few working directories used by the app itself.

- `library/` stores managed copies of ROMs that have been loaded into the in-app library.
- `saves/` stores cartridge save data.
- `quickstates/` stores `.gqs` save-state files.
- `cache/` stores downloaded game artwork and cached metadata.
- `docs/` holds the generated Javadoc.

## Build and run

GameDuck currently targets Java 22 and builds with Maven.

```bash
mvn compile
```

To run the desktop app:

```bash
mvn exec:java
```

To run the tests:

```bash
mvn test
```

To regenerate the Javadoc in `docs/`:

```bash
javadoc -d docs -sourcepath src/main/java -subpackages com.blackaby
```

## Files written outside the repo

Not everything lives in the working tree.

- The main settings file is written to `~/.gameduck.properties`.
- Installed boot ROMs are kept in `~/.gameduck/`.

That split is deliberate. Runtime data tied to the current checkout stays in the project folders, while configuration and boot ROMs live in the user profile.

## Notes on ROMs and boot ROMs

This repo does not include commercial ROMs or Nintendo boot ROMs.

If you want the emulator to boot through the original startup sequence, you need to source and install your own non-copyright boot ROM files through the application. The code validates the expected GB and GBC boot ROM sizes before using them.

## License

GameDuck source code is licensed under MPL-2.0.

Third-party components and generated runtime/legal materials in this repository may remain under their own separate licenses.

## Why the code looks like this

This codebase is split between emulator logic and desktop application code because that is how I actually use it. I wanted a project that is usable and a bit different to other emulators out there. That is why there is as much attention on the Front End as there is on the CPU and memory implementation.
