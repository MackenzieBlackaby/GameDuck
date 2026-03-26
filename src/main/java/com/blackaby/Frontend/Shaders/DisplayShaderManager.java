package com.blackaby.Frontend.Shaders;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
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

    private static final String builtInSourceLabel = "Built-in";
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

        for (DisplayShader shader : PipelineDisplayShader.BuiltIns()) {
            registerShader(loadedShadersById, new LoadedDisplayShader(shader, builtInSourceLabel, null), errors);
        }

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
                registerShader(loadedShadersById, new LoadedDisplayShader(shader, pluginSourceLabel, sourcePath), errors);
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
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String id = requiredString(root, "id");
            String name = requiredString(root, "name");
            String description = optionalString(root, "description", "");
            JsonArray passArray = root.getAsJsonArray("passes");
            if (passArray == null || passArray.isEmpty()) {
                throw new IllegalArgumentException("Shader JSON must contain at least one pass.");
            }

            List<PipelineDisplayShader.ShaderPass> passes = new ArrayList<>();
            for (JsonElement passElement : passArray) {
                if (!passElement.isJsonObject()) {
                    throw new IllegalArgumentException("Each shader pass must be a JSON object.");
                }
                passes.add(parsePass(passElement.getAsJsonObject()));
            }

            return new LoadedDisplayShader(
                    new PipelineDisplayShader(id, name, description, passes),
                    jsonSourceLabel,
                    path);
        }
    }

    private static PipelineDisplayShader.ShaderPass parsePass(JsonObject passObject) {
        String type = requiredString(passObject, "type").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "scanlines" -> new PipelineDisplayShader.ScanlinesPass(
                    optionalDouble(passObject, "intensity", 0.08),
                    optionalInt(passObject, "spacing", 2),
                    optionalInt(passObject, "offset", 0));
            case "pixel_grid" -> new PipelineDisplayShader.PixelGridPass(
                    optionalDouble(passObject, "intensity", 0.06),
                    optionalInt(passObject, "rowSpacing", 2),
                    optionalInt(passObject, "columnSpacing", 3));
            case "vignette" -> new PipelineDisplayShader.VignettePass(
                    optionalDouble(passObject, "strength", 0.12),
                    optionalDouble(passObject, "roundness", 1.6));
            case "bloom" -> new PipelineDisplayShader.BloomPass(
                    optionalInt(passObject, "radius", 1),
                    optionalDouble(passObject, "strength", 0.10),
                    optionalDouble(passObject, "threshold", 0.45));
            case "rgb_shift" -> new PipelineDisplayShader.RgbShiftPass(
                    optionalInt(passObject, "redX", optionalInt(passObject, "distance", 1)),
                    optionalInt(passObject, "redY", 0),
                    optionalInt(passObject, "blueX", -optionalInt(passObject, "distance", 1)),
                    optionalInt(passObject, "blueY", 0),
                    optionalDouble(passObject, "mix", 0.15));
            case "color_grade" -> new PipelineDisplayShader.ColorGradePass(
                    optionalDouble(passObject, "brightness", 0.0),
                    optionalDouble(passObject, "contrast", 1.0),
                    optionalDouble(passObject, "saturation", 1.0),
                    optionalDouble(passObject, "warmth", 0.0));
            default -> throw new IllegalArgumentException("Unsupported shader pass type: " + type);
        };
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

    private static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new IllegalArgumentException("Missing required \"" + key + "\" field.");
        }
        String value = object.get(key).getAsString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Field \"" + key + "\" cannot be blank.");
        }
        return value;
    }

    private static String optionalString(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        String value = object.get(key).getAsString();
        return value == null ? fallback : value.trim();
    }

    private static int optionalInt(JsonObject object, String key, int fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsInt();
    }

    private static double optionalDouble(JsonObject object, String key, double fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsDouble();
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
                    { "type": "bloom", "radius": 1, "strength": 0.10, "threshold": 0.45 },
                    { "type": "vignette", "strength": 0.12, "roundness": 1.6 },
                    { "type": "rgb_shift", "distance": 1, "mix": 0.15 }
                  ]
                }

                Supported pass types:
                - color_grade
                - pixel_grid
                - scanlines
                - bloom
                - vignette
                - rgb_shift

                Plugin JARs can expose implementations of:
                com.blackaby.Frontend.Shaders.DisplayShader

                Register plugin shaders with META-INF/services so ServiceLoader can find them.
                """;
        Files.writeString(readmePath, readme, StandardCharsets.UTF_8);
    }
}
