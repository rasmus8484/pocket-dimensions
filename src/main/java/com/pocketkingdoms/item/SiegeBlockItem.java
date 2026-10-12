package com.pocketkingdoms.item;

import com.pocketkingdoms.block.WorldAnchorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The World Breacher's and Anchor Breaker's item. Crouch + right-click a World Anchor anywhere (either half, any face)
 * and the siege block is set on top of it, as if its top had been clicked. The block's own rules still decide whether
 * it may go there (a linked anchor, one of the realm's own inside: SiegePlacement).
 */
public class SiegeBlockItem extends BlockItem {

    public SiegeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        BlockPos clicked = ctx.getClickedPos();
        BlockState state = ctx.getLevel().getBlockState(clicked);
        if (!(state.getBlock() instanceof WorldAnchorBlock)) return super.useOn(ctx);
        boolean upper = state.getValue(WorldAnchorBlock.HALF) == DoubleBlockHalf.UPPER;
        BlockPos top = new BlockPos(clicked.getX(), SiegeCrown.crownY(clicked.getY(), upper) - 1, clicked.getZ());
        return place(BlockPlaceContext.at(new BlockPlaceContext(ctx), top, Direction.UP));
    }
}
