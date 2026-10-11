# The Core's landing: the Stone Dais (RL-024)

Status: design agreed 2026-10-11, from the walkable studies page (scratchpad `core-landing-studies.html`, published as
the "Core landing studies" artifact, version 18, look "Stone Dais", 9 across, 6 vines).

## What the player sees

Every World Core stands at the exact centre of its realm, whatever the land there is: plains, forest, a hillside or open
ocean. Under it lies a small round dais of the Core's own weathered rock, 9 blocks across, its floor level and its edge
cleanly cut, with a shallow rounded underside 3 blocks deep. Out of a small pool under the Core, six vines of
**aurora stone** wind outward through the floor, flush with it, now and then splitting, and where they reach the edge
they trickle down the dais's side. Above the dais the sky is open: a cylinder is cleared straight up, so nothing (trees,
a hill, an overhang) hangs over the Core or its beam.

Aurora stone is to the Core's rock what crying obsidian is to obsidian: the same rock, with thin branching veins of power
running through it. The aurora flows inside the veins (green, cyan, violet, pink, slowly turning), with brighter pulses
travelling along them. The veins glow at night while the rock around them darkens like any stone.

## The two new blocks

| Block | Id | Look | Behaviour |
|---|---|---|---|
| Heartstone | `heartstone` | The Core's weathered rock (the palette and grain of the Geode Heart's boulder, `tools/anchor/geode.mjs`) | Stone-like: strength 1.5 / 6, pickaxe, drops itself |
| Aurora Stone | `aurora_stone` | Heartstone with crying-obsidian veins of flowing aurora | As heartstone, and gives light level 7 (no hostile mobs spawn on the dais) |

- Both are plain blocks (no block entity), in the creative tab, with loot tables that drop themselves and the
  `minecraft:mineable/pickaxe` tag. No recipe yet: a renewable source is for later (the user's idea of a recipe).
- **One pattern on every aurora stone block**, like a vanilla texture: the one the studies page settled on.
- **The rock in aurora stone is the heartstone texture itself**, so the two can never drift apart in shade.

### How aurora stone is drawn

The same proven way as the World Anchor's flowing runes: a block model of stacked elements and animated textures, no
custom renderer.

1. A full cube textured with `heartstone.png`, lit like any block.
2. A second cube in the same place with the **vein** texture: transparent except on the veins, animated, with
   `"light_emission": 15` and `"shade": false`, so the veins stay bright at night.
3. A third with the veins' stained **rim** (the rock just beside a vein, faintly coloured): animated, a lower light
   emission (about 6), so it glows a little but darkens with the night.

Render type cutout. The two overlays never cover the same pixel, so they never flicker against each other.

**The textures** come from a new generator, `tools/anchor/dais.mjs`, run by `node tools/anchor/build.mjs` like the
others:

- `heartstone.png` (16 x 16).
- `aurora_stone_veins.png` and `aurora_stone_rim.png`: animated strips with an `.mcmeta` (`interpolate: true`).
- The vein shape is the studies page's: 1-pixel random walks with a few branches, wrapping round the tile edges so
  neighbouring blocks join seamlessly. Each vein pixel also stores its distance along the vein.
- Each frame paints the aurora colour (a slow turn through the four colours, about 32 s round) with a brighter pulse
  travelling along the veins (about 2 s per pulse). Both repeat exactly, so the strip loops without a jump.
- 4 frames a second, interpolated: 128 frames.

## Where the Core lands

This replaces the dry-land search entirely.

1. **The centre.** The Core's column is the plot's centre block: `(origin + side / 2) * 16 + 8` on each axis.
   This is the column the code already uses as the centre.
2. **The ground under the dais.** For every column of the 9-wide disc, the ground is found by scanning down from the
   top. The scan skips air, leaves, logs and anything that isn't solid (plants, snow layers, flowers). It stops at the
   first solid block or fluid. Water and lava count as ground: on the ocean the dais floats on the water.
3. **The floor height.** The floor's blocks sit at the average height of the first air above that ground, rounded. On
   a slope the dais cuts into the hill on one side and stands out as a ledge on the other.
4. **The dais.** A column at offset (dx, dz) from the centre, at height y, is part of the dais when:
   - y ≤ floor, and
   - `hypot(hypot(dx, dz) / 4.5, (floor - y) / 3) < 1`.

   Every block of the dais becomes heartstone, whatever was there before: ground, water or air.
5. **The vines.** Aurora stone replaces heartstone along six winding paths, with the same rules as the studies page:
   - **The pool.** The floor blocks within 1.6 of the centre are aurora stone.
   - **The paths.** Vine i starts 1.2 blocks out at an angle of i/6 of a turn (plus a little jitter). It steps out
     half a block at a time, wandering slowly.
   - **Width.** Two blocks wide within 2.2 of the centre, one block wide beyond.
   - **Branches.** At most one branch per vine, only beyond 1.8.
   - **The edge.** Vines never leave the floor. At the edge, the vine's outward-facing side blocks below its last floor
     block become aurora stone.

   The noise is seeded by the plot index, so each realm's vines are its own and are the same every time.
6. **The open sky.** Every block in the cylinder `hypot(dx, dz) < 5.5` above the floor is set to air, up to the highest
   block in it (fluids too). That covers the dais and a one-block margin. The cylinder replaces today's single cleared
   column.
7. **The Core** is placed on the pool, at (centre, floor + 1, centre). The spawn spot next to it (one block east) is on
   the pool's aurora stone.

Blocks are set without neighbour updates, so nothing pours or collapses while the dais is built. Leaves cut from a tree
decay as usual later.

### What goes

- `findLandNearCenter` goes, along with:
  - `RingSearch` and its test;
  - the 100-attempt loop that skipped water plots;
  - the extreme fallback;
  - the config key `max_spawn_search_chunks`.
- Every plot is valid now, so `invalidPlots` only holds retired plots (relocated or disowned).

### Existing realms

Realms already generated keep their Core where it stands; nothing is moved or rebuilt. A relocation grows the new realm
with a dais. `/pd regenCore` still puts a broken Core back where it stood, without rebuilding the dais.

## The ward: the conduit goes

The line of runes that flowed from an off-centre Core's beam top in to the crown is removed (`VaultFrame.conduit`, rune
ids 70000+). With every new Core at the centre, the beam rises straight into the crown.

An old off-centre Core's beam still ends on the dome above it (`Vault.Layout.beamTop` is unchanged). The ward spec
(`2026-10-09-realm-ward-design.md`) and its tests drop the conduit.

## Code shape

- `manager/LandingDais.java` (new, pure, unit tested): given the plot seed and a function giving each column's ground
  height, returns:
  - the floor height;
  - every dais block, as an offset and a kind (heartstone or aurora stone);
  - the clearing radius.

  The studies page's hash and value noise move here as plain Java.
- `RealmManager.ensureGenerated`: works out the ground heights from the level, asks `LandingDais` for the plan, clears
  the cylinder, sets the blocks, then places the Core.
- `init/ModBlocks`, `init/ModItems`, `init/ModCreativeTabs`: the two blocks and their items.
- Resources:
  - blockstates and models (block and item);
  - textures;
  - loot tables;
  - the pickaxe tag;
  - `en_us.json` names.
- `tools/anchor/dais.mjs` and its tests (the pattern tiles seamlessly, the strip loops, the vein is 1 pixel wide).
- Docs: PRD RL-024 (done when built), `pocket-worlds.md`, README, CLAUDE.md (block count, the search's removal).

## Testing

- **`LandingDaisTest`:**
  - the floor is flat at the average ground height;
  - the shape is the disc and underside above;
  - the pool sits under the Core;
  - every vine block is on the floor or its edge spill, never outside the dais;
  - six vines reach the rim;
  - the plan is the same for the same seed and differs between seeds;
  - on water the floor sits one above the surface.
- **`VaultFrameTest`:** no rune in the 70000 range for an off-centre Core.
- **Node tests** for the textures.
- **In game:** grow a realm on land, in a forest and on the ocean (check the dais, the open sky and the vines' glow by
  day and night).
