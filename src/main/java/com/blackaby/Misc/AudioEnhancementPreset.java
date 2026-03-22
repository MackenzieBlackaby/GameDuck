package com.blackaby.Misc;

/**
 * Built-in audio enhancement presets that can be chained in the host output.
 */
public enum AudioEnhancementPreset {
    SOFT_LOW_PASS(UiText.AudioEnhancements.SOFT_LOW_PASS_LABEL, UiText.AudioEnhancements.SOFT_LOW_PASS_DESCRIPTION),
    POCKET_SPEAKER(UiText.AudioEnhancements.POCKET_SPEAKER_LABEL, UiText.AudioEnhancements.POCKET_SPEAKER_DESCRIPTION),
    SOFT_CLIP(UiText.AudioEnhancements.SOFT_CLIP_LABEL, UiText.AudioEnhancements.SOFT_CLIP_DESCRIPTION),
    STEREO_WIDEN(UiText.AudioEnhancements.STEREO_WIDEN_LABEL, UiText.AudioEnhancements.STEREO_WIDEN_DESCRIPTION),
    ROOM_REVERB(UiText.AudioEnhancements.ROOM_REVERB_LABEL, UiText.AudioEnhancements.ROOM_REVERB_DESCRIPTION),
    SHIMMER_CHORUS(UiText.AudioEnhancements.SHIMMER_CHORUS_LABEL, UiText.AudioEnhancements.SHIMMER_CHORUS_DESCRIPTION),
    BITCRUSH(UiText.AudioEnhancements.BITCRUSH_LABEL, UiText.AudioEnhancements.BITCRUSH_DESCRIPTION),
    DUB_ECHO(UiText.AudioEnhancements.DUB_ECHO_LABEL, UiText.AudioEnhancements.DUB_ECHO_DESCRIPTION),
    UNDERWATER(UiText.AudioEnhancements.UNDERWATER_LABEL, UiText.AudioEnhancements.UNDERWATER_DESCRIPTION);

    private final String label;
    private final String description;

    AudioEnhancementPreset(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /**
     * Returns the display label shown in the options UI.
     *
     * @return preset label
     */
    public String Label() {
        return label;
    }

    /**
     * Returns the summary shown in the options UI.
     *
     * @return preset description
     */
    public String Description() {
        return description;
    }

    /**
     * Parses one preset from its persisted enum name.
     *
     * @param configValue stored enum name
     * @return matching preset or {@code null} when unknown
     */
    public static AudioEnhancementPreset FromConfigValue(String configValue) {
        if (configValue == null || configValue.isBlank()) {
            return null;
        }

        try {
            return AudioEnhancementPreset.valueOf(configValue.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    public String toString() {
        return label;
    }
}
