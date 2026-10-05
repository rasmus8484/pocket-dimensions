package com.pocketdimensions.block;

import com.mojang.serialization.MapCodec;
import com.pocketdimensions.blockentity.AnchorBreakerBlockEntity;
import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.init.ModBlocks;
import com.pocketdimensions.init.ModParticles;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
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
 * Anchor Breaker (the Unmaker) - siege block placed on top of a WorldAnchor.
 * On completion destroys the WorldAnchor (and drops itself via neighborChanged).
 */
public class AnchorBreakerBlock extends BaseEntityBlock {

    public static final MapCodec<AnchorBreakerBlock> CODEC = simpleCodec(AnchorBreakerBlock::new);
    /** Breaking progress quarter: 0 not started, 1..4 = 0-25 % .. 75-100 %. Fills the coils; 2..4 add lightning sets. */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
    /** Shoulders and housing, plus the coils and spire on top; the clamps reaching into the anchor are visual only. */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 8, 16), Block.box(3, 8, 3, 13, 15, 13));

    public AnchorBreakerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /**
     * Client-only: while fueled over a linked anchor, red motes pour out of the black hole through the windows to
     * the clamp feet, and siphon motes rise from it into the funnel.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.is(ModBlocks.WORLD_ANCHOR.get()) || !below.getValue(WorldAnchorBlock.LINKED)) return;
        if (!(level.getBlockEntity(pos) instanceof AnchorBreakerBlockEntity be) || !be.hasFuel()) return;
        double cx = pos.getX() + 0.5, cy = pos.getY() + (13.5 - 32) / 16.0, cz = pos.getZ() + 0.5;
        double footY = (19.5 - 32) / 16.0 - (13.5 - 32) / 16.0;
        for (int n = 0; n < 2; n++) {
            double fx = (random.nextBoolean() ? 7 : -7) / 16.0, fz = (random.nextBoolean() ? 7 : -7) / 16.0;
            level.addParticle(ModParticles.UNMAKE.get(), cx, cy, cz, fx, footY, fz);
        }
        for (int n = 0; n < 2; n++) {
            double ox = (random.nextDouble() - 0.5) * 1.2 / 16.0, oz = (random.nextDouble() - 0.5) * 1.2 / 16.0;
            level.addParticle(ModParticles.SIPHON.get(), cx + ox, cy, cz + oz, -ox, -(13.5 - 32) / 16.0, -oz);
        }
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Takes a fixed time to mine with any tool or by hand (server config). */
    @Override
    public float getDestroyProgress(BlockState state, net.minecraft.world.entity.player.Player player,
                                    net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return com.pocketdimensions.SiegeTuning.mineProgressPerTick(
                com.pocketdimensions.PocketDimensionsServerConfig.ANCHOR_BREAKER_MINE_SECONDS.get());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnchorBreakerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntityTypes.ANCHOR_BREAKER.get(),
                AnchorBreakerBlockEntity::serverTick);
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
        AnchorBreakerBlockEntity be = (AnchorBreakerBlockEntity) level.getBlockEntity(pos);
        if (be == null) return InteractionResult.FAIL;

        int inserted = be.insertLapis(stack.getCount());
        if (inserted > 0) {
            if (!player.getAbilities().instabuild) stack.shrink(inserted);
            player.displayClientMessage(Component.literal(
                    "The breaker absorbs the lapis hungrily."), false);
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
        if (!(level.getBlockEntity(pos) instanceof AnchorBreakerBlockEntity be)) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(be, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
