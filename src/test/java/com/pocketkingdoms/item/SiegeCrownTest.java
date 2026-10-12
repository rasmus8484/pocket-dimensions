package com.pocketkingdoms.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SiegeCrownTest {

    @Test
    void clickingTheUpperHalfCrownsTheBlockAboveIt() {
        assertEquals(71, SiegeCrown.crownY(70, true));
    }

    @Test
    void clickingTheLowerHalfStillCrownsTheTopNotTheMiddle() {
        assertEquals(71, SiegeCrown.crownY(69, false));
    }
}
