package com.pocketkingdoms.manager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;

/**
 * The Stone Dais the World Core lands on (RL-024): a flat disc of stone 9 blocks across with a rounded underside 3
 * deep, and six vines of aurora stone winding out from a pool under the Core to the edge and down its side, cased in
 * aurora slate (the dais is a bullet's tip, the cylinder cleared above it the body). Offsets are
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
    /** How far out (in whole blocks) the casing reaches: the centre is block 8 of its chunk, so 2..14 stay in it. */
    public static final int CASING_REACH = 6;

    public enum Kind { STONE, AURORA_STONE, AURORA_SLATE }

    /** One block of the dais: offset from the Core's column, world y, and what it is. */
    public record Block(int dx, int y, int dz, Kind kind) {}

    /** A column of the casing's wall: offset from the Core's column; every block of it above the floor is casing. */
    public record Column(int dx, int dz) {}

    /**
     * The floor's height (the Core stands at floor + 1), every block of the dais, and its aurora slate casing: the shell
     * (every casing block at or below the floor, world y) and the wall (the columns cased above the floor, up to the sky).
     */
    public record Plan(int floor, List<Block> blocks, List<Block> shell, List<Column> wall) {}

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
                    if (inDais(dx, below, dz)) blocks.put(key(dx, below, dz), Kind.STONE);
        Vines v = new Vines(blocks, plot);
        v.grow();
        v.roots();
        List<Block> out = new ArrayList<>(blocks.size()), shell = new ArrayList<>();
        blocks.forEach((k, kind) -> out.add(new Block(unX(k), unBelow(k) + floor, unZ(k), kind)));
        for (int dx = -CASING_REACH; dx <= CASING_REACH; dx++)
            for (int dz = -CASING_REACH; dz <= CASING_REACH; dz++)
                for (int rel = -(int) DEPTH - 2; rel <= 0; rel++)
                    if (inShell(dx, rel, dz)) shell.add(new Block(dx, floor + rel, dz, Kind.AURORA_SLATE));
        List<Column> wall = new ArrayList<>();
        for (int dx = -CASING_REACH; dx <= CASING_REACH; dx++)
            for (int dz = -CASING_REACH; dz <= CASING_REACH; dz++)
                if (inWall(dx, dz)) wall.add(new Column(dx, dz));
        return new Plan(floor, out, shell, wall);
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

    /**
     * Whether the block at offset (dx, dz), `rel` blocks above the floor (0 = the floor, negative = under it), is inside
     * the bullet: the dais, or the open cylinder above the floor.
     */
    public static boolean inside(int dx, int rel, int dz) {
        return rel > 0 ? Math.hypot(dx, dz) < CLEAR : inDais(dx, rel, dz);
    }

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] EDGES = edges();

    private static int[][] edges() {
        List<int[]> e = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++)
            if (Math.abs(x) + Math.abs(y) + Math.abs(z) == 2) e.add(new int[]{x, y, z});
        return e.toArray(new int[0][]);
    }

    /**
     * Whether the block at offset (dx, dz), `rel` blocks above the floor (0 or below), is in the casing's shell: outside
     * the bullet, and in a band one block thick round the dais, or touching the inside on a face (airtight), or touching
     * the dais along an edge (no seams where the curve steps).
     */
    public static boolean inShell(int dx, int rel, int dz) {
        if (rel > 0 || inside(dx, rel, dz)) return false;
        if (Math.hypot(Math.hypot(dx, dz) / (RADIUS + 1), -rel / (DEPTH + 1)) < 1) return true;
        for (int[] f : FACES) if (inside(dx + f[0], rel + f[1], dz + f[2])) return true;
        for (int[] e : EDGES) if (inDais(dx + e[0], rel + e[1], dz + e[2])) return true;
        return false;
    }

    /**
     * Whether a block in the casing fuses into aurora slate: anything solid that is not leaves, has no block entity (a
     * chest keeps its contents) and can be broken; a fluid only in the wall above the floor, where it is sealed in so it
     * cannot pour into the cleared cylinder.
     */
    public static boolean fuses(boolean fluid, boolean solid, boolean leaves, boolean entity, boolean unbreakable, boolean wall) {
        if (entity || unbreakable) return false;
        if (fluid) return wall;
        return solid && !leaves;
    }

    /**
     * Whether a fluid in the casing `rel` blocks above the floor is sealed in aurora slate: from the floor up (the ring
     * round the rim and the wall open onto the cleared cylinder, so a lake or lava there would pour in or lie at the
     * rim), but not under the floor (the sea under a floating dais stays sea).
     */
    public static boolean sealsFluids(int rel) {
        return rel >= 0;
    }

    /** Whether column (dx, dz) is in the casing's wall above the floor: a band one block thick round the open cylinder. */
    public static boolean inWall(int dx, int dz) {
        double r = Math.hypot(dx, dz);
        if (r < CLEAR) return false;
        if (r < CLEAR + 1) return true;
        for (int[] f : FACES) if (f[1] == 0 && Math.hypot(dx + f[0], dz + f[2]) < CLEAR) return true;
        return false;
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

        /**
         * Roots: from the floor's aurora stone (the pool's centre, and about one floor block in four) a vein runs down
         * through the dais, now and then stepping one block aside, 3 to 7 blocks deep, turning its stone to aurora stone.
         * It stops where it would leave the dais, so the veins stay in the dais and the casing round it is unbroken.
         */
        void roots() {
            List<int[]> starts = new ArrayList<>();
            for (Map.Entry<Long, Kind> e : blocks.entrySet()) {
                long k = e.getKey();
                if (e.getValue() != Kind.AURORA_STONE || unBelow(k) != 0) continue;
                int dx = unX(k), dz = unZ(k);
                if ((dx == 0 && dz == 0) || hsh(dx, dz, plot, 97) < 0.25) starts.add(new int[]{dx, dz});
            }
            for (int[] s : starts) {
                int x = s[0], z = s[1], seed = hashInt(s[0], s[1], plot);
                int depth = 3 + (int) (hsh(seed, 0, 1, 96) * 5);
                for (int d = 1; d <= depth; d++) {
                    if (hsh(seed, d, 2, 96) < 0.35) {                                    // a step aside
                        int dir = (int) (hsh(seed, d, 3, 96) * 4);
                        x += dir == 0 ? 1 : dir == 1 ? -1 : 0;
                        z += dir == 2 ? 1 : dir == 3 ? -1 : 0;
                    }
                    if (!inDais(x, -d, z)) break;                                    // the veins stay in the dais
                    blocks.put(key(x, -d, z), Kind.AURORA_STONE);
                }
            }
        }

        private static int hashInt(int a, int b, int c) { return (int) (hsh(a, b, c, 95) * Integer.MAX_VALUE); }

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
                if (blocks.get(key(dx, below, dz)) == Kind.STONE && !inDais(dx + ox, below, dz + oz))
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
