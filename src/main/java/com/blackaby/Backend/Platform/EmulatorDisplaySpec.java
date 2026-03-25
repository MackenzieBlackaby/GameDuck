package com.blackaby.Backend.Platform;

import java.awt.Color;
import java.awt.Dimension;

/**
 * Defines the frame-buffer and host sizing characteristics for a backend.
 *
 * @param frameWidth logical frame width in pixels
 * @param frameHeight logical frame height in pixels
 * @param preferredSize preferred Swing component size
 * @param minimumSize minimum Swing component size
 * @param backgroundColour default display background colour
 */
public record EmulatorDisplaySpec(
        int frameWidth,
        int frameHeight,
        Dimension preferredSize,
        Dimension minimumSize,
        Color backgroundColour) {
}
