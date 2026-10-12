package com.pocketkingdoms.client.ward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** /pk test wardTime: frames measured for a while, then one report. */
class WardTimerTest {

    private static final long MS = 1_000_000, S = 1_000_000_000;

    @Test
    void idleUntilStarted() {
        WardTimer t = new WardTimer();
        assertFalse(t.measuring());
        t.frame(5 * MS, 1000);
        assertNull(t.poll(100 * S));
    }

    @Test
    void reportsOnceTheTimeIsUp() {
        WardTimer t = new WardTimer();
        t.start(2, 0);
        for (int i = 1; i <= 100; i++) t.frame(i * MS / 10, 40_000);          // 0.1 .. 10 ms
        assertNull(t.poll(S), "still measuring after 1 s");
        WardTimer.Report r = t.poll(2 * S);
        assertNotNull(r);
        assertEquals(100, r.frames());
        assertEquals(5.05, r.averageMs(), 1e-9);
        assertEquals(9.5, r.p95Ms(), 1e-9);
        assertEquals(10, r.worstMs(), 1e-9);
        assertEquals(40_000, r.vertices());
        assertFalse(t.measuring());
        assertNull(t.poll(3 * S), "one report only");
    }

    @Test
    void framesOutsideTheWindowAreIgnored() {
        WardTimer t = new WardTimer();
        t.frame(9 * MS, 1);
        t.start(1, 0);
        t.frame(1 * MS, 10);
        assertEquals(1, t.poll(S).frames());
    }

    @Test
    void noFramesMeansTheWardWasNotDrawn() {
        WardTimer t = new WardTimer();
        t.start(1, 0);
        WardTimer.Report r = t.poll(S);
        assertEquals(0, r.frames());
        assertTrue(r.describe().contains("not drawn"), r.describe());
    }

    @Test
    void describesTheShareOfASixtyFpsFrame() {
        String d = new WardTimer.Report(10, 600, 0.5, 0.8, 1.2, 48_200).describe();
        assertTrue(d.contains("0.50 ms"), d);
        assertTrue(d.contains("48,200"), d);
        assertTrue(d.contains("3.0%"), d);                                     // 0.5 of 16.67 ms
    }
}
