package com.pocketkingdoms.client.ward;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The Vaulted Ward: its layout over a plot, and where every rune is at a moment in time. */
class VaultTest {

    // radius 25: 784 blocks across, plot from 0 to 784, centre 392
    private static Vault.Layout r25() { return Vault.build(392.5, 392.5, 0, 0, 784, 784); }
    private static Vault.Layout r50() { return Vault.build(792.5, 792.5, 0, 0, 1584, 1584); }

    private static List<VaultFrame.Rune> runes(Vault.Layout l, double t, boolean turning) {
        List<VaultFrame.Rune> out = new ArrayList<>();
        VaultFrame.runes(l, t, turning, id -> false, out);
        return out;
    }

    @Test
    void layoutOfARadius25Realm() {
        Vault.Layout l = r25();
        assertEquals(392, l.cx(), 1e-9);
        assertEquals(362, l.outer(), 1e-9);
        assertEquals(1, l.scale(), 1e-9);
        assertEquals(3, l.tiers().size());
        assertEquals(362 * 0.36, l.tiers().get(0).radius(), 1e-9);
        assertEquals(362 * 0.88, l.tiers().get(2).radius(), 1e-9);
        assertEquals(new int[]{4, 3, 8}[1], l.tiers().get(1).worlds());
        assertEquals(382, l.dome(0), 1e-9);
        assertEquals(324, l.dome(362), 1e-9);
        assertEquals(324, l.dome(500), 1e-9, "flat beyond the outer tier");
        assertTrue(l.tiers().get(0).height() > l.tiers().get(2).height(), "the dome falls outward");
    }

    @Test
    void smallRealmsScaleDownAndTheSmallestKeepOnlyCrownAndBand() {
        Vault.Layout r10 = Vault.build(152.5, 152.5, 0, 0, 304, 304);      // radius 10: half 152, OUTER 122
        assertEquals(122.0 / 362, r10.scale(), 1e-9);
        assertEquals(3, r10.tiers().size());
        Vault.Layout r3 = Vault.build(40.5, 40.5, 0, 0, 80, 80);           // radius 3: OUTER 10
        assertTrue(r3.tiers().isEmpty());
        assertFalse(runes(r3, 5, true).isEmpty(), "crown and band still");
    }

    @Test
    void everyRuneIsInsideThePlot() {
        for (Vault.Layout l : new Vault.Layout[]{r25(), r50(), Vault.build(152.5, 152.5, 0, 0, 304, 304), Vault.build(500, 300, 0, 0, 784, 784)})
            for (VaultFrame.Rune r : runes(l, 37.5, true)) {
                double half = l.half();
                assertTrue(Math.abs(r.x() - l.cx()) + r.size() / 2 <= half && Math.abs(r.z() - l.cz()) + r.size() / 2 <= half, "inside " + r);
            }
    }

    @Test
    void withinBudget() {
        int n25 = runes(r25(), 3, true).size(), n50 = runes(r50(), 3, true).size();
        assertTrue(n25 > 900 && n25 < 2600, "radius 25: " + n25);
        assertTrue(n50 < 6000, "radius 50: " + n50);
    }

    @Test
    void worldsSitInGapsInTheirTrack() {
        Vault.Layout l = r25();
        double t = 21.7;
        for (Vault.Tier tier : l.tiers()) {
            double[][] worlds = VaultFrame.worldCentres(l, tier, t, true);
            for (VaultFrame.Rune r : runes(l, t, true)) {
                if (r.id() < 1000 + tier.index() * 10000 || r.id() >= 2000 + tier.index() * 10000) continue;   // this tier's main track
                for (double[] w : worlds)
                    assertTrue(Math.hypot(r.x() - w[0], r.z() - w[1]) > tier.worldRadius(), "track rune clear of world " + r);
            }
        }
    }

    @Test
    void theTiersHaveNoOutlines() {
        for (VaultFrame.Rune r : runes(r25(), 8.2, true)) {
            int inTier = r.id() % 10000;
            assertFalse(r.id() < 50000 && inTier >= 3000 && inTier < 6000, "outline rune " + r);
        }
    }

    @Test
    void theWorldsHaveNoMoons() {
        for (VaultFrame.Rune r : runes(r25(), 8.2, true))
            assertFalse(r.id() >= 50000 && r.id() < 70000 && r.id() % 100 == 95, "moon " + r);
    }

    @Test
    void theBeamEndsWhereTheConduitStarts() {
        Vault.Layout off = Vault.build(392.5 + 128, 392.5 - 90, 0, 0, 784, 784);
        double d = Math.hypot(128.5, -89.5);
        assertEquals(off.dome(d) + 1, off.beamTop(), 1e-9, "on the dome above the Core");
        assertTrue(off.beamTop() < 384);
        assertEquals(384, r25().beamTop(), 1e-9, "a centred Core's beam rises to the crown");
    }

    @Test
    void worldsTurnWithTheirTier() {
        Vault.Layout l = r25();
        Vault.Tier mid = l.tiers().get(1);
        double[][] a = VaultFrame.worldCentres(l, mid, 0, true), b = VaultFrame.worldCentres(l, mid, 100, true);
        double turn = Vault.tierTurn(1, 100);
        double a0 = Math.atan2(a[0][1] - l.cz(), a[0][0] - l.cx()), b0 = Math.atan2(b[0][1] - l.cz(), b[0][0] - l.cx());
        assertEquals(0, Math.sin(b0 - a0 - turn), 1e-9);
        assertTrue(Vault.tierTurn(0, 100) > 0 && Vault.tierTurn(1, 100) < 0, "neighbouring tiers turn opposite ways");
    }

    @Test
    void aLostAnchorFreezesIt() {
        assertEquals(runes(r25(), 0, false), runes(r25(), 500, false));
    }

    @Test
    void hiddenRunesAreLeftOut() {
        List<VaultFrame.Rune> out = new ArrayList<>();
        VaultFrame.runes(r25(), 3, true, id -> true, out);
        assertTrue(out.isEmpty());
    }

    @Test
    void noConduitRunsToTheCrownEvenForAnOffCentreCore() {
        Vault.Layout off = Vault.build(392.5 + 128, 392.5 - 90, 0, 0, 784, 784);
        assertEquals(0, runes(off, 4, true).stream().filter(r -> r.id() >= 70000 && r.id() < 80000).count());
        assertEquals(0, runes(r25(), 4, true).stream().filter(r -> r.id() >= 70000 && r.id() < 80000).count());
    }
}
