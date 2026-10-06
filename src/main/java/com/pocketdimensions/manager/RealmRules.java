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
