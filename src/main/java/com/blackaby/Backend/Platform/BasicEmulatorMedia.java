package com.blackaby.Backend.Platform;

import java.util.List;

/**
 * Generic immutable media wrapper for persisted library content.
 */
public record BasicEmulatorMedia(
        String systemId,
        String systemVariantId,
        String systemVariantLabel,
        String sourcePath,
        String sourceName,
        String displayName,
        String headerTitle,
        List<String> patchNames,
        List<String> patchSourcePaths,
        boolean batteryBackedSave,
        byte[] programBytes) implements EmulatorMedia {

    public BasicEmulatorMedia {
        patchNames = List.copyOf(patchNames == null ? List.of() : patchNames);
        patchSourcePaths = List.copyOf(patchSourcePaths == null ? List.of() : patchSourcePaths);
        programBytes = programBytes == null ? new byte[0] : programBytes.clone();
    }
}
