package com.blackaby.Backend.Platform;

/**
 * Backend-neutral cheat definition exposed to the desktop frontend.
 *
 * @param key stable cheat identifier
 * @param label user-facing cheat name
 * @param address 16-bit memory address
 * @param value 8-bit cheat value
 * @param compareValue optional 8-bit compare value that must match before applying
 * @param enabled whether the cheat is active
 */
public record EmulatorCheat(
        String key,
        String label,
        int address,
        Integer compareValue,
        int value,
        boolean enabled) {

    public EmulatorCheat {
        key = key == null ? "" : key.trim();
        label = label == null ? "" : label.trim();
        address &= 0xFFFF;
        value &= 0xFF;
        compareValue = compareValue == null ? null : Integer.valueOf(compareValue & 0xFF);
    }

    public boolean HasCompareValue() {
        return compareValue != null;
    }
}
