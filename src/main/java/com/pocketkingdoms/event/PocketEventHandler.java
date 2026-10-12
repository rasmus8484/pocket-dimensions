package com.pocketkingdoms.event;

import com.pocketkingdoms.PocketKingdomsMod;
import com.pocketkingdoms.init.ModBlocks;
import com.pocketkingdoms.init.ModItems;
import com.pocketkingdoms.item.PocketAnchorItem;
import com.pocketkingdoms.manager.PocketRoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles server-side Forge events for pocket room mechanics.
 *
 * Exit triggers (any one is sufficient):
 *   1. Hold crouch for 2 seconds (40 ticks) while in the pocket dimension
 *   2. Right-click a BoundaryBlock (handled in BoundaryBlock.useWithoutItem)
 *
 * Safety net: players in the pocket dimension with no occupant record are ejected
 * after 5 seconds - catches anyone who got stuck via bugs or commands.
 */
public class PocketEventHandler {

    /**
     * Exit cooldown - prevents double-trigger on the same jump. Counted on the world's clock, never the player's own
     * tickCount: respawning (death, or leaving the End) makes a new player whose tickCount starts again at 0, and an
     * expiry stamped on the old count would lock the way out for as long as they had been online.
     */
    private static final Map<UUID, Long> exitCooldownExpiry = new HashMap<>();

    /** Riders who clicked a wall to leave (BoundaryBlock); they go on their next tick, never mid-click. */
    private static final java.util.Set<UUID> pendingWallExits = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void queueWallExit(UUID player) { pendingWallExits.add(player); }

    public PocketEventHandler() {
        TickEvent.PlayerTickEvent.Post.BUS.addListener(this::onPlayerTick);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(this::onPlayerLogout);
        BlockEvent.BreakEvent.BUS.addListener((BlockEvent.BreakEvent event) -> onBlockBreak(event));
        EntityTeleportEvent.ChorusFruit.BUS.addListener(this::onChorusFruitTeleport);
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(this::onLeftPocket);
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(this::onRespawn);
    }

    /** Out of the pocket dimension by any way at all (/tp included): no longer inside any room. */
    private void onLeftPocket(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !event.getFrom().equals(PocketKingdomsMod.POCKET_DIM)) return;
        PocketRoomManager.get(((ServerLevel) sp.level()).getServer()).removeOccupantEverywhere(sp.getUUID());
    }

    /** Died in a room and woke up outside the pocket dimension: no longer inside it. */
    private void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.level().dimension().equals(PocketKingdomsMod.POCKET_DIM)) return;
        PocketRoomManager.get(((ServerLevel) sp.level()).getServer()).removeOccupantEverywhere(sp.getUUID());
    }

    /** Cancels chorus fruit teleportation inside pocket rooms. */
    private boolean onChorusFruitTeleport(EntityTeleportEvent.ChorusFruit event) {
        if (event.getEntityLiving().level().dimension().equals(PocketKingdomsMod.POCKET_DIM)) {
            if (event.getEntityLiving() instanceof net.minecraft.world.entity.player.Player p) {
                p.displayClientMessage(
                        Component.literal("The fruit's pull fizzles against the pocket walls."), true);
            }
            return true; // cancel
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // PlayerTickEvent.Post
    // -------------------------------------------------------------------------

    private void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (event.side() != LogicalSide.SERVER) return;
        Player player = event.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        UUID uuid = serverPlayer.getUUID();

        // -- Only act for players inside the pocket dimension --
        if (!serverPlayer.level().dimension().equals(PocketKingdomsMod.POCKET_DIM)) {
            return;
        }

        MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
        if (server == null) return;

        PocketRoomManager mgr = PocketRoomManager.get(server);
        UUID pocketId = mgr.findRoomForOccupant(uuid);
        long now = server.overworld().getGameTime();

        if (pocketId == null) {
            if (serverPlayer.isSpectator()) return;              // spectators fly free
            // Safety net: player is in the pocket dimension with no occupant record.
            // This happens if they respawned here (e.g. /spawnpoint set inside a room
            // that was later destroyed). Eject immediately to prevent a void death loop.
            mgr.teleportToEntryOrSpawn(serverPlayer, server, com.pocketkingdoms.manager.TravelRules.Journey.FORCED);
            return;
        }

        // -- Got past the walls somehow (a broken wall, a glitch, another mod): drawn back inside. Spectators fly free --
        if (!serverPlayer.isSpectator() && mgr.roomExists(pocketId) && !mgr.holds(pocketId, serverPlayer.blockPosition())) {
            ServerLevel pocketLevel = (ServerLevel) serverPlayer.level();
            BlockPos back = mgr.findSafeSpawn(pocketId, pocketLevel);
            serverPlayer.stopRiding();
            serverPlayer.connection.teleport(back.getX() + 0.5, back.getY(), back.getZ() + 0.5,
                    serverPlayer.getYRot(), serverPlayer.getXRot());
            serverPlayer.resetFallDistance();
            serverPlayer.displayClientMessage(
                    Component.literal("The fold closes around you, and the room draws you back in."), true);
            return;
        }

        // -- A rider clicked a wall: out, mount and all --
        if (pendingWallExits.remove(uuid)) {
            exitCooldownExpiry.put(uuid, now + 40);
            performExit(serverPlayer, pocketId, mgr, server);
            return;
        }

        // -- Crouch + jump exit trigger --
        Long expiry = exitCooldownExpiry.get(uuid);
        if (expiry != null && now < expiry) return; // cooldown active

        if (serverPlayer.isShiftKeyDown() && serverPlayer.getDeltaMovement().y > 0.1) {
            exitCooldownExpiry.put(uuid, now + 40);
            performExit(serverPlayer, pocketId, mgr, server);
        }
    }

    // -------------------------------------------------------------------------
    // Exit logic - called from the crouch+jump tick check
    // -------------------------------------------------------------------------

    public static void performExit(ServerPlayer player, UUID pocketId,
                                   PocketRoomManager mgr, MinecraftServer server) {
        mgr.removeOccupant(pocketId, player.getUUID());

        // 1. Try the anchor's exact location
        var anchorOpt = mgr.getAnchorLocation(pocketId);
        if (anchorOpt.isPresent()) {
            Map.Entry<ResourceKey<Level>, BlockPos> anchor = anchorOpt.get();
            ServerLevel targetLevel = server.getLevel(anchor.getKey());
            if (targetLevel != null) {
                BlockPos anchorPos = anchor.getValue();
                if (targetLevel.getBlockState(anchorPos).is(ModBlocks.POCKET_ANCHOR.get())) {
                    Companions.travel(player, targetLevel,
                            new Vec3(anchorPos.getX() + 0.5, anchorPos.getY() + 1, anchorPos.getZ() + 0.5),
                            player.getYRot(), player.getXRot(), com.pocketkingdoms.manager.TravelRules.Journey.CHOSEN);
                    return;
                }
            }
        }

        // 2. The anchor was picked up (which clears its location): come out next to whoever carries it, in their world
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online == player) continue;
            if (!holdsAnchorItem(online, pocketId)) continue;
            ServerLevel holderLevel = (ServerLevel) online.level();
            if (holderLevel.dimension().equals(PocketKingdomsMod.POCKET_DIM)) continue;
            BlockPos safeSpot = findSafeSpotNear(online, holderLevel);
            Companions.travel(player, holderLevel,
                    new Vec3(safeSpot.getX() + 0.5, safeSpot.getY(), safeSpot.getZ() + 0.5),
                    player.getYRot(), player.getXRot(), com.pocketkingdoms.manager.TravelRules.Journey.CHOSEN);
            return;
        }

        // 3. Fallback: entry location or overworld spawn
        mgr.teleportToEntryOrSpawn(player, server, com.pocketkingdoms.manager.TravelRules.Journey.CHOSEN);
    }

    // -------------------------------------------------------------------------
    // PlayerLoggedOutEvent - anchor auto-placement
    // -------------------------------------------------------------------------

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
        if (server == null) return;

        // Clean up per-player state
        UUID uuid = serverPlayer.getUUID();
        exitCooldownExpiry.remove(uuid);

        PocketRoomManager mgr = PocketRoomManager.get(server);

        // Scan all inventory slots for a live pocket item whose room has occupants
        var inventory = serverPlayer.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ModItems.POCKET_ANCHOR_ITEM.get())) continue;

            UUID pocketId = PocketAnchorItem.getPocketId(stack);
            if (pocketId == null) continue;
            if (mgr.getOccupants(pocketId).isEmpty()) continue;

            BlockPos feet = serverPlayer.blockPosition();
            Level level = serverPlayer.level();
            BlockPos placePos = findReplaceable(level, feet);
            if (placePos == null) continue;

            level.setBlock(placePos, ModBlocks.POCKET_ANCHOR.get().defaultBlockState(), 3);
            if (level.getBlockEntity(placePos) instanceof com.pocketkingdoms.blockentity.PocketAnchorBlockEntity be) {
                be.setPocketId(pocketId);
                be.setOwnerUUID(mgr.anchorOwner(pocketId, serverPlayer.getUUID()));
                be.setCustomName(stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
            }
            mgr.setAnchorLocation(pocketId, level.dimension(), placePos);
            stack.shrink(1);
            break;
        }
    }

    // -------------------------------------------------------------------------
    // BlockEvent.BreakEvent - a mined anchor takes its room with it
    // -------------------------------------------------------------------------

    private void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!event.getState().is(ModBlocks.POCKET_ANCHOR.get())) return;

        Player player = event.getPlayer();
        MinecraftServer server = serverLevel.getServer();
        PocketRoomManager mgr = PocketRoomManager.get(server);

        if (serverLevel.getBlockEntity(event.getPos()) instanceof
                com.pocketkingdoms.blockentity.PocketAnchorBlockEntity be) {
            UUID pocketId = be.getPocketId();
            if (pocketId == null) return;

            // (The miner was told at the first hit if anyone is inside: AnchorMiningHandler.)
            if (!mgr.getOccupants(pocketId).isEmpty())
                com.pocketkingdoms.advancement.Milestones.reach(player, com.pocketkingdoms.advancement.Milestones.UNMADE);
            // Destroy room here so it works in both survival and creative.
            // (In creative, Forge bypasses onDestroyedByPlayer and calls removeBlock directly.)
            AnchorMiningHandler.destroyRoom(server, serverLevel, event.getPos(), pocketId);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean holdsAnchorItem(ServerPlayer player, UUID pocketId) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(ModItems.POCKET_ANCHOR_ITEM.get()) && pocketId.equals(PocketAnchorItem.getPocketId(stack)))
                return true;
        }
        return false;
    }

    private static BlockPos findSafeSpotNear(ServerPlayer player, Level level) {
        BlockPos base = player.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos candidate = base.offset(dx, 0, dz);
                if (level.getBlockState(candidate).isAir()
                        && level.getBlockState(candidate.above()).isAir()) {
                    return candidate;
                }
            }
        }
        return base;
    }

    private static BlockPos findReplaceable(Level level, BlockPos start) {
        if (PocketAnchorItem.canRest(level, start)) return start;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos candidate = start.offset(dx, 0, dz);
                if (PocketAnchorItem.canRest(level, candidate)) return candidate;
            }
        }
        return null;
    }
}
