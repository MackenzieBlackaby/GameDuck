package com.blackaby.Misc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Manages the user-installed DMG and CGB boot ROM files used by the emulator.
 */
public final class BootRomManager {

    public static final int dmgBootRomSizeBytes = 0x100;
    public static final int cgbBootRomSizeBytes = 0x800;

    private static final Path bootRomDirectoryPath = Path.of(System.getProperty("user.home"), ".gameduck");
    private static final Path dmgBootRomPath = bootRomDirectoryPath.resolve("dmg_boot.bin");
    private static final Path cgbBootRomPath = bootRomDirectoryPath.resolve("cgb_boot.bin");

    private BootRomManager() {
    }

    /**
     * Returns the managed DMG boot ROM path.
     *
     * @return absolute managed path
     */
    public static Path DmgBootRomPath() {
        return dmgBootRomPath;
    }

    /**
     * Returns the managed CGB boot ROM path.
     *
     * @return absolute managed path
     */
    public static Path CgbBootRomPath() {
        return cgbBootRomPath;
    }

    /**
     * Returns whether a managed DMG boot ROM is installed.
     *
     * @return {@code true} when the file exists
     */
    public static boolean HasDmgBootRom() {
        return Files.exists(dmgBootRomPath);
    }

    /**
     * Returns whether a managed CGB boot ROM is installed.
     *
     * @return {@code true} when the file exists
     */
    public static boolean HasCgbBootRom() {
        return Files.exists(cgbBootRomPath);
    }

    /**
     * Loads and validates the managed DMG boot ROM.
     *
     * @return raw boot ROM bytes
     * @throws IOException when the file cannot be read
     */
    public static byte[] LoadDmgBootRom() throws IOException {
        byte[] bytes = Files.readAllBytes(dmgBootRomPath);
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
        byte[] bytes = Files.readAllBytes(cgbBootRomPath);
        ValidateCgbBootRom(bytes);
        return bytes;
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
        Files.copy(sourcePath, dmgBootRomPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Copies an external CGB boot ROM into the managed emulator data folder.
     *
     * @param sourcePath source boot ROM file
     * @throws IOException when the source cannot be read or copied
     */
    public static void InstallCgbBootRom(Path sourcePath) throws IOException {
        byte[] bytes = Files.readAllBytes(sourcePath);
        ValidateCgbBootRom(bytes);
        EnsureBootRomDirectory();
        Files.copy(sourcePath, cgbBootRomPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Removes the managed DMG boot ROM if one is installed.
     *
     * @throws IOException when the file cannot be removed
     */
    public static void RemoveDmgBootRom() throws IOException {
        Files.deleteIfExists(dmgBootRomPath);
    }

    /**
     * Removes the managed CGB boot ROM if one is installed.
     *
     * @throws IOException when the file cannot be removed
     */
    public static void RemoveCgbBootRom() throws IOException {
        Files.deleteIfExists(cgbBootRomPath);
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
        Files.createDirectories(bootRomDirectoryPath);
    }

    private static void ValidateDmgBootRom(byte[] bytes) {
        if (bytes == null || bytes.length != dmgBootRomSizeBytes) {
            throw new IllegalArgumentException(
                    "The DMG boot ROM must be exactly " + dmgBootRomSizeBytes + " bytes.");
        }
    }

    private static void ValidateCgbBootRom(byte[] bytes) {
        if (bytes == null || bytes.length != cgbBootRomSizeBytes) {
            throw new IllegalArgumentException(
                    "The CGB boot ROM must be exactly " + cgbBootRomSizeBytes + " bytes.");
        }
    }
}
