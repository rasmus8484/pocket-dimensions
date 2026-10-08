package com.pocketdimensions.event;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.manager.SleepPool;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.common.util.Result;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Pocket rooms sleep with the overworld. A bed in a pocket room works whenever the overworld's do (night or a
 * thunderstorm), and everyone in the overworld and in pocket rooms is one sleep pool: "1/5 players sleeping" counts
 * them all, and the night passes once enough of them have slept (the playersSleepingPercentage gamerule).
 * <p>
 * Vanilla counts sleepers per dimension. At the start of every overworld tick this writes the pooled count into both
 * dimensions' SleepStatus, so neither vanilla check skips the night early or wakes a pocket sleeper alone; then it skips
 * the night itself when the pool is ready, the way ServerLevel.tick does (sleepers in a pocket room aren't in the
 * overworld's player list, so vanilla's own "slept long enough" check can't see them).
 */
public final class PocketSleepHandler {

    private static Field sleepStatusField;
    private int lastSleeping = 0, lastNeeded = 0;
    private boolean lastEnough = false;

    public PocketSleepHandler() {
        SleepingTimeCheckEvent.BUS.addListener(this::onSleepingTimeCheck);
        TickEvent.LevelTickEvent.Pre.BUS.addListener(this::onLevelTick);
    }

    /** In a pocket room, beds follow the overworld's night (the dimension's own time is frozen, so never dark). */
    private void onSleepingTimeCheck(SleepingTimeCheckEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().equals(PocketDimensionsMod.POCKET_DIM)) return;
        if (player.level().getServer().overworld().isDarkOutside()) event.setResult(Result.ALLOW);
    }

    private void onLevelTick(TickEvent.LevelTickEvent.Pre event) {
        if (!(event.level() instanceof ServerLevel overworld) || overworld.dimension() != Level.OVERWORLD) return;
        MinecraftServer server = overworld.getServer();
        ServerLevel pocket = server.getLevel(PocketDimensionsMod.POCKET_DIM);

        if (pocket == null) return;
        if (pocket.players().isEmpty()) {           // nobody in a pocket room: plain vanilla (undo any pooled count)
            if (lastSleeping > 0 || lastEnough) syncVanilla(overworld, overworld.players());
            lastSleeping = 0; lastEnough = false;
            return;
        }
        List<ServerPlayer> everyone = new ArrayList<>(overworld.players());
        everyone.addAll(pocket.players());

        int percent = overworld.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        SleepPool pool = SleepPool.of(everyone.stream()
                .map(p -> new SleepPool.Sleeper(p.isSpectator(), p.isSleeping(), p.isSleepingLongEnough())).toList());

        syncVanilla(overworld, everyone);
        syncVanilla(pocket, everyone);
        announce(server, everyone, pool, percent);

        if (percent <= 100 && pool.skipNight(percent)) morning(overworld, pocket);
    }

    /** Vanilla's message, shown to the whole pool whenever the count changes (as ServerLevel.announceSleepStatus). */
    private void announce(MinecraftServer server, List<ServerPlayer> everyone, SleepPool pool, int percent) {
        int needed = pool.needed(percent);
        boolean enough = pool.enoughInBed(percent);
        boolean changed = pool.sleeping() != lastSleeping || needed != lastNeeded || enough != lastEnough;
        boolean anyone = pool.sleeping() > 0 || lastSleeping > 0;
        lastSleeping = pool.sleeping(); lastNeeded = needed; lastEnough = enough;
        if (!changed || !anyone || percent > 100) return;
        if (server.isSingleplayer() && !server.isPublished()) return;
        Component msg = enough ? Component.translatable("sleep.skipping_night")
                : Component.translatable("sleep.players_sleeping", pool.sleeping(), needed);
        for (ServerPlayer p : everyone) p.displayClientMessage(msg, true);
    }

    /** What ServerLevel.tick does when enough have slept: morning, everyone up, and the rain cleared. */
    private void morning(ServerLevel overworld, ServerLevel pocket) {
        if (overworld.getGameRules().get(GameRules.ADVANCE_TIME)) {
            long next = overworld.getDayTime() + 24000L;
            overworld.setDayTime(ForgeEventFactory.onSleepFinished(overworld, next - next % 24000L, overworld.getDayTime()));
        }
        for (ServerLevel level : List.of(overworld, pocket)) {
            SleepStatus status = sleepStatus(level);
            if (status != null) status.removeAllSleepers();
            level.players().stream().filter(ServerPlayer::isSleeping).toList().forEach(p -> p.stopSleepInBed(false, false));
        }
        if (overworld.getGameRules().get(GameRules.ADVANCE_WEATHER) && overworld.isRaining())
            overworld.setWeatherParameters(0, 0, false, false);
        lastSleeping = 0; lastEnough = false;
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
