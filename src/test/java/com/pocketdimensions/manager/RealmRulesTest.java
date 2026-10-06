package com.pocketdimensions.manager;

import org.junit.jupiter.api.Test;

import static com.pocketdimensions.manager.RealmRules.Role.*;
import static org.junit.jupiter.api.Assertions.*;

class RealmRulesTest {

    @Test
    void onlyTheOwnerTheAccessListOrAnOpenBreachMayBeInARealm() {
        assertTrue(RealmRules.mayEnter(true, false, false), "the owner");
        assertTrue(RealmRules.mayEnter(false, true, false), "a player on the access list");
        assertTrue(RealmRules.mayEnter(false, false, true), "anyone while a fuelled, completed breach stands");
        assertFalse(RealmRules.mayEnter(false, false, false), "anyone else, however they arrived");
    }

    @Test
    void aBreacherNeedsSomeoneFromTheRealmInsideIt() {
        java.util.UUID owner = new java.util.UUID(0, 1), friend = new java.util.UUID(0, 2), stranger = new java.util.UUID(0, 3);
        java.util.List<java.util.UUID> allowed = java.util.List.of(friend);
        assertFalse(RealmRules.defenderInside(owner, allowed, java.util.List.of()), "an empty realm");
        assertFalse(RealmRules.defenderInside(owner, allowed, java.util.List.of(stranger)), "only someone who was let in by a breach or smuggled");
        assertTrue(RealmRules.defenderInside(owner, allowed, java.util.List.of(stranger, friend)), "someone on the access list");
        assertTrue(RealmRules.defenderInside(owner, allowed, java.util.List.of(owner)), "the owner");
    }

    @Test
    void anAnchorMustLeaveRoomAboveItForASiegeBlock() {
        assertTrue(RealmRules.roomForSiege(70, 319, 0f), "open air above");
        assertTrue(RealmRules.roomForSiege(70, 319, 50f), "obsidian above: slow, but a siege can clear it");
        assertFalse(RealmRules.roomForSiege(127, 255, -1f), "the Nether's bedrock ceiling");
        assertTrue(RealmRules.roomForSiege(319, 319, 0f), "the very top of the world still fits a siege block");
        assertFalse(RealmRules.roomForSiege(320, 319, 0f), "above the build limit");
    }

    @Test
    void onlyOwnersAndManagersSeeAccessAndManage() {
        assertTrue(RealmRules.canManage(OWNER));
        assertTrue(RealmRules.canManage(MANAGER));
        assertFalse(RealmRules.canManage(VISITOR));
    }

    @Test
    void onlyTheOwnerCrownsAndRelocates() {
        assertTrue(RealmRules.canCrown(OWNER));
        assertFalse(RealmRules.canCrown(MANAGER));
        assertTrue(RealmRules.canRelocate(OWNER));
        assertFalse(RealmRules.canRelocate(MANAGER));
        assertFalse(RealmRules.canRelocate(VISITOR));
    }

    @Test
    void managersCanRemoveOrdinaryPlayersButNotOtherManagers() {
        assertTrue(RealmRules.canRemove(OWNER, true));
        assertTrue(RealmRules.canRemove(OWNER, false));
        assertTrue(RealmRules.canRemove(MANAGER, false));
        assertFalse(RealmRules.canRemove(MANAGER, true));
        assertFalse(RealmRules.canRemove(VISITOR, false));
    }

    @Test
    void visitorsCanGiveLapisButNotTakeIt() {
        assertTrue(RealmRules.canTakeLapis(OWNER));
        assertTrue(RealmRules.canTakeLapis(MANAGER));
        assertFalse(RealmRules.canTakeLapis(VISITOR));
    }

    @Test
    void realmNamesAreTrimmedCappedAndStrippedOfFormatting() {
        assertEquals("Hollowmere", RealmRules.cleanName("  Hollowmere  "));
        assertEquals("Red", RealmRules.cleanName("§cRed"));
        assertEquals(24, RealmRules.cleanName("x".repeat(40)).length());
        assertEquals("", RealmRules.cleanName("   "));
    }

    @Test
    void anUnnamedRealmIsNamedAfterItsOwner() {
        assertEquals("Realm of Wayfarer", RealmRules.displayName("", "Wayfarer"));
        assertEquals("Hollowmere", RealmRules.displayName("Hollowmere", "Wayfarer"));
    }
}
