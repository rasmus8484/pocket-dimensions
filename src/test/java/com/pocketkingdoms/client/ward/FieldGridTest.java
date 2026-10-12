package com.pocketkingdoms.client.ward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The ward field's polar grid: fixed per layout, within the vertex budget, ending on the square's edge. */
class FieldGridTest {

    private static Vault.Layout r25() { return Vault.build(392.5, 392.5, 0, 0, 784, 784); }
    private static Vault.Layout r50() { return Vault.build(792.5, 792.5, 0, 0, 1584, 1584); }

    @Test
    void noPointLiesOutsideTheSquare() {
        for (Vault.Layout l : new Vault.Layout[]{r25(), r50()}) {
            FieldGrid g = FieldGrid.build(l);
            double square = l.half() - 15;
            for (int p = 0; p < g.points(); p++)
                assertTrue(Math.max(Math.abs(g.x(p)), Math.abs(g.z(p))) <= square + 1e-6, "point " + p);
        }
    }

    @Test
    void theOuterRingIsTheSquaresEdge() {
        Vault.Layout l = r25();
        FieldGrid g = FieldGrid.build(l);
        for (int s = 0; s < FieldGrid.SECTORS; s++) {
            int p = g.index(FieldGrid.RINGS, s);
            assertEquals(l.half() - 15, Math.max(Math.abs(g.x(p)), Math.abs(g.z(p))), 1e-6);
        }
    }

    @Test
    void pointsSitJustUnderTheDome() {
        Vault.Layout l = r25();
        FieldGrid g = FieldGrid.build(l);
        for (int p = 0; p < g.points(); p += 37)
            assertEquals(l.dome(Math.hypot(g.x(p), g.z(p))) + 5, g.y(p), 1e-4);
    }

    @Test
    void withinTheVertexBudget() {
        for (Vault.Layout l : new Vault.Layout[]{r25(), r50()}) {
            FieldGrid g = FieldGrid.build(l);
            assertTrue(g.points() <= 7000, "noise evaluations per frame: " + g.points());
            float[] a = new float[g.points()];
            for (double t = 0; t < 60; t += 7.3) {
                g.alphas(l.half(), t, 1, a);
                int quads = 0;
                for (int ring = 0; ring < FieldGrid.RINGS; ring++)
                    for (int s = 0; s < FieldGrid.SECTORS; s++) if (g.drawn(a, ring, s)) quads++;
                assertTrue(quads * 4 < 20000, "vertices " + quads * 4 + " at t " + t);
            }
        }
    }

    @Test
    void alphasAreTheFieldAtEachPoint() {
        Vault.Layout l = r25();
        FieldGrid g = FieldGrid.build(l);
        float[] a = new float[g.points()];
        g.alphas(l.half(), 12.5, 0.5f, a);
        for (int p = 0; p < g.points(); p += 53)
            assertEquals(WardField.alpha(l.half(), g.x(p), g.z(p), 12.5) * 0.5, a[p], 1e-6);
    }
}
