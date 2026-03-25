package com.blackaby.Misc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaletteStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void migratesLegacyPaletteProperties() {
        Properties legacyProperties = new Properties();
        legacyProperties.setProperty("palette.current.0", "#112233");
        legacyProperties.setProperty("palette.current.1", "#223344");
        legacyProperties.setProperty("palette.current.2", "#334455");
        legacyProperties.setProperty("palette.current.3", "#445566");
        legacyProperties.setProperty("palette.prefer_dmg_mode_for_gbc_compatible_games", "true");
        legacyProperties.setProperty("palette.gbc_mode_enabled", "true");
        legacyProperties.setProperty("palette.gbc.background.0", "#AABBCC");
        legacyProperties.setProperty("palette.gbc.background.1", "#BBCCDD");
        legacyProperties.setProperty("palette.gbc.background.2", "#CCDDEE");
        legacyProperties.setProperty("palette.gbc.background.3", "#DDEEFF");

        String dmgName = "Mint";
        String gbcName = "Sunset";
        legacyProperties.setProperty("palette.names", EncodeLegacyName(dmgName));
        legacyProperties.setProperty("palette.saved." + EncodeLegacyName(dmgName) + ".0", "#010203");
        legacyProperties.setProperty("palette.saved." + EncodeLegacyName(dmgName) + ".1", "#111213");
        legacyProperties.setProperty("palette.saved." + EncodeLegacyName(dmgName) + ".2", "#212223");
        legacyProperties.setProperty("palette.saved." + EncodeLegacyName(dmgName) + ".3", "#313233");

        legacyProperties.setProperty("palette.gbc.saved.names", EncodeLegacyName(gbcName));
        legacyProperties.setProperty("palette.gbc.saved." + EncodeLegacyName(gbcName) + ".background.0", "#414243");
        legacyProperties.setProperty("palette.gbc.saved." + EncodeLegacyName(gbcName) + ".background.1", "#515253");
        legacyProperties.setProperty("palette.gbc.saved." + EncodeLegacyName(gbcName) + ".background.2", "#616263");
        legacyProperties.setProperty("palette.gbc.saved." + EncodeLegacyName(gbcName) + ".background.3", "#717273");
        legacyProperties.setProperty("palette.gbc.saved." + EncodeLegacyName(gbcName) + ".sprite0.0", "#818283");
        legacyProperties.setProperty("palette.gbc.saved." + EncodeLegacyName(gbcName) + ".sprite0.1", "#919293");
        legacyProperties.setProperty("palette.gbc.saved." + EncodeLegacyName(gbcName) + ".sprite0.2", "#A1A2A3");
        legacyProperties.setProperty("palette.gbc.saved." + EncodeLegacyName(gbcName) + ".sprite0.3", "#B1B2B3");
        legacyProperties.setProperty("palette.gbc.saved." + EncodeLegacyName(gbcName) + ".sprite1.0", "#C1C2C3");
        legacyProperties.setProperty("palette.gbc.saved." + EncodeLegacyName(gbcName) + ".sprite1.1", "#D1D2D3");
        legacyProperties.setProperty("palette.gbc.saved." + EncodeLegacyName(gbcName) + ".sprite1.2", "#E1E2E3");
        legacyProperties.setProperty("palette.gbc.saved." + EncodeLegacyName(gbcName) + ".sprite1.3", "#F1F2F3");

        PaletteStore.LoadResult result = PaletteStore.Load(tempDir.resolve("palettes.json"), legacyProperties);

        assertTrue(result.migratedFromLegacy());
        assertArrayEquals(new String[] { "#112233", "#223344", "#334455", "#445566" }, result.store().CurrentDmgPalette());
        assertTrue(result.store().PreferDmgModeForGbcCompatibleGames());
        assertTrue(result.store().GbcPaletteModeEnabled());
        assertArrayEquals(new String[] { "#AABBCC", "#BBCCDD", "#CCDDEE", "#DDEEFF" },
                result.store().CurrentGbcBackgroundPalette());
        assertEquals(List.of(dmgName), result.store().SavedDmgPaletteNames());
        assertArrayEquals(new String[] { "#010203", "#111213", "#212223", "#313233" },
                result.store().FindDmgPalette(dmgName));

        PaletteStore.StoredGbcPalette gbcPalette = result.store().FindGbcPalette(gbcName);
        assertNotNull(gbcPalette);
        assertEquals(List.of(gbcName), result.store().SavedGbcPaletteNames());
        assertArrayEquals(new String[] { "#414243", "#515253", "#616263", "#717273" }, gbcPalette.background());
        assertArrayEquals(new String[] { "#818283", "#919293", "#A1A2A3", "#B1B2B3" }, gbcPalette.sprite0());
        assertArrayEquals(new String[] { "#C1C2C3", "#D1D2D3", "#E1E2E3", "#F1F2F3" }, gbcPalette.sprite1());
    }

    @Test
    void savesAndLoadsPaletteJson() throws IOException {
        PaletteStore store = new PaletteStore();
        store.SetCurrentDmgPalette(new String[] { "#102030", "#203040", "#304050", "#405060" });
        store.SetPreferDmgModeForGbcCompatibleGames(true);
        store.SetGbcPaletteModeEnabled(true);
        store.SetCurrentGbcBackgroundPalette(new String[] { "#506070", "#607080", "#708090", "#8090A0" });
        store.SetCurrentGbcSpritePalette0(new String[] { "#90A0B0", "#A0B0C0", "#B0C0D0", "#C0D0E0" });
        store.SetCurrentGbcSpritePalette1(new String[] { "#D0E0F0", "#E0F001", "#F00112", "#011223" });
        store.SaveDmgPalette("Ocean", new String[] { "#111111", "#222222", "#333333", "#444444" });
        store.SaveGbcPalette(
                "Candy",
                new String[] { "#123456", "#234567", "#345678", "#456789" },
                new String[] { "#56789A", "#6789AB", "#789ABC", "#89ABCD" },
                new String[] { "#9ABCDE", "#ABCDEF", "#BCDEF0", "#CDEF01" });

        Path palettePath = tempDir.resolve("gameduck-palettes.json");
        store.Save(palettePath);

        String json = Files.readString(palettePath, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"saved\""));
        assertTrue(json.contains("\"Ocean\""));
        assertTrue(json.contains("\"Candy\""));

        PaletteStore.LoadResult result = PaletteStore.Load(palettePath, new Properties());

        assertFalse(result.migratedFromLegacy());
        assertArrayEquals(new String[] { "#102030", "#203040", "#304050", "#405060" }, result.store().CurrentDmgPalette());
        assertTrue(result.store().PreferDmgModeForGbcCompatibleGames());
        assertTrue(result.store().GbcPaletteModeEnabled());
        assertEquals(List.of("Ocean"), result.store().SavedDmgPaletteNames());
        assertArrayEquals(new String[] { "#111111", "#222222", "#333333", "#444444" },
                result.store().FindDmgPalette("Ocean"));

        PaletteStore.StoredGbcPalette gbcPalette = result.store().FindGbcPalette("Candy");
        assertNotNull(gbcPalette);
        assertEquals(List.of("Candy"), result.store().SavedGbcPaletteNames());
        assertArrayEquals(new String[] { "#123456", "#234567", "#345678", "#456789" }, gbcPalette.background());
        assertArrayEquals(new String[] { "#56789A", "#6789AB", "#789ABC", "#89ABCD" }, gbcPalette.sprite0());
        assertArrayEquals(new String[] { "#9ABCDE", "#ABCDEF", "#BCDEF0", "#CDEF01" }, gbcPalette.sprite1());
    }

    private static String EncodeLegacyName(String name) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));
    }
}
