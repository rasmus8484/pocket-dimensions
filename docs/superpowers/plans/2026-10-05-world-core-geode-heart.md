# World Core Geode Heart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Ship the Geode Heart World Core: two-block model per siege state, black hole, beam, shards, aurora crystals, rune helix.
**Architecture:** Same pipeline as the anchor/breakers: generator emits models/blockstates; block-state `SIEGE` drives the static model; a block entity renderer draws the moving/glowing parts; a client particle draws the helix.
**Tech Stack:** Node 24, Java 21, Forge 1.21.11-61.1.0.
**Spec:** `docs/superpowers/specs/2026-10-05-world-core-geode-heart-design.md`

## Global Constraints
- ≤ 200 elements per model, coords −16…32; no new dependencies; no Co-Authored-By; CLAUDE.md not committed.
- Black hole centre (8, 17.5, 8) px; fall 4 px when lost; disk spin 0.6 rad/s; ring shards 1/3, shaft shards 1/5.
- Beam colours: blue 0xFF4488FF, pink 0xFFFF44FF, red 0xFFFF4444; none when lost.

## Review Focus
1. Existing one-block cores in old worlds gain an upper half; nothing breaks if the space above is blocked.
2. Clicking the upper half behaves exactly like the lower (exit, crouch GUI, lapis).
3. Siege state change updates model, light and renderer on clients without relog.
4. Lost state: no beam, no black hole, no glow, no particles; boulder on the ground.
5. Anchor renderer looks unchanged after the black hole extraction.

---
### Task 1: Generator (geode.mjs, build.mjs, tests)
- [ ] Failing tests: 8 models ≤ 200 elements and in bounds; lost has no glow/flow voxels and its lowest voxel is 4 px lower; tablets carry glyph pixels.
- [ ] Implement; tests green; `node tools/anchor/build.mjs`; commit.

### Task 2: Block, block entity, placement
- [ ] `HALF`, `SIEGE`, shapes, light, upper-half lifecycle, interaction resolution, siege → block state, migration, RealmManager both halves; build; commit.

### Task 3: Renderer and particles
- [ ] `BlackHoleRenderer` extraction (anchor uses it); new core renderer (hole, beam, shards, aurora crystals); `HelixRuneParticle` + client ticker; build; commit.

### Task 4: Verify + docs
- [ ] Tests + regenerate + build + runClient log check; README/PRD; review; commit.
