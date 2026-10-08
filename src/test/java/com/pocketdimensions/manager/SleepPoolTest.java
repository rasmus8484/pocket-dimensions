package com.pocketdimensions.manager;

import com.pocketdimensions.manager.SleepPool.Sleeper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The overworld and every pocket room share one sleep pool, counted the way vanilla counts one dimension. */
class SleepPoolTest {

    private static final Sleeper AWAKE = new Sleeper(false, false, false);
    private static final Sleeper DOZING = new Sleeper(false, true, false);     // in bed, not yet 5 seconds
    private static final Sleeper ASLEEP = new Sleeper(false, true, true);
    private static final Sleeper SPECTATOR = new Sleeper(true, false, false);

    @Test
    void everyoneWhoIsNotSpectatingCountsAndOnlyThoseInBedSleep() {
        SleepPool p = SleepPool.of(List.of(AWAKE, DOZING, ASLEEP, SPECTATOR, AWAKE));
        assertEquals(4, p.active());
        assertEquals(2, p.sleeping());
    }

    @Test
    void theNumberNeededIsThePercentageRoundedUpButNeverBelowOne() {
        SleepPool five = SleepPool.of(List.of(AWAKE, AWAKE, AWAKE, AWAKE, ASLEEP));
        assertEquals(5, five.needed(100));
        assertEquals(3, five.needed(50), "2.5 rounds up");
        assertEquals(1, five.needed(0), "always at least one");
        assertEquals(1, SleepPool.of(List.of()).needed(100), "even with nobody there");
    }

    @Test
    void theNightIsSkippedOnlyOnceEnoughHaveSleptLongEnough() {
        assertFalse(SleepPool.of(List.of(DOZING, DOZING)).skipNight(100), "in bed, but not for 5 seconds yet");
        assertTrue(SleepPool.of(List.of(ASLEEP, ASLEEP)).skipNight(100));
        assertFalse(SleepPool.of(List.of(ASLEEP, AWAKE)).skipNight(100), "the one in the pocket room is still up");
        assertTrue(SleepPool.of(List.of(ASLEEP, AWAKE)).skipNight(50));
        assertTrue(SleepPool.of(List.of(ASLEEP, SPECTATOR)).skipNight(100), "spectators never hold the night up");
    }

    @Test
    void nobodyInBedNeverSkips() {
        assertFalse(SleepPool.of(List.of(AWAKE)).skipNight(0));
        assertFalse(SleepPool.of(List.of()).skipNight(0));
    }

    @Test
    void theMessageReadsLikeVanilla() {
        SleepPool p = SleepPool.of(List.of(DOZING, AWAKE, AWAKE, AWAKE, AWAKE));
        assertFalse(p.enoughInBed(100));
        assertEquals(1, p.sleeping());
        assertEquals(5, p.needed(100), "shown as 1/5 players sleeping");
        assertTrue(SleepPool.of(List.of(DOZING, DOZING)).enoughInBed(100), "then: sleeping through this night");
    }
}
