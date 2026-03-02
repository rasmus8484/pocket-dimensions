package com.pocketdimensions.item;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.blockentity.WorldAnchorBlockEntity;
import com.pocketdimensions.init.ModBlocks;
import com.pocketdimensions.manager.RealmManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;

/**
 * World Seed - creates or rekeys a realm binding when used on a WorldAnchor.
 * <p>
 * Rules:
 * - Cannot be used on an anchor that is already linked to any realm.
 * - Cannot be used if the player already has a living anchor in the world.
 *   (The player must break their existing anchor first to rekey.)
 * - If the player has a realm but its anchor was destroyed, relinking is allowed.
 * - The item is consumed on successful use (instabuild-aware).
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

        // Cannot use from inside the realm
        if (level.dimension().equals(PocketDimensionsMod.REALM_DIM)) return InteractionResult.PASS;

        BlockState clicked = level.getBlockState(ctx.getClickedPos());
        if (!clicked.is(ModBlocks.WORLD_ANCHOR.get())) return InteractionResult.PASS;

        // Return SUCCESS on client so the arm swings, but don't execute any logic
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        // --- Server side only below ---

        BlockPos anchorPos = ctx.getClickedPos();

        // Check 1: is this anchor already linked to someone's realm?
        if (level.getBlockEntity(anchorPos) instanceof WorldAnchorBlockEntity anchorBe
                && anchorBe.isLinked()) {
            player.displayClientMessage(Component.literal(
                    "The anchor hums with an existing bond. It cannot accept another."), false);
            return InteractionResult.FAIL;
        }

        MinecraftServer server = level.getServer();
        if (server == null) return InteractionResult.FAIL;

        RealmManager mgr = RealmManager.get(server);
        UUID playerUUID = player.getUUID();

        if (mgr.realmExistsFor(playerUUID)) {
            // Check 2: does the player's existing anchor still exist in the world?
            var anchorOpt = mgr.getAnchorLocation(playerUUID);
            if (anchorOpt.isPresent()) {
                Map.Entry<ResourceKey<Level>, BlockPos> existing = anchorOpt.get();
                ServerLevel existingDim = server.getLevel(existing.getKey());
                if (existingDim != null
                        && existingDim.getBlockState(existing.getValue()).is(ModBlocks.WORLD_ANCHOR.get())) {
                    player.displayClientMessage(Component.literal(
                            "Your realm is still tethered to another anchor. Sever it first."), false);
                    return InteractionResult.FAIL;
                }
            }
            // Old anchor was destroyed - clear its location and relink below
            mgr.clearAnchorLocation(playerUUID);
        } else {
            // First-time use: allocate a new realm for this player
            mgr.allocateRealm(playerUUID);
            mgr.setCreatedGameTime(playerUUID, level.getServer().overworld().getGameTime());
        }

        // Link the new anchor
        if (!(level.getBlockEntity(anchorPos) instanceof WorldAnchorBlockEntity be)) {
            return InteractionResult.FAIL;
        }

        // Immediate feedback — the heavy generation work comes next
        player.displayClientMessage(Component.literal(
                "The seed crumbles into the anchor... condensing dimensional tunnel..."), false);

        be.setOwnerUUID(playerUUID);
        be.setLinked(true);
        mgr.setAnchorLocation(playerUUID, level.dimension(), anchorPos);

        // Generate the realm now if the dimension is already loaded
        ServerLevel realmLevel = server.getLevel(PocketDimensionsMod.REALM_DIM);
        if (realmLevel != null) {
            mgr.ensureGenerated(playerUUID, realmLevel);
        }

        // Consume item (skip in creative)
        if (!player.getAbilities().instabuild) {
            ctx.getItemInHand().shrink(1);
        }

        player.displayClientMessage(Component.literal(
                "...the bridge stabilizes. Right-click the anchor to cross over."), false);
        return InteractionResult.SUCCESS;
    }
}
