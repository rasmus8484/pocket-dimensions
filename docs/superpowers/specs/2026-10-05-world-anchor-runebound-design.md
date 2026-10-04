# World Anchor — Runebound Monolith (implementation spec)

Date: 2026-10-05
Status: draft, awaiting review
Concept source: `design/world-anchor-concept.html`, round 7 · I "Runebound Monolith"
(published at https://claude.ai/artifact/GFtSdHJZbCeEV6iCkWfgEB)

## 1. Goal

Replace the current World Anchor visuals with the approved Runebound Monolith, matching the
concept as closely as Minecraft 1.21.11 allows:

- **Inert** (no World Seed used): basalt monolith, empty round windows, dark carved runes,
  four stone claws on top around a faintly glowing empty socket.
- **Linked** (World Seed used): runes glow cyan with light flowing toward the core, the black
  hole sits in the window (blocky starry sphere + spinning accretion disk + camera-facing photon
  ring), the seed floats in the claws with a short beam down into the sphere, and glowing rune
  particles drift off the pillar about once per second.

Gameplay behaviour does not change. This is visuals plus one new block-state property.

## 2. Fixed facts from the concept

All sizes in pixels (1 px = 1/16 block). Origin = lower block's corner; y 0–31 spans both halves.

| Part | Geometry |
|---|---|
| Plinth | y 0–1 full 16×16 (corners chamfered), y 2 and flare y 3–4 inset 1 px, gold trim on top of y 4 |
| Body | 12×12 (x/z 2–13), y 5–22 |
| Windows | spherical cavity centre (8, 13.5, 8), radius 7.4; the 2×2 corner posts (|c| ≥ 4.5) are never carved |
| Rune bands | 4×5 glyphs on every face, y 18–22 and y 5–9; dotted strips on posts y 11–19 |
| Crown | gold/rune band y 23 (inset 1), collar y 24 with 4×4 socket in the centre |
| Claws | y 25–26 at inset 2–3, y 27–28 at inset 3–4, y 29–30 at inset 4; gold from y 28 up |
| Black hole | sphere r 3.2 (blocky); photon ring r 2.8–4.2, flattened ×1.12 vertically, horizontal flares to 5.0; disk r 4.2–6.3 |
| Seed | gem shape (~5×8 px), centre (8, 27.5, 8), floating ±0.6 px |
| Beam | 1.2×3 px, centre y 22.2, from the cavity roof to the seed |

Palette (Runebound): basalt `#424658`, plinth `#30323e`, gold `#d6a23c`, rune `#8cebff`,
ring `#96f0ff`, deep disk `#286ee6`, carved rune `#22242e`, waiting socket `#78bee6`.

## 3. Architecture

Four independent pieces, built in this order.

### 3.1 Model generator (`tools/anchor/`, Node, no dependencies)

The concept's `shape()`/`paint()` functions become the single source of truth.

- `tools/anchor/runebound.mjs` — the Runebound shape and paint functions, extracted from the
  concept page into a plain ES module (the concept page will import the same file later, so the
  preview and the game cannot drift apart).
- `tools/anchor/build.mjs` — reads the module and writes the Minecraft assets:
  1. Voxelize each state (inert, linked) over y 0–31.
  2. Split into lower (y 0–15) and upper (y 16–31, shifted down by 16) halves.
  3. Greedy-merge voxels into boxes, **only merging voxels of the same class** (solid, glow,
     flowing-rune), so glow boxes can carry `"light_emission": 15`.
  4. Bake each box face's per-pixel colours into a texture atlas (one PNG per model, up to
     128×128) and write matching UVs.
  5. Flowing runes go into a separate animated texture (`world_anchor_runes_flow.png` +
     `.png.mcmeta`, 8 frames) whose brightness wave moves toward the core.
  6. Write a minimal PNG encoder with Node's built-in `zlib` (no npm packages).
- Output (all under `src/main/resources/assets/pocketdimensions/`):
  - `models/block/world_anchor_{inert,linked}_{lower,upper}.json`
  - `models/item/world_anchor.json` — inert, both halves in one model, scaled to fit the slot
  - `textures/block/world_anchor_{inert,linked}_{lower,upper}.png`, `world_anchor_runes_flow.png(.mcmeta)`
  - `textures/block/world_anchor_bh_ring.png(.mcmeta)` (photon ring shimmer, 1 px per frame step),
    `world_anchor_bh_disk.png` (disk gradient), `world_anchor_seed.png`
  - `textures/particle/rune_0..5.png` and `particles/rune.json`
- Budget: at most ~150 elements per model. The generator prints the count and fails above 200.
- Run with `node tools/anchor/build.mjs`; generated files are committed (the Gradle build does
  not run Node).

### 3.2 Block state (`WorldAnchorBlock`)

- Add `BooleanProperty LINKED` alongside the existing `HALF`. Default `false`.
- Set `LINKED=true` on both halves where a World Seed links the anchor (`WorldSeedItem`), and
  back to `false` wherever the anchor is unlinked (`WorldAnchorBlockEntity.unlink()`).
- `blockstates/world_anchor.json`: four variants (`half` × `linked`) pointing at the generated
  models.
- Light level: 0 inert, 10 linked (via `lightLevel(state -> ...)` in `ModBlocks`).
- Outline/collision shapes: lower = plinth + 12×12 body; upper = body top, crown and claws as
  a simple 12×12 column to y 31 (no per-pixel shapes).
- Existing behaviour unchanged: two-block placement, indestructible, siege blocks still require
  the UPPER half below them (see §6 open item).

### 3.3 Block entity renderer (`client/WorldAnchorBlockEntityRenderer`)

Renders only when the lower half's state has `LINKED=true`. Follows the existing
`WorldCoreBlockEntityRenderer` pattern (`BlockEntityRenderer<T, S>`, `extractRenderState`,
`submit`, `SubmitNodeCollector`).

| Part | How |
|---|---|
| Starry sphere | Blocky sphere faces (same algorithm as the concept's `blockySphereGeo`, r 3.2) submitted with `RenderTypes.endGateway()`; turned each frame to match the photon ring |
| Photon ring | One textured quad, rotated to face the camera using `CameraRenderState.orientation` and offset 0.6 px toward it; animated ring texture; full-bright (`RenderTypes.eyes` or `entityTranslucentEmissive`) |
| Accretion disk | One flat textured quad, rotating about Y at 0.6 rad/s, full-bright, steady (no pulse) |
| Beam | Small full-bright box, opacity pulsing gently |
| Seed | Small baked model (or textured box set) at (8, 27.5, 8), floating ±0.6 px, turning slowly, full-bright |

Render state carries: linked flag, animation time, camera orientation. Render distance and
off-screen behaviour as for the World Core renderer.

### 3.4 Rune particles

- `init/ModParticles.java`: `DeferredRegister<ParticleType<?>>` with `rune` (`SimpleParticleType`).
- `client/particle/RuneParticle.java` extends `SingleQuadParticle`: random sprite from the set,
  colour `#8cebff`, full-bright light, translucent layer, 60–90 tick lifetime, drifts outward
  ~0.03 blocks/s and upward ~0.08 blocks/s, fades in fast and out linearly.
- Provider registered in `ClientSetup` via `RegisterParticleProvidersEvent`.
- Spawned client-side from `WorldAnchorBlock.animateTick` (lower half, linked only): one
  particle on a random face at an upper or lower rune band. Rate tuned to about one per second
  per anchor; respects the player's particle setting automatically.

## 4. Data flow

```
concept shape()/paint()  ──►  tools/anchor/build.mjs  ──►  models + textures (committed)
WorldSeed use ──► WorldAnchorBlockEntity link ──► setBlock(LINKED=true) on both halves
client: blockstate LINKED ──► linked models   ·   BER draws black hole + seed   ·   animateTick spawns runes
```

## 5. Removed

- `models/block/world_anchor_lower.json`, `world_anchor_upper.json`
- `textures/block/world_anchor_{stone,gold,runes,energy}.png`
- The stray `worldanchor_assets/` folder at the repo root (untracked copy of the old assets).

## 6. Out of scope / open items

- **Siege block attachment.** The new top is claws plus a floating seed, so a World Breacher or
  Anchor Breaker placed on top would clash visually. Placement and appearance for breach and
  break states will be designed separately; until then the current rule (siege block on top of
  the UPPER half) stays.
- Obsidian and Sunforged variants (the generator makes adding them a palette swap).
- Animated item model (the item shows the inert monolith).

## 7. Testing

- `node tools/anchor/build.mjs` prints element counts per model; all ≤ 200.
- `./gradlew build` passes.
- `runClient`, creative world:
  1. Place a World Anchor: inert model, empty glowing socket, no particles, light 0.
  2. Use a World Seed: both halves switch to linked, runes glow with flowing light, black hole
     and seed appear, rune particles drift about once per second, light 10.
  3. Walk around: photon ring always faces the camera; disk spins; no gaps between ring and sphere.
  4. Let an Anchor Breaker finish: both halves disappear with no leftover black hole, seed or
     particles. (`unlink()` has no in-game trigger today, since a rekey requires the old anchor
     to be gone, so the `LINKED=false` path is checked by code review only.)
  5. F3+T reload keeps everything working; no missing-texture or model warnings in the log.
  6. Item in inventory shows the inert monolith.
