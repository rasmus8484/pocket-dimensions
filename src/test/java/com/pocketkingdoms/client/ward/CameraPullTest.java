package com.pocketkingdoms.client.ward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pulling the sigil toward the camera: inside the fog-free zone, and the picture never changes. */
class CameraPullTest {

    @Test
    void reachStaysInsideTheFogFreeZone() {
        assertEquals(96, CameraPull.reach(12 * 16), 1e-9, "default 12 chunks: fog starts at 173, capped at 96");
        assertEquals(0.9 * 28, CameraPull.reach(2 * 16), 1e-9, "2 chunks: fog starts at 28");
        assertEquals(96, CameraPull.reach(32 * 16), 1e-9);
    }

    @Test
    void nothingWithinReachMoves() {
        assertEquals(1, CameraPull.factor(50, 96), 1e-12);
        assertEquals(1, CameraPull.factor(96, 96), 1e-12);
    }

    @Test
    void furtherThanReachIsPulledToReach() {
        assertEquals(96.0 / 300, CameraPull.factor(300, 96), 1e-12);
        assertEquals(96, 300 * CameraPull.factor(300, 96), 1e-9);
    }

    @Test
    void aPulledPointLandsOnTheSameSpotOnScreen() {
        // a camera looking along +z: a point at (x, y, z) shows at (x / z, y / z)
        double x = 30, y = 270, z = 400, d = Math.sqrt(x * x + y * y + z * z);
        double k = CameraPull.factor(d, 96);
        assertEquals(x / z, (x * k) / (z * k), 1e-12);
        assertEquals(y / z, (y * k) / (z * k), 1e-12);
    }
}
