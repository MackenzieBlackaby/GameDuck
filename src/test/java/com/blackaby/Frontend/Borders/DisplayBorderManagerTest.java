package com.blackaby.Frontend.Borders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

class DisplayBorderManagerTest {

    @TempDir
    Path tempHomeDirectory;

    @Test
    void builtInBorderIsAvailable() {
        DisplayBorderManager.Reload();

        LoadedDisplayBorder border = DisplayBorderManager.Resolve("studio_black");

        assertEquals("Studio Black", border.displayName());
        assertNotNull(border.image());
        assertNotNull(border.screenRect());
    }

    @Test
    void reloadLoadsCustomBorderFromManagedBorderDirectory() throws Exception {
        String originalHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", tempHomeDirectory.toString());
            Path borderDirectory = DisplayBorderManager.BorderDirectory();
            Files.createDirectories(borderDirectory);
            writeCustomBorder(borderDirectory.resolve("custom-border.png"));

            DisplayBorderManager.Reload();

            LoadedDisplayBorder border = DisplayBorderManager.Resolve("custom_border");
            assertEquals("Custom Border", border.displayName());
            assertEquals("Custom PNG", border.sourceLabel());
            assertNotNull(border.screenRect());
            assertFalse(DisplayBorderManager.GetAvailableBorders().isEmpty());
            assertTrue(DisplayBorderManager.GetLoadErrors().isEmpty());
            assertTrue(Files.exists(borderDirectory.resolve("README.txt")));
        } finally {
            System.setProperty("user.home", originalHome);
            DisplayBorderManager.Reload();
        }
    }

    private void writeCustomBorder(Path path) throws Exception {
        BufferedImage image = new BufferedImage(400, 320, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new java.awt.Color(0x22, 0x33, 0x44));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(80, 56, 240, 208);
        graphics.dispose();
        ImageIO.write(image, "png", path.toFile());
    }
}
