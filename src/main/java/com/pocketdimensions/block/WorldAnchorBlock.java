package com.pocketdimensions.block;

import com.pocketdimensions.blockentity.WorldAnchorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * World Anchor — realm entry point.  Placeable in any dimension.
 * <p>
 * Access check (server-side, at interaction time only):
 * - If no breach active → owner only.
 * - If breach at 100% AND WorldBreacher above AND breacher has ≥1 lapis → anyone.
 * <p>
 * The block entity stores: owner UUID, breach progress reference.
 */
public class WorldAnchorBlock extends BaseEntityBlock {

    public WorldAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorldAnchorBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        WorldAnchorBlockEntity be = (WorldAnchorBlockEntity) level.getBlockEntity(pos);
        if (be == null) return InteractionResult.FAIL;

        be.tryEnterRealm(player, level, pos);
        return InteractionResult.CONSUME;
    }
}
