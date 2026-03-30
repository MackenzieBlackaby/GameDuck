package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.CPU.GBProcessor;
import com.blackaby.Backend.GB.Graphics.GBPPU;
import com.blackaby.Backend.GB.Memory.GBMemory;
import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.GB.Peripherals.GBAudioProcessingUnit;
import com.blackaby.Backend.GB.Peripherals.GBGamepad;
import com.blackaby.Backend.GB.Peripherals.GBTimerSet;
import com.blackaby.Frontend.DuckDisplay;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists managed save-state slots per ROM identity.
 */
public final class QuickStateManager {

    public static final int quickSlot = 0;
    public static final int maxSlot = 9;

    public record QuickStateIdentity(String sourcePath, String sourceName, String displayName,
            List<String> patchNames) {
        public QuickStateIdentity {
            patchNames = List.copyOf(patchNames == null ? List.of() : patchNames);
        }

        public static QuickStateIdentity FromRom(GBRom rom) {
            if (rom == null) {
                return null;
            }
            return new QuickStateIdentity(
                    rom.GetSourcePath(),
                    rom.GetSourceName(),
                    rom.GetName(),
                    rom.GetPatchNames());
        }
    }

    public record QuickStateData(
            GBProcessor.CpuState cpuState,
            GBMemory.MemoryState memoryState,
            GBTimerSet.TimerState timerState,
            GBPPU.PpuState ppuState,
            GBGamepad.JoypadState joypadState,
            GBAudioProcessingUnit.ApuState apuState,
            DuckDisplay.FrameState displayState,
            int previousLy) implements java.io.Serializable {
    }

    public record StateSlotInfo(int slot, Path path, boolean exists, FileTime lastModified) {
    }

    private static final int fileMagic = 0x47515331;
    private static final int fileVersion = 2;

    private QuickStateManager() {
    }

    /**
     * Writes the quick slot for the supplied ROM.
     *
     * @param rom            loaded ROM identity
     * @param quickStateData state payload
     * @throws IOException when the state cannot be written
     */
    public static void Save(GBRom rom, QuickStateData quickStateData) throws IOException {
        Save(rom, quickSlot, quickStateData);
    }

    /**
     * Writes a managed state file for the supplied ROM and slot.
     *
     * @param rom            loaded ROM identity
     * @param slot           save-state slot from 0 to 9
     * @param quickStateData state payload
     * @throws IOException when the state cannot be written
     */
    public static void Save(GBRom rom, int slot, QuickStateData quickStateData) throws IOException {
        if (rom == null) {
            throw new IOException("Load a ROM before saving a state.");
        }
        if (quickStateData == null) {
            throw new IOException("No save-state data is available to write.");
        }

        ValidateSlot(slot);
        Path quickStatePath = QuickStatePath(rom, slot);
        Files.createDirectories(quickStatePath.getParent());
        try (ObjectOutputStream output = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(quickStatePath)))) {
            output.writeInt(fileMagic);
            output.writeInt(fileVersion);
            output.writeObject(quickStateData);
        }
    }

    /**
     * Loads the quick slot for the supplied ROM.
     *
     * @param rom loaded ROM identity
     * @return state payload
     * @throws IOException when the state cannot be found or read
     */
    public static QuickStateData Load(GBRom rom) throws IOException {
        return Load(rom, quickSlot);
    }

    /**
     * Loads a managed state file for the supplied ROM and slot.
     *
     * @param rom  loaded ROM identity
     * @param slot save-state slot from 0 to 9
     * @return state payload
     * @throws IOException when the state cannot be found or read
     */
    public static QuickStateData Load(GBRom rom, int slot) throws IOException {
        if (rom == null) {
            throw new IOException("Load a ROM before trying to load a state.");
        }

        ValidateSlot(slot);
        Path quickStatePath = QuickStatePath(rom, slot);
        if (!Files.exists(quickStatePath) || !Files.isRegularFile(quickStatePath)) {
            if (slot == quickSlot) {
                throw new IOException("No quick save exists for the current game yet.");
            }
            throw new IOException("No save state exists in slot " + slot + " yet.");
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(quickStatePath)))) {
            int magic = input.readInt();
            int version = input.readInt();
            if (magic != fileMagic || version != fileVersion) {
                throw new IOException("The save state format is not supported by this version of GameDuck.");
            }

            Object stateObject = input.readObject();
            if (!(stateObject instanceof QuickStateData quickStateData)) {
                throw new IOException("The save state file is corrupted.");
            }
            return quickStateData;
        } catch (ClassNotFoundException | ClassCastException exception) {
            throw new IOException("The save state file could not be read.", exception);
        }
    }

    /**
     * Returns the quick slot path for a ROM.
     *
     * @param rom loaded ROM identity
     * @return quick-slot path
     */
    public static Path QuickStatePath(GBRom rom) {
        return QuickStatePath(rom, quickSlot);
    }

    /**
     * Returns the managed save-state slot path for a ROM.
     *
     * @param rom  loaded ROM identity
     * @param slot save-state slot from 0 to 9
     * @return save-state path
     */
    public static Path QuickStatePath(GBRom rom, int slot) {
        return QuickStatePath(QuickStateIdentity.FromRom(rom), slot);
    }

    /**
     * Returns the quick slot path for a ROM identity.
     *
     * @param quickStateIdentity ROM identity
     * @return quick-slot path
     */
    public static Path QuickStatePath(QuickStateIdentity quickStateIdentity) {
        return QuickStatePath(quickStateIdentity, quickSlot);
    }

    /**
     * Returns the managed save-state slot path for a ROM identity.
     *
     * @param quickStateIdentity ROM identity
     * @param slot               save-state slot from 0 to 9
     * @return save-state path
     */
    public static Path QuickStatePath(QuickStateIdentity quickStateIdentity, int slot) {
        ValidateSlot(slot);
        String baseFileName = BaseFileName(quickStateIdentity);
        return QuickStateDirectory().resolve(baseFileName + SlotSuffix(slot) + ".gqs");
    }

    /**
     * Describes every managed slot for the supplied ROM.
     *
     * @param rom loaded ROM identity
     * @return slot metadata in slot order
     */
    public static List<StateSlotInfo> DescribeSlots(GBRom rom) {
        return DescribeSlots(QuickStateIdentity.FromRom(rom));
    }

    /**
     * Describes every managed slot for the supplied ROM identity.
     *
     * @param quickStateIdentity ROM identity
     * @return slot metadata in slot order
     */
    public static List<StateSlotInfo> DescribeSlots(QuickStateIdentity quickStateIdentity) {
        List<StateSlotInfo> slots = new ArrayList<>();
        for (int slot = quickSlot; slot <= maxSlot; slot++) {
            Path path = QuickStatePath(quickStateIdentity, slot);
            boolean exists = Files.exists(path) && Files.isRegularFile(path);
            FileTime lastModified = FileTime.fromMillis(0L);
            if (exists) {
                try {
                    lastModified = Files.getLastModifiedTime(path);
                } catch (IOException exception) {
                    lastModified = FileTime.fromMillis(0L);
                }
            }
            slots.add(new StateSlotInfo(slot, path, exists, lastModified));
        }
        return List.copyOf(slots);
    }

    /**
     * Imports an external save-state file into the managed slot.
     *
     * @param quickStateIdentity ROM identity
     * @param slot               save-state slot from 0 to 9
     * @param sourcePath         external save-state file
     * @throws IOException when the file cannot be imported
     */
    public static void ImportState(QuickStateIdentity quickStateIdentity, int slot, Path sourcePath)
            throws IOException {
        ValidateSlot(slot);
        if (sourcePath == null || !Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new IOException("Select a valid save-state file to import.");
        }

        Path targetPath = QuickStatePath(quickStateIdentity, slot);
        Files.createDirectories(targetPath.getParent());
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Exports the managed save-state slot to an external file.
     *
     * @param quickStateIdentity ROM identity
     * @param slot               save-state slot from 0 to 9
     * @param destinationPath    external destination
     * @throws IOException when the state cannot be exported
     */
    public static void ExportState(QuickStateIdentity quickStateIdentity, int slot, Path destinationPath)
            throws IOException {
        ValidateSlot(slot);
        if (destinationPath == null) {
            throw new IOException("Choose a destination file for the exported save state.");
        }

        Path sourcePath = QuickStatePath(quickStateIdentity, slot);
        if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new IOException(slot == quickSlot
                    ? "No quick save exists for this game yet."
                    : "No save state exists in slot " + slot + " yet.");
        }

        Path parent = destinationPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Deletes one managed save-state slot.
     *
     * @param quickStateIdentity ROM identity
     * @param slot               save-state slot from 0 to 9
     * @throws IOException when deletion fails
     */
    public static void DeleteState(QuickStateIdentity quickStateIdentity, int slot) throws IOException {
        ValidateSlot(slot);
        Files.deleteIfExists(QuickStatePath(quickStateIdentity, slot));
    }

    /**
     * Deletes all managed save-state slots for a ROM identity.
     *
     * @param quickStateIdentity ROM identity
     * @throws IOException when deletion fails
     */
    public static void DeleteAllStates(QuickStateIdentity quickStateIdentity) throws IOException {
        for (int slot = quickSlot; slot <= maxSlot; slot++) {
            DeleteState(quickStateIdentity, slot);
        }
    }

    /**
     * Moves one managed save-state slot to another slot for the same ROM.
     *
     * @param quickStateIdentity ROM identity
     * @param sourceSlot         source slot from 0 to 9
     * @param targetSlot         target slot from 0 to 9
     * @throws IOException when the move cannot be completed
     */
    public static void MoveState(QuickStateIdentity quickStateIdentity, int sourceSlot, int targetSlot)
            throws IOException {
        ValidateSlot(sourceSlot);
        ValidateSlot(targetSlot);
        if (sourceSlot == targetSlot) {
            return;
        }

        Path sourcePath = QuickStatePath(quickStateIdentity, sourceSlot);
        if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new IOException(sourceSlot == quickSlot
                    ? "No quick save exists for this game yet."
                    : "No save state exists in slot " + sourceSlot + " yet.");
        }

        Path targetPath = QuickStatePath(quickStateIdentity, targetSlot);
        Files.createDirectories(targetPath.getParent());
        Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String BaseFileName(QuickStateIdentity quickStateIdentity) {
        if (quickStateIdentity == null) {
            return "unknown";
        }

        String baseName = quickStateIdentity.sourceName();
        if (baseName == null || baseName.isBlank()) {
            baseName = quickStateIdentity.displayName();
        }
        if (baseName == null || baseName.isBlank()) {
            baseName = "unknown";
        }

        StringBuilder fileName = new StringBuilder(SanitiseFileComponent(baseName));
        for (String patchName : quickStateIdentity.patchNames()) {
            String sanitisedPatch = SanitiseFileComponent(patchName);
            if (!sanitisedPatch.isBlank()) {
                fileName.append(" [").append(sanitisedPatch).append("]");
            }
        }

        fileName.append(" [")
                .append(String.format("%08X", IdentityHash(quickStateIdentity)))
                .append("]");
        return fileName.toString();
    }

    private static String SlotSuffix(int slot) {
        return slot == quickSlot ? " [quick]" : " [slot " + slot + "]";
    }

    private static int IdentityHash(QuickStateIdentity quickStateIdentity) {
        String sourcePath = quickStateIdentity.sourcePath() == null ? "" : quickStateIdentity.sourcePath();
        String displayName = quickStateIdentity.displayName() == null ? "" : quickStateIdentity.displayName();
        return (sourcePath + "|" + displayName + "|" + String.join("|", quickStateIdentity.patchNames())).hashCode();
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

    private static Path QuickStateDirectory() {
        String configuredPath = System.getProperty("gameduck.quick_state_dir");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of("quickstates");
    }

    private static void ValidateSlot(int slot) {
        if (slot < quickSlot || slot > maxSlot) {
            throw new IllegalArgumentException("Save-state slots must be between 0 and 9.");
        }
    }
}
