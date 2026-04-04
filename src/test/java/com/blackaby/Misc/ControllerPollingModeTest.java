package com.blackaby.Misc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerPollingModeTest {

    @AfterEach
    void resetSettings() {
        Settings.Reset();
    }

    @Test
    void resetControllerControlsDefaultsToBalancedPolling() {
        Settings.controllerPollingMode = ControllerPollingMode.LOW_LATENCY;

        Settings.ResetControllerControls();

        assertEquals(ControllerPollingMode.BALANCED, Settings.controllerPollingMode);
    }

    @Test
    void pollingPresetsExposeExpectedIntervals() {
        assertEquals(8L, ControllerPollingMode.LOW_LATENCY.PollIntervalMillis());
        assertEquals(16L, ControllerPollingMode.BALANCED.PollIntervalMillis());
        assertEquals(33L, ControllerPollingMode.POWER_SAVER.PollIntervalMillis());
    }
}
