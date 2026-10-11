package com.pocketdimensions.client.ward;

import java.util.List;

/**
 * The Vaulted Ward's layout over a realm: a dome of runes centred on the plot, with a crown at the top, three tiers at
 * rising heights (each with worlds set into it), and a band along the Veil. Sizes are for a radius 25 realm and scale
 * down for smaller ones; a realm too small for the tiers keeps only the crown and the band. Plain logic, unit tested;
 * VaultFrame places the runes, the reference is design/ward-sigil-studies.html.
 */
public final class Vault {

    /** The outer tier's radius in the radius 25 realm the design was drawn for. */
    public static final double REF_OUTER = 362;
    /** The crown's height, and how far from the centre (times the scale) a Core's beam still rises into it. */
    public static final double CROWN_Y = 384, CROWN_REACH = 35;
    private static final double MIN_OUTER = 60, TURN = 0.008;              // TURN: radians per second for the inner tier

    /** A tier: its radius and height, its worlds (count, radius, heart rune size) and its index (0 innermost). */
    public record Tier(int index, double radius, double height, int worlds, double worldRadius, double heart) {}

    /**
     * The layout: the plot's centre and half-width, the outer tier's radius, the size scale, the Core's position (its
     * beam), and the tiers.
     */
    public record Layout(double cx, double cz, double half, double outer, double scale, double coreX, double coreZ, List<Tier> tiers) {
        /** The dome's height r blocks from the centre: highest over the centre, flat beyond the outer tier. */
        public double dome(double r) {
            return 382 - 58 * Math.pow(Math.min(1, r / outer), 1.5);
        }

        /**
         * Where the Core's beam ends: at the crown (y 384) when the Core stands under it (every Core grown since RL-024
         * stands at the centre), else on the dome above the Core.
         */
        public double beamTop() {
            double d = Math.hypot(coreX - cx, coreZ - cz);
            return d > CROWN_REACH * scale ? dome(d) + 1 : CROWN_Y;
        }
    }

    private Vault() {}

    /** The ward over the plot [minX, maxX) x [minZ, maxZ), whose World Core's beam rises at (coreX, coreZ). */
    public static Layout build(double coreX, double coreZ, int minX, int minZ, int maxX, int maxZ) {
        double cx = (minX + maxX) / 2.0, cz = (minZ + maxZ) / 2.0;
        double half = Math.min(maxX - minX, maxZ - minZ) / 2.0, outer = half - 30, s = Math.min(1, outer / REF_OUTER);
        if (outer < MIN_OUTER) return new Layout(cx, cz, half, Math.max(outer, 1), Math.max(0.2, s), coreX, coreZ, List.of());
        Layout bare = new Layout(cx, cz, half, outer, s, coreX, coreZ, List.of());
        double r0 = outer * 0.36, r1 = outer * 0.62, r2 = outer * 0.88;
        return new Layout(cx, cz, half, outer, s, coreX, coreZ, List.of(
                new Tier(0, r0, bare.dome(r0), 4, 14 * s, 8 * s),
                new Tier(1, r1, bare.dome(r1), 3, 32 * s, 13 * s),
                new Tier(2, r2, bare.dome(r2), 8, 22 * s, 8 * s)));
    }

    /** Radians tier `index` has turned after `seconds`: neighbouring tiers in opposite directions, inner ones faster. */
    public static double tierTurn(int index, double seconds) {
        return (index % 2 == 0 ? 1 : -1) * TURN * seconds / (1 + index * 0.5);
    }
}
