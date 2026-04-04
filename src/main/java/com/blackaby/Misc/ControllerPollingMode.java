package com.blackaby.Misc;

/**
 * Fixed controller polling presets exposed to the host UI.
 */
public enum ControllerPollingMode {
    LOW_LATENCY("Low Latency", 8L),
    BALANCED("Balanced", 16L),
    POWER_SAVER("Power Saver", 33L);

    private final String label;
    private final long pollIntervalMillis;

    ControllerPollingMode(String label, long pollIntervalMillis) {
        this.label = label;
        this.pollIntervalMillis = pollIntervalMillis;
    }

    public long PollIntervalMillis() {
        return pollIntervalMillis;
    }

    @Override
    public String toString() {
        return label;
    }
}
