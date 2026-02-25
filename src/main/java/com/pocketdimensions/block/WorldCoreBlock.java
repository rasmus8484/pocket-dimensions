package com.pocketdimensions.block;

import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
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
 * World Core — permanent indestructible structure at the center of each realm region.
 * <p>
 * Responsibilities:
 * - Defines the realm's region center (used by border enforcement).
 * - Provides the guaranteed exit point (even if WorldAnchor is destroyed).
 * - Accepts lapis fuel from the realm owner to slow active breach attempts (3× slowdown).
 * <p>
 * The block entity stores: ownerUUID, fuel count, region center reference.
 */
public class WorldCoreBlock extends BaseEntityBlock {

    public WorldCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorldCoreBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        WorldCoreBlockEntity be = (WorldCoreBlockEntity) level.getBlockEntity(pos);
        if (be == null) return InteractionResult.FAIL;

        if (player.isShiftKeyDown()) {
            // Crouch + right-click: exit the realm
            be.exitRealm(player, level);
        } else {
            // Right-click: insert lapis fuel (owner only) or show status
            be.tryInsertFuel(player, level);
        }

        return InteractionResult.CONSUME;
    }
}
