package com.pocketdimensions.block;

import com.mojang.serialization.MapCodec;
import com.pocketdimensions.blockentity.WorldBreakerBlockEntity;
import com.pocketdimensions.init.ModBlockEntityTypes;
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

/**
 * WorldBreacher — siege add-on placed on top of a WorldAnchor.
 * Cleanup (progress reset) is handled in WorldBreakerBlockEntity.setRemoved().
 */
public class WorldBreakerBlock extends BaseEntityBlock {

    public static final MapCodec<WorldBreakerBlock> CODEC = simpleCodec(WorldBreakerBlock::new);

    public WorldBreakerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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

        be.sendStatusTo(player);
        return InteractionResult.SUCCESS;
    }
}
