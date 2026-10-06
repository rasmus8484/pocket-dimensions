package com.pocketdimensions.manager;

import org.junit.jupiter.api.Test;

import static com.pocketdimensions.manager.RealmRules.Role.*;
import static org.junit.jupiter.api.Assertions.*;

class RealmRulesTest {

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
