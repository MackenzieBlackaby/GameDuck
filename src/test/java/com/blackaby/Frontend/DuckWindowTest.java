package com.blackaby.Frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;

import org.junit.jupiter.api.Test;

class DuckWindowTest {

    @Test
    void throttlerSkipsRepeatedIdenticalLocationUpdates() {
        DuckWindow.WindowInteractionThrottler throttler = new DuckWindow.WindowInteractionThrottler();
        Point location = new Point(48, 64);
        Rectangle previewBounds = new Rectangle(0, 0, 320, 240);

        assertTrue(throttler.queueLocation(location, previewBounds));
        assertFalse(throttler.queueLocation(new Point(location), new Rectangle(previewBounds)));

        DuckWindow.PendingWindowInteraction pendingInteraction = throttler.drain();
        assertNotNull(pendingInteraction);
        assertEquals(location, pendingInteraction.location());
        assertNull(pendingInteraction.bounds());
        assertEquals(previewBounds, pendingInteraction.previewBounds());

        assertFalse(throttler.queueLocation(new Point(location), new Rectangle(previewBounds)));
    }

    @Test
    void throttlerSkipsRepeatedIdenticalBoundsUpdates() {
        DuckWindow.WindowInteractionThrottler throttler = new DuckWindow.WindowInteractionThrottler();
        Rectangle bounds = new Rectangle(10, 12, 640, 480);

        assertTrue(throttler.queueBounds(bounds));
        assertFalse(throttler.queueBounds(new Rectangle(bounds)));

        DuckWindow.PendingWindowInteraction pendingInteraction = throttler.drain();
        assertNotNull(pendingInteraction);
        assertNull(pendingInteraction.location());
        assertEquals(bounds, pendingInteraction.bounds());
        assertNull(pendingInteraction.previewBounds());

        assertFalse(throttler.queueBounds(new Rectangle(bounds)));
    }

    @Test
    void throttlerCoalescesPendingLocationIntoBoundsUpdate() {
        DuckWindow.WindowInteractionThrottler throttler = new DuckWindow.WindowInteractionThrottler();

        assertTrue(throttler.queueLocation(new Point(24, 32), new Rectangle(0, 0, 300, 200)));
        assertTrue(throttler.queueBounds(new Rectangle(40, 50, 800, 600)));

        DuckWindow.PendingWindowInteraction pendingInteraction = throttler.drain();
        assertNotNull(pendingInteraction);
        assertNull(pendingInteraction.location());
        assertEquals(new Rectangle(40, 50, 800, 600), pendingInteraction.bounds());
        assertNull(pendingInteraction.previewBounds());
    }
}
