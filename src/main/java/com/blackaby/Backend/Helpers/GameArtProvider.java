package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.Platform.EmulatorGame;
import com.blackaby.Misc.GameArtDisplayMode;
import com.blackaby.Misc.UiText;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Looks up cached or remote game art for loaded ROMs.
 * <p>
 * The provider prefers libretro box art and falls back to title screens or
 * gameplay snaps when cover art is unavailable.
 */
public final class GameArtProvider {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final Path cacheDirectory = Path.of("cache", "game-art");
    private static final List<String> artTypeOrder = List.of("Named_Boxarts", "Named_Titles", "Named_Snaps");
    private static final String libretroBaseUrl = "https://thumbnails.libretro.com";

    private GameArtProvider() {
    }

    /**
     * Encapsulates fetched game art and its source label.
     *
     * @param image fetched image
     * @param sourceLabel short attribution label
     * @param matchedGameName matched libretro game title
     */
    public record GameArtResult(BufferedImage image, String sourceLabel, String matchedGameName) {
    }

    public record GameArtDescriptor(String sourcePath, String sourceName, String displayName, String headerTitle) {
        public static GameArtDescriptor FromRom(GBRom rom) {
            return FromGame(rom);
        }

        public static GameArtDescriptor FromGame(EmulatorGame game) {
            if (game == null) {
                return null;
            }
            return new GameArtDescriptor(game.sourcePath(), game.sourceName(), game.displayName(), game.headerTitle());
        }
    }

    /**
     * Resolves game art for the supplied ROM, using a local cache when present.
     *
     * @param rom active ROM
     * @return fetched art if a source matched
     */
    public static Optional<GameArtResult> FindGameArt(GBRom rom) {
        return FindGameArt(GameArtDescriptor.FromRom(rom), null);
    }

    public static Optional<GameArtResult> FindGameArt(EmulatorGame game) {
        return FindGameArt(GameArtDescriptor.FromGame(game), null);
    }

    /**
     * Resolves game art for the supplied game descriptor, using a local cache when present.
     *
     * @param descriptor tracked game descriptor
     * @return fetched art if a source matched
     */
    public static Optional<GameArtResult> FindGameArt(GameArtDescriptor descriptor) {
        return FindGameArt(descriptor, null);
    }

    /**
     * Resolves game art for the supplied ROM using an optional explicit art mode.
     *
     * @param rom active ROM
     * @param displayMode requested art mode, or {@code null} for provider default order
     * @return fetched art if a source matched
     */
    public static Optional<GameArtResult> FindGameArt(GBRom rom, GameArtDisplayMode displayMode) {
        return FindGameArt(GameArtDescriptor.FromRom(rom), displayMode);
    }

    public static Optional<GameArtResult> FindGameArt(EmulatorGame game, GameArtDisplayMode displayMode) {
        return FindGameArt(GameArtDescriptor.FromGame(game), displayMode);
    }

    /**
     * Resolves game art for the supplied game descriptor, using a local cache when present.
     *
     * @param descriptor tracked game descriptor
     * @param displayMode requested art mode, or {@code null} for provider default order
     * @return fetched art if a source matched
     */
    public static Optional<GameArtResult> FindGameArt(GameArtDescriptor descriptor, GameArtDisplayMode displayMode) {
        if (descriptor == null) {
            return Optional.empty();
        }
        if (displayMode == GameArtDisplayMode.NONE) {
            return Optional.empty();
        }

        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException exception) {
            // Continue without disk caching when the cache path is unavailable.
        }

        for (GameArtCandidate candidate : BuildArtCandidates(descriptor, displayMode)) {
            Optional<BufferedImage> cachedImage = LoadCachedImage(candidate.url());
            if (cachedImage.isPresent()) {
                return Optional.of(new GameArtResult(cachedImage.get(), ResolveSourceLabel(displayMode, candidate.artType()),
                        candidate.gameName()));
            }

            BufferedImage remoteImage = DownloadImage(candidate.url());
            if (remoteImage != null) {
                return Optional.of(new GameArtResult(remoteImage, ResolveSourceLabel(displayMode, candidate.artType()),
                        candidate.gameName()));
            }
        }

        return Optional.empty();
    }

    static List<String> BuildUrlCandidates(GBRom rom) {
        return BuildUrlCandidates(GameArtDescriptor.FromRom(rom));
    }

    static List<String> BuildUrlCandidates(EmulatorGame game) {
        return BuildUrlCandidates(GameArtDescriptor.FromGame(game));
    }

    static List<String> BuildUrlCandidates(GameArtDescriptor descriptor) {
        List<String> urls = new ArrayList<>();
        for (GameArtCandidate candidate : BuildArtCandidates(descriptor, null)) {
            urls.add(candidate.url());
        }
        return urls;
    }

    static List<GameArtCandidate> BuildArtCandidates(GBRom rom) {
        return BuildArtCandidates(GameArtDescriptor.FromRom(rom), null);
    }

    static List<GameArtCandidate> BuildArtCandidates(EmulatorGame game) {
        return BuildArtCandidates(GameArtDescriptor.FromGame(game), null);
    }

    static List<GameArtCandidate> BuildArtCandidates(GameArtDescriptor descriptor) {
        return BuildArtCandidates(descriptor, null);
    }

    static List<GameArtCandidate> BuildArtCandidates(GameArtDescriptor descriptor, GameArtDisplayMode displayMode) {
        List<GameArtCandidate> candidates = new ArrayList<>();
        List<String> candidateNames = BuildCandidateNames(descriptor);
        for (String playlistName : BuildPlaylistOrder(descriptor)) {
            String encodedPlaylistName = EncodePathPart(playlistName);
            for (String artType : ResolveArtTypeOrder(displayMode)) {
                String encodedArtType = EncodePathPart(artType);
                for (String candidateName : candidateNames) {
                    String url = libretroBaseUrl + "/" + encodedPlaylistName + "/" + encodedArtType + "/"
                            + EncodePathPart(candidateName) + ".png";
                    candidates.add(new GameArtCandidate(url, candidateName, artType));
                }
            }
        }
        return candidates;
    }

    static List<String> BuildPlaylistOrder(GBRom rom) {
        return BuildPlaylistOrder(GameArtDescriptor.FromRom(rom));
    }

    static List<String> BuildPlaylistOrder(EmulatorGame game) {
        return BuildPlaylistOrder(GameArtDescriptor.FromGame(game));
    }

    static List<String> BuildPlaylistOrder(GameArtDescriptor descriptor) {
        LinkedHashSet<String> playlistNames = new LinkedHashSet<>();
        String lowerSourcePath = descriptor == null || descriptor.sourcePath() == null ? "" : descriptor.sourcePath().toLowerCase();
        if (lowerSourcePath.endsWith(".gbc") || lowerSourcePath.endsWith(".cgb")) {
            playlistNames.add("Nintendo - Game Boy Color");
            playlistNames.add("Nintendo - Game Boy");
        } else {
            playlistNames.add("Nintendo - Game Boy");
            playlistNames.add("Nintendo - Game Boy Color");
        }
        return List.copyOf(playlistNames);
    }

    static List<String> BuildCandidateNames(GBRom rom) {
        return BuildCandidateNames(GameArtDescriptor.FromRom(rom));
    }

    static List<String> BuildCandidateNames(EmulatorGame game) {
        return BuildCandidateNames(GameArtDescriptor.FromGame(game));
    }

    static List<String> BuildCandidateNames(GameArtDescriptor descriptor) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (descriptor == null) {
            return List.of();
        }
        AddCandidateName(names, descriptor.sourceName());
        AddCandidateName(names, descriptor.displayName());
        AddCandidateName(names, StripTrailingTags(descriptor.sourceName()));
        AddCandidateName(names, StripTrailingTags(descriptor.displayName()));
        AddCandidateName(names, descriptor.headerTitle());
        return List.copyOf(names);
    }

    private static void AddCandidateName(LinkedHashSet<String> names, String value) {
        if (value == null) {
            return;
        }

        String normalised = NormaliseName(value);
        if (!normalised.isBlank()) {
            names.add(normalised);
        }
    }

    private static String NormaliseName(String value) {
        String collapsedWhitespace = value.trim().replaceAll("\\s+", " ");
        return collapsedWhitespace.replaceAll("[&*/:`<>?\\\\|\"]", "_");
    }

    private static String StripTrailingTags(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("\\s*(\\[[^\\]]*]|\\([^)]*\\))+$", "").trim();
    }

    private static Optional<BufferedImage> LoadCachedImage(String url) {
        Path cachePath = CachePathFor(url);
        if (!Files.isRegularFile(cachePath)) {
            return Optional.empty();
        }

        try {
            BufferedImage image = ImageIO.read(cachePath.toFile());
            return Optional.ofNullable(image);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static BufferedImage DownloadImage(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", "GameDuck/1.0")
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length == 0) {
                return null;
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (image == null) {
                return null;
            }

            try {
                Files.write(CachePathFor(url), response.body());
            } catch (IOException exception) {
                // Display the fetched image even when it cannot be cached locally.
            }
            return image;
        } catch (IOException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String EncodePathPart(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Path CachePathFor(String url) {
        return cacheDirectory.resolve(Hash(url) + ".img");
    }

    private static List<String> ResolveArtTypeOrder(GameArtDisplayMode displayMode) {
        if (displayMode == null) {
            return artTypeOrder;
        }
        if (displayMode == GameArtDisplayMode.NONE) {
            return List.of();
        }
        return List.of(displayMode.LibretroArtType());
    }

    private static String ResolveSourceLabel(GameArtDisplayMode displayMode, String artType) {
        if (displayMode != null) {
            return displayMode.SourceLabel();
        }
        return switch (artType) {
            case "Named_Boxarts" -> UiText.GameArt.SOURCE_LIBRETRO_BOXART;
            case "Named_Titles" -> UiText.GameArt.SOURCE_LIBRETRO_TITLE;
            case "Named_Snaps" -> UiText.GameArt.SOURCE_LIBRETRO_SCREENSHOT;
            default -> UiText.GameArt.SOURCE_LIBRETRO;
        };
    }

    record GameArtCandidate(String url, String gameName, String artType) {
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
}

