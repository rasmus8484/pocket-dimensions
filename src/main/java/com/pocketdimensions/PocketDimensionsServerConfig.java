package com.pocketdimensions;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server config for Pocket Dimensions, saved per world to serverconfig/pocketdimensions-server.toml.
 *
 * These live in the server config (not the common one) because players' games must agree with the server: Forge sends
 * the server's values to everyone who joins. Mining time is predicted by the player's game, and the lapis slot limit is
 * checked on both sides of the GUI.
 */
public class PocketDimensionsServerConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue POCKET_ANCHOR_MINE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue WORLD_BREACHER_MINE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue ANCHOR_BREAKER_MINE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue SIEGE_BLOCKS_DROP;

    public static final ForgeConfigSpec.IntValue WORLD_BREACHER_MAX_LAPIS;
    public static final ForgeConfigSpec.IntValue ANCHOR_BREAKER_MAX_LAPIS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("How long each block takes to mine, in seconds. The time is fixed: tool enchantments, Haste and",
                        "mining fatigue don't change it. 0 = breaks instantly.").push("mining");

        POCKET_ANCHOR_MINE_SECONDS = builder
                .comment("Pocket Anchor. Still needs a diamond-tier pickaxe or better; any other tool can't break it.",
                         "Default: 9.4 (what a plain diamond pickaxe took before this setting existed).")
                .defineInRange("pocket_anchor_mine_seconds", 9.4, 0.0, 3600.0);

        WORLD_BREACHER_MINE_SECONDS = builder
                .comment("World Breacher. Needs a diamond-tier pickaxe or better. Default: 250 (the time it took before this setting existed).")
                .defineInRange("world_breacher_mine_seconds", 250.0, 0.0, 3600.0);

        ANCHOR_BREAKER_MINE_SECONDS = builder
                .comment("Anchor Breaker. Needs a diamond-tier pickaxe or better. Default: 250 (the time it took before this setting existed).")
                .defineInRange("anchor_breaker_mine_seconds", 250.0, 0.0, 3600.0);

        SIEGE_BLOCKS_DROP = builder
                .comment("Whether a mined World Breacher or Anchor Breaker drops itself and the lapis inside it.",
                         "false: it is destroyed along with its fuel, so breaking a siege block costs the attacker",
                         "everything they put into it. Default: false.")
                .define("siege_blocks_drop", false);

        builder.pop();

        builder.comment("Lapis fuel the siege blocks can hold (their single fuel slot, so 1-64).").push("fuel");

        WORLD_BREACHER_MAX_LAPIS = builder
                .comment("Most lapis a World Breacher can hold. Kept small so an attack needs tending. Default: 5.")
                .defineInRange("world_breacher_max_lapis", 5, 1, 64);

        ANCHOR_BREAKER_MAX_LAPIS = builder
                .comment("Most lapis an Anchor Breaker can hold. Kept small so an attack needs tending. Default: 5.")
                .defineInRange("anchor_breaker_max_lapis", 5, 1, 64);

        builder.pop();

        SPEC = builder.build();
    }
}
