package com.blackaby.Backend.GB;

import com.blackaby.Backend.Platform.EmulatorBackend;

/**
 * Central place for selecting the active Game Boy core exposed to the host UI.
 */
public final class GBBackendManager {

    private static final EmulatorBackend current = GBBackend.instance;

    private GBBackendManager() {
    }

    public static EmulatorBackend Current() {
        return current;
    }
}
