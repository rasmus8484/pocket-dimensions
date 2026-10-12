package com.pocketkingdoms.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndLandingTest {

    @Test
    void theAnchorsThatVanishedSatInTheSweptBox() {
        // Where the play test's two lost anchors were recorded
        assertTrue(EndLanding.sweeps(100, 49, 0));
        assertTrue(EndLanding.sweeps(101, 49, 1));
    }

    @Test
    void theBoxIsFiveByFiveFromTheObsidianUpThreeBlocks() {
        assertTrue(EndLanding.sweeps(98, 48, -2));
        assertTrue(EndLanding.sweeps(102, 51, 2));
        assertFalse(EndLanding.sweeps(97, 49, 0));
        assertFalse(EndLanding.sweeps(103, 49, 0));
        assertFalse(EndLanding.sweeps(100, 49, 3));
        assertFalse(EndLanding.sweeps(100, 47, 0));
        assertFalse(EndLanding.sweeps(100, 52, 0));
    }

    @Test
    void anAnchorElsewhereInTheEndIsSafe() {
        assertFalse(EndLanding.sweeps(20, 61, 17));   // the play test's third anchor, which survived
    }
}
