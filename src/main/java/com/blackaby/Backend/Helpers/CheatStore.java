package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.Platform.EmulatorCheat;
import com.blackaby.Backend.Platform.EmulatorGame;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Persists per-game cheat definitions for the desktop frontend.
 */
public final class CheatStore {

    private static final String gameKeySuffix = ".game_key";
    private static final String labelSuffix = ".label";
    private static final String addressSuffix = ".address";
    private static final String compareValueSuffix = ".compare_value";
    private static final String valueSuffix = ".value";
    private static final String enabledSuffix = ".enabled";
    private static final String positionSuffix = ".position";

    private static final Store store = new Store();

    private CheatStore() {
    }

    /**
     * Returns the stored cheats for one tracked game.
     *
     * @param game tracked game identity
     * @return cheats ordered by their saved list position
     */
    public static synchronized List<EmulatorCheat> GetCheats(EmulatorGame game) {
        String gameKey = BuildGameKey(game);
        if (gameKey.isBlank()) {
            return List.of();
        }

        store.EnsureLoaded();
        return store.StoredKeys().stream()
                .map(CheatStore::ReadEntry)
                .filter(entry -> entry != null && gameKey.equals(entry.gameKey()))
                .sorted((left, right) -> Integer.compare(left.position(), right.position()))
                .map(StoredCheat::cheat)
                .toList();
    }

    /**
     * Replaces the stored cheats for one tracked game.
     *
     * @param game tracked game identity
     * @param cheats replacement cheat list
     * @return normalised cheat list as stored on disk
     */
    public static synchronized List<EmulatorCheat> SaveCheats(EmulatorGame game, List<EmulatorCheat> cheats) {
        String gameKey = BuildGameKey(game);
        if (gameKey.isBlank()) {
            return List.of();
        }

        store.EnsureLoaded();
        List<EmulatorCheat> requestedCheats = cheats == null ? List.of() : cheats;
        LinkedHashSet<String> retainedKeys = new LinkedHashSet<>();
        int position = 0;
        for (EmulatorCheat cheat : requestedCheats) {
            if (cheat == null) {
                continue;
            }

            EmulatorCheat normalisedCheat = NormaliseCheat(gameKey, cheat, position);
            EntryProperties entry = new EntryProperties(normalisedCheat.key());
            entry.Write(gameKey, normalisedCheat, position);
            retainedKeys.add(normalisedCheat.key());
            position++;
        }

        for (String key : store.StoredKeys()) {
            StoredCheat storedCheat = ReadEntry(key);
            if (storedCheat != null && gameKey.equals(storedCheat.gameKey()) && !retainedKeys.contains(key)) {
                new EntryProperties(key).RemoveAll();
            }
        }

        store.Persist();
        return GetCheats(game);
    }

    static synchronized void ResetForTests() {
        store.ResetForTests();
    }

    static String BuildGameKey(EmulatorGame game) {
        if (game == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(KeyedPropertiesStore.NullToEmpty(game.sourcePath())).append('|');
        builder.append(KeyedPropertiesStore.NullToEmpty(game.sourceName())).append('|');
        builder.append(KeyedPropertiesStore.NullToEmpty(game.displayName())).append('|');
        for (String patchSourcePath : game.patchSourcePaths()) {
            builder.append(KeyedPropertiesStore.NullToEmpty(patchSourcePath)).append('|');
        }
        builder.append('|');
        for (String patchName : game.patchNames()) {
            builder.append(KeyedPropertiesStore.NullToEmpty(patchName)).append('|');
        }
        return KeyedPropertiesStore.Hash(builder.toString());
    }

    private static StoredCheat ReadEntry(String key) {
        EntryProperties entry = new EntryProperties(key);
        return entry.Read();
    }

    private static EmulatorCheat NormaliseCheat(String gameKey, EmulatorCheat cheat, int position) {
        String key = cheat.key();
        if (key == null || key.isBlank()) {
            key = KeyedPropertiesStore.Hash(gameKey + "|" + position + "|" + System.nanoTime() + "|" + cheat.label()
                    + "|" + cheat.address() + "|" + cheat.value());
        }

        String label = cheat.label() == null || cheat.label().isBlank()
                ? DefaultLabel(cheat.address(), cheat.value())
                : cheat.label().trim();
        return new EmulatorCheat(key, label, cheat.address(), cheat.compareValue(), cheat.value(), cheat.enabled());
    }

    private static String DefaultLabel(int address, int value) {
        return String.format("Cheat %04X=%02X", address & 0xFFFF, value & 0xFF);
    }

    private record StoredCheat(String gameKey, int position, EmulatorCheat cheat) {
    }

    private static final class EntryProperties {
        private final KeyedPropertiesStore.Entry entry;

        private EntryProperties(String key) {
            this.entry = store.Entry(key);
        }

        private void Write(String gameKey, EmulatorCheat cheat, int position) {
            entry.Set(gameKeySuffix, gameKey);
            entry.Set(labelSuffix, cheat.label());
            entry.Set(addressSuffix, cheat.address());
            if (cheat.compareValue() == null) {
                entry.Set(compareValueSuffix, "");
            } else {
                entry.Set(compareValueSuffix, cheat.compareValue());
            }
            entry.Set(valueSuffix, cheat.value());
            entry.Set(enabledSuffix, cheat.enabled());
            entry.Set(positionSuffix, position);
        }

        private StoredCheat Read() {
            String gameKey = entry.Get(gameKeySuffix);
            if (gameKey.isBlank()) {
                return null;
            }

            String compareValueText = entry.Get(compareValueSuffix);
            Integer compareValue = compareValueText.isBlank()
                    ? null
                    : Integer.valueOf(KeyedPropertiesStore.ParseInt(compareValueText, 0) & 0xFF);
            EmulatorCheat cheat = new EmulatorCheat(
                    entry.Key(),
                    entry.Get(labelSuffix),
                    entry.GetInt(addressSuffix, 0),
                    compareValue,
                    entry.GetInt(valueSuffix, 0),
                    entry.GetBoolean(enabledSuffix, true));
            return new StoredCheat(gameKey, entry.GetInt(positionSuffix, Integer.MAX_VALUE), cheat);
        }

        private void RemoveAll() {
            entry.RemoveAll();
        }
    }

    private static final class Store extends KeyedPropertiesStore {
        private Store() {
            super("cheat.", gameKeySuffix, "gameduck.cheat_metadata_path",
                    Path.of("cache", "cheats.properties"),
                    "GameDuck cheats");
        }
    }
}
