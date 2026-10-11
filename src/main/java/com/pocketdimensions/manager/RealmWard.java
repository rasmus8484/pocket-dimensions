package com.pocketdimensions.manager;

/**
 * The realm's ward overhead: players are held with their feet at or below CEILING_Y, so their eyes stay under the top
 * of the Veil and nobody can look over it, and no block is placed at NO_BUILD_Y or above, so nobody can stand above the
 * ceiling. Plain logic, unit tested; RealmEventHandler and the client's WardClient apply it.
 */
public final class RealmWard {

    /** Highest a player's feet may be in a realm. */
    public static final int CEILING_Y = 318;
    /** Lowest row where blocks may not be placed in a realm (318 and 319, the top of the world). */
    public static final int NO_BUILD_Y = 318;
    /** How far past the ceiling an honest client may get within one tick before it clamps itself; the server allows it. */
    public static final double SERVER_SLACK = 1.0;

    private RealmWard() {}

    public static boolean aboveCeiling(double feetY) { return feetY > CEILING_Y; }

    /** How far below the ceiling the server looks for a free spot when the one under the ceiling is blocked. */
    private static final int SEARCH_DEPTH = 24;
    /** How far inside the plot's edge a held player is put, as at the border. */
    private static final double EDGE_MARGIN = 2.5;

    /** Whether the server holds the player itself (beyond what the client's own clamp can leave). */
    public static boolean serverMustHold(double feetY) { return serverMustHold(feetY, false); }

    /**
     * Whether the server holds the player itself: past the slack, or above the ceiling at all when what moves them (the
     * player, or their mount) can't simply be lowered, because a block is in the way. The client never pushes anything
     * into a block, so such a player is left above the ceiling for the server to place.
     */
    public static boolean serverMustHold(double feetY, boolean cannotLower) {
        return feetY > CEILING_Y + SERVER_SLACK || (cannotLower && aboveCeiling(feetY));
    }

    /** A coordinate kept inside a plot's span [min, maxExclusive), as far in from the edge as the border puts people. */
    public static double inside(double v, int min, int maxExclusive) {
        return Math.max(min + EDGE_MARGIN, Math.min(maxExclusive - EDGE_MARGIN, v));
    }

    /** Lowest y the server's free-spot search under the ceiling goes down to. */
    public static int searchFloorY() { return CEILING_Y - SEARCH_DEPTH; }

    public static boolean buildRefused(int y) { return y >= NO_BUILD_Y; }
}
