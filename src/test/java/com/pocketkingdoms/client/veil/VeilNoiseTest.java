package com.pocketkingdoms.client.veil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Veil fog's noise: the study's 32 x 32 x 32 tileable noise, laid out as a 2D atlas the game can upload. */
class VeilNoiseTest {

    private static int r(int argb) { return (argb >> 16) & 0xFF; }
    private static int g(int argb) { return (argb >> 8) & 0xFF; }
    private static int b(int argb) { return argb & 0xFF; }
    private static int a(int argb) { return (argb >>> 24) & 0xFF; }
    private static int at(int[] f, int x, int y, int z) { return f[(z * VeilNoise.SIZE + y) * VeilNoise.SIZE + x]; }

    @Test
    void hashIsInUnitRangeAndRepeatable() {
        for (int i = -5; i < 40; i++) {
            double h = VeilNoise.hash(i, 2 * i, -i, 71);
            assertTrue(h >= 0 && h <= 1, "hash " + h);
            assertEquals(h, VeilNoise.hash(i, 2 * i, -i, 71));
        }
        assertNotEquals(VeilNoise.hash(1, 2, 3, 71), VeilNoise.hash(1, 2, 3, 83));
    }

    @Test
    void noiseRepeatsEveryFourUnitsOnEveryAxis() {
        for (double x = 0.13; x < 4; x += 0.71) {
            double v = VeilNoise.fbm(x, 1.37, 2.9, 71);
            assertEquals(v, VeilNoise.fbm(x + 4, 1.37, 2.9, 71), 1e-12);
            assertEquals(v, VeilNoise.fbm(x, 1.37 + 4, 2.9, 71), 1e-12);
            assertEquals(v, VeilNoise.fbm(x, 1.37, 2.9 + 4, 71), 1e-12);
        }
    }

    @Test
    void fieldUsesTheWholeByteRange() {
        int[] f = VeilNoise.field(71);
        assertEquals(32 * 32 * 32, f.length);
        int lo = 255, hi = 0;
        for (int v : f) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        assertEquals(0, lo);
        assertEquals(255, hi);
    }

    @Test
    void atlasHasTheRightSize() {
        assertEquals(VeilNoise.ATLAS_W * VeilNoise.ATLAS_H, VeilNoise.atlas().length);
        assertEquals(1088, VeilNoise.ATLAS_W);
        assertEquals(32, VeilNoise.ATLAS_H);
    }

    @Test
    void eachPixelHoldsItsSliceAndTheNextOneForBothFields() {
        int[] atlas = VeilNoise.atlas(), fa = VeilNoise.field(71), fb = VeilNoise.field(83);
        for (int k : new int[] {0, 5, 31}) for (int y : new int[] {0, 17, 31}) for (int x : new int[] {0, 9, 31}) {
            int p = atlas[y * VeilNoise.ATLAS_W + k * VeilNoise.TILE + 1 + x], k2 = (k + 1) % 32;
            assertEquals(at(fa, x, y, k), r(p), "a[k] at " + k + "," + y + "," + x);
            assertEquals(at(fa, x, y, k2), g(p), "a[k+1]");
            assertEquals(at(fb, x, y, k), b(p), "b[k]");
            assertEquals(at(fb, x, y, k2), a(p), "b[k+1]");
        }
    }

    @Test
    void edgePixelsCopyTheOppositeSideSoFilteringWraps() {
        int[] atlas = VeilNoise.atlas();
        for (int k = 0; k < 32; k++) for (int y = 0; y < 32; y++) {
            int row = y * VeilNoise.ATLAS_W + k * VeilNoise.TILE;
            assertEquals(atlas[row + 1 + 31], atlas[row], "left edge copies column 31, slice " + k);
            assertEquals(atlas[row + 1], atlas[row + 33], "right edge copies column 0, slice " + k);
        }
    }

    @Test
    void theAtlasIsTheSameEveryTime() {
        assertArrayEquals(VeilNoise.atlas(), VeilNoise.atlas());
    }
}
