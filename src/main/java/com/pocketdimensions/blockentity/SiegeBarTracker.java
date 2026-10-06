package com.pocketdimensions.blockentity;

import com.pocketdimensions.PocketDimensionsConfig;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.manager.RealmManager;
import com.pocketdimensions.network.ModNetworking;
import com.pocketdimensions.network.SiegeBarS2C;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who sees a siege block's bar, and keeping them told: players within {@code siege_bossbar_range} of the block in its
 * own dimension, and players inside the linked realm's plot (they pick their look client-side from their dimension).
 * Replaces the vanilla boss bar, which Forge cannot restyle.
 */
public final class SiegeBarTracker {

    private final UUID id = UUID.randomUUID();
    private final Set<ServerPlayer> watching = new HashSet<>();

    /** Sends {@code state} to everyone now in range, and a removal to anyone who has left. */
    public void update(ServerLevel level, BlockPos pos, @Nullable WorldAnchorBlockEntity anchor, SiegeBarS2C state) {
        Set<ServerPlayer> now = eligible(level, pos, anchor);
        for (ServerPlayer p : watching) if (!now.contains(p)) send(p, SiegeBarS2C.removal(id));
        watching.clear();
        watching.addAll(now);
        SiegeBarS2C msg = new SiegeBarS2C(id, false, state.kind(), state.progressTicks(), state.durationTicks(), state.rate(),
                state.siegeFuel(), state.siegeCap(), state.coreFuel());
        for (ServerPlayer p : now) send(p, msg);
    }

    /** Ends the bar for everyone who could see it. */
    public void clear() {
        for (ServerPlayer p : watching) send(p, SiegeBarS2C.removal(id));
        watching.clear();
    }

    public boolean isShowing() { return !watching.isEmpty(); }

    private static void send(ServerPlayer p, SiegeBarS2C msg) {
        if (p.hasDisconnected()) return;
        ModNetworking.getChannel().send(msg, PacketDistributor.PLAYER.with(p));
    }

    private static Set<ServerPlayer> eligible(ServerLevel level, BlockPos pos, @Nullable WorldAnchorBlockEntity anchor) {
        MinecraftServer server = level.getServer();
        double range = PocketDimensionsConfig.SIEGE_BOSSBAR_RANGE.get();
        Set<ServerPlayer> out = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() == level && p.blockPosition().closerThan(pos, range)) out.add(p);
        }
        if (anchor != null && anchor.getOwnerUUID() != null) {
            int[] bounds = RealmManager.get(server).getRealmBounds(anchor.getOwnerUUID());
            ServerLevel realm = server.getLevel(PocketDimensionsMod.REALM_DIM);
            if (realm != null && bounds != null) for (ServerPlayer p : realm.players()) {
                if (p.getX() >= bounds[0] && p.getX() < bounds[2] && p.getZ() >= bounds[1] && p.getZ() < bounds[3]) out.add(p);
            }
        }
        return out;
    }
}
