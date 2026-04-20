package com.blackaby.Backend.Helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.blackaby.Backend.Platform.EmulatorGame;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameNotesStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsNotesForGameIdentity() {
        String previous = System.getProperty("gameduck.notes_dir");
        System.setProperty("gameduck.notes_dir", tempDir.toString());
        try {
            EmulatorGame game = new TestGame("demo.gb", "Demo", List.of());

            GameNotesStore.Save(game, "line one\nline two");

            assertEquals("line one\nline two", GameNotesStore.Load(game));
        } finally {
            restoreProperty("gameduck.notes_dir", previous);
        }
    }

    @Test
    void keepsPatchNotesSeparate() {
        String previous = System.getProperty("gameduck.notes_dir");
        System.setProperty("gameduck.notes_dir", tempDir.toString());
        try {
            EmulatorGame baseGame = new TestGame("demo.gb", "Demo", List.of());
            EmulatorGame patchedGame = new TestGame("demo.gb", "Demo", List.of("hard-mode.ips"));

            GameNotesStore.Save(baseGame, "base");
            GameNotesStore.Save(patchedGame, "patched");

            assertEquals("base", GameNotesStore.Load(baseGame));
            assertEquals("patched", GameNotesStore.Load(patchedGame));
        } finally {
            restoreProperty("gameduck.notes_dir", previous);
        }
    }

    private static void restoreProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    private record TestGame(String sourceName, String displayName, List<String> patchNames)
            implements EmulatorGame {
        @Override
        public String sourcePath() {
            return sourceName;
        }
    }
}
