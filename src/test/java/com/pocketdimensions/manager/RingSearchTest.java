package com.pocketdimensions.manager;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The World Core's dry-land search: outward ring by ring from the plot's centre, stopping soon after it finds land. */
class RingSearchTest {

    /** Counts the chunks looked at, and finds land only in the chunks given. */
    private static final class Land {
        final Set<String> probed = new HashSet<>();
        final Set<String> land;
        Land(String... land) { this.land = Set.of(land); }
        RingSearch.Hit<String> probe(int dx, int dz) {
            probed.add(dx + "," + dz);
            String key = dx + "," + dz;
            return land.contains(key) ? new RingSearch.Hit<>(key, dx * dx + dz * dz) : null;
        }
    }

    @Test
    void landAtTheCentreLooksOnlyOneRingFurther() {
        Land l = new Land("0,0");
        assertEquals("0,0", RingSearch.nearest(16, l::probe));
        assertEquals(9, l.probed.size(), "the centre and the ring around it, not 33 x 33 chunks");
    }

    @Test
    void landFurtherOutStopsOneRingPastIt() {
        Land l = new Land("3,0");
        assertEquals("3,0", RingSearch.nearest(16, l::probe));
        assertEquals(81, l.probed.size(), "rings 0 to 4: 9 x 9 chunks");
    }

    @Test
    void theNearerOfTwoFindsWins() {
        Land l = new Land("2,2", "0,2");
        assertEquals("0,2", RingSearch.nearest(16, l::probe));
    }

    @Test
    void noLandWithinTheLimitGivesNothingAndLooksNoFurther() {
        Land l = new Land("9,9");
        assertNull(RingSearch.nearest(2, l::probe));
        assertEquals(25, l.probed.size(), "rings 0 to 2 only");
    }
}
