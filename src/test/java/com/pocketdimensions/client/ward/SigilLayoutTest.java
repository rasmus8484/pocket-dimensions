package com.pocketdimensions.client.ward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Where the sigil's runes and lines go: centred on the Core, inside the plot, within budget. */
class SigilLayoutTest {

    // radius 25: 49 chunks = 784 blocks
    private static final int MIN = 0, MAX = 784;

    private static void allInside(SigilLayout.Sigil s, int min, int max) {
        for (SigilLayout.Rune r : s.runes()) {
            double dist = Math.hypot(r.x() - s.cx(), r.z() - s.cz());
            double toEdge = Math.min(Math.min(s.cx() - min, max - s.cx()), Math.min(s.cz() - min, max - s.cz()));
            if (r.kind() == SigilLayout.Kind.BAND) {
                assertTrue(r.x() - r.size() / 2 >= min && r.x() + r.size() / 2 <= max, "band x " + r);
                assertTrue(r.z() - r.size() / 2 >= min && r.z() + r.size() / 2 <= max, "band z " + r);
            } else {
                // turning keeps a rune's distance from the centre; its corners reach at most size * 0.71 further
                assertTrue(dist + r.size() <= toEdge, "turning piece stays inside at any angle " + r);
            }
        }
        for (SigilLayout.Line l : s.lines()) {
            for (double[] p : new double[][]{{l.x1(), l.z1()}, {l.x2(), l.z2()}}) {
                assertTrue(p[0] >= min && p[0] <= max && p[1] >= min && p[1] <= max, "line " + l);
            }
        }
    }

    @Test
    void aCentredCoreFitsInsideThePlot() {
        allInside(SigilLayout.build(392, 392, MIN, MIN, MAX, MAX), MIN, MAX);
    }

    @Test
    void aCoreFarOffCentreStillFits() {
        allInside(SigilLayout.build(392 + 128, 392 - 128, MIN, MIN, MAX, MAX), MIN, MAX);
    }

    @Test
    void aSmallRealmFits() {
        allInside(SigilLayout.build(40, 40, 0, 0, 80, 80), 0, 80);        // radius 3
    }

    @Test
    void itHasASealFourRingsEightSpokesAndABand() {
        SigilLayout.Sigil s = SigilLayout.build(392, 392, MIN, MIN, MAX, MAX);
        assertTrue(s.runes().stream().anyMatch(r -> r.kind() == SigilLayout.Kind.SEAL));
        assertEquals(4, s.runes().stream().filter(r -> r.kind() == SigilLayout.Kind.RING).mapToInt(SigilLayout.Rune::ring).distinct().count());
        assertTrue(s.runes().stream().anyMatch(r -> r.kind() == SigilLayout.Kind.BAND));
        assertEquals(8, s.lines().stream().filter(l -> l.kind() == SigilLayout.Kind.SPOKE).map(l -> Math.round(Math.toDegrees(Math.atan2(l.z2() - l.z1(), l.x2() - l.x1())))).distinct().count());
        for (SigilLayout.Rune r : s.runes()) assertTrue(r.glyph() >= 0 && r.glyph() < 6);
    }

    @Test
    void runesOnARingDoNotOverlap() {
        SigilLayout.Sigil s = SigilLayout.build(392, 392, MIN, MIN, MAX, MAX);
        var ring0 = s.runes().stream().filter(r -> r.kind() == SigilLayout.Kind.RING && r.ring() == 0).toList();
        double r = ring0.get(0).radius(), size = ring0.get(0).size();
        assertTrue(2 * Math.PI * r / ring0.size() >= size * 1.2, "spacing");
    }

    @Test
    void budget() {
        SigilLayout.Sigil big = SigilLayout.build(792, 792, 0, 0, 1584, 1584);            // radius 50
        assertTrue(big.runes().size() < 2500, "runes " + big.runes().size());
        assertTrue(big.lines().size() < 1500, "lines " + big.lines().size());
    }

    @Test
    void ringsTurnInAlternatingDirections() {
        assertTrue(SigilLayout.turn(0, 100) > 0);
        assertTrue(SigilLayout.turn(1, 100) < 0);
        assertEquals(0, SigilLayout.turn(0, 0), 1e-12);
    }
}
