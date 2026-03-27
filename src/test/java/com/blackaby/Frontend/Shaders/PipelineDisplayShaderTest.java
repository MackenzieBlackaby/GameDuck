package com.blackaby.Frontend.Shaders;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PipelineDisplayShaderTest {

    @Test
    void bloomPassCopiesSourceWhenNothingCrossesThreshold() {
        PipelineDisplayShader.BloomPass bloomPass = new PipelineDisplayShader.BloomPass(2, 0.16, 0.95);
        int[] source = new int[] {
                0x101010, 0x202020,
                0x303030, 0x404040
        };
        int[] target = new int[source.length];

        bloomPass.apply(source, target, 2, 2);

        assertArrayEquals(source, target);
    }

    @Test
    void bloomPassStillAddsGlowAroundBrightPixels() {
        PipelineDisplayShader.BloomPass bloomPass = new PipelineDisplayShader.BloomPass(1, 0.20, 0.20);
        int[] source = new int[] {
                0x000000, 0x000000, 0x000000,
                0x000000, 0xFFFFFF, 0x000000,
                0x000000, 0x000000, 0x000000
        };
        int[] target = new int[source.length];

        bloomPass.apply(source, target, 3, 3);

        assertNotEquals(source[0], target[0]);
        assertNotEquals(source[1], target[1]);
    }

    @Test
    void spriteInterpolationPassSmoothsEligibleCornersWithoutBlurringWholeCell() {
        PipelineDisplayShader.SpriteInterpolationPass interpolationPass = new PipelineDisplayShader.SpriteInterpolationPass(
                1.0, 4, 4, 2.0);
        int red = 0xCC2211;
        int blue = 0x1144CC;
        int white = 0xF0F0F0;
        int[] source = expandLogical(new int[] {
                white, red, white,
                red, blue, white,
                white, white, white
        }, 3, 3, 4);
        int[] target = new int[source.length];

        interpolationPass.apply(source, target, 12, 12);

        int cornerPixel = target[(4 * 12) + 4];
        int innerPixel = target[(6 * 12) + 6];

        assertEquals(red, cornerPixel);
        assertEquals(blue, innerPixel);
        assertEquals(white, target[(7 * 12) + 7]);
    }

    @Test
    void scale2xPassRebuildsSimpleDiagonalAcrossTheExpandedCell() {
        PipelineDisplayShader.Scale2xPass scale2xPass = new PipelineDisplayShader.Scale2xPass();
        int red = 0xCC2211;
        int blue = 0x1144CC;
        int white = 0xF0F0F0;
        int[] source = expandLogical(new int[] {
                white, red, white,
                red, blue, white,
                white, white, white
        }, 3, 3, 2);
        int[] target = new int[source.length];

        scale2xPass.apply(source, target, 6, 6);

        assertEquals(red, target[(2 * 6) + 2]);
        assertEquals(blue, target[(2 * 6) + 3]);
        assertEquals(blue, target[(3 * 6) + 2]);
        assertEquals(white, target[(3 * 6) + 3]);
    }

    @Test
    void scale3xPassRebuildsSimpleDiagonalAcrossTheExpandedCell() {
        PipelineDisplayShader.Scale3xPass scale3xPass = new PipelineDisplayShader.Scale3xPass();
        int red = 0xCC2211;
        int blue = 0x1144CC;
        int white = 0xF0F0F0;
        int[] source = expandLogical(new int[] {
                white, red, white,
                red, blue, white,
                white, white, white
        }, 3, 3, 3);
        int[] target = new int[source.length];

        scale3xPass.apply(source, target, 9, 9);

        assertEquals(red, target[(3 * 9) + 3]);
        assertEquals(red, target[(3 * 9) + 4]);
        assertEquals(blue, target[(3 * 9) + 5]);
        assertEquals(red, target[(4 * 9) + 3]);
        assertEquals(blue, target[(4 * 9) + 4]);
        assertEquals(white, target[(4 * 9) + 5]);
        assertEquals(blue, target[(5 * 9) + 3]);
        assertEquals(white, target[(5 * 9) + 4]);
        assertEquals(white, target[(5 * 9) + 5]);
    }

    @Test
    void xbrzPassRoundsOpposingDiagonalCornersInsideA4xCell() {
        PipelineDisplayShader.XbrzPass xbrzPass = new PipelineDisplayShader.XbrzPass();
        int red = 0xCC2211;
        int blue = 0x1144CC;
        int white = 0xF0F0F0;
        int[] source = expandLogical(new int[] {
                white, red, white,
                red, blue, white,
                white, white, white
        }, 3, 3, 4);
        int[] target = new int[source.length];

        xbrzPass.apply(source, target, 12, 12);

        assertNotEquals(blue, target[(4 * 12) + 4]);
        assertNotEquals(blue, target[(7 * 12) + 7]);
        assertNotEquals(target[(4 * 12) + 4], target[(7 * 12) + 7]);
        assertNotEquals(blue, target[(5 * 12) + 5]);
    }

    @Test
    void pipelinePrefersAsyncOnlyWhenItContainsExpensivePasses() {
        PipelineDisplayShader fastShader = new PipelineDisplayShader(
                "fast",
                "Fast",
                "Fast pass chain",
                List.of(new PipelineDisplayShader.SpriteInterpolationPass(1.0, 4, 4, 2.0)));
        PipelineDisplayShader slowShader = new PipelineDisplayShader(
                "slow",
                "Slow",
                "Slow pass chain",
                List.of(new PipelineDisplayShader.BloomPass(2, 0.2, 0.4)));
        PipelineDisplayShader xbrzShader = new PipelineDisplayShader(
                "xbrz",
                "xBRZ",
                "xBRZ upscaler",
                List.of(new PipelineDisplayShader.XbrzPass()));

        assertFalse(fastShader.PreferAsyncRendering());
        assertTrue(slowShader.PreferAsyncRendering());
        assertTrue(xbrzShader.PreferAsyncRendering());
    }

    private static int[] expandLogical(int[] logical, int logicalWidth, int logicalHeight, int scale) {
        int renderWidth = logicalWidth * scale;
        int renderHeight = logicalHeight * scale;
        int[] expanded = new int[renderWidth * renderHeight];
        for (int y = 0; y < logicalHeight; y++) {
            int rowOffset = y * logicalWidth;
            int renderRowBase = y * scale * renderWidth;
            for (int x = 0; x < logicalWidth; x++) {
                int rgb = logical[rowOffset + x];
                int renderX = x * scale;
                for (int subY = 0; subY < scale; subY++) {
                    int destinationOffset = renderRowBase + (subY * renderWidth) + renderX;
                    java.util.Arrays.fill(expanded, destinationOffset, destinationOffset + scale, rgb);
                }
            }
        }
        return expanded;
    }
}
