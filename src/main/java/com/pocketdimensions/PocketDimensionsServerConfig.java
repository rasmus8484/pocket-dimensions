package com.pocketdimensions;

import com.pocketdimensions.worldgen.RealmGenRules;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server config for Pocket Dimensions, saved per world to serverconfig/pocketdimensions-server.toml.
 *
 * These live in the server config (not the common one) because players' games must agree with the server: Forge sends
 * the server's values to everyone who joins. Mining time is predicted by the player's game, and the lapis slot limit is
 * checked on both sides of the GUI. The [realm] rules (what generates and spawns in the realms) are per world too.
 */
public class PocketDimensionsServerConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue POCKET_ANCHOR_MINE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue WORLD_BREACHER_MINE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue ANCHOR_BREAKER_MINE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue SIEGE_BLOCKS_DROP;

    public static final ForgeConfigSpec.IntValue WORLD_BREACHER_MAX_LAPIS;
    public static final ForgeConfigSpec.IntValue ANCHOR_BREAKER_MAX_LAPIS;

    public static final ForgeConfigSpec.BooleanValue GENERATE_STRUCTURES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_BLACKLIST;
    public static final ForgeConfigSpec.BooleanValue GENERATE_FEATURES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FEATURE_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FEATURE_BLACKLIST;
    public static final ForgeConfigSpec.BooleanValue SPAWN_MONSTERS;
    public static final ForgeConfigSpec.BooleanValue SPAWN_FRIENDLY_MOBS;
    /** Per mob category (by its name, e.g. "water_ambient"): "group", "true" or "false". */
    public static final Map<String, ForgeConfigSpec.ConfigValue<String>> MOB_CATEGORY_OVERRIDES = new LinkedHashMap<>();
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOB_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOB_BLACKLIST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("How long each block takes to mine, in seconds. The time is fixed: tool enchantments, Haste and",
                        "mining fatigue don't change it. 0 = breaks instantly.").push("mining");

        POCKET_ANCHOR_MINE_SECONDS = builder
                .comment("Pocket Anchor. Still needs a diamond-tier pickaxe or better; any other tool can't break it.",
                         "Default: 15 (time for the people inside to hear the warnings and see the cracks coming).")
                .defineInRange("pocket_anchor_mine_seconds", 15.0, 0.0, 3600.0);

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

        builder.comment("What generates and spawns in the realms. By default a realm is quiet and untouched: no structures,",
                        "no dungeons, no monsters, so a base there is never found by a village, raided from an outpost or",
                        "dug into by a dungeon's spawner. Change it if your server wants something else.",
                        "Each part has a switch, then exceptions: the whitelist lets things through whatever the switch says,",
                        "the blacklist keeps things out whatever else says (it beats the whitelist).",
                        "List entries are ids (\"minecraft:igloo\", or just \"igloo\") or tags (\"#minecraft:village\").",
                        "Structures and features only change in terrain generated after the change; structures need a restart.")
                .push("realm");

        builder.push("structures");
        GENERATE_STRUCTURES = builder
                .comment("Whether structures (villages, outposts, temples, ancient cities, strongholds, ...) generate. Default: false.")
                .define("generate_structures", false);
        STRUCTURE_WHITELIST = builder
                .comment("Structures that generate even with generate_structures = false.",
                         "Example: [\"minecraft:igloo\", \"minecraft:ruined_portal\", \"#minecraft:village\"]")
                .defineListAllowEmpty("structure_whitelist", List.of(), RealmGenRules::validEntry);
        STRUCTURE_BLACKLIST = builder
                .comment("Structures that never generate, even with generate_structures = true.",
                         "Example: [\"minecraft:pillager_outpost\", \"minecraft:ancient_city\", \"#minecraft:village\"]")
                .defineListAllowEmpty("structure_blacklist", List.of(), RealmGenRules::validEntry);
        builder.pop();

        builder.push("features");
        GENERATE_FEATURES = builder
                .comment("Whether features generate: the smaller things a biome scatters, from ores, trees, flowers and lakes",
                         "to dungeons, geodes and fossils (the names /place feature uses). Default: true.")
                .define("generate_features", true);
        FEATURE_WHITELIST = builder
                .comment("Features that generate even with generate_features = false.",
                         "Example: [\"minecraft:ore_iron_upper\", \"minecraft:ore_diamond\", \"minecraft:trees_plains\"]")
                .defineListAllowEmpty("feature_whitelist", List.of(), RealmGenRules::validEntry);
        FEATURE_BLACKLIST = builder
                .comment("Features that never generate, even with generate_features = true.",
                         "Default: dungeons (monster_room and monster_room_deep are the mossy rooms with a monster spawner).",
                         "Example: [\"minecraft:monster_room\", \"minecraft:monster_room_deep\", \"minecraft:amethyst_geode\", \"minecraft:lake_lava_surface\"]")
                .defineListAllowEmpty("feature_blacklist", List.of("minecraft:monster_room", "minecraft:monster_room_deep"),
                        RealmGenRules::validEntry);
        builder.pop();

        builder.comment("Natural spawning: mobs appearing on their own, in the dark or when new land first generates.",
                        "Monster spawners always work.").push("mobs");
        SPAWN_MONSTERS = builder
                .comment("Whether monsters (zombies, skeletons, creepers, spiders, slimes, ...) spawn. Default: false.")
                .define("spawn_monsters", false);
        SPAWN_FRIENDLY_MOBS = builder
                .comment("Whether every other kind spawns: animals, bats, fish, squid, axolotls. Default: true.")
                .define("spawn_friendly_mobs", true);
        builder.comment("Override per category: \"group\" follows spawn_monsters (for monster) or spawn_friendly_mobs",
                        "(for the rest); \"true\" or \"false\" decides for that category instead. Default: all \"group\".")
                .push("categories");
        for (MobCategory c : MobCategory.values()) {
            if (c == MobCategory.MISC) continue;                         // never spawns naturally
            MOB_CATEGORY_OVERRIDES.put(c.getSerializedName(), builder
                    .defineInList(c.getSerializedName(), "group", List.of("group", "true", "false")));
        }
        builder.pop();
        builder.comment("Exceptions, applied last.").push("exceptions");
        MOB_WHITELIST = builder
                .comment("Mobs that spawn even when their category or group is off.",
                         "Example: [\"minecraft:slime\", \"minecraft:enderman\", \"#minecraft:skeletons\"]")
                .defineListAllowEmpty("mob_whitelist", List.of(), RealmGenRules::validEntry);
        MOB_BLACKLIST = builder
                .comment("Mobs that never spawn, even when their category or group is on.",
                         "Example: [\"minecraft:bat\", \"minecraft:glow_squid\", \"minecraft:pufferfish\"]")
                .defineListAllowEmpty("mob_blacklist", List.of(), RealmGenRules::validEntry);
        builder.pop();
        builder.pop();

        builder.pop();

        SPEC = builder.build();
    }
}
