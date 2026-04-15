package com.blackaby.Frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class LibraryWindowTest {

    @Test
    void singleArtRefreshTargetsOnlyTheExistingTile() {
        LibraryWindow.LargeIconRefreshPlan refreshPlan = LibraryWindow.planSingleArtRefresh("duck", true);

        assertFalse(refreshPlan.rebuildGrid());
        assertEquals("duck", refreshPlan.targetEntryKey());
    }

    @Test
    void singleArtRefreshDoesNothingWhenTileIsMissing() {
        LibraryWindow.LargeIconRefreshPlan refreshPlan = LibraryWindow.planSingleArtRefresh("duck", false);

        assertFalse(refreshPlan.rebuildGrid());
        assertNull(refreshPlan.targetEntryKey());
    }

    @Test
    void largeIconGridStateAvoidsRebuildWhenViewportWidthChangesInsideSameColumnBucket() {
        LibraryWindow.LargeIconGridState gridState = new LibraryWindow.LargeIconGridState();
        List<String> entryKeys = List.of("a", "b", "c", "d");

        LibraryWindow.LargeIconGridUpdate initialUpdate = gridState.update(entryKeys, 3, 220, 660);
        LibraryWindow.LargeIconGridUpdate widthOnlyUpdate = gridState.update(entryKeys, 3, 220, 672);

        assertTrue(initialUpdate.rebuild());
        assertFalse(widthOnlyUpdate.rebuild());
        assertFalse(widthOnlyUpdate.resizeTiles());
        assertTrue(widthOnlyUpdate.widthChanged());
    }

    @Test
    void largeIconGridStateRequestsTileResizeWithoutRebuildingWhenTileSizeChanges() {
        LibraryWindow.LargeIconGridState gridState = new LibraryWindow.LargeIconGridState();
        List<String> entryKeys = List.of("a", "b", "c");

        gridState.update(entryKeys, 3, 220, 660);
        LibraryWindow.LargeIconGridUpdate resizeUpdate = gridState.update(entryKeys, 3, 236, 708);

        assertFalse(resizeUpdate.rebuild());
        assertTrue(resizeUpdate.resizeTiles());
    }

    @Test
    void largeIconGridStateRebuildsWhenColumnBucketChanges() {
        LibraryWindow.LargeIconGridState gridState = new LibraryWindow.LargeIconGridState();
        List<String> entryKeys = List.of("a", "b", "c", "d");

        gridState.update(entryKeys, 3, 220, 660);
        LibraryWindow.LargeIconGridUpdate rebuildUpdate = gridState.update(entryKeys, 4, 180, 760);

        assertTrue(rebuildUpdate.rebuild());
    }
}
