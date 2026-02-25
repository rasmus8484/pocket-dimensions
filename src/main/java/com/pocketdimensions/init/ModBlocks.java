package com.pocketdimensions.init;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, PocketDimensionsMod.MODID);

    // Pocket Room boundary shell — indestructible, glowing, white
    public static final RegistryObject<Block> BOUNDARY_BLOCK = BLOCKS.register("boundary_block",
            () -> new BoundaryBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0f, 3600000.0f)   // indestructible
                    .lightLevel(state -> 15)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    // Pocket Anchor — high-hardness, requires netherite to mine
    public static final RegistryObject<Block> POCKET_ANCHOR = BLOCKS.register("pocket_anchor",
            () -> new PocketAnchorBlock(BlockBehaviour.Properties.of()
                    .strength(50.0f, 1200.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    // World Anchor — realm entry point, similarly durable
    public static final RegistryObject<Block> WORLD_ANCHOR = BLOCKS.register("world_anchor",
            () -> new WorldAnchorBlock(BlockBehaviour.Properties.of()
                    .strength(50.0f, 1200.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    // World Breacher — siege add-on, placed atop World Anchor
    public static final RegistryObject<Block> WORLD_BREAKER = BLOCKS.register("world_breaker",
            () -> new WorldBreakerBlock(BlockBehaviour.Properties.of()
                    .strength(50.0f, 1200.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    // World Core — indestructible, permanent realm structure
    public static final RegistryObject<Block> WORLD_CORE = BLOCKS.register("world_core",
            () -> new WorldCoreBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0f, 3600000.0f)   // indestructible
                    .lightLevel(state -> 12)
                    .sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops()));
}
