package com.pocketdimensions.init;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.item.PocketItem;
import com.pocketdimensions.item.WorldSeedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PocketDimensionsMod.MODID);

    // ---- Block items ----

    public static final RegistryObject<Item> BOUNDARY_BLOCK_ITEM = ITEMS.register("boundary_block",
            () -> new BlockItem(ModBlocks.BOUNDARY_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> POCKET_ANCHOR_ITEM = ITEMS.register("pocket_anchor",
            () -> new BlockItem(ModBlocks.POCKET_ANCHOR.get(), new Item.Properties()));

    public static final RegistryObject<Item> WORLD_ANCHOR_ITEM = ITEMS.register("world_anchor",
            () -> new BlockItem(ModBlocks.WORLD_ANCHOR.get(), new Item.Properties()));

    public static final RegistryObject<Item> WORLD_BREAKER_ITEM = ITEMS.register("world_breaker",
            () -> new BlockItem(ModBlocks.WORLD_BREAKER.get(), new Item.Properties()));

    public static final RegistryObject<Item> WORLD_CORE_ITEM = ITEMS.register("world_core",
            () -> new BlockItem(ModBlocks.WORLD_CORE.get(), new Item.Properties()));

    // ---- Functional items ----

    // Pocket Item — creates/enters a pocket room; stackSize=1, each carries a pocket_id via NBT
    public static final RegistryObject<Item> POCKET_ITEM = ITEMS.register("pocket_item",
            () -> new PocketItem(new Item.Properties().stacksTo(1)));

    // World Seed — creates or rekeys a realm binding when used on a WorldAnchor
    public static final RegistryObject<Item> WORLD_SEED = ITEMS.register("world_seed",
            () -> new WorldSeedItem(new Item.Properties().stacksTo(1)));
}
