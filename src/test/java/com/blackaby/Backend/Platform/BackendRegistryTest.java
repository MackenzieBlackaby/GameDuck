package com.blackaby.Backend.Platform;

import com.blackaby.Backend.GB.GBBackend;
import com.blackaby.Backend.GB.Misc.GBRom;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackendRegistryTest {

    @Test
    void resolvesGameFilesByExtension() {
        assertSame(GBBackend.instance, BackendRegistry.ResolveBackendForGame(Path.of("pokemon.gb")));
        assertSame(GBBackend.instance, BackendRegistry.ResolveBackendForGame(Path.of("pokemon.gbc")));
    }

    @Test
    void resolvesPatchFilesByExtension() {
        assertSame(GBBackend.instance, BackendRegistry.ResolveBackendForPatch(Path.of("hack.ips")));
    }

    @Test
    void findsBackendById() {
        assertSame(GBBackend.instance, BackendRegistry.FindByBackendId(GBRom.systemId));
    }

    @Test
    void rejectsPatchedMediaWhenBaseExtensionIsUnsupported() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> BackendRegistry.LoadPatchedMedia(Path.of("game.nes"), Path.of("hack.ips")));
        assertEquals("No registered core supports \"game.nes\".", exception.getMessage());
    }
}
