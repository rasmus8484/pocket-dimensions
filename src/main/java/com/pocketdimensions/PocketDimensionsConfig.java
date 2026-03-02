package com.pocketdimensions;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common config for Pocket Dimensions - saved to config/pocketdimensions-common.toml.
 *
 * Changing realm geometry settings while a world already has generated realms
 * will break boundary enforcement for existing realms. Treat these as set-once values.
 */
public class PocketDimensionsConfig {

    public static final ForgeConfigSpec SPEC;

    /** Radius in chunks. Side length per plot = 2*radius-1 chunks. Default 2 -> 3x3 chunks (48x48 blocks). */
    public static final ForgeConfigSpec.IntValue REALM_RADIUS_CHUNKS;

    /** Dead-space gap between adjacent plots in chunks. Default 1. */
    public static final ForgeConfigSpec.IntValue REALM_PADDING_CHUNKS;

    /** Maximum chunk search radius when scanning for dry land to place WorldCore. Default 16. */
    public static final ForgeConfigSpec.IntValue MAX_SPAWN_SEARCH_CHUNKS;

    // ---- Siege config ----

    /** World Breacher full-breach duration in ticks (1 MC day = 24000). */
    public static final ForgeConfigSpec.IntValue BREACH_DURATION_TICKS;

    /** Anchor Breaker anchor-destroy duration in ticks (1 MC day = 24000). */
    public static final ForgeConfigSpec.IntValue BREAKER_DURATION_TICKS;

    /** WorldCore defense: advance attacker progress 1/N ticks (N=3 -> 3x slowdown). */
    public static final ForgeConfigSpec.IntValue CORE_SLOW_FACTOR;

    /** Ticks between each attacker lapis consumed. Default 200. */
    public static final ForgeConfigSpec.IntValue CORE_FUEL_BURN_TICKS;

    /** Radius in blocks within which players see siege boss bars. */
    public static final ForgeConfigSpec.IntValue SIEGE_BOSSBAR_RANGE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Realm dimension settings").push("realm");

        REALM_RADIUS_CHUNKS = builder
                .comment("Realm radius in chunks. Side length per plot = 2*radius-1 chunks.",
                         "Default: 2 -> 3x3 chunk plot (48x48 blocks). Change before first world launch.")
                .defineInRange("realm_radius_chunks", 2, 1, 10000);

        REALM_PADDING_CHUNKS = builder
                .comment("Dead-space gap between adjacent realm plots in chunks.",
                         "Prevents terrain bleed between neighbours.",
                         "Default: 1.")
                .defineInRange("realm_padding_chunks", 1, 0, 10000);

        MAX_SPAWN_SEARCH_CHUNKS = builder
                .comment("Maximum chunk search radius when looking for dry land to place WorldCore.",
                         "Default: 16.")
                .defineInRange("max_spawn_search_chunks", 16, 1, 256);

        builder.pop();

        builder.comment("Siege mechanics settings").push("siege");

        BREACH_DURATION_TICKS = builder
                .comment("World Breacher full-breach duration in ticks (1 MC day = 24000).")
                .defineInRange("breach_duration_ticks", 24000, 1, Integer.MAX_VALUE);

        BREAKER_DURATION_TICKS = builder
                .comment("Anchor Breaker anchor-destroy duration in ticks (1 MC day = 24000).")
                .defineInRange("breaker_duration_ticks", 24000, 1, Integer.MAX_VALUE);

        CORE_SLOW_FACTOR = builder
                .comment("WorldCore defense: advance attacker progress every N ticks (N=3 -> 3x slowdown).")
                .defineInRange("core_slow_factor", 3, 1, 100);

        CORE_FUEL_BURN_TICKS = builder
                .comment("Ticks between each attacker lapis consumed.")
                .defineInRange("core_fuel_burn_ticks", 200, 1, Integer.MAX_VALUE);

        SIEGE_BOSSBAR_RANGE = builder
                .comment("Radius in blocks within which players see siege boss bars.")
                .defineInRange("siege_bossbar_range", 64, 8, 256);

        builder.pop();

        SPEC = builder.build();
    }
}
