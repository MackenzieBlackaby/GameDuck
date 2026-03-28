package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.Misc.ROM;
import com.blackaby.Backend.Platform.EmulatorGame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public record SaveDataBundle(byte[] primaryData, byte[] supplementalData) {
        public SaveDataBundle {
            primaryData = primaryData == null ? new byte[0] : primaryData.clone();
            supplementalData = supplementalData == null ? new byte[0] : supplementalData.clone();
        }

        public boolean HasAnyData() {
            return primaryData.length > 0 || supplementalData.length > 0;
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
        return LoadSaveBundle(rom).map(SaveDataBundle::primaryData);
    }

    /**
     * Loads save RAM for a tracked game identity when a save file is available.
     *
     * @param saveIdentity tracked save identity
     * @return save bytes when present
     */
    public static Optional<byte[]> LoadSave(SaveIdentity saveIdentity) {
        return LoadSaveBundle(saveIdentity).map(SaveDataBundle::primaryData);
    }

    /**
     * Loads managed save RAM and supplementary persistence data for a ROM.
     *
     * @param rom active ROM
     * @return save bytes when present
     */
    public static Optional<SaveDataBundle> LoadSaveBundle(ROM rom) {
        return LoadSaveBundle(SaveIdentity.FromRom(rom));
    }

    /**
     * Loads managed save RAM and supplementary persistence data for a tracked game
     * identity when save files are available.
     *
     * @param saveIdentity tracked save identity
     * @return save bytes when present
     */
    public static Optional<SaveDataBundle> LoadSaveBundle(SaveIdentity saveIdentity) {
        if (saveIdentity == null || !saveIdentity.batteryBackedSave()) {
            return Optional.empty();
        }

        Path preferredPath = BuildSavePath(saveIdentity);
        Path fallbackPath = BuildFallbackSavePath(saveIdentity);
        Path preferredRtcPath = BuildRtcPath(preferredPath);
        Path fallbackRtcPath = BuildRtcPath(fallbackPath);

        MoveFallbackFilesToPreferred(preferredPath, fallbackPath);
        MoveFallbackFilesToPreferred(preferredRtcPath, fallbackRtcPath);

        Path selectedPrimaryPath = SelectExistingPath(preferredPath, fallbackPath);
        Path selectedRtcPath = SelectExistingPath(preferredRtcPath, fallbackRtcPath);
        if (selectedPrimaryPath == null && selectedRtcPath == null) {
            return Optional.empty();
        }

        try {
            byte[] primaryData = selectedPrimaryPath == null ? new byte[0] : Files.readAllBytes(selectedPrimaryPath);
            byte[] supplementalData = selectedRtcPath == null ? new byte[0] : Files.readAllBytes(selectedRtcPath);
            return Optional.of(new SaveDataBundle(primaryData, supplementalData));
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
        Save(SaveIdentity.FromRom(rom), saveData, new byte[0]);
    }

    /**
     * Writes the supplied save RAM to disk for a tracked game identity.
     *
     * @param saveIdentity tracked save identity
     * @param saveData raw cartridge save bytes
     */
    public static void Save(SaveIdentity saveIdentity, byte[] saveData) {
        Save(saveIdentity, saveData, new byte[0]);
    }

    /**
     * Writes the supplied save RAM and supplementary persistence data to disk for
     * the active ROM.
     *
     * @param rom active ROM
     * @param saveData raw cartridge save bytes
     * @param supplementalData supplementary save bytes such as RTC state
     */
    public static void Save(ROM rom, byte[] saveData, byte[] supplementalData) {
        Save(SaveIdentity.FromRom(rom), saveData, supplementalData);
    }

    /**
     * Writes the supplied save RAM and supplementary persistence data to disk for
     * a tracked game identity.
     *
     * @param saveIdentity tracked save identity
     * @param saveData raw cartridge save bytes
     * @param supplementalData supplementary save bytes such as RTC state
     */
    public static void Save(SaveIdentity saveIdentity, byte[] saveData, byte[] supplementalData) {
        if (saveIdentity == null || !saveIdentity.batteryBackedSave()) {
            return;
        }

        byte[] primaryData = saveData == null ? new byte[0] : saveData;
        byte[] rtcData = supplementalData == null ? new byte[0] : supplementalData;
        if (primaryData.length == 0 && rtcData.length == 0) {
            return;
        }

        Path preferredPath = PreferredSavePath(saveIdentity);
        Path preferredRtcPath = BuildRtcPath(preferredPath);
        try {
            Files.createDirectories(preferredPath.getParent());
            Files.write(preferredPath, primaryData);
            if (rtcData.length > 0) {
                Files.write(preferredRtcPath, rtcData);
            } else {
                Files.deleteIfExists(preferredRtcPath);
            }

            Path fallbackPath = LegacySavePath(saveIdentity);
            if (!preferredPath.equals(fallbackPath)) {
                Files.deleteIfExists(fallbackPath);
                Files.deleteIfExists(BuildRtcPath(fallbackPath));
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
        AddSaveEntry(files, BuildRtcPath(preferredPath), "Managed RTC");
        if (!preferredPath.equals(fallbackPath)) {
            AddSaveEntry(files, fallbackPath, "Legacy Save");
            AddSaveEntry(files, BuildRtcPath(fallbackPath), "Legacy RTC");
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

        Path preferredPath = PreferredSavePath(saveIdentity);
        Files.deleteIfExists(preferredPath);
        Files.deleteIfExists(BuildRtcPath(preferredPath));
        Path fallbackPath = LegacySavePath(saveIdentity);
        if (!preferredPath.equals(fallbackPath)) {
            Files.deleteIfExists(fallbackPath);
            Files.deleteIfExists(BuildRtcPath(fallbackPath));
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
        return ImportSaveBundle(rom, sourcePath).primaryData();
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
        return ImportSaveBundle(saveIdentity, sourcePath).primaryData();
    }

    /**
     * Imports an external save file and optional RTC sidecar into the managed
     * location for a ROM.
     *
     * @param rom active ROM
     * @param sourcePath external save file
     * @return imported save bundle
     * @throws IOException when the file cannot be read or written
     */
    public static SaveDataBundle ImportSaveBundle(ROM rom, Path sourcePath) throws IOException {
        return ImportSaveBundle(SaveIdentity.FromRom(rom), sourcePath);
    }

    /**
     * Imports an external save file and optional RTC sidecar into the managed
     * location for a tracked game.
     *
     * @param saveIdentity tracked save identity
     * @param sourcePath external save file
     * @return imported save bundle
     * @throws IOException when the file cannot be read or written
     */
    public static SaveDataBundle ImportSaveBundle(SaveIdentity saveIdentity, Path sourcePath) throws IOException {
        if (saveIdentity == null || !saveIdentity.batteryBackedSave()) {
            throw new IllegalArgumentException("This game does not support battery-backed saves.");
        }
        if (sourcePath == null || !Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new IOException("Select a valid save data file to import.");
        }

        byte[] saveData = Files.readAllBytes(sourcePath);
        byte[] rtcData = Files.exists(BuildRtcPath(sourcePath)) ? Files.readAllBytes(BuildRtcPath(sourcePath)) : new byte[0];
        if (saveData.length == 0 && rtcData.length == 0) {
            throw new IOException("The selected save data file is empty.");
        }

        Path preferredPath = PreferredSavePath(saveIdentity);
        Files.createDirectories(preferredPath.getParent());
        Files.write(preferredPath, saveData);
        Path preferredRtcPath = BuildRtcPath(preferredPath);
        if (rtcData.length > 0) {
            Files.write(preferredRtcPath, rtcData);
        } else {
            Files.deleteIfExists(preferredRtcPath);
        }

        Path fallbackPath = LegacySavePath(saveIdentity);
        if (!preferredPath.equals(fallbackPath)) {
            Files.deleteIfExists(fallbackPath);
            Files.deleteIfExists(BuildRtcPath(fallbackPath));
        }
        return new SaveDataBundle(saveData, rtcData);
    }

    /**
     * Writes a save snapshot to an arbitrary external path.
     *
     * @param saveData raw save bytes
     * @param destinationPath destination file path
     * @throws IOException when the file cannot be written
     */
    public static void ExportSave(byte[] saveData, Path destinationPath) throws IOException {
        ExportSave(saveData, new byte[0], destinationPath);
    }

    /**
     * Writes a save snapshot and optional RTC sidecar to an arbitrary external
     * path.
     *
     * @param saveData raw save bytes
     * @param supplementalData supplementary save bytes such as RTC state
     * @param destinationPath destination file path
     * @throws IOException when the file cannot be written
     */
    public static void ExportSave(byte[] saveData, byte[] supplementalData, Path destinationPath) throws IOException {
        if (saveData == null || saveData.length == 0) {
            if (supplementalData == null || supplementalData.length == 0) {
                throw new IOException("No save data is available to export.");
            }
        }
        if (destinationPath == null) {
            throw new IOException("Choose a destination file for the exported save.");
        }

        Path parent = destinationPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(destinationPath, saveData);
        Path rtcPath = BuildRtcPath(destinationPath);
        if (supplementalData != null && supplementalData.length > 0) {
            Files.write(rtcPath, supplementalData);
        } else {
            Files.deleteIfExists(rtcPath);
        }
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
        SaveDataBundle saveData = LoadSaveBundle(saveIdentity)
                .orElseThrow(() -> new IOException("No managed save file exists for this game."));
        ExportSave(saveData.primaryData(), saveData.supplementalData(), destinationPath);
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

    private static void MoveFallbackFilesToPreferred(Path preferredPath, Path fallbackPath) {
        if (preferredPath == null || fallbackPath == null || preferredPath.equals(fallbackPath)
                || Files.exists(preferredPath) || !Files.exists(fallbackPath)) {
            return;
        }

        try {
            Files.createDirectories(preferredPath.getParent());
            Files.move(fallbackPath, preferredPath);
        } catch (IOException exception) {
            // Keep using the fallback path when migration fails.
        }
    }

    private static Path SelectExistingPath(Path preferredPath, Path fallbackPath) {
        if (preferredPath != null && Files.exists(preferredPath)) {
            return preferredPath;
        }
        if (fallbackPath != null && Files.exists(fallbackPath)) {
            return fallbackPath;
        }
        return null;
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

    private static Path BuildRtcPath(Path savePath) {
        if (savePath == null) {
            return null;
        }

        String filename = savePath.getFileName().toString();
        if (filename.endsWith(".sav")) {
            filename = filename.substring(0, filename.length() - 4) + ".rtc";
        } else {
            filename = filename + ".rtc";
        }
        return savePath.resolveSibling(filename);
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

