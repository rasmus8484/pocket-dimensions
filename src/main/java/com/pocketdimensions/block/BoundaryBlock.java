package com.pocketdimensions.block;

import com.mojang.serialization.MapCodec;
import com.pocketdimensions.blockentity.RoomVoidBlockEntity;
import com.pocketdimensions.manager.RoomShell;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * The indestructible shell around each pocket room. From inside, the room is the Tumbling Cube seen from within:
 * each wall, floor and ceiling a window into the void (drawn over the VOID blocks by the room's renderer), ringed by
 * netherite EDGE blocks with gold CORNER blocks. All full-bright, nothing casts a shadow on them, and the sky's light
 * passes through them so the room is fully lit for crops. One floor corner,
 * the HEART, carries the block entity whose renderer draws the void for the whole room. Players can never obtain it;
 * the room generator places it ({@link RoomShell} decides each block's part).
 */
public class BoundaryBlock extends BaseEntityBlock {

    public enum Part implements StringRepresentable {
        VOID("void"), EDGE("edge"), CORNER("corner");
        private final String name;
        Part(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
        public static Part of(RoomShell.Part p) { return values()[p.ordinal()]; }
    }

    public static final MapCodec<BoundaryBlock> CODEC = simpleCodec(BoundaryBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty HEART = BooleanProperty.create("heart");

    public BoundaryBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.VOID).setValue(HEART, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, HEART);
    }

    /**
     * A rider right-clicking a wall leaves the room, mount and all (TravelRules.wallLetsYouOut): crouching would set
     * them down, so they can't use crouch + jump. On foot, the wall does nothing.
     */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
                                                                   BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (!com.pocketdimensions.manager.TravelRules.wallLetsYouOut(player.isPassenger()))
            return net.minecraft.world.InteractionResult.PASS;
        if (!level.isClientSide()) com.pocketdimensions.event.PocketEventHandler.queueWallExit(player.getUUID());
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** With something in hand, the same: a rider goes out (nothing is placed or used against the wall). */
    @Override
    protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state,
                                                              net.minecraft.world.level.Level level, BlockPos pos,
                                                              net.minecraft.world.entity.player.Player player,
                                                              net.minecraft.world.InteractionHand hand,
                                                              net.minecraft.world.phys.BlockHitResult hit) {
        if (!com.pocketdimensions.manager.TravelRules.wallLetsYouOut(player.isPassenger()))
            return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
        return useWithoutItem(state, level, pos, player, hit);
    }

    /** The shell block for a local position in the room's 20-block shell. */
    public BlockState stateAt(int x, int y, int z) {
        RoomShell.Part part = RoomShell.partAt(x, y, z);
        return defaultBlockState().setValue(PART, Part.of(part == null ? RoomShell.Part.VOID : part)).setValue(HEART, RoomShell.isHeart(x, y, z));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HEART) ? new RoomVoidBlockEntity(pos, state) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    /**
     * The shell lets the sky's light through while staying solid, so every spot in the room has full light: seeds can
     * be planted and grow anywhere, and nothing hostile spawns in the dark.
     */
    @Override
    protected int getLightBlock(BlockState state) { return 0; }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) { return true; }
}
