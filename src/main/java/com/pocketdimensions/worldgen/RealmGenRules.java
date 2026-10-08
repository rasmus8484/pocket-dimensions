package com.pocketdimensions.worldgen;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * What may generate and spawn in the realms, as the server config sets it: a big switch (structures, features,
 * monsters, friendly mobs), for mobs a per-category override, then the exceptions: the whitelist lets something through
 * whatever the switch says, the blacklist keeps it out whatever else says (it wins over the whitelist). Entries are ids
 * ("minecraft:zombie", or just "zombie") or tags ("#minecraft:village"). Pure, tested; RealmWorldRules applies it.
 */
public final class RealmGenRules {

    private RealmGenRules() {}

    /** The decision for one structure, feature or mob with registry id {@code id}; {@code inTag} answers "#tag" entries. */
    public static boolean allowed(boolean base, List<String> whitelist, List<String> blacklist, String id,
                                  Predicate<String> inTag) {
        if (matches(blacklist, id, inTag)) return false;
        if (matches(whitelist, id, inTag)) return true;
        return base;
    }

    /**
     * A mob's starting answer before the exceptions: its category's override ("true" / "false"), or else the switch for
     * its group (monsters are the one unfriendly category; everything else counts as friendly).
     */
    public static boolean mobBase(boolean friendly, boolean spawnMonsters, boolean spawnFriendly, String override) {
        return switch (override) {
            case "true" -> true;
            case "false" -> false;
            default -> friendly ? spawnFriendly : spawnMonsters;
        };
    }

    /** For the config: an id or a #tag, with or without a namespace. */
    public static boolean validEntry(Object o) {
        return o instanceof String s && normalize(s.startsWith("#") ? s.substring(1) : s)
                .matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    private static boolean matches(List<String> list, String id, Predicate<String> inTag) {
        for (String raw : list) {
            String e = raw.trim();
            if (e.startsWith("#")) {
                if (inTag.test(normalize(e.substring(1)))) return true;
            } else if (normalize(e).equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Lower case, trimmed, "minecraft:" when no namespace is given. */
    static String normalize(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.contains(":") ? t : "minecraft:" + t;
    }
}
