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
    void itHasASealFourRingsAndABand() {
        SigilLayout.Sigil s = SigilLayout.build(392, 392, MIN, MIN, MAX, MAX);
        assertTrue(s.runes().stream().anyMatch(r -> r.kind() == SigilLayout.Kind.SEAL));
        assertEquals(4, s.runes().stream().filter(r -> r.kind() == SigilLayout.Kind.RING).mapToInt(SigilLayout.Rune::ring).distinct().count());
        assertTrue(s.runes().stream().anyMatch(r -> r.kind() == SigilLayout.Kind.BAND));
        for (SigilLayout.Rune r : s.runes()) assertTrue(r.glyph() >= 0 && r.glyph() < 6);
    }

    @Test
    void fourSpiralArmsCurlOutFromTheSeal() {
        SigilLayout.Sigil s = SigilLayout.build(392, 392, MIN, MIN, MAX, MAX);
        var arms = s.lines().stream().filter(l -> l.kind() == SigilLayout.Kind.ARM).toList();
        assertEquals(4, arms.stream().mapToInt(SigilLayout.Line::ring).distinct().count());
        for (int arm = 0; arm < 4; arm++) {
            int a = arm;
            var segs = arms.stream().filter(l -> l.ring() == a).toList();
            assertTrue(segs.size() > 10, "arm " + a + " has " + segs.size() + " segments");
            double first = Math.hypot(segs.get(0).x1() - 392, segs.get(0).z1() - 392);
            double last = Math.hypot(segs.get(segs.size() - 1).x2() - 392, segs.get(segs.size() - 1).z2() - 392);
            assertTrue(last > first * 5, "it reaches out: " + first + " to " + last);
            for (int i = 1; i < segs.size(); i++) {
                assertTrue(segs.get(i).fade() <= segs.get(i - 1).fade(), "fades outward");
                assertTrue(segs.get(i).along() > segs.get(i - 1).along(), "distance along grows");
            }
            // a spiral, not a spoke: the direction from the centre turns along the arm
            double a0 = Math.atan2(segs.get(0).z1() - 392, segs.get(0).x1() - 392);
            double a1 = Math.atan2(segs.get(segs.size() - 1).z2() - 392, segs.get(segs.size() - 1).x2() - 392);
            double turned = Math.abs(Math.atan2(Math.sin(a1 - a0), Math.cos(a1 - a0)));
            assertTrue(turned > 0.5, "turned " + turned);
        }
    }

    @Test
    void aHexagramTurnsInTheSeal() {
        SigilLayout.Sigil s = SigilLayout.build(392, 392, MIN, MIN, MAX, MAX);
        var star = s.lines().stream().filter(l -> l.kind() == SigilLayout.Kind.STAR).toList();
        assertEquals(6, star.size());
        for (SigilLayout.Line l : star) assertTrue(Math.hypot(l.x1() - 392, l.z1() - 392) < 40, "inside the seal");
    }

    @Test
    void armsAndSealTurnSlowly() {
        assertTrue(SigilLayout.turnArms(100) > 0);
        assertTrue(SigilLayout.turnSeal(100) != 0);
        assertEquals(0, SigilLayout.turnArms(0), 1e-12);
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
