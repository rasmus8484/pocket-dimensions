package com.pocketdimensions.block;

import com.mojang.serialization.MapCodec;
import com.pocketdimensions.blockentity.WorldAnchorBlockEntity;
import com.pocketdimensions.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class WorldAnchorBlock extends BaseEntityBlock {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final MapCodec<WorldAnchorBlock> CODEC = simpleCodec(WorldAnchorBlock::new);

    public WorldAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    // -------------------------------------------------------------------------
    // Placement — two-block-tall (door pattern)
    // -------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() < level.getMaxY() && level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null; // cancel placement if no room for upper half
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        level.setBlock(pos.above(), defaultBlockState().setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    // -------------------------------------------------------------------------
    // Breaking — remove partner half (handles creative mode)
    // -------------------------------------------------------------------------

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(this) && otherState.getValue(HALF) != half) {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** UPPER half self-destructs if LOWER is missing (enables AnchorBreaker cascade). */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            if (!below.is(this) || below.getValue(HALF) != DoubleBlockHalf.LOWER) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 35);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Block entity — LOWER half only
    // -------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? new WorldAnchorBlockEntity(pos, state) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // -------------------------------------------------------------------------
    // Interactions — resolve UPPER clicks to LOWER BE
    // -------------------------------------------------------------------------

    /**
     * Delegate item-in-hand right-clicks to useWithoutItem so the anchor works regardless
     * of what the player is holding. WorldSeed is passed through so Item#useOn handles linking.
     */
    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ModItems.WORLD_SEED.get())) return InteractionResult.PASS;
        return useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        // Resolve to LOWER half for BE access
        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        WorldAnchorBlockEntity be = (WorldAnchorBlockEntity) level.getBlockEntity(lowerPos);
        if (be == null) return InteractionResult.FAIL;

        be.tryEnterRealm(player, level, lowerPos);
        return InteractionResult.SUCCESS;
    }
}
