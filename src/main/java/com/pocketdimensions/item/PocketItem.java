package com.pocketdimensions.item;

import com.pocketdimensions.block.PocketAnchorBlock;
import com.pocketdimensions.blockentity.PocketAnchorBlockEntity;
import com.pocketdimensions.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Pocket Item — the key/token for a pocket room.
 * <p>
 * Carries {@code pocket_id} (UUID) in item NBT. The UUID is never used to derive
 * coordinates; only the server-side PocketRoomManager mapping is authoritative.
 * <p>
 * Behaviours:
 * - Right-click on block face (crouch)  → pre-place a Pocket Anchor at that face
 * - Right-click in air                  → enter the room directly (TODO Phase 2)
 * - Picked up from a placed anchor      → crouch + right-click the anchor
 */
public class PocketItem extends Item {

    public PocketItem(Properties properties) {
        super(properties);
    }

    /**
     * Crouch + right-click on a block face: place a Pocket Anchor at that position.
     * The player stays outside; others can immediately use the anchor.
     */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        // Only place when crouching (pre-placement mechanic)
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        BlockPos placePos = ctx.getClickedPos().relative(ctx.getClickedFace());

        if (!level.isClientSide()) {
            if (!level.getBlockState(placePos).canBeReplaced()) {
                player.sendSystemMessage(Component.literal("[PocketDimensions] Cannot place anchor here."));
                return InteractionResult.FAIL;
            }

            UUID pocketId = getPocketId(ctx.getItemInHand());
            if (pocketId == null) {
                player.sendSystemMessage(Component.literal("[PocketDimensions] This Pocket Item has no room linked yet."));
                return InteractionResult.FAIL;
            }

            level.setBlock(placePos, ModBlocks.POCKET_ANCHOR.get().defaultBlockState(), 3);
            if (level.getBlockEntity(placePos) instanceof PocketAnchorBlockEntity be) {
                be.setPocketId(pocketId);
                be.setOwnerUUID(player.getUUID());
            }
            ctx.getItemInHand().shrink(1);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public static UUID getPocketId(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().hasUUID("pocket_id")) {
            return stack.getTag().getUUID("pocket_id");
        }
        return null;
    }

    public static void setPocketId(ItemStack stack, UUID id) {
        stack.getOrCreateTag().putUUID("pocket_id", id);
    }
}
