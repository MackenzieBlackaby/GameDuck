package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.Emulation.Misc.ROM;
import com.blackaby.Backend.Platform.EmulatorGame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Handles battery-backed save files for cartridge RAM.
 */
public final class SaveFileManager {

    public record SaveIdentity(String sourcePath, String sourceName, String displayName, List<String> patchNames,
                               boolean batteryBackedSave) implements EmulatorGame {
        public SaveIdentity {
            patchNames = List.copyOf(patchNames == null ? List.of() : patchNames);
        }

        public static SaveIdentity FromRom(ROM rom) {
            return FromGame(rom);
        }

        public static SaveIdentity FromGame(EmulatorGame game) {
            if (game == null) {
                return null;
            }
            return new SaveIdentity(
                    game.sourcePath(),
                    game.sourceName(),
                    game.displayName(),
                    game.patchNames(),
                    game.batteryBackedSave());
        }
    }

    public record SaveFileEntry(String label, Path path, long sizeBytes, FileTime lastModified) {
    }

    public record SaveFileSummary(Path preferredPath, Path fallbackPath, List<SaveFileEntry> existingFiles) {
        public boolean HasExistingFiles() {
            return !existingFiles.isEmpty();
        }
    }

    private SaveFileManager() {
    }

    /**
     * Loads save RAM for a ROM when a save file is available.
     *
     * @param rom active ROM
     * @return save bytes when present
     */
    public static Optional<byte[]> LoadSave(ROM rom) {
        return LoadSave(SaveIdentity.FromRom(rom));
    }

    /**
     * Loads save RAM for a tracked game identity when a save file is available.
     *
     * @param saveIdentity tracked save identity
     * @return save bytes when present
     */
    public static Optional<byte[]> LoadSave(SaveIdentity saveIdentity) {
        if (saveIdentity == null || !saveIdentity.batteryBackedSave()) {
            return Optional.empty();
        }

        Path preferredPath = BuildSavePath(saveIdentity);
        Path fallbackPath = BuildFallbackSavePath(saveIdentity);
        Path selectedPath = preferredPath;

        if (!Files.exists(preferredPath) && !preferredPath.equals(fallbackPath) && Files.exists(fallbackPath)) {
            try {
                Files.createDirectories(preferredPath.getParent());
                Files.move(fallbackPath, preferredPath);
            } catch (IOException exception) {
                selectedPath = fallbackPath;
            }
        }

        if (!Files.exists(selectedPath) && !selectedPath.equals(preferredPath) && Files.exists(preferredPath)) {
            selectedPath = preferredPath;
        }

        if (!Files.exists(selectedPath)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Files.readAllBytes(selectedPath));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    /**
     * Writes the supplied save RAM to disk for the active ROM.
     *
     * @param rom active ROM
     * @param saveData raw cartridge save bytes
     */
    public static void Save(ROM rom, byte[] saveData) {
        Save(SaveIdentity.FromRom(rom), saveData);
    }

    /**
     * Writes the supplied save RAM to disk for a tracked game identity.
     *
     * @param saveIdentity tracked save identity
     * @param saveData raw cartridge save bytes
     */
    public static void Save(SaveIdentity saveIdentity, byte[] saveData) {
        if (saveIdentity == null || saveData == null || saveData.length == 0 || !saveIdentity.batteryBackedSave()) {
            return;
        }

        Path preferredPath = PreferredSavePath(saveIdentity);
        try {
            Files.createDirectories(preferredPath.getParent());
            Files.write(preferredPath, saveData);

            Path fallbackPath = LegacySavePath(saveIdentity);
            if (!preferredPath.equals(fallbackPath)) {
                Files.deleteIfExists(fallbackPath);
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Returns the managed save path for a ROM using the preferred display title.
     *
     * @param rom active ROM
     * @return preferred save path
     */
    public static Path PreferredSavePath(ROM rom) {
        return PreferredSavePath(SaveIdentity.FromRom(rom));
    }

    /**
     * Returns the managed save path for a tracked game identity.
     *
     * @param saveIdentity tracked save identity
     * @return preferred save path
     */
    public static Path PreferredSavePath(SaveIdentity saveIdentity) {
        if (saveIdentity == null) {
            return SaveDirectory().resolve("unknown.sav");
        }
        return BuildSavePath(saveIdentity);
    }

    /**
     * Returns the legacy save path based on the original ROM filename.
     *
     * @param rom active ROM
     * @return legacy save path
     */
    public static Path LegacySavePath(ROM rom) {
        return LegacySavePath(SaveIdentity.FromRom(rom));
    }

    /**
     * Returns the legacy save path based on the original game filename.
     *
     * @param saveIdentity tracked save identity
     * @return legacy save path
     */
    public static Path LegacySavePath(SaveIdentity saveIdentity) {
        if (saveIdentity == null) {
            return SaveDirectory().resolve("unknown.sav");
        }
        return BuildFallbackSavePath(saveIdentity);
    }

    /**
     * Returns the configured save directory.
     *
     * @return save directory path
     */
    public static Path SaveDirectoryPath() {
        return SaveDirectory();
    }

    /**
     * Describes the preferred and existing save files for a ROM.
     *
     * @param rom active ROM
     * @return save file summary
     */
    public static SaveFileSummary DescribeSaveFiles(ROM rom) {
        return DescribeSaveFiles(SaveIdentity.FromRom(rom));
    }

    /**
     * Describes the preferred and existing save files for a tracked game identity.
     *
     * @param saveIdentity tracked save identity
     * @return save file summary
     */
    public static SaveFileSummary DescribeSaveFiles(SaveIdentity saveIdentity) {
        if (saveIdentity == null) {
            Path unknownPath = SaveDirectory().resolve("unknown.sav");
            return new SaveFileSummary(unknownPath, unknownPath, List.of());
        }
        Path preferredPath = PreferredSavePath(saveIdentity);
        Path fallbackPath = LegacySavePath(saveIdentity);
        List<SaveFileEntry> files = new ArrayList<>();

        AddSaveEntry(files, preferredPath, "Managed Save");
        if (!preferredPath.equals(fallbackPath)) {
            AddSaveEntry(files, fallbackPath, "Legacy Save");
        }

        files.sort(Comparator.comparing(SaveFileEntry::lastModified).reversed());
        return new SaveFileSummary(preferredPath, fallbackPath, List.copyOf(files));
    }

    /**
     * Deletes the managed save file and any legacy alias for a ROM.
     *
     * @param rom active ROM
     * @throws IOException when deletion fails
     */
    public static void DeleteSave(ROM rom) throws IOException {
        DeleteSave(SaveIdentity.FromRom(rom));
    }

    /**
     * Deletes the managed save file and any legacy alias for a tracked game.
     *
     * @param saveIdentity tracked save identity
     * @throws IOException when deletion fails
     */
    public static void DeleteSave(SaveIdentity saveIdentity) throws IOException {
        if (saveIdentity == null || !saveIdentity.batteryBackedSave()) {
            return;
        }

        Files.deleteIfExists(PreferredSavePath(saveIdentity));
        Path fallbackPath = LegacySavePath(saveIdentity);
        if (!PreferredSavePath(saveIdentity).equals(fallbackPath)) {
            Files.deleteIfExists(fallbackPath);
        }
    }

    /**
     * Imports an external save file into the managed location for a ROM.
     *
     * @param rom active ROM
     * @param sourcePath external save file
     * @return imported save bytes
     * @throws IOException when the file cannot be read or written
     */
    public static byte[] ImportSave(ROM rom, Path sourcePath) throws IOException {
        return ImportSave(SaveIdentity.FromRom(rom), sourcePath);
    }

    /**
     * Imports an external save file into the managed location for a tracked game.
     *
     * @param saveIdentity tracked save identity
     * @param sourcePath external save file
     * @return imported save bytes
     * @throws IOException when the file cannot be read or written
     */
    public static byte[] ImportSave(SaveIdentity saveIdentity, Path sourcePath) throws IOException {
        if (saveIdentity == null || !saveIdentity.batteryBackedSave()) {
            throw new IllegalArgumentException("This game does not support battery-backed saves.");
        }
        if (sourcePath == null || !Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new IOException("Select a valid save data file to import.");
        }

        byte[] saveData = Files.readAllBytes(sourcePath);
        if (saveData.length == 0) {
            throw new IOException("The selected save data file is empty.");
        }

        Path preferredPath = PreferredSavePath(saveIdentity);
        Files.createDirectories(preferredPath.getParent());
        Files.write(preferredPath, saveData);

        Path fallbackPath = LegacySavePath(saveIdentity);
        if (!preferredPath.equals(fallbackPath)) {
            Files.deleteIfExists(fallbackPath);
        }
        return saveData;
    }

    /**
     * Writes a save snapshot to an arbitrary external path.
     *
     * @param saveData raw save bytes
     * @param destinationPath destination file path
     * @throws IOException when the file cannot be written
     */
    public static void ExportSave(byte[] saveData, Path destinationPath) throws IOException {
        if (saveData == null || saveData.length == 0) {
            throw new IOException("No save data is available to export.");
        }
        if (destinationPath == null) {
            throw new IOException("Choose a destination file for the exported save.");
        }

        Path parent = destinationPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(destinationPath, saveData);
    }

    /**
     * Copies an existing managed save file to an arbitrary external path.
     *
     * @param rom active ROM
     * @param destinationPath destination file path
     * @throws IOException when the file cannot be read or written
     */
    public static void ExportSave(ROM rom, Path destinationPath) throws IOException {
        ExportSave(SaveIdentity.FromRom(rom), destinationPath);
    }

    /**
     * Copies an existing managed save file to an arbitrary external path.
     *
     * @param saveIdentity tracked save identity
     * @param destinationPath destination file path
     * @throws IOException when the file cannot be read or written
     */
    public static void ExportSave(SaveIdentity saveIdentity, Path destinationPath) throws IOException {
        Path sourcePath = ResolveExistingSavePath(saveIdentity)
                .orElseThrow(() -> new IOException("No managed save file exists for this game."));
        if (destinationPath == null) {
            throw new IOException("Choose a destination file for the exported save.");
        }

        Path parent = destinationPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Returns the existing managed or legacy save path when one is present.
     *
     * @param rom active ROM
     * @return existing save path
     */
    public static Optional<Path> ResolveExistingSavePath(ROM rom) {
        return ResolveExistingSavePath(SaveIdentity.FromRom(rom));
    }

    /**
     * Returns the existing managed or legacy save path when one is present.
     *
     * @param saveIdentity tracked save identity
     * @return existing save path
     */
    public static Optional<Path> ResolveExistingSavePath(SaveIdentity saveIdentity) {
        if (saveIdentity == null || !saveIdentity.batteryBackedSave()) {
            return Optional.empty();
        }

        Path preferredPath = PreferredSavePath(saveIdentity);
        if (Files.exists(preferredPath)) {
            return Optional.of(preferredPath);
        }

        Path fallbackPath = LegacySavePath(saveIdentity);
        if (!preferredPath.equals(fallbackPath) && Files.exists(fallbackPath)) {
            return Optional.of(fallbackPath);
        }

        return Optional.empty();
    }

    static Path BuildSavePath(ROM rom) {
        return BuildSavePath(SaveIdentity.FromRom(rom));
    }

    static Path BuildSavePath(SaveIdentity saveIdentity) {
        if (saveIdentity == null) {
            return SaveDirectory().resolve("unknown.sav");
        }
        return SaveDirectory().resolve(BuildSaveFileName(ResolvePreferredBaseName(saveIdentity), saveIdentity.patchNames()));
    }

    static Path BuildFallbackSavePath(ROM rom) {
        return BuildFallbackSavePath(SaveIdentity.FromRom(rom));
    }

    static Path BuildFallbackSavePath(SaveIdentity saveIdentity) {
        if (saveIdentity == null) {
            return SaveDirectory().resolve("unknown.sav");
        }
        return SaveDirectory().resolve(BuildSaveFileName(BuildFallbackBaseName(saveIdentity), saveIdentity.patchNames()));
    }

    static String BuildFallbackBaseName(ROM rom) {
        return BuildFallbackBaseName(SaveIdentity.FromRom(rom));
    }

    static String BuildFallbackBaseName(SaveIdentity saveIdentity) {
        if (saveIdentity == null) {
            return "unknown";
        }

        String sourceName = saveIdentity.sourceName();
        if (sourceName != null && !sourceName.isBlank()) {
            return sourceName;
        }
        return saveIdentity.displayName() == null || saveIdentity.displayName().isBlank() ? "unknown" : saveIdentity.displayName();
    }

    private static String ResolvePreferredBaseName(SaveIdentity saveIdentity) {
        return GameMetadataStore.GetLibretroTitle(saveIdentity).orElse(BuildFallbackBaseName(saveIdentity));
    }

    private static String BuildSaveFileName(String baseName, java.util.List<String> patchNames) {
        StringBuilder builder = new StringBuilder(SanitiseFileComponent(baseName));
        for (String patchName : patchNames) {
            String sanitisedPatch = SanitiseFileComponent(patchName);
            if (!sanitisedPatch.isBlank()) {
                builder.append(" [").append(sanitisedPatch).append("]");
            }
        }
        builder.append(".sav");
        return builder.toString();
    }

    private static String SanitiseFileComponent(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        String cleaned = value.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ")
                .replaceAll("\\.+$", "")
                .trim();
        return cleaned.isBlank() ? "unknown" : cleaned;
    }

    private static Path SaveDirectory() {
        String configuredPath = System.getProperty("gameduck.save_dir");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of("saves");
    }

    private static void AddSaveEntry(List<SaveFileEntry> files, Path path, String label) {
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
            return;
        }

        try {
            files.add(new SaveFileEntry(
                    label,
                    path,
                    Files.size(path),
                    Files.getLastModifiedTime(path)));
        } catch (IOException exception) {
            files.add(new SaveFileEntry(label, path, -1L, FileTime.fromMillis(0L)));
        }
    }
}
