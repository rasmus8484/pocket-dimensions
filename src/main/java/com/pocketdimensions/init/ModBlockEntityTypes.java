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

public class ModBlockEntityTypes {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PocketDimensionsMod.MODID);

    public static final RegistryObject<BlockEntityType<PocketAnchorBlockEntity>> POCKET_ANCHOR =
            BLOCK_ENTITY_TYPES.register("pocket_anchor",
                    () -> BlockEntityType.Builder
                            .of(PocketAnchorBlockEntity::new, ModBlocks.POCKET_ANCHOR.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<WorldAnchorBlockEntity>> WORLD_ANCHOR =
            BLOCK_ENTITY_TYPES.register("world_anchor",
                    () -> BlockEntityType.Builder
                            .of(WorldAnchorBlockEntity::new, ModBlocks.WORLD_ANCHOR.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<WorldBreakerBlockEntity>> WORLD_BREAKER =
            BLOCK_ENTITY_TYPES.register("world_breaker",
                    () -> BlockEntityType.Builder
                            .of(WorldBreakerBlockEntity::new, ModBlocks.WORLD_BREAKER.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<WorldCoreBlockEntity>> WORLD_CORE =
            BLOCK_ENTITY_TYPES.register("world_core",
                    () -> BlockEntityType.Builder
                            .of(WorldCoreBlockEntity::new, ModBlocks.WORLD_CORE.get())
                            .build(null));
}
