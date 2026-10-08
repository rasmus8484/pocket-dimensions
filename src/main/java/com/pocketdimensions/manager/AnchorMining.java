package com.pocketdimensions.manager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who is mining which Pocket Anchor, and since when. A miner works one block at a time; several miners can work the
 * same anchor. Progress is counted the way ServerPlayerGameMode counts it, and the room inside shows one more set of
 * cracks for every quarter of it. Pure logic, tested; AnchorMiningHandler feeds it the game's left-click events.
 */
public final class AnchorMining<K> {

    public record Dig<K>(K anchor, long startTick) {}

    private final Map<UUID, Dig<K>> digs = new HashMap<>();

    public void start(UUID miner, K anchor, long tick) {
        digs.put(miner, new Dig<>(anchor, tick));
    }

    public void stop(UUID miner) {
        digs.remove(miner);
    }

    public Map<UUID, Dig<K>> digs() {
        return Collections.unmodifiableMap(digs);
    }

    /** How far a miner is (1 = broken): the block's progress per tick times the ticks since the first hit, inclusive. */
    public static float progress(float perTick, long startTick, long now) {
        return perTick * (now - startTick + 1);
    }

    /** Sets of cracks in the room: one at 25, 50 and 75 %. */
    public static int cracks(float progress) {
        return Math.max(0, Math.min(3, (int) Math.floor(progress * 4)));
    }
}
