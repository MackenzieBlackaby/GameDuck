package com.blackaby.Frontend.Borders;

import com.blackaby.Frontend.Shaders.ShaderPreviewRenderer;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Renders representative preview frames for display borders.
 */
public final class DisplayBorderPreviewRenderer {

    private static final BufferedImage sourcePreviewImage = ShaderPreviewRenderer.render(null).sourceImage();

    private DisplayBorderPreviewRenderer() {
    }

    public static PreviewImages render(LoadedDisplayBorder border) {
        BufferedImage sourceImage = sourcePreviewImage;
        if (border == null || border.isNone() || border.image() == null || border.screenRect() == null) {
            return new PreviewImages(sourceImage, sourceImage);
        }

        BufferedImage previewImage = new BufferedImage(
                border.image().getWidth(),
                border.image().getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = previewImage.createGraphics();
        DisplayBorderRenderer.paint(graphics, sourceImage, border, previewImage.getWidth(), previewImage.getHeight());
        graphics.dispose();

        return new PreviewImages(sourceImage, previewImage);
    }

    public record PreviewImages(BufferedImage sourceImage, BufferedImage previewImage) {
    }
}
