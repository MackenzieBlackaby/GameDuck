package com.blackaby.Frontend.Borders;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * Metadata wrapper around a loaded display border image.
 */
public record LoadedDisplayBorder(
        String id,
        String displayName,
        String sourceLabel,
        Path sourcePath,
        BufferedImage image,
        Rectangle screenRect) {

    public LoadedDisplayBorder {
        id = id == null ? "none" : id.trim();
        displayName = displayName == null || displayName.isBlank() ? "Off" : displayName.trim();
        sourceLabel = sourceLabel == null ? "" : sourceLabel.trim();
        sourcePath = sourcePath == null ? null : sourcePath.toAbsolutePath().normalize();
        screenRect = screenRect == null ? null : new Rectangle(screenRect);
    }

    public boolean isNone() {
        return image == null || "none".equalsIgnoreCase(id);
    }

    public Rectangle screenRectCopy() {
        return screenRect == null ? null : new Rectangle(screenRect);
    }

    public String sourcePathText() {
        return sourcePath == null ? "" : sourcePath.toString();
    }
}
