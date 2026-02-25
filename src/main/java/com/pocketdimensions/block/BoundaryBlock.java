package com.pocketdimensions.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The indestructible shell around each pocket room interior.
 * <p>
 * Properties enforced via BlockBehaviour.Properties in ModBlocks:
 * - Hardness / blast resistance set to max (indestructible in survival)
 * - Light level 15 (full glow)
 * - Not movable by pistons (handled by hardness)
 * - Not obtainable in survival (no drops; not in survival loot tables)
 */
public class BoundaryBlock extends Block {

    public BoundaryBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    // No special interactions. Players cannot place or obtain this block in survival.
    // The mod generates it programmatically when a pocket room is allocated.
}
