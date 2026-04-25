package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.Platform.EmulatorCheat;
import com.blackaby.Backend.Platform.EmulatorGame;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads and converts compatible libretro cheat files into GameDuck cheats.
 */
public final class LibretroCheatProvider {

    public enum AutoImportStatus {
        IMPORTED,
        UNCHANGED,
        ALREADY_IMPORTED,
        NOT_FOUND
    }

    public record AutoImportResult(AutoImportStatus status, int importedCount, String matchedGameName, String sourceFileName) {
    }

    public record LibretroCheatSet(String matchedGameName, String sourceFileName, List<EmulatorCheat> cheats) {
        public LibretroCheatSet {
            cheats = List.copyOf(cheats == null ? List.of() : cheats);
        }
    }

    static record ParsedGameSharkCode(int address, int value) {
    }

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(4))
            .build();
    private static final ExecutorService autoImportExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gameduck-libretro-cheats");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final Pattern cheatDescriptionPattern = Pattern.compile("cheat(\\d+)_desc\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern cheatCodePattern = Pattern.compile("cheat(\\d+)_code\\s*=\\s*\"([^\"]*)\"");
    private static final String repositoryApiBaseUrl = "https://api.github.com/repos/libretro/libretro-database";
    private static final String rawRepositoryBaseUrl = "https://raw.githubusercontent.com/libretro/libretro-database";
    private static final Path cacheDirectory = Path.of("cache", "libretro-cheats");

    private static final Map<String, Optional<LibretroCheatSet>> cheatSetCache = new ConcurrentHashMap<>();
    private static final Map<String, Optional<String>> cheatFileCache = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<AutoImportResult>> inFlightImports = new ConcurrentHashMap<>();
    private static volatile String defaultBranch;

    private LibretroCheatProvider() {
    }

    public static CompletableFuture<AutoImportResult> AutoImportCheatsAsync(EmulatorGame game) {
        String importKey = CheatStore.BuildGameKey(game);
        if (importKey.isBlank()) {
            return CompletableFuture.completedFuture(new AutoImportResult(AutoImportStatus.NOT_FOUND, 0, null, null));
        }
        if (CheatStore.HasLibretroImportRecord(game)) {
            return CompletableFuture.completedFuture(new AutoImportResult(AutoImportStatus.ALREADY_IMPORTED, 0, null, null));
        }

        return inFlightImports.computeIfAbsent(importKey, ignored -> CompletableFuture
                .supplyAsync(() -> AutoImportCheats(game), autoImportExecutor)
                .whenComplete((ignoredResult, ignoredError) -> inFlightImports.remove(importKey)));
    }

    public static AutoImportResult AutoImportCheats(EmulatorGame game) {
        return AutoImportCheats(game, () -> FindCheats(game));
    }

    static AutoImportResult AutoImportCheats(EmulatorGame game, Supplier<Optional<LibretroCheatSet>> cheatResolver) {
        if (game == null || cheatResolver == null) {
            return new AutoImportResult(AutoImportStatus.NOT_FOUND, 0, null, null);
        }
        if (CheatStore.HasLibretroImportRecord(game)) {
            return new AutoImportResult(AutoImportStatus.ALREADY_IMPORTED, 0, null, null);
        }

        Optional<LibretroCheatSet> cheatSet = cheatResolver.get();
        if (cheatSet.isEmpty() || cheatSet.get().cheats().isEmpty()) {
            return new AutoImportResult(AutoImportStatus.NOT_FOUND, 0, null, null);
        }

        LibretroCheatSet resolvedCheats = cheatSet.get();
        int importedCount = CheatStore.MergeLibretroCheats(game, resolvedCheats.cheats());
        GameMetadataStore.RememberLibretroTitle(game, resolvedCheats.matchedGameName());
        CheatStore.MarkLibretroImport(game, resolvedCheats.sourceFileName());
        return new AutoImportResult(importedCount > 0 ? AutoImportStatus.IMPORTED : AutoImportStatus.UNCHANGED,
                importedCount,
                resolvedCheats.matchedGameName(),
                resolvedCheats.sourceFileName());
    }

    public static Optional<LibretroCheatSet> FindCheats(EmulatorGame game) {
        String lookupKey = BuildLookupCacheKey(game);
        if (lookupKey.isBlank()) {
            return Optional.empty();
        }

        Optional<LibretroCheatSet> cachedCheatSet = cheatSetCache.get(lookupKey);
        if (cachedCheatSet != null) {
            return cachedCheatSet;
        }

        Optional<LibretroCheatSet> resolvedCheatSet = FindCheatsUncached(game);
        cheatSetCache.put(lookupKey, resolvedCheatSet);
        return resolvedCheatSet;
    }

    private static Optional<LibretroCheatSet> FindCheatsUncached(EmulatorGame game) {
        if (game == null) {
            return Optional.empty();
        }

        String branch = LoadDefaultBranch();
        if (branch.isBlank()) {
            return Optional.empty();
        }

        for (String playlistName : BuildPlaylistOrder(game)) {
            for (String fileName : BuildCandidateFileNames(game)) {
                String content = LoadCheatFile(branch, playlistName, fileName);
                if (content.isBlank()) {
                    continue;
                }

                List<EmulatorCheat> cheats = ParseCheatFile(game, playlistName, fileName, content);
                if (!cheats.isEmpty()) {
                    return Optional.of(new LibretroCheatSet(StripCheatFileSuffix(fileName), fileName, cheats));
                }
            }
        }

        return Optional.empty();
    }

    static List<EmulatorCheat> ParseCheatFile(EmulatorGame game, String playlistName, String fileName, String content) {
        if (game == null || content == null || content.isBlank()) {
            return List.of();
        }

        Map<Integer, String> descriptionsByIndex = new LinkedHashMap<>();
        Matcher descriptionMatcher = cheatDescriptionPattern.matcher(content);
        while (descriptionMatcher.find()) {
            descriptionsByIndex.put(Integer.parseInt(descriptionMatcher.group(1)), descriptionMatcher.group(2).trim());
        }

        String gameKey = CheatStore.BuildGameKey(game);
        if (gameKey.isBlank()) {
            return List.of();
        }

        List<EmulatorCheat> cheats = new ArrayList<>();
        Matcher codeMatcher = cheatCodePattern.matcher(content);
        while (codeMatcher.find()) {
            int cheatIndex = Integer.parseInt(codeMatcher.group(1));
            String description = descriptionsByIndex.getOrDefault(cheatIndex, "Libretro Cheat " + cheatIndex);
            String[] segments = codeMatcher.group(2).split("\\+");
            List<ParsedGameSharkCode> parsedSegments = new ArrayList<>();
            List<Integer> parsedSegmentIndexes = new ArrayList<>();
            for (int segmentIndex = 0; segmentIndex < segments.length; segmentIndex++) {
                Optional<ParsedGameSharkCode> parsedCode = ParseGameSharkCode(segments[segmentIndex]);
                if (parsedCode.isPresent()) {
                    parsedSegments.add(parsedCode.get());
                    parsedSegmentIndexes.add(segmentIndex);
                }
            }

            for (int parsedIndex = 0; parsedIndex < parsedSegments.size(); parsedIndex++) {
                ParsedGameSharkCode parsedCode = parsedSegments.get(parsedIndex);
                int originalSegmentIndex = parsedSegmentIndexes.get(parsedIndex);
                String label = parsedSegments.size() == 1
                        ? description
                        : description + " (" + (parsedIndex + 1) + "/" + parsedSegments.size() + ")";
                String key = "libretro-" + KeyedPropertiesStore.Hash(gameKey + "|" + playlistName + "|" + fileName + "|"
                        + cheatIndex + "|" + originalSegmentIndex);
                cheats.add(new EmulatorCheat(key, label, parsedCode.address(), null, parsedCode.value(), false));
            }
        }

        return List.copyOf(cheats);
    }

    static Optional<ParsedGameSharkCode> ParseGameSharkCode(String code) {
        String cleaned = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!cleaned.matches("[0-9A-F]{8}")) {
            return Optional.empty();
        }

        int value = Integer.parseInt(cleaned.substring(2, 4), 16);
        int lowAddressByte = Integer.parseInt(cleaned.substring(4, 6), 16);
        int highAddressByte = Integer.parseInt(cleaned.substring(6, 8), 16);
        return Optional.of(new ParsedGameSharkCode((highAddressByte << 8) | lowAddressByte, value));
    }

    static List<String> SelectMatchingFiles(Map<String, String> filesByName, List<String> candidateNames) {
        if (filesByName == null || filesByName.isEmpty() || candidateNames == null || candidateNames.isEmpty()) {
            return List.of();
        }

        Map<String, String> filesByLowerName = new LinkedHashMap<>();
        for (String fileName : filesByName.keySet()) {
            filesByLowerName.put(fileName.toLowerCase(Locale.ROOT), fileName);
        }

        LinkedHashSet<String> matches = new LinkedHashSet<>();
        for (String candidateName : candidateNames) {
            AddMatchingFile(matches, filesByLowerName, candidateName + " (GameShark).cht");
        }
        for (String candidateName : candidateNames) {
            AddMatchingFile(matches, filesByLowerName, candidateName + ".cht");
        }
        return List.copyOf(matches);
    }

    private static void AddMatchingFile(LinkedHashSet<String> matches, Map<String, String> filesByLowerName, String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return;
        }

        String matchedFile = filesByLowerName.get(requestedName.toLowerCase(Locale.ROOT));
        if (matchedFile != null) {
            matches.add(matchedFile);
        }
    }

    private static String LoadCheatFile(String branch, String playlistName, String fileName) {
        String lookupKey = branch + "|" + playlistName + "|" + fileName;
        Optional<String> cachedContent = cheatFileCache.get(lookupKey);
        if (cachedContent != null) {
            return cachedContent.orElse("");
        }

        Path cachePath = cacheDirectory.resolve(KeyedPropertiesStore.Hash(lookupKey) + ".cht");
        String cachedText = LoadCachedText(cachePath);
        if (!cachedText.isBlank()) {
            cheatFileCache.put(lookupKey, Optional.of(cachedText));
            return cachedText;
        }

        String url = rawRepositoryBaseUrl + "/" + EncodePathPart(branch) + "/cht/"
                + EncodePathPart(playlistName) + "/" + EncodePathPart(fileName);
        String downloadedText = DownloadText(url);
        Optional<String> resolvedContent = downloadedText == null || downloadedText.isBlank()
                ? Optional.empty()
                : Optional.of(downloadedText);
        resolvedContent.ifPresent(content -> WriteCache(cachePath, content));
        cheatFileCache.put(lookupKey, resolvedContent);
        return resolvedContent.orElse("");
    }

    private static String LoadDefaultBranch() {
        String cachedDefaultBranch = defaultBranch;
        if (cachedDefaultBranch != null && !cachedDefaultBranch.isBlank()) {
            return cachedDefaultBranch;
        }

        synchronized (LibretroCheatProvider.class) {
            if (defaultBranch != null && !defaultBranch.isBlank()) {
                return defaultBranch;
            }

            Path cachePath = cacheDirectory.resolve("repository-default-branch.txt");
            String cachedBranchText = LoadCachedText(cachePath).trim();
            if (!cachedBranchText.isBlank()) {
                defaultBranch = cachedBranchText;
                return defaultBranch;
            }

            String repositoryJson = DownloadText(repositoryApiBaseUrl);
            Matcher matcher = Pattern.compile("\"default_branch\"\\s*:\\s*\"([^\"]+)\"").matcher(repositoryJson);
            if (matcher.find()) {
                defaultBranch = matcher.group(1).trim();
            } else {
                defaultBranch = "master";
            }

            WriteCache(cachePath, defaultBranch);
            return defaultBranch;
        }
    }

    private static String DownloadText(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "GameDuck/1.0")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200 ? response.body() : "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        } catch (IOException exception) {
            return "";
        }
    }

    private static String LoadCachedText(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            return "";
        }
        return "";
    }

    private static void WriteCache(Path path, String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            // Continue without a disk cache when the cache path is unavailable.
        }
    }

    private static String BuildLookupCacheKey(EmulatorGame game) {
        String gameKey = CheatStore.BuildGameKey(game);
        if (gameKey.isBlank()) {
            return "";
        }
        return gameKey + "|" + String.join("|", BuildPlaylistOrder(game)) + "|" + String.join("|", BuildCandidateFileNames(game));
    }

    private static List<String> BuildPlaylistOrder(EmulatorGame game) {
        String lowerSourcePath = game == null || game.sourcePath() == null ? "" : game.sourcePath().toLowerCase(Locale.ROOT);
        boolean preferColorDatabase = game != null && (IsGbColorVariant(game)
                || lowerSourcePath.endsWith(".gbc")
                || lowerSourcePath.endsWith(".cgb"));

        List<String> playlists = new ArrayList<>(2);
        if (preferColorDatabase) {
            playlists.add("Nintendo - Game Boy Color");
            playlists.add("Nintendo - Game Boy");
        } else {
            playlists.add("Nintendo - Game Boy");
            playlists.add("Nintendo - Game Boy Color");
        }
        return List.copyOf(playlists);
    }

    private static boolean IsGbColorVariant(EmulatorGame game) {
        return game != null
                && GBRom.systemId.equals(game.systemId())
                && GBRom.variantIdGbc.equals(game.systemVariantId());
    }

    private static List<String> BuildCandidateNames(EmulatorGame game) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        GameMetadataStore.GetLibretroTitle(game).ifPresent(title -> AddCandidateName(names, title));
        if (game != null) {
            AddCandidateName(names, game.sourceName());
            AddCandidateName(names, game.displayName());
            AddCandidateName(names, StripTrailingTags(game.sourceName()));
            AddCandidateName(names, StripTrailingTags(game.displayName()));
            AddCandidateName(names, game.headerTitle());
        }
        return List.copyOf(names);
    }

    private static List<String> BuildCandidateFileNames(EmulatorGame game) {
        LinkedHashSet<String> fileNames = new LinkedHashSet<>();
        List<String> candidateNames = BuildCandidateNames(game);
        for (String candidateName : candidateNames) {
            fileNames.add(candidateName + " (GameShark).cht");
        }
        for (String candidateName : candidateNames) {
            fileNames.add(candidateName + ".cht");
        }
        return List.copyOf(fileNames);
    }

    private static void AddCandidateName(LinkedHashSet<String> names, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        String cleaned = value
                .replaceAll("\\s*\\[[0-9a-fA-F]{8}]$", "")
                .trim()
                .replaceAll("\\s+", " ");
        if (!cleaned.isBlank()) {
            names.add(cleaned);
        }
    }

    private static String StripTrailingTags(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("\\s*(\\[[^\\]]*]|\\([^)]*\\))+$", "").trim();
    }

    private static String StripCheatFileSuffix(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }

        String baseName = fileName.replaceFirst("\\.cht$", "");
        return baseName.replaceFirst("\\s*\\((GameShark|Game Genie|Xploder)\\)$", "").trim();
    }

    private static String EncodePathPart(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
