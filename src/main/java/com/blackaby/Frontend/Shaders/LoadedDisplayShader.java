package com.blackaby.Frontend.Shaders;

import java.nio.file.Path;

/**
 * Metadata wrapper around a loaded display shader instance.
 */
public record LoadedDisplayShader(
        DisplayShader shader,
        String sourceLabel,
        Path sourcePath,
        int renderScale) {

    public LoadedDisplayShader {
        renderScale = Math.max(1, Math.min(6, renderScale));
    }

    /**
     * Returns the shader identifier.
     *
     * @return stable shader id
     */
    public String id() {
        return shader.Id();
    }

    /**
     * Returns the user-facing shader name.
     *
     * @return display name
     */
    public String displayName() {
        return shader.DisplayName();
    }

    /**
     * Returns the shader description.
     *
     * @return description text
     */
    public String description() {
        return shader.Description();
    }

    /**
     * Applies the wrapped shader.
     *
     * @param source  raw source frame
     * @param target  processed output frame
     * @param scratch reusable scratch buffer
     * @param width   frame width in pixels
     * @param height  frame height in pixels
     */
    public void apply(int[] source, int[] target, int[] scratch, int width, int height) {
        shader.Apply(source, target, scratch, width, height);
    }

    /**
     * Returns whether the wrapped shader prefers async rendering.
     *
     * @return true when async rendering is preferred
     */
    public boolean prefersAsyncRendering() {
        return shader.PreferAsyncRendering();
    }

    /**
     * Returns a source-path label for the options UI.
     *
     * @return source path text, or blank when not applicable
     */
    public String sourcePathText() {
        return sourcePath == null ? "" : sourcePath.toString();
    }

    @Override
    public String toString() {
        return displayName();
    }
}
