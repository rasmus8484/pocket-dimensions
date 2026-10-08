package com.pocketdimensions.worldgen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** What may generate and spawn in the realms: a big switch, category overrides, then whitelist / blacklist exceptions. */
class RealmGenRulesTest {

    private static final Predicate<String> NO_TAGS = tag -> false;

    @Test
    void withoutExceptionsTheSwitchDecides() {
        assertTrue(RealmGenRules.allowed(true, List.of(), List.of(), "minecraft:village_plains", NO_TAGS));
        assertFalse(RealmGenRules.allowed(false, List.of(), List.of(), "minecraft:village_plains", NO_TAGS));
    }

    @Test
    void theWhitelistLetsThingsThroughWhenTheSwitchIsOff() {
        assertTrue(RealmGenRules.allowed(false, List.of("minecraft:village_plains"), List.of(), "minecraft:village_plains", NO_TAGS));
        assertFalse(RealmGenRules.allowed(false, List.of("minecraft:village_plains"), List.of(), "minecraft:igloo", NO_TAGS));
    }

    @Test
    void theBlacklistStopsThingsWhenTheSwitchIsOnAndBeatsTheWhitelist() {
        assertFalse(RealmGenRules.allowed(true, List.of(), List.of("minecraft:monster_room"), "minecraft:monster_room", NO_TAGS));
        assertFalse(RealmGenRules.allowed(true, List.of("minecraft:zombie"), List.of("minecraft:zombie"), "minecraft:zombie", NO_TAGS),
                "on both lists: kept out");
    }

    @Test
    void tagsMatchEverythingInThem() {
        Predicate<String> villages = tag -> tag.equals("minecraft:village");
        assertTrue(RealmGenRules.allowed(false, List.of("#minecraft:village"), List.of(), "minecraft:village_desert", villages));
        assertFalse(RealmGenRules.allowed(true, List.of(), List.of("#minecraft:village"), "minecraft:village_desert", villages));
    }

    @Test
    void anIdWithoutANamespaceMeansMinecraft() {
        assertFalse(RealmGenRules.allowed(true, List.of(), List.of("zombie"), "minecraft:zombie", NO_TAGS));
        assertTrue(RealmGenRules.allowed(false, List.of(" Minecraft:Zombie "), List.of(), "minecraft:zombie", NO_TAGS),
                "case and stray spaces don't matter");
    }

    @Test
    void mobsFollowTheirGroupUnlessTheirCategoryOverridesIt() {
        // monsters are the one unfriendly category
        assertFalse(RealmGenRules.mobBase(false, false, true, "group"));
        assertTrue(RealmGenRules.mobBase(false, true, false, "group"), "spawn_monsters = true");
        assertTrue(RealmGenRules.mobBase(true, false, true, "group"));
        assertFalse(RealmGenRules.mobBase(true, false, false, "group"), "spawn_friendly_mobs = false");
        assertTrue(RealmGenRules.mobBase(false, false, true, "true"), "the category says yes");
        assertFalse(RealmGenRules.mobBase(true, true, true, "false"), "the category says no");
    }

    @Test
    void entriesAreCheckedBeforeTheyReachTheGame() {
        assertTrue(RealmGenRules.validEntry("minecraft:zombie"));
        assertTrue(RealmGenRules.validEntry("#minecraft:village"));
        assertTrue(RealmGenRules.validEntry("zombie"));
        assertFalse(RealmGenRules.validEntry("not an id!"));
        assertFalse(RealmGenRules.validEntry(""));
        assertFalse(RealmGenRules.validEntry(42));
    }
}
