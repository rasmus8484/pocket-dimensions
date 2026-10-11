package com.pocketdimensions.client.veil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** /pd test veilTime: GPU time of the fog pass for a while, then one report. */
class VeilFogTimerTest {

    private static final long MS = 1_000_000, S = 1_000_000_000;

    @Test
    void idleUntilStarted() {
        VeilFogTimer t = new VeilFogTimer();
        assertFalse(t.measuring());
        t.frame(5 * MS);
        assertNull(t.poll(100 * S));
    }

    @Test
    void reportsOnceTheTimeIsUp() {
        VeilFogTimer t = new VeilFogTimer();
        t.start(2, 0);
        for (int i = 1; i <= 100; i++) t.frame(i * MS / 10);                    // 0.1 .. 10 ms
        assertNull(t.poll(S), "still measuring after 1 s");
        VeilFogTimer.Report r = t.poll(2 * S);
        assertNotNull(r);
        assertEquals(100, r.frames());
        assertEquals(5.05, r.averageMs(), 1e-9);
        assertEquals(9.5, r.p95Ms(), 1e-9);
        assertEquals(10.0, r.worstMs(), 1e-9);
        assertNull(t.poll(3 * S), "reports once");
        assertFalse(t.measuring());
    }

    @Test
    void nothingDrawnSaysSo() {
        VeilFogTimer t = new VeilFogTimer();
        t.start(1, 0);
        VeilFogTimer.Report r = t.poll(2 * S);
        assertNotNull(r);
        assertEquals(0, r.frames());
        assertTrue(r.describe().contains("not drawn"), r.describe());
    }

    @Test
    void framesSkippedWhileAnotherGpuTimerRunsAreExplained() {
        VeilFogTimer t = new VeilFogTimer();
        t.start(1, 0);
        for (int i = 0; i < 5; i++) t.skipped();
        VeilFogTimer.Report r = t.poll(2 * S);
        assertEquals(0, r.frames());
        assertEquals(5, r.skipped());
        assertTrue(r.describe().contains("F3"), r.describe());
    }

    @Test
    void skippingIsIgnoredOutsideAWindow() {
        VeilFogTimer t = new VeilFogTimer();
        t.skipped();
        t.start(1, 0);
        assertEquals(0, t.poll(2 * S).skipped());
    }

    @Test
    void theReportGivesTheShareOfASixtyFpsFrame() {
        VeilFogTimer t = new VeilFogTimer();
        t.start(1, 0);
        for (int i = 0; i < 10; i++) t.frame(MS);
        String text = t.poll(S).describe();
        assertTrue(text.contains("1.00 ms"), text);
        assertTrue(text.contains("6.0% of a 60 fps frame"), text);
    }
}
