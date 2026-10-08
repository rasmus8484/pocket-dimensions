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

    public static final RegistryObject<Block> BOUNDARY_BLOCK = BLOCKS.register("boundary_block",
            () -> new BoundaryBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("boundary_block"))
                    .strength(-1.0f, 3600000.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> POCKET_ANCHOR = BLOCKS.register("pocket_anchor",
            () -> new PocketAnchorBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("pocket_anchor"))
                    .strength(50.0f, 1200.0f)
                    .lightLevel(state -> state.getValue(PocketAnchorBlock.OCCUPIED) ? 9 : 6)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    // its 10 px cube is too small to count as solid, and flowing liquid washes non-solid blocks away
                    // (losing the room): forced solid, water and lava treat it like stone
                    .forceSolidOn()));

    public static final RegistryObject<Block> WORLD_ANCHOR = BLOCKS.register("world_anchor",
            () -> new WorldAnchorBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("world_anchor"))
                    .strength(-1.0f, 3600000.0f)
                    .lightLevel(state -> state.getValue(WorldAnchorBlock.LINKED) ? 10 : 0)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> WORLD_BREACHER = BLOCKS.register("world_breacher",
            () -> new WorldBreacherBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("world_breacher"))
                    .strength(50.0f, 1200.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> ANCHOR_BREAKER = BLOCKS.register("anchor_breaker",
            () -> new AnchorBreakerBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("anchor_breaker"))
                    .strength(50.0f, 1200.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> WORLD_CORE = BLOCKS.register("world_core",
            () -> new WorldCoreBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("world_core"))
                    .strength(-1.0f, 3600000.0f)
                    // the Geode Heart glows while it lives; once the anchor is lost it falls dark
                    .lightLevel(state -> state.getValue(WorldCoreBlock.SIEGE) == 3 ? 0 : 12)
                    .noOcclusion()
                    .sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops()));
}
