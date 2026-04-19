package com.blackaby.Misc;

/**
 * Selects how monochrome Game Boy cartridges should be presented.
 */
public enum NonGbcColourMode {
    GB_ORIGINAL,
    CUSTOM_PALETTE,
    GBC_COLOURISATION;

    public static NonGbcColourMode FromLegacyBoolean(boolean enabled) {
        return enabled ? GBC_COLOURISATION : GB_ORIGINAL;
    }
}
