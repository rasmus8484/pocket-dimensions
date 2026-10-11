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
    void groundIsAnythingSolidThatDoesNotBurnOrAFluid() {
        assertTrue(LandingDais.isGround(false, true, false), "stone, dirt, sand");
        assertTrue(LandingDais.isGround(true, false, false), "water and lava: the dais floats on them");
        assertFalse(LandingDais.isGround(false, true, true), "logs, leaves, bamboo, huge mushrooms, bee nests: the forest floor is below");
        assertFalse(LandingDais.isGround(false, false, false), "air, flowers, grass, snow layers");
    }

    @Test
    void theWallRingSealsFluidsJustOutsideTheOpenCylinder() {
        assertFalse(LandingDais.inWall(5, 2), "inside the open cylinder (5.39 < 5.5)");
        assertTrue(LandingDais.inWall(5, 3), "the ring just outside it (5.83)");
        assertTrue(LandingDais.inWall(6, 0));
        assertFalse(LandingDais.inWall(7, 0), "beyond the ring");
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -7; dz <= 7; dz++)
                if (LandingDais.inWall(dx, dz)) assertTrue(Math.abs(dx) <= LandingDais.WALL_REACH && Math.abs(dz) <= LandingDais.WALL_REACH);
    }

    @Test
    void firstAirCountsWaterAsGround() {
        // an ocean column: sand up to 40, water from 41 to 62; RealmManager's isGround says yes to both
        assertEquals(63, LandingDais.firstAir(63, -64, y -> y <= 62));
        int floor = LandingDais.plan(1, (dx, dz) -> 63).floor();
        assertEquals(63, floor, "the floor sits one above the water's surface (y 62)");
    }

    private static final int[][] HERE_OR_BESIDE = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Every aurora stone block of a plan, dais and roots, as "dx,depth below the floor,dz". */
    private static Set<String> aurora(LandingDais.Plan p) {
        Set<String> s = new HashSet<>();
        for (LandingDais.Block b : p.blocks()) if (b.kind() == LandingDais.Kind.AURORA_STONE) s.add(b.dx() + "," + (p.floor() - b.y()) + "," + b.dz());
        for (LandingDais.Block b : p.roots()) s.add(b.dx() + "," + (p.floor() - b.y()) + "," + b.dz());
        return s;
    }

    @Test
    void theDaisIsStone() {
        LandingDais.Plan p = LandingDais.plan(1, FLAT);
        assertTrue(p.blocks().stream().anyMatch(b -> b.kind() == LandingDais.Kind.STONE));
        assertTrue(p.blocks().stream().allMatch(b -> b.kind() == LandingDais.Kind.STONE || b.kind() == LandingDais.Kind.AURORA_STONE));
    }

    @Test
    void rootsRunDownFromTheFloorJoinedBlockToBlock() {
        for (int plot = 0; plot < 300; plot++) {
            LandingDais.Plan p = LandingDais.plan(plot, FLAT);
            Set<String> aur = aurora(p);
            assertFalse(p.roots().isEmpty(), "plot " + plot + " has roots below the dais");
            for (LandingDais.Block b : p.roots()) {
                int d = p.floor() - b.y();
                assertEquals(LandingDais.Kind.AURORA_STONE, b.kind());
                assertTrue(d >= 1 && d < LandingDais.ROOT_DEPTH, "plot " + plot + " root depth " + d);
                assertTrue(Math.abs(b.dx()) <= 7 && Math.abs(b.dz()) <= 7, "plot " + plot + " stays in the Core's chunk: " + b);
                assertFalse(LandingDais.inDais(b.dx(), -d, b.dz()), "roots in the dais are dais blocks: " + b);
                boolean joined = false;
                for (int[] o : HERE_OR_BESIDE) joined |= aur.contains((b.dx() + o[0]) + "," + (d - 1) + "," + (b.dz() + o[1]));
                assertTrue(joined, "plot " + plot + ": " + b + " hangs from the vein above it");
            }
        }
    }

    @Test
    void rootsTurnTheDaisItselfToAuroraStoneOnTheirWayDown() {
        LandingDais.Plan p = LandingDais.plan(1, FLAT);
        long inside = p.blocks().stream().filter(b -> b.kind() == LandingDais.Kind.AURORA_STONE && b.y() < p.floor()).count();
        assertTrue(inside >= 8, "aurora stone inside the dais below the floor: " + inside);
    }

    @Test
    void aRootRunsDownUnderThePool() {
        for (int plot = 0; plot < 300; plot++) {
            Set<String> aur = aurora(LandingDais.plan(plot, FLAT));
            boolean under = false;
            for (int[] o : HERE_OR_BESIDE) under |= aur.contains(o[0] + ",1," + o[1]);
            assertTrue(under, "plot " + plot);
        }
    }
}
