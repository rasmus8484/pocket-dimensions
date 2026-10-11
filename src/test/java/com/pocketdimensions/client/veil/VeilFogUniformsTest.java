package com.pocketdimensions.client.veil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** What the fog shader is given each frame: small, precise numbers wherever the realm is. */
class VeilFogUniformsTest {

    @Test
    void theCameraIsRelativeToThePlotCorner() {
        assertArrayEquals(new float[] {12.5f, 70.25f, 800.75f}, VeilFogUniforms.camera(112.5, 70.25, -799.25, 100, -1600), 1e-6f);
    }

    @Test
    void aPlotFarFromTheOriginKeepsFullPrecision() {
        float[] c = VeilFogUniforms.camera(3_000_012.37, 64, -3_000_005.81, 3_000_000, -3_000_016);
        assertEquals(12.37f, c[0], 1e-4f);
        assertEquals(10.19f, c[2], 1e-4f);
    }

    @Test
    void timeIsInSecondsAndWrapsEveryHour() {
        assertEquals(0.5f + 0.05f / 20, VeilFogUniforms.time(10, 0.05f), 1e-6f);
        assertEquals(VeilFogUniforms.time(1234, 0.5f), VeilFogUniforms.time(1234 + 72_000L * 50, 0.5f), 1e-6f);
        assertTrue(VeilFogUniforms.time(71_999, 0.99f) < 3600f);
    }

    @Test
    void nightFollowsTheSkyDarkening() {
        assertEquals(0f, VeilFogUniforms.night(0));
        assertEquals(1f, VeilFogUniforms.night(11));
        assertEquals(1f, VeilFogUniforms.night(15));
        assertEquals(5f / 11, VeilFogUniforms.night(5), 1e-6f);
    }
}
