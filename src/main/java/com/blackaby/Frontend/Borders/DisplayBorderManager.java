package com.blackaby.Frontend.Borders;

import javax.imageio.ImageIO;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads built-in and custom PNG display borders.
 */
public final class DisplayBorderManager {

    private static final String bundledBorderIndexResource = "display-borders/index.txt";
    private static final String bundledBorderResourcePrefix = "display-borders/";
    private static final String builtInSourceLabel = "Built-in PNG";
    private static final String customSourceLabel = "Custom PNG";
    private static final int transparentThreshold = 24;

    private static volatile List<LoadedDisplayBorder> borders = List.of();
    private static volatile List<String> loadErrors = List.of();
    private static volatile boolean loaded;

    private DisplayBorderManager() {
    }

    public static Path BorderDirectory() {
        String configuredPath = System.getProperty("gameduck.border_dir");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of(System.getProperty("user.home"), ".gameduck", "borders");
    }

    public static List<LoadedDisplayBorder> GetAvailableBorders() {
        EnsureLoaded();
        return borders;
    }

    public static List<String> GetLoadErrors() {
        EnsureLoaded();
        return loadErrors;
    }

    public static LoadedDisplayBorder Resolve(String borderId) {
        EnsureLoaded();
        if (borderId != null && !borderId.isBlank()) {
            for (LoadedDisplayBorder border : borders) {
                if (border.id().equalsIgnoreCase(borderId)) {
                    return border;
                }
            }
        }
        for (LoadedDisplayBorder border : borders) {
            if ("none".equalsIgnoreCase(border.id())) {
                return border;
            }
        }
        throw new IllegalStateException("Display border registry does not contain the required fallback border.");
    }

    public static LoadedDisplayBorder InspectExternalBorder(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Border PNG path is required.");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Border PNG file does not exist.");
        }

        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".png")) {
            throw new IllegalArgumentException("Only .png display borders are supported.");
        }

        try (InputStream stream = Files.newInputStream(path)) {
            return loadBorderImage(stream, path, fileName, customSourceLabel);
        }
    }

    public static synchronized void Reload() {
        Path borderDirectory = BorderDirectory();
        List<String> errors = new ArrayList<>();
        Map<String, LoadedDisplayBorder> loadedBordersById = new LinkedHashMap<>();

        registerBorder(loadedBordersById, new LoadedDisplayBorder("none", "Off", builtInSourceLabel, null, null, null), errors);

        try {
            Files.createDirectories(borderDirectory);
            EnsureReadme(borderDirectory);
        } catch (IOException exception) {
            errors.add("Failed to prepare border directory: " + exception.getMessage());
        }

        loadBundledBorders(loadedBordersById, errors);
        loadCustomBorders(borderDirectory, loadedBordersById, errors);

        borders = List.copyOf(loadedBordersById.values());
        loadErrors = List.copyOf(errors);
        loaded = true;
    }

    private static void EnsureLoaded() {
        if (!loaded) {
            Reload();
        }
    }

    private static void loadBundledBorders(Map<String, LoadedDisplayBorder> loadedBordersById, List<String> errors) {
        try (InputStream indexStream = DisplayBorderManager.class.getClassLoader()
                .getResourceAsStream(bundledBorderIndexResource)) {
            if (indexStream == null) {
                errors.add("Failed to locate bundled border index.");
                return;
            }

            String[] resourceNames = new String(indexStream.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
            for (String resourceName : resourceNames) {
                String trimmedName = resourceName.trim();
                if (trimmedName.isEmpty() || trimmedName.startsWith("#")) {
                    continue;
                }

                String resourcePath = bundledBorderResourcePrefix + trimmedName;
                try (InputStream borderStream = DisplayBorderManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (borderStream == null) {
                        errors.add("Missing bundled border resource \"" + trimmedName + "\".");
                        continue;
                    }
                    registerBorder(loadedBordersById,
                            loadBorderImage(borderStream, null, trimmedName, builtInSourceLabel),
                            errors);
                } catch (IOException | IllegalArgumentException exception) {
                    errors.add(trimmedName + ": " + exception.getMessage());
                }
            }
        } catch (IOException exception) {
            errors.add("Failed to read bundled border index: " + exception.getMessage());
        }
    }

    private static void loadCustomBorders(Path borderDirectory, Map<String, LoadedDisplayBorder> loadedBordersById,
            List<String> errors) {
        if (!Files.isDirectory(borderDirectory)) {
            return;
        }

        try (var files = Files.walk(borderDirectory)) {
            files.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted()
                    .forEach(path -> {
                        try (InputStream borderStream = Files.newInputStream(path)) {
                            registerBorder(loadedBordersById,
                                    loadBorderImage(borderStream, path, path.getFileName().toString(), customSourceLabel),
                                    errors);
                        } catch (IOException | IllegalArgumentException exception) {
                            errors.add(path.getFileName() + ": " + exception.getMessage());
                        }
                    });
        } catch (IOException exception) {
            errors.add("Failed to scan border PNG files: " + exception.getMessage());
        }
    }

    private static LoadedDisplayBorder loadBorderImage(InputStream stream, Path sourcePath, String fileName,
            String sourceLabel)
            throws IOException {
        BufferedImage image = ImageIO.read(stream);
        if (image == null) {
            throw new IllegalArgumentException("PNG could not be decoded.");
        }
        if (!image.getColorModel().hasAlpha()) {
            throw new IllegalArgumentException("PNG must include transparency for the gameplay cutout.");
        }

        Rectangle screenRect = detectScreenRect(image);
        if (screenRect == null || screenRect.width < 24 || screenRect.height < 24) {
            throw new IllegalArgumentException("PNG must have a transparent gameplay cutout connected to the image center.");
        }

        String normalizedFileName = fileNameForBundled(image, fileName);
        String id = normalizedId(normalizedFileName);
        String displayName = displayNameFromFileName(normalizedFileName);
        return new LoadedDisplayBorder(id, displayName, sourceLabel, sourcePath, image, screenRect);
    }

    private static String fileNameForBundled(BufferedImage image, String fallback) {
        return fallback == null || fallback.isBlank()
                ? "border-" + image.getWidth() + "x" + image.getHeight() + ".png"
                : fallback;
    }

    private static void registerBorder(Map<String, LoadedDisplayBorder> loadedBordersById, LoadedDisplayBorder border,
            List<String> errors) {
        String id = border.id();
        if (id == null || id.isBlank()) {
            errors.add("Skipped a border with a blank id.");
            return;
        }

        String normalizedId = id.toLowerCase(Locale.ROOT);
        if (loadedBordersById.containsKey(normalizedId)) {
            errors.add("Skipped duplicate border id \"" + id + "\".");
            return;
        }

        loadedBordersById.put(normalizedId, border);
    }

    private static Rectangle detectScreenRect(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        if (alpha(image.getRGB(centerX, centerY)) > transparentThreshold) {
            return null;
        }

        boolean[] visited = new boolean[width * height];
        ArrayDeque<Point> queue = new ArrayDeque<>();
        queue.add(new Point(centerX, centerY));
        visited[(centerY * width) + centerX] = true;

        int minX = centerX;
        int maxX = centerX;
        int minY = centerY;
        int maxY = centerY;

        while (!queue.isEmpty()) {
            Point point = queue.removeFirst();
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);

            enqueueTransparent(image, queue, visited, point.x - 1, point.y, width, height);
            enqueueTransparent(image, queue, visited, point.x + 1, point.y, width, height);
            enqueueTransparent(image, queue, visited, point.x, point.y - 1, width, height);
            enqueueTransparent(image, queue, visited, point.x, point.y + 1, width, height);
        }

        return new Rectangle(minX, minY, (maxX - minX) + 1, (maxY - minY) + 1);
    }

    private static void enqueueTransparent(BufferedImage image, ArrayDeque<Point> queue, boolean[] visited,
            int x, int y, int width, int height) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        int index = (y * width) + x;
        if (visited[index]) {
            return;
        }
        visited[index] = true;
        if (alpha(image.getRGB(x, y)) <= transparentThreshold) {
            queue.addLast(new Point(x, y));
        }
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static String normalizedId(String fileName) {
        String id = stripExtension(fileName).toLowerCase(Locale.ROOT)
                .replace('\\', '/')
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return id.isBlank() ? "custom_border" : id;
    }

    private static String displayNameFromFileName(String fileName) {
        String baseName = stripExtension(fileName).replace('_', ' ').replace('-', ' ');
        String[] tokens = baseName.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                builder.append(token.substring(1));
            }
        }
        return builder.isEmpty() ? "Custom Border" : builder.toString();
    }

    private static String stripExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex <= 0 ? fileName : fileName.substring(0, extensionIndex);
    }

    private static void EnsureReadme(Path borderDirectory) throws IOException {
        Path readmePath = borderDirectory.resolve("README.txt");
        if (Files.exists(readmePath)) {
            return;
        }

        String readme = """
                GameDuck display borders
                =======================

                Drop custom PNG borders into this folder, then pick them from:
                Options > Window > Display Border.

                Border PNG requirements:
                - PNG format only
                - Must include transparency
                - The gameplay window must be transparent and connected to the PNG center
                - Keep the transparent gameplay window aligned to the Game Boy aspect ratio for best results

                The emulator scales the full PNG to fit the available display area, then draws
                gameplay into the detected transparent center cutout.
                """;
        Files.writeString(readmePath, readme, StandardCharsets.UTF_8);
    }
}
