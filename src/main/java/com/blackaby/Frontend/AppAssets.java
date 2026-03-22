package com.blackaby.Frontend;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Centralises loading of packaged desktop UI artwork.
 */
public final class AppAssets {

    private static final String LOGO_NO_BG_PATH = "/Images/LogoNoBG.png";
    private static final String LOGO_NO_BG_128_PATH = "/Images/LogoNoBG128.png";
    private static final String LOGO_BG_PATH = "/Images/LogoBG.png";

    private AppAssets() {
    }

    /**
     * Returns the multi-resolution icon set used for application windows.
     *
     * @return ordered icon images for the frame chrome
     */
    public static List<Image> WindowIcons() {
        BufferedImage baseIcon = LoadBufferedImage(LOGO_NO_BG_PATH);
        if (baseIcon == null) {
            baseIcon = LoadBufferedImage(LOGO_NO_BG_128_PATH);
        }
        if (baseIcon == null) {
            return List.of();
        }
        return List.of(
                GameArtScaler.ScaleToSize(baseIcon, 16, 16),
                GameArtScaler.ScaleToSize(baseIcon, 24, 24),
                GameArtScaler.ScaleToSize(baseIcon, 32, 32),
                GameArtScaler.ScaleToSize(baseIcon, 48, 48),
                GameArtScaler.ScaleToSize(baseIcon, 64, 64),
                GameArtScaler.ScaleToSize(baseIcon, 128, 128),
                GameArtScaler.ScaleToSize(baseIcon, 256, 256));
    }

    /**
     * Creates a scaled logo icon from the transparent artwork.
     *
     * @param size target size in pixels
     * @return scaled icon or {@code null} if the resource could not be loaded
     */
    public static ImageIcon HeaderLogoIcon(int size) {
        return CreateScaledIcon(LOGO_NO_BG_PATH, size, size);
    }

    /**
     * Creates a scaled logo icon from the boxed artwork.
     *
     * @param size target size in pixels
     * @return scaled icon or {@code null} if the resource could not be loaded
     */
    public static ImageIcon AboutLogoIcon(int size) {
        return CreateScaledIcon(LOGO_BG_PATH, size, size);
    }

    private static ImageIcon CreateScaledIcon(String resourcePath, int width, int height) {
        BufferedImage image = LoadBufferedImage(resourcePath);
        if (image == null) {
            return null;
        }
        return new ImageIcon(GameArtScaler.ScaleToSize(image, width, height));
    }

    private static BufferedImage LoadBufferedImage(String resourcePath) {
        try (InputStream stream = AppAssets.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }
            return ImageIO.read(stream);
        } catch (IOException exception) {
            return null;
        }
    }
}
