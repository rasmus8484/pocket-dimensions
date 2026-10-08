package com.pocketdimensions;

/** Small pure helpers behind the server-config tuning (no Minecraft classes, so they can be unit tested). */
public final class SiegeTuning {

    private SiegeTuning() {}

    /** Mining progress per tick for a block that should take this many seconds to break (0 = instantly). */
    public static float mineProgressPerTick(double seconds) {
        return seconds <= 0 ? 1f : (float) (1.0 / (seconds * 20.0));
    }

    /**
     * One running tick of a lapis burning: how long it has burnt, plus one. Once that reaches {@code burnTicks}
     * (core_fuel_burn_ticks) the lapis is used up and this returns 0, so every lapis lasts the full burn time.
     */
    public static int burnTick(int burnt, int burnTicks) {
        return burnt + 1 >= burnTicks ? 0 : burnt + 1;
    }
}
