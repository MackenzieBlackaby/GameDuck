package com.blackaby.Backend.Platform;

import com.blackaby.Backend.GB.GBBackend;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central registry for every emulator backend exposed to the desktop host.
 */
public final class BackendRegistry {

    private static final List<EmulatorBackend> backends = List.of(
            GBBackend.instance);
    private static final Map<String, EmulatorBackend> backendsById = IndexBackendsById(backends);

    private BackendRegistry() {
    }

    public static List<EmulatorBackend> All() {
        return backends;
    }

    public static EmulatorBackend Default() {
        return backends.getFirst();
    }

    public static EmulatorBackend FindByBackendId(String backendId) {
        if (backendId == null || backendId.isBlank()) {
            return null;
        }
        return backendsById.get(backendId);
    }

    public static EmulatorBackend ResolveBackendForGame(Path gamePath) {
        return ResolveBackendByExtension(gamePath, false);
    }

    public static EmulatorBackend ResolveBackendForGame(EmulatorGame game) {
        if (game == null) {
            return null;
        }

        if (game.sourcePath() != null && !game.sourcePath().isBlank()) {
            try {
                EmulatorBackend resolvedBackend = ResolveBackendForGame(Path.of(game.sourcePath()));
                if (resolvedBackend != null) {
                    return resolvedBackend;
                }
            } catch (RuntimeException exception) {
                // Fall back to identity-based resolution below.
            }
        }

        return FindByBackendId(game.systemId());
    }

    public static EmulatorBackend ResolveBackendForPatch(Path patchPath) {
        return ResolveBackendByExtension(patchPath, true);
    }

    public static EmulatorMedia LoadMedia(Path gamePath) throws IOException {
        EmulatorBackend backend = ResolveBackendForGame(gamePath);
        if (backend == null) {
            throw new IllegalArgumentException("No registered core supports \"" + SafeFilename(gamePath) + "\".");
        }
        return backend.LoadMedia(gamePath);
    }

    public static EmulatorMedia LoadPatchedMedia(Path baseGamePath, Path patchPath) throws IOException {
        EmulatorBackend backend = ResolveBackendForGame(baseGamePath);
        if (backend == null) {
            throw new IllegalArgumentException("No registered core supports \"" + SafeFilename(baseGamePath) + "\".");
        }
        if (!SupportsExtension(backend.Profile().supportedPatchFileExtensions(), FileExtension(patchPath))) {
            throw new IllegalArgumentException("The selected core does not support \"" + SafeFilename(patchPath) + "\".");
        }
        return backend.LoadPatchedMedia(baseGamePath, patchPath);
    }

    private static EmulatorBackend ResolveBackendByExtension(Path path, boolean patchExtension) {
        String extension = FileExtension(path);
        if (extension.isBlank()) {
            return null;
        }

        for (EmulatorBackend backend : backends) {
            List<String> extensions = patchExtension
                    ? backend.Profile().supportedPatchFileExtensions()
                    : backend.Profile().supportedGameFileExtensions();
            if (SupportsExtension(extensions, extension)) {
                return backend;
            }
        }
        return null;
    }

    private static boolean SupportsExtension(List<String> extensions, String extension) {
        if (extension == null || extension.isBlank() || extensions == null) {
            return false;
        }

        for (String item : extensions) {
            if (extension.equals(NormaliseExtension(item))) {
                return true;
            }
        }
        return false;
    }

    private static String FileExtension(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }

        String filename = path.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= filename.length() - 1) {
            return "";
        }
        return NormaliseExtension(filename.substring(dotIndex));
    }

    private static String NormaliseExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }

        String trimmed = extension.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith(".") ? trimmed : "." + trimmed;
    }

    private static String SafeFilename(Path path) {
        if (path == null || path.getFileName() == null) {
            return "unknown";
        }
        return path.getFileName().toString();
    }

    private static Map<String, EmulatorBackend> IndexBackendsById(List<EmulatorBackend> registeredBackends) {
        Map<String, EmulatorBackend> indexedBackends = new LinkedHashMap<>();
        for (EmulatorBackend backend : registeredBackends) {
            if (backend == null || backend.Profile() == null) {
                continue;
            }

            String backendId = backend.Profile().backendId();
            if (backendId == null || backendId.isBlank()) {
                continue;
            }
            indexedBackends.put(backendId, backend);
        }
        return Map.copyOf(indexedBackends);
    }
}
