package com.blackaby.Frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.blackaby.Frontend.Shaders.LoadedDisplayShader;
import com.blackaby.Frontend.Shaders.ShaderPresetDocument;
import com.blackaby.Frontend.Shaders.ShaderPresetDocument.ShaderPassConfig;
import com.blackaby.Frontend.Shaders.ShaderPresetDocument.ShaderPassType;
import com.blackaby.Frontend.Shaders.ShaderPreviewRenderer;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

class ShaderPresetDocumentTest {

    @Test
    void fromJsonParsesLegacyRgbShiftDistance() {
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
        assertEquals(3, document.renderScale());
        assertEquals(2, document.passes().size());

        ShaderPassConfig renderScalePass = document.passes().get(0);
        assertEquals(ShaderPassType.RENDER_SCALE, renderScalePass.type());
        assertEquals(3, renderScalePass.intValue("scale"));

        ShaderPassConfig pass = document.passes().get(1);
        assertEquals(ShaderPassType.RGB_SHIFT, pass.type());
        assertEquals(2, pass.intValue("redX"));
        assertEquals(-2, pass.intValue("blueX"));
        assertEquals(0.35, pass.doubleValue("mix"), 0.0001);

        assertEquals(2, document.toJsonObject().getAsJsonArray("passes").size());
        JsonObject passJson = document.toJsonObject().getAsJsonArray("passes").get(1).getAsJsonObject();
        assertEquals(2, passJson.get("redX").getAsInt());
        assertEquals(-2, passJson.get("blueX").getAsInt());
        assertEquals(false, document.toJsonObject().has("renderScale"));
    }

    @Test
    void fromJsonParsesSpriteInterpolationPass() {
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

        ShaderPassConfig pass = document.passes().get(1);
        assertEquals(ShaderPassType.SPRITE_INTERPOLATION, pass.type());
        assertEquals(0.85, pass.doubleValue("strength"), 0.0001);
        assertEquals(3, pass.intValue("cellWidth"));
        assertEquals(3, pass.intValue("cellHeight"));
        assertEquals(2.4, pass.doubleValue("sharpness"), 0.0001);
    }

    @Test
    void fromJsonParsesXbrzPassWithoutParameters() {
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

        ShaderPassConfig pass = document.passes().get(1);
        assertEquals(ShaderPassType.XBRZ, pass.type());
        assertEquals(0, pass.toJsonObject().entrySet().size() - 1);
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
    void explicitRenderScalePassRemainsInChainAndDeterminesOutputScale() {
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

        assertEquals(3, document.passes().size());
        assertEquals(ShaderPassType.COLOR_GRADE, document.passes().get(0).type());
        assertEquals(ShaderPassType.RENDER_SCALE, document.passes().get(1).type());
        assertEquals(ShaderPassType.PIXEL_GRID, document.passes().get(2).type());
        assertEquals(3, document.renderScale());
    }

    @Test
    void renderScalePassAppliesAtItsPositionInTheChain() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {
                  "id": "stepped_scale",
                  "name": "Stepped Scale",
                  "passes": [
                    { "type": "render_scale", "scale": 2 },
                    { "type": "pixel_grid", "intensity": 1.0, "rowSpacing": 2, "columnSpacing": 99 },
                    { "type": "render_scale", "scale": 2 }
                  ]
                }
                """);

        assertEquals(4, document.renderScale());

        int[] source = new int[32];
        source[0] = 0xFFFFFF;
        source[1] = 0xFFFFFF;
        int[] target = new int[source.length];
        int[] scratch = new int[source.length];

        document.toShader().Apply(source, target, scratch, 1, 2);

        for (int y = 0; y < 8; y++) {
            int expectedRgb = ((y / 2) % 2 == 0) ? 0xFFFFFF : 0x000000;
            for (int x = 0; x < 4; x++) {
                assertEquals(expectedRgb, target[(y * 4) + x] & 0xFFFFFF);
            }
        }
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
    void scaledMultiPassShaderPrefersAsyncRendering() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {
                  "id": "async_scaled",
                  "name": "Async Scaled",
                  "passes": [
                    { "type": "render_scale", "scale": 2 },
                    { "type": "pixel_grid", "rowSpacing": 2, "columnSpacing": 2 },
                    { "type": "vignette", "strength": 0.12, "roundness": 1.4 }
                  ]
                }
                """);

        LoadedDisplayShader shader = document.toLoadedShader("JSON preset", null);

        assertTrue(shader.prefersAsyncRendering());
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
        int[] source = new int[2 * 2 * shader.renderScale() * shader.renderScale()];
        source[0] = 0x4A7EB2;
        source[1] = 0x4A7EB2;
        source[2] = 0x4A7EB2;
        source[3] = 0x4A7EB2;
        int[] target = new int[source.length];
        int[] scratch = new int[source.length];
        shader.apply(source, target, scratch, 2, 2);

        assertEquals(2, shader.renderScale());
        assertNotEquals(source[0], target[0]);
    }

    @Test
    void toLoadedShaderCanCreateIsolatedRenderInstances() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {
                  "id": "isolated_runtime",
                  "name": "Isolated Runtime",
                  "passes": [
                    { "type": "dot_matrix", "intensity": 0.30, "cellWidth": 3, "cellHeight": 3, "roundness": 1.4 }
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
