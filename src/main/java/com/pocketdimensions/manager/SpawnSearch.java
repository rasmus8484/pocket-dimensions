package com.pocketdimensions.manager;

/**
 * Finds somewhere to stand in a box of cells, nearest to a preferred spot. Plain integers, so it does not depend on
 * the world: callers pass what "free" and "something to stand on" mean.
 */
public final class SpawnSearch {

    @FunctionalInterface
    public interface Cell {
        boolean test(int x, int y, int z);
    }

    private SpawnSearch() {}

    /**
     * The standable spot (feet and head free, something to stand on below the feet) inside x0..x1, y0..y1, z0..z1 that
     * is nearest to (px, py, pz). On a tie the lower spot wins, so you land on the floor rather than on a ledge.
     *
     * @return {x, y, z} of the feet, or null if there is nowhere to stand
     */
    public static int[] find(int px, int py, int pz, int x0, int y0, int z0, int x1, int y1, int z1, Cell free, Cell floor) {
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        for (int y = y0; y < y1; y++) {                      // the head needs y + 1 inside the box too
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    long d = (long) (x - px) * (x - px) + (long) (y - py) * (y - py) + (long) (z - pz) * (z - pz);
                    if (d >= bestD) continue;                // also keeps the lower spot on a tie (y ascends)
                    if (free.test(x, y, z) && free.test(x, y + 1, z) && floor.test(x, y - 1, z)) {
                        best = new int[]{x, y, z};
                        bestD = d;
                    }
                }
            }
        }
        return best;
    }
}
