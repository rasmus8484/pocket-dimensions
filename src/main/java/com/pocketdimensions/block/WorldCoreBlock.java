package com.pocketdimensions.block;

import com.mojang.serialization.MapCodec;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.init.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

public class WorldCoreBlock extends BaseEntityBlock {

    public static final MapCodec<WorldCoreBlock> CODEC = simpleCodec(WorldCoreBlock::new);

    public WorldCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorldCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntityTypes.WORLD_CORE.get(),
                WorldCoreBlockEntity::serverTick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
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
        WorldCoreBlockEntity be = (WorldCoreBlockEntity) level.getBlockEntity(pos);
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
        WorldCoreBlockEntity be = (WorldCoreBlockEntity) level.getBlockEntity(pos);
        if (be == null) return InteractionResult.FAIL;

        if (player.isShiftKeyDown()) {
            return openGui(player, be);
        }

        be.exitRealm(player, level);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult openGui(Player player, WorldCoreBlockEntity be) {
        if (be.getOwnerUUID() == null || !be.getOwnerUUID().equals(player.getUUID())) {
            player.displayClientMessage(Component.literal(
                    "The core's inner workings remain sealed to all but its master."), false);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(be, be.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }
}
