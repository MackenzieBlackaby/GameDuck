package com.blackaby.Frontend.Shaders;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mutable document model for JSON-defined display shaders.
 */
public final class ShaderPresetDocument {

    private String id;
    private String name;
    private String description;
    private final List<ShaderPassConfig> passes = new ArrayList<>();

    public ShaderPresetDocument(String id, String name, String description, int renderScale,
            List<ShaderPassConfig> passes) {
        this.id = requireNonBlank(id, "id");
        this.name = requireNonBlank(name, "name");
        this.description = description == null ? "" : description.trim();
        if (passes != null) {
            this.passes.addAll(passes);
        }
        if (renderScale > 1 && findLastRenderScalePassIndex() < 0) {
            ShaderPassConfig renderScalePass = ShaderPassType.RENDER_SCALE.createDefaultPass();
            renderScalePass.setValue("scale", clampRenderScale(renderScale));
            this.passes.add(0, renderScalePass);
        }
    }

    public static ShaderPresetDocument createDefault(String suggestedFileName) {
        return new ShaderPresetDocument(
                suggestedIdFromFileName(suggestedFileName),
                "Custom Shader",
                "Describe the look here",
                1,
                List.of(
                        ShaderPassType.COLOR_GRADE.createDefaultPass(),
                        ShaderPassType.PIXEL_GRID.createDefaultPass(),
                        ShaderPassType.VIGNETTE.createDefaultPass()));
    }

    public static ShaderPresetDocument fromJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            throw new IllegalArgumentException("Shader preset JSON cannot be blank.");
        }
        JsonElement rootElement = JsonParser.parseString(jsonText);
        if (!rootElement.isJsonObject()) {
            throw new IllegalArgumentException("Shader preset JSON must be an object.");
        }
        return fromJsonObject(rootElement.getAsJsonObject());
    }

    public static ShaderPresetDocument fromReader(Reader reader) {
        JsonElement rootElement = JsonParser.parseReader(reader);
        if (!rootElement.isJsonObject()) {
            throw new IllegalArgumentException("Shader preset JSON must be an object.");
        }
        return fromJsonObject(rootElement.getAsJsonObject());
    }

    public static ShaderPresetDocument fromJsonObject(JsonObject root) {
        if (root == null) {
            throw new IllegalArgumentException("Shader preset JSON must be an object.");
        }

        String id = requiredString(root, "id");
        String name = requiredString(root, "name");
        String description = optionalString(root, "description", "");
        int renderScale = clampRenderScale(optionalInt(root, "renderScale", 1));
        List<ShaderPassConfig> passes = new ArrayList<>();
        JsonArray passArray = root.getAsJsonArray("passes");
        if (passArray != null) {
            for (JsonElement passElement : passArray) {
                if (!passElement.isJsonObject()) {
                    throw new IllegalArgumentException("Each shader pass must be a JSON object.");
                }
                passes.add(ShaderPassType.fromId(requiredString(passElement.getAsJsonObject(), "type"))
                        .fromJson(passElement.getAsJsonObject()));
            }
        }

        return new ShaderPresetDocument(id, name, description, renderScale, passes);
    }

    public JsonObject toJsonObject() {
        JsonObject root = new JsonObject();
        root.addProperty("id", requireNonBlank(id, "id"));
        root.addProperty("name", requireNonBlank(name, "name"));
        root.addProperty("description", description == null ? "" : description);

        JsonArray passArray = new JsonArray();
        for (ShaderPassConfig pass : passes) {
            passArray.add(pass.toJsonObject());
        }
        root.add("passes", passArray);
        return root;
    }

    public String toPrettyJson() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(toJsonObject());
    }

    public PipelineDisplayShader toShader() {
        List<PipelineDisplayShader.ShaderPass> runtimePasses = new ArrayList<>(passes.size());
        for (ShaderPassConfig pass : passes) {
            runtimePasses.add(pass.type().toRuntimePass(pass));
        }
        return new PipelineDisplayShader(id, name, description, runtimePasses);
    }

    public LoadedDisplayShader toLoadedShader(String sourceLabel, Path sourcePath) {
        JsonObject snapshot = toJsonObject();
        return new LoadedDisplayShader(
                toShader(),
                sourceLabel,
                sourcePath,
                renderScale(),
                () -> ShaderPresetDocument.fromJsonObject(snapshot.deepCopy()).toShader());
    }

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = requireNonBlank(id, "id");
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = requireNonBlank(name, "name");
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description.trim();
    }

    public int renderScale() {
        int renderScale = 1;
        for (ShaderPassConfig pass : passes) {
            if (pass != null && pass.type() == ShaderPassType.RENDER_SCALE) {
                renderScale *= clampRenderScale(pass.intValue("scale"));
            }
        }
        return renderScale;
    }

    public void setRenderScale(int renderScale) {
        int clampedRenderScale = clampRenderScale(renderScale);
        int existingIndex = findLastRenderScalePassIndex();
        if (existingIndex >= 0) {
            passes.get(existingIndex).setValue("scale", clampedRenderScale);
            return;
        }

        ShaderPassConfig renderScalePass = ShaderPassType.RENDER_SCALE.createDefaultPass();
        renderScalePass.setValue("scale", clampedRenderScale);
        passes.add(renderScalePass);
    }

    public List<ShaderPassConfig> passes() {
        return passes;
    }

    private int findLastRenderScalePassIndex() {
        for (int index = passes.size() - 1; index >= 0; index--) {
            ShaderPassConfig pass = passes.get(index);
            if (pass != null && pass.type() == ShaderPassType.RENDER_SCALE) {
                return index;
            }
        }
        return -1;
    }

    public static String suggestedIdFromFileName(String suggestedFileName) {
        String presetId = suggestedFileName == null ? "" : suggestedFileName.replace('\\', '/');
        int slashIndex = presetId.lastIndexOf('/');
        if (slashIndex >= 0) {
            presetId = presetId.substring(slashIndex + 1);
        }
        if (presetId.toLowerCase(Locale.ROOT).endsWith(".json")) {
            presetId = presetId.substring(0, presetId.length() - 5);
        }
        presetId = presetId.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return presetId.isBlank() ? "custom_shader" : presetId;
    }

    public static String fileStemFromShaderId(String shaderId) {
        String trimmedId = shaderId == null ? "" : shaderId.trim();
        if (trimmedId.toLowerCase(Locale.ROOT).endsWith(".json")) {
            trimmedId = trimmedId.substring(0, trimmedId.length() - 5).trim();
        }
        if (trimmedId.isBlank()) {
            throw new IllegalArgumentException("Shader name cannot be blank.");
        }
        if (".".equals(trimmedId) || "..".equals(trimmedId)
                || trimmedId.contains("/") || trimmedId.contains("\\")) {
            throw new IllegalArgumentException("Shader name must stay inside the shader folder.");
        }
        for (int index = 0; index < trimmedId.length(); index++) {
            char character = trimmedId.charAt(index);
            if (character < 32 || "<>:\"|?*".indexOf(character) >= 0) {
                throw new IllegalArgumentException(
                        "Shader name contains characters that cannot be used in a preset file.");
            }
        }
        return trimmedId;
    }

    public static String fileNameFromShaderId(String shaderId) {
        return fileStemFromShaderId(shaderId) + ".json";
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Shader preset JSON is missing a valid \"" + fieldName + "\" field.");
        }
        return value.trim();
    }

    private static int clampRenderScale(int renderScale) {
        return Math.max(1, Math.min(6, renderScale));
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

    public enum ParameterValueType {
        INTEGER,
        DECIMAL
    }

    public record ShaderParameterDefinition(
            String key,
            String label,
            ParameterValueType valueType,
            double minimum,
            double maximum,
            double step,
            double defaultValue) {
    }

    public enum ShaderPassType {
        RENDER_SCALE(
                "render_scale",
                "Render Scale",
                "Multiply the current render scale by this amount.",
                List.of(
                        new ShaderParameterDefinition("scale", "Scale", ParameterValueType.INTEGER, 1.00, 6.00,
                                1.00, 2.00))) {
            @Override
            PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass) {
                return new PipelineDisplayShader.RenderScalePass(pass.intValue("scale"));
            }
        },
        COLOR_GRADE(
                "color_grade",
                "Color Grade",
                "Adjust overall tone, contrast, and warmth.",
                List.of(
                        new ShaderParameterDefinition("brightness", "Brightness", ParameterValueType.DECIMAL, -0.50,
                                0.50, 0.01, 0.02),
                        new ShaderParameterDefinition("contrast", "Contrast", ParameterValueType.DECIMAL, 0.20, 2.00,
                                0.01, 1.05),
                        new ShaderParameterDefinition("saturation", "Saturation", ParameterValueType.DECIMAL, 0.00,
                                2.00, 0.01, 0.90),
                        new ShaderParameterDefinition("warmth", "Warmth", ParameterValueType.DECIMAL, -1.00, 1.00,
                                0.01, 0.08))) {
            @Override
            PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass) {
                return new PipelineDisplayShader.ColorGradePass(
                        pass.doubleValue("brightness"),
                        pass.doubleValue("contrast"),
                        pass.doubleValue("saturation"),
                        pass.doubleValue("warmth"));
            }
        },
        SPRITE_INTERPOLATION(
                "sprite_interpolation",
                "Sprite Interpolation",
                "Smooth enlarged sprite corners with edge-aware interpolation while keeping pixels sharp.",
                List.of(
                        new ShaderParameterDefinition("strength", "Strength", ParameterValueType.DECIMAL, 0.00,
                                1.00, 0.01, 1.00),
                        new ShaderParameterDefinition("cellWidth", "Cell Width", ParameterValueType.INTEGER, 1.00,
                                8.00, 1.00, 2.00),
                        new ShaderParameterDefinition("cellHeight", "Cell Height", ParameterValueType.INTEGER, 1.00,
                                8.00, 1.00, 2.00),
                        new ShaderParameterDefinition("sharpness", "Sharpness", ParameterValueType.DECIMAL, 1.00,
                                6.00, 0.01, 2.00))) {
            @Override
            PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass) {
                return new PipelineDisplayShader.SpriteInterpolationPass(
                        pass.doubleValue("strength"),
                        pass.intValue("cellWidth"),
                        pass.intValue("cellHeight"),
                        pass.doubleValue("sharpness"));
            }
        },
        SCALE2X(
                "scale2x",
                "Scale2x",
                "Apply the classic 2x edge-aware pixel-art scaler.",
                List.of()) {
            @Override
            PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass) {
                return new PipelineDisplayShader.Scale2xPass();
            }
        },
        SCALE3X(
                "scale3x",
                "Scale3x",
                "Apply the classic 3x edge-aware pixel-art scaler.",
                List.of()) {
            @Override
            PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass) {
                return new PipelineDisplayShader.Scale3xPass();
            }
        },
        XBRZ(
                "xbrz",
                "xBRZ",
                "Reconstruct diagonals with an xBRZ-inspired 4x smoother.",
                List.of()) {
            @Override
            PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass) {
                return new PipelineDisplayShader.XbrzPass();
            }
        },
        PIXEL_GRID(
                "pixel_grid",
                "Pixel Grid",
                "Draw subtle row and column gaps between repeated pixels.",
                List.of(
                        new ShaderParameterDefinition("intensity", "Intensity", ParameterValueType.DECIMAL, 0.00,
                                1.00, 0.01, 0.06),
                        new ShaderParameterDefinition("rowSpacing", "Row Spacing", ParameterValueType.INTEGER, 1.00,
                                8.00, 1.00, 2.00),
                        new ShaderParameterDefinition("columnSpacing", "Column Spacing", ParameterValueType.INTEGER,
                                1.00, 8.00, 1.00, 2.00),
                        new ShaderParameterDefinition("rowLineWidth", "Row Line Width", ParameterValueType.INTEGER,
                                1.00, 8.00, 1.00, 1.00),
                        new ShaderParameterDefinition("columnLineWidth", "Column Line Width", ParameterValueType.INTEGER,
                                1.00, 8.00, 1.00, 1.00))) {
            @Override
            PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass) {
                return new PipelineDisplayShader.PixelGridPass(
                        pass.doubleValue("intensity"),
                        pass.intValue("rowSpacing"),
                        pass.intValue("columnSpacing"),
                        pass.intValue("rowLineWidth"),
                        pass.intValue("columnLineWidth"));
            }
        },
        PIXEL_OUTLINE(
                "pixel_outline",
                "Pixel Outline",
                "Darken the border around each upscaled pixel cell.",
                List.of(
                        new ShaderParameterDefinition("intensity", "Intensity", ParameterValueType.DECIMAL, 0.00,
                                1.00, 0.01, 0.35),
                        new ShaderParameterDefinition("cellWidth", "Cell Width", ParameterValueType.INTEGER, 1.00,
                                8.00, 1.00, 2.00),
                        new ShaderParameterDefinition("cellHeight", "Cell Height", ParameterValueType.INTEGER, 1.00,
                                8.00, 1.00, 2.00),
                        new ShaderParameterDefinition("edgeWidth", "Edge Width", ParameterValueType.INTEGER, 1.00,
                                4.00, 1.00, 1.00))) {
            @Override
            PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass) {
                return new PipelineDisplayShader.PixelOutlinePass(
                        pass.doubleValue("intensity"),
                        pass.intValue("cellWidth"),
                        pass.intValue("cellHeight"),
                        pass.intValue("edgeWidth"));
            }
        },
        DOT_MATRIX(
                "dot_matrix",
                "Dot Matrix",
                "Round each pixel into soft matrix-style phosphor dots.",
                List.of(
                        new ShaderParameterDefinition("intensity", "Intensity", ParameterValueType.DECIMAL, 0.00,
                                1.00, 0.01, 0.30),
                        new ShaderParameterDefinition("cellWidth", "Cell Width", ParameterValueType.INTEGER, 1.00,
                                8.00, 1.00, 2.00),
                        new ShaderParameterDefinition("cellHeight", "Cell Height", ParameterValueType.INTEGER, 1.00,
                                8.00, 1.00, 2.00),
                        new ShaderParameterDefinition("roundness", "Roundness", ParameterValueType.DECIMAL, 0.60,
                                3.00, 0.01, 1.40))) {
            @Override
            PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass) {
                return new PipelineDisplayShader.DotMatrixPass(
                        pass.doubleValue("intensity"),
                        pass.intValue("cellWidth"),
                        pass.intValue("cellHeight"),
                        pass.doubleValue("roundness"));
            }
        },
        SCANLINES(
                "scanlines",
                "Scanlines",
                "Darken every Nth row to simulate scanlines.",
                List.of(
                        new ShaderParameterDefinition("intensity", "Intensity", ParameterValueType.DECIMAL, 0.00,
                                1.00, 0.01, 0.08),
                        new ShaderParameterDefinition("spacing", "Spacing", ParameterValueType.INTEGER, 1.00, 8.00,
                                1.00, 2.00),
                        new ShaderParameterDefinition("offset", "Offset", ParameterValueType.INTEGER, -8.00, 8.00,
                                1.00, 0.00))) {
            @Override
            PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass) {
                return new PipelineDisplayShader.ScanlinesPass(
                        pass.doubleValue("intensity"),
                        pass.intValue("spacing"),
                        pass.intValue("offset"));
            }
        },
        BLOOM(
                "bloom",
                "Bloom",
                "Glow bright areas by sampling nearby highlights.",
                List.of(
                        new ShaderParameterDefinition("radius", "Radius", ParameterValueType.INTEGER, 1.00, 4.00,
                                1.00, 1.00),
                        new ShaderParameterDefinition("strength", "Strength", ParameterValueType.DECIMAL, 0.00,
                                1.00, 0.01, 0.10),
                        new ShaderParameterDefinition("threshold", "Threshold", ParameterValueType.DECIMAL, 0.00,
                                1.00, 0.01, 0.45))) {
            @Override
            PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass) {
                return new PipelineDisplayShader.BloomPass(
                        pass.intValue("radius"),
                        pass.doubleValue("strength"),
                        pass.doubleValue("threshold"));
            }
        },
        VIGNETTE(
                "vignette",
                "Vignette",
                "Darken edges to pull focus toward the center.",
                List.of(
                        new ShaderParameterDefinition("strength", "Strength", ParameterValueType.DECIMAL, 0.00,
                                1.00, 0.01, 0.10),
                        new ShaderParameterDefinition("roundness", "Roundness", ParameterValueType.DECIMAL, 0.60,
                                3.00, 0.01, 1.50))) {
            @Override
            PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass) {
                return new PipelineDisplayShader.VignettePass(
                        pass.doubleValue("strength"),
                        pass.doubleValue("roundness"));
            }
        },
        RGB_SHIFT(
                "rgb_shift",
                "RGB Shift",
                "Offset red and blue channels for a subtle chromatic split.",
                List.of(
                        new ShaderParameterDefinition("redX", "Red X", ParameterValueType.INTEGER, -6.00, 6.00,
                                1.00, 1.00),
                        new ShaderParameterDefinition("redY", "Red Y", ParameterValueType.INTEGER, -6.00, 6.00,
                                1.00, 0.00),
                        new ShaderParameterDefinition("blueX", "Blue X", ParameterValueType.INTEGER, -6.00, 6.00,
                                1.00, -1.00),
                        new ShaderParameterDefinition("blueY", "Blue Y", ParameterValueType.INTEGER, -6.00, 6.00,
                                1.00, 0.00),
                        new ShaderParameterDefinition("mix", "Mix", ParameterValueType.DECIMAL, 0.00, 1.00, 0.01,
                                0.15))) {
            @Override
            ShaderPassConfig fromJson(JsonObject passObject) {
                ShaderPassConfig pass = createDefaultPass();
                int legacyDistance = optionalInt(passObject, "distance", 1);
                pass.setValue("redX", optionalInt(passObject, "redX", legacyDistance));
                pass.setValue("redY", optionalInt(passObject, "redY", 0));
                pass.setValue("blueX", optionalInt(passObject, "blueX", -legacyDistance));
                pass.setValue("blueY", optionalInt(passObject, "blueY", 0));
                pass.setValue("mix", optionalDouble(passObject, "mix", 0.15));
                return pass;
            }

            @Override
            PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass) {
                return new PipelineDisplayShader.RgbShiftPass(
                        pass.intValue("redX"),
                        pass.intValue("redY"),
                        pass.intValue("blueX"),
                        pass.intValue("blueY"),
                        pass.doubleValue("mix"));
            }
        };

        private final String id;
        private final String label;
        private final String description;
        private final List<ShaderParameterDefinition> parameters;

        ShaderPassType(String id, String label, String description, List<ShaderParameterDefinition> parameters) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.parameters = List.copyOf(parameters);
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }

        public List<ShaderParameterDefinition> parameters() {
            return parameters;
        }

        public ShaderPassConfig createDefaultPass() {
            return new ShaderPassConfig(this);
        }

        ShaderPassConfig fromJson(JsonObject passObject) {
            ShaderPassConfig pass = createDefaultPass();
            for (ShaderParameterDefinition parameter : parameters) {
                if (parameter.valueType() == ParameterValueType.INTEGER) {
                    pass.setValue(parameter.key(), optionalInt(passObject, parameter.key(),
                            (int) Math.round(parameter.defaultValue())));
                } else {
                    pass.setValue(parameter.key(), optionalDouble(passObject, parameter.key(), parameter.defaultValue()));
                }
            }
            return pass;
        }

        abstract PipelineDisplayShader.ShaderPass toRuntimePass(ShaderPassConfig pass);

        public static ShaderPassType fromId(String typeId) {
            if (typeId == null || typeId.isBlank()) {
                throw new IllegalArgumentException("Unsupported shader pass type: " + typeId);
            }
            String normalized = typeId.trim().toLowerCase(Locale.ROOT);
            for (ShaderPassType value : values()) {
                if (value.id.equals(normalized)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unsupported shader pass type: " + typeId);
        }
    }

    public static final class ShaderPassConfig {
        private final ShaderPassType type;
        private final Map<String, Number> values = new LinkedHashMap<>();

        public ShaderPassConfig(ShaderPassType type) {
            this.type = type;
            for (ShaderParameterDefinition parameter : type.parameters()) {
                if (parameter.valueType() == ParameterValueType.INTEGER) {
                    values.put(parameter.key(), (int) Math.round(parameter.defaultValue()));
                } else {
                    values.put(parameter.key(), parameter.defaultValue());
                }
            }
        }

        public ShaderPassType type() {
            return type;
        }

        public Number value(String key) {
            Number value = values.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Unknown shader parameter: " + key);
            }
            return value;
        }

        public boolean hasParameter(String key) {
            return values.containsKey(key);
        }

        public double doubleValue(String key) {
            return value(key).doubleValue();
        }

        public int intValue(String key) {
            return value(key).intValue();
        }

        public void setValue(String key, Number rawValue) {
            ShaderParameterDefinition parameter = parameter(key);
            if (parameter.valueType() == ParameterValueType.INTEGER) {
                int clamped = (int) Math.round(clamp(rawValue == null ? parameter.defaultValue() : rawValue.doubleValue(),
                        parameter.minimum(), parameter.maximum()));
                values.put(key, clamped);
            } else {
                double clamped = clamp(rawValue == null ? parameter.defaultValue() : rawValue.doubleValue(),
                        parameter.minimum(), parameter.maximum());
                values.put(key, clamped);
            }
        }

        public JsonObject toJsonObject() {
            JsonObject passObject = new JsonObject();
            passObject.addProperty("type", type.id());
            for (ShaderParameterDefinition parameter : type.parameters()) {
                Number value = values.get(parameter.key());
                if (parameter.valueType() == ParameterValueType.INTEGER) {
                    passObject.addProperty(parameter.key(), value == null ? (int) Math.round(parameter.defaultValue())
                            : value.intValue());
                } else {
                    passObject.addProperty(parameter.key(), value == null ? parameter.defaultValue() : value.doubleValue());
                }
            }
            return passObject;
        }

        private ShaderParameterDefinition parameter(String key) {
            for (ShaderParameterDefinition parameter : type.parameters()) {
                if (parameter.key().equals(key)) {
                    return parameter;
                }
            }
            throw new IllegalArgumentException("Unknown shader parameter: " + key);
        }

        private static double clamp(double value, double minimum, double maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }

        @Override
        public String toString() {
            return type.label();
        }
    }
}
