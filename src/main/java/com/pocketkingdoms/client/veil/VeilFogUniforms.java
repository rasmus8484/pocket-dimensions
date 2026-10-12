package com.pocketkingdoms.client.veil;

/**
 * The per-frame numbers the Veil fog shader gets, worked out in doubles and handed over as small floats: positions
 * relative to the plot's min corner (exact even for a realm millions of blocks out) and time wrapped every hour. Plain
 * logic, unit tested; VeilFogRenderer writes them into the uniform block.
 */
public final class VeilFogUniforms {

    private static final long HOUR_TICKS = 72_000;

    private VeilFogUniforms() {}

    /** The camera's x and z relative to the plot's min corner, its y as is. */
    public static float[] camera(double camX, double camY, double camZ, int minX, int minZ) {
        return new float[] {(float) (camX - minX), (float) camY, (float) (camZ - minZ)};
    }

    /** Seconds of game time, wrapped every hour of ticks (one unseen jump an hour keeps float precision). */
    public static float time(long gameTime, float partialTick) {
        return (Math.floorMod(gameTime, HOUR_TICKS) + partialTick) / 20f;
    }

    /** 0 by day, 1 at night, from the level's sky darkening (15 minus the sky light). */
    public static float night(int skyDarken) {
        return Math.max(0f, Math.min(1f, skyDarken / 11f));
    }
}
