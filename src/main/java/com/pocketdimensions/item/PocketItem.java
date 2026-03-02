package com.pocketdimensions.item;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.block.PocketAnchorBlock;
import com.pocketdimensions.blockentity.PocketAnchorBlockEntity;
import com.pocketdimensions.init.ModBlocks;
import com.pocketdimensions.manager.PocketRoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Pocket Item - the key/token for a pocket room.
 * <p>
 * Carries {@code pocket_id} (UUID as int array) in item CustomData. The UUID is never used to
 * derive coordinates; only the server-side PocketRoomManager mapping is authoritative.
 * <p>
 * Behaviours:
 * - Crouch + right-click on block face  -> pre-place a Pocket Anchor at that face
 * - Right-click on block face (no shift) -> enter room (allocate if needed, place anchor at feet)
 * - Right-click in air (use())           -> same as right-click block without shift
 */
public class PocketItem extends Item {

    public PocketItem(Properties properties) {
        super(properties);
    }

    // -------------------------------------------------------------------------
    // useOn - right-click on a block face
    // -------------------------------------------------------------------------

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.dimension().equals(PocketDimensionsMod.POCKET_DIM)) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            // Crouch+right-click: pre-place anchor at the clicked face
            return placeAnchor(ctx.getItemInHand(), player, level,
                    ctx.getClickedPos().relative(ctx.getClickedFace()), ctx.getHand());
        } else {
            // Normal right-click on block: enter/allocate room
            if (!level.isClientSide()) {
                return enterOrAllocate(ctx.getItemInHand(), player, level, ctx.getHand());
            }
            return InteractionResult.SUCCESS;
        }
    }

    // -------------------------------------------------------------------------
    // use - right-click in air (no block target)
    // -------------------------------------------------------------------------

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.dimension().equals(PocketDimensionsMod.POCKET_DIM)) return InteractionResult.PASS;
        return enterOrAllocate(player.getItemInHand(hand), player, level, hand);
    }

    // -------------------------------------------------------------------------
    // Logic helpers
    // -------------------------------------------------------------------------

    /**
     * Place an anchor at {@code placePos} and consume the item.
     */
    private InteractionResult placeAnchor(ItemStack stack, Player player, Level level,
                                          BlockPos placePos, InteractionHand hand) {
        if (!level.isClientSide()) {
            if (!level.getBlockState(placePos).canBeReplaced()) {
                player.displayClientMessage(
                        Component.literal("The ground here is too solid. The anchor cannot take root."), false);
                return InteractionResult.FAIL;
            }

            UUID pocketId = getPocketId(stack);
            if (pocketId == null) {
                player.displayClientMessage(
                        Component.literal("This token is blank - no pocket has been folded into it yet."), false);
                return InteractionResult.FAIL;
            }

            level.setBlock(placePos, ModBlocks.POCKET_ANCHOR.get().defaultBlockState(), 3);
            if (level.getBlockEntity(placePos) instanceof PocketAnchorBlockEntity be) {
                be.setPocketId(pocketId);
                be.setOwnerUUID(player.getUUID());
            }

            PocketRoomManager mgr = PocketRoomManager.get(((net.minecraft.server.level.ServerLevel) level).getServer());
            mgr.setAnchorLocation(pocketId, level.dimension(), placePos);

            consumeItem(stack, player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Allocate a room if needed, place anchor at player's feet, then teleport in.
     */
    private InteractionResult enterOrAllocate(ItemStack stack, Player player, Level level, InteractionHand hand) {
        // Never enter from inside the pocket dimension - prevents entry location being overwritten
        // with a pocket-dim coordinate, which would make exit loop back into the room.
        if (level.dimension().equals(PocketDimensionsMod.POCKET_DIM)) return InteractionResult.PASS;

        MinecraftServer server = ((net.minecraft.server.level.ServerLevel) level).getServer();
        if (server == null) return InteractionResult.FAIL;

        PocketRoomManager mgr = PocketRoomManager.get(server);

        // Allocate room lazily, or re-allocate if the previously linked room was destroyed
        UUID pocketId = getPocketId(stack);
        if (pocketId == null || !mgr.roomExists(pocketId)) {
            pocketId = mgr.allocateRoom(player.getUUID());
            setPocketId(stack, pocketId);
        }

        // Place anchor at feet if no anchor is currently registered
        if (mgr.getAnchorLocation(pocketId).isEmpty()) {
            BlockPos feet = player.blockPosition();
            BlockPos placePos = findReplaceable(level, feet);
            if (placePos != null) {
                level.setBlock(placePos, ModBlocks.POCKET_ANCHOR.get().defaultBlockState(), 3);
                if (level.getBlockEntity(placePos) instanceof PocketAnchorBlockEntity be) {
                    be.setPocketId(pocketId);
                    be.setOwnerUUID(player.getUUID());
                }
                mgr.setAnchorLocation(pocketId, level.dimension(), placePos);
            }
        }

        // Consume item and teleport player in via block entity logic
        consumeItem(stack, player, hand);

        ServerLevel pocketLevel = server.getLevel(PocketDimensionsMod.POCKET_DIM);
        if (pocketLevel == null) {
            player.displayClientMessage(
                    Component.literal("The fold between worlds has collapsed. The pocket cannot be reached."), false);
            return InteractionResult.FAIL;
        }

        final UUID finalPocketId = pocketId;
        mgr.ensureGenerated(finalPocketId, pocketLevel);
        mgr.setEntryLocation(player.getUUID(), level.dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        mgr.addOccupant(finalPocketId, player.getUUID());

        BlockPos spawn = mgr.getSpawnPos(finalPocketId);
        Vec3 dest = new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        ((ServerPlayer) player).teleport(new TeleportTransition(pocketLevel, dest, Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING));

        return InteractionResult.SUCCESS;
    }

    /** Removes one item from the stack, working in both survival and creative mode. */
    private static void consumeItem(ItemStack stack, Player player, InteractionHand hand) {
        if (player.getAbilities().instabuild) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        } else {
            stack.shrink(1);
        }
    }

    private static BlockPos findReplaceable(Level level, BlockPos start) {
        if (level.getBlockState(start).canBeReplaced()) return start;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos c = start.offset(dx, 0, dz);
                if (level.getBlockState(c).canBeReplaced()) return c;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public static UUID getPocketId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        if (!tag.contains("pocket_id_msb")) return null;
        long msb = tag.getLongOr("pocket_id_msb", 0L);
        long lsb = tag.getLongOr("pocket_id_lsb", 0L);
        return (msb != 0 || lsb != 0) ? new UUID(msb, lsb) : null;
    }

    public static void setPocketId(ItemStack stack, UUID id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putLong("pocket_id_msb", id.getMostSignificantBits());
            tag.putLong("pocket_id_lsb", id.getLeastSignificantBits());
        });
    }
}
