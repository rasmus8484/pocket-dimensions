package com.pocketdimensions.client.ward;

import net.minecraft.util.Mth;

/**
 * The ward's sigil hangs hundreds of blocks up, past where the game's fog and far plane reach. Each piece is drawn
 * pulled toward the camera instead, scaled about it by one factor: something twice as close and half as big covers
 * exactly the same pixels, so the picture is unchanged while it sits inside the fog-free zone. Plain logic, unit tested.
 */
public final class CameraPull {

    private static final double MAX_REACH = 96;

    private CameraPull() {}

    /** How far from the camera pulled pieces are put: inside the fog-free zone, never more than 96 blocks. */
    public static double reach(float renderDistanceBlocks) {
        double fogStart = renderDistanceBlocks - Mth.clamp(renderDistanceBlocks / 10f, 4f, 64f);
        return Math.min(MAX_REACH, 0.9 * fogStart);
    }

    /** The scale about the camera for a piece `distance` away: 1 within reach, else what brings it to reach. */
    public static double factor(double distance, double reach) {
        return distance <= reach ? 1 : reach / distance;
    }
}
