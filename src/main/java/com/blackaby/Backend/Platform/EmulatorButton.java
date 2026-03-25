package com.blackaby.Backend.Platform;

/**
 * Identifies one logical input exposed by an emulator backend.
 */
public interface EmulatorButton {

    /**
     * Returns the stable backend-defined button identifier.
     *
     * @return stable button id
     */
    String id();
}
