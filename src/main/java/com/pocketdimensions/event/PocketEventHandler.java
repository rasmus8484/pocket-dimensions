package com.pocketdimensions.event;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.init.ModBlocks;
import com.pocketdimensions.init.ModItems;
import com.pocketdimensions.item.PocketItem;
import com.pocketdimensions.manager.PocketRoomManager;
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
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles server-side Forge events for pocket room mechanics.
 *
 * Exit triggers (any one is sufficient):
 *   1. Hold crouch for 2 seconds (40 ticks) while in the pocket dimension
 *   2. Right-click a BoundaryBlock (handled in BoundaryBlock.useWithoutItem)
 *
 * Safety net: players in the pocket dimension with no occupant record are ejected
 * after 5 seconds — catches anyone who got stuck via bugs or commands.
 */
public class PocketEventHandler {

    /** server tickCount when the player started continuously crouching. */
    private static final Map<UUID, Integer> crouchStartTick = new HashMap<>();

    /** Exit cooldown — prevents double-trigger. */
    private static final Map<UUID, Integer> exitCooldownExpiry = new HashMap<>();

    private static final int CROUCH_HOLD_TICKS = 20; // 1 second

    public PocketEventHandler() {
        TickEvent.PlayerTickEvent.Post.BUS.addListener(this::onPlayerTick);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(this::onPlayerLogout);
        BlockEvent.BreakEvent.BUS.addListener((BlockEvent.BreakEvent event) -> onBlockBreak(event));
    }

    // -------------------------------------------------------------------------
    // PlayerTickEvent.Post
    // -------------------------------------------------------------------------

    private void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (event.side() != LogicalSide.SERVER) return;
        Player player = event.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        UUID uuid = serverPlayer.getUUID();

        // ── Only act for players inside the pocket dimension ──
        if (!serverPlayer.level().dimension().equals(PocketDimensionsMod.POCKET_DIM)) {
            crouchStartTick.remove(uuid);
            return;
        }

        MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
        if (server == null) return;

        PocketRoomManager mgr = PocketRoomManager.get(server);
        UUID pocketId = mgr.findRoomForOccupant(uuid);
        int now = serverPlayer.tickCount;

        if (pocketId == null) return; // not tracked as an occupant — do nothing

        // ── Crouch-hold exit trigger ──
        Integer expiry = exitCooldownExpiry.get(uuid);
        if (expiry != null && now < expiry) {
            // On cooldown — still track crouch so it doesn't feel laggy after cooldown expires
            if (!serverPlayer.isShiftKeyDown()) crouchStartTick.remove(uuid);
            return;
        }

        if (serverPlayer.isShiftKeyDown()) {
            int since = crouchStartTick.computeIfAbsent(uuid, k -> now);
            int heldFor = now - since;

            // Show hold progress every 8 ticks (0.5s) as action bar hint
            if (heldFor > 0 && heldFor % 8 == 0 && heldFor < CROUCH_HOLD_TICKS) {
                int pct = (heldFor * 100) / CROUCH_HOLD_TICKS;
                serverPlayer.displayClientMessage(
                        Component.literal("[PocketDimensions] Hold sneak to exit... " + pct + "%"), true);
            }

            if (heldFor >= CROUCH_HOLD_TICKS) {
                crouchStartTick.remove(uuid);
                exitCooldownExpiry.put(uuid, now + 40);
                performExit(serverPlayer, pocketId, mgr, server);
            }
        } else {
            crouchStartTick.remove(uuid); // reset if they let go
        }
    }

    // -------------------------------------------------------------------------
    // Exit logic — called from tick event AND BoundaryBlock right-click
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
                    player.teleport(new TeleportTransition(targetLevel,
                            new Vec3(anchorPos.getX() + 0.5, anchorPos.getY() + 1, anchorPos.getZ() + 0.5),
                            Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
                    return;
                }

                // Anchor was stolen — find who holds the pocket item and teleport near them
                for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                    if (online == player) continue;
                    if (!holdsPocketItem(online, pocketId)) continue;
                    BlockPos safeSpot = findSafeSpotNear(online, targetLevel);
                    player.teleport(new TeleportTransition(targetLevel,
                            new Vec3(safeSpot.getX() + 0.5, safeSpot.getY(), safeSpot.getZ() + 0.5),
                            Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
                    return;
                }
            }
        }

        // 2. Fallback: entry location or overworld spawn
        mgr.teleportToEntryOrSpawn(player, server);
    }

    // -------------------------------------------------------------------------
    // PlayerLoggedOutEvent — anchor auto-placement
    // -------------------------------------------------------------------------

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
        if (server == null) return;

        // Clean up per-player state
        UUID uuid = serverPlayer.getUUID();
        crouchStartTick.remove(uuid);
        exitCooldownExpiry.remove(uuid);

        PocketRoomManager mgr = PocketRoomManager.get(server);

        // Scan all inventory slots for a live pocket item whose room has occupants
        var inventory = serverPlayer.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ModItems.POCKET_ITEM.get())) continue;

            UUID pocketId = PocketItem.getPocketId(stack);
            if (pocketId == null) continue;
            if (mgr.getOccupants(pocketId).isEmpty()) continue;

            BlockPos feet = serverPlayer.blockPosition();
            Level level = serverPlayer.level();
            BlockPos placePos = findReplaceable(level, feet);
            if (placePos == null) continue;

            level.setBlock(placePos, ModBlocks.POCKET_ANCHOR.get().defaultBlockState(), 3);
            if (level.getBlockEntity(placePos) instanceof com.pocketdimensions.blockentity.PocketAnchorBlockEntity be) {
                be.setPocketId(pocketId);
                be.setOwnerUUID(serverPlayer.getUUID());
            }
            mgr.setAnchorLocation(pocketId, level.dimension(), placePos);
            stack.shrink(1);
            break;
        }
    }

    // -------------------------------------------------------------------------
    // BlockEvent.BreakEvent — anchor break warning
    // -------------------------------------------------------------------------

    private void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!event.getState().is(ModBlocks.POCKET_ANCHOR.get())) return;

        Player player = event.getPlayer();
        MinecraftServer server = serverLevel.getServer();
        PocketRoomManager mgr = PocketRoomManager.get(server);

        if (serverLevel.getBlockEntity(event.getPos()) instanceof
                com.pocketdimensions.blockentity.PocketAnchorBlockEntity be) {
            UUID pocketId = be.getPocketId();
            if (pocketId == null) return;

            if (!mgr.getOccupants(pocketId).isEmpty()) {
                player.displayClientMessage(
                        Component.literal("[PocketDimensions] Warning: players are inside this room!"),
                        true);
            }

            // Destroy room here so it works in both survival and creative.
            // (In creative, Forge bypasses onDestroyedByPlayer and calls removeBlock directly.)
            mgr.destroyRoom(pocketId, server);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean holdsPocketItem(ServerPlayer player, UUID pocketId) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(ModItems.POCKET_ITEM.get()) && pocketId.equals(PocketItem.getPocketId(stack)))
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
        if (level.getBlockState(start).canBeReplaced()) return start;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos candidate = start.offset(dx, 0, dz);
                if (level.getBlockState(candidate).canBeReplaced()) return candidate;
            }
        }
        return null;
    }
}
