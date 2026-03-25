package com.blackaby.Backend.Platform;

import java.util.List;

/**
 * Stable identity for a loaded or tracked game.
 */
public interface EmulatorGame {

    String sourcePath();

    String sourceName();

    String displayName();

    default String headerTitle() {
        return "";
    }

    default List<String> patchNames() {
        return List.of();
    }

    default List<String> patchSourcePaths() {
        return List.of();
    }

    default boolean batteryBackedSave() {
        return false;
    }

    default boolean cgbCompatible() {
        return false;
    }

    default boolean cgbOnly() {
        return false;
    }
}
