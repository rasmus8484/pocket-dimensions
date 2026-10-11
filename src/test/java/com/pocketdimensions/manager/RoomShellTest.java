package com.pocketdimensions.manager;

import com.pocketdimensions.manager.RoomShell.Part;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The shell is 20 blocks a side, two thick, around a 16-block interior (local 2..17 on every axis). */
class RoomShellTest {

    @Test
    void theInteriorIsNotShell() {
        assertNull(RoomShell.partAt(2, 2, 2));
        assertNull(RoomShell.partAt(17, 9, 10));
    }

    @Test
    void theMiddleOfEveryVisibleFaceIsVoid() {
        assertEquals(Part.VOID, RoomShell.partAt(9, 1, 9), "floor");
        assertEquals(Part.VOID, RoomShell.partAt(9, 18, 9), "ceiling");
        assertEquals(Part.VOID, RoomShell.partAt(1, 9, 9), "west wall");
        assertEquals(Part.VOID, RoomShell.partAt(9, 9, 18), "south wall");
    }

    @Test
    void theRingAroundEachFaceIsEdgeAndItsCornersAreGold() {
        assertEquals(Part.EDGE, RoomShell.partAt(2, 1, 9), "floor block along the west wall");
        assertEquals(Part.EDGE, RoomShell.partAt(9, 17, 1), "north wall block under the ceiling");
        assertEquals(Part.CORNER, RoomShell.partAt(2, 1, 2), "floor block in a corner");
        assertEquals(Part.CORNER, RoomShell.partAt(17, 17, 18), "south wall block in the top corner");
    }

    @Test
    void blocksNobodyCanSeeAreLeftAsVoid() {
        assertEquals(Part.VOID, RoomShell.partAt(0, 0, 0), "outer layer");
        assertEquals(Part.VOID, RoomShell.partAt(1, 1, 9), "hidden seam behind floor and wall");
    }

    @Test
    void exactlyOneHeartCarriesTheRoomsRenderer() {
        int hearts = 0;
        for (int x = 0; x < 20; x++) for (int y = 0; y < 20; y++) for (int z = 0; z < 20; z++) if (RoomShell.isHeart(x, y, z)) hearts++;
        assertEquals(1, hearts);
        assertTrue(RoomShell.isHeart(RoomShell.HEART_X, RoomShell.HEART_Y, RoomShell.HEART_Z));
        assertEquals(Part.CORNER, RoomShell.partAt(RoomShell.HEART_X, RoomShell.HEART_Y, RoomShell.HEART_Z), "it looks like any other corner");
    }

    @Test
    void theVoidPanelsCoverEachFaceInsideItsRing() {
        // floor: the top of the y = 1 layer, over blocks 3..16 on x and z
        float[] floor = RoomShell.PANELS[0];
        assertArrayEquals(new float[]{3, 2, 3, 17, 2, 17}, floor, 0.01f);
        assertEquals(6, RoomShell.PANELS.length);
    }

    @Test
    void theShellIsTheTwoLayersRoundTheInsideAndNothingElse() {
        assertTrue(RoomShell.inShell(0, 0, 0), "outer corner");
        assertTrue(RoomShell.inShell(1, 9, 9), "inner wall layer");
        assertTrue(RoomShell.inShell(19, 19, 19), "far outer corner");
        assertFalse(RoomShell.inShell(2, 2, 2), "the inside's first block is air, not wall");
        assertFalse(RoomShell.inShell(17, 9, 17), "inside");
        assertFalse(RoomShell.inShell(-1, 9, 9), "outside the room");
        assertFalse(RoomShell.inShell(20, 9, 9), "outside the room");
        int count = 0;
        for (int x = -1; x <= 20; x++) for (int y = -1; y <= 20; y++) for (int z = -1; z <= 20; z++) if (RoomShell.inShell(x, y, z)) count++;
        assertEquals(20 * 20 * 20 - 16 * 16 * 16, count);
    }

    @Test
    void theRoomHoldsEverythingWithinItsWallsAndNothingBeyond() {
        assertTrue(RoomShell.holds(9, 2, 9), "standing on the floor");
        assertTrue(RoomShell.holds(0, 0, 0), "inside the wall itself still counts");
        assertTrue(RoomShell.holds(19, 19, 19));
        assertFalse(RoomShell.holds(-1, 5, 5), "through the west wall");
        assertFalse(RoomShell.holds(20, 5, 5), "through the east wall");
        assertFalse(RoomShell.holds(5, -1, 5), "fallen through the floor");
        assertFalse(RoomShell.holds(5, 20, 5), "above the ceiling");
        assertFalse(RoomShell.holds(5, 5, 20));
    }
}
