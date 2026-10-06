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
}
