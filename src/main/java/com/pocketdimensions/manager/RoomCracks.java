package com.pocketdimensions.manager;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a pocket room cracks while its anchor is being mined: each set of cracks (one per quarter of the mining) sends
 * one bolt in from every wall, floor and ceiling, starting somewhere on that face's void panel and reaching toward the
 * middle of the room; later sets reach further. Room-local block coordinates (the shell is 0..20), static per seed so
 * the cracks never flicker. Pure, tested; client/RoomVoidRenderer draws them as the Anchor Breaker's frozen lightning.
 */
public final class RoomCracks {

    /** The middle of the room, on every axis. */
    public static final float CENTER = RoomShell.SIZE / 2f;

    private RoomCracks() {}

    /** {ax, ay, az, bx, by, bz} per bolt: six per set, in RoomShell.PANELS order (floor, ceiling, west, east, north, south). */
    public static List<float[]> bolts(int sets, long seed) {
        List<float[]> out = new ArrayList<>();
        for (int st = 0; st < sets; st++) {
            for (int f = 0; f < 6; f++) {
                float[] p = RoomShell.PANELS[f], a = new float[3];
                for (int c = 0; c < 3; c++) {
                    float t = 0.2f + 0.6f * hash(seed, st, f, c);           // keep clear of the panel's rim
                    a[c] = p[c] == p[c + 3] ? p[c] : p[c] + (p[c + 3] - p[c]) * t;
                }
                float reach = 0.3f + 0.15f * st + 0.05f * hash(seed, st, f, 3);   // 30-35 / 45-50 / 60-65 % of the way in
                out.add(new float[]{a[0], a[1], a[2],
                        a[0] + (CENTER - a[0]) * reach, a[1] + (CENTER - a[1]) * reach, a[2] + (CENTER - a[2]) * reach});
            }
        }
        return out;
    }

    /** Deterministic 0..1. */
    private static float hash(long seed, int a, int b, int c) {
        long h = seed * 0x9E3779B97F4A7C15L + a * 0xBF58476D1CE4E5B9L + b * 0x94D049BB133111EBL + c * 0x2545F4914F6CDD1DL;
        h ^= h >>> 31; h *= 0x7FB5D329728EA185L; h ^= h >>> 27; h *= 0x81DADEF4BC2DD44DL; h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }
}
