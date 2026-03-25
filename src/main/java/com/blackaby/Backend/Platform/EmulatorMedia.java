package com.blackaby.Backend.Platform;

/**
 * Loadable game media supplied to an emulator runtime.
 */
public interface EmulatorMedia extends EmulatorGame {

    /**
     * Returns the complete binary image to boot.
     *
     * @return program bytes
     */
    byte[] programBytes();
}
