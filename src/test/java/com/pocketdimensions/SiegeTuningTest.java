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
    void zeroSecondsBreaksInstantly() {
        assertEquals(1f, SiegeTuning.mineProgressPerTick(0.0));
    }

    @Test
    void theFuelCounterReadsNaturally() {
        assertEquals("1 lapis", SiegeTuning.fuelLabel(1));
        assertEquals("37 lapis", SiegeTuning.fuelLabel(37));
        assertEquals("no lapis", SiegeTuning.fuelLabel(0));
    }
}
