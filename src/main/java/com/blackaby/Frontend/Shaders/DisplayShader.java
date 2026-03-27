package com.blackaby.Frontend.Shaders;

/**
 * Applies a CPU-side post-process effect to a completed emulator frame.
 * <p>
 * Custom shader JARs can provide implementations of this interface through
 * {@link java.util.ServiceLoader}.
 */
public interface DisplayShader {

    /**
     * Returns the stable identifier used in settings and JSON files.
     *
     * @return unique shader identifier
     */
    String Id();

    /**
     * Returns the display name shown in the options UI.
     *
     * @return user-facing shader name
     */
    String DisplayName();

    /**
     * Returns a short description of the shader.
     *
     * @return shader description
     */
    String Description();

    /**
     * Returns the preferred render scale used before post-processing.
     * Values above 1 let shaders operate on a higher-resolution copy of the
     * emulator frame so per-pixel structure can be more pronounced.
     *
     * @return preferred integer render scale
     */
    default int RenderScale() {
        return 1;
    }

    /**
     * Returns whether this shader should be rendered on the async worker rather
     * than inline with presentation.
     *
     * @return true when asynchronous rendering is preferred
     */
    default boolean PreferAsyncRendering() {
        return true;
    }

    /**
     * Applies the shader from {@code source} into {@code target}.
     *
     * @param source  raw source frame
     * @param target  processed frame output
     * @param scratch reusable scratch buffer matching the source length
     * @param width   frame width in pixels
     * @param height  frame height in pixels
     */
    void Apply(int[] source, int[] target, int[] scratch, int width, int height);
}
