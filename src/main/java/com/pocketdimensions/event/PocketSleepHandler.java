package com.pocketdimensions.event;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.manager.PocketRoomManager;
import com.pocketdimensions.manager.RoomHost;
import com.pocketdimensions.manager.SleepPool;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pocket rooms sleep with the world their anchor is in ({@link RoomHost}): the room is folded space inside that world,
 * not a world of its own. A bed in a pocket room follows the host's bed rule (night in the overworld or a realm, never in
 * the Nether or the End), and its sleeper joins the host's sleep pool: "1/5 players sleeping" counts the host's players
 * and everyone in pocket rooms anchored there, and the night passes once enough of them have slept (the
 * playersSleepingPercentage gamerule).
 * <p>
 * Vanilla counts sleepers per dimension. At the start of each host's tick this writes the pooled count into its
 * SleepStatus, so vanilla's own check never skips the night early, and then skips the night itself when the pool is
 * ready, the way ServerLevel.tick does (pocket sleepers aren't in the host's player list, so vanilla's "slept long
 * enough" check can't see them). The pocket dimension's own count is emptied every tick so it never wakes anyone alone.
 */
public final class PocketSleepHandler {

    private record Host(ServerLevel level, Vec3 pos) {}

    private static Field sleepStatusField;
    /** Per host: the last count shown (sleeping, needed, enough), so the message only goes out when it changes. */
    private final Map<ResourceKey<Level>, int[]> shown = new HashMap<>();
    private final Map<UUID, Host> hosts = new HashMap<>();
    private int hostsTick = -1;

    public PocketSleepHandler() {
        PlayerSleepInBedEvent.BUS.addListener(this::onSleepInBed);
        SleepingTimeCheckEvent.BUS.addListener(this::onSleepingTimeCheck);
        TickEvent.LevelTickEvent.Pre.BUS.addListener(this::onLevelTick);
    }

    // -------------------------------------------------------------------------
    // Beds: the host's rule
    // -------------------------------------------------------------------------

    /** Hosts that never sleep (the Nether, the End) refuse outright, with a word on why. */
    private void onSleepInBed(PlayerSleepInBedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !inPocket(player)) return;
        if (rule(hostOf(player)).canSleep() == BedRule.Rule.NEVER)
            event.setResult(new Player.BedSleepingProblem(
                    Component.literal("This pocket is folded into a land that knows no night.")));
    }

    /** Elsewhere a pocket bed works exactly when the host's would (the pocket's own time is frozen, never dark). */
    private void onSleepingTimeCheck(SleepingTimeCheckEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !inPocket(player)) return;
        Host host = hostOf(player);
        event.setResult(rule(host).canSleep(host.level()) ? Result.ALLOW : Result.DENY);
    }

    private static BedRule rule(Host host) {
        return host.level().environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, host.pos());
    }

    // -------------------------------------------------------------------------
    // The pool
    // -------------------------------------------------------------------------

    private void onLevelTick(TickEvent.LevelTickEvent.Pre event) {
        if (!(event.level() instanceof ServerLevel level)) return;
        if (level.dimension().equals(PocketDimensionsMod.POCKET_DIM)) {         // never counts on its own
            SleepStatus status = sleepStatus(level);
            if (status != null) status.removeAllSleepers();
            return;
        }
        MinecraftServer server = level.getServer();
        ServerLevel pocket = server.getLevel(PocketDimensionsMod.POCKET_DIM);
        List<ServerPlayer> hosted = new ArrayList<>();
        if (pocket != null) for (ServerPlayer p : pocket.players()) if (hostOf(p).level() == level) hosted.add(p);

        if (hosted.isEmpty()) {                     // no pocket rooms here: plain vanilla (undo any pooled count once)
            if (shown.remove(level.dimension()) != null) syncVanilla(level, level.players());
            return;
        }
        List<ServerPlayer> everyone = new ArrayList<>(level.players());
        everyone.addAll(hosted);
        int percent = level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        SleepPool pool = SleepPool.of(everyone.stream()
                .map(p -> new SleepPool.Sleeper(p.isSpectator(), p.isSleeping(), p.isSleepingLongEnough())).toList());

        syncVanilla(level, everyone);
        announce(level, everyone, pool, percent);
        if (percent <= 100 && pool.skipNight(percent)) morning(level, everyone);
    }

    /** Vanilla's message, to the whole pool, whenever the count changes (as ServerLevel.announceSleepStatus). */
    private void announce(ServerLevel level, List<ServerPlayer> everyone, SleepPool pool, int percent) {
        int needed = pool.needed(percent);
        int[] now = {pool.sleeping(), needed, pool.enoughInBed(percent) ? 1 : 0};
        int[] before = shown.put(level.dimension(), now);
        boolean changed = before == null || before[0] != now[0] || before[1] != now[1] || before[2] != now[2];
        boolean anyone = now[0] > 0 || (before != null && before[0] > 0);
        MinecraftServer server = level.getServer();
        if (!changed || !anyone || percent > 100 || (server.isSingleplayer() && !server.isPublished())) return;
        Component msg = now[2] == 1 ? Component.translatable("sleep.skipping_night")
                : Component.translatable("sleep.players_sleeping", now[0], needed);
        for (ServerPlayer p : everyone) p.displayClientMessage(msg, true);
    }

    /**
     * What ServerLevel.tick does when enough have slept: morning, everyone up, and the rain cleared. A realm's own
     * setDayTime does nothing (it borrows the overworld's clock); RealmEventHandler moves the overworld's instead.
     */
    private void morning(ServerLevel level, List<ServerPlayer> everyone) {
        if (level.getGameRules().get(GameRules.ADVANCE_TIME)) {
            long next = level.getDayTime() + 24000L;
            level.setDayTime(ForgeEventFactory.onSleepFinished(level, next - next % 24000L, level.getDayTime()));
        }
        SleepStatus status = sleepStatus(level);
        if (status != null) status.removeAllSleepers();
        everyone.stream().filter(ServerPlayer::isSleeping).toList().forEach(p -> p.stopSleepInBed(false, false));
        if (level.getGameRules().get(GameRules.ADVANCE_WEATHER) && level.isRaining())
            level.setWeatherParameters(0, 0, false, false);
        shown.put(level.dimension(), new int[]{0, 0, 0});
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean inPocket(ServerPlayer player) {
        return player.level().dimension().equals(PocketDimensionsMod.POCKET_DIM);
    }

    /** The world a pocket-room player's room belongs to; worked out once per tick per player. */
    private Host hostOf(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (hostsTick != server.getTickCount()) { hosts.clear(); hostsTick = server.getTickCount(); }
        return hosts.computeIfAbsent(player.getUUID(), id -> {
            PocketRoomManager mgr = PocketRoomManager.get(server);
            UUID room = mgr.findRoomForOccupant(id);
            var anchor = room == null ? null : mgr.getAnchorLocation(room).orElse(null);
            PocketRoomManager.EntryLocation entry = mgr.getEntryLocation(id);
            ResourceKey<Level> dim = RoomHost.pick(anchor == null ? null : anchor.getKey(),
                    entry == null ? null : entry.dimension, Level.OVERWORLD);
            ServerLevel level = server.getLevel(dim);
            if (level == null) level = server.overworld();
            Vec3 pos = anchor != null && dim.equals(anchor.getKey()) ? Vec3.atCenterOf(anchor.getValue())
                    : entry != null && dim.equals(entry.dimension) ? new Vec3(entry.x, entry.y, entry.z)
                    : Vec3.atCenterOf(BlockPos.ZERO);
            return new Host(level, pos);
        });
    }

    private static void syncVanilla(ServerLevel level, List<ServerPlayer> everyone) {
        SleepStatus status = sleepStatus(level);
        if (status != null) status.update(everyone);
    }

    /** ServerLevel keeps its SleepStatus in a package-private field; found by type, not name. */
    private static SleepStatus sleepStatus(ServerLevel level) {
        try {
            if (sleepStatusField == null) {
                for (Field f : ServerLevel.class.getDeclaredFields()) {
                    if (f.getType() == SleepStatus.class) { f.setAccessible(true); sleepStatusField = f; break; }
                }
            }
            return sleepStatusField == null ? null : (SleepStatus) sleepStatusField.get(level);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
