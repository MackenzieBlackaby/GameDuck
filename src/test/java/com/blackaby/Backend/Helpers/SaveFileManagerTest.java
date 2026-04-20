package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveFileManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void saveLoadImportAndExportBundlePreserveRtcSidecar() throws IOException {
        String previous = System.getProperty("gameduck.save_dir");
        System.setProperty("gameduck.save_dir", tempDir.toString());
        try {
            var rom = EmulatorTestUtils.CreateBlankRom(0x10, 4, 0x03, 0x00, "rtc.gb", "rtc");
            byte[] saveData = new byte[] { 1, 2, 3, 4 };
            byte[] rtcData = new byte[] { 9, 8, 7 };

            SaveFileManager.Save(rom, saveData, rtcData);

            SaveFileManager.SaveDataBundle loaded = SaveFileManager.LoadSaveBundle(rom).orElseThrow();
            assertArrayEquals(saveData, loaded.primaryData());
            assertArrayEquals(rtcData, loaded.supplementalData());
            assertTrue(loaded.supplementalLastModifiedEpochSeconds() > 0L);

            Path exportPath = tempDir.resolve("exported.sav");
            SaveFileManager.ExportSave(rom, exportPath);
            assertTrue(Files.exists(exportPath));
            assertTrue(Files.exists(tempDir.resolve("exported.rtc")));

            byte[] importedSave = new byte[] { 5, 6 };
            byte[] importedRtc = new byte[] { 4, 3, 2, 1 };
            Path importPath = tempDir.resolve("imported.sav");
            Files.write(importPath, importedSave);
            Files.write(tempDir.resolve("imported.rtc"), importedRtc);

            SaveFileManager.SaveDataBundle imported = SaveFileManager.ImportSaveBundle(rom, importPath);
            assertArrayEquals(importedSave, imported.primaryData());
            assertArrayEquals(importedRtc, imported.supplementalData());

            SaveFileManager.SaveFileSummary summary = SaveFileManager.DescribeSaveFiles(rom);
            assertEquals(2, summary.existingFiles().size());
        } finally {
            RestoreSaveProperty(previous);
        }
    }

    @Test
    void rtcOnlyBundleCanRoundTripWithoutPrimarySaveBytes() throws IOException {
        String previous = System.getProperty("gameduck.save_dir");
        System.setProperty("gameduck.save_dir", tempDir.toString());
        try {
            var rom = EmulatorTestUtils.CreateBlankRom(0x0F, 4, 0x00, 0x00, "rtc_only.gb", "rtc-only");
            byte[] rtcData = new byte[] { 1, 3, 3, 7 };

            SaveFileManager.Save(rom, new byte[0], rtcData);

            SaveFileManager.SaveDataBundle loaded = SaveFileManager.LoadSaveBundle(rom).orElseThrow();
            assertEquals(0, loaded.primaryData().length);
            assertArrayEquals(rtcData, loaded.supplementalData());
            assertTrue(loaded.supplementalLastModifiedEpochSeconds() > 0L);
            assertTrue(Files.exists(SaveFileManager.PreferredSavePath(rom)));
        } finally {
            RestoreSaveProperty(previous);
        }
    }

    private static void RestoreSaveProperty(String previous) {
        if (previous == null) {
            System.clearProperty("gameduck.save_dir");
        } else {
            System.setProperty("gameduck.save_dir", previous);
        }
    }
}
