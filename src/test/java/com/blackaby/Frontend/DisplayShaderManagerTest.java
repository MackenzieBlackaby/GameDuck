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
    void builtInPocketMonoShaderIsAvailable() {
        DisplayShaderManager.Reload();

        LoadedDisplayShader shader = DisplayShaderManager.Resolve("pocket_mono");
        int[] source = new int[2 * 2];
        source[0] = 0x7CB060;
        source[1] = 0x7CB060;
        source[2] = 0x7CB060;
        source[3] = 0x7CB060;
        int[] target = new int[source.length];
        int[] scratch = new int[source.length];
        shader.apply(source, target, scratch, 2, 2);

        assertEquals("Pocket Mono", shader.displayName());
        assertEquals(1, shader.renderScale());
        assertNotEquals(source[0], target[0]);
    }

    @Test
    void resolvedJsonShadersCanCreateFreshRenderInstances() {
        DisplayShaderManager.Reload();

        LoadedDisplayShader resolvedShader = DisplayShaderManager.Resolve("pocket_mono");
        LoadedDisplayShader renderShaderA = resolvedShader.createRenderInstance();
        LoadedDisplayShader renderShaderB = resolvedShader.createRenderInstance();

        assertNotSame(resolvedShader.shader(), renderShaderA.shader());
        assertNotSame(renderShaderA.shader(), renderShaderB.shader());
        assertEquals(resolvedShader.displayName(), renderShaderA.displayName());
    }

    @Test
    void authenticGbcLcdIsBundledAndRendersAtSixTimesScale() {
        DisplayShaderManager.Reload();
        LoadedDisplayShader shader = DisplayShaderManager.Resolve("gbc_authentic_lcd").createRenderInstance();

        assertEquals("Game Boy Color - Authentic LCD", shader.displayName());
        assertEquals("Built-in JSON", shader.sourceLabel());
        assertEquals(6, shader.renderScale());
        assertEquals(1, shader.sourceRenderScale());
        assertTrue(shader.prefersAsyncRendering());

        int[] source = new int[36];
        int[] target = new int[36];
        int[] scratch = new int[36];
        source[0] = 0xB0C080;
        shader.apply(source, target, scratch, 1, 1);
        assertNotEquals(target[3 * 6 + 3], target[5 * 6 + 5]);
    }

    @Test
    void removedHeavyBundledShadersFallBackToOff() {
        DisplayShaderManager.Reload();

        LoadedDisplayShader fallback = DisplayShaderManager.Resolve("xbrz");

        assertEquals("Off", fallback.displayName());
        assertEquals(1, fallback.renderScale());
    }
}
