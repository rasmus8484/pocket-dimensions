package com.pocketdimensions.block;

import com.mojang.serialization.MapCodec;
import com.pocketdimensions.blockentity.PocketAnchorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import com.pocketdimensions.init.ModBlockEntityTypes;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Pocket Anchor, the Tumbling Cube: a head-sized cube hovering in the block, turning on several axes inside three
 * bands of runes. The renderer draws all of it; the placed block's own model is empty.
 */
public class PocketAnchorBlock extends BaseEntityBlock {

    public static final MapCodec<PocketAnchorBlock> CODEC = simpleCodec(PocketAnchorBlock::new);
    /** Someone is in the room (an online occupant); set by the server, read by the renderer and the light level. */
    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");
    /** Never true in the world: the state the renderer draws to get the cube model (with its full-bright runes). */
    public static final BooleanProperty CUBE = BooleanProperty.create("cube");

    /** A still, head-sized box around the hovering cube, so it is easy to hit however the cube is turned. */
    private static final VoxelShape SHAPE = Block.box(3, 5.5, 3, 13, 15.5, 13);

    public PocketAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OCCUPIED, false).setValue(CUBE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OCCUPIED, CUBE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, ModBlockEntityTypes.POCKET_ANCHOR.get(), PocketAnchorBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntityTypes.POCKET_ANCHOR.get(), PocketAnchorBlockEntity::serverTick);
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
        return DiamondPickaxeMining.progressPerTick(player, state,
                com.pocketdimensions.PocketDimensionsServerConfig.POCKET_ANCHOR_MINE_SECONDS.get());
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
