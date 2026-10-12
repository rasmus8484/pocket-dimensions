package com.pocketkingdoms;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client config for Pocket Kingdoms, saved per player to config/pocketkingdoms-client.toml. Only how this player's
 * game draws things: nothing here changes gameplay.
 */
public class PocketKingdomsClientConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue VEIL_FOG;
    public static final ForgeConfigSpec.IntValue VEIL_FOG_STEPS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("The Veil: the falling smoke along every edge of a realm.").push("veil");

        VEIL_FOG = builder
                .comment("Draw the falling smoke. Off: the Veil is a plain wall of mist blocks. Default: true.")
                .define("veil_fog", true);

        VEIL_FOG_STEPS = builder
                .comment("How many samples the smoke takes near the wall, per pixel. Fewer is cheaper on the graphics card",
                         "(6 halves the cost near the wall) and a little grainier. Default: 12.")
                .defineInRange("veil_fog_steps", 12, 4, 16);

        builder.pop();
        SPEC = builder.build();
    }
}
