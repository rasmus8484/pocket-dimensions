package com.pocketdimensions.block;

import com.mojang.serialization.MapCodec;
import com.pocketdimensions.blockentity.WorldBreacherBlockEntity;
import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * World Breacher - siege add-on placed on top of a WorldAnchor.
 * Cleanup (progress reset) is handled in WorldBreacherBlockEntity.setRemoved().
 */
public class WorldBreacherBlock extends BaseEntityBlock {

    public static final MapCodec<WorldBreacherBlock> CODEC = simpleCodec(WorldBreacherBlock::new);

    public WorldBreacherBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorldBreacherBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntityTypes.WORLD_BREACHER.get(),
                WorldBreacherBlockEntity::serverTick);
    }

    /** Only survives when placed directly on a WorldAnchor. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(ModBlocks.WORLD_ANCHOR.get());
    }

    /** Drop the block if the WorldAnchor below is removed. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide() && !state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Right-click with lapis -> add fuel. Non-lapis items pass through. */
    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.LAPIS_LAZULI)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        WorldBreacherBlockEntity be = (WorldBreacherBlockEntity) level.getBlockEntity(pos);
        if (be == null) return InteractionResult.FAIL;

        int amount = stack.getCount();
        be.addFuel(amount);
        if (!player.getAbilities().instabuild) stack.shrink(amount);
        player.displayClientMessage(Component.literal(
                "The breacher drinks in the lapis. (" + be.getFuel() + " stored)"), false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }
}
