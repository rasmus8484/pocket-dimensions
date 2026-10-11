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
                if (LandingDais.inWall(dx, dz)) assertTrue(Math.abs(dx) <= LandingDais.CASING_REACH && Math.abs(dz) <= LandingDais.CASING_REACH);
    }

    @Test
    void firstAirCountsWaterAsGround() {
        // an ocean column: sand up to 40, water from 41 to 62; RealmManager's isGround says yes to both
        assertEquals(63, LandingDais.firstAir(63, -64, y -> y <= 62));
        int floor = LandingDais.plan(1, (dx, dz) -> 63).floor();
        assertEquals(63, floor, "the floor sits one above the water's surface (y 62)");
    }

    private static final int[][] HERE_OR_BESIDE = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Every aurora stone block of a plan (the roots are in the dais), as "dx,depth below the floor,dz". */
    private static Set<String> aurora(LandingDais.Plan p) {
        Set<String> s = new HashSet<>();
        for (LandingDais.Block b : p.blocks()) if (b.kind() == LandingDais.Kind.AURORA_STONE) s.add(b.dx() + "," + (p.floor() - b.y()) + "," + b.dz());
        return s;
    }

    @Test
    void theDaisIsStone() {
        LandingDais.Plan p = LandingDais.plan(1, FLAT);
        assertTrue(p.blocks().stream().anyMatch(b -> b.kind() == LandingDais.Kind.STONE));
        assertTrue(p.blocks().stream().allMatch(b -> b.kind() == LandingDais.Kind.STONE || b.kind() == LandingDais.Kind.AURORA_STONE));
    }

    @Test
    void rootsStayInTheDaisJoinedBlockToBlock() {
        for (int plot = 0; plot < 300; plot++) {
            LandingDais.Plan p = LandingDais.plan(plot, FLAT);
            Set<String> aur = aurora(p);
            for (LandingDais.Block b : p.blocks()) {
                int d = p.floor() - b.y();
                assertTrue(LandingDais.inDais(b.dx(), -d, b.dz()), "plot " + plot + ": everything is in the dais: " + b);
                if (b.kind() != LandingDais.Kind.AURORA_STONE || d < 1) continue;
                boolean joined = false, spill = false;
                for (int[] o : HERE_OR_BESIDE) joined |= aur.contains((b.dx() + o[0]) + "," + (d - 1) + "," + (b.dz() + o[1]));
                for (int[] o : HERE_OR_BESIDE) spill |= !LandingDais.inDais(b.dx() + o[0], -d, b.dz() + o[1]);   // a vine down the side
                assertTrue(joined || spill, "plot " + plot + ": " + b + " hangs from the vein above it");
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

    @Test
    void nothingEverReachesOutOfTheCoresChunk() {
        // the Core's column is block 8 of its chunk, so 7 out either way is the chunk's edge; the casing reaches 6
        for (int plot = 0; plot < 20000; plot++) {
            LandingDais.Plan p = LandingDais.plan(plot, FLAT);
            for (LandingDais.Block b : p.blocks())
                assertTrue(Math.abs(b.dx()) <= 7 && Math.abs(b.dz()) <= 7, "plot " + plot + ": " + b);
            if (plot > 0) continue;                                    // the casing is the same shape for every plot
            for (LandingDais.Block b : p.shell())
                assertTrue(Math.abs(b.dx()) <= LandingDais.CASING_REACH && Math.abs(b.dz()) <= LandingDais.CASING_REACH, b.toString());
            for (LandingDais.Column c : p.wall())
                assertTrue(Math.abs(c.dx()) <= LandingDais.CASING_REACH && Math.abs(c.dz()) <= LandingDais.CASING_REACH, c.toString());
        }
    }

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    @Test
    void theCasingIsAirtight() {
        for (int plot = 0; plot < 50; plot++) {
            LandingDais.Plan p = LandingDais.plan(plot, (dx, dz) -> 64 + (dx + dz) / 3);
            int f = p.floor();
            Set<String> shell = new HashSet<>(), wall = new HashSet<>();
            p.shell().forEach(b -> shell.add(b.dx() + "," + (b.y() - f) + "," + b.dz()));
            p.wall().forEach(c -> wall.add(c.dx() + "," + c.dz()));
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) for (int rel = -6; rel <= 4; rel++) {
                if (!LandingDais.inside(x, rel, z)) continue;
                for (int[] o : FACES) {
                    int nx = x + o[0], nr = rel + o[1], nz = z + o[2];
                    if (LandingDais.inside(nx, nr, nz)) continue;
                    boolean cased = nr <= 0 ? shell.contains(nx + "," + nr + "," + nz) : wall.contains(nx + "," + nz);
                    assertTrue(cased, "plot " + plot + ": a way out at " + nx + "," + nr + "," + nz);
                }
            }
        }
    }

    @Test
    void theCasingIsOneBlockThick() {
        LandingDais.Plan p = LandingDais.plan(1, FLAT);
        int f = p.floor();
        for (LandingDais.Block b : p.shell()) {
            int rel = b.y() - f;
            assertFalse(LandingDais.inside(b.dx(), rel, b.dz()), "never inside the shape: " + b);
            boolean touches = false;
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++)
                touches |= LandingDais.inside(b.dx() + x, rel + y, b.dz() + z);
            assertTrue(touches, "touches the shape at least at a corner: " + b);
        }
        for (LandingDais.Column c : p.wall()) {
            double r = Math.hypot(c.dx(), c.dz());
            assertTrue(r >= LandingDais.CLEAR && r < LandingDais.CLEAR + 1.5, "the wall hugs the cylinder: " + c);
        }
    }

    @Test
    void theRimHasARingOfSlateAtFloorLevel() {
        LandingDais.Plan p = LandingDais.plan(1, FLAT);
        Set<String> atFloor = new HashSet<>();
        p.shell().stream().filter(b -> b.y() == p.floor()).forEach(b -> atFloor.add(b.dx() + "," + b.dz()));
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            double r = Math.hypot(x, z);
            if (r >= LandingDais.RADIUS && r < LandingDais.CLEAR) assertTrue(atFloor.contains(x + "," + z), "ring at " + x + "," + z);
        }
    }

    @Test
    void whatFusesIntoAuroraSlate() {
        assertTrue(LandingDais.fuses(false, true, false, false, false, false), "stone, earth, logs");
        assertFalse(LandingDais.fuses(false, true, true, false, false, false), "leaves stay");
        assertFalse(LandingDais.fuses(false, false, false, false, false, false), "plants, carpets, snow layers, air stay");
        assertFalse(LandingDais.fuses(false, true, false, true, false, false), "a chest keeps its contents");
        assertFalse(LandingDais.fuses(false, true, false, false, true, false), "bedrock stays");
        assertFalse(LandingDais.fuses(true, false, false, false, false, false), "water under the dais stays");
        assertTrue(LandingDais.fuses(true, false, false, false, false, true), "a lake in the wall is sealed");
    }

    @Test
    void fluidsAreSealedFromTheFloorUpButNotUnderIt() {
        // the ring round the rim at floor level opens onto the cleared cylinder, so a lake or lava reaching it is sealed;
        // under the floor (the ocean under a floating dais) fluids stay
        assertTrue(LandingDais.sealsFluids(0), "the ring at floor level");
        assertTrue(LandingDais.sealsFluids(5), "the wall above it");
        assertFalse(LandingDais.sealsFluids(-1), "under the floor");
        assertFalse(LandingDais.sealsFluids(-3));
    }
}
