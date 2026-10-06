package com.pocketdimensions.manager;

/**
 * What each block of a pocket room's shell is, by its local position (0..19 on each axis; the 16-block interior is
 * 2..17). From inside, the room is the Tumbling Cube seen from within: each face is a window into the void, ringed by
 * netherite edges with gold corners. Only the shell's inner layer (local 1 or 18) is ever seen. Pure logic, tested.
 */
public final class RoomShell {

    public enum Part { VOID, EDGE, CORNER }

    public static final int SIZE = 20, IN_MIN = 2, IN_MAX = 17;

    /** One corner block of the floor carries the block entity whose renderer draws the void for the whole room. */
    public static final int HEART_X = 2, HEART_Y = 1, HEART_Z = 2;

    /**
     * The six void panels in local block coordinates {x0, y0, z0, x1, y1, z1}, each flat on one axis: the face of the
     * inner shell layer, inside its ring of edge blocks. Order: floor, ceiling, west, east, north, south.
     */
    public static final float[][] PANELS = {
            {3, 2, 3, 17, 2, 17}, {3, 18, 3, 17, 18, 17},
            {2, 3, 3, 2, 17, 17}, {18, 3, 3, 18, 17, 17},
            {3, 3, 2, 17, 17, 2}, {3, 3, 18, 17, 17, 18},
    };

    private RoomShell() {}

    /** The part at a local position, or null inside the room. Blocks no one can see are VOID. */
    public static Part partAt(int x, int y, int z) {
        boolean ix = inside(x), iy = inside(y), iz = inside(z);
        if (ix && iy && iz) return null;
        int faces = (layer(x) ? 1 : 0) + (layer(y) ? 1 : 0) + (layer(z) ? 1 : 0);
        int insideCount = (ix ? 1 : 0) + (iy ? 1 : 0) + (iz ? 1 : 0);
        if (faces != 1 || insideCount != 2) return Part.VOID;      // the outer layer and the hidden seams
        int rim = 0;
        if (ix && rim(x)) rim++;
        if (iy && rim(y)) rim++;
        if (iz && rim(z)) rim++;
        return rim == 0 ? Part.VOID : rim == 1 ? Part.EDGE : Part.CORNER;
    }

    public static boolean isHeart(int x, int y, int z) { return x == HEART_X && y == HEART_Y && z == HEART_Z; }

    private static boolean inside(int v) { return v >= IN_MIN && v <= IN_MAX; }
    private static boolean layer(int v) { return v == IN_MIN - 1 || v == IN_MAX + 1; }
    private static boolean rim(int v) { return v == IN_MIN || v == IN_MAX; }
}
