package com.pocketdimensions.block;

import com.pocketdimensions.blockentity.WorldBreakerBlockEntity;
import com.pocketdimensions.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import com.pocketdimensions.init.ModBlockEntityTypes;

/**
 * World Breacher — siege add-on placed on top of a WorldAnchor.
 * <p>
 * Placement rules (enforced in WorldSeedItem / placement event):
 * - Must be placed directly on top of a WorldAnchor block.
 * - Realm owner must currently be inside their realm at placement time.
 * - Only one per anchor.
 * <p>
 * Ticks only while the chunk is loaded (standard Minecraft behaviour).
 * Siege progress advances per tick while: breacher exists, anchor below exists,
 * lapis fuel > 0, chunk loaded.
 */
public class WorldBreakerBlock extends BaseEntityBlock {

    public WorldBreakerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorldBreakerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntityTypes.WORLD_BREAKER.get(),
                WorldBreakerBlockEntity::serverTick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        WorldBreakerBlockEntity be = (WorldBreakerBlockEntity) level.getBlockEntity(pos);
        if (be == null) return InteractionResult.FAIL;

        // Right-click: show siege progress info to the interacting player
        be.sendStatusTo(player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            WorldBreakerBlockEntity be = (WorldBreakerBlockEntity) level.getBlockEntity(pos);
            if (be != null) {
                be.onDestroyed();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
