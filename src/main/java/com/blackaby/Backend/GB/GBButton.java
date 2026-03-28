package com.blackaby.Backend.GB;

import com.blackaby.Backend.Platform.EmulatorButton;

/**
 * Stable Game Boy control identifiers shared across GB cores.
 */
public enum GBButton implements EmulatorButton {
    RIGHT(0x01, Group.DIRECTIONS),
    LEFT(0x02, Group.DIRECTIONS),
    UP(0x04, Group.DIRECTIONS),
    DOWN(0x08, Group.DIRECTIONS),
    A(0x01, Group.ACTIONS),
    B(0x02, Group.ACTIONS),
    SELECT(0x04, Group.ACTIONS),
    START(0x08, Group.ACTIONS);

    public enum Group {
        DIRECTIONS,
        ACTIONS
    }

    private final int mask;
    private final Group group;

    GBButton(int mask, Group group) {
        this.mask = mask;
        this.group = group;
    }

    public int GetMask() {
        return mask;
    }

    public Group GetGroup() {
        return group;
    }

    @Override
    public String id() {
        return name();
    }

    public static GBButton FromId(String buttonId) {
        if (buttonId == null || buttonId.isBlank()) {
            return null;
        }
        for (GBButton button : values()) {
            if (button.name().equalsIgnoreCase(buttonId)) {
                return button;
            }
        }
        return null;
    }
}
