package com.pocketkingdoms.client.ward;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * Where every rune of the Vaulted Ward is at a moment in time: the crown's three rings, each tier's main track (with a
 * gap where each world sits), its worlds (carried round with it, each spinning), and the band
 * over the Veil. Positions are in world coordinates; sizes and the reference speeds come from
 * design/ward-sigil-studies.html. Plain logic, unit tested; WardSigilRenderer draws the result.
 */
public final class VaultFrame {

    /** One rune: stable id (for cracks), centre, size in blocks, turn about the vertical, sprite 0..5, opacity factor. */
    public record Rune(int id, double x, double y, double z, double size, double angle, int glyph, double alpha) {}

    private static final double TAU = Math.PI * 2;

    private VaultFrame() {}

    /**
     * Adds the ward's runes at `seconds` to `out`. Not turning (the anchor is lost), everything stands as at time 0.
     * Runes whose id `hidden` accepts are left out (the cracks of a breach).
     */
    public static void runes(Vault.Layout l, double seconds, boolean turning, IntPredicate hidden, List<Rune> out) {
        double t = turning ? seconds : 0, s = l.scale();
        // the crown: three nested rings at the top
        ring(out, hidden, l.cx(), l.cz(), 384, 9 * s, 4.5 * s, 6 * s, t * 0.05, 10, 1);
        ring(out, hidden, l.cx(), l.cz(), 383, 17 * s, 5.5 * s, 8 * s, -t * 0.03, 40, 1);
        ring(out, hidden, l.cx(), l.cz(), 382, 27 * s, 6.5 * s, 10 * s, t * 0.02, 80, 1);

        for (Vault.Tier tier : l.tiers()) tier(out, hidden, l, tier, t);
        band(out, hidden, l);
    }

    /** The centres (x, z) of a tier's worlds at `seconds`: evenly spaced, carried round with the tier. */
    public static double[][] worldCentres(Vault.Layout l, Vault.Tier tier, double seconds, boolean turning) {
        double turn = Vault.tierTurn(tier.index(), turning ? seconds : 0);
        double[][] c = new double[tier.worlds()][];
        for (int i = 0; i < tier.worlds(); i++) {
            double a = i * TAU / tier.worlds() + turn;
            c[i] = new double[]{l.cx() + Math.cos(a) * tier.radius(), l.cz() + Math.sin(a) * tier.radius()};
        }
        return c;
    }

    private static void tier(List<Rune> out, IntPredicate hidden, Vault.Layout l, Vault.Tier tier, double t) {
        int k = tier.index();
        double s = l.scale(), r = tier.radius(), y = tier.height(), wr = tier.worldRadius(), turn = Vault.tierTurn(k, t);
        double[] worlds = new double[tier.worlds()];
        for (int i = 0; i < worlds.length; i++) worlds[i] = i * TAU / worlds.length + turn;

        // the main track, with a gap round each world
        int n = Math.max(3, (int) Math.floor(TAU * r / (15 * s)));
        for (int i = 0; i < n; i++) {
            int id = 1000 + k * 10000 + i;
            double a = i * TAU / n + turn;
            if (hidden.test(id) || !clearOf(a, r, worlds, wr + 8 * s)) continue;
            out.add(new Rune(id, l.cx() + Math.cos(a) * r, y, l.cz() + Math.sin(a) * r, 10 * s, a + Math.PI / 2, glyph(id), 1));
        }

        // the worlds, carried round with the tier, each spinning on itself
        boolean big = wr > 25 * s;
        for (int i = 0; i < worlds.length; i++) {
            double a = worlds[i], wx = l.cx() + Math.cos(a) * r, wz = l.cz() + Math.sin(a) * r, wy = y + 2;
            int id = 50000 + k * 1000 + i * 100;
            ring(out, hidden, wx, wz, wy, wr, (big ? 6.5 : 5.5) * s, (big ? 9 : 8) * s, t * 0.12 * (i % 2 == 0 ? 1 : -1), id, 1);
            ring(out, hidden, wx, wz, wy, wr * 0.55, (big ? 4.5 : 3.5) * s, (big ? 7 : 6) * s, -t * 0.2, id + 50, 0.8);
            if (!hidden.test(id + 90)) out.add(new Rune(id + 90, wx, wy, wz, tier.heart(), a, glyph(i), 1));
        }
    }

    /** The band over the Veil: a row of runes and a row of tiny runes just inside it. */
    private static void band(List<Rune> out, IntPredicate hidden, Vault.Layout l) {
        double s = l.scale(), size = 10 * s, y = l.dome(l.outer() + 24), e = l.half() - 6 - size / 2;
        int id = 90000;
        double step = 14 * s;
        for (double x = -e; x <= e + 1e-9; x += step) {
            id = add(out, hidden, id, l.cx() + x, y, l.cz() - e, size, 0, 1);
            id = add(out, hidden, id, l.cx() + x, y, l.cz() + e, size, Math.PI, 1);
        }
        for (double z = -e + step; z <= e - step + 1e-9; z += step) {
            id = add(out, hidden, id, l.cx() - e, y, l.cz() + z, size, -Math.PI / 2, 1);
            id = add(out, hidden, id, l.cx() + e, y, l.cz() + z, size, Math.PI / 2, 1);
        }
        double f = e - size * 1.3, st = 9 * s, tiny = 4.5 * s;
        for (double x = -f; x <= f + 1e-9; x += st) {
            id = add(out, hidden, id, l.cx() + x, y, l.cz() - f, tiny, 0, 0.7);
            id = add(out, hidden, id, l.cx() + x, y, l.cz() + f, tiny, 0, 0.7);
        }
        for (double z = -f + st; z <= f - st + 1e-9; z += st) {
            id = add(out, hidden, id, l.cx() - f, y, l.cz() + z, tiny, 0, 0.7);
            id = add(out, hidden, id, l.cx() + f, y, l.cz() + z, tiny, 0, 0.7);
        }
    }

    private static void ring(List<Rune> out, IntPredicate hidden, double cx, double cz, double y, double r, double size,
                             double spacing, double turn, int id0, double alpha) {
        int n = Math.max(3, (int) Math.floor(TAU * r / spacing));
        for (int i = 0; i < n; i++) {
            int id = id0 + i;
            if (hidden.test(id)) continue;
            double a = i * TAU / n + turn;
            out.add(new Rune(id, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, size, a + Math.PI / 2, glyph(id), alpha));
        }
    }

    private static int add(List<Rune> out, IntPredicate hidden, int id, double x, double y, double z, double size, double angle, double alpha) {
        if (!hidden.test(id)) out.add(new Rune(id, x, y, z, size, angle, glyph(id), alpha));
        return id + 1;
    }

    /** Whether angle a on a circle of radius r is further than `clear` blocks (along it) from every world. */
    private static boolean clearOf(double a, double r, double[] worlds, double clear) {
        for (double wa : worlds) if (Math.abs(Math.atan2(Math.sin(a - wa), Math.cos(a - wa))) * r <= clear) return false;
        return true;
    }

    private static int glyph(int id) { return Math.floorMod(id, 6); }
}
