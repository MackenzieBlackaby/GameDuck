package com.blackaby.Misc;

import com.blackaby.Backend.Helpers.GameLibraryStore;
import com.blackaby.Backend.Helpers.ManagedGameRegistry;
import com.blackaby.Backend.Helpers.QuickStateManager;
import com.blackaby.Backend.Helpers.SaveFileManager;
import com.blackaby.Frontend.Borders.DisplayBorderManager;
import com.blackaby.Frontend.Shaders.DisplayShaderManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Creates and restores versioned .gdlib bundles containing the user's managed
 * GameDuck data and profile assets.
 */
public final class GameDuckDataBundleManager {

    public static final String bundleExtension = ".gdlib";

    private static final String manifestEntryName = "manifest.json";
    private static final String formatName = "gameduck-library-bundle";
    private static final int formatVersion = 1;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private GameDuckDataBundleManager() {
    }

    public static void CreateBackup(Path destinationPath) throws IOException {
        if (destinationPath == null) {
            throw new IOException("Choose a destination .gdlib file first.");
        }

        Path parent = destinationPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporaryPath = destinationPath.resolveSibling(destinationPath.getFileName() + ".tmp");
        Files.deleteIfExists(temporaryPath);
        List<BundleSource> bundleSources = BundleSources();
        BundleManifest manifest = new BundleManifest(
                formatName,
                formatVersion,
                System.currentTimeMillis(),
                bundleSources.stream().map(bundleSource -> ToBundleEntryName(bundleSource.bundlePath())).toList());

        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(temporaryPath));
             Writer manifestWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            outputStream.putNextEntry(new ZipEntry(manifestEntryName));
            gson.toJson(manifest, manifestWriter);
            manifestWriter.flush();
            outputStream.closeEntry();

            for (BundleSource bundleSource : bundleSources) {
                WriteBundleSource(outputStream, bundleSource);
            }
        } catch (IOException exception) {
            Files.deleteIfExists(temporaryPath);
            throw exception;
        }

        Files.move(temporaryPath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void RestoreBackup(Path sourcePath) throws IOException {
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            throw new IOException("Select a valid .gdlib backup file.");
        }

        Path extractionDirectory = Files.createTempDirectory("gameduck-restore-");
        try {
            BundleManifest manifest = ExtractBackup(sourcePath, extractionDirectory);
            if (manifest == null || manifest.format() == null || !manifest.format().equals(formatName)) {
                throw new IOException("The selected file is not a valid GameDuck .gdlib backup.");
            }

            DeleteEverything();
            for (BundleSource bundleSource : BundleSources()) {
                Path extractedPath = extractionDirectory.resolve(bundleSource.bundlePath());
                RestoreBundleSource(extractedPath, bundleSource.actualPath());
            }
            ReloadManagedState();
        } finally {
            DeletePath(extractionDirectory);
        }
    }

    public static void ResetPreferences() throws IOException {
        DeletePath(Config.ConfigFilePath());
        DeletePath(Config.PaletteFilePath());
        Settings.Reset();
        Config.Load();
        Config.Save();
    }

    public static void ResetShaders() throws IOException {
        DeletePath(DisplayShaderManager.ShaderDirectory());
        Settings.displayShaderId = "none";
        Config.Save();
        DisplayShaderManager.Reload();
    }

    public static void DeleteLibrary() throws IOException {
        DeletePath(GameLibraryStore.LibraryDirectoryPath());
        DeletePath(GameLibraryStore.MetadataFilePath());
        DeletePath(GameLibraryStore.PortableMetadataFilePath());
        GameLibraryStore.RefreshLibrary();
    }

    public static void DeleteAllSaveData() throws IOException {
        DeletePath(SaveFileManager.SaveDirectoryPath());
        DeletePath(QuickStateManager.QuickStateDirectoryPath());
        DeletePath(ManagedGameRegistry.MetadataFilePath());
        ManagedGameRegistry.RefreshFromLibraryEntries(GameLibraryStore.GetEntries());
    }

    public static void DeleteEverything() throws IOException {
        for (BundleSource bundleSource : BundleSources()) {
            DeletePath(bundleSource.actualPath());
        }
        ReloadManagedState();
    }

    private static List<BundleSource> BundleSources() {
        List<BundleSource> sources = new ArrayList<>();
        sources.add(new BundleSource(Path.of("runtime", "cache"), CacheDirectoryPath()));
        sources.add(new BundleSource(Path.of("runtime", "library"), GameLibraryStore.LibraryDirectoryPath()));
        sources.add(new BundleSource(Path.of("runtime", "saves"), SaveFileManager.SaveDirectoryPath()));
        sources.add(new BundleSource(Path.of("runtime", "quickstates"), QuickStateManager.QuickStateDirectoryPath()));
        sources.add(new BundleSource(Path.of("profile", "config.properties"), Config.ConfigFilePath()));
        sources.add(new BundleSource(Path.of("profile", "palettes.json"), Config.PaletteFilePath()));
        sources.add(new BundleSource(Path.of("profile", "themes"), ThemeStore.ThemeDirectoryPath()));
        sources.add(new BundleSource(Path.of("profile", "shaders"), DisplayShaderManager.ShaderDirectory()));
        sources.add(new BundleSource(Path.of("profile", "borders"), DisplayBorderManager.BorderDirectory()));
        sources.add(new BundleSource(Path.of("profile", "dmg_boot.bin"), BootRomManager.DmgBootRomPath()));
        sources.add(new BundleSource(Path.of("profile", "cgb_boot.bin"), BootRomManager.CgbBootRomPath()));
        return List.copyOf(sources);
    }

    private static void WriteBundleSource(ZipOutputStream outputStream, BundleSource bundleSource) throws IOException {
        Path actualPath = bundleSource.actualPath();
        if (Files.isRegularFile(actualPath)) {
            WriteFileEntry(outputStream, actualPath, bundleSource.bundlePath());
            return;
        }
        if (!Files.isDirectory(actualPath)) {
            return;
        }

        try (var paths = Files.walk(actualPath)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Path relativePath = actualPath.relativize(path);
                WriteFileEntry(outputStream, path, bundleSource.bundlePath().resolve(relativePath));
            }
        }
    }

    private static void WriteFileEntry(ZipOutputStream outputStream, Path sourcePath, Path bundlePath) throws IOException {
        ZipEntry zipEntry = new ZipEntry(ToBundleEntryName(bundlePath));
        zipEntry.setTime(Files.getLastModifiedTime(sourcePath).toMillis());
        outputStream.putNextEntry(zipEntry);
        Files.copy(sourcePath, outputStream);
        outputStream.closeEntry();
    }

    private static BundleManifest ExtractBackup(Path sourcePath, Path extractionDirectory) throws IOException {
        BundleManifest manifest = null;
        try (ZipInputStream inputStream = new ZipInputStream(Files.newInputStream(sourcePath))) {
            ZipEntry zipEntry;
            while ((zipEntry = inputStream.getNextEntry()) != null) {
                Path targetPath = ResolveExtractedPath(extractionDirectory, zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(targetPath);
                    inputStream.closeEntry();
                    continue;
                }

                Path parent = targetPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                if (zipEntry.getTime() > 0L) {
                    Files.setLastModifiedTime(targetPath, java.nio.file.attribute.FileTime.fromMillis(zipEntry.getTime()));
                }
                inputStream.closeEntry();

                if (manifestEntryName.equals(zipEntry.getName())) {
                    try (Reader reader = Files.newBufferedReader(targetPath, StandardCharsets.UTF_8)) {
                        manifest = gson.fromJson(reader, BundleManifest.class);
                    }
                }
            }
        }
        return manifest;
    }

    private static Path ResolveExtractedPath(Path root, String entryName) throws IOException {
        Path resolvedPath = root.resolve(entryName).normalize();
        if (!resolvedPath.startsWith(root)) {
            throw new IOException("The .gdlib file contains an invalid path.");
        }
        return resolvedPath;
    }

    private static void RestoreBundleSource(Path extractedPath, Path actualPath) throws IOException {
        if (Files.isRegularFile(extractedPath)) {
            Path parent = actualPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(extractedPath, actualPath, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        if (!Files.isDirectory(extractedPath)) {
            return;
        }

        Files.createDirectories(actualPath);
        try (var paths = Files.walk(extractedPath)) {
            for (Path path : paths.toList()) {
                Path relativePath = extractedPath.relativize(path);
                Path targetPath = actualPath.resolve(relativePath);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(targetPath);
                    continue;
                }
                Path parent = targetPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void ReloadManagedState() {
        Config.Load();
        DisplayShaderManager.Reload();
        DisplayBorderManager.Reload();
        GameLibraryStore.RecoverLibrary();
        ManagedGameRegistry.RefreshFromLibraryEntries(GameLibraryStore.GetEntries());
    }

    private static void DeletePath(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }

        if (Files.isRegularFile(path)) {
            Files.deleteIfExists(path);
            return;
        }

        try (var paths = Files.walk(path)) {
            for (Path currentPath : paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(currentPath);
            }
        }
    }

    private static String ToBundleEntryName(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static Path CacheDirectoryPath() {
        String configuredPath = System.getProperty("gameduck.cache_dir");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of("cache");
    }

    private record BundleSource(Path bundlePath, Path actualPath) {
    }

    private record BundleManifest(String format, int version, long createdAtEpochMs, List<String> roots) {
    }
}
