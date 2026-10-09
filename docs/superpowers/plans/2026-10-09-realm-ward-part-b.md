# Realm Ward Part B: the sigil Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A great rune sigil hangs over every realm at y 340, centred on the World Core, coloured by the siege, dimmer without lapis, faint by day, joined to the Core by its beam, drawn so it looks the same at every render distance.

**Architecture:** Three pure, tested classes carry the logic: `CameraPull` (the pull toward the camera), `WardLook` (colour, brightness, which elements show, pulses) and `SigilLayout` (where every rune and line goes). The server sends each player in a realm a `WardSyncS2C` with their realm's ward state when it changes (checked once a second). A mixin adds a frame pass before the main (terrain) pass, proven in the spike; `WardSigilRenderer` draws the sigil there with the `eyes` render type.

**Tech Stack:** Minecraft 1.21.11, Forge 61.1.0, Mixin 0.8.7 (shipped with Forge), Java 21, JUnit 5, Node texture tools.

**Spec:** `docs/superpowers/specs/2026-10-09-realm-ward-design.md` (Part B)

## Global Constraints

- Never use em dashes, or spaced en dashes as a substitute, anywhere.
- No `Co-Authored-By` lines in commits.
- Never run gradle while `runServer` or `runClient` is running: stop both first.
- The sigil's plane is y 340 (`SigilLayout.PLANE_Y`). Pull reach `R = min(96, 0.9 x fog start)`, fog start = `rd - clamp(rd / 10, 4, 64)` with `rd` the render distance in blocks. Each element is scaled about the camera by one factor `k = min(1, R / d)`.
- Colours: normal `#8CEBFF`, breaching `#FF5ADC`, breaking `#FF5028`, anchor lost dim red. Brightness full while warded, about half without; by day never below a quarter.
- Drawn after the sky, before the terrain, no depth writes (`RenderTypes.eyes`); nothing is drawn outside the realm dimension or before the first sync.
- Mixins: config `pocketdimensions.mixins.json`, loaded by `-mixin.config=` in dev runs and `MixinConfigs` in the jar manifest (done in the spike).

## Review Focus

1. A player in a realm whose World Core chunk is not loaded (far from the core, after a restart): the sigil must still appear, with the last known state or a sensible default (normal, or lost if the realm has no anchor). Pinned in Task 3 (`WardSync.stateFor` falls back).
2. Leaving the realm (World Core, death, logout) and entering another realm: the old realm's sigil must not linger or show in the new one. Pinned in Task 3 (client clears on level change; server forgets what it sent when the player leaves the realm dimension).
3. A small realm (radius 2 or 5) and a core far off centre: rings, spokes and band must stay inside the plot and runes must not overlap into a smear. Pinned by `SigilLayoutTest`.
4. Checking a breach must not load the anchor's chunk every second (`isBreachOpen` calls `getBlockEntity`, which loads chunks): only read it when the chunk is already loaded. Pinned in Task 3.
5. Very long frames: the per-frame build must not allocate per element in a way that stutters with a radius 50 realm (about 1,800 runes). Pinned by `SigilLayoutTest.budget` and building layouts once per sync, not per frame.

---

### Task 1: CameraPull and WardLook (pure)

**Files:**
- Create: `src/main/java/com/pocketdimensions/client/ward/CameraPull.java`, `src/main/java/com/pocketdimensions/client/ward/WardLook.java`
- Test: `src/test/java/com/pocketdimensions/client/ward/CameraPullTest.java`, `src/test/java/com/pocketdimensions/client/ward/WardLookTest.java`

**Interfaces:**
- Produces: `CameraPull.reach(float renderDistanceBlocks)` (double); `CameraPull.factor(double distance, double reach)` (double).
- Produces: `WardLook.colour(int siege)` (int RGB); `WardLook.brightness(boolean warded, float starBrightness)` (float 0..1); `WardLook.shown(int index, int siege, boolean breachOpen, long tick)` (boolean); `WardLook.turns(int siege)` (boolean); `WardLook.pulse(double radius, double ticks)` (float 0..1). Siege values are `WorldCoreBlockEntity.STATE_*`: 0 normal, 1 breaching, 2 breaking, 3 anchor lost.

- [ ] **Step 1: Write the failing tests**

`CameraPullTest.java`:

```java
package com.pocketdimensions.client.ward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pulling the sigil toward the camera: inside the fog-free zone, and the picture never changes. */
class CameraPullTest {

    @Test
    void reachStaysInsideTheFogFreeZone() {
        assertEquals(96, CameraPull.reach(12 * 16), 1e-9, "default 12 chunks: fog starts at 173, capped at 96");
        assertEquals(0.9 * 28, CameraPull.reach(2 * 16), 1e-9, "2 chunks: fog starts at 28");
        assertEquals(96, CameraPull.reach(32 * 16), 1e-9);
    }

    @Test
    void nothingWithinReachMoves() {
        assertEquals(1, CameraPull.factor(50, 96), 1e-12);
        assertEquals(1, CameraPull.factor(96, 96), 1e-12);
    }

    @Test
    void furtherThanReachIsPulledToReach() {
        assertEquals(96.0 / 300, CameraPull.factor(300, 96), 1e-12);
        assertEquals(96, 300 * CameraPull.factor(300, 96), 1e-9);
    }

    @Test
    void aPulledPointLandsOnTheSameSpotOnScreen() {
        // a camera looking along +z: a point at (x, y, z) shows at (x / z, y / z)
        double x = 30, y = 270, z = 400, d = Math.sqrt(x * x + y * y + z * z);
        double k = CameraPull.factor(d, 96);
        assertEquals(x / z, (x * k) / (z * k), 1e-12);
        assertEquals(y / z, (y * k) / (z * k), 1e-12);
    }
}
```

`WardLookTest.java`:

```java
package com.pocketdimensions.client.ward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** How the ward looks for each siege state, with and without lapis, by day and by night. */
class WardLookTest {

    @Test
    void coloursFollowTheSiege() {
        assertEquals(0x8CEBFF, WardLook.colour(0));
        assertEquals(0xFF5ADC, WardLook.colour(1));
        assertEquals(0xFF5028, WardLook.colour(2));
        int lost = WardLook.colour(3);
        assertTrue((lost >> 16 & 0xFF) > (lost & 0xFF), "lost is red");
        assertTrue((lost >> 16 & 0xFF) < 0xFF, "and dim");
    }

    @Test
    void aWardedRealmShinesBrighterThanAStarvingOne() {
        assertTrue(WardLook.brightness(true, 0.5f) > WardLook.brightness(false, 0.5f));
        assertEquals(1f, WardLook.brightness(true, 0.5f), 1e-6, "full at night with lapis");
    }

    @Test
    void byDayItIsFaintButNeverGone() {
        float day = WardLook.brightness(true, 0f);
        assertTrue(day >= 0.25f && day < 0.5f, "day " + day);
    }

    @Test
    void atPeaceEverythingShowsAndTurns() {
        for (int i = 0; i < 200; i++) assertTrue(WardLook.shown(i, 0, false, 1234));
        assertTrue(WardLook.turns(0));
    }

    @Test
    void anOpenBreachCracksTheWard() {
        int hidden = 0;
        for (int i = 0; i < 400; i++) if (!WardLook.shown(i, 1, true, 0)) hidden++;
        assertTrue(hidden > 40 && hidden < 200, "hidden " + hidden);
    }

    @Test
    void aLostAnchorBreaksItAndStopsItTurning() {
        int hidden = 0;
        for (int i = 0; i < 400; i++) if (!WardLook.shown(i, 3, false, 0)) hidden++;
        assertTrue(hidden > 100, "hidden " + hidden);
        assertFalse(WardLook.turns(3));
    }

    @Test
    void pulsesRunOutward() {
        // a pulse at radius r and time t is at radius r + speed * dt later
        float now = WardLook.pulse(100, 0);
        float later = WardLook.pulse(100 + WardLook.PULSE_SPEED * 10, 10);
        assertEquals(now, later, 1e-5);
        for (int r = 0; r < 500; r += 7) { float p = WardLook.pulse(r, 3.5); assertTrue(p >= 0 && p <= 1); }
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew.bat test --tests "com.pocketdimensions.client.ward.*"`
Expected: FAIL, "cannot find symbol: class CameraPull" / "class WardLook".

- [ ] **Step 3: Write minimal implementation**

`CameraPull.java`:

```java
package com.pocketdimensions.client.ward;

import net.minecraft.util.Mth;

/**
 * The ward's sigil hangs hundreds of blocks up, past where the game's fog and far plane reach. Each piece is drawn
 * pulled toward the camera instead, scaled about it by one factor: something twice as close and half as big covers
 * exactly the same pixels, so the picture is unchanged while it sits inside the fog-free zone. Plain logic, unit tested.
 */
public final class CameraPull {

    private static final double MAX_REACH = 96;

    private CameraPull() {}

    /** How far from the camera pulled pieces are put: inside the fog-free zone, never more than 96 blocks. */
    public static double reach(float renderDistanceBlocks) {
        double fogStart = renderDistanceBlocks - Mth.clamp(renderDistanceBlocks / 10f, 4f, 64f);
        return Math.min(MAX_REACH, 0.9 * fogStart);
    }

    /** The scale about the camera for a piece `distance` away: 1 within reach, else what brings it to reach. */
    public static double factor(double distance, double reach) {
        return distance <= reach ? 1 : reach / distance;
    }
}
```

`WardLook.java`:

```java
package com.pocketdimensions.client.ward;

/**
 * How the ward's sigil looks: its colour for the siege (the World Core beam's colours), its brightness (the Core's
 * lapis, day and night), which pieces show (an open breach cracks it, a lost anchor breaks it) and the pulses running
 * out along its spokes. Plain logic, unit tested; WardSigilRenderer draws with it.
 */
public final class WardLook {

    public static final int NORMAL = 0, BREACHING = 1, BREAKING = 2, LOST = 3;
    /** Blocks per tick a pulse runs outward along the spokes. */
    public static final double PULSE_SPEED = 2.0;
    private static final double PULSE_GAP = 160;           // blocks between pulses

    private WardLook() {}

    public static int colour(int siege) {
        return switch (siege) {
            case BREACHING -> 0xFF5ADC;
            case BREAKING -> 0xFF5028;
            case LOST -> 0x8A2A1A;
            default -> 0x8CEBFF;
        };
    }

    /** 0..1: full with lapis at night, about half without; by day a quarter of that or more. starBrightness is 0..0.5. */
    public static float brightness(boolean warded, float starBrightness) {
        float night = Math.max(0f, Math.min(1f, starBrightness * 2f));
        return (warded ? 1f : 0.55f) * (0.3f + 0.7f * night);
    }

    /** Whether piece `index` shows: all at peace; a quarter missing (and some flickering) once breached; 40% when lost. */
    public static boolean shown(int index, int siege, boolean breachOpen, long tick) {
        double h = hash(index);
        if (siege == LOST) return h >= 0.4;
        if (breachOpen) {
            if (h < 0.25) return false;
            if (h < 0.35) return hash(index * 31 + (int) (tick / 4)) > 0.5;      // flickering at the cracks
        }
        return true;
    }

    /** Whether the rings turn: not once the anchor is lost. */
    public static boolean turns(int siege) { return siege != LOST; }

    /** 0..1 brightness boost of a pulse at `radius` blocks out, at `ticks`. */
    public static float pulse(double radius, double ticks) {
        double phase = ((radius - ticks * PULSE_SPEED) % PULSE_GAP + PULSE_GAP) % PULSE_GAP / PULSE_GAP;   // 0..1
        double p = Math.max(0, 1 - Math.abs(phase - 0.5) * 8);                                             // a narrow band
        return (float) p;
    }

    private static double hash(int i) {
        int n = i * 374761393;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) >>> 0 & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }
}
```

- [ ] **Step 4: Run them to verify they pass**

Run: `./gradlew.bat test --tests "com.pocketdimensions.client.ward.*"`
Expected: PASS, 11 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/pocketdimensions/client/ward/CameraPull.java src/main/java/com/pocketdimensions/client/ward/WardLook.java src/test/java/com/pocketdimensions/client/ward
git commit -m "Ward sigil logic: the pull toward the camera, and the ward's colours, brightness, cracks and pulses"
```

---

### Task 2: SigilLayout (pure)

**Files:**
- Create: `src/main/java/com/pocketdimensions/client/ward/SigilLayout.java`
- Test: `src/test/java/com/pocketdimensions/client/ward/SigilLayoutTest.java`

**Interfaces:**
- Produces: `SigilLayout.PLANE_Y` (int 340); `enum Kind { SEAL, RING, SPOKE, BAND }`; `record Rune(Kind kind, int ring, double x, double z, double size, double angle, int glyph, double radius)`; `record Line(Kind kind, int ring, double x1, double z1, double x2, double z2, double width)`; `record Sigil(double cx, double cz, List<Rune> runes, List<Line> lines)`; `static Sigil build(double cx, double cz, int minX, int minZ, int maxX, int maxZ)`; `static double turn(int ring, double ticks)` (radians the ring has turned).

- [ ] **Step 1: Write the failing test**

```java
package com.pocketdimensions.client.ward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Where the sigil's runes and lines go: centred on the Core, inside the plot, within budget. */
class SigilLayoutTest {

    // radius 25: 49 chunks = 784 blocks
    private static final int MIN = 0, MAX = 784;

    private static void allInside(SigilLayout.Sigil s, int min, int max) {
        for (SigilLayout.Rune r : s.runes()) {
            double reach = r.size() / 2 + Math.hypot(r.x() - s.cx(), r.z() - s.cz()) * 0;   // rotation keeps distance
            double dist = Math.hypot(r.x() - s.cx(), r.z() - s.cz());
            double toEdge = Math.min(Math.min(s.cx() - min, max - s.cx()), Math.min(s.cz() - min, max - s.cz()));
            if (r.kind() == SigilLayout.Kind.BAND) {
                assertTrue(r.x() - r.size() / 2 >= min && r.x() + r.size() / 2 <= max, "band x " + r);
                assertTrue(r.z() - r.size() / 2 >= min && r.z() + r.size() / 2 <= max, "band z " + r);
            } else {
                assertTrue(dist + reach + r.size() / 2 <= toEdge, "turning piece stays inside at any angle " + r);
            }
        }
        for (SigilLayout.Line l : s.lines()) {
            for (double[] p : new double[][]{{l.x1(), l.z1()}, {l.x2(), l.z2()}}) {
                assertTrue(p[0] >= min && p[0] <= max && p[1] >= min && p[1] <= max, "line " + l);
            }
        }
    }

    @Test
    void aCentredCoreFitsInsideThePlot() {
        allInside(SigilLayout.build(392, 392, MIN, MIN, MAX, MAX), MIN, MAX);
    }

    @Test
    void aCoreFarOffCentreStillFits() {
        allInside(SigilLayout.build(392 + 128, 392 - 128, MIN, MIN, MAX, MAX), MIN, MAX);
    }

    @Test
    void aSmallRealmFits() {
        allInside(SigilLayout.build(40, 40, 0, 0, 80, 80), 0, 80);        // radius 3
    }

    @Test
    void ithasASealFourRingsEightSpokesAndABand() {
        SigilLayout.Sigil s = SigilLayout.build(392, 392, MIN, MIN, MAX, MAX);
        assertTrue(s.runes().stream().anyMatch(r -> r.kind() == SigilLayout.Kind.SEAL));
        assertEquals(4, s.runes().stream().filter(r -> r.kind() == SigilLayout.Kind.RING).mapToInt(SigilLayout.Rune::ring).distinct().count());
        assertTrue(s.runes().stream().anyMatch(r -> r.kind() == SigilLayout.Kind.BAND));
        assertEquals(8, s.lines().stream().filter(l -> l.kind() == SigilLayout.Kind.SPOKE).map(l -> Math.round(Math.toDegrees(Math.atan2(l.z2() - l.z1(), l.x2() - l.x1())))).distinct().count());
        for (SigilLayout.Rune r : s.runes()) assertTrue(r.glyph() >= 0 && r.glyph() < 6);
    }

    @Test
    void runesOnARingDoNotOverlap() {
        SigilLayout.Sigil s = SigilLayout.build(392, 392, MIN, MIN, MAX, MAX);
        var ring0 = s.runes().stream().filter(r -> r.kind() == SigilLayout.Kind.RING && r.ring() == 0).toList();
        double r = ring0.get(0).radius(), size = ring0.get(0).size();
        assertTrue(2 * Math.PI * r / ring0.size() >= size * 1.2, "spacing");
    }

    @Test
    void budget() {
        SigilLayout.Sigil big = SigilLayout.build(792, 792, 0, 0, 1584, 1584);            // radius 50
        assertTrue(big.runes().size() < 2500, "runes " + big.runes().size());
        assertTrue(big.lines().size() < 1500, "lines " + big.lines().size());
    }

    @Test
    void ringsTurnInAlternatingDirections() {
        assertTrue(SigilLayout.turn(0, 100) > 0);
        assertTrue(SigilLayout.turn(1, 100) < 0);
        assertEquals(0, SigilLayout.turn(0, 0), 1e-12);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew.bat test --tests com.pocketdimensions.client.ward.SigilLayoutTest`
Expected: FAIL, "cannot find symbol: class SigilLayout".

- [ ] **Step 3: Write the implementation**

```java
package com.pocketdimensions.client.ward;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the ward's sigil puts its pieces, flat at PLANE_Y over the realm: an inner seal round the top of the Core's
 * beam, four rings of runes centred on the Core (the outermost inside the nearest edge, wherever the Core stands), a
 * square band of runes just inside the Veil, and eight spokes from the seal out to the band. Rings are drawn turned by
 * turn(ring, ticks). Plain logic, unit tested; built once per ward sync, drawn every frame.
 */
public final class SigilLayout {

    public static final int PLANE_Y = 340;
    private static final double EDGE_MARGIN = 6, SEAL_RADIUS = 20, RING_TURN = 0.0004;     // radians per tick

    public enum Kind { SEAL, RING, SPOKE, BAND }
    public record Rune(Kind kind, int ring, double x, double z, double size, double angle, int glyph, double radius) {}
    public record Line(Kind kind, int ring, double x1, double z1, double x2, double z2, double width) {}
    public record Sigil(double cx, double cz, List<Rune> runes, List<Line> lines) {}

    private SigilLayout() {}

    public static Sigil build(double cx, double cz, int minX, int minZ, int maxX, int maxZ) {
        List<Rune> runes = new ArrayList<>();
        List<Line> lines = new ArrayList<>();
        double edge = Math.min(Math.min(cx - minX, maxX - cx), Math.min(cz - minZ, maxZ - cz)) - EDGE_MARGIN;
        double size = Math.max(3, Math.min(16, edge / 24));
        int glyph = 0;

        // the seal: eight runes round the beam's top, and a circle
        double seal = Math.min(SEAL_RADIUS, edge * 0.12);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            runes.add(new Rune(Kind.SEAL, -1, cx + Math.cos(a) * seal, cz + Math.sin(a) * seal, size * 0.7, a, glyph++ % 6, seal));
        }
        circle(lines, Kind.SEAL, -1, cx, cz, seal + size * 0.6, size * 0.12, 48);

        // four turning rings, the outermost clear of the nearest edge at any angle
        double outer = edge - size;
        for (int ring = 0; ring < 4; ring++) {
            double r = outer * (0.28 + 0.24 * ring);
            int count = (int) Math.floor(2 * Math.PI * r / (size * 1.6));
            for (int i = 0; i < count; i++) {
                double a = 2 * Math.PI * i / count;
                runes.add(new Rune(Kind.RING, ring, cx + Math.cos(a) * r, cz + Math.sin(a) * r, size, a + Math.PI / 2, glyph++ % 6, r));
            }
            circle(lines, Kind.RING, ring, cx, cz, r - size * 0.75, size * 0.08, 96);
            circle(lines, Kind.RING, ring, cx, cz, r + size * 0.75, size * 0.08, 96);
        }

        // the band: runes along a square just inside the plot
        double bx0 = minX + EDGE_MARGIN + size / 2, bx1 = maxX - EDGE_MARGIN - size / 2;
        double bz0 = minZ + EDGE_MARGIN + size / 2, bz1 = maxZ - EDGE_MARGIN - size / 2;
        double step = size * 1.4;
        for (double x = bx0; x <= bx1 + 1e-9; x += step) {
            runes.add(new Rune(Kind.BAND, -2, x, bz0, size, 0, glyph++ % 6, 0));
            runes.add(new Rune(Kind.BAND, -2, x, bz1, size, Math.PI, glyph++ % 6, 0));
        }
        for (double z = bz0 + step; z <= bz1 - step + 1e-9; z += step) {
            runes.add(new Rune(Kind.BAND, -2, bx0, z, size, -Math.PI / 2, glyph++ % 6, 0));
            runes.add(new Rune(Kind.BAND, -2, bx1, z, size, Math.PI / 2, glyph++ % 6, 0));
        }

        // eight spokes from the seal to the band, in short segments so each is pulled on its own
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4, dx = Math.cos(a), dz = Math.sin(a);
            double to = toSquare(cx, cz, dx, dz, bx0, bz0, bx1, bz1) - size;
            double seg = Math.max(4, size);
            for (double r = seal + size; r + seg <= to; r += seg)
                lines.add(new Line(Kind.SPOKE, -3, cx + dx * r, cz + dz * r, cx + dx * (r + seg), cz + dz * (r + seg), size * 0.1));
        }
        return new Sigil(cx, cz, runes, lines);
    }

    /** Radians ring `ring` has turned after `ticks`: even rings one way, odd rings the other, inner rings faster. */
    public static double turn(int ring, double ticks) {
        double dir = ring % 2 == 0 ? 1 : -1;
        return dir * RING_TURN * ticks / (1 + ring * 0.5);
    }

    private static void circle(List<Line> out, Kind kind, int ring, double cx, double cz, double r, double width, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments, a1 = 2 * Math.PI * (i + 1) / segments;
            out.add(new Line(kind, ring, cx + Math.cos(a0) * r, cz + Math.sin(a0) * r, cx + Math.cos(a1) * r, cz + Math.sin(a1) * r, width));
        }
    }

    /** Distance from (cx, cz) along (dx, dz) to the square [x0, x1] x [z0, z1]. */
    private static double toSquare(double cx, double cz, double dx, double dz, double x0, double z0, double x1, double z1) {
        double tx = dx > 1e-9 ? (x1 - cx) / dx : dx < -1e-9 ? (x0 - cx) / dx : Double.MAX_VALUE;
        double tz = dz > 1e-9 ? (z1 - cz) / dz : dz < -1e-9 ? (z0 - cz) / dz : Double.MAX_VALUE;
        return Math.min(tx, tz);
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew.bat test --tests com.pocketdimensions.client.ward.SigilLayoutTest`
Expected: PASS, 7 tests. If `allInside` fails for the small realm, the size floor (3) or the seal radius is too large for an 80-block plot: shrink the factor in `seal = Math.min(SEAL_RADIUS, edge * 0.12)`, not the test.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/pocketdimensions/client/ward/SigilLayout.java src/test/java/com/pocketdimensions/client/ward/SigilLayoutTest.java
git commit -m "SigilLayout: the ward sigil's seal, four turning rings, eight spokes and square band, centred on the Core inside the plot"
```

---

### Task 3: Ward sync (server to client)

**Files:**
- Create: `src/main/java/com/pocketdimensions/network/WardSyncS2C.java`, `src/main/java/com/pocketdimensions/event/WardSync.java`, `src/main/java/com/pocketdimensions/client/ward/WardView.java`
- Modify: `src/main/java/com/pocketdimensions/network/ModNetworking.java` (register), `src/main/java/com/pocketdimensions/PocketDimensionsMod.java` (create `WardSync` beside the other handlers)

**Interfaces:**
- Consumes: `SigilLayout.build` (Task 2).
- Produces: `record WardSyncS2C(boolean present, int coreX, int coreY, int coreZ, int minX, int minZ, int maxX, int maxZ, int siege, boolean breachOpen, boolean warded)` with `encode`, `decode`, `handle`, `static WardSyncS2C absent()`; `WardView.current()` returning `@Nullable WardView.State` (`record State(WardSyncS2C sync, SigilLayout.Sigil sigil)`), valid only in the level it arrived in.

- [ ] **Step 1: Write a failing round-trip test**

`src/test/java/com/pocketdimensions/network/WardSyncS2CTest.java`:

```java
package com.pocketdimensions.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The ward's state crosses the network unchanged. */
class WardSyncS2CTest {

    @Test
    void roundTrip() {
        WardSyncS2C m = new WardSyncS2C(true, 392, 71, -392, 0, -784, 784, 0, 2, true, false);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        WardSyncS2C.encode(m, buf);
        assertEquals(m, WardSyncS2C.decode(buf));
    }

    @Test
    void absentRoundTrip() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        WardSyncS2C.encode(WardSyncS2C.absent(), buf);
        assertFalse(WardSyncS2C.decode(buf).present());
    }
}
```

Run: `./gradlew.bat test --tests com.pocketdimensions.network.WardSyncS2CTest`
Expected: FAIL, "cannot find symbol: class WardSyncS2C". (If the test then fails at runtime because Minecraft classes can't load in plain JUnit, rule it in the ledger, delete this test, and rely on the in-game check: the encoder is symmetric field by field.)

- [ ] **Step 2: The packet**

```java
package com.pocketdimensions.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * The ward of the realm the player is in, for the sigil overhead: the Core's position, the plot's bounds, the siege
 * state (WorldCoreBlockEntity.STATE_*), whether a breach stands open and whether the Core holds lapis. Sent by
 * event/WardSync when any of it changes; `present` false when the player is in no realm.
 */
public record WardSyncS2C(boolean present, int coreX, int coreY, int coreZ, int minX, int minZ, int maxX, int maxZ,
                          int siege, boolean breachOpen, boolean warded) {

    public static WardSyncS2C absent() { return new WardSyncS2C(false, 0, 0, 0, 0, 0, 0, 0, 0, false, false); }

    public static void encode(WardSyncS2C m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.present);
        if (!m.present) return;
        buf.writeVarInt(m.coreX); buf.writeVarInt(m.coreY); buf.writeVarInt(m.coreZ);
        buf.writeVarInt(m.minX); buf.writeVarInt(m.minZ); buf.writeVarInt(m.maxX); buf.writeVarInt(m.maxZ);
        buf.writeVarInt(m.siege); buf.writeBoolean(m.breachOpen); buf.writeBoolean(m.warded);
    }

    public static WardSyncS2C decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return absent();
        return new WardSyncS2C(true, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(WardSyncS2C m, CustomPayloadEvent.Context ctx) {
        com.pocketdimensions.client.ward.WardView.receive(m);
    }
}
```

Register it in `ModNetworking.register()` after `SiegeBarS2C`:

```java
        CHANNEL.messageBuilder(WardSyncS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WardSyncS2C::encode).decoder(WardSyncS2C::decode).consumerMainThread(WardSyncS2C::handle).add();
```

- [ ] **Step 3: Run the round-trip test**

Run: `./gradlew.bat test --tests com.pocketdimensions.network.WardSyncS2CTest`
Expected: compile error only for `WardView` until Step 5; write Step 5's `WardView` first if needed, then: PASS, 2 tests.

- [ ] **Step 4: The server side**

`src/main/java/com/pocketdimensions/event/WardSync.java`:

```java
package com.pocketdimensions.event;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.blockentity.WorldCoreBlockEntity;
import com.pocketdimensions.manager.RealmManager;
import com.pocketdimensions.network.ModNetworking;
import com.pocketdimensions.network.WardSyncS2C;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells each player in a realm the state of that realm's ward (for the sigil overhead), once a second if it changed.
 * The siege state and lapis come from the World Core when its chunk is loaded, else from the last time it was; never
 * seen since the server started, the realm is at peace unless its anchor is gone.
 */
public class WardSync {

    private record Known(int siege, boolean warded) {}

    private final Map<UUID, WardSyncS2C> sent = new HashMap<>();
    private final Map<UUID, Known> known = new HashMap<>();
    private final Map<UUID, Boolean> breach = new HashMap<>();

    public WardSync() {
        TickEvent.PlayerTickEvent.Post.BUS.addListener(this::onPlayerTick);
    }

    private void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (event.side() != LogicalSide.SERVER || !(event.player() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().equals(PocketDimensionsMod.REALM_DIM)) {
            sent.remove(player.getUUID());          // re-sent on the next arrival
            return;
        }
        if ((player.level().getGameTime() + player.getId()) % 20 != 0) return;
        WardSyncS2C now = stateFor(player);
        if (now.equals(sent.get(player.getUUID()))) return;
        sent.put(player.getUUID(), now);
        ModNetworking.getChannel().send(now, PacketDistributor.PLAYER.with(player));
    }

    private WardSyncS2C stateFor(ServerPlayer player) {
        ServerLevel realm = (ServerLevel) player.level();
        MinecraftServer server = realm.getServer();
        RealmManager mgr = RealmManager.get(server);
        RealmManager.PlayerRealmInfo info = mgr.getPlayerRealmInfo(player.getUUID());
        if (info == null) return WardSyncS2C.absent();
        UUID owner = info.realmOwner;
        BlockPos core = mgr.getWorldCorePos(owner);
        if (core == null) return WardSyncS2C.absent();
        if (realm.isLoaded(core) && realm.getBlockEntity(core) instanceof WorldCoreBlockEntity be)
            known.put(owner, new Known(be.getSiegeState(), be.hasDefenseFuel()));
        Known k = known.getOrDefault(owner, new Known(
                mgr.getAnchorLocation(owner).isPresent() ? WorldCoreBlockEntity.STATE_NORMAL : WorldCoreBlockEntity.STATE_ANCHOR_LOST, false));
        int[] b = mgr.getRealmBounds(owner);
        return new WardSyncS2C(true, core.getX(), core.getY(), core.getZ(), b[0], b[1], b[2], b[3], k.siege(), breachOpen(server, mgr, owner), k.warded());
    }

    /** Whether a breach stands open, read only while the anchor's chunk is loaded (checking must never load it). */
    private boolean breachOpen(MinecraftServer server, RealmManager mgr, UUID owner) {
        var anchor = mgr.getAnchorLocation(owner);
        if (anchor.isEmpty()) { breach.remove(owner); return false; }
        ServerLevel level = server.getLevel(anchor.get().getKey());
        if (level != null && level.isLoaded(anchor.get().getValue())) breach.put(owner, mgr.isBreachOpen(server, owner));
        return breach.getOrDefault(owner, false);
    }
}
```

In `PocketDimensionsMod`'s constructor, where `RealmEventHandler` is created, add `new com.pocketdimensions.event.WardSync();` beside it (check the exact line with `grep -n "new RealmEventHandler" src/main/java/com/pocketdimensions/PocketDimensionsMod.java`; if handlers are created differently, follow that pattern).

- [ ] **Step 5: The client side**

`src/main/java/com/pocketdimensions/client/ward/WardView.java`:

```java
package com.pocketdimensions.client.ward;

import com.pocketdimensions.network.WardSyncS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The ward of the realm the player is in, as last sent by the server, with its sigil laid out once. It belongs to the
 * level it arrived in: after any dimension change it is forgotten until the next sync.
 */
public final class WardView {

    public record State(WardSyncS2C sync, SigilLayout.Sigil sigil) {}

    private static @Nullable State state;
    private static @Nullable Level level;

    private WardView() {}

    public static void receive(WardSyncS2C m) {
        Minecraft mc = Minecraft.getInstance();
        if (!m.present()) { state = null; return; }
        SigilLayout.Sigil sigil = state != null && sameShape(state.sync(), m) ? state.sigil()
                : SigilLayout.build(m.coreX() + 0.5, m.coreZ() + 0.5, m.minX(), m.minZ(), m.maxX(), m.maxZ());
        state = new State(m, sigil);
        level = mc.level;
    }

    public static @Nullable State current() {
        Minecraft mc = Minecraft.getInstance();
        if (state == null || mc.level == null || mc.level != level) return null;
        return state;
    }

    private static boolean sameShape(WardSyncS2C a, WardSyncS2C b) {
        return a.coreX() == b.coreX() && a.coreZ() == b.coreZ() && a.minX() == b.minX() && a.minZ() == b.minZ()
                && a.maxX() == b.maxX() && a.maxZ() == b.maxZ();
    }
}
```

- [ ] **Step 6: Run the whole suite**

Stop `runServer` / `runClient` if running. Run: `./gradlew.bat test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/pocketdimensions/network src/main/java/com/pocketdimensions/event/WardSync.java src/main/java/com/pocketdimensions/client/ward/WardView.java src/main/java/com/pocketdimensions/PocketDimensionsMod.java src/test/java/com/pocketdimensions/network
git commit -m "Ward sync: each player in a realm is told its ward's state (core, bounds, siege, breach, lapis) when it changes"
```

---

### Task 4: The sigil renderer (replacing the spike)

**Files:**
- Modify: `src/main/java/com/pocketdimensions/client/ward/WardSigilRenderer.java` (replace the spike), `src/main/java/com/pocketdimensions/mixin/LevelRendererMixin.java` (pass the render state)
- Create: `tools/anchor/sigil.mjs`, `tools/anchor/test/sigil.test.mjs`; modify `tools/anchor/build.mjs`
- Generated: `src/main/resources/assets/pocketdimensions/textures/misc/ward_line.png`

**Interfaces:**
- Consumes: `CameraPull.reach`, `CameraPull.factor`, `WardLook.*` (Task 1); `SigilLayout.*` (Task 2); `WardView.current()` (Task 3).
- Produces: `WardSigilRenderer.addPass(FrameGraphBuilder, LevelTargetBundle, LevelRenderState)`.

- [ ] **Step 1: The line texture (test first)**

`tools/anchor/test/sigil.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { lineTexture } from '../sigil.mjs';

test('the line is white, brightest along its middle and fading to nothing at its sides', () => {
  const rgba = lineTexture();
  assert.equal(rgba.length, 16 * 16 * 4);
  const alpha = y => rgba[(y * 16 + 8) * 4 + 3];
  assert.ok(alpha(8) > 200, 'middle');
  assert.ok(alpha(0) < 20 && alpha(15) < 20, 'sides');
  assert.ok(alpha(4) < alpha(7), 'fades outward');
  for (let i = 0; i < rgba.length; i += 4) assert.equal(rgba[i], 255);
});
```

Run: `node --test tools/anchor/test/sigil.test.mjs` . Expected: FAIL, "Cannot find module '../sigil.mjs'".

`tools/anchor/sigil.mjs`:

```js
// The ward sigil's lines and beam: a soft white stroke, tinted by the renderer with the siege colour.

/** 16 x 16 RGBA: white, its alpha a soft band across the rows (brightest on rows 7 and 8). */
export function lineTexture() {
  const rgba = new Uint8Array(16 * 16 * 4);
  for (let y = 0; y < 16; y++) {
    const d = Math.abs(y - 7.5) / 7.5;                         // 0 at the middle, 1 at the edges
    const a = Math.round(255 * Math.max(0, 1 - d) ** 2.2);
    for (let x = 0; x < 16; x++) rgba.set([255, 255, 255, a], (y * 16 + x) * 4);
  }
  return rgba;
}
```

In `tools/anchor/build.mjs` add `import { lineTexture } from './sigil.mjs';` beside the Veil import and at the end:

```js
// The ward sigil's line and beam stroke
write('textures/misc/ward_line.png', encodePNG(16, 16, lineTexture()));
```

Run: `node --test tools/anchor/test/sigil.test.mjs && node tools/anchor/build.mjs && git status --short`
Expected: test PASS; only `textures/misc/ward_line.png` new among generated files.

- [ ] **Step 2: Pass the render state through the mixin**

In `LevelRendererMixin`, change the call to `WardSigilRenderer.addPass(builder, targets, state);`.

- [ ] **Step 3: The renderer**

Replace `WardSigilRenderer.java` with:

```java
package com.pocketdimensions.client.ward;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.network.WardSyncS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the ward's sigil over the realm (SigilLayout), in a frame pass of its own just before the terrain (added by
 * LevelRendererMixin), so the world is drawn over it. Every piece is pulled toward the camera by one factor (CameraPull),
 * which leaves its picture unchanged while keeping it inside the fog-free zone, so the sigil looks the same at every
 * render distance. Colour, brightness, cracks and pulses come from WardLook.
 */
public final class WardSigilRenderer {

    private static final Identifier[] RUNE = new Identifier[6], GLOW = new Identifier[6];
    static {
        for (int i = 0; i < 6; i++) {
            RUNE[i] = Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "textures/particle/rune_" + i + ".png");
            GLOW[i] = Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "textures/particle/rune_glow_" + i + ".png");
        }
    }
    private static final Identifier LINE = Identifier.fromNamespaceAndPath(PocketDimensionsMod.MODID, "textures/misc/ward_line.png");

    private WardSigilRenderer() {}

    public static void addPass(FrameGraphBuilder builder, LevelTargetBundle targets, LevelRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(PocketDimensionsMod.REALM_DIM)) return;
        WardView.State ward = WardView.current();
        if (ward == null) return;
        float stars = state.skyRenderState.starBrightness;
        FramePass pass = builder.addPass("pocketdimensions_ward_sigil");
        targets.main = pass.readsAndWrites(targets.main);
        pass.executes(() -> draw(ward, stars));
    }

    /** One frame's drawing context: the camera, the reach, the time and the ward's look. */
    private record Frame(Vec3 cam, double reach, double ticks, long tick, int rgb, float bright, WardSyncS2C sync, boolean turns) {}

    private static void draw(WardView.State ward, float stars) {
        Minecraft mc = Minecraft.getInstance();
        WardSyncS2C s = ward.sync();
        double ticks = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Frame f = new Frame(mc.gameRenderer.getMainCamera().position(),
                CameraPull.reach(mc.options.getEffectiveRenderDistance() * 16f), ticks, (long) ticks,
                WardLook.colour(s.siege()), WardLook.brightness(s.warded(), stars), s, WardLook.turns(s.siege()));
        SigilLayout.Sigil sigil = ward.sigil();

        RenderType line = RenderTypes.eyes(LINE);
        BufferBuilder buf = Tesselator.getInstance().begin(line.mode(), line.format());
        for (int i = 0; i < sigil.lines().size(); i++) lineQuad(buf, f, sigil, sigil.lines().get(i), i);
        beam(buf, f);
        drawIfAny(buf, line);

        for (int pass = 0; pass < 2; pass++) {
            for (int g = 0; g < 6; g++) {
                RenderType type = RenderTypes.eyes(pass == 0 ? GLOW[g] : RUNE[g]);
                BufferBuilder b = Tesselator.getInstance().begin(type.mode(), type.format());
                for (int i = 0; i < sigil.runes().size(); i++) {
                    SigilLayout.Rune r = sigil.runes().get(i);
                    if (r.glyph() == g) runeQuad(b, f, sigil, r, i + 100000, pass == 0);
                }
                drawIfAny(b, type);
            }
        }
    }

    private static void drawIfAny(BufferBuilder b, RenderType type) {
        MeshData mesh = b.build();
        if (mesh != null) try (mesh) { type.draw(mesh); }
    }

    /** (x, z) turned about the sigil's centre by `a` radians. */
    private static double[] turned(SigilLayout.Sigil s, double x, double z, double a) {
        if (a == 0) return new double[]{x, z};
        double dx = x - s.cx(), dz = z - s.cz(), c = Math.cos(a), n = Math.sin(a);
        return new double[]{s.cx() + dx * c - dz * n, s.cz() + dx * n + dz * c};
    }

    private static double turnOf(Frame f, int ring) {
        return ring >= 0 && f.turns() ? SigilLayout.turn(ring, f.ticks()) : 0;
    }

    private static void runeQuad(BufferBuilder b, Frame f, SigilLayout.Sigil s, SigilLayout.Rune r, int index, boolean glow) {
        if (!WardLook.shown(index, f.sync().siege(), f.sync().breachOpen(), f.tick())) return;
        double turn = turnOf(f, r.ring());
        double[] c = turned(s, r.x(), r.z(), turn);
        double half = r.size() / 2 * (glow ? 1.7 : 1.0), a = r.angle() + turn;
        float alpha = f.bright() * (glow ? 0.45f : 0.9f);
        quad(b, f, c[0], c[1], half, half, a, alpha, 1);
    }

    private static void lineQuad(BufferBuilder b, Frame f, SigilLayout.Sigil s, SigilLayout.Line l, int index) {
        if (!WardLook.shown(index, f.sync().siege(), f.sync().breachOpen(), f.tick())) return;
        double turn = turnOf(f, l.ring());
        double[] p = turned(s, l.x1(), l.z1(), turn), q = turned(s, l.x2(), l.z2(), turn);
        double mx = (p[0] + q[0]) / 2, mz = (p[1] + q[1]) / 2;
        double len = Math.hypot(q[0] - p[0], q[1] - p[1]), a = Math.atan2(q[1] - p[1], q[0] - p[0]);
        float boost = l.kind() == SigilLayout.Kind.SPOKE ? WardLook.pulse(Math.hypot(mx - s.cx(), mz - s.cz()), f.ticks()) : 0;
        quad(b, f, mx, mz, len / 2, l.width() * 2, a, f.bright() * (0.6f + 0.4f * boost), 1 + boost);
    }

    /**
     * A flat quad at the sigil's plane centred on (x, z), halfU along angle `a` and halfV across it, pulled toward the
     * camera as a whole (one factor for the whole quad keeps its picture exact, texture included).
     */
    private static void quad(BufferBuilder b, Frame f, double x, double z, double halfU, double halfV, double a, float alpha, float glow) {
        double rx = x - f.cam().x, ry = SigilLayout.PLANE_Y - f.cam().y, rz = z - f.cam().z;
        double k = CameraPull.factor(Math.sqrt(rx * rx + ry * ry + rz * rz), f.reach());
        double ux = Math.cos(a) * halfU, uz = Math.sin(a) * halfU, vx = -Math.sin(a) * halfV, vz = Math.cos(a) * halfV;
        int r = Math.min(255, (int) ((f.rgb() >> 16 & 0xFF) * glow)), g = Math.min(255, (int) ((f.rgb() >> 8 & 0xFF) * glow)),
                bl = Math.min(255, (int) ((f.rgb() & 0xFF) * glow)), al = Math.min(255, (int) (alpha * 255));
        double[][] corners = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
        for (double[] c : corners) {
            float px = (float) ((rx + ux * c[0] + vx * c[1]) * k), py = (float) (ry * k), pz = (float) ((rz + uz * c[0] + vz * c[1]) * k);
            b.addVertex(px, py, pz).setColor(r, g, bl, al).setUv((float) c[2], (float) c[3])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, -1, 0);
        }
    }

    /**
     * The Core's beam above where the vanilla beam shows: two crossed thin quads per 16-block segment, from the Core up
     * to the sigil, drawn only where the Core's own renderer can't (pulled segments, or above its 256-block reach).
     */
    private static void beam(BufferBuilder b, Frame f) {
        WardSyncS2C s = f.sync();
        if (s.siege() == WardLook.LOST) return;
        double x = s.coreX() + 0.5, z = s.coreZ() + 0.5, top = SigilLayout.PLANE_Y, bottom = s.coreY() + 3;
        int r = f.rgb() >> 16 & 0xFF, g = f.rgb() >> 8 & 0xFF, bl = f.rgb() & 0xFF, al = (int) (f.bright() * 200);
        for (double y0 = bottom; y0 < top; y0 += 16) {
            double y1 = Math.min(top, y0 + 16), my = (y0 + y1) / 2;
            double rx = x - f.cam().x, ry = my - f.cam().y, rz = z - f.cam().z;
            double k = CameraPull.factor(Math.sqrt(rx * rx + ry * ry + rz * rz), f.reach());
            if (k >= 1 && y1 <= s.coreY() + 256) continue;             // the Core's own beam is drawn there
            for (int side = 0; side < 2; side++) {
                double ox = side == 0 ? 0.3 : 0, oz = side == 0 ? 0 : 0.3;
                double[][] v = {{-ox, y0, -oz, 0, 1}, {ox, y0, oz, 1, 1}, {ox, y1, oz, 1, 0}, {-ox, y1, -oz, 0, 0}};
                for (double[] c : v) {
                    float px = (float) ((rx + c[0]) * k), py = (float) ((c[1] - f.cam().y) * k), pz = (float) ((rz + c[2]) * k);
                    b.addVertex(px, py, pz).setColor(r, g, bl, al).setUv((float) c[3], (float) c[4])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
                }
            }
        }
    }
}
```

Note: `BufferBuilder.build()` returns null when nothing was added (check: if it doesn't exist under that name, use `buildOrThrow()` guarded by a vertex count you keep yourself).

- [ ] **Step 4: Compile and run the suite**

Stop `runServer` / `runClient` if running. Run: `./gradlew.bat test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Check it in game**

Start server and client, enter the realm.
Expected:
- The sigil overhead, centred over the Core; the beam rises into its seal. Rings turn slowly, alternating directions; pulses run out along the spokes.
- Walking toward the edge, the sigil's centre swings behind you and the band runs along the top of the Veil.
- Identical at render distance 2, 12 and 32. Clouds, trees and builds cover it.
- Day: faint. Night: glowing. Remove the Core's lapis: dimmer.
- `/pd test` is not needed: place a World Breacher (pink), complete it (cracks), an Anchor Breaker (red), let it finish (dim, broken, still).
- Leave through the Core: no sigil in the overworld; enter again: it is back.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/pocketdimensions/client/ward src/main/java/com/pocketdimensions/mixin/LevelRendererMixin.java tools/anchor/sigil.mjs tools/anchor/test/sigil.test.mjs tools/anchor/build.mjs src/main/resources/assets/pocketdimensions/textures/misc/ward_line.png
git commit -m "The ward's sigil: seal, turning rings, spokes with pulses and a band along the Veil, over the Core, in the siege's colours, pulled toward the camera so it looks the same at any render distance"
```

---

### Task 5: Docs

- [ ] **Step 1:** `README.md` (Realms: a paragraph on the sigil: what each colour and dimming means), `pocket-worlds.md` (the ward section: the sigil is now built, Part C still planned), `CHANGELOG.md` (one line under Realms), `PRD.md` (RL-022 DONE with the classes), `CLAUDE.md` (mixins: `pocketdimensions.mixins.json`, `mixin/LevelRendererMixin`, the `-mixin.config` run arg and `MixinConfigs` manifest; `client/ward/*`; `event/WardSync`; `network/WardSyncS2C`), `docs/friend-test.md` (the sigil's states from two players' views).
- [ ] **Step 2:** `grep -n "—" README.md PRD.md pocket-worlds.md CHANGELOG.md` gives nothing; commit `"Docs: the ward's sigil"`.
