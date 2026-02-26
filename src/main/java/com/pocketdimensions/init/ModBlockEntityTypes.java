package com.pocketdimensions.init;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.blockentity.PocketAnchorBlockEntity;
import com.pocketdimensions.blockentity.WorldAnchorBlockEntity;
import com.pocketdimensions.blockentity.WorldBreakerBlockEntity;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

public class ModBlockEntityTypes {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PocketDimensionsMod.MODID);

    public static final RegistryObject<BlockEntityType<PocketAnchorBlockEntity>> POCKET_ANCHOR =
            BLOCK_ENTITY_TYPES.register("pocket_anchor",
                    () -> new BlockEntityType<>(PocketAnchorBlockEntity::new,
                            Set.of(ModBlocks.POCKET_ANCHOR.get())));

    public static final RegistryObject<BlockEntityType<WorldAnchorBlockEntity>> WORLD_ANCHOR =
            BLOCK_ENTITY_TYPES.register("world_anchor",
                    () -> new BlockEntityType<>(WorldAnchorBlockEntity::new,
                            Set.of(ModBlocks.WORLD_ANCHOR.get())));

    public static final RegistryObject<BlockEntityType<WorldBreakerBlockEntity>> WORLD_BREAKER =
            BLOCK_ENTITY_TYPES.register("world_breaker",
                    () -> new BlockEntityType<>(WorldBreakerBlockEntity::new,
                            Set.of(ModBlocks.WORLD_BREAKER.get())));

    public static final RegistryObject<BlockEntityType<WorldCoreBlockEntity>> WORLD_CORE =
            BLOCK_ENTITY_TYPES.register("world_core",
                    () -> new BlockEntityType<>(WorldCoreBlockEntity::new,
                            Set.of(ModBlocks.WORLD_CORE.get())));
}
