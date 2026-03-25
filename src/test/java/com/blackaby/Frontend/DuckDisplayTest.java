package com.blackaby.Frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;

import org.junit.jupiter.api.Test;

class DuckDisplayTest {

    @Test
    void snapshotAndRestoreRoundTripFrontAndBackBuffers() {
        DuckDisplay display = new DuckDisplay();
        display.setPixel(0, 0, Color.RED.getRGB(), false);
        display.setPixel(1, 0, Color.GREEN.getRGB(), true);

        DuckDisplay.FrameState snapshot = display.SnapshotFrameState();

        display.clear();
        display.RestoreFrameState(snapshot);
        DuckDisplay.FrameState restored = display.SnapshotFrameState();

        assertEquals(Color.GREEN.getRGB(), restored.frontBuffer()[1]);
        assertEquals(Color.RED.getRGB(), restored.backBuffer()[0]);
    }

    @Test
    void clearResetsBuffersToBlack() {
        DuckDisplay display = new DuckDisplay();
        display.setPixel(10, 10, Color.WHITE.getRGB(), true);

        display.clear();
        DuckDisplay.FrameState snapshot = display.SnapshotFrameState();

        assertEquals(Color.BLACK.getRGB(), snapshot.frontBuffer()[(10 * 160) + 10]);
        assertEquals(Color.BLACK.getRGB(), snapshot.backBuffer()[(10 * 160) + 10]);
    }
}
