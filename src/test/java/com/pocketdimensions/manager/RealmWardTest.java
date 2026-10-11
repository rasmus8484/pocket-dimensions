package com.pocketdimensions.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The ward's ceiling: feet are held at or below y 318, and nothing is built at y 318 or 319. */
class RealmWardTest {

    @Test
    void theCeilingHoldsFeetAboveY318() {
        assertFalse(RealmWard.aboveCeiling(317.5));
        assertFalse(RealmWard.aboveCeiling(318.0), "standing on a block at y 317 is allowed");
        assertTrue(RealmWard.aboveCeiling(318.01));
    }

    @Test
    void atTheCeilingTheEyesAreBelowTheVeilsTop() {
        assertTrue(RealmWard.CEILING_Y + 1.62 < 320, "eyes stay under the top surface of the Veil (y 320)");
    }

    @Test
    void theServerOnlyStepsInPastItsSlack() {
        assertFalse(RealmWard.serverMustHold(318.9), "an honest client may overshoot by up to one tick");
        assertTrue(RealmWard.serverMustHold(319.01));
    }

    @Test
    void noBuildingAtTheTopTwoRows() {
        assertFalse(RealmWard.buildRefused(317));
        assertTrue(RealmWard.buildRefused(318));
        assertTrue(RealmWard.buildRefused(319));
    }
}
