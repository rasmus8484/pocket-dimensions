package com.pocketdimensions.block;

import com.mojang.serialization.MapCodec;
import com.pocketdimensions.blockentity.WorldAnchorBlockEntity;
import com.pocketdimensions.init.ModItems;
import com.pocketdimensions.init.ModParticles;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class WorldAnchorBlock extends BaseEntityBlock {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** True once a World Seed has linked this anchor to a realm; swaps the inert and linked models. */
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");
    /** Siege influence from a World Breacher on top: 0 none, 1..3 breaching (spreads with progress), 4 breach complete. */
    public static final IntegerProperty INFLUENCE = IntegerProperty.create("influence", 0, 4);
    /** Damage from an Anchor Breaker on top: 0 none, 1..4 = breaking progress quarters (cracks, heated runes, sigil). */
    public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 4);
    private static final VoxelShape LOWER_SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 5, 16), Block.box(2, 5, 2, 14, 16, 14));
    private static final VoxelShape UPPER_SHAPE = Block.box(2, 0, 2, 14, 15, 14);
    public static final MapCodec<WorldAnchorBlock> CODEC = simpleCodec(WorldAnchorBlock::new);

    public WorldAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(LINKED, false).setValue(INFLUENCE, 0).setValue(DAMAGE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPE : UPPER_SHAPE;
    }

    /**
     * Client-only, called at random for blocks near the player (about 0.4 calls/s per block);
     * spawning two runes per call gives roughly one rune per second per linked anchor.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER || !state.getValue(LINKED)) return;
        for (int n = 0; n < 2; n++) {
            int face = random.nextInt(4);
            double nx = face == 2 ? -1 : face == 3 ? 1 : 0, nz = face == 0 ? -1 : face == 1 ? 1 : 0;
            double along = (random.nextDouble() - 0.5) * 9 / 16.0;
            double y = random.nextBoolean() ? (18 + random.nextDouble() * 5) / 16.0 : (5 + random.nextDouble() * 5) / 16.0;
            double x = pos.getX() + 0.5 + nx * 6.8 / 16.0 + (nz != 0 ? along : 0);
            double z = pos.getZ() + 0.5 + nz * 6.8 / 16.0 + (nx != 0 ? along : 0);
            double out = (0.5 + random.nextDouble() * 0.5) / 16.0 / 20.0;       // 0.5–1 px per second
            double up = (1.2 + random.nextDouble() * 0.8) / 16.0 / 20.0;        // 1.2–2 px per second
            // Breacher influence tints the runes that drift off: cyan, cyan/pink while breaching, pink/gold once breached.
            // A breaker heats them instead: red, with gold sparks joining past the halfway mark.
            int influence = state.getValue(INFLUENCE), damage = state.getValue(DAMAGE);
            var type = damage > 0 ? (damage > 2 && random.nextInt(3) == 0 ? ModParticles.RUNE_GOLD.get() : ModParticles.RUNE_RED.get())
                    : influence == 0 ? ModParticles.RUNE.get()
                    : influence < 4 ? (random.nextBoolean() ? ModParticles.RUNE.get() : ModParticles.RUNE_PINK.get())
                    : (random.nextBoolean() ? ModParticles.RUNE_PINK.get() : ModParticles.RUNE_GOLD.get());
            level.addParticle(type, x, pos.getY() + y, z, nx * out, up, nz * out);
        }
    }

    /** Sets INFLUENCE on both halves of the anchor whose lower half is at lowerPos. */
    public static void setInfluence(Level level, BlockPos lowerPos, int influence) {
        for (BlockPos p : new BlockPos[]{lowerPos, lowerPos.above()}) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof WorldAnchorBlock && s.getValue(INFLUENCE) != influence) {
                level.setBlock(p, s.setValue(INFLUENCE, influence), 3);
            }
        }
    }

    /** Sets DAMAGE on both halves of the anchor whose lower half is at lowerPos. */
    public static void setDamage(Level level, BlockPos lowerPos, int damage) {
        for (BlockPos p : new BlockPos[]{lowerPos, lowerPos.above()}) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof WorldAnchorBlock && s.getValue(DAMAGE) != damage) {
                level.setBlock(p, s.setValue(DAMAGE, damage), 3);
            }
        }
    }

    /** Sets LINKED on both halves of the anchor whose lower half is at lowerPos. */
    public static void setLinked(Level level, BlockPos lowerPos, boolean linked) {
        for (BlockPos p : new BlockPos[]{lowerPos, lowerPos.above()}) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof WorldAnchorBlock && s.getValue(LINKED) != linked) {
                level.setBlock(p, s.setValue(LINKED, linked), 3);
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, LINKED, INFLUENCE, DAMAGE);
    }

    // -------------------------------------------------------------------------
    // Placement — two-block-tall (door pattern)
    // -------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return null; // no room for the upper half
        }
        BlockPos siege = pos.above(2);
        if (!com.pocketdimensions.manager.RealmRules.roomForSiege(siege.getY(), level.getMaxY(),
                level.getBlockState(siege).getDestroySpeed(level, siege))) {
            if (!level.isClientSide() && ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.literal(
                    "The anchor will not settle beneath what can never be broken."), true);
            return null;
        }
        return defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER);
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
                return;
            }
            // The breacher on top is gone: the anchor shakes off its influence
            if (state.getValue(INFLUENCE) > 0 && !(level.getBlockState(pos.above()).getBlock() instanceof WorldBreacherBlock)) {
                setInfluence(level, pos.below(), 0);
            }
            // The breaker on top is gone: the cracks close and the runes cool
            if (state.getValue(DAMAGE) > 0 && !(level.getBlockState(pos.above()).getBlock() instanceof AnchorBreakerBlock)) {
                setDamage(level, pos.below(), 0);
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
