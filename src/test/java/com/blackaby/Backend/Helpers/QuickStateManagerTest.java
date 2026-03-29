package com.blackaby.Backend.Helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blackaby.Backend.GB.Graphics.DuckPPU;
import com.blackaby.Backend.GB.Memory.DuckMemory;
import com.blackaby.Backend.GB.Peripherals.DuckJoypad;
import com.blackaby.Backend.GB.Peripherals.DuckTimer;
import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;
import com.blackaby.Frontend.DuckDisplay;

class QuickStateManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void saveLoadAndDescribeSlotsRoundTrip() throws IOException {
        String previous = System.getProperty("gameduck.quick_state_dir");
        System.setProperty("gameduck.quick_state_dir", tempDir.toString());
        try {
            var rom = EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "quick.gb", "quick");
            QuickStateManager.QuickStateData state = new QuickStateManager.QuickStateData(
                    null,
                    new DuckMemory.MemoryState(new int[0x10000], null, false, false, false, 0, 1, false, false,
                            false, 0, 0, 0, false, 0, 0x8000, 0, false, new int[2][0x2000], new int[8][0x1000],
                            new int[0x40], new int[0x40], null),
                    new DuckTimer.TimerState(0x1234, true, 2, true),
                    new DuckPPU.PpuState(1, 42, 123, true, 17, 6, true),
                    new DuckJoypad.JoypadState(0x10, 0x03),
                    null,
                    new DuckDisplay.FrameState(new int[] { 1, 2 }, new int[] { 3, 4 }),
                    42);

            QuickStateManager.Save(rom, 2, state);
            QuickStateManager.QuickStateData loaded = QuickStateManager.Load(rom, 2);

            assertEquals(42, loaded.previousLy());
            assertEquals(0x1234, loaded.timerState().internalCounter());
            assertTrue(QuickStateManager.DescribeSlots(rom).get(2).exists());
        } finally {
            RestoreQuickStateProperty(previous);
        }
    }

    @Test
    void moveExportImportAndDeleteManagedStates() throws IOException {
        String previous = System.getProperty("gameduck.quick_state_dir");
        System.setProperty("gameduck.quick_state_dir", tempDir.toString());
        try {
            var rom = EmulatorTestUtils.CreateBlankRom(0x00, 2, 0x00, 0x00, "quick.gb", "quick");
            QuickStateManager.QuickStateData state = new QuickStateManager.QuickStateData(
                    null, null, null, null, null, null, new DuckDisplay.FrameState(new int[0], new int[0]), 1);

            QuickStateManager.Save(rom, 1, state);
            QuickStateManager.MoveState(QuickStateManager.QuickStateIdentity.FromRom(rom), 1, 3);
            assertFalse(Files.exists(QuickStateManager.QuickStatePath(rom, 1)));
            assertTrue(Files.exists(QuickStateManager.QuickStatePath(rom, 3)));

            Path exportPath = tempDir.resolve("exported.gqs");
            QuickStateManager.ExportState(QuickStateManager.QuickStateIdentity.FromRom(rom), 3, exportPath);
            assertTrue(Files.exists(exportPath));

            QuickStateManager.ImportState(QuickStateManager.QuickStateIdentity.FromRom(rom), 4, exportPath);
            assertTrue(Files.exists(QuickStateManager.QuickStatePath(rom, 4)));

            QuickStateManager.DeleteAllStates(QuickStateManager.QuickStateIdentity.FromRom(rom));
            assertFalse(Files.exists(QuickStateManager.QuickStatePath(rom, 3)));
            assertFalse(Files.exists(QuickStateManager.QuickStatePath(rom, 4)));
        } finally {
            RestoreQuickStateProperty(previous);
        }
    }

    private static void RestoreQuickStateProperty(String previous) {
        if (previous == null) {
            System.clearProperty("gameduck.quick_state_dir");
        } else {
            System.setProperty("gameduck.quick_state_dir", previous);
        }
    }
}
