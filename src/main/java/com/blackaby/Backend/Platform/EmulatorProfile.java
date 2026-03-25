package com.blackaby.Backend.Platform;

import java.util.List;

/**
 * Immutable backend description consumed by the host UI.
 */
public interface EmulatorProfile {

    String backendId();

    String displayName();

    EmulatorDisplaySpec displaySpec();

    EmulatorCapabilities capabilities();

    List<? extends EmulatorButton> controlButtons();

    String controlButtonLabel(EmulatorButton button);

    String controlButtonHelper(EmulatorButton button);

    List<String> supportedGameFileExtensions();

    List<String> supportedPatchFileExtensions();
}
