package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.Platform.EmulatorGame;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Properties;

/**
 * Persists resolved libretro game titles for ROM identities.
 */
public final class GameMetadataStore {

    private static final String libretroTitlePrefix = "libretro.title.";
    private static final Properties properties = new Properties();
    private static boolean loaded;

    private GameMetadataStore() {
    }

    /**
     * Returns a cached libretro title for the supplied ROM identity.
     *
     * @param rom ROM identity
     * @return cached libretro title when known
     */
    public static synchronized Optional<String> GetLibretroTitle(GBRom rom) {
        return GetLibretroTitle(SaveFileManager.SaveIdentity.FromRom(rom));
    }

    public static synchronized Optional<String> GetLibretroTitle(EmulatorGame game) {
        return GetLibretroTitle(SaveFileManager.SaveIdentity.FromGame(game));
    }

    /**
     * Returns a cached libretro title for the supplied save identity.
     *
     * @param saveIdentity tracked game identity
     * @return cached libretro title when known
     */
    public static synchronized Optional<String> GetLibretroTitle(SaveFileManager.SaveIdentity saveIdentity) {
        if (saveIdentity == null) {
            return Optional.empty();
        }
        EnsureLoaded();
        String value = properties.getProperty(libretroTitlePrefix + BuildRomKey(saveIdentity));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /**
     * Stores a resolved libretro title for later save-file naming.
     *
     * @param rom ROM identity
     * @param libretroTitle matched libretro title
     */
    public static synchronized void RememberLibretroTitle(GBRom rom, String libretroTitle) {
        RememberLibretroTitle(SaveFileManager.SaveIdentity.FromRom(rom), libretroTitle);
    }

    public static synchronized void RememberLibretroTitle(EmulatorGame game, String libretroTitle) {
        RememberLibretroTitle(SaveFileManager.SaveIdentity.FromGame(game), libretroTitle);
    }

    /**
     * Stores a resolved libretro title for later save-file naming.
     *
     * @param saveIdentity tracked game identity
     * @param libretroTitle matched libretro title
     */
    public static synchronized void RememberLibretroTitle(SaveFileManager.SaveIdentity saveIdentity, String libretroTitle) {
        if (saveIdentity == null || libretroTitle == null || libretroTitle.isBlank()) {
            return;
        }

        EnsureLoaded();
        properties.setProperty(libretroTitlePrefix + BuildRomKey(saveIdentity), libretroTitle.trim());
        Persist();
    }

    static String BuildRomKey(GBRom rom) {
        return BuildRomKey(SaveFileManager.SaveIdentity.FromRom(rom));
    }

    static String BuildRomKey(EmulatorGame game) {
        return BuildRomKey(SaveFileManager.SaveIdentity.FromGame(game));
    }

    static String BuildRomKey(SaveFileManager.SaveIdentity saveIdentity) {
        String identity = SaveFileManager.BuildFallbackBaseName(saveIdentity) + "|"
                + String.join("|", saveIdentity == null ? java.util.List.of() : saveIdentity.patchNames());
        return Hash(identity);
    }

    private static void EnsureLoaded() {
        if (loaded) {
            return;
        }

        properties.clear();
        Path metadataPath = MetadataPath();
        if (Files.exists(metadataPath)) {
            try (InputStream inputStream = Files.newInputStream(metadataPath)) {
                properties.load(inputStream);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        loaded = true;
    }

    private static void Persist() {
        Path metadataPath = MetadataPath();
        try {
            Path parent = metadataPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream outputStream = Files.newOutputStream(metadataPath)) {
                properties.store(outputStream, "GameDuck game metadata");
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private static Path MetadataPath() {
        String configuredPath = System.getProperty("gameduck.game_metadata_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of("cache", "game-metadata.properties");
    }

    private static String Hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is unavailable.", exception);
        }
    }

    static synchronized void ResetForTests() {
        properties.clear();
        loaded = false;
    }
}

