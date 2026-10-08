package com.pocketdimensions.manager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The cracks that tear in from a pocket room's walls while its anchor is being mined. */
class RoomCracksTest {

    private static final float C = RoomCracks.CENTER;

    @Test
    void noSetsNoCracks() {
        assertTrue(RoomCracks.bolts(0, 7).isEmpty());
    }

    @Test
    void eachSetSendsOneCrackInFromEveryWall() {
        List<float[]> b = RoomCracks.bolts(2, 7);
        assertEquals(12, b.size());
        for (int i = 0; i < b.size(); i++) {
            float[] p = RoomShell.PANELS[i % 6], s = b.get(i);
            for (int c = 0; c < 3; c++) {
                assertTrue(s[c] >= p[c] && s[c] <= p[c + 3], "crack " + i + " starts on its wall's void panel");
            }
        }
    }

    @Test
    void cracksReachTowardTheCentreAndLaterSetsReachFurther() {
        List<float[]> b = RoomCracks.bolts(3, 7);
        float[] reached = new float[3];
        for (int i = 0; i < b.size(); i++) {
            float[] s = b.get(i);
            float from = dist(s[0], s[1], s[2]), to = dist(s[3], s[4], s[5]);
            assertTrue(to < from, "crack " + i + " heads inward");
            reached[i / 6] += (from - to) / from;
        }
        assertTrue(reached[0] < reached[1] && reached[1] < reached[2]);
        for (float[] s : b) assertTrue(dist(s[3], s[4], s[5]) > 1f, "no crack quite reaches the middle");
    }

    @Test
    void theSameRoomAlwaysCracksTheSameWay() {
        List<float[]> a = RoomCracks.bolts(3, 42), b = RoomCracks.bolts(3, 42), c = RoomCracks.bolts(3, 43);
        for (int i = 0; i < a.size(); i++) assertArrayEquals(a.get(i), b.get(i));
        assertFalse(java.util.Arrays.equals(a.get(0), c.get(0)), "another room, other cracks");
    }

    private static float dist(float x, float y, float z) {
        return (float) Math.sqrt((x - C) * (x - C) + (y - C) * (y - C) + (z - C) * (z - C));
    }
}
