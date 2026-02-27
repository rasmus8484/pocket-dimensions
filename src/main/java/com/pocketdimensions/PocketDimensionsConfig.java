package com.pocketdimensions;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common config for Pocket Dimensions — saved to config/pocketdimensions-common.toml.
 *
 * Changing realm geometry settings while a world already has generated realms
 * will break boundary enforcement for existing realms. Treat these as set-once values.
 */
public class PocketDimensionsConfig {

    public static final ForgeConfigSpec SPEC;

    /** Radius in chunks. Side length per plot = 2*radius-1 chunks. Default 2 → 3×3 chunks (48×48 blocks). */
    public static final ForgeConfigSpec.IntValue REALM_RADIUS_CHUNKS;

    /** Dead-space gap between adjacent plots in chunks. Default 1. */
    public static final ForgeConfigSpec.IntValue REALM_PADDING_CHUNKS;

    /** Maximum chunk search radius when scanning for dry land to place WorldCore. Default 16. */
    public static final ForgeConfigSpec.IntValue MAX_SPAWN_SEARCH_CHUNKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Realm dimension settings").push("realm");

        REALM_RADIUS_CHUNKS = builder
                .comment("Realm radius in chunks. Side length per plot = 2*radius-1 chunks.",
                         "Default: 2 → 3×3 chunk plot (48×48 blocks). Change before first world launch.")
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
        SPEC = builder.build();
    }
}
