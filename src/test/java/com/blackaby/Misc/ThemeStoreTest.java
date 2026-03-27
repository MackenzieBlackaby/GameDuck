package com.blackaby.Misc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadSeedsDefaultThemesAndSaveWritesJson() throws IOException {
        String previous = System.getProperty("gameduck.theme_dir");
        System.setProperty("gameduck.theme_dir", tempDir.toString());
        try {
            ThemeStore.LoadResult loadResult = ThemeStore.Load(new Properties());

            assertTrue(loadResult.seededDefaults());
            assertTrue(ThemeStore.SavedThemeNames().contains(AppThemePreset.HARBOR.Label()));

            AppTheme customTheme = AppThemePreset.HARBOR.Theme().Renamed("Custom Blue");
            customTheme.SetCoreColour(AppThemeColorRole.ACCENT, "#112233");
            ThemeStore.SaveTheme(customTheme);

            AppTheme loadedTheme = ThemeStore.FindTheme("Custom Blue");
            assertNotNull(loadedTheme);
            assertEquals("#112233", loadedTheme.CoreHex(AppThemeColorRole.ACCENT));

            List<Path> themeFiles = Files.list(tempDir).filter(path -> path.getFileName().toString().endsWith(".json")).toList();
            assertTrue(themeFiles.stream().anyMatch(path -> path.getFileName().toString().contains("Custom Blue")));

            String json = Files.readString(themeFiles.stream()
                    .filter(path -> path.getFileName().toString().contains("Custom Blue"))
                    .findFirst()
                    .orElseThrow(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"colors\""));
            assertTrue(json.contains("\"ACCENT\": \"#112233\""));
        } finally {
            RestoreThemeDirectoryProperty(previous);
        }
    }

    @Test
    void loadMigratesLegacyPropertyThemesIntoJsonFiles() {
        String previous = System.getProperty("gameduck.theme_dir");
        System.setProperty("gameduck.theme_dir", tempDir.toString());
        try {
            Properties legacyProperties = new Properties();
            String themeName = "Legacy Harbor";
            String encodedName = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(themeName.getBytes(StandardCharsets.UTF_8));
            legacyProperties.setProperty("theme.saved.names", encodedName);
            legacyProperties.setProperty("theme.saved." + encodedName + ".APP_BACKGROUND", "#010203");
            legacyProperties.setProperty("theme.saved." + encodedName + ".SURFACE", "#111213");
            legacyProperties.setProperty("theme.saved." + encodedName + ".ACCENT", "#212223");
            legacyProperties.setProperty("theme.saved." + encodedName + ".MUTED_TEXT", "#313233");
            legacyProperties.setProperty("theme.saved." + encodedName + ".DISPLAY_FRAME", "#414243");
            legacyProperties.setProperty("theme.saved." + encodedName + ".SECTION_HIGHLIGHT", "#515253");

            ThemeStore.LoadResult loadResult = ThemeStore.Load(legacyProperties);

            assertTrue(loadResult.migratedFromLegacy());
            assertEquals(List.of(
                    AppThemePreset.HARBOR.Label(),
                    AppThemePreset.MINT_DMG.Label(),
                    AppThemePreset.GRAPHITE.Label(),
                    AppThemePreset.SUNSET.Label(),
                    themeName), ThemeStore.SavedThemeNames());
            AppTheme migratedTheme = ThemeStore.FindTheme(themeName);
            assertNotNull(migratedTheme);
            assertEquals("#212223", migratedTheme.CoreHex(AppThemeColorRole.ACCENT));
            assertTrue(legacyProperties.getProperty("theme.saved.names", "").isBlank());
        } finally {
            RestoreThemeDirectoryProperty(previous);
        }
    }

    private static void RestoreThemeDirectoryProperty(String previous) {
        if (previous == null) {
            System.clearProperty("gameduck.theme_dir");
        } else {
            System.setProperty("gameduck.theme_dir", previous);
        }
    }
}
