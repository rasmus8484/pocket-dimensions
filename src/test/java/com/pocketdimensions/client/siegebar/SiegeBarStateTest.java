package com.pocketdimensions.client.siegebar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SiegeBarStateTest {

    private static SiegeBarState state(int progress, int duration, int rate, int siegeFuel, int coreFuel) {
        return burning(progress, duration, rate, siegeFuel, coreFuel, 0, 0);
    }

    private static SiegeBarState burning(int progress, int duration, int rate, int siegeFuel, int coreFuel,
                                         int siegeBurnt, int coreBurnt) {
        return new SiegeBarState(SiegeBarArt.Kind.BREACHER, progress, duration, rate, siegeFuel, 5, coreFuel,
                siegeBurnt, coreBurnt, 200);
    }

    @Test
    void theBurningLapisEmptiesSmoothlyWhileTheSiegeRuns() {
        SiegeBarState s = burning(0, 24000, 1, 3, 0, 50, 0);
        assertEquals(2.75, s.siegeFuelAt(0), 1e-9, "a quarter of the burning lapis is gone");
        assertEquals(2.5, s.siegeFuelAt(50), 1e-9, "and it keeps going between packets");
        assertEquals(2.0, s.siegeFuelAt(1000), 1e-9, "never past the lapis being burnt");
        SiegeBarState warded = burning(0, 24000, 3, 3, 0, 50, 0);
        assertEquals(2.5, warded.siegeFuelAt(50), 1e-9, "lapis burns by the tick, however slow the siege");
    }

    @Test
    void aStoppedSiegeHoldsItsHalfBurntLapis() {
        SiegeBarState stopped = burning(0, 24000, 0, 3, 0, 100, 0);
        assertEquals(2.5, stopped.siegeFuelAt(400), 1e-9);
        assertEquals(0, burning(0, 24000, 0, 0, 0, 100, 0).siegeFuelAt(0), 1e-9, "no lapis, nothing to show");
    }

    @Test
    void theCoreBurnsOnlyWhileItWardsARunningSiege() {
        SiegeBarState warded = burning(0, 24000, 3, 3, 10, 0, 100);
        assertEquals(9.5, warded.coreFuelAt(0), 1e-9);
        assertEquals(9.25, warded.coreFuelAt(50), 1e-9);
        SiegeBarState dormant = burning(0, 24000, 0, 0, 10, 0, 100);
        assertEquals(9.5, dormant.coreFuelAt(50), 1e-9, "a dormant siege burns nothing");
        assertEquals(0, burning(0, 24000, 1, 3, 0, 0, 100).coreFuelAt(0), 1e-9);
    }

    @Test
    void progressMovesOnBetweenPacketsAtTheReportedRate() {
        SiegeBarState s = state(1000, 24000, 1, 3, 0);
        assertEquals(1000 / 24000.0, s.progressAt(0), 1e-9);
        assertEquals(1020 / 24000.0, s.progressAt(20), 1e-9);
        SiegeBarState warded = state(1000, 24000, 3, 3, 10);
        assertEquals(1010 / 24000.0, warded.progressAt(30), 1e-9, "a warded siege moves a third as fast");
        SiegeBarState dormant = state(1000, 24000, 0, 0, 0);
        assertEquals(1000 / 24000.0, dormant.progressAt(400), 1e-9, "a dormant siege stands still");
    }

    @Test
    void progressNeverRunsPastTheEnd() {
        assertEquals(1.0, state(23990, 24000, 1, 3, 0).progressAt(100), 1e-9);
    }

    @Test
    void theModeFollowsTheTwoFuels() {
        assertEquals(SiegeBarArt.Mode.ACTIVE, state(0, 24000, 1, 3, 0).mode());
        assertEquals(SiegeBarArt.Mode.WARDED, state(0, 24000, 3, 3, 40).mode());
        assertEquals(SiegeBarArt.Mode.DORMANT, state(0, 24000, 0, 0, 40).mode(), "no lapis in the siege block wins over the ward");
    }

    @Test
    void timeLeftCountsTheSlowedTimeWhileWarded() {
        SiegeBarState s = state(14880, 24000, 1, 3, 0);                 // 9120 ticks left = 7m 36s
        assertEquals("7m 36s", s.timeText(0));
        SiegeBarState w = state(14880, 24000, 3, 3, 40);
        assertEquals("22m 48s", w.timeText(0));
        assertEquals("Dormant", state(14880, 24000, 0, 0, 0).timeText(0));
        assertEquals("1h 0m 0s", state(0, 72000, 1, 3, 0).timeText(0));
        assertEquals("0s", state(24000, 24000, 1, 3, 0).timeText(0));
    }

    @Test
    void percentRoundsDown() {
        assertEquals("62%", state(14880, 24000, 1, 3, 0).percentText(0));
        assertEquals("99%", state(23999, 24000, 0, 0, 0).percentText(0));
    }
}
