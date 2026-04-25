package com.blackaby.Misc;

import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.Platform.EmulatorGame;

/**
 * Shared UI filter for grouping games by system variant.
 */
public enum RomConsoleFilter {
    ALL(UiText.Common.CONSOLE_FILTER_ALL),
    GB(UiText.Common.CONSOLE_FILTER_GB),
    GBC(UiText.Common.CONSOLE_FILTER_GBC);

    private final String label;

    RomConsoleFilter(String label) {
        this.label = label;
    }

    public boolean Matches(boolean cgbCompatible) {
        return Matches(GBRom.systemId, cgbCompatible ? GBRom.variantIdGbc : GBRom.variantIdGb);
    }

    public boolean Matches(EmulatorGame game) {
        return game != null && Matches(game.systemId(), game.systemVariantId());
    }

    public boolean Matches(String systemId, String systemVariantId) {
        return switch (this) {
            case ALL -> true;
            case GB -> GBRom.systemId.equals(systemId) && GBRom.variantIdGb.equals(systemVariantId);
            case GBC -> GBRom.systemId.equals(systemId) && GBRom.variantIdGbc.equals(systemVariantId);
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
