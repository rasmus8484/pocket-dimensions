# Realm Ward Part A: Veil and ceiling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every realm is walled by an unbreakable, full-height Veil of mist blocks generated just outside its plot, and players are held under a ceiling at feet y 318 with no building at y 318 and 319, so nobody can see past their own realm.

**Architecture:** Two pure, unit-tested rule classes (`VeilLayout` for where Veil columns and colour bands go, `RealmWard` for the ceiling and no-build heights) carry all the logic. `RealmChunkGenerator.buildSurface` places Veil columns as chunks generate; `RealmEventHandler` enforces the ceiling on the server and refuses block placement; a small client class clamps the local player so the ceiling feels solid. Textures come from the existing Node texture tools.

**Tech Stack:** Minecraft 1.21.11, Forge 61.1.0, Java 21, JUnit 5 (`src/test`), Node (texture tools in `tools/anchor/`, `node --test`).

**Spec:** `docs/superpowers/specs/2026-10-09-realm-ward-design.md` (Part A)

## Global Constraints

- Never use em dashes, or spaced en dashes as a substitute, anywhere: code, comments, docs, commit messages.
- No `Co-Authored-By` lines in commits.
- Never run gradle (build, test, compile) while `runServer` or `runClient` is running: stop both first (it crashes them).
- 1.21.11 APIs only: `Identifier`, not `ResourceLocation`; `ValueInput`/`ValueOutput`; `ChunkAccess.setBlockState(BlockPos, BlockState, int flags)`.
- Heights: the realm keeps y -64 to 319. Ceiling: feet held at or below y 318 (`RealmWard.CEILING_Y`). No blocks placed at y 318 or 319 (`RealmWard.NO_BUILD_Y` = 318).
- The Veil is the ring of block columns just outside each plot: x = minX - 1 and x = maxX (max exclusive), z from minZ - 1 to maxZ, and the same with x and z swapped. Every grid cell gets one.
- Veil blockstate `band` 0..7, 48 blocks per band from y -64.
- `realm_padding_chunks` minimum becomes 1.
- Player-facing text uses the mod's storytelling tone. The Veil block's name is "The Veil".
- Do not change the realm's dimension type.

## Review Focus

1. A player whose head would be inside a block when held at the ceiling (a block pushed by a piston or a falling block to y 318 or 319, or natural terrain that high): they must land on a free spot below, not inside a block. Pinned by the SafeSpot fallback in Task 5 (manual check: `/setblock` stone at y 319, stand on a pillar to 317, jump).
2. Riding a horse or flying an elytra into the ceiling: the mount must be held too (the rider's height above the mount kept), and an honest client must not be rubber-banded every tick. Pinned by the client clamp in Task 5 and the server's 1-block slack (`RealmWard.SERVER_SLACK`).
3. Lakes, geodes and lava pools generated after the Veil must not cut holes in it. Pinned by the `features_cannot_replace` and `lava_pool_stone_cannot_replace` tags in Task 2.
4. Beds and doors (two-block placements) reaching into y 318: refused through `EntityMultiPlaceEvent` as well as `EntityPlaceEvent`. Pinned in Task 5.
5. Columns far from the origin and at negative coordinates: `VeilLayout` uses `Math.floorMod` so the ring is the same in every cell. Pinned by tests in Task 1.

---

### Task 1: VeilLayout (where the Veil goes, and its bands)

**Files:**
- Create: `src/main/java/com/pocketkingdoms/manager/VeilLayout.java`
- Test: `src/test/java/com/pocketkingdoms/manager/VeilLayoutTest.java`

**Interfaces:**
- Produces: `VeilLayout.BANDS` (int, 8); `static boolean isVeilColumn(int x, int z, int sideBlocks, int cellBlocks)`; `static int band(int y, int minY, int height)` returning 0..7.

- [ ] **Step 1: Write the failing test**

```java
package com.pocketkingdoms.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Veil: the ring of block columns just outside each realm's plot, and its colour bands by height. */
class VeilLayoutTest {

    // plots 32 blocks across, one every 48 blocks: plot 0 is x and z in [0, 32), plot 1 starts at x 48
    private static final int SIDE = 32, CELL = 48;
    private static boolean veil(int x, int z) { return VeilLayout.isVeilColumn(x, z, SIDE, CELL); }

    @Test
    void insideAPlotIsNeverVeil() {
        assertFalse(veil(0, 0));
        assertFalse(veil(31, 31));
        assertFalse(veil(16, 5));
        assertFalse(veil(48 * 3 + 10, 48 * 2 + 10), "inside a plot further out");
    }

    @Test
    void theColumnsJustOutsideEachSideAreVeil() {
        assertTrue(veil(32, 10), "east, at maxX");
        assertTrue(veil(-1, 10), "west, at minX - 1");
        assertTrue(veil(10, 32), "south, at maxZ");
        assertTrue(veil(10, -1), "north, at minZ - 1");
    }

    @Test
    void theCornersAreVeil() {
        assertTrue(veil(32, 32));
        assertTrue(veil(-1, -1));
        assertTrue(veil(32, -1));
        assertTrue(veil(-1, 32));
    }

    @Test
    void theGapBeyondTheRingIsNotVeil() {
        assertFalse(veil(33, 10), "one past the east wall");
        assertFalse(veil(40, 10), "middle of the gap");
        assertFalse(veil(40, 40), "middle of a gap crossing");
        assertFalse(veil(32, 33), "past the end of the east wall");
    }

    @Test
    void theNextPlotHasItsOwnRing() {
        assertTrue(veil(47, 10), "west wall of plot 1 (x 48)");
        assertTrue(veil(80, 10), "east wall of plot 1");
        assertTrue(veil(48 * 9999 + 32, 48 * 3 + 5), "far along the row");
    }

    @Test
    void bandsSplitTheWorldsHeightIntoEight() {
        assertEquals(0, VeilLayout.band(-64, -64, 384));
        assertEquals(0, VeilLayout.band(-17, -64, 384));
        assertEquals(1, VeilLayout.band(-16, -64, 384));
        assertEquals(2, VeilLayout.band(63, -64, 384), "sea level is pale mist");
        assertEquals(7, VeilLayout.band(319, -64, 384));
    }

    @Test
    void bandsAreClampedOutsideTheWorld() {
        assertEquals(0, VeilLayout.band(-100, -64, 384));
        assertEquals(7, VeilLayout.band(400, -64, 384));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests com.pocketkingdoms.manager.VeilLayoutTest`
Expected: FAIL, compilation error "cannot find symbol: class VeilLayout".

- [ ] **Step 3: Write minimal implementation**

```java
package com.pocketkingdoms.manager;

/**
 * Where the Veil stands: the ring of block columns just outside each realm's plot, and which colour band a block of it
 * is in. Plain logic, unit tested; RealmChunkGenerator places the blocks.
 */
public final class VeilLayout {

    /** Colour bands from the bottom of the world to the top. */
    public static final int BANDS = 8;

    private VeilLayout() {}

    /**
     * Whether block column (x, z) is part of a Veil. Plots start every cellBlocks on both axes and are sideBlocks
     * across; the ring runs just outside one (x = minX - 1 and x = maxX, z from minZ - 1 to maxZ, and the same with x and
     * z swapped). Needs a gap of at least two blocks (cellBlocks - sideBlocks >= 2), which a one-chunk padding gives.
     */
    public static boolean isVeilColumn(int x, int z, int sideBlocks, int cellBlocks) {
        int lx = Math.floorMod(x, cellBlocks), lz = Math.floorMod(z, cellBlocks);
        return (wall(lx, sideBlocks, cellBlocks) && along(lz, sideBlocks, cellBlocks))
                || (wall(lz, sideBlocks, cellBlocks) && along(lx, sideBlocks, cellBlocks));
    }

    /** On a wall line: just past a plot's max, or just before the next plot's min. */
    private static boolean wall(int local, int side, int cell) { return local == side || local == cell - 1; }

    /** Within a wall's length: the plot's span plus one column at each end (the corners). */
    private static boolean along(int local, int side, int cell) { return local <= side || local == cell - 1; }

    /** The band (0 at the bottom, BANDS - 1 at the top) of a Veil block at y in a world from minY, height blocks tall. */
    public static int band(int y, int minY, int height) {
        int b = (int) Math.floorDiv((long) (y - minY) * BANDS, height);
        return Math.max(0, Math.min(BANDS - 1, b));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat test --tests com.pocketkingdoms.manager.VeilLayoutTest`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/pocketkingdoms/manager/VeilLayout.java src/test/java/com/pocketkingdoms/manager/VeilLayoutTest.java
git commit -m "VeilLayout: the ring of columns just outside each realm plot, and its eight colour bands"
```

---

### Task 2: The Veil block (block, textures, model, tags, name)

**Files:**
- Create: `src/main/java/com/pocketkingdoms/block/VeilBlock.java`
- Modify: `src/main/java/com/pocketkingdoms/init/ModBlocks.java` (register `VEIL`)
- Create: `tools/anchor/veil.mjs`, `tools/anchor/test/veil.test.mjs`
- Modify: `tools/anchor/build.mjs` (write the Veil's textures, models and blockstate)
- Generated by the build: `src/main/resources/assets/pocketkingdoms/textures/block/veil_0.png` .. `veil_7.png` (+ `.png.mcmeta`), `models/block/veil_0.json` .. `veil_7.json`, `blockstates/veil.json`
- Modify: `src/main/resources/data/minecraft/tags/block/wither_immune.json`, `dragon_immune.json`; create `features_cannot_replace.json`, `lava_pool_stone_cannot_replace.json` in the same folder
- Modify: `src/main/resources/assets/pocketkingdoms/lang/en_us.json`

**Interfaces:**
- Consumes: `VeilLayout.BANDS` (Task 1).
- Produces: `ModBlocks.VEIL` (`RegistryObject<Block>`); `VeilBlock.BAND` (`IntegerProperty` 0..7); `VeilBlock.state(int band)` returning the `BlockState`.

- [ ] **Step 1: Write the failing texture test**

`tools/anchor/test/veil.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FRAMES, BAND_COLOURS, veilTexture } from '../veil.mjs';

const avg = rgba => {
  let s = 0;
  for (let i = 0; i < rgba.length; i += 4) s += rgba[i] + rgba[i + 1] + rgba[i + 2];
  return s / (rgba.length / 4) / 3;
};

test('each band is a 16 x 16 strip of animation frames, fully opaque', () => {
  for (let b = 0; b < 8; b++) {
    const rgba = veilTexture(b);
    assert.equal(rgba.length, 16 * 16 * FRAMES * 4);
    for (let i = 3; i < rgba.length; i += 4) assert.equal(rgba[i], 255, `band ${b} pixel ${(i - 3) / 4}`);
  }
});

test('eight bands, dark at the bottom, the realm sky colour at the top', () => {
  assert.equal(BAND_COLOURS.length, 8);
  assert.ok(avg(veilTexture(0)) < avg(veilTexture(2)), 'underground is darker than ground level');
  assert.deepEqual(BAND_COLOURS[7], [120, 167, 255], 'top band is the realm sky #78a7ff');
  assert.deepEqual(BAND_COLOURS[5], [192, 216, 255], 'a high band is the realm fog #c0d8ff');
});

test('the mist moves: consecutive frames differ', () => {
  const rgba = veilTexture(3), frame = 16 * 16 * 4;
  let diff = 0;
  for (let i = 0; i < frame; i++) diff += Math.abs(rgba[i] - rgba[frame + i]);
  assert.ok(diff > 0);
});

test('the texture tiles: the left and right columns are close in colour', () => {
  const rgba = veilTexture(3);
  for (let y = 0; y < 16; y++) {
    const l = (y * 16) * 4, r = (y * 16 + 15) * 4;
    assert.ok(Math.abs(rgba[l] - rgba[r]) < 40, `row ${y}`);
  }
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `node --test tools/anchor/test/veil.test.mjs`
Expected: FAIL, "Cannot find module '../veil.mjs'".

- [ ] **Step 3: Write the texture generator**

`tools/anchor/veil.mjs`:

```js
// The Veil: the ward's wall around every realm. A full-height curtain of drifting mist with a few faint rune glints,
// coloured by height band: dark indigo underground, pale mist at ground level, the realm's fog and then its sky colour
// at the top, so from far away the wall melts into the horizon haze.
import { hash3 } from './runebound.mjs';

export const FRAMES = 16;
// bottom (y -64) to top (y 319); 5 is the realm's fog colour #c0d8ff, 7 its sky colour #78a7ff (realm_type.json)
export const BAND_COLOURS = [
  [30, 26, 64], [86, 92, 146], [172, 188, 222], [182, 200, 236],
  [188, 210, 248], [192, 216, 255], [156, 192, 255], [120, 167, 255],
];
const GLINT = [235, 250, 255];

// smooth value noise on a 4 x 4 lattice that wraps every 16 pixels, so the texture tiles and the loop is seamless
const lattice = (i, j, seed) => hash3(((i % 4) + 4) % 4, ((j % 4) + 4) % 4, seed);
const smooth = t => t * t * (3 - 2 * t);
function noise(x, y, seed) {
  const gx = x / 4, gy = y / 4, i = Math.floor(gx), j = Math.floor(gy), fx = smooth(gx - i), fy = smooth(gy - j);
  const a = lattice(i, j, seed), b = lattice(i + 1, j, seed), c = lattice(i, j + 1, seed), d = lattice(i + 1, j + 1, seed);
  return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
}

/** Brightness factor of pixel (x, y) in frame f: two mist layers drifting opposite ways, one full period per loop. */
function mist(x, y, f) {
  const s = (f / FRAMES) * 16;
  return 0.78 + 0.16 * noise(x + s, y, 31) + 0.10 * noise(x - s, y + 5, 57) - 0.02;
}

/** The band's texture: FRAMES frames of 16 x 16, stacked top to bottom (Minecraft's animated texture layout). */
export function veilTexture(band) {
  const base = BAND_COLOURS[band], rgba = new Uint8Array(16 * 16 * FRAMES * 4);
  for (let f = 0; f < FRAMES; f++) for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const k = mist(x, y, f);
    let c = base.map(v => Math.max(0, Math.min(255, Math.round(v * k))));
    // a faint rune glint drifts with the first mist layer
    const gx = ((x + Math.round((f / FRAMES) * 16)) % 16 + 16) % 16;
    if (hash3(gx, y, band + 90) > 0.985) c = c.map((v, i) => Math.round(v + (GLINT[i] - v) * 0.35));
    rgba.set([...c, 255], ((f * 16 + y) * 16 + x) * 4);
  }
  return rgba;
}
```

- [ ] **Step 4: Run the texture test to verify it passes**

Run: `node --test tools/anchor/test/veil.test.mjs`
Expected: PASS, 4 tests. (If the tiling test fails, the noise's x period is wrong: `lattice` must wrap every 4 cells of 4 pixels.)

- [ ] **Step 5: Add the Veil to the asset build**

In `tools/anchor/build.mjs`, add the import beside the other design imports:

```js
import { veilTexture, FRAMES as VEIL_FRAMES } from './veil.mjs';
```

and at the end of the file:

```js
// The Veil: one full-bright cube model and one animated texture per height band
const veilVariants = {};
for (let b = 0; b < 8; b++) {
  write(`textures/block/veil_${b}.png`, encodePNG(16, 16 * VEIL_FRAMES, veilTexture(b)));
  write(`textures/block/veil_${b}.png.mcmeta`, json({ animation: { frametime: 3, interpolate: true } }));
  const face = dir => ({ uv: [0, 0, 16, 16], texture: '#all', cullface: dir });
  write(`models/block/veil_${b}.json`, json({
    ambientocclusion: false,
    textures: { all: `pocketkingdoms:block/veil_${b}`, particle: `pocketkingdoms:block/veil_${b}` },
    elements: [{ from: [0, 0, 0], to: [16, 16, 16], shade: false, light_emission: 15,
      faces: { north: face('north'), south: face('south'), east: face('east'), west: face('west'), up: face('up'), down: face('down') } }],
  }));
  veilVariants[`band=${b}`] = { model: `pocketkingdoms:block/veil_${b}` };
}
write('blockstates/veil.json', json({ variants: veilVariants }));
```

(`json` is the helper `build.mjs` already uses for the World Seed's files.)

Run: `node tools/anchor/build.mjs`
Expected: no errors; `git status` shows the 8 Veil textures, 8 `.mcmeta` files, 8 models and `blockstates/veil.json` as new, and no other generated file changed.

- [ ] **Step 6: Write the block class**

`src/main/java/com/pocketkingdoms/block/VeilBlock.java`:

```java
package com.pocketkingdoms.block;

import com.pocketkingdoms.manager.VeilLayout;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The Veil: the realm's ward made visible, a wall of drifting mist along every edge of a realm from the bottom of the
 * world to the top. It can't be broken, blown up or moved, and it hides whatever lies beyond. BAND picks its colour by
 * height (VeilLayout); RealmChunkGenerator raises it as the realm generates. Players never get it as an item.
 */
public class VeilBlock extends Block {

    public static final IntegerProperty BAND = IntegerProperty.create("band", 0, VeilLayout.BANDS - 1);

    public VeilBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BAND, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BAND);
    }

    /** The Veil block for a height band. */
    public BlockState state(int band) {
        return defaultBlockState().setValue(BAND, band);
    }
}
```

- [ ] **Step 7: Register it**

In `src/main/java/com/pocketkingdoms/init/ModBlocks.java`, add the import `net.minecraft.world.level.material.PushReaction;` and, after `BOUNDARY_BLOCK`:

```java
    public static final RegistryObject<Block> VEIL = BLOCKS.register("veil",
            () -> new VeilBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("veil"))
                    .strength(-1.0f, 3600000.0f)
                    .sound(SoundType.AMETHYST)
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable()
                    .isValidSpawn((state, level, pos, type) -> false)));
```

No `BlockItem` is registered for it (players never hold it; `/setblock pocketkingdoms:veil` still works).

- [ ] **Step 8: Tags and name**

Add `"pocketkingdoms:veil"` to the `values` of `src/main/resources/data/minecraft/tags/block/wither_immune.json` and `dragon_immune.json`. Create `features_cannot_replace.json` and `lava_pool_stone_cannot_replace.json` in the same folder, each:

```json
{
  "replace": false,
  "values": [
    "pocketkingdoms:veil"
  ]
}
```

In `src/main/resources/assets/pocketkingdoms/lang/en_us.json` add `"block.pocketkingdoms.veil": "The Veil",` beside the other block names.

- [ ] **Step 9: Compile, and check the block in game**

Stop `runServer` and `runClient` if they are running. Run: `./gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL.

Start the client, and in a world: `/setblock ~ ~ ~2 pocketkingdoms:veil[band=2]`, then the other bands beside it.
Expected: animated pale-to-blue mist cubes, evenly lit at night, unbreakable in survival, not pushed by a piston, named "The Veil" in F3.

- [ ] **Step 10: Commit**

```bash
git add tools/anchor/veil.mjs tools/anchor/test/veil.test.mjs tools/anchor/build.mjs src/main/java/com/pocketkingdoms/block/VeilBlock.java src/main/java/com/pocketkingdoms/init/ModBlocks.java src/main/resources
git commit -m "The Veil block: unbreakable drifting mist in eight height bands, from dark indigo underground to the realm's sky colour at the top"
```

---

### Task 3: Raise the Veil as realms generate

**Files:**
- Modify: `src/main/java/com/pocketkingdoms/manager/RealmManager.java` (two public grid helpers)
- Modify: `src/main/java/com/pocketkingdoms/worldgen/RealmChunkGenerator.java` (override `buildSurface`)
- Modify: `src/main/java/com/pocketkingdoms/PocketKingdomsConfig.java` (padding minimum 1)

**Interfaces:**
- Consumes: `VeilLayout.isVeilColumn`, `VeilLayout.band`, `VeilLayout.BANDS` (Task 1); `ModBlocks.VEIL`, `VeilBlock.state(int)` (Task 2).
- Produces: `RealmManager.sideBlocks()` and `RealmManager.cellBlocks()` (public static int).

- [ ] **Step 1: Grid helpers**

In `RealmManager.java`, below `cellChunks()`:

```java
    /** Blocks across one plot, and between the starts of neighbouring plots (the plot plus the gap). */
    public static int sideBlocks()       { return sideChunks() * 16; }
    public static int cellBlocks()       { return cellChunks() * 16; }
```

- [ ] **Step 2: Padding minimum**

In `PocketKingdomsConfig.java`, change `.defineInRange("realm_padding_chunks", 1, 0, 10000);` to `.defineInRange("realm_padding_chunks", 1, 1, 10000);` and add to that setting's `.comment(...)` the line `"At least 1: the Veil stands in the gap."`.

- [ ] **Step 3: Place the Veil while a chunk generates**

In `RealmChunkGenerator.java` add imports `com.pocketkingdoms.block.VeilBlock`, `com.pocketkingdoms.init.ModBlocks`, `com.pocketkingdoms.manager.RealmManager`, `com.pocketkingdoms.manager.VeilLayout`, `net.minecraft.server.level.WorldGenRegion`, `net.minecraft.world.level.block.state.BlockState`, `net.minecraft.world.level.levelgen.RandomState`, and add a section after the structures section:

```java
    // -------------------------------------------------------------------------
    // The Veil
    // -------------------------------------------------------------------------

    /** Vanilla's surface, then the Veil in any of this chunk's columns that lie on a plot's ring (VeilLayout). */
    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
        super.buildSurface(region, structureManager, random, chunk);
        raiseVeil(chunk);
    }

    /**
     * Fills each Veil column from the bottom of the world to the top. Carvers and features run after the surface, and
     * the Veil is in features_cannot_replace (and the carvers' replaceable tag doesn't have it), so nothing cuts it.
     */
    private static void raiseVeil(ChunkAccess chunk) {
        int side = RealmManager.sideBlocks(), cell = RealmManager.cellBlocks();
        int minY = chunk.getMinY(), height = chunk.getHeight();
        VeilBlock veil = (VeilBlock) ModBlocks.VEIL.get();
        BlockState[] bands = new BlockState[VeilLayout.BANDS];
        for (int b = 0; b < bands.length; b++) bands[b] = veil.state(b);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = x0 + dx, z = z0 + dz;
            if (!VeilLayout.isVeilColumn(x, z, side, cell)) continue;
            for (int y = minY; y < minY + height; y++)
                chunk.setBlockState(pos.set(x, y, z), bands[VeilLayout.band(y, minY, height)], 0);
        }
    }
```

- [ ] **Step 4: Compile and run the whole test suite**

Stop `runServer` and `runClient` if running. Run: `./gradlew.bat test`
Expected: BUILD SUCCESSFUL, all tests pass (the earlier suites plus `VeilLayoutTest`).

- [ ] **Step 5: Check it in a fresh world**

Generation only changes new chunks, so use a new test world (the dev world's `run/config/pocketkingdoms-common.toml` has `realm_radius_chunks = 5`, which is fine). Start the client, make a world, link a World Anchor, enter the realm and walk to an edge (radius 5: about 72 blocks from the core).
Expected: the Veil stands along the edge from bedrock to the build limit, dark at the bottom of a cave and blue at the top; the border message never shows while walking into it (the wall stops you first); a tunnel dug along the edge meets the Veil, not open gap terrain.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/pocketkingdoms/manager/RealmManager.java src/main/java/com/pocketkingdoms/worldgen/RealmChunkGenerator.java src/main/java/com/pocketkingdoms/PocketKingdomsConfig.java
git commit -m "Realms generate the Veil just outside every plot, bottom to top; the gap between plots is at least one chunk"
```

---

### Task 4: RealmWard (the ceiling and no-build rules)

**Files:**
- Create: `src/main/java/com/pocketkingdoms/manager/RealmWard.java`
- Test: `src/test/java/com/pocketkingdoms/manager/RealmWardTest.java`

**Interfaces:**
- Produces: `RealmWard.CEILING_Y` (int 318), `RealmWard.NO_BUILD_Y` (int 318), `RealmWard.SERVER_SLACK` (double 1.0); `static boolean aboveCeiling(double feetY)`; `static boolean serverMustHold(double feetY)`; `static boolean buildRefused(int y)`.

- [ ] **Step 1: Write the failing test**

```java
package com.pocketkingdoms.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The ward's ceiling: feet are held at or below y 318, and nothing is built at y 318 or 319. */
class RealmWardTest {

    @Test
    void theCeilingHoldsFeetAboveY318() {
        assertFalse(RealmWard.aboveCeiling(317.5));
        assertFalse(RealmWard.aboveCeiling(318.0), "standing on a block at y 317 is allowed");
        assertTrue(RealmWard.aboveCeiling(318.01));
    }

    @Test
    void atTheCeilingTheEyesAreBelowTheVeilsTop() {
        assertTrue(RealmWard.CEILING_Y + 1.62 < 320, "eyes stay under the top surface of the Veil (y 320)");
    }

    @Test
    void theServerOnlyStepsInPastItsSlack() {
        assertFalse(RealmWard.serverMustHold(318.9), "an honest client may overshoot by up to one tick");
        assertTrue(RealmWard.serverMustHold(319.01));
    }

    @Test
    void noBuildingAtTheTopTwoRows() {
        assertFalse(RealmWard.buildRefused(317));
        assertTrue(RealmWard.buildRefused(318));
        assertTrue(RealmWard.buildRefused(319));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests com.pocketkingdoms.manager.RealmWardTest`
Expected: FAIL, "cannot find symbol: class RealmWard".

- [ ] **Step 3: Write minimal implementation**

```java
package com.pocketkingdoms.manager;

/**
 * The realm's ward overhead: players are held with their feet at or below CEILING_Y, so their eyes stay under the top
 * of the Veil and nobody can look over it, and no block is placed at NO_BUILD_Y or above, so nobody can stand above the
 * ceiling. Plain logic, unit tested; RealmEventHandler and the client's WardClient apply it.
 */
public final class RealmWard {

    /** Highest a player's feet may be in a realm. */
    public static final int CEILING_Y = 318;
    /** Lowest row where blocks may not be placed in a realm (318 and 319, the top of the world). */
    public static final int NO_BUILD_Y = 318;
    /** How far past the ceiling an honest client may get within one tick before it clamps itself; the server allows it. */
    public static final double SERVER_SLACK = 1.0;

    private RealmWard() {}

    public static boolean aboveCeiling(double feetY) { return feetY > CEILING_Y; }

    /** Whether the server holds the player itself (beyond what the client's own clamp can leave). */
    public static boolean serverMustHold(double feetY) { return feetY > CEILING_Y + SERVER_SLACK; }

    public static boolean buildRefused(int y) { return y >= NO_BUILD_Y; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat test --tests com.pocketkingdoms.manager.RealmWardTest`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/pocketkingdoms/manager/RealmWard.java src/test/java/com/pocketkingdoms/manager/RealmWardTest.java
git commit -m "RealmWard: the ceiling at feet y 318 and no building at y 318 and 319"
```

---

### Task 5: Enforce the ceiling and refuse building above it

**Files:**
- Modify: `src/main/java/com/pocketkingdoms/event/RealmEventHandler.java`
- Create: `src/main/java/com/pocketkingdoms/client/WardClient.java`
- Modify: `src/main/java/com/pocketkingdoms/client/ClientSetup.java` (register `WardClient`)

**Interfaces:**
- Consumes: `RealmWard.CEILING_Y`, `aboveCeiling`, `serverMustHold`, `buildRefused` (Task 4); `SafeSpot.nearest(ServerLevel, BlockPos, BlockPos, BlockPos, Predicate<BlockPos>)`, `SafeSpot.isFreeToStandIn(ServerLevel, BlockPos)` (existing).
- Produces: `WardClient.register()`.

- [ ] **Step 1: Server: hold players under the ceiling**

In `RealmEventHandler.onPlayerTick`, right after the line `long gameTime = serverPlayer.level().getGameTime();`, insert:

```java
        // The ward's ceiling: checked every tick (one comparison), unlike the border below
        if (RealmWard.serverMustHold(y)) {
            holdUnderCeiling(serverPlayer, info, x, z, yaw, pitch);
            return;
        }
```

and add this method below `onPlayerTick` (import `com.pocketkingdoms.manager.RealmWard`):

```java
    /**
     * Puts a player who got above the ward's ceiling back under it, on a free spot (a block pushed up to the top of the
     * world must not trap them), mount and all: a rider is moved with their mount, keeping their height above it.
     */
    private void holdUnderCeiling(ServerPlayer player, RealmManager.PlayerRealmInfo info, double x, double z, float yaw, float pitch) {
        ServerLevel realm = (ServerLevel) player.level();
        double y = RealmWard.CEILING_Y;
        BlockPos at = BlockPos.containing(x, y, z);
        if (!SafeSpot.isFreeToStandIn(realm, at) || !SafeSpot.isFreeToStandIn(realm, at.above())) {
            BlockPos min = new BlockPos(Math.max(info.minX + 1, at.getX() - 8), realm.getMinY() + 1, Math.max(info.minZ + 1, at.getZ() - 8));
            BlockPos max = new BlockPos(Math.min(info.maxX - 2, at.getX() + 8), RealmWard.CEILING_Y, Math.min(info.maxZ - 2, at.getZ() + 8));
            BlockPos safe = SafeSpot.nearest(realm, at, min, max, q -> false);
            if (safe != null) { x = safe.getX() + 0.5; y = safe.getY(); z = safe.getZ() + 0.5; }
        }
        net.minecraft.world.entity.Entity mount = player.getRootVehicle();
        if (mount != player && !(mount instanceof Player)) {
            double above = player.getY() - mount.getY();
            player.stopRiding();
            mount.teleportTo(x, y - above, z);
            player.connection.teleport(x, y, z, yaw, pitch);
            player.startRiding(mount, true, false);
        } else {
            player.connection.teleport(x, y, z, yaw, pitch);
        }
    }
```

- [ ] **Step 2: Server: refuse building at the top two rows**

In the constructor where the other listeners are added (beside `TickEvent.PlayerTickEvent.Post.BUS.addListener(this::onPlayerTick);`), add:

```java
        BlockEvent.EntityPlaceEvent.BUS.addListener(this::onPlaceAboveCeiling);
        BlockEvent.EntityMultiPlaceEvent.BUS.addListener(this::onMultiPlaceAboveCeiling);
```

and the handlers (import `net.minecraftforge.event.level.BlockEvent`; returning true cancels, as with `EntityTravelToDimensionEvent`):

```java
    /** Nothing is built at the top two rows of a realm, so nobody can stand above the ward's ceiling. */
    private boolean onPlaceAboveCeiling(BlockEvent.EntityPlaceEvent event) {
        return inRealm(event.getLevel()) && RealmWard.buildRefused(event.getPos().getY());
    }

    /** The same for beds, doors and other blocks placed as several at once. */
    private boolean onMultiPlaceAboveCeiling(BlockEvent.EntityMultiPlaceEvent event) {
        return inRealm(event.getLevel())
                && event.getReplacedBlockSnapshots().stream().anyMatch(s -> RealmWard.buildRefused(s.getPos().getY()));
    }

    private static boolean inRealm(net.minecraft.world.level.LevelAccessor level) {
        return level instanceof net.minecraft.world.level.Level l && l.dimension().equals(PocketKingdomsMod.REALM_DIM);
    }
```

If `EntityMultiPlaceEvent` is also delivered to `EntityPlaceEvent.BUS` listeners (it is a subclass), the multi handler is harmless duplication; keep both.

- [ ] **Step 3: Client: the ceiling feels solid**

`src/main/java/com/pocketkingdoms/client/WardClient.java`:

```java
package com.pocketkingdoms.client;

import com.pocketkingdoms.PocketKingdomsMod;
import com.pocketkingdoms.manager.RealmWard;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;

/**
 * The ward's ceiling on the player's own side: before the player moves, upward speed is cut so the move ends at the
 * ceiling; after, anything left over is put back under it. The player (or their mount, which is what moves while
 * riding) meets the ceiling like a surface instead of being pulled back by the server.
 */
public final class WardClient {

    private WardClient() {}

    public static void register() {
        TickEvent.PlayerTickEvent.Pre.BUS.addListener(WardClient::beforeMove);
        TickEvent.PlayerTickEvent.Post.BUS.addListener(WardClient::afterMove);
    }

    private static Entity moverInRealm(Player player, LogicalSide side) {
        if (side != LogicalSide.CLIENT || player != Minecraft.getInstance().player) return null;
        if (!player.level().dimension().equals(PocketKingdomsMod.REALM_DIM)) return null;
        return player.getRootVehicle();
    }

    private static void beforeMove(TickEvent.PlayerTickEvent.Pre event) {
        Player player = event.player();
        Entity mover = moverInRealm(player, event.side());
        if (mover == null) return;
        double room = RealmWard.CEILING_Y - player.getY();      // how far the feet may still rise
        Vec3 v = mover.getDeltaMovement();
        if (v.y > room) mover.setDeltaMovement(v.x, Math.max(0, room), v.z);
    }

    private static void afterMove(TickEvent.PlayerTickEvent.Post event) {
        Player player = event.player();
        Entity mover = moverInRealm(player, event.side());
        if (mover == null || !RealmWard.aboveCeiling(player.getY())) return;
        double over = player.getY() - RealmWard.CEILING_Y;
        mover.setPos(mover.getX(), mover.getY() - over, mover.getZ());
        Vec3 v = mover.getDeltaMovement();
        if (v.y > 0) mover.setDeltaMovement(v.x, 0, v.z);
    }
}
```

In `ClientSetup.java`, beside `ItemTooltips.register();`, add `WardClient.register();`.

If `TickEvent.PlayerTickEvent.Pre` / `.Post` or `event.player()` / `event.side()` don't match these names on the client, check how `RealmEventHandler.onPlayerTick` uses `TickEvent.PlayerTickEvent.Post` (it calls `event.side()` and `event.player()`) and mirror it.

- [ ] **Step 4: Compile and run the whole test suite**

Stop `runServer` and `runClient` if running. Run: `./gradlew.bat test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Check it in game**

In the realm of the Task 3 world:
- Creative flight straight up: you stop at feet y 318 (F3), smoothly, no rubber-banding.
- Survival with an elytra and rockets, climbing steeply: held at the ceiling while gliding on, no stutter.
- A horse jumping on a pillar of blocks at y 316: horse and rider stay under the ceiling.
- Placing a block at y 318 or 319: refused (it pops back). A bed whose head would be at y 318: refused. At y 317: allowed.
- `/setblock ~ 319 ~ stone` above you, then tower up to y 317 and jump: you end up on a free spot below, not inside the stone.
Expected: all as described.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/pocketkingdoms/event/RealmEventHandler.java src/main/java/com/pocketkingdoms/client/WardClient.java src/main/java/com/pocketkingdoms/client/ClientSetup.java
git commit -m "The ward's ceiling: players are held at feet y 318 (the client clamps itself, the server holds anything past a block of slack) and nothing is built at y 318 or 319 in a realm"
```

---

### Task 6: Docs

**Files:**
- Modify: `README.md`, `PRD.md`, `pocket-worlds.md`, `CHANGELOG.md`, `CLAUDE.md` (local, gitignored), `docs/friend-test.md` (local, excluded)

- [ ] **Step 1: Update the player docs**

- `pocket-worlds.md` and `README.md`: a short section "The Veil and the ceiling": every realm is walled by the Veil, the ward made visible, which can't be broken and hides what lies beyond; players are held at feet y 318 and can't build at y 318 or 319. README's config table: `realm_padding_chunks` minimum 1.
- `CHANGELOG.md` under 0.1.0: "Realms are walled by the Veil, an unbreakable wall of mist along every edge from bedrock to the build limit, and players are held under a ceiling at y 318 (no building at y 318 and 319), so nobody can see into another realm, Distant Horizons included."
- `PRD.md`: add the Veil and ceiling as done under realms, and Part B (the sigil) and Part C (touching the ward) as planned.
- `CLAUDE.md`: add `VeilBlock`, `VeilLayout`, `RealmWard`, `WardClient` to the project structure, and `tools/anchor/veil.mjs` to the tools list.
- `docs/friend-test.md`: under realms, walk to the edge (the Veil stops you, it can't be mined or blown up), fly to the ceiling, try to build at y 318.

- [ ] **Step 2: Check for banned dashes and commit**

Run: `grep -n "—" README.md PRD.md pocket-worlds.md CHANGELOG.md`
Expected: no output.

```bash
git add README.md PRD.md pocket-worlds.md CHANGELOG.md
git commit -m "Docs: the Veil and the ward's ceiling"
```
