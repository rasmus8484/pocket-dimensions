package com.pocketdimensions.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Veil: the ring of block columns just outside each realm's plot, and its colour bands by height. */
class VeilLayoutTest {

    // plots 32 blocks across, one every 48 blocks: plot 0 is x and z in [0, 32), plot 1 starts at x 48
    private static final int SIDE = 32, CELL = 48;
    private static boolean veil(int x, int z) { return VeilLayout.isVeilColumn(x, z, SIDE, CELL); }

    @Test
    void insideAPlotIsNeverVeil() {
        assertFalse(veil(0, 0));
        assertFalse(veil(31, 31));
        assertFalse(veil(16, 5));
        assertFalse(veil(48 * 3 + 10, 48 * 2 + 10), "inside a plot further out");
    }

    @Test
    void theColumnsJustOutsideEachSideAreVeil() {
        assertTrue(veil(32, 10), "east, at maxX");
        assertTrue(veil(-1, 10), "west, at minX - 1");
        assertTrue(veil(10, 32), "south, at maxZ");
        assertTrue(veil(10, -1), "north, at minZ - 1");
    }

    @Test
    void theCornersAreVeil() {
        assertTrue(veil(32, 32));
        assertTrue(veil(-1, -1));
        assertTrue(veil(32, -1));
        assertTrue(veil(-1, 32));
    }

    @Test
    void theGapBeyondTheRingIsNotVeil() {
        assertFalse(veil(33, 10), "one past the east wall");
        assertFalse(veil(40, 10), "middle of the gap");
        assertFalse(veil(40, 40), "middle of a gap crossing");
        assertFalse(veil(32, 33), "past the end of the east wall");
    }

    @Test
    void theNextPlotHasItsOwnRing() {
        assertTrue(veil(47, 10), "west wall of plot 1 (x 48)");
        assertTrue(veil(80, 10), "east wall of plot 1");
        assertTrue(veil(48 * 9999 + 32, 48 * 3 + 5), "far along the row");
    }

    @Test
    void bandsSplitTheWorldsHeightIntoEight() {
        assertEquals(0, VeilLayout.band(-64, -64, 384));
        assertEquals(0, VeilLayout.band(-17, -64, 384));
        assertEquals(1, VeilLayout.band(-16, -64, 384));
        assertEquals(2, VeilLayout.band(63, -64, 384), "sea level is pale mist");
        assertEquals(7, VeilLayout.band(319, -64, 384));
    }

    @Test
    void bandsAreClampedOutsideTheWorld() {
        assertEquals(0, VeilLayout.band(-100, -64, 384));
        assertEquals(7, VeilLayout.band(400, -64, 384));
    }
}
