package com.blackaby.Misc;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Backend.GB.Misc.GBRom;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputBindingsTest {

    @Test
    void keepsBindingsIsolatedByBackendId() {
        InputBindings bindings = new InputBindings();

        bindings.SetKeyCode("other-system", GBButton.A, KeyEvent.VK_Q);

        assertEquals(KeyEvent.VK_X, bindings.GetKeyCode(GBRom.systemId, GBButton.A));
        assertEquals(KeyEvent.VK_Q, bindings.GetKeyCode("other-system", GBButton.A));
    }
}
