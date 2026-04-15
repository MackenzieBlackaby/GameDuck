package com.blackaby.Frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OptionsWindowTest {

    @Test
    void responsiveLayoutStateSkipsRepeatedIdenticalMetrics() {
        OptionsWindow.ResponsiveLayoutState layoutState = new OptionsWindow.ResponsiveLayoutState();

        assertTrue(layoutState.update(3, 96, 96));
        assertFalse(layoutState.update(3, 96, 96));
    }

    @Test
    void responsiveLayoutStateInvalidatesWhenColumnCountChanges() {
        OptionsWindow.ResponsiveLayoutState layoutState = new OptionsWindow.ResponsiveLayoutState();

        layoutState.update(3, 96, 96);
        assertTrue(layoutState.update(4, 96, 96));
    }

    @Test
    void responsiveLayoutStateInvalidatesWhenGeometryBucketChanges() {
        OptionsWindow.ResponsiveLayoutState layoutState = new OptionsWindow.ResponsiveLayoutState();

        layoutState.update(3, 96, 96);
        assertTrue(layoutState.update(3, 112, 112));
    }
}
