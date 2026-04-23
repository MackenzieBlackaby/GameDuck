package com.blackaby.Misc;

import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;
import com.blackaby.Backend.Helpers.GameLibraryStore;
import com.blackaby.Backend.Helpers.ManagedGameRegistry;
import com.blackaby.Frontend.Shaders.DisplayShaderManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDuckDataBundleManagerTest {

    @TempDir
    Path tempDir;

    private Path workDir;
    private Path homeDir;
    private String previousUserDir;
    private String previousUserHome;
    private String previousConfigPath;
    private String previousPalettePath;
    private String previousThemeDir;
    private String previousShaderDir;
    private String previousBorderDir;
    private String previousBootRomDir;
    private String previousCacheDir;
    private String previousLibraryRomDir;
    private String previousLibraryMetadataPath;
    private String previousPortableLibraryMetadataPath;
    private String previousSaveDir;
    private String previousQuickStateDir;
    private String previousManagedGamesPath;
    private String previousGameMetadataPath;
    private String previousCheatMetadataPath;
    private String previousNotesDir;

    @BeforeEach
    void setUp() throws IOException {
        workDir = tempDir.resolve("work");
        homeDir = tempDir.resolve("home");
        Files.createDirectories(workDir);
        Files.createDirectories(homeDir);

        previousUserDir = System.getProperty("user.dir");
        previousUserHome = System.getProperty("user.home");
        previousConfigPath = System.getProperty("gameduck.config_path");
        previousPalettePath = System.getProperty("gameduck.palette_config_path");
        previousThemeDir = System.getProperty("gameduck.theme_dir");
        previousShaderDir = System.getProperty("gameduck.shader_dir");
        previousBorderDir = System.getProperty("gameduck.border_dir");
        previousBootRomDir = System.getProperty("gameduck.boot_rom_dir");
        previousCacheDir = System.getProperty("gameduck.cache_dir");
        previousLibraryRomDir = System.getProperty("gameduck.library_rom_dir");
        previousLibraryMetadataPath = System.getProperty("gameduck.library_metadata_path");
        previousPortableLibraryMetadataPath = System.getProperty("gameduck.library_portable_metadata_path");
        previousSaveDir = System.getProperty("gameduck.save_dir");
        previousQuickStateDir = System.getProperty("gameduck.quick_state_dir");
        previousManagedGamesPath = System.getProperty("gameduck.managed_games_path");
        previousGameMetadataPath = System.getProperty("gameduck.game_metadata_path");
        previousCheatMetadataPath = System.getProperty("gameduck.cheat_metadata_path");
        previousNotesDir = System.getProperty("gameduck.notes_dir");

        System.setProperty("user.dir", workDir.toString());
        System.setProperty("user.home", homeDir.toString());
        System.setProperty("gameduck.config_path", homeDir.resolve("config.properties").toString());
        System.setProperty("gameduck.palette_config_path", homeDir.resolve("palettes.json").toString());
        System.setProperty("gameduck.theme_dir", homeDir.resolve("themes").toString());
        System.setProperty("gameduck.shader_dir", homeDir.resolve("shaders").toString());
        System.setProperty("gameduck.border_dir", homeDir.resolve("borders").toString());
        System.setProperty("gameduck.boot_rom_dir", homeDir.toString());
        System.setProperty("gameduck.cache_dir", workDir.resolve("cache").toString());
        System.setProperty("gameduck.library_rom_dir", workDir.resolve("library").resolve("roms").toString());
        System.setProperty("gameduck.library_metadata_path", workDir.resolve("cache").resolve("game-library.properties").toString());
        System.setProperty("gameduck.library_portable_metadata_path", workDir.resolve("library").resolve("game-library.properties").toString());
        System.setProperty("gameduck.save_dir", workDir.resolve("saves").toString());
        System.setProperty("gameduck.quick_state_dir", workDir.resolve("quickstates").toString());
        System.setProperty("gameduck.managed_games_path", workDir.resolve("cache").resolve("managed-games.properties").toString());
        System.setProperty("gameduck.game_metadata_path", workDir.resolve("cache").resolve("game-metadata.properties").toString());
        System.setProperty("gameduck.cheat_metadata_path", workDir.resolve("cache").resolve("cheats.properties").toString());
        System.setProperty("gameduck.notes_dir", workDir.resolve("saves").resolve("notes").toString());

        GameLibraryStore.ResetForTests();
        ManagedGameRegistry.ResetForTests();
        Config.Load();
    }

    @AfterEach
    void tearDown() {
        restoreProperty("user.dir", previousUserDir);
        restoreProperty("user.home", previousUserHome);
        restoreProperty("gameduck.config_path", previousConfigPath);
        restoreProperty("gameduck.palette_config_path", previousPalettePath);
        restoreProperty("gameduck.theme_dir", previousThemeDir);
        restoreProperty("gameduck.shader_dir", previousShaderDir);
        restoreProperty("gameduck.border_dir", previousBorderDir);
        restoreProperty("gameduck.boot_rom_dir", previousBootRomDir);
        restoreProperty("gameduck.cache_dir", previousCacheDir);
        restoreProperty("gameduck.library_rom_dir", previousLibraryRomDir);
        restoreProperty("gameduck.library_metadata_path", previousLibraryMetadataPath);
        restoreProperty("gameduck.library_portable_metadata_path", previousPortableLibraryMetadataPath);
        restoreProperty("gameduck.save_dir", previousSaveDir);
        restoreProperty("gameduck.quick_state_dir", previousQuickStateDir);
        restoreProperty("gameduck.managed_games_path", previousManagedGamesPath);
        restoreProperty("gameduck.game_metadata_path", previousGameMetadataPath);
        restoreProperty("gameduck.cheat_metadata_path", previousCheatMetadataPath);
        restoreProperty("gameduck.notes_dir", previousNotesDir);
        GameLibraryStore.ResetForTests();
        ManagedGameRegistry.ResetForTests();
    }

    @Test
    void backupAndRestoreRoundTripRestoresManagedFiles() throws Exception {
        byte[] originalCache = new byte[] { 1, 2, 3, 4 };
        byte[] originalSave = new byte[] { 9, 8, 7 };
        byte[] originalQuickState = new byte[] { 5, 4, 3, 2 };
        byte[] originalDmgBootRom = new byte[BootRomManager.dmgBootRomSizeBytes];
        byte[] originalCgbBootRom = new byte[BootRomManager.cgbBootRomSizeBytes];
        originalDmgBootRom[0] = 0x11;
        originalCgbBootRom[0] = 0x22;

        Path cacheFile = Path.of(System.getProperty("gameduck.cache_dir")).resolve("art.cache");
        Path libraryRom = GameLibraryStore.LibraryDirectoryPath().resolve("roms").resolve("backup.gb");
        Path saveFile = Path.of(System.getProperty("gameduck.save_dir")).resolve("backup.sav");
        Path quickStateFile = Path.of(System.getProperty("gameduck.quick_state_dir")).resolve("backup.gqs");
        Path configFile = Config.ConfigFilePath();
        Path paletteFile = Config.PaletteFilePath();
        Path shaderFile = DisplayShaderManager.ShaderDirectory().resolve("custom.json");
        Path themeFile = ThemeStore.ThemeDirectoryPath().resolve("theme.json");
        Path bundlePath = tempDir.resolve("backup.gdlib");

        Files.createDirectories(cacheFile.getParent());
        Files.createDirectories(libraryRom.getParent());
        Files.createDirectories(saveFile.getParent());
        Files.createDirectories(quickStateFile.getParent());
        Files.createDirectories(shaderFile.getParent());
        Files.createDirectories(themeFile.getParent());
        Files.write(cacheFile, originalCache);
        Files.write(libraryRom, createRomBytes("backup.gb", "Backup Rom"));
        Files.write(saveFile, originalSave);
        Files.write(quickStateFile, originalQuickState);
        Files.writeString(configFile, "sound.enabled=false\n");
        Files.writeString(paletteFile, "{\"version\":1}\n");
        Files.writeString(shaderFile, """
                {
                  "id": "custom_test",
                  "name": "Custom Test",
                  "passes": []
                }
                """);
        Files.writeString(themeFile, """
                {
                  "name": "Backup Theme",
                  "colors": {
                    "SURFACE": "#112233",
                    "SURFACE_ACCENT": "#112233",
                    "SURFACE_BORDER": "#112233",
                    "APP_BACKGROUND": "#112233",
                    "TEXT_PRIMARY": "#112233",
                    "TEXT_MUTED": "#112233",
                    "ACCENT": "#112233",
                    "ACCENT_SOFT": "#112233",
                    "BUTTON_PRIMARY_BACKGROUND": "#112233",
                    "BUTTON_PRIMARY_FOREGROUND": "#112233",
                    "BUTTON_SECONDARY_BACKGROUND": "#112233",
                    "BUTTON_SECONDARY_FOREGROUND": "#112233",
                    "SECTION_HIGHLIGHT": "#112233",
                    "SECTION_HIGHLIGHT_BORDER": "#112233",
                    "DISPLAY_FRAME": "#112233",
                    "DISPLAY_FRAME_BORDER": "#112233",
                    "LIST_SELECTION": "#112233"
                  }
                }
                """);
        Files.write(BootRomManager.DmgBootRomPath(), originalDmgBootRom);
        Files.write(BootRomManager.CgbBootRomPath(), originalCgbBootRom);

        GameDuckDataBundleManager.CreateBackup(bundlePath);

        Files.write(cacheFile, new byte[] { 0 });
        Files.deleteIfExists(libraryRom);
        Files.write(saveFile, new byte[] { 0, 0 });
        Files.writeString(configFile, "sound.enabled=true\n");
        Files.deleteIfExists(shaderFile);
        Files.deleteIfExists(themeFile);
        Files.write(BootRomManager.DmgBootRomPath(), new byte[BootRomManager.dmgBootRomSizeBytes]);

        GameDuckDataBundleManager.RestoreBackup(bundlePath);

        assertArrayEquals(originalCache, Files.readAllBytes(cacheFile));
        assertArrayEquals(createRomBytes("backup.gb", "Backup Rom"), Files.readAllBytes(libraryRom));
        assertArrayEquals(originalSave, Files.readAllBytes(saveFile));
        assertArrayEquals(originalQuickState, Files.readAllBytes(quickStateFile));
        assertEquals("sound.enabled=false\n", Files.readString(configFile));
        assertTrue(Files.exists(shaderFile));
        assertTrue(Files.exists(themeFile));
        assertArrayEquals(originalDmgBootRom, Files.readAllBytes(BootRomManager.DmgBootRomPath()));
        assertArrayEquals(originalCgbBootRom, Files.readAllBytes(BootRomManager.CgbBootRomPath()));
    }

    @Test
    void deleteLibraryLeavesSaveDataInPlace() throws Exception {
        Path libraryRom = GameLibraryStore.LibraryDirectoryPath().resolve("roms").resolve("library-only.gb");
        Path saveFile = Path.of(System.getProperty("gameduck.save_dir")).resolve("library-only.sav");
        Files.createDirectories(libraryRom.getParent());
        Files.createDirectories(saveFile.getParent());
        Files.write(libraryRom, createRomBytes("library-only.gb", "Library Only"));
        Files.write(saveFile, new byte[] { 3, 2, 1 });

        GameDuckDataBundleManager.DeleteLibrary();

        assertFalse(Files.exists(libraryRom));
        assertTrue(Files.exists(saveFile));
    }

    @Test
    void resetShadersDeletesCustomShaderFilesAndResetsSelection() throws Exception {
        Path shaderFile = DisplayShaderManager.ShaderDirectory().resolve("custom-reset.json");
        Files.createDirectories(shaderFile.getParent());
        Files.writeString(shaderFile, """
                {
                  "id": "custom_reset",
                  "name": "Custom Reset",
                  "passes": []
                }
                """);
        Settings.displayShaderId = "custom_reset";

        GameDuckDataBundleManager.ResetShaders();

        assertEquals("none", Settings.displayShaderId);
        assertFalse(Files.exists(shaderFile));
        assertTrue(Files.exists(DisplayShaderManager.ShaderDirectory().resolve("README.txt")));
    }

    private byte[] createRomBytes(String filename, String displayName) {
        GBRom rom = EmulatorTestUtils.CreateBlankRom(0x10, 2, 0x03, 0x00, filename, displayName);
        return rom.ToByteArray();
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
