package com.pocketdimensions.client.veil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Veil fog's closed forms (Java twins of veil_fog.fsh): the smoke between two points of a view, without steps. */
class VeilFogMathTest {

    /** The same amount by brute force: 10,000 midpoint steps of e^(-d/H). */
    private static double summed(double dc, double rx, double ta, double tb) {
        int n = 10_000;
        double dt = (tb - ta) / n, s = 0;
        for (int i = 0; i < n; i++) s += Math.exp(-(dc + rx * (ta + (i + 0.5) * dt)) / VeilFogMath.H) * dt;
        return s;
    }

    @Test
    void amountMatchesSummingTowardTheWall() {
        assertEquals(summed(20, -0.8, 0, 24), VeilFogMath.amount(20, -0.8, 0, 24), summed(20, -0.8, 0, 24) * 1e-3);
    }

    @Test
    void amountMatchesSummingAwayFromTheWall() {
        assertEquals(summed(2, 0.5, 0, 30), VeilFogMath.amount(2, 0.5, 0, 30), summed(2, 0.5, 0, 30) * 1e-3);
    }

    @Test
    void amountAlongTheWallHasNoDivisionByZero() {
        assertEquals(Math.exp(-3 / VeilFogMath.H) * 100, VeilFogMath.amount(3, 0, 0, 100), 1e-9);
        assertEquals(summed(3, 0.0005, 0, 100), VeilFogMath.amount(3, 0.0005, 0, 100), summed(3, 0.0005, 0, 100) * 1e-3);
    }

    @Test
    void anEmptyStretchHasNoSmoke() {
        assertEquals(0, VeilFogMath.amount(5, -1, 4, 4));
        assertEquals(0, VeilFogMath.amount(5, -1, 6, 4));
    }

    @Test
    void theMiddleSplitsTheSmokeInTwo() {
        double[][] cases = {{20, -0.8, 0, 24}, {2, 0.5, 0, 30}, {9, -0.3, 1, 25}};
        for (double[] c : cases) {
            double m = VeilFogMath.middle(c[0], c[1], c[2], c[3]);
            double before = VeilFogMath.amount(c[0], c[1], c[2], m), after = VeilFogMath.amount(c[0], c[1], m, c[3]);
            assertEquals(before, after, (before + after) * 1e-6, "case " + java.util.Arrays.toString(c));
        }
    }

    @Test
    void clipFindsWhereTheViewIsNearTheWall() {
        double[] r = VeilFogMath.clip(0, 10, 30, -1, 0, 100);
        assertEquals(20, r[0], 1e-9);
        assertEquals(30, r[1], 1e-9);
    }

    @Test
    void clipIsEmptyWhenTheViewNeverComesNear() {
        double[] r = VeilFogMath.clip(0, 10, 30, 0.2, 0, 100);
        assertTrue(r[1] <= r[0]);
    }

    @Test
    void clipStartsAtTheCameraWhenItStandsInsideTheSmoke() {
        double[] r = VeilFogMath.clip(0, VeilFogMath.XMAX, 5, 0.5, 0, 100);   // 5 blocks from the wall, walking away
        assertEquals(0, r[0], 1e-9);
        assertEquals(38, r[1], 1e-9);
    }

    @Test
    void clipAlongTheWallKeepsTheWholeViewOrNothing() {
        assertArrayEquals(new double[] {0, 50}, VeilFogMath.clip(0, 10, 4, 0, 0, 50), 1e-9);
        assertTrue(VeilFogMath.clip(0, 10, 14, 0, 0, 50)[1] <= VeilFogMath.clip(0, 10, 14, 0, 0, 50)[0]);
    }

    @Test
    void theSmokeGoesAllTheWayUpAndFadesOnlyInTheLastBlocksBelowTheTop() {
        assertEquals(304, VeilFogMath.fadeStart(-64, 384), 1e-9);
        assertEquals(320, VeilFogMath.fadeEnd(-64, 384), 1e-9);
        assertEquals(240, VeilFogMath.fadeStart(0, 256), 1e-9);
        assertEquals(256, VeilFogMath.fadeEnd(0, 256), 1e-9);
    }
}
