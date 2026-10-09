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

    /** Whether the server holds the player itself (beyond what the client's own clamp can leave). */
    public static boolean serverMustHold(double feetY) { return feetY > CEILING_Y + SERVER_SLACK; }

    public static boolean buildRefused(int y) { return y >= NO_BUILD_Y; }
}
