# World Core — Geode Heart (implementation spec)

Date: 2026-10-05 · Status: approved ("i like it, make it real")
Concept: `design/world-anchor-concept.html`, round 18 "Aurora Spears" (build 68), siege states from round 15.

## 1. Goal
Replace the World Core's amethyst cube with the Geode Heart:
- **Static model (two blocks tall):** a weathered boulder (lichen on the crown) floating 4 px above the ground,
  split open on four sides around a hollow lined with calcite and glowing crystal, a crystal-lined shaft down
  its vertical axis, and a rune tablet under each opening with two of the anchor's glyphs and a straight
  vein of light running up into the hollow.
- **Renderer:** the anchor's black hole (sphere, disk, camera-facing ring) at the centre of the hollow
  (8, 17.5, 8 px); the beacon beam rising from it up the shaft; crystal shards (ring around the hole at
  1/3 of the disk speed, shards along the shaft walls at 1/5, five leaked shards above the crown and four
  under the boulder bobbing out of step); five faceted aurora crystals driven down through the crown and a
  hanging aurora crystal layer with inverted peaks under the boulder, translucent, colour drifting
  green → teal → violet → pink, each cluster out of step.
- **Rune helix:** rune particles climbing the beam in a slow double helix (~2.2 px/s, ~18 s each), lying flat
  and facing outward from the beam (not camera-facing).
- **Siege states** (existing `WorldCoreBlockEntity` states) drive everything:

| State | Crystal, runes, shards | Black hole | Beacon | Aurora crystals | Motion |
|---|---|---|---|---|---|
| Normal | blue | cyan (GLOW) | blue | aurora | yes |
| Breaching | pink | ember | pink | aurora swallowed 60 % by pink | yes |
| Breaking | hot red / ember | ember | red | aurora swallowed 60 % by red | yes |
| Anchor lost | none glow; dull crystal, dark carved runes | none | none | crown crystals dull and still, hanging layer gone | none; boulder fallen 4 px to the ground among dead shards; light level 0 |

## 2. Architecture
- `tools/anchor/geode.mjs`: shape/paint per state (port of the concept); `build.mjs` emits
  `world_core_{normal,breaching,breaking,lost}_{lower,upper}` (≤ 200 elements each), the `world_core`
  blockstate (half × siege), the item model and the flow texture whose wave runs toward y 17.5.
- `WorldCoreBlock`: `HALF` (door pattern, block entity on the lower half), `SIEGE` 0..3; shape per half and
  state; light 12 (0 when lost); interactions on either half resolve to the lower block entity; the upper
  half removes itself without a lower half; client ticker spawns the rune helix.
- `WorldCoreBlockEntity`: on siege state change also sets `SIEGE` on both halves; ensures the upper half
  exists (migrates existing one-block cores); infinite render box.
- `RealmManager`: places both halves.
- Renderer: shared `BlackHoleRenderer` extracted from the anchor renderer (anchor unchanged visually);
  new `WorldCoreBlockEntityRenderer` (black hole, beam, shards via `lightning`, aurora crystals via
  `debugQuads`); geometry tables computed once (Java port of the concept with the same hash).
- `HelixRuneParticle` (`rune_helix`, rune sprites, colour passed in the spawn data) with a fixed rotation
  from `getFacingCameraMode()`, drawn from both sides.

## 3. Out of scope
Motes sinking into the black hole (the shards already carry the motion), raising the boulder for a bigger
hanging layer, anti-grief changes.

## 4. Testing
Node tests: element budget and bounds for all 8 models, lost state shifted down by 4 px and never glowing,
tablets and openings present. Build. In game: a new realm shows the Geode Heart; old one-block cores grow
their upper half; each siege state (breacher / breaker / destroyed anchor) switches model, beam, black hole,
crystals and helix; right-click the core leaves the realm, crouch opens the ward from either half.
