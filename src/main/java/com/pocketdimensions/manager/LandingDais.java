package com.pocketdimensions.manager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;

/**
 * The Stone Dais the World Core lands on (RL-024): a flat disc of heartstone 9 blocks across with a rounded underside 3
 * deep, and six vines of aurora stone winding out from a pool under the Core to the edge and down its side. Offsets are
 * from the Core's column; heights are world y. Plain logic, unit tested; RealmManager reads the ground and sets the
 * blocks. Ported from the approved studies page (core-landing-studies.html, version 18, "Stone Dais").
 */
public final class LandingDais {

    /** The disc's radius, the underside's depth, and the radius of the open cylinder cleared above it. */
    public static final double RADIUS = 4.5, DEPTH = 3, CLEAR = RADIUS + 1;
    /** The number of vines. */
    public static final int VINES = 6;
    /** How far out (in whole blocks) the dais or its open cylinder reaches from the Core's column. */
    public static final int REACH = 5;
    /** The ring just outside the open cylinder where standing fluid is sealed in, so it can't pour onto the dais. */
    public static final double WALL = CLEAR + 1;
    /** How far out (in whole blocks) the sealing ring reaches: the centre is block 8 of its chunk, so 2..14 stay in it. */
    public static final int WALL_REACH = 6;

    public enum Kind { HEARTSTONE, AURORA_STONE }

    /** One block of the dais: offset from the Core's column, world y, and what it is. */
    public record Block(int dx, int y, int dz, Kind kind) {}

    /** The floor's height (the Core stands at floor + 1) and every block of the dais. */
    public record Plan(int floor, List<Block> blocks) {}

    private LandingDais() {}

    /**
     * The dais for plot `plot` (its vines are its own), over ground where `firstAir(dx, dz)` is the height of the first
     * air above the ground in that column (leaves, logs and plants skipped; water counts as ground).
     */
    public static Plan plan(int plot, IntBinaryOperator firstAir) {
        int floor = floor(firstAir);
        Map<Long, Kind> blocks = new LinkedHashMap<>();
        for (int dx = -REACH; dx <= REACH; dx++)
            for (int dz = -REACH; dz <= REACH; dz++)
                for (int below = -(int) DEPTH; below <= 0; below++)
                    if (inDais(dx, below, dz)) blocks.put(key(dx, below, dz), Kind.HEARTSTONE);
        new Vines(blocks, plot).grow();
        List<Block> out = new ArrayList<>(blocks.size());
        blocks.forEach((k, kind) -> out.add(new Block(unX(k), unBelow(k) + floor, unZ(k), kind)));
        return new Plan(floor, out);
    }

    /**
     * The height of the first air above the ground in one column: scanning down from `top` (the first air above
     * everything there) to `minY`, the block above the first one `ground` accepts; `minY` if none is ground.
     */
    public static int firstAir(int top, int minY, IntPredicate ground) {
        for (int y = top - 1; y >= minY; y--) if (ground.test(y)) return y + 1;
        return minY;
    }

    /**
     * What the dais rests on: a fluid (on the ocean it floats on the water), or anything solid that does not burn.
     * Burning rules out logs, leaves, bamboo, huge mushrooms and bee nests, so in a forest the floor is the forest floor.
     */
    public static boolean isGround(boolean fluid, boolean solid, boolean burns) {
        return fluid || (solid && !burns);
    }

    /** Whether column (dx, dz) is in the ring just outside the open cylinder, where fluids above the floor are sealed. */
    public static boolean inWall(int dx, int dz) {
        double r = Math.hypot(dx, dz);
        return r >= CLEAR && r < WALL;
    }

    /** The floor's height: the average height of the first air above the ground under the disc, rounded. */
    static int floor(IntBinaryOperator firstAir) {
        long sum = 0;
        int n = 0;
        for (int dx = -REACH; dx <= REACH; dx++)
            for (int dz = -REACH; dz <= REACH; dz++)
                if (Math.hypot(dx, dz) < RADIUS) { sum += firstAir.applyAsInt(dx, dz); n++; }
        return (int) Math.round((double) sum / n);
    }

    /** Whether offset (dx, dz), `below` blocks from the floor (0 = the floor, negative = under it), is in the dais. */
    public static boolean inDais(int dx, int below, int dz) {
        return below <= 0 && Math.hypot(Math.hypot(dx, dz) / RADIUS, -below / DEPTH) < 1;
    }

    /** The vines, as on the studies page: a pool under the Core, then six winding paths that spill over the edge. */
    private static final class Vines {
        private final Map<Long, Kind> blocks;
        private final int plot;

        Vines(Map<Long, Kind> blocks, int plot) { this.blocks = blocks; this.plot = plot; }

        void grow() {
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (Math.hypot(dx, dz) < 1.6) vine(dx, dz);                          // the pool under the Core
            for (int i = 0; i < VINES; i++) {
                double a = i * Math.PI * 2 / VINES + (hsh(i, plot, 1, 99) - 0.5) * 0.5;
                walk(Math.cos(a) * 1.2, Math.sin(a) * 1.2, a, true, plot * 8 + i + 1, 1);
            }
        }

        private void vine(int dx, int dz) { if (inDais(dx, 0, dz)) blocks.put(key(dx, 0, dz), Kind.AURORA_STONE); }

        private void walk(double x, double z, double h, boolean wide, int seed, int branches) {
            int[] last = null;
            for (int k = 0; k < 40; k++) {
                h += (vn(k * 0.25, seed, 98) - 0.5) * 0.45;                              // a slow wander
                x += Math.cos(h) * 0.5;
                z += Math.sin(h) * 0.5;
                int gx = (int) Math.floor(x), gz = (int) Math.floor(z);
                if (!inDais(gx, 0, gz)) { if (last != null) spill(last[0], last[1], h); return; }
                vine(gx, gz);
                last = new int[]{gx, gz};
                double r = Math.hypot(x, z);
                if (wide && r < 2.2) vine((int) Math.floor(x - Math.sin(h) * 0.9), (int) Math.floor(z + Math.cos(h) * 0.9));   // two wide near the Core
                if (branches > 0 && r > 1.8 && hsh(seed, k, 3, 99) < 0.08) {
                    branches--;
                    double turn = (hsh(seed, k, 4, 99) < 0.5 ? -1 : 1) * (0.5 + 0.4 * hsh(seed, k, 5, 99));
                    walk(x, z, h + turn, false, seed * 7 + k, 0);
                }
            }
        }

        /** At the edge a vine trickles down the dais's side: the outward-facing blocks under its last floor block. */
        private void spill(int dx, int dz, double h) {
            int ox = (int) Math.round(Math.cos(h)), oz = (int) Math.round(Math.sin(h));
            for (int below = -1; below >= -(int) DEPTH; below--)
                if (blocks.get(key(dx, below, dz)) == Kind.HEARTSTONE && !inDais(dx + ox, below, dz + oz))
                    blocks.put(key(dx, below, dz), Kind.AURORA_STONE);
        }
    }

    /** The studies page's integer hash, 0..1. */
    static double hsh(int i, int j, int k, int s) {
        int n = (i * 374761393) ^ (j * 668265263) ^ (k * 1274126177) ^ (s * 1442695041);
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;
        return (n & 0xFFFFFFFFL) / 4294967295.0;
    }

    /** Smooth value noise over the hash. */
    static double vn(double x, double y, int s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = smooth(x - xi), fy = smooth(y - yi);
        double a = lerp(hsh(xi, yi, 0, s), hsh(xi + 1, yi, 0, s), fx), b = lerp(hsh(xi, yi + 1, 0, s), hsh(xi + 1, yi + 1, 0, s), fx);
        return lerp(a, b, fy);
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    // an offset packed into one long: dx and dz in 16 bits each, the height below the floor in the top half
    private static long key(int dx, int below, int dz) { return ((long) below << 32) | ((dx & 0xFFFFL) << 16) | (dz & 0xFFFFL); }

    private static int unX(long k) { return (short) (k >> 16); }

    private static int unZ(long k) { return (short) k; }

    private static int unBelow(long k) { return (int) (k >> 32); }
}
