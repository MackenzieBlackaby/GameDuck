package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.Misc.ROM;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * Loads supplemental ROM metadata from libretro's official DAT repository.
 */
public final class LibretroMetadataProvider {

    public record LibretroMetadata(String publisher, String releaseYear, String databaseName) {
    }

    private record FieldData(Map<String, String> valuesByCrc) {
    }

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(4))
            .build();
    private static final Path cacheDirectory = Path.of("cache", "libretro-metadata");
    private static final String baseUrl = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat";
    private static final String libretroDatabaseBaseUrl = "https://db.libretro.com";
    private static final Map<String, FieldData> fieldCache = new ConcurrentHashMap<>();
    private static final Map<String, String> releaseYearFallbackCache = new ConcurrentHashMap<>();

    private LibretroMetadataProvider() {
    }

    /**
     * Resolves publisher and release-year metadata for a ROM using libretro's
     * official DAT files.
     *
     * @param rom ROM to inspect
     * @return resolved libretro metadata when found
     */
    public static Optional<LibretroMetadata> FindMetadata(ROM rom) {
        if (rom == null) {
            return Optional.empty();
        }

        String crc = ComputeCrc32(rom.ToByteArray());
        for (String databaseName : BuildDatabaseOrder(rom)) {
            String publisher = LookupField(databaseName, "publisher", crc).orElse("");
            String releaseYear = LookupField(databaseName, "releaseyear", crc).orElse("");
            if (!publisher.isBlank() || !releaseYear.isBlank()) {
                if (releaseYear.isBlank()) {
                    releaseYear = LookupReleaseYearFallback(databaseName, rom).orElse("");
                }
                return Optional.of(new LibretroMetadata(
                        BlankToNull(publisher),
                        BlankToNull(releaseYear),
                        databaseName));
            }
        }

        for (String databaseName : BuildDatabaseOrder(rom)) {
            String fallbackReleaseYear = LookupReleaseYearFallback(databaseName, rom).orElse("");
            if (!fallbackReleaseYear.isBlank()) {
                return Optional.of(new LibretroMetadata(null, fallbackReleaseYear, databaseName));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> LookupField(String databaseName, String fieldName, String crc) {
        if (databaseName == null || databaseName.isBlank() || fieldName == null || fieldName.isBlank()
                || crc == null || crc.isBlank()) {
            return Optional.empty();
        }

        FieldData data = fieldCache.computeIfAbsent(databaseName + "|" + fieldName,
                key -> LoadFieldMap(databaseName, fieldName));
        String value = data.valuesByCrc().get(crc.toUpperCase());
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static FieldData LoadFieldMap(String databaseName, String fieldName) {
        String content = LoadDatContent(databaseName, fieldName);
        if (content == null || content.isBlank()) {
            return new FieldData(Map.of());
        }

        Pattern pattern = Pattern.compile(fieldName + "\\s+\"([^\"]*)\"\\s+rom\\s*\\(\\s*crc\\s+([0-9A-Fa-f]{8})\\s*\\)");
        Matcher matcher = pattern.matcher(content);
        Map<String, String> values = new ConcurrentHashMap<>();
        while (matcher.find()) {
            values.put(matcher.group(2).toUpperCase(), matcher.group(1).trim());
        }
        return new FieldData(Map.copyOf(values));
    }

    private static String LoadDatContent(String databaseName, String fieldName) {
        Path cachePath = CachePath(databaseName, fieldName);
        try {
            if (Files.isRegularFile(cachePath)) {
                return Files.readString(cachePath);
            }
        } catch (IOException exception) {
            // Fall back to a fresh download when the cache cannot be read.
        }

        String url = BuildMetadataUrl(databaseName, fieldName);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", "GameDuck/1.0")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return "";
            }

            Files.createDirectories(cacheDirectory);
            Files.writeString(cachePath, response.body(), StandardCharsets.UTF_8);
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        } catch (IOException exception) {
            return "";
        }
    }

    private static String BuildMetadataUrl(String databaseName, String fieldName) {
        String encodedDatabaseName = URLEncoder.encode(databaseName, StandardCharsets.UTF_8).replace("+", "%20");
        return baseUrl + "/" + fieldName + "/" + encodedDatabaseName + ".dat";
    }

    private static Path CachePath(String databaseName, String fieldName) {
        String safeDatabaseName = databaseName
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replace(' ', '_');
        return cacheDirectory.resolve(fieldName + "-" + safeDatabaseName + ".dat");
    }

    private static List<String> BuildDatabaseOrder(ROM rom) {
        String lowerSourcePath = rom.GetSourcePath() == null ? "" : rom.GetSourcePath().toLowerCase();
        boolean preferColorDatabase = rom.IsCgbOnly()
                || rom.IsCgbCompatible()
                || lowerSourcePath.endsWith(".gbc")
                || lowerSourcePath.endsWith(".cgb");

        List<String> databases = new ArrayList<>(2);
        if (preferColorDatabase) {
            databases.add("Nintendo - Game Boy Color");
            databases.add("Nintendo - Game Boy");
        } else {
            databases.add("Nintendo - Game Boy");
            databases.add("Nintendo - Game Boy Color");
        }
        return List.copyOf(databases);
    }

    private static String ComputeCrc32(byte[] romBytes) {
        CRC32 crc32 = new CRC32();
        crc32.update(romBytes);
        return String.format("%08X", crc32.getValue());
    }

    private static Optional<String> LookupReleaseYearFallback(String databaseName, ROM rom) {
        if (databaseName == null || databaseName.isBlank() || rom == null) {
            return Optional.empty();
        }

        for (String candidate : BuildTitleCandidates(rom)) {
            String cacheKey = databaseName + "|" + candidate;
            String cachedValue = releaseYearFallbackCache.get(cacheKey);
            if (cachedValue != null) {
                return cachedValue.isBlank() ? Optional.empty() : Optional.of(cachedValue);
            }

            String releaseYear = ResolveReleaseYearFromLibretroPage(databaseName, candidate);
            releaseYearFallbackCache.put(cacheKey, releaseYear == null ? "" : releaseYear);
            if (releaseYear != null && !releaseYear.isBlank()) {
                return Optional.of(releaseYear);
            }
        }

        return Optional.empty();
    }

    private static List<String> BuildTitleCandidates(ROM rom) {
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        AddTitleCandidate(titles, rom.GetSourceName());
        AddTitleCandidate(titles, rom.GetName());
        AddTitleCandidate(titles, rom.GetHeaderTitle());
        GameMetadataStore.GetLibretroTitle(rom).ifPresent(title -> AddTitleCandidate(titles, title));
        return List.copyOf(titles);
    }

    private static void AddTitleCandidate(LinkedHashSet<String> titles, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        String rawTitle = value
                .replaceAll("\\s*\\[[0-9a-fA-F]{8}]$", "")
                .trim();
        if (!rawTitle.isBlank()) {
            titles.add(rawTitle);
        }

        String stripped = rawTitle
                .replaceAll("\\s*(\\[[^\\]]*]|\\([^)]*\\))+$", "")
                .trim();
        if (!stripped.isBlank()) {
            titles.add(stripped);
        }
    }

    private static String ResolveReleaseYearFromLibretroPage(String databaseName, String title) {
        if (databaseName == null || databaseName.isBlank() || title == null || title.isBlank()) {
            return "";
        }

        String encodedDatabaseName = URLEncoder.encode(databaseName, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8).replace("+", "%20");
        String url = libretroDatabaseBaseUrl + "/" + encodedDatabaseName + "/" + encodedTitle + ".html";
        String response = DownloadText(url);
        if (response == null || response.isBlank()) {
            return "";
        }

        Matcher matcher = Pattern.compile("Release\\s+Year:</th>\\s*<td>(\\d{4})</td>").matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String DownloadText(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(6))
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

    private static String BlankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

