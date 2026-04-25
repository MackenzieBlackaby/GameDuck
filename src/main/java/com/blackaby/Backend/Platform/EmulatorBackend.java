package com.blackaby.Backend.Platform;

import com.blackaby.Frontend.DuckDisplay;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Factory for one concrete emulator backend.
 */
public interface EmulatorBackend {

    EmulatorProfile Profile();

    EmulatorRuntime CreateRuntime(EmulatorHost host, DuckDisplay display);

    EmulatorMedia LoadMedia(Path mediaPath) throws IOException;

    EmulatorMedia LoadPatchedMedia(Path baseGamePath, Path patchPath) throws IOException;
}
