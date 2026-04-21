package com.blackaby.Frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.blackaby.Frontend.Shaders.LoadedDisplayShader;
import com.blackaby.Frontend.Shaders.ShaderPresetDocument;
import com.blackaby.Frontend.Shaders.ShaderPresetDocument.ShaderPassConfig;
import com.blackaby.Frontend.Shaders.ShaderPresetDocument.ShaderPassType;
import com.blackaby.Frontend.Shaders.ShaderPreviewRenderer;

import org.junit.jupiter.api.Test;

class ShaderPresetDocumentTest {

    @Test
    void fromJsonStripsLegacyRgbShiftDistanceInLightweightMode() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {
                  "id": "legacy_shift",
                  "name": "Legacy Shift",
                  "renderScale": 3,
                  "passes": [
                    { "type": "rgb_shift", "distance": 2, "mix": 0.35 }
                  ]
                }
                """);

        assertEquals("legacy_shift", document.id());
        assertEquals(1, document.renderScale());
        assertEquals(0, document.passes().size());
        assertEquals(0, document.toJsonObject().getAsJsonArray("passes").size());
        assertEquals(false, document.toJsonObject().has("renderScale"));
    }

    @Test
    void fromJsonStripsUnsupportedSpriteInterpolationPass() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {
                  "id": "sharp_pixels",
                  "name": "Sharp Pixels",
                  "renderScale": 3,
                  "passes": [
                    { "type": "sprite_interpolation", "strength": 0.85, "cellWidth": 3, "cellHeight": 3, "sharpness": 2.4 }
                  ]
                }
                """);

        assertEquals(1, document.renderScale());
        assertEquals(0, document.passes().size());
    }

    @Test
    void fromJsonStripsUnsupportedXbrzPassWithoutParameters() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {
                  "id": "smooth_diagonals",
                  "name": "Smooth Diagonals",
                  "renderScale": 4,
                  "passes": [
                    { "type": "xbrz" }
                  ]
                }
                """);

        assertEquals(1, document.renderScale());
        assertEquals(0, document.passes().size());
    }

    @Test
    void createDefaultUsesSuggestedFileNameAndRendersPreview() {
        ShaderPresetDocument document = ShaderPresetDocument.createDefault("custom presets/Warm Glow.json");
        ShaderPreviewRenderer.PreviewImages previewImages = ShaderPreviewRenderer.render(document);

        assertEquals("warm_glow", document.id());
        assertEquals(1, document.renderScale());
        assertEquals(3, document.passes().size());
        assertNotNull(previewImages.sourceImage());
        assertNotNull(previewImages.previewImage());
        assertEquals(160, previewImages.previewImage().getWidth());
        assertEquals(144, previewImages.previewImage().getHeight());
    }

    @Test
    void fileNameFromShaderIdUsesStemAndStripsJsonSuffix() {
        assertEquals("matrix.json", ShaderPresetDocument.fileNameFromShaderId("matrix"));
        assertEquals("matrix.json", ShaderPresetDocument.fileNameFromShaderId(" matrix.json "));
    }

    @Test
    void fileNameFromShaderIdRejectsDirectoryTraversal() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ShaderPresetDocument.fileNameFromShaderId("../matrix"));

        assertEquals("Shader name must stay inside the shader folder.", exception.getMessage());
    }

    @Test
    void unsupportedRenderScalePassIsRemovedFromChain() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {
                  "id": "chain_scale",
                  "name": "Chain Scale",
                  "passes": [
                    { "type": "color_grade", "warmth": 0.20 },
                    { "type": "render_scale", "scale": 3 },
                    { "type": "pixel_grid", "rowSpacing": 3, "columnSpacing": 3 }
                  ]
                }
                """);

        assertEquals(2, document.passes().size());
        assertEquals(ShaderPassType.COLOR_GRADE, document.passes().get(0).type());
        assertEquals(ShaderPassType.PIXEL_GRID, document.passes().get(1).type());
        assertEquals(1, document.renderScale());
    }

    @Test
    void unsupportedPassesAreDroppedWhileSupportedPassOrderStaysIntact() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {
                  "id": "mixed_chain",
                  "name": "Mixed Chain",
                  "passes": [
                    { "type": "render_scale", "scale": 2 },
                    { "type": "color_grade", "warmth": 0.20 },
                    { "type": "bloom", "radius": 1, "strength": 0.10, "threshold": 0.45 },
                    { "type": "pixel_grid", "intensity": 1.0, "rowSpacing": 2, "columnSpacing": 99 },
                    { "type": "vignette", "strength": 0.10, "roundness": 1.50 }
                  ]
                }
                """);

        assertEquals(1, document.renderScale());
        assertEquals(3, document.passes().size());
        assertEquals(ShaderPassType.COLOR_GRADE, document.passes().get(0).type());
        assertEquals(ShaderPassType.PIXEL_GRID, document.passes().get(1).type());
        assertEquals(ShaderPassType.VIGNETTE, document.passes().get(2).type());
    }

    @Test
    void pixelGridSupportsConfigurableLineWidths() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {
                  "id": "wide_grid",
                  "name": "Wide Grid",
                  "passes": [
                    {
                      "type": "pixel_grid",
                      "intensity": 1.0,
                      "rowSpacing": 4,
                      "columnSpacing": 99,
                      "rowLineWidth": 2,
                      "columnLineWidth": 1
                    }
                  ]
                }
                """);

        ShaderPassConfig pass = document.passes().get(0);
        assertEquals(2, pass.intValue("rowLineWidth"));
        assertEquals(1, pass.intValue("columnLineWidth"));

        int[] source = new int[8];
        java.util.Arrays.fill(source, 0xFFFFFF);
        int[] target = new int[source.length];
        int[] scratch = new int[source.length];

        document.toShader().Apply(source, target, scratch, 1, 8);

        int[] expected = new int[] {
                0xFFFFFF,
                0xFFFFFF,
                0x000000,
                0x000000,
                0xFFFFFF,
                0xFFFFFF,
                0x000000,
                0x000000
        };
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], target[index] & 0xFFFFFF);
        }
    }

    @Test
    void lightweightMultiPassShaderStaysOnTheSynchronousPath() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {
                  "id": "inline_lightweight",
                  "name": "Inline Lightweight",
                  "passes": [
                    { "type": "color_grade", "warmth": 0.12 },
                    { "type": "pixel_grid", "rowSpacing": 2, "columnSpacing": 2 },
                    { "type": "vignette", "strength": 0.12, "roundness": 1.4 }
                  ]
                }
                """);

        LoadedDisplayShader shader = document.toLoadedShader("JSON preset", null);

        assertEquals(1, shader.renderScale());
        assertEquals(false, shader.prefersAsyncRendering());
    }

    @Test
    void toLoadedShaderProducesFunctionalRuntimeShader() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {
                  "id": "warm_test",
                  "name": "Warm Test",
                  "description": "Preview document test",
                  "renderScale": 2,
                  "passes": [
                    { "type": "color_grade", "warmth": 0.40, "contrast": 1.10, "saturation": 0.75 }
                  ]
                }
                """);

        LoadedDisplayShader shader = document.toLoadedShader("JSON preset", null);
        int[] source = new int[2 * 2];
        source[0] = 0x4A7EB2;
        source[1] = 0x4A7EB2;
        source[2] = 0x4A7EB2;
        source[3] = 0x4A7EB2;
        int[] target = new int[source.length];
        int[] scratch = new int[source.length];
        shader.apply(source, target, scratch, 2, 2);

        assertEquals(1, shader.renderScale());
        assertNotEquals(source[0], target[0]);
    }

    @Test
    void toLoadedShaderCanCreateIsolatedRenderInstances() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                    {
                      "id": "isolated_runtime",
                      "name": "Isolated Runtime",
                      "passes": [
                    { "type": "pixel_grid", "intensity": 0.30, "rowSpacing": 2, "columnSpacing": 2 }
                  ]
                }
                """);

        LoadedDisplayShader metadataShader = document.toLoadedShader("JSON preset", null);
        LoadedDisplayShader renderShaderA = metadataShader.createRenderInstance();
        LoadedDisplayShader renderShaderB = metadataShader.createRenderInstance();

        assertNotSame(metadataShader.shader(), renderShaderA.shader());
        assertNotSame(renderShaderA.shader(), renderShaderB.shader());
        assertEquals(metadataShader.displayName(), renderShaderA.displayName());
        assertEquals(metadataShader.renderScale(), renderShaderA.renderScale());
    }
}
