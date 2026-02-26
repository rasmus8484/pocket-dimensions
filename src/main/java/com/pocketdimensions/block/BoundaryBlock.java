package com.pocketdimensions.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The indestructible shell around each pocket room interior.
 * Players cannot place or obtain this block in survival.
 * The mod generates it programmatically when a pocket room is allocated.
 */
public class BoundaryBlock extends Block {

    public BoundaryBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
