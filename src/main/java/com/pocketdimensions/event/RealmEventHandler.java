package com.pocketdimensions.event;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.init.ModBlocks;
import com.pocketdimensions.init.ModItems;
import com.pocketdimensions.manager.RealmManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.fml.LogicalSide;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles server-side Forge events for the Realm system:
 *   - Border enforcement: clamp players to their realm bounds if they stray outside
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

    /**
     * Per-player transient runtime state for chunk-change / timer-based boundary checks.
     * Not persisted - rebuilt from PlayerRealmInfo on login or entry.
     */
    private static final Map<UUID, RuntimeRealmState> runtimeStates = new ConcurrentHashMap<>();

    private static class RuntimeRealmState {
        int lastChunkX, lastChunkZ;
        long lastCheckGameTime;

        RuntimeRealmState(int chunkX, int chunkZ, long gameTime) {
            this.lastChunkX = chunkX;
            this.lastChunkZ = chunkZ;
            this.lastCheckGameTime = gameTime;
        }
    }

    public RealmEventHandler() {
        TickEvent.PlayerTickEvent.Post.BUS.addListener(this::onPlayerTick);
        // Predicate overload required for record-based cancellable events
        EntityTravelToDimensionEvent.BUS.addListener(this::onEntityTravelToDimension);
        MobSpawnEvent.FinalizeSpawn.BUS.addListener(this::onFinalizeSpawn);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(this::onRightClickWorldAnchor);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(this::onPlayerLoggedIn);
        SleepFinishedTimeEvent.BUS.addListener(this::onRealmSleepFinished);
    }

    /**
     * Called by WorldAnchorBlockEntity.tryEnterRealm to queue a realm entry.
     * The teleport itself fires from the next PlayerTickEvent to avoid mid-interaction packet issues.
     */
    public static void queueRealmEntry(UUID playerUUID, UUID ownerUUID) {
        pendingRealmEntries.put(playerUUID, ownerUUID);
    }

    /**
     * Called by WorldCoreBlockEntity.exitRealm to queue a realm exit.
     * The teleport fires from the next PlayerTickEvent.
     * While pending, onEntityTravelToDimension will allow this player's travel.
     */
    public static void queueRealmExit(UUID playerUUID) {
        pendingRealmExits.add(playerUUID);
    }

    // -------------------------------------------------------------------------
    // PlayerLoggedInEvent - restore runtime state or eject on login
    // -------------------------------------------------------------------------

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        if (!serverPlayer.level().dimension().equals(PocketDimensionsMod.REALM_DIM)) return;

        MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
        if (server == null) return;

        UUID uuid = serverPlayer.getUUID();
        RealmManager mgr = RealmManager.get(server);
        RealmManager.PlayerRealmInfo info = mgr.getPlayerRealmInfo(uuid);

        if (info == null) {
            mgr.teleportToEntryOrSpawn(serverPlayer, server);
            return;
        }

        int cx = ((int) Math.floor(serverPlayer.getX())) >> 4;
        int cz = ((int) Math.floor(serverPlayer.getZ())) >> 4;
        runtimeStates.put(uuid, new RuntimeRealmState(cx, cz, serverPlayer.level().getGameTime()));
    }

    // -------------------------------------------------------------------------
    // PlayerTickEvent - realm entry + border enforcement
    // -------------------------------------------------------------------------

    private void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (event.side() != LogicalSide.SERVER) return;
        Player player = event.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        UUID playerUUID = serverPlayer.getUUID();

        // -- Process any pending realm entry (queued from block interaction) --
        UUID pendingOwner = pendingRealmEntries.remove(playerUUID);
        if (pendingOwner != null) {
            MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
            if (server != null) {
                ServerLevel realmLevel = server.getLevel(PocketDimensionsMod.REALM_DIM);
                if (realmLevel != null) {
                    BlockPos spawnPos = RealmManager.get(server).getSpawnPos(pendingOwner);
                    Vec3 dest = new Vec3(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                    serverPlayer.teleport(new TeleportTransition(realmLevel, dest, Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING));
                    // Init runtime state for the spawn position
                    int cx = spawnPos.getX() >> 4;
                    int cz = spawnPos.getZ() >> 4;
                    runtimeStates.put(playerUUID, new RuntimeRealmState(cx, cz, realmLevel.getGameTime()));
                } else {
                    LOGGER.warn("[WorldAnchor] Realm level not found, discarding pending entry for {}", playerUUID);
                }
            }
            return;
        }

        // -- Process any pending realm exit (queued from WorldCore interaction) --
        // Use contains(), NOT remove() here - the entry is consumed by onEntityTravelToDimension
        // when the teleport fires synchronously inside teleportToEntryOrSpawn().
        if (pendingRealmExits.contains(playerUUID)) {
            MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
            if (server != null) {
                RealmManager mgr = RealmManager.get(server);
                mgr.teleportToEntryOrSpawn(serverPlayer, server);
                runtimeStates.remove(playerUUID);
                // If teleport failed (e.g. target level null) the entry stays for retry next tick.
                // If teleport succeeded, onEntityTravelToDimension already removed the entry.
            }
            return;
        }

        // If not in the realm dimension, remove runtime state and bail
        if (!serverPlayer.level().dimension().equals(PocketDimensionsMod.REALM_DIM)) {
            runtimeStates.remove(playerUUID);
            return;
        }

        MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
        if (server == null) return;

        RealmManager mgr = RealmManager.get(server);

        // Persistent bounds check - eject if no info (e.g. never recorded, data corrupted)
        RealmManager.PlayerRealmInfo info = mgr.getPlayerRealmInfo(playerUUID);
        if (info == null) {
            mgr.teleportToEntryOrSpawn(serverPlayer, server);
            return;
        }

        double x = serverPlayer.getX();
        double y = serverPlayer.getY();
        double z = serverPlayer.getZ();
        float yaw = serverPlayer.getYRot();
        float pitch = serverPlayer.getXRot();
        long gameTime = serverPlayer.level().getGameTime();

        int cx = ((int) Math.floor(x)) >> 4;
        int cz = ((int) Math.floor(z)) >> 4;
        RuntimeRealmState rs = runtimeStates.computeIfAbsent(playerUUID,
                k -> new RuntimeRealmState(cx, cz, gameTime));

        boolean chunkChanged = cx != rs.lastChunkX || cz != rs.lastChunkZ;
        boolean timerExpired = gameTime - rs.lastCheckGameTime >= 200;

        if (chunkChanged || timerExpired) {
            rs.lastChunkX = cx;
            rs.lastChunkZ = cz;
            rs.lastCheckGameTime = gameTime;

            if (!info.contains(x, z)) {
                double M = 2.5;
                double clampedX = Math.max(info.minX + M, Math.min(info.maxX - M, x));
                double clampedZ = Math.max(info.minZ + M, Math.min(info.maxZ - M, z));
                serverPlayer.displayClientMessage(Component.literal(
                        "[Realm] Outside boundary - pushed back!"), true);
                // Use connection.teleport() directly - serverPlayer.teleport(TeleportTransition) fires
                // EntityTravelToDimensionEvent even for same-dimension moves, and our handler cancels
                // it (player is in REALM_DIM with no pending exit queued).
                serverPlayer.connection.teleport(clampedX, y, clampedZ, yaw, pitch);
            }
        }
    }

    // -------------------------------------------------------------------------
    // EntityTravelToDimensionEvent - block portals from inside the realm
    // Returning true from a Predicate listener cancels the event.
    // -------------------------------------------------------------------------

    private boolean onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof Player player)) return false; // allow - non-players
        if (event.getEntity().level().dimension().equals(PocketDimensionsMod.REALM_DIM)) {
            // Allow queued exits (WorldCore shift+right-click) - pendingRealmExits entry
            // is consumed here so the event fires only once per queued exit.
            if (pendingRealmExits.remove(player.getUUID())) return false; // allow queued exit
            return true; // cancel - block portals out of the realm
        }
        return false; // allow all other dimension travel
    }

    // -------------------------------------------------------------------------
    // PlayerInteractEvent.RightClickBlock - WorldAnchor entry works with any item held
    // -------------------------------------------------------------------------

    private void onRightClickWorldAnchor(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().getBlockState(event.getPos()).is(ModBlocks.WORLD_ANCHOR.get())) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;                         // empty hand already reaches useWithoutItem
        if (stack.is(ModItems.WORLD_SEED.get())) return;    // WorldSeed handles itself via useOn

        // Sneaking: let the item act normally so block items (e.g. World Breacher) can be placed.
        if (event.getEntity().isShiftKeyDown()) return;

        // Not sneaking: suppress the item so useWithoutItem fires and the player enters the realm.
        event.setUseItem(Result.DENY);
    }

    // -------------------------------------------------------------------------
    // MobSpawnEvent.FinalizeSpawn - no natural Monster spawns in realm
    // -------------------------------------------------------------------------

    private void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(PocketDimensionsMod.REALM_DIM)) return;
        if (event.getSpawnReason() != EntitySpawnReason.NATURAL) return;
        if (!(event.getEntity() instanceof Monster)) return;
        event.setSpawnCancelled(true);
    }

    // -------------------------------------------------------------------------
    // SleepFinishedTimeEvent - advance time to dawn when sleeping in realm
    // -------------------------------------------------------------------------

    private void onRealmSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(PocketDimensionsMod.REALM_DIM)) return;
        // Non-overworld dimensions use DerivedLevelData whose setDayTime() is a no-op.
        // The realm shares the overworld's day time, so we must advance it on the overworld directly.
        ServerLevel overworld = serverLevel.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        overworld.setDayTime(event.getNewTime());
    }
}
