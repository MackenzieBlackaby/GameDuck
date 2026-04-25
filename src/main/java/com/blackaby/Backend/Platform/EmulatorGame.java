package com.blackaby.Backend.Platform;

import java.util.List;

/**
 * Stable identity for a loaded or tracked game.
 */
public interface EmulatorGame {

    String systemId();

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

    default String systemVariantId() {
        return "";
    }

    default String systemVariantLabel() {
        return "";
    }
}
