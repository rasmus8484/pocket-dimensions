package com.pocketdimensions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SiegeTuningTest {

    @Test
    void miningTakesTheConfiguredNumberOfSeconds() {
        float perTick = SiegeTuning.mineProgressPerTick(10.0);
        assertEquals(200, Math.round(1f / perTick), "10 s is 200 ticks of progress");
    }

    @Test
    void aLapisBurnsForExactlyTheBurnTimeOfRunningTicks() {
        int burnt = 0, ticks = 0;
        do { burnt = SiegeTuning.burnTick(burnt, 200); ticks++; } while (burnt != 0);
        assertEquals(200, ticks);
        assertEquals(0, SiegeTuning.burnTick(0, 1), "a burn time of one tick uses a lapis every tick");
    }

    @Test
    void aTimerPastALoweredBurnTimeBurnsOutAtOnce() {
        assertEquals(0, SiegeTuning.burnTick(500, 200));
    }

    @Test
    void zeroSecondsBreaksInstantly() {
        assertEquals(1f, SiegeTuning.mineProgressPerTick(0.0));
    }
}
