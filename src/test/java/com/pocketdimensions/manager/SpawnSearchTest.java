package com.pocketdimensions.manager;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** A 16 x 16 x 16 room (0..15 on each axis) whose floor is the shell below y 0, like a pocket room's interior. */
class SpawnSearchTest {

    private final Set<String> blocks = new HashSet<>();

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) blocks.add(x + "," + y + "," + z);
    }

    private boolean solid(int x, int y, int z) {
        return y < 0 || blocks.contains(x + "," + y + "," + z);
    }

    private int[] find() {
        return SpawnSearch.find(8, 0, 8, 0, 0, 0, 15, 15, 15, (x, y, z) -> !solid(x, y, z), this::solid);
    }

    @Test
    void anEmptyRoomUsesTheUsualSpawn() {
        assertArrayEquals(new int[]{8, 0, 8}, find());
    }

    @Test
    void blocksOnTheSpawnMoveYouToTheNearestFreeSpot() {
        fill(8, 0, 8, 8, 4, 8);                       // a pillar where you would land
        int[] p = find();
        assertNotNull(p);
        assertEquals(0, p[1], "stays on the floor next to the pillar rather than climbing it");
        assertEquals(1, Math.abs(p[0] - 8) + Math.abs(p[2] - 8));
    }

    @Test
    void aBlockAtHeadHeightAlsoCountsAsBlocked() {
        fill(8, 1, 8, 8, 1, 8);
        int[] p = find();
        assertNotNull(p);
        assertFalse(p[0] == 8 && p[1] == 0 && p[2] == 8);
    }

    @Test
    void aPocketLeftInAPackedRoomIsFound() {
        fill(0, 0, 0, 15, 15, 15);
        blocks.remove("3,7,3"); blocks.remove("3,8,3");
        assertArrayEquals(new int[]{3, 7, 3}, find());
    }

    @Test
    void youNeedSomethingToStandOn() {
        // free air everywhere, but only one spot has anything solid under it
        int[] p = SpawnSearch.find(8, 0, 8, 0, 0, 0, 15, 15, 15, (x, y, z) -> true, (x, y, z) -> x == 2 && y == -1 && z == 2);
        assertArrayEquals(new int[]{2, 0, 2}, p);
    }

    @Test
    void aRoomPackedFullHasNoFreeSpot() {
        fill(0, 0, 0, 15, 15, 15);
        assertNull(find());
    }

    @Test
    void theCeilingCountsAsBlockingYourHead() {
        fill(0, 0, 0, 15, 14, 15);                    // only the top layer is free: no room for a head
        assertNull(find());
    }
}
