package com.pocketdimensions.block;

import com.pocketdimensions.PocketDimensionsServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * What a World Breacher or Anchor Breaker leaves behind when a player mines it. By default nothing: the block and the
 * lapis in it are destroyed. With mining.siege_blocks_drop on (server config) it drops itself and its lapis.
 */
final class SiegeBlockDrops {

    private SiegeBlockDrops() {}

    static void onMined(Level level, BlockPos pos, Block block, Container fuel) {
        if (level.isClientSide() || !PocketDimensionsServerConfig.SIEGE_BLOCKS_DROP.get()) return;
        Block.popResource(level, pos, new ItemStack(block));
        Containers.dropContents(level, pos, fuel);
    }
}
