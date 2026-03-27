package com.blackaby.Frontend.Borders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.blackaby.Misc.Settings;

import java.awt.Rectangle;

import org.junit.jupiter.api.Test;

class DisplayBorderRendererTest {

    @Test
    void calculateGameplayRectUsesLargestIntegerScaleWhenEnabled() {
        boolean originalIntegerScaling = Settings.integerScaleWindowOutput;
        try {
            Settings.integerScaleWindowOutput = true;

            Rectangle rect = DisplayBorderRenderer.calculateGameplayRect(500, 500);

            assertEquals(new Rectangle(10, 34, 480, 432), rect);
        } finally {
            Settings.integerScaleWindowOutput = originalIntegerScaling;
        }
    }

    @Test
    void calculateGameplayRectFallsBackToFitScalingWhenWindowIsSmallerThanNativeSize() {
        boolean originalIntegerScaling = Settings.integerScaleWindowOutput;
        try {
            Settings.integerScaleWindowOutput = true;

            Rectangle rect = DisplayBorderRenderer.calculateGameplayRect(120, 100);

            assertEquals(new Rectangle(4, 0, 111, 100), rect);
        } finally {
            Settings.integerScaleWindowOutput = originalIntegerScaling;
        }
    }
}
