package com.blackaby.Frontend.Shaders;

import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Loads built-in, JSON, and optional plugin-JAR display shaders.
 */
public final class DisplayShaderManager {

    private static final String bundledShaderIndexResource = "display-shaders/index.txt";
    private static final String bundledShaderResourcePrefix = "display-shaders/";
    private static final String builtInSourceLabel = "Built-in JSON";
    private static final String jsonSourceLabel = "JSON preset";
    private static final String pluginSourceLabel = "Plugin JAR";

    private static volatile List<LoadedDisplayShader> shaders = List.of();
    private static volatile List<String> loadErrors = List.of();
    private static volatile boolean loaded;

    private DisplayShaderManager() {
    }

    /**
     * Returns the managed shader directory.
     *
     * @return shader directory path
     */
    public static Path ShaderDirectory() {
        String configuredPath = System.getProperty("gameduck.shader_dir");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of(System.getProperty("user.home"), ".gameduck", "shaders");
    }

    /**
     * Returns the loaded shader list.
     *
     * @return available shaders
     */
    public static List<LoadedDisplayShader> GetAvailableShaders() {
        EnsureLoaded();
        return shaders;
    }

    /**
     * Returns the last load errors.
     *
     * @return load errors
     */
    public static List<String> GetLoadErrors() {
        EnsureLoaded();
        return loadErrors;
    }

    /**
     * Resolves a shader by id, falling back to the bundled "Off" shader.
     *
     * @param shaderId configured shader id
     * @return loaded shader
     */
    public static LoadedDisplayShader Resolve(String shaderId) {
        EnsureLoaded();
        if (shaderId != null && !shaderId.isBlank()) {
            for (LoadedDisplayShader shader : shaders) {
                if (shader.id().equalsIgnoreCase(shaderId)) {
                    return shader;
                }
            }
        }
        for (LoadedDisplayShader shader : shaders) {
            if ("none".equalsIgnoreCase(shader.id())) {
                return shader;
            }
        }
        throw new IllegalStateException("Display shader registry does not contain the required fallback shader.");
    }

    /**
     * Reloads shaders from disk.
     */
    public static synchronized void Reload() {
        Path shaderDirectory = ShaderDirectory();
        List<String> errors = new ArrayList<>();
        Map<String, LoadedDisplayShader> loadedShadersById = new LinkedHashMap<>();

        try {
            Files.createDirectories(shaderDirectory);
            EnsureReadme(shaderDirectory);
        } catch (IOException exception) {
            errors.add("Failed to prepare shader directory: " + exception.getMessage());
        }

        loadBundledShaders(loadedShadersById, errors);

        if (Files.isDirectory(shaderDirectory)) {
            loadJsonShaders(shaderDirectory, loadedShadersById, errors);
            loadPluginShaders(shaderDirectory, loadedShadersById, errors);
        }

        shaders = List.copyOf(loadedShadersById.values());
        loadErrors = List.copyOf(errors);
        loaded = true;
    }

    private static void EnsureLoaded() {
        if (!loaded) {
            Reload();
        }
    }

    private static void loadBundledShaders(Map<String, LoadedDisplayShader> loadedShadersById, List<String> errors) {
        try (InputStream indexStream = DisplayShaderManager.class.getClassLoader().getResourceAsStream(bundledShaderIndexResource)) {
            if (indexStream == null) {
                errors.add("Failed to locate bundled shader index.");
                return;
            }

            String[] resourceNames = new String(indexStream.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
            for (String resourceName : resourceNames) {
                String trimmedName = resourceName.trim();
                if (trimmedName.isEmpty() || trimmedName.startsWith("#")) {
                    continue;
                }

                String resourcePath = bundledShaderResourcePrefix + trimmedName;
                try (InputStream shaderStream = DisplayShaderManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (shaderStream == null) {
                        errors.add("Missing bundled shader resource \"" + trimmedName + "\".");
                        continue;
                    }
                    LoadedDisplayShader shader = loadJsonShader(
                            new InputStreamReader(shaderStream, StandardCharsets.UTF_8),
                            builtInSourceLabel,
                            null);
                    registerShader(loadedShadersById, shader, errors);
                } catch (IOException | JsonParseException | IllegalArgumentException exception) {
                    errors.add(trimmedName + ": " + exception.getMessage());
                }
            }
        } catch (IOException exception) {
            errors.add("Failed to read bundled shader index: " + exception.getMessage());
        }
    }

    private static void loadJsonShaders(Path shaderDirectory, Map<String, LoadedDisplayShader> loadedShadersById,
            List<String> errors) {
        try (var files = Files.walk(shaderDirectory)) {
            files.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            LoadedDisplayShader shader = loadJsonShader(path);
                            registerShader(loadedShadersById, shader, errors);
                        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
                            errors.add(path.getFileName() + ": " + exception.getMessage());
                        }
                    });
        } catch (IOException exception) {
            errors.add("Failed to scan shader JSON files: " + exception.getMessage());
        }
    }

    private static void loadPluginShaders(Path shaderDirectory, Map<String, LoadedDisplayShader> loadedShadersById,
            List<String> errors) {
        List<Path> jarPaths = new ArrayList<>();
        try (var files = Files.walk(shaderDirectory)) {
            files.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .forEach(jarPaths::add);
        } catch (IOException exception) {
            errors.add("Failed to scan shader plugin JARs: " + exception.getMessage());
            return;
        }

        if (jarPaths.isEmpty()) {
            return;
        }

        try {
            URL[] urls = new URL[jarPaths.size()];
            for (int index = 0; index < jarPaths.size(); index++) {
                urls[index] = jarPaths.get(index).toUri().toURL();
            }

            try (URLClassLoader classLoader = new URLClassLoader(urls, DisplayShaderManager.class.getClassLoader())) {
            ServiceLoader<DisplayShader> serviceLoader = ServiceLoader.load(DisplayShader.class, classLoader);
            for (DisplayShader shader : serviceLoader) {
                Path sourcePath = findOwningJar(shader.getClass().getProtectionDomain().getCodeSource() == null
                        ? null
                        : shader.getClass().getProtectionDomain().getCodeSource().getLocation());
                registerShader(loadedShadersById,
                        new LoadedDisplayShader(shader, pluginSourceLabel, sourcePath, shader.RenderScale()),
                        errors);
            }
            }
        } catch (IOException | ServiceConfigurationError exception) {
            errors.add("Failed to load shader plugins: " + exception.getMessage());
        }
    }

    private static Path findOwningJar(URL location) {
        if (location == null) {
            return null;
        }
        try {
            return Path.of(location.toURI());
        } catch (Exception exception) {
            return null;
        }
    }

    private static LoadedDisplayShader loadJsonShader(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return loadJsonShader(reader, jsonSourceLabel, path);
        }
    }

    private static LoadedDisplayShader loadJsonShader(Reader reader, String sourceLabel, Path sourcePath) throws IOException {
        return ShaderPresetDocument.fromReader(reader).toLoadedShader(sourceLabel, sourcePath);
    }

    private static void registerShader(Map<String, LoadedDisplayShader> loadedShadersById, LoadedDisplayShader shader,
            List<String> errors) {
        String id = shader.id();
        if (id == null || id.isBlank()) {
            errors.add("Skipped a shader with a blank id.");
            return;
        }

        String normalizedId = id.toLowerCase(Locale.ROOT);
        if (loadedShadersById.containsKey(normalizedId)) {
            errors.add("Skipped duplicate shader id \"" + id + "\".");
            return;
        }

        loadedShadersById.put(normalizedId, shader);
    }

    private static void EnsureReadme(Path shaderDirectory) throws IOException {
        Path readmePath = shaderDirectory.resolve("README.txt");
        if (Files.exists(readmePath)) {
            return;
        }

        String readme = """
                GameDuck display shaders
                =======================

                Drop custom JSON shader presets or plugin JARs into this folder, then use
                Options > Window > Display Shaders > Reload Shader Folder.

                JSON format:

                {
                  "id": "my_shader",
                  "name": "My Shader",
                  "description": "Optional description shown in the UI",
                  "passes": [
                    { "type": "color_grade", "brightness": 0.02, "contrast": 1.05, "saturation": 0.9, "warmth": 0.08 },
                    { "type": "pixel_grid", "intensity": 0.06, "rowSpacing": 2, "columnSpacing": 3 },
                    { "type": "scanlines", "intensity": 0.08, "spacing": 2 },
                    { "type": "vignette", "strength": 0.12, "roundness": 1.6 }
                  ]
                }

                Supported lightweight pass types:
                - color_grade
                - pixel_grid
                - scanlines
                - vignette

                Plugin JARs can expose implementations of:
                com.blackaby.Frontend.Shaders.DisplayShader

                Register plugin shaders with META-INF/services so ServiceLoader can find them.
                """;
        Files.writeString(readmePath, readme, StandardCharsets.UTF_8);
    }
}
