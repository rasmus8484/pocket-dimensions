package com.pocketdimensions.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A pocket room is folded space, not a world of its own: it belongs to wherever its anchor is. */
class RoomHostTest {

    @Test
    void theRoomBelongsWhereItsAnchorIsPlaced() {
        assertEquals("realm", RoomHost.pick("realm", "overworld", "overworld"), "entered in the overworld, anchor since moved");
    }

    @Test
    void whileTheAnchorIsCarriedItBelongsWhereYouCameIn() {
        assertEquals("the_nether", RoomHost.pick(null, "the_nether", "overworld"));
    }

    @Test
    void withNeitherItFallsBackToTheOverworld() {
        assertEquals("overworld", RoomHost.pick(null, null, "overworld"));
    }
}
