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
    void legacyRenderScaleRestoresRgbShift() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {"id":"shift","name":"Shift","renderScale":3,
                 "passes":[{"type":"rgb_shift","mix":0.35}]}
                """);
        assertEquals(3, document.renderScale());
        assertEquals(2, document.passes().size());
        assertEquals(ShaderPassType.RENDER_SCALE, document.passes().get(0).type());
        assertEquals(ShaderPassType.RGB_SHIFT, document.passes().get(1).type());
        assertEquals(3, ShaderPresetDocument.fromJson(document.toPrettyJson()).renderScale());
    }

    @Test
    void restoresSpriteInterpolation() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {"id":"smooth","name":"Smooth","renderScale":3,
                 "passes":[{"type":"sprite_interpolation","cellWidth":3,"cellHeight":3}]}
                """);
        assertEquals(3, document.renderScale());
        assertEquals(ShaderPassType.SPRITE_INTERPOLATION, document.passes().get(1).type());
        assertEquals(480, ShaderPreviewRenderer.render(document).previewImage().getWidth());
    }

    @Test
    void restoresXbrz() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {"id":"xbrz_test","name":"xBRZ","renderScale":4,"passes":[{"type":"xbrz"}]}
                """);
        assertEquals(4, document.renderScale());
        assertEquals(ShaderPassType.XBRZ, document.passes().get(1).type());
        assertEquals(640, ShaderPreviewRenderer.render(document).previewImage().getWidth());
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
    void orderedRenderScaleAndGridArePreserved() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {"id":"chain","name":"Chain","renderScale":3,"passes":[
                 {"type":"color_grade"},{"type":"render_scale","scale":3},
                 {"type":"pixel_grid","rowSpacing":3,"columnSpacing":3}]}
                """);
        assertEquals(3, document.passes().size());
        assertEquals(ShaderPassType.RENDER_SCALE, document.passes().get(1).type());
        assertEquals(3, document.renderScale());
        assertEquals(480, ShaderPreviewRenderer.render(document).previewImage().getWidth());
        assertEquals(3, ShaderPresetDocument.fromJson(document.toPrettyJson()).renderScale());
    }

    @Test
    void advancedPassesRemainInOrderAndScaleIsBounded() {
        ShaderPresetDocument document = ShaderPresetDocument.fromJson("""
                {"id":"matrix","name":"Matrix","passes":[
                 {"type":"render_scale","scale":2},{"type":"render_scale","scale":3},
                 {"type":"dot_matrix","cellWidth":6,"cellHeight":6},
                 {"type":"pixel_outline","cellWidth":6,"cellHeight":6},{"type":"bloom"}]}
                """);
        assertEquals(6, document.renderScale());
        assertEquals(5, document.passes().size());
        assertEquals(ShaderPassType.DOT_MATRIX, document.passes().get(2).type());
        assertEquals(960, ShaderPreviewRenderer.render(document).previewImage().getWidth());
        assertThrows(IllegalArgumentException.class, () -> ShaderPresetDocument.fromJson("""
                {"id":"oversize","name":"Oversize","passes":[
                 {"type":"render_scale","scale":6},{"type":"render_scale","scale":6}]}
                """));
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
