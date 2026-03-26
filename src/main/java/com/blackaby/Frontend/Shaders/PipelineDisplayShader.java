package com.blackaby.Frontend.Shaders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A configurable multi-pass CPU shader built from reusable post-process passes.
 */
public final class PipelineDisplayShader implements DisplayShader {

    private final String id;
    private final String displayName;
    private final String description;
    private final List<ShaderPass> passes;

    /**
     * Creates a shader pipeline.
     *
     * @param id          stable shader id
     * @param displayName user-facing name
     * @param description user-facing description
     * @param passes      ordered pass list
     */
    public PipelineDisplayShader(String id, String displayName, String description, List<ShaderPass> passes) {
        this.id = id;
        this.displayName = displayName;
        this.description = description == null ? "" : description;
        this.passes = passes == null ? List.of() : List.copyOf(passes);
    }

    @Override
    public String Id() {
        return id;
    }

    @Override
    public String DisplayName() {
        return displayName;
    }

    @Override
    public String Description() {
        return description;
    }

    @Override
    public void Apply(int[] source, int[] target, int[] scratch, int width, int height) {
        if (source == null || target == null || scratch == null) {
            throw new IllegalArgumentException("Shader buffers cannot be null.");
        }
        if (source.length != target.length || source.length != scratch.length) {
            throw new IllegalArgumentException("Shader buffers must be the same size.");
        }
        if (passes.isEmpty()) {
            System.arraycopy(source, 0, target, 0, source.length);
            return;
        }

        int[] read = source;
        int[] write = target;
        for (ShaderPass pass : passes) {
            pass.apply(read, write, width, height);
            read = write;
            write = (write == target) ? scratch : target;
        }

        if (read != target) {
            System.arraycopy(read, 0, target, 0, read.length);
        }
    }

    /**
     * Creates the bundled built-in shader set.
     *
     * @return built-in shaders in display order
     */
    public static List<DisplayShader> BuiltIns() {
        List<DisplayShader> shaders = new ArrayList<>();
        shaders.add(new PipelineDisplayShader(
                "none",
                "Off",
                "Raw emulator output with no post-process shader.",
                List.of()));
        shaders.add(new PipelineDisplayShader(
                "classic_lcd",
                "Classic LCD",
                "A restrained Game Boy LCD look with a touch of warmth, scanlines, and edge falloff.",
                List.of(
                        new ColorGradePass(0.02, 1.05, 0.90, 0.08),
                        new PixelGridPass(0.06, 2, 3),
                        new ScanlinesPass(0.08, 2, 0),
                        new BloomPass(1, 0.10, 0.45),
                        new VignettePass(0.12, 1.6))));
        shaders.add(new PipelineDisplayShader(
                "phosphor_glow",
                "Phosphor Glow",
                "Soft bloom, faint chromatic separation, and deeper corners for a more dramatic display.",
                List.of(
                        new ColorGradePass(0.03, 1.08, 1.04, -0.03),
                        new BloomPass(2, 0.16, 0.34),
                        new RgbShiftPass(1, 0, -1, 0, 0.18),
                        new ScanlinesPass(0.06, 2, 1),
                        new VignettePass(0.18, 1.8))));
        shaders.add(new PipelineDisplayShader(
                "amber_monitor",
                "Amber Monitor",
                "A warm monochrome amber tint with bloom and scanlines for late-night sessions.",
                List.of(
                        new ColorGradePass(-0.03, 1.12, 0.18, 0.42),
                        new BloomPass(1, 0.12, 0.28),
                        new ScanlinesPass(0.10, 2, 0),
                        new VignettePass(0.16, 1.7))));
        return List.copyOf(shaders);
    }

    /**
     * Shared pass contract for JSON-defined and built-in shaders.
     */
    public interface ShaderPass {

        /**
         * Applies the pass from {@code source} into {@code target}.
         *
         * @param source source pixels
         * @param target output pixels
         * @param width  frame width
         * @param height frame height
         */
        void apply(int[] source, int[] target, int width, int height);
    }

    /**
     * Alternating scanline darkening.
     */
    public static final class ScanlinesPass implements ShaderPass {
        private final int spacing;
        private final int offset;
        private final int darkenScale256;
        private int cachedHeight = -1;
        private int[] cachedRowScale256 = new int[0];

        public ScanlinesPass(double intensity, int spacing, int offset) {
            this.darkenScale256 = scale256(1.0 - clampUnit(intensity));
            this.spacing = Math.max(1, spacing);
            this.offset = offset;
        }

        @Override
        public void apply(int[] source, int[] target, int width, int height) {
            ensureRowScaleCache(height);
            for (int y = 0; y < height; y++) {
                int rowOffset = y * width;
                int factor256 = cachedRowScale256[y];
                for (int x = 0; x < width; x++) {
                    target[rowOffset + x] = scaleRgb256(source[rowOffset + x], factor256);
                }
            }
        }

        private void ensureRowScaleCache(int height) {
            if (cachedHeight == height) {
                return;
            }

            cachedRowScale256 = new int[height];
            for (int y = 0; y < height; y++) {
                cachedRowScale256[y] = Math.floorMod(y + offset, spacing) == 0 ? darkenScale256 : 256;
            }
            cachedHeight = height;
        }
    }

    /**
     * Adds a subtle grid across rows and columns.
     */
    public static final class PixelGridPass implements ShaderPass {
        private final int rowSpacing;
        private final int columnSpacing;
        private final int rowScale256;
        private final int columnScale256;
        private int cachedWidth = -1;
        private int cachedHeight = -1;
        private int[] cachedRowScale256 = new int[0];
        private int[] cachedColumnScale256 = new int[0];

        public PixelGridPass(double intensity, int rowSpacing, int columnSpacing) {
            double clampedIntensity = clampUnit(intensity);
            this.rowSpacing = Math.max(1, rowSpacing);
            this.columnSpacing = Math.max(1, columnSpacing);
            this.rowScale256 = scale256(1.0 - clampedIntensity);
            this.columnScale256 = scale256(1.0 - (clampedIntensity * 0.8));
        }

        @Override
        public void apply(int[] source, int[] target, int width, int height) {
            ensureFactorCache(width, height);
            for (int y = 0; y < height; y++) {
                int rowOffset = y * width;
                int rowFactor256 = cachedRowScale256[y];
                for (int x = 0; x < width; x++) {
                    int factor256 = (rowFactor256 * cachedColumnScale256[x] + 128) >> 8;
                    target[rowOffset + x] = scaleRgb256(source[rowOffset + x], factor256);
                }
            }
        }

        private void ensureFactorCache(int width, int height) {
            if (cachedWidth != width) {
                cachedColumnScale256 = new int[width];
                for (int x = 0; x < width; x++) {
                    cachedColumnScale256[x] = Math.floorMod(x + 1, columnSpacing) == 0 ? columnScale256 : 256;
                }
                cachedWidth = width;
            }

            if (cachedHeight != height) {
                cachedRowScale256 = new int[height];
                for (int y = 0; y < height; y++) {
                    cachedRowScale256[y] = Math.floorMod(y + 1, rowSpacing) == 0 ? rowScale256 : 256;
                }
                cachedHeight = height;
            }
        }
    }

    /**
     * Darkens the edges to focus the image center.
     */
    public static final class VignettePass implements ShaderPass {
        private final double strength;
        private final double roundness;
        private int cachedWidth = -1;
        private int cachedHeight = -1;
        private int[] cachedScale256 = new int[0];

        public VignettePass(double strength, double roundness) {
            this.strength = clampUnit(strength);
            this.roundness = Math.max(0.6, roundness);
        }

        @Override
        public void apply(int[] source, int[] target, int width, int height) {
            ensureScaleCache(width, height);
            for (int index = 0; index < source.length; index++) {
                target[index] = scaleRgb256(source[index], cachedScale256[index]);
            }
        }

        private void ensureScaleCache(int width, int height) {
            if (cachedWidth == width && cachedHeight == height) {
                return;
            }

            cachedScale256 = new int[width * height];
            double halfWidth = Math.max(1.0, width / 2.0);
            double halfHeight = Math.max(1.0, height / 2.0);
            double[] xEdge = new double[width];
            double[] yEdge = new double[height];
            for (int x = 0; x < width; x++) {
                xEdge[x] = Math.pow(Math.abs(((x + 0.5) - halfWidth) / halfWidth), roundness);
            }
            for (int y = 0; y < height; y++) {
                yEdge[y] = Math.pow(Math.abs(((y + 0.5) - halfHeight) / halfHeight), roundness);
                int rowOffset = y * width;
                for (int x = 0; x < width; x++) {
                    double edge = xEdge[x] + yEdge[y];
                    double factor = 1.0 - (strength * clampUnit(edge / 1.4));
                    cachedScale256[rowOffset + x] = scale256(factor);
                }
            }
            cachedWidth = width;
            cachedHeight = height;
        }
    }

    /**
     * Brightens highlights by sampling nearby bright pixels.
     */
    public static final class BloomPass implements ShaderPass {
        private final int radius;
        private final int glowScale256;
        private final int luminanceThresholdScaled;
        private int cachedStride = -1;
        private int[] integralRed = new int[0];
        private int[] integralGreen = new int[0];
        private int[] integralBlue = new int[0];
        private int[] integralCount = new int[0];

        public BloomPass(int radius, double strength, double threshold) {
            this.radius = Math.max(1, Math.min(4, radius));
            this.glowScale256 = scale256(clampUnit(strength) * 0.55);
            this.luminanceThresholdScaled = (int) Math.round(clampUnit(threshold) * 255_000.0);
        }

        @Override
        public void apply(int[] source, int[] target, int width, int height) {
            int stride = width + 1;
            ensureIntegralCapacity(stride, height + 1);
            buildIntegralBuffers(source, width, height, stride);
            for (int y = 0; y < height; y++) {
                int rowOffset = y * width;
                int minY = Math.max(0, y - radius);
                int maxY = Math.min(height - 1, y + radius);
                for (int x = 0; x < width; x++) {
                    int baseRgb = source[rowOffset + x];
                    int minX = Math.max(0, x - radius);
                    int maxX = Math.min(width - 1, x + radius);
                    int count = regionSum(integralCount, stride, minX, minY, maxX, maxY);

                    if (count == 0) {
                        target[rowOffset + x] = baseRgb;
                        continue;
                    }

                    int averageRed = regionSum(integralRed, stride, minX, minY, maxX, maxY) / count;
                    int averageGreen = regionSum(integralGreen, stride, minX, minY, maxX, maxY) / count;
                    int averageBlue = regionSum(integralBlue, stride, minX, minY, maxX, maxY) / count;
                    int glowRed = (averageRed * glowScale256 + 128) >> 8;
                    int glowGreen = (averageGreen * glowScale256 + 128) >> 8;
                    int glowBlue = (averageBlue * glowScale256 + 128) >> 8;
                    target[rowOffset + x] = compose(
                            clampChannel(red(baseRgb) + glowRed),
                            clampChannel(green(baseRgb) + glowGreen),
                            clampChannel(blue(baseRgb) + glowBlue));
                }
            }
        }

        private void ensureIntegralCapacity(int stride, int rows) {
            int requiredLength = stride * rows;
            if (cachedStride == stride && integralRed.length == requiredLength) {
                return;
            }

            integralRed = new int[requiredLength];
            integralGreen = new int[requiredLength];
            integralBlue = new int[requiredLength];
            integralCount = new int[requiredLength];
            cachedStride = stride;
        }

        private void buildIntegralBuffers(int[] source, int width, int height, int stride) {
            Arrays.fill(integralRed, 0, stride, 0);
            Arrays.fill(integralGreen, 0, stride, 0);
            Arrays.fill(integralBlue, 0, stride, 0);
            Arrays.fill(integralCount, 0, stride, 0);

            for (int y = 1; y <= height; y++) {
                int rowSourceOffset = (y - 1) * width;
                int rowIntegralOffset = y * stride;
                integralRed[rowIntegralOffset] = 0;
                integralGreen[rowIntegralOffset] = 0;
                integralBlue[rowIntegralOffset] = 0;
                integralCount[rowIntegralOffset] = 0;

                int runningRed = 0;
                int runningGreen = 0;
                int runningBlue = 0;
                int runningCount = 0;

                for (int x = 1; x <= width; x++) {
                    int sampleRgb = source[rowSourceOffset + x - 1];
                    if (luminanceScaled(sampleRgb) >= luminanceThresholdScaled) {
                        runningRed += red(sampleRgb);
                        runningGreen += green(sampleRgb);
                        runningBlue += blue(sampleRgb);
                        runningCount++;
                    }

                    int integralIndex = rowIntegralOffset + x;
                    int previousRowIndex = integralIndex - stride;
                    integralRed[integralIndex] = integralRed[previousRowIndex] + runningRed;
                    integralGreen[integralIndex] = integralGreen[previousRowIndex] + runningGreen;
                    integralBlue[integralIndex] = integralBlue[previousRowIndex] + runningBlue;
                    integralCount[integralIndex] = integralCount[previousRowIndex] + runningCount;
                }
            }
        }
    }

    /**
     * Shifts the red and blue samples for a light chromatic split.
     */
    public static final class RgbShiftPass implements ShaderPass {
        private final int redX;
        private final int redY;
        private final int blueX;
        private final int blueY;
        private final double mix;

        public RgbShiftPass(int redX, int redY, int blueX, int blueY, double mix) {
            this.redX = redX;
            this.redY = redY;
            this.blueX = blueX;
            this.blueY = blueY;
            this.mix = clampUnit(mix);
        }

        @Override
        public void apply(int[] source, int[] target, int width, int height) {
            for (int y = 0; y < height; y++) {
                int rowOffset = y * width;
                for (int x = 0; x < width; x++) {
                    int baseRgb = source[rowOffset + x];
                    int redSample = source[sampleIndex(x + redX, y + redY, width, height)];
                    int blueSample = source[sampleIndex(x + blueX, y + blueY, width, height)];
                    int shifted = compose(red(redSample), green(baseRgb), blue(blueSample));
                    target[rowOffset + x] = blend(baseRgb, shifted, mix);
                }
            }
        }
    }

    /**
     * Applies simple brightness, contrast, saturation, and warmth grading.
     */
    public static final class ColorGradePass implements ShaderPass {
        private final double brightness;
        private final double contrast;
        private final double saturation;
        private final double warmth;

        public ColorGradePass(double brightness, double contrast, double saturation, double warmth) {
            this.brightness = clamp(brightness, -1.0, 1.0);
            this.contrast = clamp(contrast, 0.2, 2.5);
            this.saturation = clamp(saturation, 0.0, 2.5);
            this.warmth = clamp(warmth, -1.0, 1.0);
        }

        @Override
        public void apply(int[] source, int[] target, int width, int height) {
            double brightnessOffset = brightness * 255.0;
            for (int index = 0; index < source.length; index++) {
                int rgb = source[index];
                double red = ((red(rgb) - 127.5) * contrast) + 127.5 + brightnessOffset + (warmth * 36.0);
                double green = ((green(rgb) - 127.5) * contrast) + 127.5 + brightnessOffset + (warmth * 8.0);
                double blue = ((blue(rgb) - 127.5) * contrast) + 127.5 + brightnessOffset - (warmth * 40.0);
                double grayscale = (red * 0.299) + (green * 0.587) + (blue * 0.114);
                target[index] = compose(
                        clampChannel((int) Math.round(grayscale + ((red - grayscale) * saturation))),
                        clampChannel((int) Math.round(grayscale + ((green - grayscale) * saturation))),
                        clampChannel((int) Math.round(grayscale + ((blue - grayscale) * saturation))));
            }
        }
    }

    private static int sampleIndex(int x, int y, int width, int height) {
        int clampedX = Math.max(0, Math.min(width - 1, x));
        int clampedY = Math.max(0, Math.min(height - 1, y));
        return (clampedY * width) + clampedX;
    }

    private static int blend(int rgbA, int rgbB, double mix) {
        double clampedMix = clampUnit(mix);
        return compose(
                clampChannel((int) Math.round((red(rgbA) * (1.0 - clampedMix)) + (red(rgbB) * clampedMix))),
                clampChannel((int) Math.round((green(rgbA) * (1.0 - clampedMix)) + (green(rgbB) * clampedMix))),
                clampChannel((int) Math.round((blue(rgbA) * (1.0 - clampedMix)) + (blue(rgbB) * clampedMix))));
    }

    private static int scaleRgb256(int rgb, int factor256) {
        if (factor256 >= 256) {
            return rgb & 0xFFFFFF;
        }
        if (factor256 <= 0) {
            return 0;
        }
        return compose(
                (red(rgb) * factor256 + 128) >> 8,
                (green(rgb) * factor256 + 128) >> 8,
                (blue(rgb) * factor256 + 128) >> 8);
    }

    private static int regionSum(int[] integral, int stride, int minX, int minY, int maxX, int maxY) {
        int x0 = minX;
        int y0 = minY;
        int x1 = maxX + 1;
        int y1 = maxY + 1;
        int bottomRight = integral[(y1 * stride) + x1];
        int topRight = integral[(y0 * stride) + x1];
        int bottomLeft = integral[(y1 * stride) + x0];
        int topLeft = integral[(y0 * stride) + x0];
        return bottomRight - topRight - bottomLeft + topLeft;
    }

    private static int luminanceScaled(int rgb) {
        return (red(rgb) * 299) + (green(rgb) * 587) + (blue(rgb) * 114);
    }

    private static int scale256(double factor) {
        return Math.max(0, Math.min(256, (int) Math.round(factor * 256.0)));
    }

    private static int compose(int red, int green, int blue) {
        return (clampChannel(red) << 16) | (clampChannel(green) << 8) | clampChannel(blue);
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

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static double clampUnit(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
