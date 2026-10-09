package com.pocketdimensions.client.ward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** How the ward looks for each siege state, with and without lapis, by day and by night. */
class WardLookTest {

    @Test
    void coloursFollowTheSiege() {
        assertEquals(0x8CEBFF, WardLook.colour(0));
        assertEquals(0xFF5ADC, WardLook.colour(1));
        assertEquals(0xFF5028, WardLook.colour(2));
        int lost = WardLook.colour(3);
        assertTrue((lost >> 16 & 0xFF) > (lost & 0xFF), "lost is red");
        assertTrue((lost >> 16 & 0xFF) < 0xFF, "and dim");
    }

    @Test
    void aWardedRealmShinesBrighterThanAStarvingOne() {
        assertTrue(WardLook.brightness(true, 0.5f) > WardLook.brightness(false, 0.5f));
        assertEquals(1f, WardLook.brightness(true, 0.5f), 1e-6, "full at night with lapis");
    }

    @Test
    void byDayItIsFaintButNeverGone() {
        float day = WardLook.brightness(true, 0f);
        assertTrue(day >= 0.25f && day < 0.5f, "day " + day);
    }

    @Test
    void atPeaceEverythingShowsAndTurns() {
        for (int i = 0; i < 200; i++) assertTrue(WardLook.shown(i, 0, false, 1234));
        assertTrue(WardLook.turns(0));
    }

    @Test
    void anOpenBreachCracksTheWard() {
        int hidden = 0;
        for (int i = 0; i < 400; i++) if (!WardLook.shown(i, 1, true, 0)) hidden++;
        assertTrue(hidden > 40 && hidden < 200, "hidden " + hidden);
    }

    @Test
    void aLostAnchorBreaksItAndStopsItTurning() {
        int hidden = 0;
        for (int i = 0; i < 400; i++) if (!WardLook.shown(i, 3, false, 0)) hidden++;
        assertTrue(hidden > 100, "hidden " + hidden);
        assertFalse(WardLook.turns(3));
    }

    @Test
    void pulsesRunOutward() {
        // a pulse at radius r and time t is at radius r + speed * dt later
        float now = WardLook.pulse(100, 0);
        float later = WardLook.pulse(100 + WardLook.PULSE_SPEED * 10, 10);
        assertEquals(now, later, 1e-5);
        for (int r = 0; r < 500; r += 7) { float p = WardLook.pulse(r, 3.5); assertTrue(p >= 0 && p <= 1); }
    }
}
