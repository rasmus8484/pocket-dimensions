package com.pocketkingdoms.event;

import com.pocketkingdoms.PocketKingdomsMod;
import com.pocketkingdoms.blockentity.WorldCoreBlockEntity;
import com.pocketkingdoms.manager.RealmManager;
import com.pocketkingdoms.network.ModNetworking;
import com.pocketkingdoms.network.WardSyncS2C;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells each player in a realm the state of that realm's ward (for the sigil overhead), once a second if it changed.
 * The siege state and lapis come from the World Core when its chunk is loaded, else from the last time it was; never
 * seen since the server started, the realm is at peace unless its anchor is gone.
 */
public class WardSync {

    private record Known(int siege, boolean warded) {}

    private final Map<UUID, WardSyncS2C> sent = new HashMap<>();
    private final Map<UUID, Known> known = new HashMap<>();
    private final Map<UUID, Boolean> breach = new HashMap<>();

    public WardSync() {
        TickEvent.PlayerTickEvent.Post.BUS.addListener(this::onPlayerTick);
        // a player who logs out (and back in) gets the ward sent again: their new level has no sigil until then
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e -> sent.remove(e.getEntity().getUUID()));
        // so does one changing dimension, even out and back between two of its ticks
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(e -> sent.remove(e.getEntity().getUUID()));
        // one WardSync serves every world opened in this game (singleplayer): nothing carries over between them
        ServerStoppedEvent.BUS.addListener(e -> { sent.clear(); known.clear(); breach.clear(); });
    }

    private void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (event.side() != LogicalSide.SERVER || !(event.player() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().equals(PocketKingdomsMod.REALM_DIM)) {
            sent.remove(player.getUUID());          // re-sent on the next arrival
            return;
        }
        if ((player.level().getGameTime() + player.getId()) % 20 != 0) return;
        WardSyncS2C now = stateFor(player);
        if (now.equals(sent.get(player.getUUID()))) return;
        sent.put(player.getUUID(), now);
        ModNetworking.getChannel().send(now, PacketDistributor.PLAYER.with(player));
    }

    private WardSyncS2C stateFor(ServerPlayer player) {
        ServerLevel realm = (ServerLevel) player.level();
        MinecraftServer server = realm.getServer();
        RealmManager mgr = RealmManager.get(server);
        RealmManager.PlayerRealmInfo info = mgr.getPlayerRealmInfo(player.getUUID());
        if (info == null) return WardSyncS2C.absent();
        UUID owner = info.realmOwner;
        BlockPos core = mgr.getWorldCorePos(owner);
        if (core == null) return WardSyncS2C.absent();
        if (realm.isLoaded(core) && realm.getBlockEntity(core) instanceof WorldCoreBlockEntity be)
            known.put(owner, new Known(be.getSiegeState(), be.hasDefenseFuel()));
        Known k = known.getOrDefault(owner, new Known(
                mgr.getAnchorLocation(owner).isPresent() ? WorldCoreBlockEntity.STATE_NORMAL : WorldCoreBlockEntity.STATE_ANCHOR_LOST, false));
        int[] b = mgr.getRealmBounds(owner);
        return new WardSyncS2C(true, core.getX(), core.getY(), core.getZ(), b[0], b[1], b[2], b[3], k.siege(), breachOpen(server, mgr, owner), k.warded());
    }

    /** Whether a breach stands open, read only while the anchor's chunk is loaded (checking must never load it). */
    private boolean breachOpen(MinecraftServer server, RealmManager mgr, UUID owner) {
        var anchor = mgr.getAnchorLocation(owner);
        if (anchor.isEmpty()) { breach.remove(owner); return false; }
        ServerLevel level = server.getLevel(anchor.get().getKey());
        if (level != null && level.isLoaded(anchor.get().getValue())) breach.put(owner, mgr.isBreachOpen(server, owner));
        return breach.getOrDefault(owner, false);
    }
}
