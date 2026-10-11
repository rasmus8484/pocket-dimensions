# The Core's Landing, Round 2 (stone, and veins that go deep) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After the user's first look in game, three changes:
- the dais is plain vanilla stone, and the heartstone block goes;
- aurora stone becomes vanilla stone with the veins on top;
- roots of aurora stone run downward through the dais and into the ground below.

**Architecture:**
- `LandingDais` (pure, tested) changes in three ways:
  - `Kind.HEARTSTONE` becomes `Kind.STONE`;
  - it grows roots from the floor's aurora stone;
  - `Plan` gains a `roots` list for the part below the dais.
- `RealmManager` sets `Blocks.STONE`. It sets roots only where the ground below is natural solid ground.
- The aurora stone model's bottom layer is `minecraft:block/stone`.
- The rim overlay mixes the aurora into a fixed grey of 126 (vanilla stone's average), so the generator no longer
  paints any rock of its own.
- Heartstone's block, item, texture, model, loot table, tag entry and name are removed.

**Tech Stack:** Java 21, Forge 1.21.11 (61.1.0), JUnit 5; Node (`node:test`) for the texture generator.

**Spec:** `docs/superpowers/specs/2026-10-11-core-landing-dais-design.md` (revised for round 2)

## Global Constraints

- **No dashes:** never use em dashes, and never a spaced en dash used in its place. This applies to code, comments,
  docs and commit messages.
- **Commit messages:** no `Co-Authored-By` line. One descriptive sentence, as in `git log`.
- **Gradle and the dev server:** never run Gradle while the dev server or client runs.
  - Stop the background tasks `bzt6ak183` (runServer) and `bnshb3tk1` (runClient) with TaskStop first, if they are
    still running.
  - Gradle: `./gradlew.bat test --tests "<class>"`; the whole suite is `./gradlew.bat test`.
- **Aurora stone stays as it is:** id `aurora_stone`, name "Aurora Stone", strength 1.5 / 6, pickaxe, drops itself,
  light 7.
- **The dais blocks:** the dais and the fluid seal are `minecraft:stone`.
- **Roots:**
  - they start from the pool's centre and from about one in four of the floor's aurora stone blocks;
  - each runs 3 to 7 blocks below the floor, stepping one block aside 35% of the time;
  - below the dais they replace only natural solid ground: `isGround`, not a fluid, no block entity.
- The user gave the go-ahead on 2026-10-11, adding Task 2b (the aurora's colours).

## Review Focus

1. **A root under a floating ledge (a slope).** Below the dais there may be air. Roots must skip air and never leave
   floating aurora stone in the sky. (Task 3: roots are set only where the block is ground. In-game check, Task 4.)
2. **A root into a cave, water or an aquifer.** It must never replace a fluid and open a leak, nor a chest.
   (Task 3 `canRootInto`; Task 1's test checks every root stays within 7 blocks so it never leaves the chunk.)
3. **The dev world still holds heartstone blocks from round 1.** Removing the block turns them to air on load (they
   were only in test realms). This is accepted; tell the user. (Task 2.)
4. **A resource pack that changes stone.** The vein rim is mixed into a fixed grey, so it may look a little off
   against a strongly coloured pack's stone. This is accepted and documented in the spec.
5. **The pool's root runs straight under the Core and the arrival spot.** Aurora stone is solid, so standing there is
   unchanged. (Task 1 `aRootRunsDownUnderThePool`.)

---

### Task 1: LandingDais: stone, and roots

**Files:**
- Modify: `src/main/java/com/pocketdimensions/manager/LandingDais.java`
- Test: `src/test/java/com/pocketdimensions/manager/LandingDaisTest.java`

**Interfaces:**
- Produces:
  - `enum Kind { STONE, AURORA_STONE }`, which replaces `HEARTSTONE`;
  - `record Plan(int floor, List<Block> blocks, List<Block> roots)`, where `roots` are the root blocks below the dais
    (world y, offsets from the Core's column, kind AURORA_STONE);
  - `LandingDais.ROOT_DEPTH = 8`, the deepest bound used by tests and RealmManager: roots are less than 8 below the
    floor.

- [ ] **Step 1: Write the failing tests**

Add to `LandingDaisTest` (and `import java.util.HashSet; import java.util.Set;` are already there):

```java
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
```

In the existing tests:
- replace `LandingDais.Kind.HEARTSTONE` with `LandingDais.Kind.STONE` wherever it appears (none at present);
- in `aFarPlotStillGrowsAWholeDais`, the side check still holds.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew.bat test --tests "com.pocketdimensions.manager.LandingDaisTest"`
Expected: compilation FAILS with "cannot find symbol" for `Kind.STONE`, `roots()` and `ROOT_DEPTH`.

- [ ] **Step 3: Implement**

In `LandingDais.java`:

1. Replace every `HEARTSTONE` with `STONE`. In the class javadoc, "a flat disc of heartstone" becomes "a flat disc of
   stone".
2. Replace the `Plan` record and its javadoc with:

```java
    /**
     * The floor's height (the Core stands at floor + 1), every block of the dais, and the roots: aurora stone running on
     * down below the dais into the ground, set only where the ground there is natural solid ground.
     */
    public record Plan(int floor, List<Block> blocks, List<Block> roots) {}

    /** Roots reach less than this far below the floor. */
    public static final int ROOT_DEPTH = 8;
```

3. In `plan`, replace

```java
        new Vines(blocks, plot).grow();
        List<Block> out = new ArrayList<>(blocks.size());
        blocks.forEach((k, kind) -> out.add(new Block(unX(k), unBelow(k) + floor, unZ(k), kind)));
        return new Plan(floor, out);
```

with

```java
        Map<Long, Kind> roots = new LinkedHashMap<>();
        Vines v = new Vines(blocks, plot);
        v.grow();
        v.roots(roots);
        List<Block> out = new ArrayList<>(blocks.size()), down = new ArrayList<>(roots.size());
        blocks.forEach((k, kind) -> out.add(new Block(unX(k), unBelow(k) + floor, unZ(k), kind)));
        roots.forEach((k, kind) -> down.add(new Block(unX(k), unBelow(k) + floor, unZ(k), kind)));
        return new Plan(floor, out, down);
```

4. In `Vines`, before `private void vine(int dx, int dz)`, add:

```java
        /**
         * Roots: from the floor's aurora stone (the pool's centre, and about one floor block in four) a vein runs down
         * through the dais, now and then stepping one block aside, 3 to 7 blocks deep. Inside the dais it turns its
         * stone to aurora stone; below the dais it goes into `below`, for the ground beneath.
         */
        void roots(Map<Long, Kind> below) {
            List<int[]> starts = new ArrayList<>();
            for (Map.Entry<Long, Kind> e : blocks.entrySet()) {
                long k = e.getKey();
                if (e.getValue() != Kind.AURORA_STONE || unBelow(k) != 0) continue;
                int dx = unX(k), dz = unZ(k);
                if ((dx == 0 && dz == 0) || hsh(dx, dz, plot, 97) < 0.25) starts.add(new int[]{dx, dz});
            }
            for (int[] s : starts) {
                int x = s[0], z = s[1], seed = hashInt(s[0], s[1], plot);
                int depth = 3 + (int) (hsh(seed, 0, 1, 96) * 5);
                for (int d = 1; d <= depth; d++) {
                    if (hsh(seed, d, 2, 96) < 0.35) {                                    // a step aside
                        int dir = (int) (hsh(seed, d, 3, 96) * 4);
                        x += dir == 0 ? 1 : dir == 1 ? -1 : 0;
                        z += dir == 2 ? 1 : dir == 3 ? -1 : 0;
                    }
                    if (inDais(x, -d, z)) blocks.put(key(x, -d, z), Kind.AURORA_STONE);
                    else below.put(key(x, -d, z), Kind.AURORA_STONE);
                }
            }
        }

        private static int hashInt(int a, int b, int c) { return (int) (hsh(a, b, c, 95) * Integer.MAX_VALUE); }
```

(Checked before it was written: over plots 0 to 299 on flat ground, every root hangs from the vein above it, the
deepest is 7, the furthest out is 7, there are 10 to 64 root blocks below the dais and at least 8 aurora stone blocks
inside it below the floor, and every plot has a vein under the pool.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew.bat test --tests "com.pocketdimensions.manager.LandingDaisTest"`
Expected: PASS, 15 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/pocketdimensions/manager/LandingDais.java src/test/java/com/pocketdimensions/manager/LandingDaisTest.java
git commit -m "The Stone Dais is plain stone, and roots of aurora stone run down from its vines through the dais and into the ground below"
```

---

### Task 2: Aurora stone on vanilla stone; heartstone goes

**Files:**
- Modify: `tools/anchor/dais.mjs`, `tools/anchor/test/dais.test.mjs`, `tools/anchor/build.mjs` (the dais section)
- Delete (generated; `git rm`):
  - `src/main/resources/assets/pocketdimensions/textures/block/heartstone.png`;
  - `models/block/heartstone.json`;
  - `blockstates/heartstone.json`;
  - `items/heartstone.json`.
- Delete: `src/main/resources/data/pocketdimensions/loot_table/blocks/heartstone.json`
- Modify:
  - `init/ModBlocks.java`, `init/ModItems.java` and `init/ModCreativeTabs.java` (remove heartstone);
  - `lang/en_us.json`;
  - `data/minecraft/tags/block/mineable/pickaxe.json`.
- Test: `src/test/java/com/pocketdimensions/init/DaisBlocksResourcesTest.java`

**Interfaces:**
- Produces:
  - `models/block/aurora_stone.json` with textures `rock` and `particle` set to `minecraft:block/stone`;
  - no `ModBlocks.HEARTSTONE` and no `ModItems.HEARTSTONE_ITEM`. Task 3 must not use them.

- [ ] **Step 1: Change the tests first**

`DaisBlocksResourcesTest`:
- `BLOCKS` becomes `{"aurora_stone"}`.
- In `eachHasANameABlockstateAndAnItem`, drop the Heartstone lang assertion and add:

```java
        assertFalse(lang.has("block.pocketdimensions.heartstone"), "heartstone is gone: the dais is plain stone");
        assertFalse(Files.exists(ASSETS.resolve("blockstates/heartstone.json")), "heartstone is gone");
```

- In `auroraStoneIsTheHeartstoneWithGlowingVeinsOnTop` (rename it `auroraStoneIsVanillaStoneWithGlowingVeinsOnTop`),
  change the rock assertion to:

```java
        assertEquals("minecraft:block/stone", m.getAsJsonObject("textures").get("rock").getAsString(),
                "the rock is vanilla stone's own texture, so it matches the stone around it (and any resource pack)");
```

`tools/anchor/test/dais.test.mjs`:
- In the import, change `heartstone` to `STONE_GREY`.
- Replace the test `'heartstone: the Core rock, 16 x 16, opaque'` with:

```js
test('the rim is mixed into vanilla stone grey (126, its average), not a rock of our own', () => {
  assert.deepEqual(STONE_GREY, [126, 126, 126]);
  const r = frame('rim', 0);
  for (let i = 0; i < 256; i++) if (VEINS.mask[i] === 0.5)
    for (let ch = 0; ch < 3; ch++) assert.ok(Math.abs(r[i * 4 + ch] - 126) < 90, `rim pixel ${i} stays near grey`);
});
```

- [ ] **Step 2: Run them to verify they fail**

Run: `node --test tools/anchor/test/dais.test.mjs` and `./gradlew.bat test --tests "com.pocketdimensions.init.DaisBlocksResourcesTest"`
Expected:
- Node FAILS: `STONE_GREY` is not exported.
- JUnit FAILS: the heartstone lang key and blockstate still exist, and the rock is `pocketdimensions:block/heartstone`.

- [ ] **Step 3: Implement**

In `tools/anchor/dais.mjs`, replace the `rock` and `heartstone` functions (and their javadocs) with:

```js
/** Vanilla stone's average grey (its pixels run from 104 to 143): the rim's veins are mixed into it. */
export const STONE_GREY = [126, 126, 126];
```

In `frame`, change the rim colour line

```js
      : rock(x, y).map((r, ch) => r + (au[ch] * 0.35 - r) * (0.35 + 0.2 * p));
```

to

```js
      : STONE_GREY.map((r, ch) => r + (au[ch] * 0.35 - r) * (0.35 + 0.2 * p));
```

Update the file's header comment: "heartstone, the Core's weathered rock, and aurora stone's two animated overlays" becomes "aurora stone's two
animated overlays (drawn over vanilla stone)".

In `tools/anchor/build.mjs`:
- The import becomes `import { strip as daisStrip, FRAMES as DAIS_FRAMES, FRAMETIME as DAIS_FRAMETIME } from './dais.mjs';`.
- In the dais section:
  - delete the `heartstone.png` write and the `models/block/heartstone.json` write;
  - in the aurora stone model, set `particle` and `rock` to `'minecraft:block/stone'`;
  - the blockstate and item loop runs over `['aurora_stone']` only;
  - the log line becomes `dais: aurora_stone (veins + rim, ${DAIS_FRAMES} frames)`;
  - update the section comment to "aurora stone (vanilla stone with two animated overlays, ...)".

Then remove heartstone everywhere else:

```bash
git rm src/main/resources/assets/pocketdimensions/textures/block/heartstone.png src/main/resources/assets/pocketdimensions/models/block/heartstone.json src/main/resources/assets/pocketdimensions/blockstates/heartstone.json src/main/resources/assets/pocketdimensions/items/heartstone.json src/main/resources/data/pocketdimensions/loot_table/blocks/heartstone.json
```

- **`ModBlocks.java`:** delete the `HEARTSTONE` entry. Its comment becomes "aurora stone, veining the Stone Dais the
  World Core lands on (RL-024): stone with the aurora flowing in thin veins".
- **`ModItems.java`:** delete `HEARTSTONE_ITEM`.
- **`ModCreativeTabs.java`:** delete `output.accept(ModItems.HEARTSTONE_ITEM.get());`.
- **`en_us.json`:** delete the `block.pocketdimensions.heartstone` line.
- **`pickaxe.json`:** delete `"pocketdimensions:heartstone",`.

- [ ] **Step 4: Regenerate and run the tests**

Run: `node tools/anchor/build.mjs && node --test tools/anchor/test/*.test.mjs && git status --short`
Expected:
- the Node tests pass;
- `git status` shows changed `aurora_stone_rim.png` and `models/block/aurora_stone.json`, the deleted heartstone
  files, and the edited sources;
- `aurora_stone_veins.png` is unchanged (the veins never used the rock).

Run: `./gradlew.bat test --tests "com.pocketdimensions.init.DaisBlocksResourcesTest"`
Expected: PASS. (`RealmManager` still uses `ModBlocks.HEARTSTONE` until Task 3, so the compile fails here. Do Task 3's
Step 1 code edit before running Gradle, and commit Tasks 2 and 3 separately. Ruling ledgered if needed.)

- [ ] **Step 5: Commit** (after Task 3 Step 1 makes the build compile)

```bash
git add -A tools/anchor src/main/resources src/main/java/com/pocketdimensions/init src/test/java/com/pocketdimensions/init
git commit -m "Aurora stone is vanilla stone with the veins on top; the heartstone block is gone"
```

---

### Task 2b: The aurora's colours: dancing teal with swatches

The user's ask (after round 1): "mainly dancing teal with swatches of the pink, blue and purple like a real aurora,
instead of cycling between the solid colours".

The new colour rule:
- The veins' body is teal, rippling between a deep and a bright teal along the veins.
- Three accents (pink, blue, purple) drift through as soft swatches. Each is a travelling wave that is periodic in 16
  px (so blocks tile seamlessly) and in 32 s (so the strip loops).
- Each accent fades in and out over the loop.

At any moment about two thirds of the vein is plain teal (checked before writing this: 0.67 on the studies pattern).

**Files:**
- Modify: `tools/anchor/dais.mjs` (replace `AURORA`/`aurora` with `TEAL`, `ACCENTS`, `accents`, `auroraAt`; use
  `auroraAt` in `frame`)
- Test: `tools/anchor/test/dais.test.mjs`

- [ ] **Step 1: Write the failing tests**

Add `TEAL, accents` to the import, and add:

```js
test('the aurora is mainly teal: most of the vein, most of the time, shows no accent', () => {
  let n = 0, teal = 0;
  for (let k = 0; k < FRAMES; k++) for (let i = 0; i < 256; i++) if (VEINS.mask[i] === 1) {
    n++; if (accents(i % 16, i >> 4, k * LOOP_SECONDS / FRAMES).reduce((a, b) => a + b) < 0.2) teal++;
  }
  assert.ok(teal / n >= 0.6, `teal share ${(teal / n).toFixed(2)}`);
  assert.ok(TEAL.every(c => c[1] > c[0] && c[2] > c[0]), 'teal: more green and blue than red');
});

test('swatches of pink, blue and purple each show strongly somewhere in the loop', () => {
  const peak = [0, 0, 0];
  for (let k = 0; k < FRAMES; k++) for (let i = 0; i < 256; i++) if (VEINS.mask[i] === 1)
    accents(i % 16, i >> 4, k * LOOP_SECONDS / FRAMES).forEach((w, c) => { peak[c] = Math.max(peak[c], w); });
  peak.forEach((p, c) => assert.ok(p > 0.6, `accent ${c} peaks at ${p.toFixed(2)}`));
});

test('the swatches tile across blocks and loop in time', () => {
  for (const [x, y, t] of [[3, 5, 1.5], [0, 15, 20], [11, 2, 31]]) {
    assert.deepEqual(accents(x + 16, y, t).map(v => v.toFixed(9)), accents(x, y, t).map(v => v.toFixed(9)));
    assert.deepEqual(accents(x, y + 16, t).map(v => v.toFixed(9)), accents(x, y, t).map(v => v.toFixed(9)));
    assert.deepEqual(accents(x, y, t + LOOP_SECONDS).map(v => v.toFixed(9)), accents(x, y, t).map(v => v.toFixed(9)));
  }
});
```

- [ ] **Step 2: Run them to verify they fail**

Run: `node --test tools/anchor/test/dais.test.mjs`
Expected: FAIL with "does not provide an export named 'TEAL'" (or `accents`).

- [ ] **Step 3: Implement**

In `dais.mjs`, replace the `AURORA` constant and the `aurora` function with:

```js
export const TEAL = [[20, 190, 160], [90, 255, 215]];                       // deep and bright teal: the aurora's body
const ACCENTS = [                                                           // swatches drifting through it
  { c: [255, 110, 210], k: [1, 2, 3], ph: 0.0 },                           // pink
  { c: [80, 150, 255], k: [2, -1, -2], ph: 0.37 },                         // blue
  { c: [185, 110, 255], k: [-1, 1, 4], ph: 0.71 },                         // purple
];
const smoothstep = (a, b, v) => { const t = Math.max(0, Math.min(1, (v - a) / (b - a))); return t * t * (3 - 2 * t); };

/**
 * How strongly each accent (pink, blue, purple) shows at pixel (x, y), t seconds in: a wave travelling across the
 * block (whole cycles per 16 px, so blocks tile, and per loop, so the strip loops), only its crest showing, and each
 * colour coming and going over the loop. Mostly 0: the aurora is mainly teal.
 */
export function accents(x, y, t) {
  return ACCENTS.map(({ k, ph }) => {
    const wave = 0.5 + 0.5 * Math.sin(TAU * (k[0] * x / 16 + k[1] * y / 16 + k[2] * t / LOOP_SECONDS + ph));
    const gate = 0.5 + 0.5 * Math.sin(TAU * (t / LOOP_SECONDS + ph * 2));
    return smoothstep(0.82, 0.99, wave) * gate * gate;
  });
}

/** The aurora's colour at pixel (x, y), `along` its vein, t seconds in: dancing teal with swatches of pink, blue, purple. */
export function auroraAt(x, y, along, t) {
  const shimmer = 0.5 + 0.5 * Math.sin(TAU * (along * 2 + x / 16 - 3 * t / LOOP_SECONDS));   // teal rippling deep to bright
  let c = TEAL[0].map((v, i) => v + (TEAL[1][i] - v) * shimmer);
  accents(x, y, t).forEach((w, i) => { c = c.map((v, ch) => v + (ACCENTS[i].c[ch] - v) * w); });
  return c;
}
```

(`TAU` must be declared before these. Move the line `const TAU = Math.PI * 2, clamp = ...` above them.)

In `frame`, the colour line becomes `au = auroraAt(x, y, a, t)`.

Update the module's header comment: the veins are "dancing teal with drifting swatches of pink, blue and purple".

- [ ] **Step 4: Run, regenerate, look**

Run: `node --test tools/anchor/test/dais.test.mjs && node tools/anchor/build.mjs`
Expected: all dais tests pass; `aurora_stone_veins.png` and `aurora_stone_rim.png` change.

- [ ] **Step 5: Commit**

```bash
git add tools/anchor/dais.mjs tools/anchor/test/dais.test.mjs src/main/resources/assets/pocketdimensions/textures/block
git commit -m "Aurora stone's veins dance in teal with drifting swatches of pink, blue and purple, like a real aurora, instead of cycling through solid colours"
```

---

### Task 3: RealmManager builds the dais of stone and sets the roots

**Files:**
- Modify: `src/main/java/com/pocketdimensions/manager/RealmManager.java` (`buildDais`)

**Interfaces:**
- Consumes:
  - `LandingDais.Plan.roots()` and `Kind.STONE` (Task 1);
  - no heartstone (Task 2).

The world side has no unit test. The rule it applies is `LandingDais.isGround`, which is already tested; Task 4 checks
it in game.

- [ ] **Step 1: Implement**

In `buildDais`:
- `BlockState rock = ModBlocks.HEARTSTONE.get().defaultBlockState();` becomes
  `BlockState rock = Blocks.STONE.defaultBlockState();`.
- In the dais loop, `ModBlocks.HEARTSTONE` becomes `Blocks.STONE`, and the line becomes
  `BlockState state = b.kind() == LandingDais.Kind.AURORA_STONE ? ModBlocks.AURORA_STONE.get().defaultBlockState() : Blocks.STONE.defaultBlockState();`.
  Set it with `level.setBlock(p.set(...), state, DAIS_FLAGS)`.
- After the dais loop, add:

```java
        BlockState aurora = ModBlocks.AURORA_STONE.get().defaultBlockState();
        for (LandingDais.Block b : plan.roots())                    // roots go on down only through natural solid ground
            if (canRootInto(level, p.set(cx + b.dx(), b.y(), cz + b.dz()))) level.setBlock(p, aurora, DAIS_FLAGS);
```

- Add next to `isGround`:

```java
    /** Whether a root may run through this block: natural solid ground, never a fluid, air or anything with contents. */
    private static boolean canRootInto(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getFluidState().isEmpty() && isGround(s) && level.getBlockEntity(pos) == null;
    }
```

- Update `buildDais`'s javadoc: "...then the dais's stone and aurora stone set, and its roots run on into the ground
  below."

- [ ] **Step 2: Run the whole suite and the build**

Run: `./gradlew.bat test && ./gradlew.bat build`
Expected: BUILD SUCCESSFUL; all tests pass (180 before, plus the 4 new ones in Task 1, so 184).

- [ ] **Step 3: Commit** (Task 2's commit first, then this one)

```bash
git add src/main/java/com/pocketdimensions/manager/RealmManager.java
git commit -m "The dais is built of stone, and its aurora stone roots run down into natural ground below (never into caves, water or chests)"
```

---

### Task 4: Docs, then the in-game check

**Files:**
- Modify:
  - `PRD.md` (RL-024 row);
  - `README.md` (the Stone Dais paragraph);
  - `pocket-worlds.md` (the Stone Dais bullet);
  - `CLAUDE.md` (gitignored: the `ModBlocks` note and the `dais.mjs` note).

- [ ] **Step 1: Update the docs**

- **PRD RL-024:** "on a Stone Dais 9 across: heartstone (the Core's rock) with six vines" becomes "on a Stone Dais 9
  across: plain stone with six vines", and add "; roots of aurora stone run down through the dais into the ground".
- **README:** in "The Stone Dais" paragraph:
  - "of **Heartstone** (the Core's own rock)" becomes "of plain stone";
  - "the same rock with thin veins" becomes "stone with thin veins";
  - add "and roots of it run down into the ground below";
  - "Both can be mined" becomes "It can be mined".
- **pocket-worlds.md:** "a small Stone Dais of heartstone veined with aurora stone" becomes "a small Stone Dais of
  stone veined with aurora stone, its roots running down into the ground".
- **CLAUDE.md:**
  - "(incl. heartstone and aurora_stone, the Core's Stone Dais)" becomes "(incl. aurora_stone, veining the Core's
    Stone Dais)";
  - "Heartstone + Aurora Stone textures" becomes "Aurora Stone overlays (veins and rim strips, over vanilla stone)".

Run: `grep -rn -i "heartstone" --include=*.md --include=*.java --include=*.mjs --include=*.json . | grep -v "docs/superpowers\|\.superpowers/"`
Expected: no output.

- [ ] **Step 2: Commit**

```bash
git add PRD.md README.md pocket-worlds.md
git commit -m "Docs: the Stone Dais is plain stone veined with aurora stone, its roots running into the ground"
```

- [ ] **Step 3: The in-game check**

Start runServer and runClient in the background as before, check the logs for errors about `aurora_stone`, then ask
the user to:
1. **Grow a new realm.** The dais should be stone, and the vines' rock should match the stone around them.
2. **Dig down** next to a vine and under the Core. Aurora stone roots should show in the dais and a few blocks into
   the ground.
3. **Check a slope or a cave below.** No aurora stone should float in the air under a ledge.

Note for the user: realms grown in round 1 lose their heartstone blocks, which turn to air, because the block no
longer exists.
