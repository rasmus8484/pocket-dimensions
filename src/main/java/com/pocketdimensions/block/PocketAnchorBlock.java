package com.pocketdimensions.block;

import com.pocketdimensions.blockentity.PocketAnchorBlockEntity;
import com.pocketdimensions.init.ModBlockEntityTypes;
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
 * Pocket Anchor — the physical entry point for a pocket room.
 * <p>
 * Interactions (server-side):
 * - Right-click               → enter the linked pocket room (anyone)
 * - Crouch + right-click      → silently steal the anchor back to inventory as a PocketItem
 * - Mining (netherite tier)   → destroy the room permanently; eject occupants
 * <p>
 * The block entity stores: pocket_id (UUID), owner UUID.
 */
public class PocketAnchorBlock extends BaseEntityBlock {

    public PocketAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PocketAnchorBlockEntity(pos, state);
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

        PocketAnchorBlockEntity be = (PocketAnchorBlockEntity) level.getBlockEntity(pos);
        if (be == null) return InteractionResult.FAIL;

        if (player.isShiftKeyDown()) {
            // Crouch + right-click: silent theft — convert anchor back to PocketItem
            be.stealAnchor(player, level, pos, state);
        } else {
            // Right-click: enter the pocket room
            be.enterRoom(player, level, pos);
        }

        return InteractionResult.CONSUME;
    }
}
