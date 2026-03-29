package com.blackaby.Backend.GB;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Backend.Platform.EmulatorBackend;
import com.blackaby.Backend.Platform.EmulatorButton;
import com.blackaby.Backend.Platform.EmulatorCapabilities;
import com.blackaby.Backend.Platform.EmulatorDisplaySpec;
import com.blackaby.Backend.Platform.EmulatorHost;
import com.blackaby.Backend.Platform.EmulatorProfile;
import com.blackaby.Backend.Platform.EmulatorRuntime;
import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Misc.UiText;

import java.awt.Color;
import java.awt.Dimension;
import java.util.List;

/**
 * Registers the current Game Boy backend with the host UI.
 */
public final class DuckBackend implements EmulatorBackend {

    public static final DuckBackend instance = new DuckBackend();

    private static final EmulatorProfile profile = new DuckProfile();

    private DuckBackend() {
    }

    @Override
    public EmulatorProfile Profile() {
        return profile;
    }

    @Override
    public EmulatorRuntime CreateRuntime(EmulatorHost host, DuckDisplay display) {
        return new DuckEmulation(host, display, profile);
    }

    private static final class DuckProfile implements EmulatorProfile {
        private static final EmulatorDisplaySpec displaySpec = new EmulatorDisplaySpec(
                160,
                144,
                new Dimension(640, 576),
                new Dimension(160, 144),
                Color.BLACK);
        private static final EmulatorCapabilities capabilities = new EmulatorCapabilities(true, true, true, true, true, true);
        private static final List<GBButton> controlButtons = List.of(
                GBButton.UP,
                GBButton.DOWN,
                GBButton.LEFT,
                GBButton.RIGHT,
                GBButton.A,
                GBButton.B,
                GBButton.START,
                GBButton.SELECT);

        @Override
        public String backendId() {
            return "duck-game-boy";
        }

        @Override
        public String displayName() {
            return "Game Boy";
        }

        @Override
        public EmulatorDisplaySpec displaySpec() {
            return displaySpec;
        }

        @Override
        public EmulatorCapabilities capabilities() {
            return capabilities;
        }

        @Override
        public List<? extends EmulatorButton> controlButtons() {
            return controlButtons;
        }

        @Override
        public String controlButtonLabel(EmulatorButton button) {
            return UiText.OptionsWindow.DmgButtonName(button == null ? "" : button.id());
        }

        @Override
        public String controlButtonHelper(EmulatorButton button) {
            return UiText.OptionsWindow.DmgControlHelper(button == null ? "" : button.id());
        }

        @Override
        public List<String> supportedGameFileExtensions() {
            return List.of(".gb", ".gbc");
        }

        @Override
        public List<String> supportedPatchFileExtensions() {
            return List.of(".ips");
        }
    }
}


