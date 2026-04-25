package com.blackaby.Misc;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Backend.GB.Misc.GBRom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerBindingsTest {

    @Test
    void keepsBindingsIsolatedByBackendId() {
        ControllerBindings bindings = new ControllerBindings();
        ControllerBinding customBinding = ControllerBinding.Button("9");

        bindings.SetBinding("other-system", GBButton.A, customBinding);

        assertEquals(ControllerBinding.Button("0"), bindings.GetBinding(GBRom.systemId, GBButton.A));
        assertEquals(customBinding, bindings.GetBinding("other-system", GBButton.A));
    }
}
