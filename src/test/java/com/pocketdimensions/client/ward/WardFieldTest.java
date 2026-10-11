package com.pocketdimensions.client.ward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The ward field: clear by the core, very faint by the outer tier, streaming outward, filling the square. */
class WardFieldTest {

    // radius 25: the plot is 784 blocks across, so half 392, OUTER 362, the field's square 377
    private static final double HALF = 392;

    @Test
    void strengthFallsOffSteeplyFromTheCore() {
        assertEquals(0.449, WardField.strength(20), 5e-4);
        assertEquals(0.199, WardField.strength(60), 5e-4);
        assertEquals(0.065, WardField.strength(320), 5e-4);
        for (double r = 20; r < 500; r += 10) assertTrue(WardField.strength(r + 10) < WardField.strength(r), "falls at " + r);
    }

    @Test
    void nothingRightAtTheBeamOrOutsideTheSquare() {
        assertEquals(0, WardField.alpha(HALF, 5, 5, 12.3), 1e-12);
        assertEquals(0, WardField.alpha(HALF, 380, 0, 12.3), 1e-12, "past the square's edge (377)");
        assertEquals(0, WardField.alpha(HALF, 0, -381, 12.3), 1e-12);
    }

    @Test
    void itFillsTheSquareCornersToo() {
        double max = 0;
        for (double t = 0; t < 40; t += 0.5) max = Math.max(max, WardField.alpha(HALF, 360, 360, t));
        assertTrue(max > 0.01, "the corners get the faint field, max " + max);
    }

    @Test
    void neverStrongerThanHalf() {
        for (int i = -40; i <= 40; i++) for (int j = -40; j <= 40; j++)
            assertTrue(WardField.alpha(HALF, i * 9.3, j * 9.3, 7.7) <= 0.5);
    }

    @Test
    void theWavesStreamOutward() {
        // what is at radius r now is at r + speed * dt a moment later, in the same direction
        double dt = 1.5;
        for (double a = 0; a < 6.28; a += 0.7) for (double r = 30; r < 300; r += 37) {
            double c = Math.cos(a), s = Math.sin(a), r2 = r + WardField.SPEED * dt;
            assertEquals(WardField.wave(c * r, s * r, 10), WardField.wave(c * r2, s * r2, 10 + dt), 1e-9, "a " + a + " r " + r);
        }
    }

    @Test
    void wavesAreSoftSwellsBetweenClearAndFull() {
        double min = 1, max = 0;
        for (int i = -30; i <= 30; i++) for (int j = -30; j <= 30; j++) {
            double w = WardField.wave(i * 11.7, j * 11.7, 3.3);
            min = Math.min(min, w); max = Math.max(max, w);
        }
        assertTrue(min < 0.01, "clear between the walls, min " + min);
        assertTrue(max > 0.95, "full on the walls, max " + max);
    }
}
