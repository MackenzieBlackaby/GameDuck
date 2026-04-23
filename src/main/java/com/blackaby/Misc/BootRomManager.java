package com.blackaby.Misc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages the user-installed DMG and CGB boot ROM files used by the emulator.
 */
public final class BootRomManager {

    public static final int dmgBootRomSizeBytes = 0x100;
    public static final int cgbBootRomSizeBytes = 0x800;
    public static final int cgbBootRomFullDumpSizeBytes = 0x900;

    private BootRomManager() {
    }

    /**
     * Returns the managed DMG boot ROM path.
     *
     * @return absolute managed path
     */
    public static Path DmgBootRomPath() {
        String configuredPath = System.getProperty("gameduck.dmg_boot_rom_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return BootRomDirectoryPath().resolve("dmg_boot.bin");
    }

    /**
     * Returns the managed CGB boot ROM path.
     *
     * @return absolute managed path
     */
    public static Path CgbBootRomPath() {
        String configuredPath = System.getProperty("gameduck.cgb_boot_rom_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return BootRomDirectoryPath().resolve("cgb_boot.bin");
    }

    /**
     * Returns whether a managed DMG boot ROM is installed.
     *
     * @return {@code true} when the file exists
     */
    public static boolean HasDmgBootRom() {
        return Files.exists(DmgBootRomPath());
    }

    /**
     * Returns whether a managed CGB boot ROM is installed.
     *
     * @return {@code true} when the file exists
     */
    public static boolean HasCgbBootRom() {
        return Files.exists(CgbBootRomPath());
    }

    /**
     * Loads and validates the managed DMG boot ROM.
     *
     * @return raw boot ROM bytes
     * @throws IOException when the file cannot be read
     */
    public static byte[] LoadDmgBootRom() throws IOException {
        byte[] bytes = Files.readAllBytes(DmgBootRomPath());
        ValidateDmgBootRom(bytes);
        return bytes;
    }

    /**
     * Loads and validates the managed CGB boot ROM.
     *
     * @return raw boot ROM bytes
     * @throws IOException when the file cannot be read
     */
    public static byte[] LoadCgbBootRom() throws IOException {
        byte[] bytes = Files.readAllBytes(CgbBootRomPath());
        return NormaliseCgbBootRom(bytes);
    }

    /**
     * Copies an external DMG boot ROM into the managed emulator data folder.
     *
     * @param sourcePath source boot ROM file
     * @throws IOException when the source cannot be read or copied
     */
    public static void InstallDmgBootRom(Path sourcePath) throws IOException {
        byte[] bytes = Files.readAllBytes(sourcePath);
        ValidateDmgBootRom(bytes);
        EnsureBootRomDirectory();
        Files.write(DmgBootRomPath(), bytes);
    }

    /**
     * Copies an external CGB boot ROM into the managed emulator data folder.
     *
     * @param sourcePath source boot ROM file
     * @throws IOException when the source cannot be read or copied
     */
    public static void InstallCgbBootRom(Path sourcePath) throws IOException {
        byte[] bytes = Files.readAllBytes(sourcePath);
        byte[] normalisedBytes = NormaliseCgbBootRom(bytes);
        EnsureBootRomDirectory();
        Files.write(CgbBootRomPath(), normalisedBytes);
    }

    /**
     * Removes the managed DMG boot ROM if one is installed.
     *
     * @throws IOException when the file cannot be removed
     */
    public static void RemoveDmgBootRom() throws IOException {
        Files.deleteIfExists(DmgBootRomPath());
    }

    /**
     * Removes the managed CGB boot ROM if one is installed.
     *
     * @throws IOException when the file cannot be removed
     */
    public static void RemoveCgbBootRom() throws IOException {
        Files.deleteIfExists(CgbBootRomPath());
    }

    /**
     * Returns the legacy managed DMG boot ROM path.
     *
     * @return DMG boot ROM path
     */
    public static Path BootRomPath() {
        return DmgBootRomPath();
    }

    /**
     * Returns whether a managed DMG boot ROM is installed.
     *
     * @return {@code true} when the file exists
     */
    public static boolean HasBootRom() {
        return HasDmgBootRom();
    }

    /**
     * Loads and validates the managed DMG boot ROM.
     *
     * @return raw boot ROM bytes
     * @throws IOException when the file cannot be read
     */
    public static byte[] LoadBootRom() throws IOException {
        return LoadDmgBootRom();
    }

    /**
     * Copies an external DMG boot ROM into the managed emulator data folder.
     *
     * @param sourcePath source boot ROM file
     * @throws IOException when the source cannot be read or copied
     */
    public static void InstallBootRom(Path sourcePath) throws IOException {
        InstallDmgBootRom(sourcePath);
    }

    /**
     * Removes the managed DMG boot ROM if one is installed.
     *
     * @throws IOException when the file cannot be removed
     */
    public static void RemoveBootRom() throws IOException {
        RemoveDmgBootRom();
    }

    private static void EnsureBootRomDirectory() throws IOException {
        Files.createDirectories(BootRomDirectoryPath());
    }

    private static Path BootRomDirectoryPath() {
        String configuredPath = System.getProperty("gameduck.boot_rom_dir");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of(System.getProperty("user.home"), ".gameduck");
    }

    private static void ValidateDmgBootRom(byte[] bytes) {
        if (bytes == null || bytes.length != dmgBootRomSizeBytes) {
            throw new IllegalArgumentException(
                    "The DMG boot ROM must be exactly " + dmgBootRomSizeBytes + " bytes.");
        }
    }

    private static void ValidateCgbBootRom(byte[] bytes) {
        if (bytes == null
                || (bytes.length != cgbBootRomSizeBytes && bytes.length != cgbBootRomFullDumpSizeBytes)) {
            throw new IllegalArgumentException(
                    "The CGB boot ROM must be either "
                            + cgbBootRomSizeBytes
                            + " bytes (mapped image) or "
                            + cgbBootRomFullDumpSizeBytes
                            + " bytes (full dump).");
        }
    }

    private static byte[] NormaliseCgbBootRom(byte[] bytes) {
        ValidateCgbBootRom(bytes);
        if (bytes.length == cgbBootRomSizeBytes) {
            return bytes;
        }

        byte[] normalisedBytes = new byte[cgbBootRomSizeBytes];
        System.arraycopy(bytes, 0, normalisedBytes, 0, 0x100);
        System.arraycopy(bytes, 0x200, normalisedBytes, 0x100, 0x700);
        return normalisedBytes;
    }
}
