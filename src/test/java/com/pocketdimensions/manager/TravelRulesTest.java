package com.pocketdimensions.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Who and what comes along when a player travels into or out of a pocket room or a realm. */
class TravelRulesTest {

    @Test
    void yourMountComesWhenYouHoldTheReinsAndThereIsRoomForIt() {
        assertTrue(TravelRules.takesMount(false, true));
        assertFalse(TravelRules.takesMount(true, true), "someone else is steering: you get off, it stays theirs");
        assertFalse(TravelRules.takesMount(false, false), "no room for it where you arrive: you go alone");
    }

    @Test
    void everythingAboardComesExceptOtherPlayers() {
        assertTrue(TravelRules.passengerComes(false), "a creature in your boat, monster or not");
        assertFalse(TravelRules.passengerComes(true), "another player is set down before the trip");
    }

    @Test
    void animalsOnYourLeadFollowButMonstersStay() {
        assertTrue(TravelRules.leashedFollows(false, false));
        assertFalse(TravelRules.leashedFollows(true, false), "a monster on a lead stays behind");
        assertFalse(TravelRules.leashedFollows(false, true), "already aboard: it travels with the mount");
    }

    @Test
    void aRiderLeavesARoomByTheWallsAndSomeoneOnFootByCrouchAndJump() {
        assertTrue(TravelRules.wallLetsYouOut(true), "crouching would set you down, so a rider uses the walls");
        assertFalse(TravelRules.wallLetsYouOut(false), "on foot a wall is just a wall: no exits by a stray click");
    }

    @Test
    void onlyAJourneyYouChoseBringsCompany() {
        assertTrue(TravelRules.bringsCompany(TravelRules.Journey.CHOSEN));
        assertFalse(TravelRules.bringsCompany(TravelRules.Journey.FORCED), "thrown out: you go alone");
    }
}
