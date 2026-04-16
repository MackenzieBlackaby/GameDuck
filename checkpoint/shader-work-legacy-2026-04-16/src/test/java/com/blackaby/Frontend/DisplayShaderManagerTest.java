package com.blackaby.Frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.blackaby.Frontend.Shaders.DisplayShaderManager;
import com.blackaby.Frontend.Shaders.LoadedDisplayShader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class DisplayShaderManagerTest {

    @TempDir
    Path tempHomeDirectory;

    @Test
    void reloadLoadsJsonShaderFromManagedShaderDirectory() throws Exception {
        String originalHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", tempHomeDirectory.toString());
            Path shaderDirectory = DisplayShaderManager.ShaderDirectory();
            Files.createDirectories(shaderDirectory);
            Files.writeString(shaderDirectory.resolve("custom-warm.json"), """
                    {
                      "id": "custom_warm",
                      "name": "Custom Warm",
                      "description": "Warms the display slightly",
                      "passes": [
                        { "type": "color_grade", "warmth": 0.35, "contrast": 1.08, "saturation": 0.85 }
                      ]
                    }
                    """);

            DisplayShaderManager.Reload();

            LoadedDisplayShader shader = DisplayShaderManager.Resolve("custom_warm");
            int[] source = new int[] { 0x4A7EB2 };
            int[] target = new int[1];
            int[] scratch = new int[1];
            shader.apply(source, target, scratch, 1, 1);

            assertEquals("Custom Warm", shader.displayName());
            assertEquals("JSON preset", shader.sourceLabel());
            assertNotEquals(source[0], target[0]);
            assertTrue(Files.exists(shaderDirectory.resolve("README.txt")));
            assertFalse(DisplayShaderManager.GetAvailableShaders().isEmpty());
            assertTrue(DisplayShaderManager.GetLoadErrors().isEmpty());
        } finally {
            System.setProperty("user.home", originalHome);
            DisplayShaderManager.Reload();
        }
    }

    @Test
    void builtInDotMatrixShaderIsAvailable() {
        DisplayShaderManager.Reload();

        LoadedDisplayShader shader = DisplayShaderManager.Resolve("dot_matrix");
        int[] source = new int[2 * 2 * shader.renderScale() * shader.renderScale()];
        source[0] = 0x7CB060;
        source[1] = 0x7CB060;
        source[2] = 0x7CB060;
        source[3] = 0x7CB060;
        int[] target = new int[source.length];
        int[] scratch = new int[source.length];
        shader.apply(source, target, scratch, 2, 2);

        assertEquals("Dot Matrix", shader.displayName());
        assertEquals(4, shader.renderScale());
        assertNotEquals(source[0], target[0]);
    }

    @Test
    void resolvedJsonShadersCanCreateFreshRenderInstances() {
        DisplayShaderManager.Reload();

        LoadedDisplayShader resolvedShader = DisplayShaderManager.Resolve("dot_matrix");
        LoadedDisplayShader renderShaderA = resolvedShader.createRenderInstance();
        LoadedDisplayShader renderShaderB = resolvedShader.createRenderInstance();

        assertNotSame(resolvedShader.shader(), renderShaderA.shader());
        assertNotSame(renderShaderA.shader(), renderShaderB.shader());
        assertEquals(resolvedShader.displayName(), renderShaderA.displayName());
    }

    @Test
    void builtInPixelArtUpscalerShadersAreAvailable() {
        DisplayShaderManager.Reload();

        LoadedDisplayShader scale2x = DisplayShaderManager.Resolve("scale2x");
        LoadedDisplayShader scale3x = DisplayShaderManager.Resolve("scale3x");
        LoadedDisplayShader xbrz = DisplayShaderManager.Resolve("xbrz");

        assertEquals("Scale2x", scale2x.displayName());
        assertEquals(2, scale2x.renderScale());
        assertEquals("Scale3x", scale3x.displayName());
        assertEquals(3, scale3x.renderScale());
        assertEquals("xBRZ", xbrz.displayName());
        assertEquals(4, xbrz.renderScale());
    }
}
