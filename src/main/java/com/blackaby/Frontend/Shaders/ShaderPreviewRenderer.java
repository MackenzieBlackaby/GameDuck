package com.blackaby.Frontend.Shaders;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * Renders representative preview frames for editable shader presets.
 */
public final class ShaderPreviewRenderer {

    private static final int LOGICAL_WIDTH = 160;
    private static final int LOGICAL_HEIGHT = 144;
    private static final int[] SAMPLE_LOGICAL_FRAME = buildSampleFrame();

    private ShaderPreviewRenderer() {
    }

    public static PreviewImages render(ShaderPresetDocument document) {
        int renderScale = document == null ? 1 : Math.max(1, document.renderScale());
        int sourceScale = 1;
        int sourceWidth = LOGICAL_WIDTH * sourceScale;
        int sourceHeight = LOGICAL_HEIGHT * sourceScale;
        int renderWidth = LOGICAL_WIDTH * renderScale;
        int renderHeight = LOGICAL_HEIGHT * renderScale;

        int[] sourcePixels = new int[renderWidth * renderHeight];
        int[] targetPixels = new int[renderWidth * renderHeight];
        int[] scratchPixels = new int[renderWidth * renderHeight];
        prepareShaderSource(SAMPLE_LOGICAL_FRAME, sourcePixels, sourceScale);

        if (document == null) {
            System.arraycopy(sourcePixels, 0, targetPixels, 0, sourceWidth * sourceHeight);
        } else {
            document.toShader().Apply(sourcePixels, targetPixels, scratchPixels, sourceWidth, sourceHeight);
        }

        return new PreviewImages(
                imageFromPixels(sourcePixels, sourceWidth, sourceHeight),
                imageFromPixels(targetPixels, renderWidth, renderHeight));
    }

    public record PreviewImages(BufferedImage sourceImage, BufferedImage previewImage) {
    }

    private static BufferedImage imageFromPixels(int[] pixels, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] imagePixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(pixels, 0, imagePixels, 0, Math.min(pixels.length, imagePixels.length));
        return image;
    }

    private static void prepareShaderSource(int[] logicalSource, int[] renderTarget, int renderScale) {
        if (renderScale <= 1) {
            System.arraycopy(logicalSource, 0, renderTarget, 0, Math.min(logicalSource.length, renderTarget.length));
            return;
        }

        int renderWidth = LOGICAL_WIDTH * renderScale;
        for (int y = 0; y < LOGICAL_HEIGHT; y++) {
            int sourceRowOffset = y * LOGICAL_WIDTH;
            int renderRowBase = y * renderScale * renderWidth;
            for (int x = 0; x < LOGICAL_WIDTH; x++) {
                int rgb = logicalSource[sourceRowOffset + x];
                int renderX = x * renderScale;
                for (int subY = 0; subY < renderScale; subY++) {
                    int destinationOffset = renderRowBase + (subY * renderWidth) + renderX;
                    Arrays.fill(renderTarget, destinationOffset, destinationOffset + renderScale, rgb);
                }
            }
        }
    }

    private static int[] buildSampleFrame() {
        int[] pixels = new int[LOGICAL_WIDTH * LOGICAL_HEIGHT];
        int light = 0xE0F8D0;
        int midLight = 0x88C070;
        int midDark = 0x346856;
        int dark = 0x081820;

        for (int y = 0; y < LOGICAL_HEIGHT; y++) {
            double skyMix = Math.min(1.0, y / 90.0);
            int base = blend(light, midLight, skyMix);
            for (int x = 0; x < LOGICAL_WIDTH; x++) {
                double vignette = 0.06 + (Math.abs((x - 80) / 80.0) * 0.08);
                pixels[(y * LOGICAL_WIDTH) + x] = blend(base, midDark, vignette);
            }
        }

        fillRect(pixels, 0, 104, 160, 40, midDark);
        fillRect(pixels, 0, 116, 160, 28, dark);

        for (int y = 104; y < 116; y += 4) {
            for (int x = 0; x < LOGICAL_WIDTH; x += 4) {
                if (((x + y) / 4) % 2 == 0) {
                    fillRect(pixels, x, y, 4, 4, blend(midDark, dark, 0.18));
                }
            }
        }

        fillCircle(pixels, 124, 28, 14, blend(light, 0xFFF6AF, 0.50));
        fillCircle(pixels, 124, 28, 8, 0xFFF6AF);

        drawRect(pixels, 12, 12, 44, 24, dark);
        fillRect(pixels, 13, 13, 42, 22, blend(light, midLight, 0.20));
        fillRect(pixels, 16, 17, 6, 14, dark);
        fillRect(pixels, 25, 17, 6, 14, midDark);
        fillRect(pixels, 34, 17, 18, 6, midDark);
        fillRect(pixels, 34, 25, 14, 6, dark);

        fillRect(pixels, 24, 58, 40, 28, dark);
        fillRect(pixels, 28, 62, 32, 20, midDark);
        fillRect(pixels, 32, 54, 24, 10, midLight);
        fillRect(pixels, 35, 66, 6, 6, light);
        fillRect(pixels, 47, 66, 6, 6, light);
        fillRect(pixels, 38, 76, 12, 3, light);

        fillRect(pixels, 84, 40, 44, 58, dark);
        fillRect(pixels, 88, 44, 36, 50, blend(midLight, light, 0.25));
        fillRect(pixels, 92, 48, 28, 6, midDark);
        fillRect(pixels, 92, 58, 20, 4, midDark);
        fillRect(pixels, 92, 66, 24, 4, midDark);
        fillRect(pixels, 92, 74, 18, 4, midDark);
        fillRect(pixels, 92, 82, 12, 4, midDark);

        drawRect(pixels, 70, 16, 32, 18, dark);
        fillRect(pixels, 72, 18, 28, 14, blend(light, midLight, 0.10));
        for (int x = 74; x < 98; x += 4) {
            fillRect(pixels, x, 20, 2, 10, dark);
        }

        for (int x = 18; x < 142; x += 16) {
            fillRect(pixels, x, 110, 10, 3, light);
        }

        fillCircle(pixels, 136, 92, 6, light);
        fillCircle(pixels, 142, 88, 2, 0xFFFFFF);
        fillCircle(pixels, 116, 94, 2, 0xFFFFFF);

        return pixels;
    }

    private static void fillRect(int[] pixels, int x, int y, int width, int height, int rgb) {
        int startX = Math.max(0, x);
        int startY = Math.max(0, y);
        int endX = Math.min(LOGICAL_WIDTH, x + width);
        int endY = Math.min(LOGICAL_HEIGHT, y + height);
        for (int row = startY; row < endY; row++) {
            int rowOffset = row * LOGICAL_WIDTH;
            Arrays.fill(pixels, rowOffset + startX, rowOffset + endX, rgb);
        }
    }

    private static void drawRect(int[] pixels, int x, int y, int width, int height, int rgb) {
        fillRect(pixels, x, y, width, 1, rgb);
        fillRect(pixels, x, y + height - 1, width, 1, rgb);
        fillRect(pixels, x, y, 1, height, rgb);
        fillRect(pixels, x + width - 1, y, 1, height, rgb);
    }

    private static void fillCircle(int[] pixels, int centerX, int centerY, int radius, int rgb) {
        int radiusSquared = radius * radius;
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            if (y < 0 || y >= LOGICAL_HEIGHT) {
                continue;
            }
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                if (x < 0 || x >= LOGICAL_WIDTH) {
                    continue;
                }
                int dx = x - centerX;
                int dy = y - centerY;
                if ((dx * dx) + (dy * dy) <= radiusSquared) {
                    pixels[(y * LOGICAL_WIDTH) + x] = rgb;
                }
            }
        }
    }

    private static int blend(int startRgb, int endRgb, double amount) {
        double clamped = Math.max(0.0, Math.min(1.0, amount));
        int red = (int) Math.round(red(startRgb) + ((red(endRgb) - red(startRgb)) * clamped));
        int green = (int) Math.round(green(startRgb) + ((green(endRgb) - green(startRgb)) * clamped));
        int blue = (int) Math.round(blue(startRgb) + ((blue(endRgb) - blue(startRgb)) * clamped));
        return (red << 16) | (green << 8) | blue;
    }

    private static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    private static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }
}
