package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.Platform.EmulatorGame;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists user-editable plain-text notes for each game identity.
 */
public final class GameNotesStore {

    private GameNotesStore() {
    }

    /**
     * Loads the note text for the supplied game.
     *
     * @param game game identity
     * @return saved note text, or an empty string when no note exists
     */
    public static String Load(EmulatorGame game) {
        if (game == null) {
            return "";
        }

        Path notePath = BuildNotePath(game);
        if (!Files.isRegularFile(notePath)) {
            return "";
        }

        try {
            return Files.readString(notePath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            exception.printStackTrace();
            return "";
        }
    }

    /**
     * Saves plain-text note content for the supplied game.
     *
     * @param game game identity
     * @param text note text
     */
    public static void Save(EmulatorGame game, String text) {
        if (game == null) {
            return;
        }

        Path notePath = BuildNotePath(game);
        try {
            Path parent = notePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(notePath, text == null ? "" : text, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    static Path BuildNotePath(EmulatorGame game) {
        return NotesDirectory().resolve(BuildNoteKey(game) + ".txt");
    }

    private static String BuildNoteKey(EmulatorGame game) {
        if (game instanceof GBRom rom) {
            return KeyedPropertiesStore.Hash(String.join("|",
                    KeyedPropertiesStore.Hash(rom.ToByteArray()),
                    String.join("|", rom.GetPatchNames())));
        }

        if (game instanceof GameLibraryStore.LibraryEntry entry) {
            return entry.key();
        }

        SaveFileManager.SaveIdentity identity = SaveFileManager.SaveIdentity.FromGame(game);
        return KeyedPropertiesStore.Hash(String.join("|",
                KeyedPropertiesStore.NullToEmpty(identity.sourcePath()),
                KeyedPropertiesStore.NullToEmpty(identity.sourceName()),
                KeyedPropertiesStore.NullToEmpty(identity.displayName()),
                String.join("|", identity.patchNames())));
    }

    private static Path NotesDirectory() {
        String configuredPath = System.getProperty("gameduck.notes_dir");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of("saves", "notes");
    }
}
