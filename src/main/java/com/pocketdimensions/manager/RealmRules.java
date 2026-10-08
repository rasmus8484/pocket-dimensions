package com.pocketdimensions.manager;

/**
 * Who may do what at a World Core, and how realm names are cleaned. Plain logic (no Minecraft classes) so it is unit
 * tested; the server checks every core action against these, the screen only hides what a role can't do.
 */
public final class RealmRules {

    /** The owner; a manager (an allowed player the owner crowned); anyone else who reaches the core. */
    public enum Role { OWNER, MANAGER, VISITOR }

    public static final int MAX_NAME = 24;

    private RealmRules() {}

    /**
     * The access list and managers when a realm changes hands (/pd owner): the new owner needs no place on the list,
     * and the old owner stays on as a manager. The old owner is added even past max_allowed_players.
     */
    public static void handOver(java.util.List<java.util.UUID> allowed, java.util.Set<java.util.UUID> managers,
                                java.util.UUID oldOwner, java.util.UUID newOwner) {
        allowed.remove(newOwner);
        managers.remove(newOwner);
        if (oldOwner.equals(newOwner)) return;
        if (!allowed.contains(oldOwner)) allowed.add(oldOwner);
        managers.add(oldOwner);
    }

    /**
     * Who may be in a realm: its owner, a player on its access list, or anyone while a fuelled, completed World Breacher
     * stands on its anchor. The anchor checks this on entry; the realm checks it again on any other arrival.
     */
    public static boolean mayEnter(boolean owner, boolean allowed, boolean breachOpen) {
        return owner || allowed || breachOpen;
    }

    /**
     * A World Breacher may only be set on an anchor while the realm has a defender inside: its owner or someone on its
     * access list. Players in through a breach or smuggled through a pocket room don't count.
     */
    /**
     * Whether a siege block (World Breacher or Anchor Breaker) may be set on a realm's anchor: always by its owner (to
     * open their own realm to the public, say), by anyone else only while one of the realm's own is inside.
     */
    public static boolean maySetSiege(@org.jetbrains.annotations.Nullable java.util.UUID placer, java.util.UUID owner,
                                      boolean defenderInside) {
        return owner.equals(placer) || defenderInside;
    }

    public static boolean defenderInside(java.util.UUID owner, java.util.Collection<java.util.UUID> allowed,
                                         java.util.Collection<java.util.UUID> inside) {
        for (java.util.UUID p : inside) if (p.equals(owner) || allowed.contains(p)) return true;
        return false;
    }

    /**
     * A World Anchor must leave room for a siege block on top of it: the spot two above the anchor's foot has to be
     * inside the world and hold nothing unbreakable (hardness below zero, like the Nether's bedrock ceiling or a
     * modded wall), or the anchor could never be besieged.
     */
    public static boolean roomForSiege(int siegeY, int maxY, float hardness) {
        return siegeY <= maxY && hardness >= 0;
    }

    /** The Access and Manage tabs: add players, remove ordinary players, rename. */
    public static boolean canManage(Role role) { return role != Role.VISITOR; }

    /** Make an allowed player a manager or take it away. */
    public static boolean canCrown(Role role) { return role == Role.OWNER; }

    /** Grow the realm somewhere new, losing everything in it. */
    public static boolean canRelocate(Role role) { return role == Role.OWNER; }

    /** Remove a player from the access list; managers can't remove each other. */
    public static boolean canRemove(Role role, boolean targetIsManager) {
        return role == Role.OWNER || (role == Role.MANAGER && !targetIsManager);
    }

    /** Take lapis out of the ward; everyone may put it in. */
    public static boolean canTakeLapis(Role role) { return role != Role.VISITOR; }

    /** A realm name as typed, cleaned: formatting codes stripped, trimmed, at most MAX_NAME characters ("" = unnamed). */
    public static String cleanName(String raw) {
        String s = raw == null ? "" : raw.replaceAll("§.?", "").strip();
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME).strip() : s;
    }

    /** What a realm is called on screen: its name, or "Realm of <owner>" while unnamed. */
    public static String displayName(String name, String ownerName) {
        return name == null || name.isEmpty() ? "Realm of " + ownerName : name;
    }
}
