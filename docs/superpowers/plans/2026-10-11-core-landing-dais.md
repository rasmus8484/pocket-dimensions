# The Core's Landing (Stone Dais) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every new realm's World Core lands at the plot's exact centre on a 9-wide Stone Dais of heartstone with six
vines of glowing aurora stone, with the sky cleared above. The ward's conduit of runes is removed.

**Architecture:**
- A pure, unit-tested `LandingDais` plans the dais (floor height, every block and its kind) from a plot index and the
  ground heights. `RealmManager` reads the ground, clears the open cylinder, sets the blocks and places the Core.
- The two new blocks are plain blocks.
- Aurora stone's flowing veins are a three-element block model: the heartstone texture, then two animated overlays
  with `light_emission`. Their textures come from a new Node generator, `tools/anchor/dais.mjs`, ported from the
  approved studies page.

**Tech Stack:** Java 21, Forge 1.21.11 (61.1.0), JUnit 5; Node (ES modules, `node:test`) for the texture generator.

**Spec:** `docs/superpowers/specs/2026-10-11-core-landing-dais-design.md`

## Global Constraints

**Writing**
- Never use em dashes, anywhere: code, comments, docs or commit messages. A spaced en dash (" – ") as a substitute is
  banned too. Use a colon, comma, full stop or parentheses.
- Commit messages: no `Co-Authored-By` line (user preference). Style: one descriptive sentence, as in `git log`.
- Comments match the codebase: plain English, storytelling-light, no jargon.

**Gradle**
- **Never run Gradle while the dev server or client runs:** it crashes them.
  - Before the first Gradle command, stop the background tasks `bifh18u7j` (runServer) and `bk1enetsu` (runClient)
    with TaskStop, if they are still running.
  - Gradle test command: `./gradlew.bat test --tests "<class>"`. Whole suite: `./gradlew.bat test`.

**The dais**
- Block ids `heartstone` and `aurora_stone`; names "Heartstone" and "Aurora Stone".
- Strength 1.5 / 6, pickaxe, drops itself, `SoundType.STONE`.
- Aurora stone gives light level 7.
- The dais is 9 across (`RADIUS = 4.5`), with an underside 3 deep (`DEPTH = 3`) and 6 vines. The open cylinder
  radius is 5.5.
- A dais is built only by `ensureGenerated` for a realm not yet generated: never on relinking to the same realm.
  Existing realms are not touched.

**Aurora stone's look**
- One vein pattern on every block.
- The aurora takes 32 s to go round, with a pulse every 2 s.
- The strip is 128 frames, `frametime` 5, `interpolate: true`.
- The vein overlay has light emission 15; the rim overlay has light emission 6.

## Review Focus

1. **A centre in a tall forest or jungle.** The floor must sit on the forest floor, never in the canopy: leaves and
   logs are not ground. (Task 4, `firstAirSkipsWhatIsNotGround`, plus the in-game check in Task 7.)
2. **A centre on a cliff or steep slope.** The floor lies between the lowest and the highest ground under it, and the
   open cylinder cuts the hill away above the floor, so the Core is never buried. (Task 1,
   `theFloorLiesBetweenTheLowestAndHighestGround`; Task 7.)
3. **A centre on open ocean.** Water is ground, so the floor is one block above the water and the dais floats.
   (Task 1, `firstAirCountsWaterAsGround`; Task 7.)
4. **A very large plot index** (an old, busy server). The vine seeds overflow `int` and must still grow a whole dais.
   (Task 1, `aFarPlotStillGrowsAWholeDais`.)
5. **Relinking a new anchor to the same realm.** No dais is rebuilt and no blocks change. (Task 4 keeps the
   `data.generated` early return; Task 7 checks it in game.)

---

### Task 1: LandingDais, the pure plan of the dais

**Files:**
- Create: `src/main/java/com/pocketdimensions/manager/LandingDais.java`
- Test: `src/test/java/com/pocketdimensions/manager/LandingDaisTest.java`

**Interfaces:**
- Produces:
  - `LandingDais.plan(int plot, IntBinaryOperator firstAir) -> LandingDais.Plan`.
  - `record Plan(int floor, List<Block> blocks)` and `record Block(int dx, int y, int dz, Kind kind)`. In a `Block`,
    `dx`/`dz` are offsets from the Core's column and `y` is the world y.
  - `enum Kind { HEARTSTONE, AURORA_STONE }`.
  - `LandingDais.inDais(int dx, int below, int dz) -> boolean`.
  - `LandingDais.firstAir(int top, int minY, IntPredicate ground) -> int`.
  - Constants `RADIUS = 4.5`, `DEPTH = 3`, `CLEAR = 5.5`, `VINES = 6`, `REACH = 5`.

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew.bat test --tests "com.pocketdimensions.manager.LandingDaisTest"`
Expected: compilation FAILS with "cannot find symbol: class LandingDais".

- [ ] **Step 3: Write the implementation**

```java
package com.pocketdimensions.manager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;

/**
 * The Stone Dais the World Core lands on (RL-024): a flat disc of heartstone 9 blocks across with a rounded underside 3
 * deep, and six vines of aurora stone winding out from a pool under the Core to the edge and down its side. Offsets are
 * from the Core's column; heights are world y. Plain logic, unit tested; RealmManager reads the ground and sets the
 * blocks. Ported from the approved studies page (core-landing-studies.html, version 18, "Stone Dais").
 */
public final class LandingDais {

    /** The disc's radius, the underside's depth, and the radius of the open cylinder cleared above it. */
    public static final double RADIUS = 4.5, DEPTH = 3, CLEAR = RADIUS + 1;
    /** The number of vines. */
    public static final int VINES = 6;
    /** How far out (in whole blocks) the dais or its open cylinder reaches from the Core's column. */
    public static final int REACH = 5;

    public enum Kind { HEARTSTONE, AURORA_STONE }

    /** One block of the dais: offset from the Core's column, world y, and what it is. */
    public record Block(int dx, int y, int dz, Kind kind) {}

    /** The floor's height (the Core stands at floor + 1) and every block of the dais. */
    public record Plan(int floor, List<Block> blocks) {}

    private LandingDais() {}

    /**
     * The dais for plot `plot` (its vines are its own), over ground where `firstAir(dx, dz)` is the height of the first
     * air above the ground in that column (leaves, logs and plants skipped; water counts as ground).
     */
    public static Plan plan(int plot, IntBinaryOperator firstAir) {
        int floor = floor(firstAir);
        Map<Long, Kind> blocks = new LinkedHashMap<>();
        for (int dx = -REACH; dx <= REACH; dx++)
            for (int dz = -REACH; dz <= REACH; dz++)
                for (int below = -(int) DEPTH; below <= 0; below++)
                    if (inDais(dx, below, dz)) blocks.put(key(dx, below, dz), Kind.HEARTSTONE);
        new Vines(blocks, plot).grow();
        List<Block> out = new ArrayList<>(blocks.size());
        blocks.forEach((k, kind) -> out.add(new Block(unX(k), unBelow(k) + floor, unZ(k), kind)));
        return new Plan(floor, out);
    }

    /**
     * The height of the first air above the ground in one column: scanning down from `top` (the first air above
     * everything there) to `minY`, the block above the first one `ground` accepts; `minY` if none is ground.
     */
    public static int firstAir(int top, int minY, IntPredicate ground) {
        for (int y = top - 1; y >= minY; y--) if (ground.test(y)) return y + 1;
        return minY;
    }

    /** The floor's height: the average height of the first air above the ground under the disc, rounded. */
    static int floor(IntBinaryOperator firstAir) {
        long sum = 0;
        int n = 0;
        for (int dx = -REACH; dx <= REACH; dx++)
            for (int dz = -REACH; dz <= REACH; dz++)
                if (Math.hypot(dx, dz) < RADIUS) { sum += firstAir.applyAsInt(dx, dz); n++; }
        return (int) Math.round((double) sum / n);
    }

    /** Whether offset (dx, dz), `below` blocks from the floor (0 = the floor, negative = under it), is in the dais. */
    public static boolean inDais(int dx, int below, int dz) {
        return below <= 0 && Math.hypot(Math.hypot(dx, dz) / RADIUS, -below / DEPTH) < 1;
    }

    /** The vines, as on the studies page: a pool under the Core, then six winding paths that spill over the edge. */
    private static final class Vines {
        private final Map<Long, Kind> blocks;
        private final int plot;

        Vines(Map<Long, Kind> blocks, int plot) { this.blocks = blocks; this.plot = plot; }

        void grow() {
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (Math.hypot(dx, dz) < 1.6) vine(dx, dz);                          // the pool under the Core
            for (int i = 0; i < VINES; i++) {
                double a = i * Math.PI * 2 / VINES + (hsh(i, plot, 1, 99) - 0.5) * 0.5;
                walk(Math.cos(a) * 1.2, Math.sin(a) * 1.2, a, true, plot * 8 + i + 1, 1);
            }
        }

        private void vine(int dx, int dz) { if (inDais(dx, 0, dz)) blocks.put(key(dx, 0, dz), Kind.AURORA_STONE); }

        private void walk(double x, double z, double h, boolean wide, int seed, int branches) {
            int[] last = null;
            for (int k = 0; k < 40; k++) {
                h += (vn(k * 0.25, seed, 98) - 0.5) * 0.45;                              // a slow wander
                x += Math.cos(h) * 0.5;
                z += Math.sin(h) * 0.5;
                int gx = (int) Math.floor(x), gz = (int) Math.floor(z);
                if (!inDais(gx, 0, gz)) { if (last != null) spill(last[0], last[1], h); return; }
                vine(gx, gz);
                last = new int[]{gx, gz};
                double r = Math.hypot(x, z);
                if (wide && r < 2.2) vine((int) Math.floor(x - Math.sin(h) * 0.9), (int) Math.floor(z + Math.cos(h) * 0.9));   // two wide near the Core
                if (branches > 0 && r > 1.8 && hsh(seed, k, 3, 99) < 0.08) {
                    branches--;
                    double turn = (hsh(seed, k, 4, 99) < 0.5 ? -1 : 1) * (0.5 + 0.4 * hsh(seed, k, 5, 99));
                    walk(x, z, h + turn, false, seed * 7 + k, 0);
                }
            }
        }

        /** At the edge a vine trickles down the dais's side: the outward-facing blocks under its last floor block. */
        private void spill(int dx, int dz, double h) {
            int ox = (int) Math.round(Math.cos(h)), oz = (int) Math.round(Math.sin(h));
            for (int below = -1; below >= -(int) DEPTH; below--)
                if (blocks.get(key(dx, below, dz)) == Kind.HEARTSTONE && !inDais(dx + ox, below, dz + oz))
                    blocks.put(key(dx, below, dz), Kind.AURORA_STONE);
        }
    }

    /** The studies page's integer hash, 0..1. */
    static double hsh(int i, int j, int k, int s) {
        int n = (i * 374761393) ^ (j * 668265263) ^ (k * 1274126177) ^ (s * 1442695041);
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;
        return (n & 0xFFFFFFFFL) / 4294967295.0;
    }

    /** Smooth value noise over the hash. */
    static double vn(double x, double y, int s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = smooth(x - xi), fy = smooth(y - yi);
        double a = lerp(hsh(xi, yi, 0, s), hsh(xi + 1, yi, 0, s), fx), b = lerp(hsh(xi, yi + 1, 0, s), hsh(xi + 1, yi + 1, 0, s), fx);
        return lerp(a, b, fy);
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    // an offset packed into one long: dx and dz in 16 bits each, the height below the floor in the top half
    private static long key(int dx, int below, int dz) { return ((long) below << 32) | ((dx & 0xFFFFL) << 16) | (dz & 0xFFFFL); }

    private static int unX(long k) { return (short) (k >> 16); }

    private static int unZ(long k) { return (short) k; }

    private static int unBelow(long k) { return (int) (k >> 32); }
}
```

(The plan was checked before it was written: over plots 0 to 299 on flat ground, at least 7 aurora stone floor blocks
lie at r ≥ 3.5 and at least one block spills down the side; the slope test gives 60.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew.bat test --tests "com.pocketdimensions.manager.LandingDaisTest"`
Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/pocketdimensions/manager/LandingDais.java src/test/java/com/pocketdimensions/manager/LandingDaisTest.java
git commit -m "LandingDais plans the Stone Dais under the World Core: a flat heartstone disc 9 across, 3 deep underneath, six aurora stone vines from a pool under the Core spilling down the side, each plot its own"
```

---

### Task 2: The textures and models (tools/anchor/dais.mjs)

**Files:**
- Create: `tools/anchor/dais.mjs`
- Create: `tools/anchor/test/dais.test.mjs`
- Modify: `tools/anchor/build.mjs` (import and a new section after the Veil section, about line 262)
- Generated by `node tools/anchor/build.mjs`, all under `src/main/resources/assets/pocketdimensions/`:
  - `textures/block/heartstone.png`;
  - `textures/block/aurora_stone_veins.png` and its `.mcmeta`;
  - `textures/block/aurora_stone_rim.png` and its `.mcmeta`;
  - `models/block/heartstone.json` and `models/block/aurora_stone.json`;
  - `blockstates/heartstone.json` and `blockstates/aurora_stone.json`;
  - `items/heartstone.json` and `items/aurora_stone.json`.

**Interfaces:**
- Produces (used by Task 3's resource test):
  - texture ids `pocketdimensions:block/heartstone`, `pocketdimensions:block/aurora_stone_veins` and
    `pocketdimensions:block/aurora_stone_rim`;
  - model ids `pocketdimensions:block/heartstone` and `pocketdimensions:block/aurora_stone`.
  - The aurora stone model has three elements: `#rock`, then `#rim` with `light_emission` 6, then `#veins` with
    `light_emission` 15. It also has `"render_type": "minecraft:cutout"`.

- [ ] **Step 1: Write the failing test**

`tools/anchor/test/dais.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FRAMES, FRAMETIME, LOOP_SECONDS, VEINS, heartstone, frame, strip } from '../dais.mjs';

const at = (x, y) => VEINS.mask[((y + 16) % 16) * 16 + ((x + 16) % 16)];

test('heartstone: the Core rock, 16 x 16, opaque', () => {
  const rgba = heartstone();
  assert.equal(rgba.length, 16 * 16 * 4);
  for (let i = 0; i < rgba.length; i += 4) {
    assert.equal(rgba[i + 3], 255);
    [112, 108, 120].forEach((c, ch) => assert.ok(rgba[i + ch] >= Math.floor(c * 0.9) && rgba[i + ch] <= Math.ceil(c * 1.1), `pixel ${i / 4}`));
  }
});

test('the veins are thin, like crying obsidian: a few dozen pixels, never a 2 x 2 clump', () => {
  const veins = VEINS.mask.filter(v => v === 1).length;
  assert.ok(veins >= 30 && veins <= 60, `vein pixels ${veins}`);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++)
    assert.ok(!(at(x, y) === 1 && at(x + 1, y) === 1 && at(x, y + 1) === 1 && at(x + 1, y + 1) === 1), `clump at ${x},${y}`);
});

test('the veins run on across the tile edges, so neighbouring blocks join', () => {
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    if (at(x, y) !== 1) continue;
    let n = 0;
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) if ((dx || dy) && at(x + dx, y + dy) === 1) n++;
    assert.ok(n > 0, `lone vein pixel at ${x},${y}`);
  }
  const edge = f => [...Array(16).keys()].some(f);
  assert.ok(edge(i => at(0, i) === 1) && edge(i => at(15, i) === 1), 'veins reach the left and right edges');
  assert.ok(edge(i => at(i, 0) === 1) && edge(i => at(i, 15) === 1), 'veins reach the top and bottom edges');
});

test('the rim lies only beside a vein', () => {
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++)
    if (at(x, y) === 0.5) assert.ok([[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => at(x + dx, y + dy) === 1), `${x},${y}`);
});

test('the two overlays never share a pixel and are transparent elsewhere', () => {
  for (const t of [0, 3.25, 17]) {
    const v = frame('veins', t), r = frame('rim', t);
    for (let i = 0; i < 256; i++) {
      assert.equal(v[i * 4 + 3], VEINS.mask[i] === 1 ? 255 : 0, `veins pixel ${i}`);
      assert.equal(r[i * 4 + 3], VEINS.mask[i] === 0.5 ? 255 : 0, `rim pixel ${i}`);
    }
  }
});

test('the strip loops: 128 frames of 5 ticks make 32 s, and the frame after the last is the first again', () => {
  assert.equal(FRAMES, 128);
  assert.equal(FRAMETIME, 5);
  assert.equal(LOOP_SECONDS, 32);
  for (const layer of ['veins', 'rim']) {
    assert.deepEqual(frame(layer, LOOP_SECONDS), frame(layer, 0), layer);
    assert.equal(strip(layer).length, 16 * 16 * 4 * FRAMES, layer);
  }
});

test('the power flows: the veins change from one moment to the next', () => {
  assert.notDeepEqual(frame('veins', 0), frame('veins', 0.5));
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `node --test tools/anchor/test/dais.test.mjs`
Expected: FAIL with `Cannot find module` ... `dais.mjs`.

- [ ] **Step 3: Write `tools/anchor/dais.mjs`**

```js
// The Stone Dais under the World Core (RL-024, docs/superpowers/specs/2026-10-11-core-landing-dais-design.md):
// heartstone, the Core's weathered rock, and aurora stone's two animated overlays, its veins and their stained rim.
// Ported from the approved studies page (core-landing-studies.html, version 18): the same hash, rock and vein walk.

/** The studies page's integer hash, 0..1. */
export function hsh(i, j, k, s) {
  let n = Math.imul(i, 374761393) ^ Math.imul(j, 668265263) ^ Math.imul(k, 1274126177) ^ Math.imul(s, 1442695041);
  n = Math.imul(n ^ (n >>> 13), 1274126177); n ^= n >>> 16;
  return (n >>> 0) / 4294967295;
}

/** 128 frames, 5 ticks each (4 a second): 32 s, one turn of the aurora's colours and 16 pulses, so the strip loops. */
export const FRAMES = 128, FRAMETIME = 5, LOOP_SECONDS = FRAMES * FRAMETIME / 20;

/** Heartstone's colour at pixel (x, y): the Core's rock [112, 108, 120], grained in 2 x 2 flecks. */
export function rock(x, y) {
  const v = 0.91 + 0.18 * (hsh(x >> 1, y >> 1, 5, 9) * 0.5 + hsh(x, y, 8, 4) * 0.5);
  return [112 * v, 108 * v, 120 * v].map(Math.round);
}

/** Heartstone: 16 x 16, opaque. Aurora stone's bottom layer is this same texture, so their rock always matches. */
export function heartstone() {
  const rgba = new Uint8Array(16 * 16 * 4);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) rgba.set([...rock(x, y), 255], (y * 16 + x) * 4);
  return rgba;
}

const DIRS8 = [[1, 0], [1, 1], [0, 1], [-1, 1], [-1, 0], [-1, -1], [0, -1], [1, -1]];
const W = n => ((n % 16) + 16) % 16;

/**
 * The vein pattern (the studies page's tile 0): two 1-pixel random walks with a few branches, wrapping round the tile's
 * edges so neighbouring blocks join. mask: 1 = vein, 0.5 = the stained rim beside it, 0 = rock. along: 0..1, how far
 * along its vein a pixel lies (in 16ths, wrapping), which the pulses travel down.
 */
export const VEINS = (() => {
  const v = 0, mask = new Float32Array(256), dist = new Float32Array(256); let k = 0;
  const walk = (x, y, d, steps, from, depth) => {
    for (let i = 0; i < steps; i++) {
      x = W(x); y = W(y); mask[y * 16 + x] = 1; dist[y * 16 + x] = from + i; k++;
      const r = hsh(v, k, 1, 58);
      if (r < 0.3) d = (d + 1) & 7; else if (r < 0.6) d = (d + 7) & 7;              // wander, never doubling back
      if (depth < 2 && hsh(v, k, 2, 58) < 0.07)
        walk(x, y, (d + (hsh(v, k, 3, 58) < 0.5 ? 2 : 6)) & 7, 4 + Math.floor(hsh(v, k, 4, 58) * 6), from + i, depth + 1);
      x += DIRS8[d][0]; y += DIRS8[d][1];
    }
  };
  for (let j = 0; j < 2; j++)
    walk(Math.floor(hsh(v, j, 5, 58) * 16), Math.floor(hsh(v, j, 6, 58) * 16), Math.floor(hsh(v, j, 7, 58) * 8), 14 + Math.floor(hsh(v, j, 8, 58) * 7), 0, 0);
  const along = new Float32Array(256);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const i = y * 16 + x;
    if (!mask[i] && [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => mask[W(y + dy) * 16 + W(x + dx)] === 1) && hsh(x, y, v, 59) < 0.35) mask[i] = 0.5;
    along[i] = Math.round((dist[i] % 16) / 16 * 255) / 255;                        // as the studies page stored it
  }
  return { mask, along };
})();

const AURORA = [[60, 255, 170], [60, 200, 255], [170, 90, 255], [255, 110, 200]];   // green, cyan, violet, pink
/** The aurora's colour at s (one turn per 1.0), each colour easing into the next. */
export function aurora(s) {
  const f = (s - Math.floor(s)) * 4, i = Math.floor(f) % 4, t = f - Math.floor(f), e = t * t * (3 - 2 * t);
  return AURORA[i].map((a, c) => a + (AURORA[(i + 1) % 4][c] - a) * e);
}

const TAU = Math.PI * 2, clamp = v => Math.max(0, Math.min(255, Math.round(v)));
/** The pulse's brightness 0..1 at a vein pixel `along` its vein, t seconds in: one pulse every 2 s, two per 16 px. */
export const pulse = (along, t) => Math.pow(0.5 + 0.5 * Math.sin(TAU * (along * 2 - t * 0.5)), 2);

/** One 16 x 16 frame of a layer ('veins' or 'rim') at t seconds: that layer's pixels coloured, the rest transparent. */
export function frame(layer, t) {
  const rgba = new Uint8Array(16 * 16 * 4), want = layer === 'veins' ? 1 : 0.5;
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const i = y * 16 + x;
    if (VEINS.mask[i] !== want) continue;
    const a = VEINS.along[i], p = pulse(a, t), au = aurora(t / LOOP_SECONDS + 0.08 * Math.sin(TAU * a));
    const c = layer === 'veins'
      ? au.map(v => v * (0.6 + 0.75 * p) + 255 * 0.05 * p)
      : rock(x, y).map((r, ch) => r + (au[ch] * 0.35 - r) * (0.35 + 0.2 * p));
    rgba.set([...c.map(clamp), 255], i * 4);
  }
  return rgba;
}

/** A layer's animation strip: FRAMES frames stacked downward (16 x 16 * FRAMES). */
export function strip(layer) {
  const out = new Uint8Array(16 * 16 * 4 * FRAMES);
  for (let k = 0; k < FRAMES; k++) out.set(frame(layer, k * LOOP_SECONDS / FRAMES), k * 16 * 16 * 4);
  return out;
}
```

(Checked before it was written: the pattern is 45 vein pixels and 36 rim pixels, with no lone pixel and no 2 x 2
clump; `frame(layer, 32)` equals `frame(layer, 0)`.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `node --test tools/anchor/test/dais.test.mjs`
Expected: PASS, 7 tests.

- [ ] **Step 5: Wire it into the build**

In `tools/anchor/build.mjs`, add next to the other imports:

```js
import { heartstone, strip as daisStrip, FRAMES as DAIS_FRAMES, FRAMETIME as DAIS_FRAMETIME } from './dais.mjs';
```

and after the line `write('blockstates/veil.json', json({ variants: veilVariants }));` add:

```js
// The Stone Dais under the World Core (RL-024): heartstone, and aurora stone (heartstone with two animated overlays,
// the veins at full brightness and their stained rim glowing a little, so the veins shine at night and the rock darkens)
const cubeFaces = (tex, extra = {}) => ({
  from: [0, 0, 0], to: [16, 16, 16], ...extra,
  faces: Object.fromEntries(['down', 'up', 'north', 'south', 'west', 'east'].map(d => [d, { uv: [0, 0, 16, 16], texture: tex, cullface: d }])),
});
write('textures/block/heartstone.png', encodePNG(16, 16, heartstone()));
for (const layer of ['veins', 'rim']) {
  write(`textures/block/aurora_stone_${layer}.png`, encodePNG(16, 16 * DAIS_FRAMES, daisStrip(layer)));
  write(`textures/block/aurora_stone_${layer}.png.mcmeta`, json({ animation: { frametime: DAIS_FRAMETIME, interpolate: true } }));
}
write('models/block/heartstone.json', json({ parent: 'minecraft:block/cube_all', textures: { all: 'pocketdimensions:block/heartstone' } }));
write('models/block/aurora_stone.json', json({
  parent: 'minecraft:block/block',
  render_type: 'minecraft:cutout',
  textures: {
    particle: 'pocketdimensions:block/heartstone', rock: 'pocketdimensions:block/heartstone',
    rim: 'pocketdimensions:block/aurora_stone_rim', veins: 'pocketdimensions:block/aurora_stone_veins',
  },
  elements: [cubeFaces('#rock'), cubeFaces('#rim', { shade: false, light_emission: 6 }), cubeFaces('#veins', { shade: false, light_emission: 15 })],
}));
for (const name of ['heartstone', 'aurora_stone']) {
  write(`blockstates/${name}.json`, json({ variants: { '': { model: `pocketdimensions:block/${name}` } } }));
  write(`items/${name}.json`, json({ model: { type: 'minecraft:model', model: `pocketdimensions:block/${name}` } }));
}
console.log(`dais: heartstone, aurora_stone (veins + rim, ${DAIS_FRAMES} frames)`);
```

(`report` is already printed at line 247, before this section, so the dais logs its own line.)

- [ ] **Step 6: Generate and check**

Run: `node tools/anchor/build.mjs && node --test tools/anchor/test/*.test.mjs && git status --short`
Expected:
- all node tests pass;
- `git status` shows the 12 new generated files listed under **Files** and nothing else changed (the generator is
  deterministic, so the existing outputs come out byte-identical).

If other generated files show as changed, stop and investigate before committing.

- [ ] **Step 7: Commit**

```bash
git add tools/anchor/dais.mjs tools/anchor/test/dais.test.mjs tools/anchor/build.mjs src/main/resources/assets/pocketdimensions
git commit -m "Heartstone and aurora stone textures and models: the Core's rock, and the same rock with thin crying-obsidian veins of flowing aurora as two glowing animated overlays (one pattern on every block)"
```

---

### Task 3: The two blocks

**Files:**
- Modify: `src/main/java/com/pocketdimensions/init/ModBlocks.java` (two new entries after `WORLD_CORE`)
- Modify: `src/main/java/com/pocketdimensions/init/ModItems.java` (two block items after `WORLD_CORE_ITEM`)
- Modify: `src/main/java/com/pocketdimensions/init/ModCreativeTabs.java:28` (after `BOUNDARY_BLOCK_ITEM`)
- Modify: `src/main/resources/assets/pocketdimensions/lang/en_us.json`
- Modify: `src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json`
- Create: `src/main/resources/data/pocketdimensions/loot_table/blocks/heartstone.json`
- Create: `src/main/resources/data/pocketdimensions/loot_table/blocks/aurora_stone.json`
- Test: `src/test/java/com/pocketdimensions/init/DaisBlocksResourcesTest.java`

**Interfaces:**
- Consumes: Task 2's models, blockstates and item files.
- Produces: `ModBlocks.HEARTSTONE` and `ModBlocks.AURORA_STONE` (`RegistryObject<Block>`), used by Task 4.

- [ ] **Step 1: Write the failing test**

```java
package com.pocketdimensions.init;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The Stone Dais's two blocks have everything the game needs: names, models, items, drops, and the pickaxe (RL-024). */
class DaisBlocksResourcesTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/pocketdimensions");
    private static final Path DATA = Path.of("src/main/resources/data");
    private static final String[] BLOCKS = {"heartstone", "aurora_stone"};

    private static JsonObject read(Path p) throws IOException {
        assertTrue(Files.exists(p), "missing " + p);
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void eachHasANameABlockstateAndAnItem() throws IOException {
        JsonObject lang = read(ASSETS.resolve("lang/en_us.json"));
        assertEquals("Heartstone", lang.get("block.pocketdimensions.heartstone").getAsString());
        assertEquals("Aurora Stone", lang.get("block.pocketdimensions.aurora_stone").getAsString());
        for (String b : BLOCKS) {
            read(ASSETS.resolve("blockstates/" + b + ".json"));
            read(ASSETS.resolve("models/block/" + b + ".json"));
            read(ASSETS.resolve("items/" + b + ".json"));
        }
    }

    @Test
    void eachDropsItselfAndNeedsAPickaxe() throws IOException {
        JsonArray pickaxe = read(DATA.resolve("minecraft/tags/block/mineable/pickaxe.json")).getAsJsonArray("values");
        for (String b : BLOCKS) {
            assertTrue(pickaxe.contains(new com.google.gson.JsonPrimitive("pocketdimensions:" + b)), b + " in mineable/pickaxe");
            JsonObject loot = read(DATA.resolve("pocketdimensions/loot_table/blocks/" + b + ".json"));
            assertEquals("pocketdimensions:" + b, loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                    .getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        }
    }

    @Test
    void auroraStoneIsTheHeartstoneWithGlowingVeinsOnTop() throws IOException {
        JsonObject m = read(ASSETS.resolve("models/block/aurora_stone.json"));
        assertEquals("minecraft:cutout", m.get("render_type").getAsString());
        assertEquals("pocketdimensions:block/heartstone", m.getAsJsonObject("textures").get("rock").getAsString(),
                "the rock is the heartstone texture itself, so the two always match");
        JsonArray el = m.getAsJsonArray("elements");
        assertEquals(3, el.size());
        assertFalse(el.get(0).getAsJsonObject().has("light_emission"), "the rock is lit like any block");
        assertEquals(6, el.get(1).getAsJsonObject().get("light_emission").getAsInt(), "the rim glows a little");
        assertEquals(15, el.get(2).getAsJsonObject().get("light_emission").getAsInt(), "the veins shine at night");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew.bat test --tests "com.pocketdimensions.init.DaisBlocksResourcesTest"`
Expected: FAIL. The models and items exist from Task 2, so the failures are the lang assertion (`expected: <Heartstone>
but was: <null>` or an NPE on `getAsString`) and "missing ...loot_table/blocks/heartstone.json".

- [ ] **Step 3: Add the blocks, items, names, drops and tag**

`ModBlocks.java`, after `WORLD_CORE`:

```java
    // the Stone Dais the World Core lands on (RL-024): the Core's own rock, and the same rock veined with the aurora
    public static final RegistryObject<Block> HEARTSTONE = BLOCKS.register("heartstone",
            () -> new Block(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("heartstone"))
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> AURORA_STONE = BLOCKS.register("aurora_stone",
            () -> new Block(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("aurora_stone"))
                    .strength(1.5f, 6.0f)
                    .lightLevel(state -> 7)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));
```

`ModItems.java`, after `WORLD_CORE_ITEM`:

```java
    public static final RegistryObject<Item> HEARTSTONE_ITEM = ITEMS.register("heartstone",
            () -> new BlockItem(ModBlocks.HEARTSTONE.get(),
                    new Item.Properties().setId(ITEMS.key("heartstone")).useBlockDescriptionPrefix()));

    public static final RegistryObject<Item> AURORA_STONE_ITEM = ITEMS.register("aurora_stone",
            () -> new BlockItem(ModBlocks.AURORA_STONE.get(),
                    new Item.Properties().setId(ITEMS.key("aurora_stone")).useBlockDescriptionPrefix()));
```

(`useBlockDescriptionPrefix()` makes the item show the `block.` name, so one lang key per block is enough.)

`ModCreativeTabs.java`, after `output.accept(ModItems.BOUNDARY_BLOCK_ITEM.get());`:

```java
                                output.accept(ModItems.HEARTSTONE_ITEM.get());
                                output.accept(ModItems.AURORA_STONE_ITEM.get());
```

`en_us.json`, after the `"block.pocketdimensions.world_core"` line:

```json
    "block.pocketdimensions.heartstone": "Heartstone",
    "block.pocketdimensions.aurora_stone": "Aurora Stone",
```

`mineable/pickaxe.json` `values`: add `"pocketdimensions:heartstone"` and `"pocketdimensions:aurora_stone"`.

`loot_table/blocks/heartstone.json` (and the same for `aurora_stone.json`, with its own name):

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [{ "type": "minecraft:item", "name": "pocketdimensions:heartstone" }],
      "conditions": [{ "condition": "minecraft:survives_explosion" }]
    }
  ]
}
```

- [ ] **Step 4: Run the test and the build**

Run: `./gradlew.bat test --tests "com.pocketdimensions.init.DaisBlocksResourcesTest" && ./gradlew.bat build`
Expected: PASS (3 tests), and BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/pocketdimensions/init src/main/resources src/test/java/com/pocketdimensions/init/DaisBlocksResourcesTest.java
git commit -m "Heartstone and Aurora Stone blocks: mined with a pickaxe, each drops itself, aurora stone gives light 7, both in the creative tab"
```

---

### Task 4: The Core lands at the centre on its dais

**Files:**
- Modify: `src/main/java/com/pocketdimensions/manager/RealmManager.java`:
  - the class javadoc (about line 46);
  - `maxSearchChunks` (line 66);
  - `ensureGenerated`, `clearColumnAbove` and `findLandNearCenter` (lines 409 to 520).
- Modify: `src/main/java/com/pocketdimensions/PocketDimensionsConfig.java` (remove `MAX_SPAWN_SEARCH_CHUNKS`, lines
  about 64 to 69, and its field declaration).
- Delete: `src/main/java/com/pocketdimensions/manager/RingSearch.java` and
  `src/test/java/com/pocketdimensions/manager/RingSearchTest.java`.

**Interfaces:**
- Consumes:
  - `LandingDais.plan`, `LandingDais.firstAir`, `LandingDais.CLEAR`, `LandingDais.REACH` and `LandingDais.Block`
    (Task 1);
  - `ModBlocks.HEARTSTONE` and `ModBlocks.AURORA_STONE` (Task 3).
- Produces: `ensureGenerated(UUID, ServerLevel)` keeps its signature. It now always places the Core at
  `(centreX, floor + 1, centreZ)`.

The landing reads the real world, so it has no unit test here. Its logic is `LandingDais` (Task 1), and the world side
is checked in game in Task 7. The test for this task is the whole suite staying green after the search is removed.

- [ ] **Step 1: Replace `ensureGenerated` and remove the search**

Replace the whole of `ensureGenerated`, `clearColumnAbove` and `findLandNearCenter` (from
`public void ensureGenerated` through the end of `findLandNearCenter`, keeping `placeCore` as it is) with:

```java
    /**
     * Grows ownerUUID's realm the first time it is needed: the World Core lands at the plot's exact centre, whatever the
     * land there, on its Stone Dais (LandingDais), with the sky cleared above it. Only ever once per realm: a realm
     * reached again through a new anchor is already generated and nothing changes.
     */
    public void ensureGenerated(UUID ownerUUID, ServerLevel realmLevel) {
        RealmData data = realms.get(ownerUUID);
        if (data == null || data.generated) return;
        int[] origChunk = plotOriginChunkXZ(data.plotIndex);
        int centerX = (origChunk[0] + sideChunks() / 2) * 16 + 8;
        int centerZ = (origChunk[1] + sideChunks() / 2) * 16 + 8;
        BlockPos corePos = buildDais(realmLevel, centerX, centerZ, data.plotIndex);
        placeCore(realmLevel, corePos, ownerUUID);
        data.worldCorePos = corePos;
        data.generated = true;
        setDirty();
    }

    /**
     * The Stone Dais at the column (cx, cz): the open cylinder above it cleared to the sky, then its heartstone and
     * aurora stone set. Blocks are set without neighbour updates, so nothing pours or collapses while it is built.
     * Returns where the Core stands: on the pool, one above the floor.
     */
    private static BlockPos buildDais(ServerLevel level, int cx, int cz, int plot) {
        level.getChunk(cx >> 4, cz >> 4);              // the centre is block 8 of its chunk, so all of it (3..13) is here
        LandingDais.Plan plan = LandingDais.plan(plot, (dx, dz) -> firstAirAboveGround(level, cx + dx, cz + dz));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -LandingDais.REACH; dx <= LandingDais.REACH; dx++)
            for (int dz = -LandingDais.REACH; dz <= LandingDais.REACH; dz++) {
                if (Math.hypot(dx, dz) >= LandingDais.CLEAR) continue;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + dx, cz + dz);
                for (int y = plan.floor() + 1; y < top; y++)
                    level.setBlock(p.set(cx + dx, y, cz + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        for (LandingDais.Block b : plan.blocks()) {
            Block block = (b.kind() == LandingDais.Kind.AURORA_STONE ? ModBlocks.AURORA_STONE : ModBlocks.HEARTSTONE).get();
            level.setBlock(p.set(cx + b.dx(), b.y(), cz + b.dz()), block.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return new BlockPos(cx, plan.floor() + 1, cz);
    }

    /** The height of the first air above the ground in column (x, z): see isGround. */
    private static int firstAirAboveGround(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        return LandingDais.firstAir(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), level.getMinY(),
                y -> isGround(level.getBlockState(p.set(x, y, z))));
    }

    /** What the dais rests on: anything solid but leaves and logs, and any fluid (on the ocean it floats on the water). */
    private static boolean isGround(BlockState s) {
        return !s.getFluidState().isEmpty() || (s.isSolid() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS));
    }
```

Add the import `import net.minecraft.world.level.block.Block;` (the code refers to `LandingDais.Block` by its full name,
so the two never clash). `LandingDais` is in the same package.

Then:
- Delete `private static int maxSearchChunks()` (line 66).
- In the class javadoc, replace the line "Plots with no dry land are added to invalidPlots and skipped on future
  allocation." with "Retired plots (relocated or disowned) are kept in invalidPlots and never handed out again."
- Check no other code uses what was removed:
  `grep -rn "RingSearch\|findLandNearCenter\|clearColumnAbove\|maxSearchChunks\|MAX_SPAWN_SEARCH" src/`. Only
  `RingSearch.java`, `RingSearchTest.java` and the config should be left.

- [ ] **Step 2: Remove RingSearch and the config key**

```bash
git rm src/main/java/com/pocketdimensions/manager/RingSearch.java src/test/java/com/pocketdimensions/manager/RingSearchTest.java
```

In `PocketDimensionsConfig.java`, delete:
- the `MAX_SPAWN_SEARCH_CHUNKS = builder ... .defineInRange("max_spawn_search_chunks", 8, 1, 256);` statement;
- the `MAX_SPAWN_SEARCH_CHUNKS` field declaration (find it with `grep -n MAX_SPAWN_SEARCH_CHUNKS`).

An old config file that still has the key is harmless: Forge drops unknown keys when it corrects the file.

- [ ] **Step 3: Run the whole suite and the build**

Run: `./gradlew.bat test && ./gradlew.bat build`
Expected: BUILD SUCCESSFUL, every test passing. `RingSearchTest` is gone; `LandingDaisTest` and
`DaisBlocksResourcesTest` pass.

- [ ] **Step 4: Commit**

```bash
git add -A src/main/java/com/pocketdimensions/manager src/main/java/com/pocketdimensions/PocketDimensionsConfig.java src/test/java/com/pocketdimensions/manager
git commit -m "The World Core lands at the plot's exact centre on its Stone Dais with the sky cleared above, on land or water; the dry-land search, RingSearch and max_spawn_search_chunks go"
```

---

### Task 5: The ward's conduit goes

**Files:**
- Modify: `src/main/java/com/pocketdimensions/client/ward/VaultFrame.java`:
  - the class javadoc (lines 6 to 10);
  - the `conduit(out, hidden, l, t);` call (line 34);
  - the `conduit` method (lines 98 to 109).
- Modify: `src/main/java/com/pocketdimensions/client/ward/Vault.java` (the javadoc of `beamTop`, about line 33)
- Modify: `src/main/java/com/pocketdimensions/client/ward/WardSigilRenderer.java` (the javadoc of `beam`, about line 192)
- Test: `src/test/java/com/pocketdimensions/client/ward/VaultTest.java:126-131`

- [ ] **Step 1: Change the test to what we now want**

Replace the test `anOffCentreCoreGetsAConduit` with:

```java
    @Test
    void noConduitRunsToTheCrownEvenForAnOffCentreCore() {
        Vault.Layout off = Vault.build(392.5 + 128, 392.5 - 90, 0, 0, 784, 784);
        assertEquals(0, runes(off, 4, true).stream().filter(r -> r.id() >= 70000 && r.id() < 80000).count());
        assertEquals(0, runes(r25(), 4, true).stream().filter(r -> r.id() >= 70000 && r.id() < 80000).count());
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew.bat test --tests "com.pocketdimensions.client.ward.VaultTest"`
Expected: FAIL in `noConduitRunsToTheCrownEvenForAnOffCentreCore` (`expected: <0> but was: <N>`, N > 5).

- [ ] **Step 3: Remove the conduit**

In `VaultFrame.java`:
- Delete the line `conduit(out, hidden, l, t);` and the whole `conduit` method, including its javadoc.
- In the class javadoc, replace "the band\n * over the Veil, and a conduit from an off-centre Core's beam to the crown."
  with "and the band\n * over the Veil." (keep the line wrapping tidy).

In `Vault.java`, change the javadoc of `beamTop` to:

```java
        /**
         * Where the Core's beam ends: at the crown (y 384) when the Core stands under it (every Core grown since RL-024
         * stands at the centre), else on the dome above the Core.
         */
```

In `WardSigilRenderer.java`, change "up to the crown (or, for an off-centre Core, to the dome\n     * where the conduit
takes over)" to "up to the crown (or, for an off-centre Core of an older realm, to the dome above it)".

- [ ] **Step 4: Run the tests**

Run: `./gradlew.bat test --tests "com.pocketdimensions.client.ward.*"`
Expected: PASS. `beamTop` is unchanged, so its existing test still passes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/pocketdimensions/client/ward src/test/java/com/pocketdimensions/client/ward/VaultTest.java
git commit -m "The ward's conduit is gone: no line of runes runs from an off-centre Core's beam to the crown, since every new Core stands at the centre"
```

---

### Task 6: Docs

**Files:**
- Modify:
  - `PRD.md` (lines 39 and 60);
  - `README.md:174`;
  - `pocket-worlds.md` (line 85 and any dry-land wording);
  - `docs/superpowers/specs/2026-10-09-realm-ward-design.md` (lines 116 to 117 and 160);
  - `CLAUDE.md` (gitignored; edit it, but it is not committed).

- [ ] **Step 1: Find every mention**

Run: `grep -rn "max_spawn_search\|RingSearch\|dry land\|dry-land\|conduit\|RL-024" --include=*.md . | grep -v "docs/superpowers/plans\|\.superpowers/\|2026-10-11-core"`
Expected: the lines listed above. Update each:

- **PRD.md RL-004:** "The World Core lands at the plot's exact centre on its Stone Dais (RL-024), the sky cleared
  above; sets owner UUID".
- **PRD.md RL-024:** status `DONE`. Text: "The World Core lands at the plot's exact centre, on land or water, on a Stone
  Dais 9 across: heartstone (the Core's rock) with six vines of aurora stone (thin crying-obsidian veins of flowing
  aurora, light 7) from a pool under the Core to the edge and down its side; a cylinder is cleared to the sky above.
  Built once per new realm (first seed, after a disown, relocation), never on relinking. The dry-land search,
  `RingSearch` and `max_spawn_search_chunks` are gone; the ward's conduit is removed. A recipe for aurora stone is for
  later (spec `docs/superpowers/specs/2026-10-11-core-landing-dais-design.md`)".
- **README.md:** delete the `realm.max_spawn_search_chunks` row.
  - Wherever the README describes where the Core is placed (`grep -n "Core" README.md`), say it lands at the centre on
    its Stone Dais.
  - If the README lists the blocks, add Heartstone and Aurora Stone.
- **pocket-worlds.md:**
  - Line 85: drop "and the Core always landing at the centre on an aurora crystal platform (PRD RL-024)".
  - Add a bullet in that section: "The World Core always stands at the realm's exact centre, on land or water, on a
    small Stone Dais of heartstone veined with aurora stone, with the sky cleared above it."
- **The ward spec:**
  - Line 116 to 117: "Everything is centred on the plot's centre, where the Core stands (RL-024)." Drop the conduit
    sentence.
  - Line 160: drop the conduit clause; say an older realm's off-centre Core's beam ends on the dome above it.
- **CLAUDE.md:**
  - Remove the `max_spawn_search_chunks` line.
  - Change `ModBlocks.java ← DeferredRegister<Block> (6 blocks)` to name the dais blocks too ("incl. heartstone and
    aurora_stone, the Core's Stone Dais").
  - Add to the `manager/` pure list: `LandingDais (the Core's Stone Dais: shape, floor, vines)`.
  - Add `dais.mjs ← Heartstone + Aurora Stone textures (veins and rim strips)` to the tools list.

- [ ] **Step 2: Check for banned dashes**

Run: `grep -n $'\u2014\| \u2013 ' PRD.md README.md pocket-worlds.md docs/superpowers/specs/2026-10-09-realm-ward-design.md`
Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add PRD.md README.md pocket-worlds.md docs/superpowers/specs/2026-10-09-realm-ward-design.md
git commit -m "Docs: RL-024 done, the Core lands at the centre on its Stone Dais; the dry-land search and the ward's conduit are gone"
```

---

### Task 7: In-game check with the user

No code. Starts the dev server and client again, and asks the user to look. Report to the user what was checked and
what they should look at; do not claim anything you have not seen.

- [ ] **Step 1: Start the server and the client**

Run in the background (as before):
```bash
cd C:/Users/rasmu/Documents/claude/minecraft-pocket-dimensions && ./gradlew.bat runServer > ../runserver.log 2>&1
cd C:/Users/rasmu/Documents/claude/minecraft-pocket-dimensions && sleep 20; ./gradlew.bat runClient > ../runclient.log 2>&1
```
Expected: `../runserver.log` reaches "Done (" with no exception mentioning `pocketdimensions`, `heartstone` or
`aurora_stone`. The client log has no "Missing texture" or model errors for the two blocks (`grep -i "aurora_stone\|heartstone" ../runclient.log`).

- [ ] **Step 2: Hand over to the user with a checklist**

Ask the user to:
1. **A new realm:** grow one with a new World Seed (or `/pd disown`, then a new seed). Check:
   - the Core stands at the centre on the dais;
   - the floor is level;
   - the vines glow and flow;
   - the sky is open above.
2. **Night:** `/time set night`. The veins should glow while the rock darkens.
3. **Mining:** mine a heartstone and an aurora stone with a pickaxe. Each should drop itself, and each should show in
   the creative tab.
4. **Relinking:** break the anchor (`/pd test mineAnchor` or creative), place a new anchor and use a new seed. They
   should arrive at the **same** Core and dais, unchanged.
5. **Relocation:** relocate from the Core's screen. The new realm should have a new dais with different vines.
   Relocate again a few times to try for a forest or ocean centre.
6. **The sky:** look up. There should be no line of runes running from the beam to the crown.

Wait for the user's reply. Fixes they ask for become new tasks.
