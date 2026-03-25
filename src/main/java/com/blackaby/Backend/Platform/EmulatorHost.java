package com.blackaby.Backend.Platform;

/**
 * Host callbacks exposed by the desktop frontend to emulator runtimes.
 */
public interface EmulatorHost {

    void SetSubtitle(String... subtitleParts);

    void SetLoadedGame(EmulatorGame game);

    void SetLoadedGame(EmulatorGame game, boolean allowFallback);

    void LoadGameArt(EmulatorGame game);

    void ClearGameArt();
}
