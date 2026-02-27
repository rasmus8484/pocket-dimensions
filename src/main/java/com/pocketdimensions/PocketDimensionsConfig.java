package com.pocketdimensions;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common config for Pocket Dimensions — saved to config/pocketdimensions-common.toml.
 *
 * Changing realm_size or realm_padding while a world already has generated realms
 * will break boundary enforcement for existing realms. Treat these as set-once values.
 */
public class PocketDimensionsConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue REALM_SIZE;
    public static final ForgeConfigSpec.IntValue REALM_PADDING;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Realm dimension settings").push("realm");

        REALM_SIZE = builder
                .comment("Playable XZ area per realm in blocks.",
                         "Stride is automatically realm_size + 2 * realm_padding.",
                         "Default: 48 (3×3 chunks). Change before first world launch.")
                .defineInRange("realm_size", 48, 16, 100000);

        REALM_PADDING = builder
                .comment("Dead-space buffer between adjacent realm plots in blocks.",
                         "Prevents terrain bleed between neighbours.",
                         "Default: 8.")
                .defineInRange("realm_padding", 8, 0, 10000);

        builder.pop();
        SPEC = builder.build();
    }
}
