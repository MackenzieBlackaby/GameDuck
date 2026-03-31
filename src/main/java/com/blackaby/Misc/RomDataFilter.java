package com.blackaby.Misc;

/**
 * Shared UI filter for filtering if a ROM has tracked data
 */
public enum RomDataFilter {
    ALL(UiText.Common.STATUS_ALL),
    HAS_DATA(UiText.Common.STATUS_TRACKED);

    private final String label;

    RomDataFilter(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
