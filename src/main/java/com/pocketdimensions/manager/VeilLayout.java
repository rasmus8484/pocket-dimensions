package com.pocketdimensions.manager;

/**
 * Where the Veil stands: the ring of block columns just outside each realm's plot, and which colour band a block of it
 * is in. Plain logic, unit tested; RealmChunkGenerator places the blocks.
 */
public final class VeilLayout {

    /** Colour bands from the bottom of the world to the top. */
    public static final int BANDS = 8;

    private VeilLayout() {}

    /**
     * Whether block column (x, z) is part of a Veil. Plots start every cellBlocks on both axes and are sideBlocks
     * across; the ring runs just outside one (x = minX - 1 and x = maxX, z from minZ - 1 to maxZ, and the same with x and
     * z swapped). Needs a gap of at least two blocks (cellBlocks - sideBlocks >= 2), which a one-chunk padding gives.
     */
    public static boolean isVeilColumn(int x, int z, int sideBlocks, int cellBlocks) {
        int lx = Math.floorMod(x, cellBlocks), lz = Math.floorMod(z, cellBlocks);
        return (wall(lx, sideBlocks, cellBlocks) && along(lz, sideBlocks, cellBlocks))
                || (wall(lz, sideBlocks, cellBlocks) && along(lx, sideBlocks, cellBlocks));
    }

    /** On a wall line: just past a plot's max, or just before the next plot's min. */
    private static boolean wall(int local, int side, int cell) { return local == side || local == cell - 1; }

    /** Within a wall's length: the plot's span plus one column at each end (the corners). */
    private static boolean along(int local, int side, int cell) { return local <= side || local == cell - 1; }

    /** The band (0 at the bottom, BANDS - 1 at the top) of a Veil block at y in a world from minY, height blocks tall. */
    public static int band(int y, int minY, int height) {
        int b = (int) Math.floorDiv((long) (y - minY) * BANDS, height);
        return Math.max(0, Math.min(BANDS - 1, b));
    }
}
