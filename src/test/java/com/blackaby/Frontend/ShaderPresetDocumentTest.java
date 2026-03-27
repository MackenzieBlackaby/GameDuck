package com.blackaby.Frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        assertEquals(1, document.passes().size());

        ShaderPassConfig pass = document.passes().get(0);
        assertEquals(ShaderPassType.RGB_SHIFT, pass.type());
        assertEquals(2, pass.intValue("redX"));
        assertEquals(-2, pass.intValue("blueX"));
        assertEquals(0.35, pass.doubleValue("mix"), 0.0001);

        JsonObject passJson = document.toJsonObject().getAsJsonArray("passes").get(0).getAsJsonObject();
        assertEquals(2, passJson.get("redX").getAsInt());
        assertEquals(-2, passJson.get("blueX").getAsInt());
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

        ShaderPassConfig pass = document.passes().get(0);
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

        ShaderPassConfig pass = document.passes().get(0);
        assertEquals(ShaderPassType.XBRZ, pass.type());
        assertEquals(0, pass.toJsonObject().entrySet().size() - 1);
    }

    @Test
    void createDefaultUsesSuggestedFileNameAndRendersPreview() {
        ShaderPresetDocument document = ShaderPresetDocument.createDefault("custom presets/Warm Glow.json");
        ShaderPreviewRenderer.PreviewImages previewImages = ShaderPreviewRenderer.render(document);

        assertEquals("warm_glow", document.id());
        assertEquals(2, document.renderScale());
        assertEquals(3, document.passes().size());
        assertNotNull(previewImages.sourceImage());
        assertNotNull(previewImages.previewImage());
        assertEquals(320, previewImages.previewImage().getWidth());
        assertEquals(288, previewImages.previewImage().getHeight());
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
        int[] source = new int[] { 0x4A7EB2, 0x4A7EB2, 0x4A7EB2, 0x4A7EB2 };
        int[] target = new int[source.length];
        int[] scratch = new int[source.length];
        shader.apply(source, target, scratch, 2, 2);

        assertEquals(2, shader.renderScale());
        assertNotEquals(source[0], target[0]);
    }
}
