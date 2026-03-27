package com.blackaby.Frontend.Shaders;

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
    private final boolean prefersAsyncRendering;

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
        this.prefersAsyncRendering = this.passes.stream().anyMatch(ShaderPass::prefersAsyncRendering);
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
    public boolean PreferAsyncRendering() {
        return prefersAsyncRendering;
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

        /**
         * Returns whether this pass is expensive enough to prefer async rendering.
         *
         * @return true when async rendering is preferred
         */
        default boolean prefersAsyncRendering() {
            return false;
        }
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
     * Shapes pixels into repeated rounded dots for LCD and matrix-style looks.
     */
    public static final class DotMatrixPass implements ShaderPass {
        private final double intensity;
        private final int cellWidth;
        private final int cellHeight;
        private final double roundness;
        private int cachedWidth = -1;
        private int cachedHeight = -1;
        private int[] cachedScale256 = new int[0];

        public DotMatrixPass(double intensity, int cellWidth, int cellHeight, double roundness) {
            this.intensity = clampUnit(intensity);
            this.cellWidth = Math.max(1, cellWidth);
            this.cellHeight = Math.max(1, cellHeight);
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
            double[] xShape = new double[width];
            double[] yShape = new double[height];
            for (int x = 0; x < width; x++) {
                xShape[x] = axisDistance(x, cellWidth);
            }
            for (int y = 0; y < height; y++) {
                yShape[y] = axisDistance(y, cellHeight);
            }
            for (int y = 0; y < height; y++) {
                int rowOffset = y * width;
                for (int x = 0; x < width; x++) {
                    double edge = clampUnit((xShape[x] + yShape[y]) / 1.35);
                    double factor = 1.0 - (Math.pow(edge, 0.85) * intensity);
                    cachedScale256[rowOffset + x] = scale256(factor);
                }
            }

            cachedWidth = width;
            cachedHeight = height;
        }

        private double axisDistance(int index, int cellSize) {
            double position = (((index % cellSize) + 0.5) / cellSize) * 2.0 - 1.0;
            return Math.pow(Math.abs(position), roundness);
        }
    }

    /**
     * Darkens the border of each repeated pixel cell.
     */
    public static final class PixelOutlinePass implements ShaderPass {
        private final double intensity;
        private final int cellWidth;
        private final int cellHeight;
        private final int edgeWidth;
        private int cachedWidth = -1;
        private int cachedHeight = -1;
        private int[] cachedScale256 = new int[0];

        public PixelOutlinePass(double intensity, int cellWidth, int cellHeight, int edgeWidth) {
            this.intensity = clampUnit(intensity);
            this.cellWidth = Math.max(1, cellWidth);
            this.cellHeight = Math.max(1, cellHeight);
            this.edgeWidth = Math.max(1, edgeWidth);
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
            for (int y = 0; y < height; y++) {
                int rowOffset = y * width;
                int distanceY = distanceToCellEdge(y, cellHeight);
                for (int x = 0; x < width; x++) {
                    int distanceX = distanceToCellEdge(x, cellWidth);
                    double darkness = 0.0;
                    if (distanceX < edgeWidth) {
                        darkness = Math.max(darkness, (edgeWidth - distanceX) / (double) edgeWidth);
                    }
                    if (distanceY < edgeWidth) {
                        darkness = Math.max(darkness, (edgeWidth - distanceY) / (double) edgeWidth);
                    }
                    cachedScale256[rowOffset + x] = scale256(1.0 - (darkness * intensity));
                }
            }

            cachedWidth = width;
            cachedHeight = height;
        }

        private int distanceToCellEdge(int index, int cellSize) {
            int offset = Math.floorMod(index, cellSize);
            return Math.min(offset, cellSize - 1 - offset);
        }
    }

    /**
     * Interpolates enlarged sprite corners while keeping cell centers crisp.
     */
    public static final class SpriteInterpolationPass implements ShaderPass {
        private final double strength;
        private final int cellWidth;
        private final int cellHeight;
        private final int sharpnessBias;

        public SpriteInterpolationPass(double strength, int cellWidth, int cellHeight, double sharpness) {
            this.strength = clampUnit(strength);
            this.cellWidth = Math.max(1, cellWidth);
            this.cellHeight = Math.max(1, cellHeight);
            this.sharpnessBias = Math.max(0, (int) Math.round(clamp(sharpness, 1.0, 6.0) - 1.0));
        }

        @Override
        public void apply(int[] source, int[] target, int width, int height) {
            if (strength <= 0.0 || (cellWidth <= 1 && cellHeight <= 1)) {
                System.arraycopy(source, 0, target, 0, source.length);
                return;
            }

            int cellColumns = (width + cellWidth - 1) / cellWidth;
            int cellRows = (height + cellHeight - 1) / cellHeight;
            for (int cellY = 0; cellY < cellRows; cellY++) {
                int startY = cellY * cellHeight;
                int endY = Math.min(height, startY + cellHeight);
                int actualCellHeight = endY - startY;
                for (int cellX = 0; cellX < cellColumns; cellX++) {
                    int startX = cellX * cellWidth;
                    int endX = Math.min(width, startX + cellWidth);
                    int actualCellWidth = endX - startX;

                    int center = sampleCell(source, width, height, cellX, cellY);
                    int north = sampleCell(source, width, height, cellX, cellY - 1);
                    int south = sampleCell(source, width, height, cellX, cellY + 1);
                    int west = sampleCell(source, width, height, cellX - 1, cellY);
                    int east = sampleCell(source, width, height, cellX + 1, cellY);

                    int topLeft = center;
                    int topRight = center;
                    int bottomLeft = center;
                    int bottomRight = center;
                    if (north != south && west != east) {
                        topLeft = west == north ? north : center;
                        topRight = north == east ? east : center;
                        bottomLeft = west == south ? west : center;
                        bottomRight = south == east ? east : center;
                    }

                    int shadedTopLeft = topLeft == center ? center : blend(center, topLeft, strength);
                    int shadedTopRight = topRight == center ? center : blend(center, topRight, strength);
                    int shadedBottomLeft = bottomLeft == center ? center : blend(center, bottomLeft, strength);
                    int shadedBottomRight = bottomRight == center ? center : blend(center, bottomRight, strength);

                    for (int y = startY; y < endY; y++) {
                        int verticalRegion = subCellRegion(y - startY, actualCellHeight);
                        int rowOffset = y * width;
                        for (int x = startX; x < endX; x++) {
                            int horizontalRegion = subCellRegion(x - startX, actualCellWidth);
                            target[rowOffset + x] = switch ((verticalRegion + 1) * 3 + (horizontalRegion + 1)) {
                                case 0 -> shadedTopLeft;
                                case 2 -> shadedTopRight;
                                case 6 -> shadedBottomLeft;
                                case 8 -> shadedBottomRight;
                                default -> center;
                            };
                        }
                    }
                }
            }
        }

        private int subCellRegion(int offset, int span) {
            if (span <= 1) {
                return 0;
            }
            int centerStart = Math.max(0, (span / 2) - sharpnessBias);
            int centerEnd = Math.min(span, ((span + 1) / 2) + sharpnessBias);
            if (offset < centerStart) {
                return -1;
            }
            if (offset >= centerEnd) {
                return 1;
            }
            return 0;
        }

        private int sampleCell(int[] source, int width, int height, int cellX, int cellY) {
            int maxCellX = Math.max(0, (width - 1) / cellWidth);
            int maxCellY = Math.max(0, (height - 1) / cellHeight);
            int clampedCellX = Math.max(0, Math.min(maxCellX, cellX));
            int clampedCellY = Math.max(0, Math.min(maxCellY, cellY));
            int sampleX = Math.min(width - 1, (clampedCellX * cellWidth) + ((cellWidth - 1) / 2));
            int sampleY = Math.min(height - 1, (clampedCellY * cellHeight) + ((cellHeight - 1) / 2));
            return source[(sampleY * width) + sampleX];
        }
    }

    /**
     * Shared helper for fixed-scale pixel-art upscalers that reinterpret the
     * nearest-neighbour-expanded frame as a logical cell grid.
     */
    private abstract static class FixedCellScalerPass implements ShaderPass {
        private final int scale;
        private int cachedLogicalWidth = -1;
        private int cachedLogicalHeight = -1;
        private int[] logicalBuffer = new int[0];

        protected FixedCellScalerPass(int scale) {
            this.scale = Math.max(1, scale);
        }

        @Override
        public void apply(int[] source, int[] target, int width, int height) {
            if (scale <= 1
                    || width <= 0
                    || height <= 0
                    || width % scale != 0
                    || height % scale != 0) {
                System.arraycopy(source, 0, target, 0, source.length);
                return;
            }

            int logicalWidth = width / scale;
            int logicalHeight = height / scale;
            ensureLogicalBuffer(logicalWidth, logicalHeight);
            populateLogicalBuffer(source, width, height, logicalWidth, logicalHeight);

            for (int cellY = 0; cellY < logicalHeight; cellY++) {
                for (int cellX = 0; cellX < logicalWidth; cellX++) {
                    renderCell(logicalBuffer, logicalWidth, logicalHeight, cellX, cellY, target, width);
                }
            }
        }

        protected abstract void renderCell(
                int[] logicalBuffer,
                int logicalWidth,
                int logicalHeight,
                int cellX,
                int cellY,
                int[] target,
                int renderWidth);

        protected final int scale() {
            return scale;
        }

        protected final int sampleLogical(int[] logicalBuffer, int logicalWidth, int logicalHeight, int x, int y) {
            int clampedX = Math.max(0, Math.min(logicalWidth - 1, x));
            int clampedY = Math.max(0, Math.min(logicalHeight - 1, y));
            return logicalBuffer[(clampedY * logicalWidth) + clampedX];
        }

        protected final void fillCell(int[] target, int renderWidth, int cellX, int cellY, int rgb) {
            int startX = cellX * scale;
            int startY = cellY * scale;
            for (int subY = 0; subY < scale; subY++) {
                int rowOffset = (startY + subY) * renderWidth;
                Arrays.fill(target, rowOffset + startX, rowOffset + startX + scale, rgb);
            }
        }

        protected final void setCellPixel(int[] target, int renderWidth, int cellX, int cellY, int subX, int subY, int rgb) {
            int x = (cellX * scale) + subX;
            int y = (cellY * scale) + subY;
            target[(y * renderWidth) + x] = rgb;
        }

        private void ensureLogicalBuffer(int logicalWidth, int logicalHeight) {
            int requiredLength = logicalWidth * logicalHeight;
            if (cachedLogicalWidth == logicalWidth
                    && cachedLogicalHeight == logicalHeight
                    && logicalBuffer.length == requiredLength) {
                return;
            }

            logicalBuffer = new int[requiredLength];
            cachedLogicalWidth = logicalWidth;
            cachedLogicalHeight = logicalHeight;
        }

        private void populateLogicalBuffer(int[] source, int renderWidth, int renderHeight, int logicalWidth, int logicalHeight) {
            int sampleOffsetX = scale / 2;
            int sampleOffsetY = scale / 2;
            for (int cellY = 0; cellY < logicalHeight; cellY++) {
                int sampleY = Math.min(renderHeight - 1, (cellY * scale) + sampleOffsetY);
                int rowOffset = cellY * logicalWidth;
                for (int cellX = 0; cellX < logicalWidth; cellX++) {
                    int sampleX = Math.min(renderWidth - 1, (cellX * scale) + sampleOffsetX);
                    logicalBuffer[rowOffset + cellX] = source[(sampleY * renderWidth) + sampleX];
                }
            }
        }
    }

    /**
     * Applies the classic Scale2x rules to a 2x nearest-neighbour-expanded frame.
     */
    public static final class Scale2xPass extends FixedCellScalerPass {
        public Scale2xPass() {
            super(2);
        }

        @Override
        protected void renderCell(int[] logicalBuffer, int logicalWidth, int logicalHeight, int cellX, int cellY,
                int[] target, int renderWidth) {
            int north = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX, cellY - 1);
            int west = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX - 1, cellY);
            int center = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX, cellY);
            int east = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX + 1, cellY);
            int south = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX, cellY + 1);

            int topLeft = center;
            int topRight = center;
            int bottomLeft = center;
            int bottomRight = center;
            if (north != south && west != east) {
                topLeft = west == north ? west : center;
                topRight = north == east ? east : center;
                bottomLeft = west == south ? west : center;
                bottomRight = south == east ? east : center;
            }

            setCellPixel(target, renderWidth, cellX, cellY, 0, 0, topLeft);
            setCellPixel(target, renderWidth, cellX, cellY, 1, 0, topRight);
            setCellPixel(target, renderWidth, cellX, cellY, 0, 1, bottomLeft);
            setCellPixel(target, renderWidth, cellX, cellY, 1, 1, bottomRight);
        }
    }

    /**
     * Applies the classic Scale3x rules to a 3x nearest-neighbour-expanded frame.
     */
    public static final class Scale3xPass extends FixedCellScalerPass {
        public Scale3xPass() {
            super(3);
        }

        @Override
        protected void renderCell(int[] logicalBuffer, int logicalWidth, int logicalHeight, int cellX, int cellY,
                int[] target, int renderWidth) {
            int northWest = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX - 1, cellY - 1);
            int north = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX, cellY - 1);
            int northEast = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX + 1, cellY - 1);
            int west = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX - 1, cellY);
            int center = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX, cellY);
            int east = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX + 1, cellY);
            int southWest = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX - 1, cellY + 1);
            int south = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX, cellY + 1);
            int southEast = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX + 1, cellY + 1);

            int[] cell = new int[] {
                    center, center, center,
                    center, center, center,
                    center, center, center
            };
            if (north != south && west != east) {
                cell[0] = west == north ? west : center;
                cell[1] = (west == north && center != northEast) || (north == east && center != northWest) ? north : center;
                cell[2] = north == east ? east : center;
                cell[3] = (west == north && center != southWest) || (west == south && center != northWest) ? west : center;
                cell[5] = (north == east && center != southEast) || (south == east && center != northEast) ? east : center;
                cell[6] = west == south ? west : center;
                cell[7] = (west == south && center != southEast) || (south == east && center != southWest) ? south : center;
                cell[8] = south == east ? east : center;
            }

            for (int subY = 0; subY < 3; subY++) {
                for (int subX = 0; subX < 3; subX++) {
                    setCellPixel(target, renderWidth, cellX, cellY, subX, subY, cell[(subY * 3) + subX]);
                }
            }
        }
    }

    /**
     * Applies an xBRZ-inspired diagonal reconstruction to a 4x expanded frame.
     */
    public static final class XbrzPass extends FixedCellScalerPass {
        public XbrzPass() {
            super(4);
        }

        @Override
        public boolean prefersAsyncRendering() {
            return true;
        }

        @Override
        protected void renderCell(int[] logicalBuffer, int logicalWidth, int logicalHeight, int cellX, int cellY,
                int[] target, int renderWidth) {
            int northWest = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX - 1, cellY - 1);
            int north = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX, cellY - 1);
            int northEast = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX + 1, cellY - 1);
            int west = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX - 1, cellY);
            int center = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX, cellY);
            int east = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX + 1, cellY);
            int southWest = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX - 1, cellY + 1);
            int south = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX, cellY + 1);
            int southEast = sampleLogical(logicalBuffer, logicalWidth, logicalHeight, cellX + 1, cellY + 1);

            fillCell(target, renderWidth, cellX, cellY, center);

            if (north == west && north != center) {
                blendCorner(target, renderWidth, cellX, cellY, 0, north, northWest == north ? 1.0 : 0.82);
            }
            if (north == east && north != center) {
                blendCorner(target, renderWidth, cellX, cellY, 1, north, northEast == north ? 1.0 : 0.82);
            }
            if (south == west && south != center) {
                blendCorner(target, renderWidth, cellX, cellY, 2, south, southWest == south ? 1.0 : 0.82);
            }
            if (south == east && south != center) {
                blendCorner(target, renderWidth, cellX, cellY, 3, south, southEast == south ? 1.0 : 0.82);
            }
        }

        private void blendCorner(int[] target, int renderWidth, int cellX, int cellY, int corner, int rgb, double strength) {
            for (int subY = 0; subY < scale(); subY++) {
                for (int subX = 0; subX < scale(); subX++) {
                    double mix = cornerBlendMix(subX, subY, corner) * strength;
                    if (mix <= 0.0) {
                        continue;
                    }

                    int x = (cellX * scale()) + subX;
                    int y = (cellY * scale()) + subY;
                    int index = (y * renderWidth) + x;
                    target[index] = mix >= 0.995 ? rgb : blend(target[index], rgb, mix);
                }
            }
        }

        private double cornerBlendMix(int subX, int subY, int corner) {
            double u = (subX + 0.5) / scale();
            double v = (subY + 0.5) / scale();
            double metric = switch (corner) {
                case 0 -> u + v;
                case 1 -> (1.0 - u) + v;
                case 2 -> u + (1.0 - v);
                case 3 -> (1.0 - u) + (1.0 - v);
                default -> 2.0;
            };

            if (metric <= 0.70) {
                return 1.0;
            }
            if (metric <= 1.00) {
                return 0.82;
            }
            if (metric <= 1.28) {
                return 0.55;
            }
            if (metric <= 1.55) {
                return 0.28;
            }
            return 0.0;
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
        public boolean prefersAsyncRendering() {
            return true;
        }

        @Override
        public void apply(int[] source, int[] target, int width, int height) {
            if (glowScale256 <= 0) {
                System.arraycopy(source, 0, target, 0, source.length);
                return;
            }

            int stride = width + 1;
            ensureIntegralCapacity(stride, height + 1);
            if (buildIntegralBuffers(source, width, height, stride) == 0) {
                System.arraycopy(source, 0, target, 0, source.length);
                return;
            }
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

        private int buildIntegralBuffers(int[] source, int width, int height, int stride) {
            Arrays.fill(integralRed, 0, stride, 0);
            Arrays.fill(integralGreen, 0, stride, 0);
            Arrays.fill(integralBlue, 0, stride, 0);
            Arrays.fill(integralCount, 0, stride, 0);

            int brightPixelCount = 0;
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
                        brightPixelCount++;
                    }

                    int integralIndex = rowIntegralOffset + x;
                    int previousRowIndex = integralIndex - stride;
                    integralRed[integralIndex] = integralRed[previousRowIndex] + runningRed;
                    integralGreen[integralIndex] = integralGreen[previousRowIndex] + runningGreen;
                    integralBlue[integralIndex] = integralBlue[previousRowIndex] + runningBlue;
                    integralCount[integralIndex] = integralCount[previousRowIndex] + runningCount;
                }
            }

            return brightPixelCount;
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
