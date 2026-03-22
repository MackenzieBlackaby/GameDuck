package com.blackaby.Frontend;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Shared high-quality box-art scaling helpers for host UI previews.
 */
public final class GameArtScaler {

    private GameArtScaler() {
    }

    /**
     * Scales an image to fit inside the target bounds using multi-step
     * high-quality resampling to avoid blocky thumbnails.
     *
     * @param source source image
     * @param maxWidth maximum target width
     * @param maxHeight maximum target height
     * @return scaled image
     */
    public static BufferedImage ScaleToFit(BufferedImage source, int maxWidth, int maxHeight) {
        return ScaleToFit(source, maxWidth, maxHeight, false);
    }

    /**
     * Scales an image to fit inside the target bounds using multi-step
     * high-quality resampling to avoid blocky thumbnails.
     *
     * @param source source image
     * @param maxWidth maximum target width
     * @param maxHeight maximum target height
     * @param allowUpscale when true, smaller source images may be enlarged
     * @return scaled image
     */
    public static BufferedImage ScaleToFit(BufferedImage source, int maxWidth, int maxHeight, boolean allowUpscale) {
        if (source == null || maxWidth <= 0 || maxHeight <= 0) {
            return null;
        }

        double widthScale = maxWidth / (double) source.getWidth();
        double heightScale = maxHeight / (double) source.getHeight();
        double scale = Math.min(widthScale, heightScale);
        if (!allowUpscale) {
            scale = Math.min(1.0, scale);
        }
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));

        if (targetWidth == source.getWidth() && targetHeight == source.getHeight()) {
            return source;
        }

        BufferedImage current = source;
        int currentWidth = source.getWidth();
        int currentHeight = source.getHeight();

        while (currentWidth / 2 >= targetWidth && currentHeight / 2 >= targetHeight) {
            currentWidth = Math.max(targetWidth, currentWidth / 2);
            currentHeight = Math.max(targetHeight, currentHeight / 2);
            current = Resize(current, currentWidth, currentHeight);
        }

        if (currentWidth != targetWidth || currentHeight != targetHeight) {
            current = Resize(current, targetWidth, targetHeight);
        }

        return current;
    }

    /**
     * Scales an image so it completely covers the target bounds, cropping from
     * the center when the source aspect ratio does not match.
     *
     * @param source source image
     * @param targetWidth target width
     * @param targetHeight target height
     * @return scaled and center-cropped image
     */
    public static BufferedImage ScaleToCover(BufferedImage source, int targetWidth, int targetHeight) {
        if (source == null || targetWidth <= 0 || targetHeight <= 0) {
            return null;
        }

        double widthScale = targetWidth / (double) source.getWidth();
        double heightScale = targetHeight / (double) source.getHeight();
        double scale = Math.max(widthScale, heightScale);
        int scaledWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int scaledHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage scaledImage = Resize(source, scaledWidth, scaledHeight);
        if (scaledWidth == targetWidth && scaledHeight == targetHeight) {
            return scaledImage;
        }

        int cropX = Math.max(0, (scaledWidth - targetWidth) / 2);
        int cropY = Math.max(0, (scaledHeight - targetHeight) / 2);
        BufferedImage croppedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = croppedImage.createGraphics();
        graphics.drawImage(scaledImage,
                0, 0, targetWidth, targetHeight,
                cropX, cropY, cropX + targetWidth, cropY + targetHeight,
                null);
        graphics.dispose();
        return croppedImage;
    }

    private static BufferedImage Resize(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaledImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return scaledImage;
    }
}
