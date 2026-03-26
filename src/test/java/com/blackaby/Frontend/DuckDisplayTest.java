package com.blackaby.Frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import com.blackaby.Misc.Settings;

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

    @Test
    void presentFrameCanBlendWithPreviousFrameForGhosting() {
        DuckDisplay display = new DuckDisplay();
        display.setPixel(0, 0, Color.BLACK.getRGB(), false);
        display.presentFrame();
        display.setPixel(0, 0, Color.WHITE.getRGB(), false);

        display.presentFrame(true);

        DuckDisplay.FrameState snapshot = display.SnapshotFrameState();
        assertEquals(0x9F9F9F, snapshot.frontBuffer()[0] & 0xFFFFFF);
        assertEquals(Color.WHITE.getRGB(), snapshot.backBuffer()[0]);
    }

    @Test
    void paintUsesConfiguredShaderWhenRenderingImageBuffer() {
        String originalShaderId = Settings.displayShaderId;
        try {
            Settings.displayShaderId = "amber_monitor";
            DuckDisplay display = new DuckDisplay();
            display.setSize(160, 144);
            int rawRgb = new Color(92, 140, 212).getRGB();
            display.setPixel(0, 0, rawRgb, false);
            display.presentFrame();

            BufferedImage canvas = new BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = canvas.createGraphics();
            display.paint(graphics);
            graphics.dispose();

            assertNotEquals(rawRgb & 0xFFFFFF, canvas.getRGB(0, 0) & 0xFFFFFF);
        } finally {
            Settings.displayShaderId = originalShaderId;
        }
    }
}
