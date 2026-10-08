package com.pocketdimensions.event;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.blockentity.PocketAnchorBlockEntity;
import com.pocketdimensions.blockentity.RoomVoidBlockEntity;
import com.pocketdimensions.init.ModBlocks;
import com.pocketdimensions.init.ModParticles;
import com.pocketdimensions.init.ModSounds;
import com.pocketdimensions.manager.AnchorMining;
import com.pocketdimensions.manager.PocketRoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * A Pocket Anchor being mined is felt from both sides. Outside, the cube bleeds red motes and reality cracks (sound).
 * Inside, the room shudders: the same crack at the same volume, a warning on the action bar, motes off the walls, and
 * the Anchor Breaker's lightning, enlarged, tearing in from every wall, one more set at 25, 50 and 75 % (drawn by the
 * room's RoomVoidBlockEntity). Stop mining and the cracks are gone. The miner is told at the first hit when someone is
 * inside. Every anchor shows its outside signs, occupied or not, so mining never gives away whether anyone is home.
 * Silent theft (crouch + right-click) stays silent.
 */
public final class AnchorMiningHandler {

    private record Spot(ResourceKey<Level> dim, BlockPos pos) {}

    /** An anchor someone is working on: its room and the sets of cracks showing. */
    private static final class Watch {
        final UUID pocketId;
        int cracks;
        Watch(UUID pocketId) { this.pocketId = pocketId; }
    }

    private final AnchorMining<Spot> mining = new AnchorMining<>();
    private final Map<Spot, Watch> watched = new HashMap<>();

    public AnchorMiningHandler() {
        PlayerInteractEvent.LeftClickBlock.BUS.addListener(this::onLeftClick);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e -> mining.stop(e.getEntity().getUUID()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(this::onServerTick);
    }

    private void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        UUID id = player.getUUID();
        switch (event.getAction()) {
            case START -> {
                if (!level.getBlockState(event.getPos()).is(ModBlocks.POCKET_ANCHOR.get())) { mining.stop(id); return; }
                mining.start(id, new Spot(level.dimension(), event.getPos().immutable()), level.getGameTime());
                UUID room = roomOf(level, event.getPos());
                if (room != null && !PocketRoomManager.get(level.getServer()).getOccupants(room).isEmpty())
                    player.displayClientMessage(Component.literal("Voices echo from within. Someone is inside."), true);
            }
            case STOP, ABORT -> mining.stop(id);
            default -> {}
        }
    }

    private void onServerTick(TickEvent.ServerTickEvent.Post event) {
        if (mining.digs().isEmpty() && watched.isEmpty()) return;
        MinecraftServer server = event.server();

        // How far each anchor is: the furthest of the players working it (a fist on it doesn't count)
        Map<Spot, Float> progress = new HashMap<>();
        for (var e : new ArrayList<>(mining.digs().entrySet())) {
            Spot spot = e.getValue().anchor();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            ServerLevel level = server.getLevel(spot.dim());
            if (p == null || level == null || p.level() != level || !level.getBlockState(spot.pos()).is(ModBlocks.POCKET_ANCHOR.get())) {
                mining.stop(e.getKey());
                continue;
            }
            float perTick = level.getBlockState(spot.pos()).getDestroyProgress(p, level, spot.pos());
            if (perTick <= 0) continue;
            progress.merge(spot, AnchorMining.progress(perTick, e.getValue().startTick(), level.getGameTime()), Math::max);
        }

        // Anchors no one works any more: the cracks close
        for (Iterator<Map.Entry<Spot, Watch>> it = watched.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (progress.containsKey(e.getKey())) continue;
            setCracks(server, e.getValue().pocketId, 0);
            it.remove();
        }

        long tick = server.getTickCount();
        for (var e : progress.entrySet()) {
            Spot spot = e.getKey();
            ServerLevel level = server.getLevel(spot.dim());
            Watch w = watched.get(spot);
            if (w == null) {
                UUID room = roomOf(level, spot.pos());
                if (room == null) continue;
                watched.put(spot, w = new Watch(room));
                crack(server, level, spot.pos(), w.pocketId);                // the first hit
            }
            int cracks = AnchorMining.cracks(e.getValue());
            if (cracks > w.cracks) {
                w.cracks = cracks;
                setCracks(server, w.pocketId, cracks);
                crack(server, level, spot.pos(), w.pocketId);                // each new set of cracks
            }
            if (tick % 5 == 0) motesAtAnchor(level, spot.pos());
            if (tick % 20 == 0) shudder(server, w.pocketId);
        }
    }

    // -------------------------------------------------------------------------
    // Effects
    // -------------------------------------------------------------------------

    /** The crack, at the anchor and for everyone inside, at the same volume. */
    private static void crack(MinecraftServer server, ServerLevel level, BlockPos pos, UUID room) {
        level.playSound(null, pos, ModSounds.REALITY_CRACK.get(), SoundSource.BLOCKS, 1f, 1f);
        var sound = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.REALITY_CRACK.get());
        for (ServerPlayer p : occupants(server, room))
            p.connection.send(new ClientboundSoundPacket(sound, SoundSource.BLOCKS, p.getX(), p.getEyeY(), p.getZ(),
                    1f, 1f, p.getRandom().nextLong()));
    }

    /** Red motes pulled out of the cube in every direction. */
    private static void motesAtAnchor(ServerLevel level, BlockPos pos) {
        RandomSource r = level.getRandom();
        for (int i = 0; i < 2; i++)
            level.sendParticles(ModParticles.UNMAKE.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0,
                    (r.nextDouble() - 0.5) * 1.6, r.nextDouble() * 0.8, (r.nextDouble() - 0.5) * 1.6, 1);
    }

    /** Inside: the warning, and motes shaken off the walls toward each occupant. */
    private static void shudder(MinecraftServer server, UUID room) {
        Component warning = Component.literal("Something is prying at your anchor. The room shudders.");
        for (ServerPlayer p : occupants(server, room)) {
            p.displayClientMessage(warning, true);
            ServerLevel level = (ServerLevel) p.level();
            RandomSource r = p.getRandom();
            for (int i = 0; i < 6; i++) {
                // a point on the nearest wall in a random direction, drifting a block or two toward the player
                double dx = (r.nextDouble() - 0.5) * 6, dy = r.nextDouble() * 3, dz = (r.nextDouble() - 0.5) * 6;
                double sx = p.getX() + dx * 1.5, sy = p.getY() + dy, sz = p.getZ() + dz * 1.5;
                level.sendParticles(p, ModParticles.UNMAKE.get(), false, false, sx, sy, sz, 0,
                        -dx * 0.3, -dy * 0.2, -dz * 0.3, 1);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static UUID roomOf(ServerLevel level, BlockPos pos) {
        if (level == null || !(level.getBlockEntity(pos) instanceof PocketAnchorBlockEntity be)) return null;
        return be.getPocketId();
    }

    private static java.util.List<ServerPlayer> occupants(MinecraftServer server, UUID room) {
        java.util.List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : PocketRoomManager.get(server).getOccupants(room)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.level().dimension().equals(PocketDimensionsMod.POCKET_DIM)) out.add(p);
        }
        return out;
    }

    private static void setCracks(MinecraftServer server, UUID room, int cracks) {
        ServerLevel pocket = server.getLevel(PocketDimensionsMod.POCKET_DIM);
        BlockPos heart = PocketRoomManager.get(server).heartPos(room);
        if (pocket == null || heart == null || !pocket.isLoaded(heart)) return;
        if (pocket.getBlockEntity(heart) instanceof RoomVoidBlockEntity be) be.setCracks(cracks);
    }
}
