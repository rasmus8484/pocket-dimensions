package com.pocketdimensions.manager;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The Stone Dais the World Core lands on: its shape, its floor, its vines (RL-024). */
class LandingDaisTest {

    private static final IntBinaryOperator FLAT = (dx, dz) -> 70;

    @Test
    void theFloorIsFlatAtTheAverageGround() {
        assertEquals(70, LandingDais.plan(1, FLAT).floor());
        assertEquals(60, LandingDais.plan(1, (dx, dz) -> 60 + dx).floor(), "a steady slope: its average");
    }

    @Test
    void theFloorLiesBetweenTheLowestAndHighestGround() {
        int floor = LandingDais.plan(1, (dx, dz) -> dx < 0 ? 50 : 90).floor();
        assertTrue(floor > 50 && floor < 90, "floor " + floor);
    }

    @Test
    void theDaisIsADiscNineAcrossWithARoundedUndersideThreeDeep() {
        assertTrue(LandingDais.inDais(0, 0, 0));
        assertTrue(LandingDais.inDais(4, 0, 0), "the rim of the floor");
        assertFalse(LandingDais.inDais(5, 0, 0), "past the rim");
        assertTrue(LandingDais.inDais(0, -2, 0));
        assertFalse(LandingDais.inDais(0, -3, 0), "the underside is 3 deep: floor, then 2 below");
        assertFalse(LandingDais.inDais(0, 1, 0), "nothing above the floor");

        LandingDais.Plan p = LandingDais.plan(1, FLAT);
        Set<String> want = new HashSet<>();
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++)
                for (int below = -5; below <= 2; below++)
                    if (LandingDais.inDais(dx, below, dz)) want.add(dx + "," + below + "," + dz);
        Set<String> got = p.blocks().stream().map(b -> b.dx() + "," + (b.y() - p.floor()) + "," + b.dz()).collect(Collectors.toSet());
        assertEquals(want, got, "every block of the shape, and nothing else");
        assertEquals(want.size(), p.blocks().size(), "no block twice");
    }

    @Test
    void thePoolUnderTheCoreIsAuroraStone() {
        LandingDais.Plan p = LandingDais.plan(1, FLAT);
        long pool = p.blocks().stream()
                .filter(b -> b.y() == p.floor() && Math.hypot(b.dx(), b.dz()) < 1.6)
                .peek(b -> assertEquals(LandingDais.Kind.AURORA_STONE, b.kind(), "pool " + b))
                .count();
        assertEquals(9, pool);
    }

    @Test
    void vinesReachTheEdgeAndTrickleDownTheSide() {
        for (int plot = 0; plot < 300; plot++) {
            LandingDais.Plan p = LandingDais.plan(plot, FLAT);
            long rim = p.blocks().stream().filter(b -> b.kind() == LandingDais.Kind.AURORA_STONE && b.y() == p.floor()
                    && Math.hypot(b.dx(), b.dz()) >= 3.5).count();
            long side = p.blocks().stream().filter(b -> b.kind() == LandingDais.Kind.AURORA_STONE && b.y() < p.floor()).count();
            assertTrue(rim >= 4, "plot " + plot + ": aurora stone near the rim " + rim);
            assertTrue(side >= 1, "plot " + plot + ": a vine runs down the side");
        }
    }

    @Test
    void eachPlotHasItsOwnVinesAndTheyNeverChange() {
        assertEquals(LandingDais.plan(5, FLAT), LandingDais.plan(5, FLAT));
        assertNotEquals(LandingDais.plan(5, FLAT), LandingDais.plan(6, FLAT));
    }

    @Test
    void aFarPlotStillGrowsAWholeDais() {
        int whole = LandingDais.plan(1, FLAT).blocks().size();
        for (int plot : new int[]{Integer.MAX_VALUE / 8, Integer.MAX_VALUE, 1_000_003}) {
            LandingDais.Plan p = LandingDais.plan(plot, FLAT);
            assertEquals(whole, p.blocks().size(), "plot " + plot);
            assertTrue(p.blocks().stream().anyMatch(b -> b.kind() == LandingDais.Kind.AURORA_STONE && b.y() < p.floor()),
                    "plot " + plot + " has vines");
        }
    }

    @Test
    void firstAirIsJustAboveTheFirstGroundFromTheTop() {
        assertEquals(63, LandingDais.firstAir(80, -64, y -> y <= 62));
        assertEquals(76, LandingDais.firstAir(80, -64, y -> y == 75 || y <= 62), "the first ground from the top wins");
        assertEquals(-64, LandingDais.firstAir(80, -64, y -> false), "no ground at all: the bottom of the world");
    }

    @Test
    void firstAirCountsWaterAsGround() {
        // an ocean column: sand up to 40, water from 41 to 62; RealmManager's isGround says yes to both
        assertEquals(63, LandingDais.firstAir(63, -64, y -> y <= 62));
        int floor = LandingDais.plan(1, (dx, dz) -> 63).floor();
        assertEquals(63, floor, "the floor sits one above the water's surface (y 62)");
    }
}
