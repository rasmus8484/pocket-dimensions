# World Breacher Mandible Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the Mandible breacher model, siege influence on the World Anchor (rune gradient, EMBER rings, coloured rune particles), drain particles, fuel-gated beam, and the post-breach access fix.

**Architecture:** Extend the Node generator to emit breacher models, influence-level anchor variants and generated blockstates; add `INFLUENCE` (anchor) and `COMPLETE` (breacher) block-state properties driven by the breacher's server tick; colour-parameterised rune particles plus a new path-following drain particle.

**Tech Stack:** Node 24 (`node:test`), Java 21, Forge 1.21.11-61.1.0.

**Spec:** `docs/superpowers/specs/2026-10-05-world-breacher-mandible-design.md`

## Global Constraints
- No new dependencies. Element budget ≤ 200 per model; element coords within −16…32.
- Magenta `[255,90,220]`, gold glow `[255,205,95]`, lapis `[80,130,255]`, cyan rune `[140,235,255]`.
- No `Co-Authored-By` in commits. CLAUDE.md is gitignored: edit, never commit.
- Anchor elements are never removed; breacher voxels overlapping anchor voxels are dropped.

## Review Focus
1. Breacher removed (mined, `/setblock`, anchor destroyed) → anchor returns to influence 0, cyan rings, blue runes.
2. Breach complete but fuel runs out → beam off, eye stays bright, access closed; refuel → beam back within ~2 s.
3. Server restart mid-siege → influence level and COMPLETE are restored from progress on the first ticks.
4. Particle setting Minimal → no drain/rune particles, no errors.
5. Breacher placed on an inert anchor → no crash; influence shows on the inert runes.

---

### Task 1: Generator — mandible module, influence, breacher models, blockstates, particle JSONs
**Files:** Create `tools/anchor/mandible.mjs`, `tools/anchor/test/mandible.test.mjs`; modify `tools/anchor/build.mjs`.
**Produces:** models `block/world_anchor_linked_i{1..4}_{lower,upper}`, `block/world_breacher_{breaching,complete}`, `item/world_breacher`; blockstates `world_anchor.json` (keys `half=…,influence=…,linked=…`), `world_breacher.json` (`complete=false|true`); `particles/{rune_pink,rune_gold,drain}.json`.
- [ ] Write failing tests: `influenceAmount(y,0)=0`, `(y,4)=1`, monotonic in level and in y; breacher models within budget and bounds; no breacher voxel where `anchorShape(x,y,z,true)` is non-null.
- [ ] Run `node --test tools/anchor/test/*.test.mjs` → FAIL (module missing).
- [ ] Implement `mandible.mjs` (shape/paint ported from the concept, `influenceAmount`), extend `build.mjs`.
- [ ] Run tests → PASS; run `node tools/anchor/build.mjs` → counts printed, all ≤ 200.
- [ ] Commit.

### Task 2: World Anchor influence state
**Files:** `block/WorldAnchorBlock.java`, `blockentity/WorldAnchorBlockEntity.java`, `client/WorldAnchorBlockEntityRenderer.java`, `client/WorldAnchorRenderState.java`.
**Produces:** `public static final IntegerProperty INFLUENCE` (0..4); `public static void setInfluence(Level, BlockPos lowerPos, int)`.
- [ ] Add property (default 0) to state definition; upper-half `neighborChanged` resets influence when the block above is not a `WorldBreacherBlock`.
- [ ] Renderer: `s.palette = influence > 0 ? EMBER : GLOW` (influence read from the lower half's state).
- [ ] Access fix: `breacher.hasFuel()`.
- [ ] `./gradlew build` → SUCCESS. Commit.

### Task 3: Particles
**Files:** `init/ModParticles.java`, `client/particle/RuneParticle.java`, create `client/particle/DrainParticle.java`, `client/ClientSetup.java`, `block/WorldAnchorBlock.java` (`animateTick`).
**Produces:** `ModParticles.RUNE_PINK`, `RUNE_GOLD`, `DRAIN`.
- [ ] RuneParticle provider takes an `int rgb`; register three providers.
- [ ] DrainParticle as specified (target via velocity args).
- [ ] Anchor `animateTick` picks particle type by influence.
- [ ] `./gradlew build` → SUCCESS. Commit.

### Task 4: World Breacher block, logic and renderer
**Files:** `block/WorldBreacherBlock.java`, `blockentity/WorldBreacherBlockEntity.java`, `client/WorldBreacherBlockEntityRenderer.java`, item JSON.
- [ ] `COMPLETE` property, head shape, `animateTick` drain particles.
- [ ] serverTick: influence/COMPLETE sync every 20 ticks and on completion; `sendBlockUpdated` every 40 ticks.
- [ ] Renderer: beam only when complete and fueled, translated 12 px up.
- [ ] Remove old `models/block/world_breacher.json`; item JSON → `pocketdimensions:item/world_breacher`.
- [ ] `./gradlew build` → SUCCESS. Commit.

### Task 5: Verify and document
- [ ] Clean regenerate + tests + build; runClient log check; user in-game checklist (spec §4).
- [ ] PRD: CP-004/CP-007 notes, SG-011 partial (breacher siege visuals done). README: breacher beam needs fuel, influence visuals. CLAUDE.md tree (uncommitted).
- [ ] Commit.
