package com.pocketdimensions.manager;

/**
 * The End's landing platform. Every arrival through an End portal lays it again (vanilla EndPlatformFeature): obsidian
 * one block below the spawn point, and the three blocks above it swept clear, whatever stands there destroyed. A Pocket
 * Anchor set down there would vanish with the next arrival, so it is never placed there. Plain logic, unit tested.
 */
public final class EndLanding {

    // ServerLevel.END_SPAWN_POINT, written out so this stays plain (loading ServerLevel needs the game bootstrapped)
    private static final int SPAWN_X = 100, SPAWN_Y = 50, SPAWN_Z = 0;

    private EndLanding() {}

    /** Whether a block at x, y, z in the End is swept by the next arrival: 5 x 5, from the obsidian up three blocks. */
    public static boolean sweeps(int x, int y, int z) {
        return Math.abs(x - SPAWN_X) <= 2 && Math.abs(z - SPAWN_Z) <= 2
                && y >= SPAWN_Y - 2 && y <= SPAWN_Y + 1;
    }
}
