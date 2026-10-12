package com.pocketkingdoms.client.veil;

/**
 * The Veil fog's noise, the same as the study's: 32 x 32 x 32 tileable value noise (3 octaves, period 4 lattice cells),
 * two fields (seeds 71 and 83). The game's renderer has no 3D textures, so the 32 slices (along the depth axis) sit side
 * by side in one 2D atlas, each 34 pixels wide: one copied edge pixel each side, so linear filtering never blends into
 * the next slice. A pixel of slice k holds (a[k], a[k+1], b[k], b[k+1]): one read gives a field at two neighbouring
 * slices, and the shader blends them. Plain logic, unit tested; VeilFogRenderer uploads it.
 */
public final class VeilNoise {

    public static final int SIZE = 32, TILE = SIZE + 2, ATLAS_W = SIZE * TILE, ATLAS_H = SIZE;
    private static final int SEED_A = 71, SEED_B = 83, PERIOD = 4;

    private VeilNoise() {}

    /** A repeatable hash of a lattice point, in [0, 1] (the study's hsh, bit for bit). */
    static double hash(int i, int j, int k, int seed) {
        int n = (i * 374761393) ^ (j * 668265263) ^ (k * 1274126177) ^ (seed * 1442695041);
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;
        return (n & 0xFFFFFFFFL) / 4294967295.0;
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    private static int wrap(int a, int period) { return Math.floorMod(a, period); }

    /** Value noise on a lattice that repeats every `period` cells. */
    private static double periodic(double x, double y, double z, int period, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double fx = smooth(x - xi), fy = smooth(y - yi), fz = smooth(z - zi);
        double[] c = new double[8];
        for (int n = 0; n < 8; n++)
            c[n] = hash(wrap(xi + (n & 1), period), wrap(yi + ((n >> 1) & 1), period), wrap(zi + ((n >> 2) & 1), period), seed);
        double x00 = c[0] + (c[1] - c[0]) * fx, x10 = c[2] + (c[3] - c[2]) * fx;
        double x01 = c[4] + (c[5] - c[4]) * fx, x11 = c[6] + (c[7] - c[6]) * fx;
        double y0 = x00 + (x10 - x00) * fy, y1 = x01 + (x11 - x01) * fy;
        return y0 + (y1 - y0) * fz;
    }

    /** Three octaves, each still repeating every 4 units. */
    static double fbm(double x, double y, double z, int seed) {
        double sum = 0, amp = 0.5, norm = 0;
        for (int o = 0, f = 1; o < 3; o++, f *= 2) {
            sum += amp * periodic(x * f, y * f, z * f, PERIOD * f, seed + o * 17);
            norm += amp;
            amp *= 0.5;
        }
        return sum / norm;
    }

    /** One field, stretched to 0..255; index (z * SIZE + y) * SIZE + x. */
    public static int[] field(int seed) {
        double[] v = new double[SIZE * SIZE * SIZE];
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int z = 0; z < SIZE; z++) for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            double n = fbm(x / 8.0, y / 8.0, z / 8.0, seed);
            v[(z * SIZE + y) * SIZE + x] = n;
            lo = Math.min(lo, n);
            hi = Math.max(hi, n);
        }
        int[] out = new int[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (int) Math.round((v[i] - lo) / (hi - lo) * 255);
        return out;
    }

    /** The atlas as ARGB pixels, ATLAS_W x ATLAS_H, row-major. */
    public static int[] atlas() {
        int[] a = field(SEED_A), b = field(SEED_B), out = new int[ATLAS_W * ATLAS_H];
        for (int k = 0; k < SIZE; k++) {
            int k2 = (k + 1) % SIZE;
            for (int y = 0; y < SIZE; y++) for (int c = 0; c < TILE; c++) {
                int x = Math.floorMod(c - 1, SIZE), i = (k * SIZE + y) * SIZE + x, i2 = (k2 * SIZE + y) * SIZE + x;
                out[y * ATLAS_W + k * TILE + c] = (b[i2] << 24) | (a[i] << 16) | (a[i2] << 8) | b[i];
            }
        }
        return out;
    }
}
