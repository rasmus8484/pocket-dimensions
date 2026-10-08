package com.pocketdimensions.worldgen;

import com.pocketdimensions.PocketDimensionsServerConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;

/**
 * The server config's [realm] rules applied to the game's own things: a structure, a placed feature or a mob type is
 * looked up by its registry id and its tags and judged by RealmGenRules. Read on worldgen threads too (config values
 * are safe to read from any thread).
 */
public final class RealmWorldRules {

    private RealmWorldRules() {}

    /** True when every structure is off and nothing is whitelisted: worldgen can skip structures entirely. */
    public static boolean noStructures() {
        return !PocketDimensionsServerConfig.GENERATE_STRUCTURES.get()
                && PocketDimensionsServerConfig.STRUCTURE_WHITELIST.get().isEmpty();
    }

    public static boolean structureAllowed(Registry<Structure> registry, Structure structure) {
        return allowed(registry, registry.wrapAsHolder(structure), Registries.STRUCTURE,
                PocketDimensionsServerConfig.GENERATE_STRUCTURES.get(),
                PocketDimensionsServerConfig.STRUCTURE_WHITELIST.get(), PocketDimensionsServerConfig.STRUCTURE_BLACKLIST.get());
    }

    /** True when every feature generates: worldgen can use vanilla's own decoration unchanged. */
    public static boolean allFeatures() {
        return PocketDimensionsServerConfig.GENERATE_FEATURES.get()
                && PocketDimensionsServerConfig.FEATURE_BLACKLIST.get().isEmpty();
    }

    public static boolean featureAllowed(Registry<PlacedFeature> registry, PlacedFeature feature) {
        return allowed(registry, registry.wrapAsHolder(feature), Registries.PLACED_FEATURE,
                PocketDimensionsServerConfig.GENERATE_FEATURES.get(),
                PocketDimensionsServerConfig.FEATURE_WHITELIST.get(), PocketDimensionsServerConfig.FEATURE_BLACKLIST.get());
    }

    /** Whether this kind of mob may spawn naturally in the realm. */
    public static boolean mobAllowed(EntityType<?> type) {
        MobCategory category = type.getCategory();
        var override = PocketDimensionsServerConfig.MOB_CATEGORY_OVERRIDES.get(category.getSerializedName());
        boolean base = RealmGenRules.mobBase(category.isFriendly(), PocketDimensionsServerConfig.SPAWN_MONSTERS.get(),
                PocketDimensionsServerConfig.SPAWN_FRIENDLY_MOBS.get(), override == null ? "group" : override.get());
        return allowed(BuiltInRegistries.ENTITY_TYPE, BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type), Registries.ENTITY_TYPE,
                base, PocketDimensionsServerConfig.MOB_WHITELIST.get(), PocketDimensionsServerConfig.MOB_BLACKLIST.get());
    }

    private static <T> boolean allowed(Registry<T> registry, Holder<T> holder, ResourceKey<? extends Registry<T>> key,
                                       boolean base, List<? extends String> whitelist, List<? extends String> blacklist) {
        String id = holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
        return RealmGenRules.allowed(base, List.copyOf(whitelist), List.copyOf(blacklist), id, tag -> {
            Identifier tagId = Identifier.tryParse(tag);
            return tagId != null && holder.is(TagKey.create(key, tagId));
        });
    }
}
