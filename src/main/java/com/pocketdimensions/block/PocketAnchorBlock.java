package com.pocketdimensions.block;

import com.mojang.serialization.MapCodec;
import com.pocketdimensions.blockentity.PocketAnchorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class PocketAnchorBlock extends BaseEntityBlock {

    public static final MapCodec<PocketAnchorBlock> CODEC = simpleCodec(PocketAnchorBlock::new);

    public PocketAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        ItemStack tool = player.getMainHandItem();
        // Allow any pickaxe (vanilla or mod-added) that meets diamond tier
        if (tool.is(ItemTags.PICKAXES) && tool.isCorrectToolForDrops(state)) {
            return super.getDestroyProgress(state, player, level, pos);
        }
        return 0.0f;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        PocketAnchorBlockEntity be = (PocketAnchorBlockEntity) level.getBlockEntity(pos);
        if (be == null) return InteractionResult.FAIL;

        if (player.isShiftKeyDown()) {
            be.stealAnchor(player, level, pos, state);
        } else {
            be.enterRoom(player, level, pos);
        }

        return InteractionResult.SUCCESS;
    }

}
