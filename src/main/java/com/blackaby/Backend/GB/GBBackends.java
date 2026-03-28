package com.blackaby.Backend.GB;

import com.blackaby.Backend.Platform.EmulatorBackend;

/**
 * Central place for selecting the active Game Boy core exposed to the host UI.
 */
public final class GBBackends {

    private static final EmulatorBackend current = DuckBackend.instance;

    private GBBackends() {
    }

    public static EmulatorBackend Current() {
        return current;
    }
}
