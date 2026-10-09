package com.pocketdimensions.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** When the server holds a player under the ceiling itself, and where it puts them. */
class RealmWardHoldTest {

    @Test
    void anythingThatCannotBeLoweredIsHeldAtOnce() {
        assertTrue(RealmWard.serverMustHold(318.5, true), "standing on a block at y 318: the client can't lower them");
        assertFalse(RealmWard.serverMustHold(318.5, false), "free air below: the client's own clamp handles it");
        assertTrue(RealmWard.serverMustHold(319.5, false), "past the slack, the server holds anyway");
        assertFalse(RealmWard.serverMustHold(318.0, true), "at the ceiling is not above it");
    }

    @Test
    void theHoldKeepsThePlayerInsideTheirPlot() {
        assertEquals(397.5, RealmWard.inside(500, 0, 400), 1e-9);
        assertEquals(2.5, RealmWard.inside(-30, 0, 400), 1e-9);
        assertEquals(123.25, RealmWard.inside(123.25, 0, 400), 1e-9);
    }

    @Test
    void theFreeSpotSearchStaysNearTheCeiling() {
        assertEquals(RealmWard.CEILING_Y - 24, RealmWard.searchFloorY());
    }
}
