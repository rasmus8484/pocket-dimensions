package com.pocketdimensions.item;

import com.pocketdimensions.blockentity.WorldAnchorBlockEntity;
import com.pocketdimensions.init.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * World Seed — creates or rekeys a realm binding when used on a WorldAnchor.
 * <p>
 * Behaviour:
 * - If the player has no realm yet: allocate a new realm region, generate it, bind this anchor.
 * - If the player already has a realm: rekey (rebind) the existing realm to this new anchor;
 *   the old anchor becomes invalid.
 * - The item is consumed on successful use.
 */
public class WorldSeedItem extends Item {

    public WorldSeedItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockState clicked = level.getBlockState(ctx.getClickedPos());
        if (!clicked.is(ModBlocks.WORLD_ANCHOR.get())) {
            return InteractionResult.PASS; // Only valid on WorldAnchor
        }

        if (!level.isClientSide()) {
            WorldAnchorBlockEntity be = (WorldAnchorBlockEntity)
                    level.getBlockEntity(ctx.getClickedPos());
            if (be == null) return InteractionResult.FAIL;

            be.linkToPlayer(player.getUUID(), level);
            ctx.getItemInHand().shrink(1);
            player.sendSystemMessage(Component.literal(
                    "[PocketDimensions] Realm bound to this anchor. (stub — allocation TODO)"));
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
