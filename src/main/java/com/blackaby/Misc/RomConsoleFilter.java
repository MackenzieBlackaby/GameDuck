package com.blackaby.Misc;

/**
 * Shared UI filter for grouping ROMs by Game Boy hardware target.
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
        return switch (this) {
            case ALL -> true;
            case GB -> !cgbCompatible;
            case GBC -> cgbCompatible;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
