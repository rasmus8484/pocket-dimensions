package com.pocketdimensions.advancement;

import com.pocketdimensions.init.ModTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * The moments the advancements in data/pocketdimensions/advancement/main listen for through PocketEventTrigger, and the
 * one call that reports them. Vanilla triggers cover the rest (holding items, changing dimension, sleeping, placing).
 */
public final class Milestones {

    /** Took someone else's Pocket Anchor (crouch + right-click). Light Fingers. */
    public static final String STEAL_ANCHOR = "steal_anchor";
    /** Was inside a pocket room as its anchor started to be mined. Echoes in the Walls. */
    public static final String ROOM_SHUDDERS = "room_shudders";
    /** Mined a Pocket Anchor to the end with someone inside. Unmade. */
    public static final String UNMADE = "unmade";
    /** Named a realm at its World Core. Heart of the Realm. */
    public static final String NAME_REALM = "name_realm";
    /** Let a player through a realm's wards (access list). Kept Company. */
    public static final String GRANT_ACCESS = "grant_access";
    /** Was in their realm while its World Core burned lapis against a siege. Hold the Line. */
    public static final String HOLD_THE_LINE = "hold_the_line";
    /** Entered a realm only a completed breach let them into. Open Gates. */
    public static final String BREACH_ENTRY = "breach_entry";
    /** An Anchor Breaker they placed destroyed its World Anchor. Severed. */
    public static final String SEVER_ANCHOR = "sever_anchor";

    public static final List<String> ALL = List.of(STEAL_ANCHOR, ROOM_SHUDDERS, UNMADE, NAME_REALM, GRANT_ACCESS,
            HOLD_THE_LINE, BREACH_ENTRY, SEVER_ANCHOR);

    private Milestones() {}

    public static void reach(Player player, String event) {
        if (player instanceof ServerPlayer sp) ModTriggers.EVENT.get().trigger(sp, event);
    }
}
