package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.Platform.BasicEmulatorMedia;
import com.blackaby.Backend.Platform.EmulatorGame;
import com.blackaby.Backend.Platform.EmulatorMedia;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Stores playable ROM images inside GameDuck's managed library.
 */
public final class GameLibraryStore {

    public record LibraryEntry(String key, Path romPath, String systemId, String systemVariantId,
                               String systemVariantLabel, String sourcePath, String sourceName, String displayName,
                               List<String> patchNames, List<String> patchSourcePaths, String headerTitle,
                               boolean cgbCompatible, boolean cgbOnly, long addedAtMillis, long lastPlayedMillis,
                               boolean favourite) implements EmulatorGame {
        public LibraryEntry {
            systemId = systemId == null || systemId.isBlank() ? GBRom.systemId : systemId.trim();
            systemVariantId = systemVariantId == null || systemVariantId.isBlank()
                    ? (cgbCompatible ? GBRom.variantIdGbc : GBRom.variantIdGb)
                    : systemVariantId.trim();
            systemVariantLabel = systemVariantLabel == null || systemVariantLabel.isBlank()
                    ? (cgbCompatible ? "GBC" : "GB")
                    : systemVariantLabel.trim();
            patchNames = List.copyOf(patchNames == null ? List.of() : patchNames);
            patchSourcePaths = List.copyOf(patchSourcePaths == null ? List.of() : patchSourcePaths);
        }

        public LibraryEntry(String key, Path romPath, String sourcePath, String sourceName, String displayName,
                            List<String> patchNames, List<String> patchSourcePaths, String headerTitle,
                            boolean cgbCompatible, boolean cgbOnly, long addedAtMillis, long lastPlayedMillis,
                            boolean favourite) {
            this(
                    key,
                    romPath,
                    GBRom.systemId,
                    cgbCompatible ? GBRom.variantIdGbc : GBRom.variantIdGb,
                    cgbCompatible ? "GBC" : "GB",
                    sourcePath,
                    sourceName,
                    displayName,
                    patchNames,
                    patchSourcePaths,
                    headerTitle,
                    cgbCompatible,
                    cgbOnly,
                    addedAtMillis,
                    lastPlayedMillis,
                    favourite);
        }

        public SaveFileManager.SaveIdentity SaveIdentity() {
            return new SaveFileManager.SaveIdentity(
                    systemId,
                    systemVariantId,
                    systemVariantLabel,
                    sourcePath,
                    sourceName,
                    displayName,
                    patchNames,
                    true);
        }

        public GameArtProvider.GameArtDescriptor ArtDescriptor() {
            return new GameArtProvider.GameArtDescriptor(sourcePath, sourceName, displayName, headerTitle);
        }

        public GBRom LoadRom() throws IOException {
            byte[] romBytes = Files.readAllBytes(romPath);
            return GBRom.FromBytes(sourcePath, romBytes, displayName, patchNames, patchSourcePaths);
        }

        public EmulatorMedia LoadMedia() throws IOException {
            return new BasicEmulatorMedia(
                    systemId(),
                    systemVariantId(),
                    systemVariantLabel(),
                    sourcePath,
                    sourceName,
                    displayName,
                    headerTitle,
                    patchNames,
                    patchSourcePaths,
                    true,
                    Files.readAllBytes(romPath));
        }
    }

    private static final String romPathSuffix = ".rom_path";
    private static final String systemIdSuffix = ".system_id";
    private static final String systemVariantIdSuffix = ".system_variant_id";
    private static final String systemVariantLabelSuffix = ".system_variant_label";
    private static final String sourcePathSuffix = ".source_path";
    private static final String sourceNameSuffix = ".source_name";
    private static final String displayNameSuffix = ".display_name";
    private static final String patchNameCountSuffix = ".patch_name_count";
    private static final String patchSourceCountSuffix = ".patch_source_count";
    private static final String patchNamePrefix = ".patch_name.";
    private static final String patchSourcePrefix = ".patch_source.";
    private static final String headerTitleSuffix = ".header_title";
    private static final String cgbCompatibleSuffix = ".cgb_compatible";
    private static final String cgbOnlySuffix = ".cgb_only";
    private static final String addedAtSuffix = ".added_at";
    private static final String lastPlayedSuffix = ".last_played";
    private static final String favouriteSuffix = ".favourite";
    private static final String contentHashSuffix = ".content_hash";

    private static final Comparator<LibraryEntry> entrySortOrder = Comparator
            .comparingLong(LibraryEntry::lastPlayedMillis)
            .reversed()
            .thenComparing(entry -> SaveFileManager.BuildFallbackBaseName(entry.SaveIdentity()));
    private static final Comparator<EntrySnapshot> duplicatePreference = Comparator
            .comparing((EntrySnapshot snapshot) -> IsManagedLibraryPath(snapshot.entry().sourcePath()))
            .thenComparing((EntrySnapshot snapshot) -> snapshot.entry().favourite(), Comparator.reverseOrder())
            .thenComparingLong(snapshot -> -snapshot.entry().lastPlayedMillis())
            .thenComparingLong(snapshot -> PositiveOrMax(snapshot.entry().addedAtMillis()))
            .thenComparing(snapshot -> SaveFileManager.BuildFallbackBaseName(snapshot.entry().SaveIdentity()),
                    String.CASE_INSENSITIVE_ORDER);

    private static final Store store = new Store();

    public record RefreshResult(int scannedRomCount, int preservedEntryCount, int rebuiltEntryCount,
                                int removedEntryCount, boolean restoredFromPortableMirror) {
    }

    private GameLibraryStore() {
    }

    /**
     * Copies a playable ROM image into GameDuck's managed library and updates its metadata.
     *
     * @param rom playable ROM image
     * @return persisted library entry
     */
    public static synchronized LibraryEntry RememberGame(GBRom rom) {
        if (rom == null) {
            throw new IllegalArgumentException("A ROM is required.");
        }

        store.EnsureLoaded();
        CleanupDuplicateEntriesInternal();

        byte[] romBytes = rom.ToByteArray();
        String contentHash = KeyedPropertiesStore.Hash(romBytes);
        long now = System.currentTimeMillis();
        LibraryEntry existingEntry = FindEntryByContentHash(contentHash);
        if (existingEntry != null) {
            EntryProperties existingProperties = new EntryProperties(existingEntry.key());
            if (!Files.isRegularFile(existingEntry.romPath())) {
                Path restoredPath = RomStorageDirectory().resolve(BuildStoredFilename(rom, existingEntry.key()));
                try {
                    Files.createDirectories(restoredPath.getParent());
                    Files.write(restoredPath, romBytes);
                } catch (IOException exception) {
                    throw new IllegalStateException("Unable to restore the ROM in the managed library.", exception);
                }
                existingProperties.SetString(romPathSuffix, restoredPath.toString());
            }
            existingProperties.TouchFromExistingRom(rom, now, contentHash);
            PersistStores();
            return existingProperties.Read();
        }

        String key = BuildEntryKey(rom);
        EntryProperties entry = new EntryProperties(key);
        Path storedPath = RomStorageDirectory().resolve(BuildStoredFilename(rom, key));

        try {
            Files.createDirectories(storedPath.getParent());
            Files.write(storedPath, romBytes);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store the ROM in the managed library.", exception);
        }

        entry.WriteMetadata(rom, storedPath, now, contentHash);
        PersistStores();
        return entry.Read();
    }

    /**
     * Repairs the managed library on startup by restoring mirrored metadata when
     * available, then rescanning the stored ROM folder.
     *
     * @return refresh summary
     */
    public static synchronized RefreshResult RecoverLibrary() {
        boolean restoredFromPortableMirror = RestorePortableMirrorIfNeeded();
        RefreshResult refreshResult = RefreshLibraryInternal(restoredFromPortableMirror);
        MirrorPrimaryMetadataToPortableStore();
        return refreshResult;
    }

    /**
     * Rescans the managed ROM folder and rebuilds the library metadata from disk.
     *
     * @return refresh summary
     */
    public static synchronized RefreshResult RefreshLibrary() {
        return RefreshLibraryInternal(false);
    }

    /**
     * Returns all managed library entries.
     *
     * @return library entries
     */
    public static synchronized List<LibraryEntry> GetEntries() {
        store.EnsureLoaded();
        CleanupDuplicateEntriesInternal();
        return store.StoredKeys().stream()
                .map(GameLibraryStore::ReadEntry)
                .filter(entry -> entry != null && Files.isRegularFile(entry.romPath()))
                .sorted(entrySortOrder)
                .toList();
    }

    /**
     * Returns the most recently played library entries up to the requested limit.
     *
     * @param limit maximum number of entries to return
     * @return recent entries ordered newest first
     */
    public static synchronized List<LibraryEntry> GetRecentEntries(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return GetEntries().stream()
                .filter(entry -> entry.lastPlayedMillis() > 0L)
                .limit(limit)
                .toList();
    }

    public static synchronized void ResetForTests() {
        store.ResetForTests();
    }

    /**
     * Marks a managed library entry as favourite or not favourite.
     *
     * @param key entry key
     * @param favourite whether the entry should be favourited
     */
    public static synchronized void SetFavourite(String key, boolean favourite) {
        if (key == null || key.isBlank()) {
            return;
        }

        store.EnsureLoaded();
        CleanupDuplicateEntriesInternal();
        EntryProperties entry = new EntryProperties(key);
        if (!entry.Exists()) {
            return;
        }

        entry.SetBoolean(favouriteSuffix, favourite);
        PersistStores();
    }

    /**
     * Deletes a managed library entry and its stored ROM image.
     *
     * @param key entry key
     * @throws IOException when the stored ROM cannot be removed
     */
    public static synchronized void DeleteEntry(String key) throws IOException {
        if (key == null || key.isBlank()) {
            return;
        }

        store.EnsureLoaded();
        CleanupDuplicateEntriesInternal();
        LibraryEntry entry = ReadEntry(key);
        if (entry == null) {
            return;
        }

        Files.deleteIfExists(entry.romPath());
        new EntryProperties(key).RemoveAll();
        PersistStores();
    }

    /**
     * Returns whether a managed library entry is favourited.
     *
     * @param key entry key
     * @return {@code true} when the entry is marked as favourite
     */
    public static synchronized boolean IsFavourite(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        store.EnsureLoaded();
        CleanupDuplicateEntriesInternal();
        return new EntryProperties(key).GetBoolean(favouriteSuffix, false);
    }

    /**
     * Clears the recent-play history for all managed library entries.
     */
    public static synchronized void ClearRecentHistory() {
        store.EnsureLoaded();
        CleanupDuplicateEntriesInternal();
        for (String key : store.StoredKeys()) {
            EntryProperties entry = new EntryProperties(key);
            if (entry.Exists()) {
                entry.SetLong(lastPlayedSuffix, 0L);
            }
        }
        PersistStores();
    }

    public static Path LibraryDirectoryPath() {
        Path romDirectory = RomStorageDirectory();
        Path parent = romDirectory.getParent();
        return parent == null ? romDirectory : parent;
    }

    public static Path MetadataFilePath() {
        return store.MetadataPath();
    }

    public static Path PortableMetadataFilePath() {
        return PortableMetadataPath();
    }

    private static RefreshResult RefreshLibraryInternal(boolean restoredFromPortableMirror) {
        store.EnsureLoaded();

        List<EntrySnapshot> existingSnapshots = BuildSnapshots();
        List<EntrySnapshot> portableSnapshots = LoadSnapshotsFromMetadataFile(PortableMetadataPath());
        List<EntrySnapshot> knownSnapshots = MergeSnapshots(existingSnapshots, portableSnapshots);
        Map<Path, List<EntrySnapshot>> snapshotsByPath = IndexSnapshotsByPath(knownSnapshots);
        Map<String, List<EntrySnapshot>> snapshotsByHash = IndexSnapshotsByHash(knownSnapshots);

        List<ScannedRom> scannedRoms = ScanStoredRoms();
        Map<String, List<ScannedRom>> scannedByHash = new HashMap<>();
        for (ScannedRom scannedRom : scannedRoms) {
            scannedByHash.computeIfAbsent(scannedRom.contentHash(), ignored -> new ArrayList<>()).add(scannedRom);
        }

        long now = System.currentTimeMillis();
        Set<String> reusedKeys = new LinkedHashSet<>();
        Set<String> previousKeys = new LinkedHashSet<>();
        for (EntrySnapshot snapshot : knownSnapshots) {
            previousKeys.add(snapshot.entry().key());
        }

        store.ClearEntries();
        int preservedEntryCount = 0;
        int rebuiltEntryCount = 0;
        for (List<ScannedRom> duplicateGroup : scannedByHash.values()) {
            duplicateGroup.sort(Comparator.comparing(scannedRom -> scannedRom.path().toString(), String.CASE_INSENSITIVE_ORDER));
            ScannedRom canonicalRom = SelectCanonicalRom(duplicateGroup, snapshotsByPath);
            EntrySnapshot candidateSnapshot = FindPreferredSnapshot(duplicateGroup, snapshotsByPath, snapshotsByHash);
            LibraryEntry rebuiltEntry = BuildRefreshedEntry(candidateSnapshot, canonicalRom, now);
            reusedKeys.add(rebuiltEntry.key());
            if (candidateSnapshot == null) {
                rebuiltEntryCount++;
            } else {
                preservedEntryCount++;
            }
            WriteEntry(rebuiltEntry, canonicalRom.contentHash());
        }

        PersistStores();
        DeleteOrphanedStoredRoms();
        ManagedGameRegistry.RefreshFromLibraryEntries(GetEntries());

        int removedEntryCount = 0;
        for (String previousKey : previousKeys) {
            if (!reusedKeys.contains(previousKey)) {
                removedEntryCount++;
            }
        }

        return new RefreshResult(scannedRoms.size(), preservedEntryCount, rebuiltEntryCount,
                removedEntryCount, restoredFromPortableMirror);
    }

    private static int CleanupDuplicateEntriesInternal() {
        List<EntrySnapshot> snapshots = BuildSnapshots();
        Map<String, List<EntrySnapshot>> snapshotsByHash = new HashMap<>();
        for (EntrySnapshot snapshot : snapshots) {
            if (snapshot.contentHash().isBlank()) {
                continue;
            }
            snapshotsByHash.computeIfAbsent(snapshot.contentHash(), ignored -> new ArrayList<>()).add(snapshot);
        }

        int removedCount = 0;
        boolean changed = false;
        for (List<EntrySnapshot> group : snapshotsByHash.values()) {
            if (group.size() < 2) {
                continue;
            }

            group.sort(duplicatePreference);
            EntrySnapshot canonical = group.get(0);
            MergeDuplicateMetadata(canonical, group);
            MigrateNewestSaveBundle(group, canonical.entry().SaveIdentity());
            for (int index = 1; index < group.size(); index++) {
                EntrySnapshot duplicate = group.get(index);
                DeleteDuplicateRomFile(canonical, duplicate);
                new EntryProperties(duplicate.entry().key()).RemoveAll();
                removedCount++;
                changed = true;
            }
        }

        if (DeleteOrphanedStoredRoms()) {
            changed = true;
        }

        if (changed) {
            PersistStores();
        }
        return removedCount;
    }

    private static void WriteEntry(LibraryEntry entry, String contentHash) {
        GBRom rom = GBRom.FromBytes(
                entry.sourcePath() == null || entry.sourcePath().isBlank()
                        ? entry.romPath().toString()
                        : entry.sourcePath(),
                ReadRomBytes(entry.romPath()),
                entry.displayName(),
                entry.patchNames(),
                entry.patchSourcePaths());
        new EntryProperties(entry.key()).WriteMetadata(
                rom,
                entry.romPath(),
                entry.addedAtMillis(),
                entry.lastPlayedMillis(),
                entry.favourite(),
                contentHash);
    }

    private static byte[] ReadRomBytes(Path romPath) {
        try {
            return Files.readAllBytes(romPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the managed ROM library.", exception);
        }
    }

    private static List<ScannedRom> ScanStoredRoms() {
        Path storageDirectory = RomStorageDirectory();
        if (!Files.isDirectory(storageDirectory)) {
            return List.of();
        }

        List<ScannedRom> scannedRoms = new ArrayList<>();
        try (var paths = Files.list(storageDirectory)) {
            for (Path path : paths.toList()) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                if (".gitkeep".equalsIgnoreCase(path.getFileName().toString())) {
                    continue;
                }

                try {
                    byte[] romBytes = Files.readAllBytes(path);
                    GBRom rom = new GBRom(path.toString());
                    scannedRoms.add(new ScannedRom(path, romBytes, KeyedPropertiesStore.Hash(romBytes), rom));
                } catch (IOException | IllegalArgumentException exception) {
                    // Ignore unreadable or invalid files in the managed ROM folder.
                }
            }
        } catch (IOException exception) {
            return List.of();
        }
        return scannedRoms;
    }

    private static ScannedRom SelectCanonicalRom(List<ScannedRom> duplicateGroup, Map<Path, List<EntrySnapshot>> snapshotsByPath) {
        for (ScannedRom scannedRom : duplicateGroup) {
            List<EntrySnapshot> matchingSnapshots = snapshotsByPath.get(NormalisePath(scannedRom.path()));
            if (matchingSnapshots != null && !matchingSnapshots.isEmpty()) {
                return scannedRom;
            }
        }
        return duplicateGroup.get(0);
    }

    private static EntrySnapshot FindPreferredSnapshot(List<ScannedRom> duplicateGroup, Map<Path, List<EntrySnapshot>> snapshotsByPath,
                                                       Map<String, List<EntrySnapshot>> snapshotsByHash) {
        List<EntrySnapshot> candidates = new ArrayList<>();
        for (ScannedRom scannedRom : duplicateGroup) {
            List<EntrySnapshot> pathMatches = snapshotsByPath.get(NormalisePath(scannedRom.path()));
            if (pathMatches != null) {
                candidates.addAll(pathMatches);
            }
        }

        String contentHash = duplicateGroup.get(0).contentHash();
        List<EntrySnapshot> hashMatches = snapshotsByHash.get(contentHash);
        if (hashMatches != null) {
            candidates.addAll(hashMatches);
        }

        return candidates.stream()
                .distinct()
                .min(duplicatePreference)
                .orElse(null);
    }

    private static LibraryEntry BuildRefreshedEntry(EntrySnapshot candidateSnapshot, ScannedRom scannedRom, long now) {
        GBRom scannedMetadata = scannedRom.rom();
        if (candidateSnapshot == null) {
            String key = BuildEntryKey(scannedMetadata);
            return new LibraryEntry(
                    key,
                    scannedRom.path(),
                    scannedMetadata.GetSourcePath(),
                    scannedMetadata.GetSourceName(),
                    scannedMetadata.GetName(),
                    scannedMetadata.GetPatchNames(),
                    scannedMetadata.GetPatchSourcePaths(),
                    scannedMetadata.GetHeaderTitle(),
                    scannedMetadata.IsCgbCompatible(),
                    scannedMetadata.IsCgbOnly(),
                    now,
                    0L,
                    false);
        }

        LibraryEntry entry = candidateSnapshot.entry();
        return new LibraryEntry(
                entry.key(),
                scannedRom.path(),
                entry.systemId(),
                entry.systemVariantId(),
                entry.systemVariantLabel(),
                entry.sourcePath(),
                entry.sourceName(),
                entry.displayName(),
                entry.patchNames(),
                entry.patchSourcePaths(),
                entry.headerTitle() == null || entry.headerTitle().isBlank()
                        ? scannedMetadata.GetHeaderTitle()
                        : entry.headerTitle(),
                scannedMetadata.IsCgbCompatible(),
                scannedMetadata.IsCgbOnly(),
                entry.addedAtMillis() > 0L ? entry.addedAtMillis() : now,
                Math.max(0L, entry.lastPlayedMillis()),
                entry.favourite());
    }

    private static List<EntrySnapshot> MergeSnapshots(List<EntrySnapshot> primarySnapshots, List<EntrySnapshot> portableSnapshots) {
        Map<String, EntrySnapshot> byKey = new HashMap<>();
        for (EntrySnapshot snapshot : portableSnapshots) {
            byKey.put(snapshot.entry().key(), snapshot);
        }
        for (EntrySnapshot snapshot : primarySnapshots) {
            byKey.put(snapshot.entry().key(), snapshot);
        }
        return List.copyOf(byKey.values());
    }

    private static Map<Path, List<EntrySnapshot>> IndexSnapshotsByPath(List<EntrySnapshot> snapshots) {
        Map<Path, List<EntrySnapshot>> snapshotsByPath = new HashMap<>();
        for (EntrySnapshot snapshot : snapshots) {
            Path normalisedPath = NormalisePath(snapshot.entry().romPath());
            if (normalisedPath == null) {
                continue;
            }
            snapshotsByPath.computeIfAbsent(normalisedPath, ignored -> new ArrayList<>()).add(snapshot);
        }
        return snapshotsByPath;
    }

    private static Map<String, List<EntrySnapshot>> IndexSnapshotsByHash(List<EntrySnapshot> snapshots) {
        Map<String, List<EntrySnapshot>> snapshotsByHash = new HashMap<>();
        for (EntrySnapshot snapshot : snapshots) {
            if (snapshot.contentHash() == null || snapshot.contentHash().isBlank()) {
                continue;
            }
            snapshotsByHash.computeIfAbsent(snapshot.contentHash(), ignored -> new ArrayList<>()).add(snapshot);
        }
        return snapshotsByHash;
    }

    private static List<EntrySnapshot> LoadSnapshotsFromMetadataFile(Path metadataPath) {
        if (metadataPath == null || !Files.isRegularFile(metadataPath)) {
            return List.of();
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(metadataPath)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            return List.of();
        }

        List<EntrySnapshot> snapshots = new ArrayList<>();
        for (String key : ExtractStoredKeys(properties)) {
            LibraryEntry entry = ReadEntry(properties, key);
            if (entry == null) {
                continue;
            }
            snapshots.add(new EntrySnapshot(entry, ResolveContentHash(properties, key, entry.romPath())));
        }
        return snapshots;
    }

    private static Set<String> ExtractStoredKeys(Properties properties) {
        Set<String> keys = new LinkedHashSet<>();
        for (String propertyName : properties.stringPropertyNames()) {
            if (!propertyName.startsWith("entry.") || !propertyName.endsWith(sourceNameSuffix)) {
                continue;
            }

            String key = propertyName.substring("entry.".length(), propertyName.length() - sourceNameSuffix.length());
            if (!key.isBlank()) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static LibraryEntry ReadEntry(Properties properties, String key) {
        String prefix = "entry." + key;
        String romPathValue = properties.getProperty(prefix + romPathSuffix, "");
        String sourceName = properties.getProperty(prefix + sourceNameSuffix, "");
        String displayName = properties.getProperty(prefix + displayNameSuffix, "");
        if (romPathValue.isBlank() || (sourceName.isBlank() && displayName.isBlank())) {
            return null;
        }

        Path romPath;
        try {
            romPath = Path.of(romPathValue);
        } catch (RuntimeException exception) {
            return null;
        }

        String sourcePath = properties.getProperty(prefix + sourcePathSuffix, "");
        boolean cgbCompatible = Boolean.parseBoolean(properties.getProperty(prefix + cgbCompatibleSuffix, "false"));
        boolean cgbOnly = Boolean.parseBoolean(properties.getProperty(prefix + cgbOnlySuffix, "false"));
        return new LibraryEntry(
                key,
                romPath,
                properties.getProperty(prefix + systemIdSuffix, GBRom.systemId),
                properties.getProperty(prefix + systemVariantIdSuffix,
                        cgbCompatible ? GBRom.variantIdGbc : GBRom.variantIdGb),
                properties.getProperty(prefix + systemVariantLabelSuffix, cgbCompatible ? "GBC" : "GB"),
                sourcePath,
                sourceName,
                displayName,
                ReadIndexedList(properties, prefix, patchNamePrefix, patchNameCountSuffix),
                ReadIndexedList(properties, prefix, patchSourcePrefix, patchSourceCountSuffix),
                properties.getProperty(prefix + headerTitleSuffix, ""),
                cgbCompatible,
                cgbOnly,
                KeyedPropertiesStore.ParseLong(properties.getProperty(prefix + addedAtSuffix, ""), 0L),
                KeyedPropertiesStore.ParseLong(properties.getProperty(prefix + lastPlayedSuffix, ""), 0L),
                Boolean.parseBoolean(properties.getProperty(prefix + favouriteSuffix, "false")));
    }

    private static List<String> ReadIndexedList(Properties properties, String prefix, String itemPrefix, String countSuffix) {
        int count = KeyedPropertiesStore.ParseInt(properties.getProperty(prefix + countSuffix, ""), 0);
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(properties.getProperty(prefix + itemPrefix + index, ""));
        }
        return List.copyOf(values);
    }

    private static String ResolveContentHash(Properties properties, String key, Path romPath) {
        String storedHash = properties.getProperty("entry." + key + contentHashSuffix, "");
        if (!storedHash.isBlank()) {
            return storedHash;
        }
        if (romPath == null || !Files.isRegularFile(romPath)) {
            return "";
        }

        try {
            return KeyedPropertiesStore.Hash(Files.readAllBytes(romPath));
        } catch (IOException exception) {
            return "";
        }
    }

    private static Path NormalisePath(Path path) {
        if (path == null) {
            return null;
        }

        try {
            return path.toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean RestorePortableMirrorIfNeeded() {
        Path primaryPath = store.MetadataPath();
        Path portablePath = PortableMetadataPath();
        if (Files.exists(primaryPath) || !Files.isRegularFile(portablePath)) {
            return false;
        }

        try {
            Path parent = primaryPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(portablePath, primaryPath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void PersistStores() {
        store.Persist();
        MirrorPrimaryMetadataToPortableStore();
    }

    private static void MirrorPrimaryMetadataToPortableStore() {
        Path primaryPath = store.MetadataPath();
        Path portablePath = PortableMetadataPath();
        if (!Files.isRegularFile(primaryPath)) {
            return;
        }

        try {
            Path parent = portablePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(primaryPath, portablePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            // Keep the primary metadata even when the portable mirror cannot be updated.
        }
    }

    private static Path PortableMetadataPath() {
        String configuredPath = System.getProperty("gameduck.library_portable_metadata_path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of("library", "game-library.properties");
    }

    private static void MergeDuplicateMetadata(EntrySnapshot canonical, List<EntrySnapshot> group) {
        EntryProperties canonicalProperties = new EntryProperties(canonical.entry().key());
        long earliestAdded = PositiveMin(group.stream()
                .mapToLong(snapshot -> snapshot.entry().addedAtMillis())
                .toArray());
        long latestPlayed = group.stream()
                .mapToLong(snapshot -> snapshot.entry().lastPlayedMillis())
                .max()
                .orElse(canonical.entry().lastPlayedMillis());
        boolean favourite = group.stream().anyMatch(snapshot -> snapshot.entry().favourite());
        EntrySnapshot preferredSource = group.stream().min(duplicatePreference).orElse(canonical);

        canonicalProperties.SetString(sourcePathSuffix, preferredSource.entry().sourcePath());
        canonicalProperties.SetString(systemIdSuffix, preferredSource.entry().systemId());
        canonicalProperties.SetString(systemVariantIdSuffix, preferredSource.entry().systemVariantId());
        canonicalProperties.SetString(systemVariantLabelSuffix, preferredSource.entry().systemVariantLabel());
        canonicalProperties.SetString(sourceNameSuffix, preferredSource.entry().sourceName());
        canonicalProperties.SetString(displayNameSuffix, preferredSource.entry().displayName());
        canonicalProperties.SetString(headerTitleSuffix, preferredSource.entry().headerTitle());
        canonicalProperties.SetBoolean(cgbCompatibleSuffix, preferredSource.entry().cgbCompatible());
        canonicalProperties.SetBoolean(cgbOnlySuffix, preferredSource.entry().cgbOnly());
        canonicalProperties.WriteIndexedList(patchNamePrefix, patchNameCountSuffix, preferredSource.entry().patchNames());
        canonicalProperties.WriteIndexedList(patchSourcePrefix, patchSourceCountSuffix, preferredSource.entry().patchSourcePaths());
        canonicalProperties.SetString(contentHashSuffix, canonical.contentHash());
        canonicalProperties.SetLong(addedAtSuffix, earliestAdded == Long.MAX_VALUE ? canonical.entry().addedAtMillis() : earliestAdded);
        canonicalProperties.SetLong(lastPlayedSuffix, latestPlayed);
        canonicalProperties.SetBoolean(favouriteSuffix, favourite);
    }

    private static void MigrateNewestSaveBundle(List<EntrySnapshot> group, SaveFileManager.SaveIdentity canonicalIdentity) {
        SaveFileManager.SaveIdentity newestIdentity = null;
        long newestSaveMillis = Long.MIN_VALUE;
        for (EntrySnapshot snapshot : group) {
            long candidateMillis = LatestSaveMillis(snapshot.entry().SaveIdentity());
            if (candidateMillis > newestSaveMillis) {
                newestSaveMillis = candidateMillis;
                newestIdentity = snapshot.entry().SaveIdentity();
            }
        }

        if (newestIdentity != null && newestSaveMillis > Long.MIN_VALUE && !sameIdentity(newestIdentity, canonicalIdentity)) {
            SaveFileManager.LoadSaveBundle(newestIdentity)
                    .filter(SaveFileManager.SaveDataBundle::HasAnyData)
                    .ifPresent(bundle -> SaveFileManager.Save(canonicalIdentity, bundle.primaryData(), bundle.supplementalData()));
        }
    }

    private static long LatestSaveMillis(SaveFileManager.SaveIdentity saveIdentity) {
        return SaveFileManager.DescribeSaveFiles(saveIdentity).existingFiles().stream()
                .mapToLong(file -> file.lastModified().toMillis())
                .max()
                .orElse(Long.MIN_VALUE);
    }

    private static void DeleteDuplicateRomFile(EntrySnapshot canonical, EntrySnapshot duplicate) {
        if (duplicate.entry().romPath() == null
                || canonical.entry().romPath() == null
                || duplicate.entry().romPath().equals(canonical.entry().romPath())) {
            return;
        }

        try {
            Files.deleteIfExists(duplicate.entry().romPath());
        } catch (IOException exception) {
            // Keep the duplicate ROM file when deletion fails.
        }
    }

    private static boolean DeleteOrphanedStoredRoms() {
        Path storageDirectory = RomStorageDirectory();
        if (!Files.isDirectory(storageDirectory)) {
            return false;
        }

        Set<Path> referencedPaths = new HashSet<>();
        for (String key : store.StoredKeys()) {
            LibraryEntry entry = ReadEntry(key);
            if (entry != null && entry.romPath() != null) {
                referencedPaths.add(entry.romPath().toAbsolutePath().normalize());
            }
        }

        boolean deletedAny = false;
        try (var paths = Files.list(storageDirectory)) {
            for (Path path : paths.toList()) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                if (".gitkeep".equalsIgnoreCase(path.getFileName().toString())) {
                    continue;
                }
                if (referencedPaths.contains(path.toAbsolutePath().normalize())) {
                    continue;
                }
                Files.deleteIfExists(path);
                deletedAny = true;
            }
        } catch (IOException exception) {
            // Keep orphaned files when cleanup fails.
        }
        return deletedAny;
    }

    private static LibraryEntry FindEntryByContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return null;
        }

        return BuildSnapshots().stream()
                .filter(snapshot -> contentHash.equals(snapshot.contentHash()))
                .min(duplicatePreference)
                .map(EntrySnapshot::entry)
                .orElse(null);
    }

    private static List<EntrySnapshot> BuildSnapshots() {
        List<EntrySnapshot> snapshots = new ArrayList<>();
        for (String key : store.StoredKeys()) {
            EntryProperties properties = new EntryProperties(key);
            LibraryEntry entry = properties.Read();
            if (entry == null) {
                continue;
            }
            snapshots.add(new EntrySnapshot(entry, properties.ResolveContentHash(entry.romPath())));
        }
        return snapshots;
    }

    private static boolean IsManagedLibraryPath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return false;
        }

        try {
            Path candidatePath = Path.of(sourcePath).toAbsolutePath().normalize();
            Path libraryPath = RomStorageDirectory().toAbsolutePath().normalize();
            return candidatePath.startsWith(libraryPath);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean ShouldPreferSourcePath(String currentSourcePath, String candidateSourcePath) {
        if (candidateSourcePath == null || candidateSourcePath.isBlank()) {
            return false;
        }
        if (currentSourcePath == null || currentSourcePath.isBlank()) {
            return true;
        }
        return IsManagedLibraryPath(currentSourcePath) && !IsManagedLibraryPath(candidateSourcePath);
    }

    private static long PositiveOrMax(long value) {
        return value > 0L ? value : Long.MAX_VALUE;
    }

    private static long PositiveMin(long[] values) {
        long result = Long.MAX_VALUE;
        for (long value : values) {
            if (value > 0L && value < result) {
                result = value;
            }
        }
        return result;
    }

    private static boolean sameIdentity(SaveFileManager.SaveIdentity left, SaveFileManager.SaveIdentity right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return KeyedPropertiesStore.NullToEmpty(left.sourcePath()).equals(KeyedPropertiesStore.NullToEmpty(right.sourcePath()))
                && KeyedPropertiesStore.NullToEmpty(left.sourceName()).equals(KeyedPropertiesStore.NullToEmpty(right.sourceName()))
                && KeyedPropertiesStore.NullToEmpty(left.displayName()).equals(KeyedPropertiesStore.NullToEmpty(right.displayName()))
                && left.patchNames().equals(right.patchNames());
    }

    private static LibraryEntry ReadEntry(String key) {
        return new EntryProperties(key).Read();
    }

    private record EntrySnapshot(LibraryEntry entry, String contentHash) {
    }

    private record ScannedRom(Path path, byte[] romBytes, String contentHash, GBRom rom) {
    }

    private static String BuildEntryKey(GBRom rom) {
        return KeyedPropertiesStore.Hash(String.join("|",
                KeyedPropertiesStore.Hash(rom.ToByteArray()),
                KeyedPropertiesStore.NullToEmpty(rom.GetSourceName()),
                String.join("|", rom.GetPatchNames())));
    }

    private static String BuildStoredFilename(GBRom rom, String key) {
        String baseName = GameMetadataStore.GetLibretroTitle(rom).orElse(SaveFileManager.BuildFallbackBaseName(rom));
        String extension = ResolveExtension(rom.GetSourcePath());
        return SanitiseFileComponent(baseName) + " [" + key.substring(0, Math.min(8, key.length())) + "]" + extension;
    }

    private static String ResolveExtension(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return ".gb";
        }

        String lowerPath = sourcePath.toLowerCase();
        if (lowerPath.endsWith(".gbc") || lowerPath.endsWith(".cgb")) {
            return ".gbc";
        }
        return lowerPath.endsWith(".gb") ? ".gb" : ".gb";
    }

    private static String SanitiseFileComponent(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        String cleaned = value.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ")
                .replaceAll("\\.+$", "")
                .trim();
        return cleaned.isBlank() ? "unknown" : cleaned;
    }

    private static Path RomStorageDirectory() {
        String configuredPath = System.getProperty("gameduck.library_rom_dir");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath);
        }
        return Path.of("library", "roms");
    }

    private static boolean ResolveCgbCompatible(String prefix, Path romPath, String sourcePath) {
        String storedValue = store.EntryValue(prefix + cgbCompatibleSuffix);
        if (storedValue != null) {
            return Boolean.parseBoolean(storedValue);
        }

        boolean cgbCompatible = RomConsoleSupport.IsGbc(romPath);
        if (!cgbCompatible) {
            cgbCompatible = RomConsoleSupport.IsProbablyGbc(sourcePath);
        }

        store.SetValue(prefix + cgbCompatibleSuffix, String.valueOf(cgbCompatible));
        PersistStores();
        return cgbCompatible;
    }

    private static boolean ResolveCgbOnly(String prefix, Path romPath) {
        String storedValue = store.EntryValue(prefix + cgbOnlySuffix);
        if (storedValue != null) {
            return Boolean.parseBoolean(storedValue);
        }

        boolean cgbOnly = RomConsoleSupport.IsCgbOnly(romPath);
        store.SetValue(prefix + cgbOnlySuffix, String.valueOf(cgbOnly));
        PersistStores();
        return cgbOnly;
    }

    private static final class Store extends KeyedPropertiesStore {
        private Store() {
            super("entry.", sourceNameSuffix, "gameduck.library_metadata_path",
                    Path.of("cache", "game-library.properties"), "GameDuck library");
        }

        private Path MetadataPath() {
            return ResolvedStorePath();
        }

        private String EntryValue(String key) {
            return RawProperty(key);
        }

        private void SetValue(String key, String value) {
            SetRawProperty(key, value);
        }

        private void ClearEntries() {
            ClearAllProperties();
        }
    }

    private static final class EntryProperties {
        private final KeyedPropertiesStore.Entry entry;

        private EntryProperties(String key) {
            this.entry = store.Entry(key);
        }

        private boolean Exists() {
            return entry.Has(sourceNameSuffix);
        }

        private void WriteMetadata(GBRom rom, Path storedPath, long now, String contentHash) {
            WriteMetadata(rom, storedPath, entry.GetLong(addedAtSuffix, now), now,
                    entry.GetBoolean(favouriteSuffix, false), contentHash);
        }

        private void WriteMetadata(GBRom rom, Path storedPath, long addedAt, long lastPlayed, boolean favourite,
                                   String contentHash) {
            entry.Set(romPathSuffix, storedPath.toString());
            entry.Set(systemIdSuffix, rom.systemId());
            entry.Set(systemVariantIdSuffix, rom.systemVariantId());
            entry.Set(systemVariantLabelSuffix, rom.systemVariantLabel());
            entry.Set(sourcePathSuffix, rom.GetSourcePath());
            entry.Set(sourceNameSuffix, rom.GetSourceName());
            entry.Set(displayNameSuffix, rom.GetName());
            entry.Set(headerTitleSuffix, rom.GetHeaderTitle());
            entry.Set(cgbCompatibleSuffix, RomConsoleSupport.IsGbc(rom));
            entry.Set(cgbOnlySuffix, RomConsoleSupport.IsCgbOnly(rom));
            entry.Set(contentHashSuffix, contentHash);
            entry.Set(addedAtSuffix, addedAt);
            entry.Set(lastPlayedSuffix, lastPlayed);
            entry.Set(favouriteSuffix, favourite);
            entry.WriteIndexedList(patchNamePrefix, patchNameCountSuffix, rom.GetPatchNames());
            entry.WriteIndexedList(patchSourcePrefix, patchSourceCountSuffix, rom.GetPatchSourcePaths());
        }

        private void TouchFromExistingRom(GBRom rom, long now, String contentHash) {
            entry.Set(lastPlayedSuffix, Math.max(entry.GetLong(lastPlayedSuffix, 0L), now));
            if (entry.GetLong(addedAtSuffix, 0L) <= 0L) {
                entry.Set(addedAtSuffix, now);
            }
            entry.Set(contentHashSuffix, contentHash);
            entry.Set(cgbCompatibleSuffix, RomConsoleSupport.IsGbc(rom));
            entry.Set(cgbOnlySuffix, RomConsoleSupport.IsCgbOnly(rom));
            if (ShouldPreferSourcePath(entry.Get(sourcePathSuffix), rom.GetSourcePath())) {
                entry.Set(sourcePathSuffix, rom.GetSourcePath());
                entry.Set(systemIdSuffix, rom.systemId());
                entry.Set(systemVariantIdSuffix, rom.systemVariantId());
                entry.Set(systemVariantLabelSuffix, rom.systemVariantLabel());
                entry.Set(sourceNameSuffix, rom.GetSourceName());
                entry.Set(displayNameSuffix, rom.GetName());
                entry.Set(headerTitleSuffix, rom.GetHeaderTitle());
                entry.WriteIndexedList(patchNamePrefix, patchNameCountSuffix, rom.GetPatchNames());
                entry.WriteIndexedList(patchSourcePrefix, patchSourceCountSuffix, rom.GetPatchSourcePaths());
            }
        }

        private String ResolveContentHash(Path romPath) {
            String storedHash = entry.Get(contentHashSuffix);
            if (!storedHash.isBlank()) {
                return storedHash;
            }
            if (romPath == null || !Files.isRegularFile(romPath)) {
                return "";
            }

            try {
                String resolvedHash = KeyedPropertiesStore.Hash(Files.readAllBytes(romPath));
                entry.Set(contentHashSuffix, resolvedHash);
                return resolvedHash;
            } catch (IOException exception) {
                return "";
            }
        }

        private LibraryEntry Read() {
            String romPathValue = entry.Get(romPathSuffix);
            String sourceName = entry.Get(sourceNameSuffix);
            String displayName = entry.Get(displayNameSuffix);
            if (romPathValue.isBlank() || (sourceName.isBlank() && displayName.isBlank())) {
                return null;
            }

            Path romPath = Path.of(romPathValue);
            String sourcePath = entry.Get(sourcePathSuffix);
            boolean cgbCompatible = ResolveCgbCompatible(entry.Prefix(), romPath, sourcePath);
            boolean cgbOnly = ResolveCgbOnly(entry.Prefix(), romPath);
            return new LibraryEntry(
                    entry.Key(),
                    romPath,
                    entry.Get(systemIdSuffix).isBlank() ? GBRom.systemId : entry.Get(systemIdSuffix),
                    entry.Get(systemVariantIdSuffix).isBlank()
                            ? (cgbCompatible ? GBRom.variantIdGbc : GBRom.variantIdGb)
                            : entry.Get(systemVariantIdSuffix),
                    entry.Get(systemVariantLabelSuffix).isBlank()
                            ? (cgbCompatible ? "GBC" : "GB")
                            : entry.Get(systemVariantLabelSuffix),
                    sourcePath,
                    sourceName,
                    displayName,
                    entry.ReadIndexedList(patchNamePrefix, patchNameCountSuffix),
                    entry.ReadIndexedList(patchSourcePrefix, patchSourceCountSuffix),
                    entry.Get(headerTitleSuffix),
                    cgbCompatible,
                    cgbOnly,
                    entry.GetLong(addedAtSuffix, 0L),
                    entry.GetLong(lastPlayedSuffix, 0L),
                    entry.GetBoolean(favouriteSuffix, false));
        }

        private void RemoveAll() {
            entry.RemoveAll();
        }

        private void SetBoolean(String suffix, boolean value) {
            entry.Set(suffix, value);
        }

        private void SetLong(String suffix, long value) {
            entry.Set(suffix, value);
        }

        private void SetString(String suffix, String value) {
            entry.Set(suffix, value);
        }

        private void WriteIndexedList(String itemPrefix, String countSuffix, List<String> values) {
            entry.WriteIndexedList(itemPrefix, countSuffix, values);
        }

        private boolean GetBoolean(String suffix, boolean fallback) {
            return entry.GetBoolean(suffix, fallback);
        }
    }
}

