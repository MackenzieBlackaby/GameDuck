package com.blackaby.Frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
}
