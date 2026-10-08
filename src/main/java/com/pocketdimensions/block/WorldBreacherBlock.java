package com.pocketdimensions.block;

import com.mojang.serialization.MapCodec;
import com.pocketdimensions.blockentity.WorldBreacherBlockEntity;
import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.init.ModBlocks;
import com.pocketdimensions.init.ModParticles;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
    /** True once the breach is complete: the eye on top lights up. */
    public static final BooleanProperty COMPLETE = BooleanProperty.create("complete");
    /** The Mandible's head in its own block; the mandibles reaching into the anchor are visual only. */
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);

    public WorldBreacherBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COMPLETE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COMPLETE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /**
     * Client-only: pink motes leave the four mandible hooks and curve over the windows into the
     * anchor's black hole. Only on a linked anchor (there is no black hole otherwise).
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.is(ModBlocks.WORLD_ANCHOR.get()) || !below.getValue(WorldAnchorBlock.LINKED)) return;
        double coreY = (13.5 - 32) / 16.0, hookY = (22.5 - 32) / 16.0;
        for (int n = 0; n < 3; n++) {
            double sx = random.nextBoolean() ? 1 : -1, sz = random.nextBoolean() ? 1 : -1;
            double hx = 0.5 + sx * 6 / 16.0, hz = 0.5 + sz * 6 / 16.0;
            level.addParticle(ModParticles.DRAIN.get(), pos.getX() + hx, pos.getY() + hookY, pos.getZ() + hz,
                    0.5 - hx, coreY - hookY, 0.5 - hz);
        }
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Mined by a player: destroyed with its fuel, or dropped with it if the server config says so. */
    @Override
    public void playerDestroy(Level level, net.minecraft.world.entity.player.Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (blockEntity instanceof WorldBreacherBlockEntity be) SiegeBlockDrops.onMined(level, pos, this, be.getInventory());
    }

    /** Only a diamond-tier pickaxe or better can break it, in a fixed time (server config). */
    @Override
    public float getDestroyProgress(BlockState state, net.minecraft.world.entity.player.Player player,
                                    net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return DiamondPickaxeMining.progressPerTick(player, state,
                com.pocketdimensions.PocketDimensionsServerConfig.WORLD_BREACHER_MINE_SECONDS.get());
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
        return level.isClientSide()
                ? createTickerHelper(type, ModBlockEntityTypes.WORLD_BREACHER.get(), WorldBreacherBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntityTypes.WORLD_BREACHER.get(), WorldBreacherBlockEntity::serverTick);
    }

    /** A breach can only begin while the realm has one of its own inside (SiegePlacement). */
    @Override
    public @Nullable BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) return null;
        return SiegePlacement.defenderInside(ctx, "The realm lies empty. A breach needs one of its own inside.") ? state : null;
    }

    /** Only survives when placed on the UPPER half of a WorldAnchor. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(ModBlocks.WORLD_ANCHOR.get())
                && below.getValue(WorldAnchorBlock.HALF) == DoubleBlockHalf.UPPER;
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

    /** Right-click with lapis -> add fuel. Crouch+right-click -> open GUI. Non-lapis items pass through. */
    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            return openGui(player, level, pos);
        }
        if (!stack.is(Items.LAPIS_LAZULI)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        WorldBreacherBlockEntity be = (WorldBreacherBlockEntity) level.getBlockEntity(pos);
        if (be == null) return InteractionResult.FAIL;

        int inserted = be.insertLapis(stack.getCount());
        if (inserted > 0) {
            if (!player.getAbilities().instabuild) stack.shrink(inserted);
            player.displayClientMessage(Component.literal(
                    "The breacher drinks in the lapis."), false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            return openGui(player, level, pos);
        }
        return InteractionResult.PASS;
    }

    private InteractionResult openGui(Player player, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof WorldBreacherBlockEntity be)) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(be, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
