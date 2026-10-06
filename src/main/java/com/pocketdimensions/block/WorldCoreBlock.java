package com.pocketdimensions.block;

import com.mojang.serialization.MapCodec;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.init.ModBlockEntityTypes;
import com.pocketdimensions.menu.WorldCoreMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The World Core, the Geode Heart: a two-block boulder floating at the realm's centre around the realm's black hole.
 * The block entity lives on the lower half; clicks on either half resolve to it.
 */
public class WorldCoreBlock extends BaseEntityBlock {

    public static final MapCodec<WorldCoreBlock> CODEC = simpleCodec(WorldCoreBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** Siege state, mirrored from WorldCoreBlockEntity.STATE_*: 0 normal, 1 breaching, 2 breaking, 3 anchor lost (inert, fallen). */
    public static final IntegerProperty SIEGE = IntegerProperty.create("siege", 0, 3);

    // The boulder floats 4 px above the ground; once inert it has fallen onto it
    private static final VoxelShape LOWER = Block.box(1, 4, 1, 15, 16, 15), LOWER_FALLEN = Block.box(1, 0, 1, 15, 16, 15);
    private static final VoxelShape UPPER = Block.box(1, 0, 1, 15, 14, 15), UPPER_FALLEN = Block.box(1, 0, 1, 15, 10, 15);

    public WorldCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(SIEGE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, SIEGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        boolean fallen = state.getValue(SIEGE) == WorldCoreBlockEntity.STATE_ANCHOR_LOST;
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? (fallen ? LOWER_FALLEN : LOWER) : (fallen ? UPPER_FALLEN : UPPER);
    }

    /** Sets SIEGE on both halves of the core whose lower half is at lowerPos. */
    public static void setSiege(Level level, BlockPos lowerPos, int siege) {
        for (BlockPos p : new BlockPos[]{lowerPos, lowerPos.above()}) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof WorldCoreBlock && s.getValue(SIEGE) != siege) {
                level.setBlock(p, s.setValue(SIEGE, siege), 3);
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new WorldCoreBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, ModBlockEntityTypes.WORLD_CORE.get(), WorldCoreBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntityTypes.WORLD_CORE.get(), WorldCoreBlockEntity::serverTick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // -------------------------------------------------------------------------
    // Two blocks tall (door pattern). Normally placed by RealmManager; this covers creative placement.
    // -------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() < level.getMaxY() && level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return defaultBlockState();
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState other = level.getBlockState(otherPos);
            if (other.is(this) && other.getValue(HALF) != half) {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, otherPos, Block.getId(other));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** The upper half goes with the lower. */
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

    @Nullable
    private static WorldCoreBlockEntity coreAt(BlockState state, Level level, BlockPos pos) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        return level.getBlockEntity(lower) instanceof WorldCoreBlockEntity be ? be : null;
    }

    /**
     * Right-click with item:
     * - Crouch: open GUI (owner only)
     * - Lapis (not crouching): direct fuel insert (owner only)
     * - Anything else: exit realm
     */
    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        WorldCoreBlockEntity be = coreAt(state, level, pos);
        if (be == null) return InteractionResult.FAIL;

        if (player.isShiftKeyDown()) {
            return openGui(player, be);
        }

        if (stack.is(Items.LAPIS_LAZULI)) {
            be.tryInsertFuel(player, stack, level);
        } else {
            be.exitRealm(player, level);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Empty-hand right-click:
     * - Crouch: open GUI (owner only)
     * - Normal: exit realm
     */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        WorldCoreBlockEntity be = coreAt(state, level, pos);
        if (be == null) return InteractionResult.FAIL;

        if (player.isShiftKeyDown()) {
            return openGui(player, be);
        }

        be.exitRealm(player, level);
        return InteractionResult.SUCCESS;
    }

    /** Anyone who reaches the core may open it; what they see and may do depends on their role (RealmRules). */
    private InteractionResult openGui(Player player, WorldCoreBlockEntity be) {
        if (be.getOwnerUUID() == null) {
            player.displayClientMessage(Component.literal("The core is silent. No realm answers it."), false);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(be, buf -> WorldCoreMenu.writeExtraData(buf, be, sp));
        }
        return InteractionResult.SUCCESS;
    }
}
