package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.Emulation.Misc.ROM;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the broad console family used for UI filtering.
 */
public final class RomConsoleSupport {

    private RomConsoleSupport() {
    }

    /**
     * Returns whether the supplied ROM should be grouped under the GBC filter.
     * Dual-mode cartridges are treated as GBC-capable for filtering.
     *
     * @param rom ROM to inspect
     * @return {@code true} when the ROM advertises CGB support
     */
    public static boolean IsGbc(ROM rom) {
        return rom != null && rom.IsCgbCompatible();
    }

    /**
     * Returns whether the supplied ROM requires Game Boy Color hardware.
     *
     * @param rom ROM to inspect
     * @return {@code true} when the ROM is CGB-only
     */
    public static boolean IsCgbOnly(ROM rom) {
        return rom != null && rom.IsCgbOnly();
    }

    /**
     * Resolves a stored ROM file to a console family.
     *
     * @param romPath managed or source ROM path
     * @return {@code true} when the ROM advertises CGB support
     */
    public static boolean IsGbc(Path romPath) {
        if (romPath == null || !Files.isRegularFile(romPath)) {
            return false;
        }
        return IsGbc(new ROM(romPath.toString()));
    }

    /**
     * Resolves whether a stored ROM file requires Game Boy Color hardware.
     *
     * @param romPath managed or source ROM path
     * @return {@code true} when the ROM is CGB-only
     */
    public static boolean IsCgbOnly(Path romPath) {
        if (romPath == null || !Files.isRegularFile(romPath)) {
            return false;
        }
        return IsCgbOnly(new ROM(romPath.toString()));
    }

    /**
     * Falls back to file-extension heuristics when ROM bytes are unavailable.
     *
     * @param sourcePath source ROM path
     * @return {@code true} for explicit {@code .gbc}/{@code .cgb} files
     */
    public static boolean IsProbablyGbc(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return false;
        }

        String lowerPath = sourcePath.toLowerCase();
        return lowerPath.endsWith(".gbc") || lowerPath.endsWith(".cgb");
    }
}
