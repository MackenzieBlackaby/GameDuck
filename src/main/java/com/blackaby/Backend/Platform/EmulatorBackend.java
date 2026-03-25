package com.blackaby.Backend.Platform;

import com.blackaby.Frontend.DuckDisplay;

/**
 * Factory for one concrete emulator backend.
 */
public interface EmulatorBackend {

    EmulatorProfile Profile();

    EmulatorRuntime CreateRuntime(EmulatorHost host, DuckDisplay display);
}
