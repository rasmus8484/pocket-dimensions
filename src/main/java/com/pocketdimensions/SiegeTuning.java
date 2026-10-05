package com.pocketdimensions;

/** Small pure helpers behind the server-config tuning (no Minecraft classes, so they can be unit tested). */
public final class SiegeTuning {

    private SiegeTuning() {}

    /** Mining progress per tick for a block that should take this many seconds to break (0 = instantly). */
    public static float mineProgressPerTick(double seconds) {
        return seconds <= 0 ? 1f : (float) (1.0 / (seconds * 20.0));
    }

    /** The fuel counter shown on the siege boss bars. */
    public static String fuelLabel(int lapis) {
        return lapis <= 0 ? "no lapis" : lapis + " lapis";
    }
}
