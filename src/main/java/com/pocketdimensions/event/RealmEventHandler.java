package com.pocketdimensions.event;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.init.ModBlocks;
import com.pocketdimensions.init.ModItems;
import com.pocketdimensions.manager.RealmManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.LogicalSide;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles server-side Forge events for the Realm system:
 *   - Border enforcement: teleport players back to realm center if they stray outside
 *   - Portal blocking: cancel any dimension travel originating from inside the realm
 *   - Mob spawn blocking: suppress natural Monster spawns in the realm dimension
 *
 * Realm entry is queued here (pendingRealmEntries) so the actual teleport fires from
 * PlayerTickEvent, not from inside a block interaction handler. This avoids the
 * packet-ordering issue where ClientboundRespawnPacket is sent mid-interaction.
 */
public class RealmEventHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Players waiting to enter the realm on the next tick.
     * Key = playerUUID, Value = realm owner UUID.
     * Populated by WorldAnchorBlockEntity; consumed here in onPlayerTick.
     */
    private static final Map<UUID, UUID> pendingRealmEntries = new ConcurrentHashMap<>();

    /**
     * Players waiting to exit the realm on the next tick.
     * Populated by WorldCoreBlockEntity; consumed here in onPlayerTick.
     * Also used by onEntityTravelToDimension to allow the queued exit through.
     */
    private static final Set<UUID> pendingRealmExits = ConcurrentHashMap.newKeySet();

    public RealmEventHandler() {
        TickEvent.PlayerTickEvent.Post.BUS.addListener(this::onPlayerTick);
        // Predicate overload required for record-based cancellable events
        EntityTravelToDimensionEvent.BUS.addListener(this::onEntityTravelToDimension);
        MobSpawnEvent.FinalizeSpawn.BUS.addListener(this::onFinalizeSpawn);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(this::onRightClickWorldAnchor);
    }

    /**
     * Called by WorldAnchorBlockEntity.tryEnterRealm to queue a realm entry.
     * The teleport itself fires from the next PlayerTickEvent to avoid mid-interaction packet issues.
     */
    public static void queueRealmEntry(UUID playerUUID, UUID ownerUUID) {
        LOGGER.info("[WorldAnchor] Queuing realm entry for player={} owner={}", playerUUID, ownerUUID);
        pendingRealmEntries.put(playerUUID, ownerUUID);
    }

    /**
     * Called by WorldCoreBlockEntity.exitRealm to queue a realm exit.
     * The teleport fires from the next PlayerTickEvent.
     * While pending, onEntityTravelToDimension will allow this player's travel.
     */
    public static void queueRealmExit(UUID playerUUID) {
        LOGGER.info("[WorldCore] Queuing realm exit for player={}", playerUUID);
        pendingRealmExits.add(playerUUID);
    }

    // -------------------------------------------------------------------------
    // PlayerTickEvent — realm entry + border enforcement (every 5 ticks)
    // -------------------------------------------------------------------------

    private void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (event.side() != LogicalSide.SERVER) return;
        Player player = event.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        UUID playerUUID = serverPlayer.getUUID();

        // ── Process any pending realm entry (queued from block interaction) ──
        UUID pendingOwner = pendingRealmEntries.remove(playerUUID);
        if (pendingOwner != null) {
            MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
            if (server != null) {
                ServerLevel realmLevel = server.getLevel(PocketDimensionsMod.REALM_DIM);
                if (realmLevel != null) {
                    BlockPos spawnPos = RealmManager.get(server).getSpawnPos(pendingOwner);
                    Vec3 dest = new Vec3(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                    LOGGER.info("[WorldAnchor] Executing queued realm entry for {} → {}", serverPlayer.getName().getString(), spawnPos);
                    serverPlayer.teleport(new TeleportTransition(realmLevel, dest, Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING));
                } else {
                    LOGGER.warn("[WorldAnchor] Realm level not found, discarding pending entry for {}", playerUUID);
                }
            }
            return;
        }

        // ── Process any pending realm exit (queued from WorldCore interaction) ──
        // Use contains(), NOT remove() here — the entry is consumed by onEntityTravelToDimension
        // when the teleport fires synchronously inside teleportToEntryOrSpawn().
        if (pendingRealmExits.contains(playerUUID)) {
            MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
            if (server != null) {
                LOGGER.info("[WorldCore] Executing queued realm exit for {}", serverPlayer.getName().getString());
                RealmManager mgr = RealmManager.get(server);
                mgr.clearPlayerRealm(playerUUID);
                mgr.teleportToEntryOrSpawn(serverPlayer, server);
                // If teleport failed (e.g. target level null) the entry stays for retry next tick.
                // If teleport succeeded, onEntityTravelToDimension already removed the entry.
            }
            return;
        }

        // If not in the realm dimension, clear any stale playerRealm entry and bail
        if (!serverPlayer.level().dimension().equals(PocketDimensionsMod.REALM_DIM)) {
            MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
            if (server != null) {
                RealmManager.get(server).clearPlayerRealm(serverPlayer.getUUID());
            }
            return;
        }

        // Check every 5 ticks
        if (serverPlayer.tickCount % 5 != 0) return;

        MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
        if (server == null) return;

        RealmManager mgr = RealmManager.get(server);

        // Resolve which realm the player belongs to
        UUID realmOwner = mgr.getPlayerRealm(playerUUID);
        if (realmOwner == null) {
            // Auto-recover after server restart (transient map was wiped)
            LOGGER.info("[RealmBorder] playerRealm null for {}, scanning position ({},{})",
                    serverPlayer.getName().getString(), (int)serverPlayer.getX(), (int)serverPlayer.getZ());
            realmOwner = mgr.findRealmAtPosition(serverPlayer.getX(), serverPlayer.getZ());
            if (realmOwner == null) {
                // No realm found — eject to entry location or spawn
                LOGGER.info("[RealmBorder] No realm found at position, ejecting {}", serverPlayer.getName().getString());
                mgr.teleportToEntryOrSpawn(serverPlayer, server);
                return;
            }
            mgr.setPlayerRealm(playerUUID, realmOwner);
        }

        // Enforce bounds — teleport back to spawn pos if outside
        int[] bounds = mgr.getRealmBounds(realmOwner);
        boolean inside = mgr.isWithinRealm(realmOwner, serverPlayer.getX(), serverPlayer.getZ());
        LOGGER.info("[RealmBorder] {} pos=({},{}) bounds=[{},{},{},{}] inside={}",
                serverPlayer.getName().getString(),
                (int)serverPlayer.getX(), (int)serverPlayer.getZ(),
                bounds[0], bounds[1], bounds[2], bounds[3], inside);

        if (!inside) {
            BlockPos spawnPos = mgr.getSpawnPos(realmOwner);
            LOGGER.info("[RealmBorder] Snapping {} back to {}", serverPlayer.getName().getString(), spawnPos);
            serverPlayer.displayClientMessage(Component.literal(
                    "[Realm] Outside boundary — snapping back!"), true);
            Vec3 dest = new Vec3(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            serverPlayer.teleport(new TeleportTransition(
                    (ServerLevel) serverPlayer.level(), dest, Vec3.ZERO, 0f, 0f,
                    TeleportTransition.DO_NOTHING));
        }
    }

    // -------------------------------------------------------------------------
    // EntityTravelToDimensionEvent — block portals from inside the realm
    // Returning false from a Predicate listener cancels the event.
    // -------------------------------------------------------------------------

    private boolean onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof Player player)) return false; // allow — non-players
        if (event.getEntity().level().dimension().equals(PocketDimensionsMod.REALM_DIM)) {
            // Allow queued exits (WorldCore shift+right-click) — pendingRealmExits entry
            // is consumed here so the event fires only once per queued exit.
            if (pendingRealmExits.remove(player.getUUID())) return false; // allow queued exit
            return true; // cancel — block portals out of the realm
        }
        return false; // allow all other dimension travel
    }

    // -------------------------------------------------------------------------
    // PlayerInteractEvent.RightClickBlock — WorldAnchor entry works with any item held
    // -------------------------------------------------------------------------

    private void onRightClickWorldAnchor(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().getBlockState(event.getPos()).is(ModBlocks.WORLD_ANCHOR.get())) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;                         // empty hand already reaches useWithoutItem
        if (stack.is(ModItems.WORLD_SEED.get())) return;    // WorldSeed handles itself via useOn

        // For any other held item (block items, tools, etc.), prevent the item from acting
        // so the block's useWithoutItem fires through the normal DEFAULT chain.
        event.setUseItem(Result.DENY);
    }

    // -------------------------------------------------------------------------
    // MobSpawnEvent.FinalizeSpawn — no natural Monster spawns in realm
    // -------------------------------------------------------------------------

    private void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(PocketDimensionsMod.REALM_DIM)) return;
        if (event.getSpawnReason() != EntitySpawnReason.NATURAL) return;
        if (!(event.getEntity() instanceof Monster)) return;
        event.setSpawnCancelled(true);
    }
}
