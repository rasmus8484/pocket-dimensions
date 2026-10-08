package com.pocketdimensions.manager;

/**
 * Who and what comes along when a player travels into or out of a pocket room or a realm (event/Companions does the
 * moving). Plain logic, unit tested.
 */
public final class TravelRules {

    /** A trip the player chose (an anchor, the core, the way out of a room), or one they were sent on. */
    public enum Journey { CHOSEN, FORCED }

    private TravelRules() {}

    /** Only a trip you chose brings company; thrown out (ejected, the room destroyed), you go alone. */
    public static boolean bringsCompany(Journey journey) {
        return journey == Journey.CHOSEN;
    }

    /** What you ride comes along, unless another player is steering it or it has no room where you arrive. */
    public static boolean takesMount(boolean steeredByAnotherPlayer, boolean fitsWhereYouArrive) {
        return !steeredByAnotherPlayer && fitsWhereYouArrive;
    }

    /**
     * Whatever else is aboard comes too, monsters included; other players are set down first (they would arrive with no
     * record of coming in, and have their own way through).
     */
    public static boolean passengerComes(boolean isAnotherPlayer) {
        return !isAnotherPlayer;
    }

    /** Animals on your lead follow you; a monster on a lead stays behind, and whatever is aboard rides along instead. */
    public static boolean leashedFollows(boolean monster, boolean alreadyAboard) {
        return !monster && !alreadyAboard;
    }

    /**
     * Right-clicking a pocket room's wall takes a rider out, mount and all: they can't crouch + jump (crouching sets them
     * down). On foot a wall is just a wall, so a stray click never throws anyone out.
     */
    public static boolean wallLetsYouOut(boolean riding) {
        return riding;
    }
}
