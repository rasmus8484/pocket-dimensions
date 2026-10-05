# Anchor Breaker Unmaker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Ship the Unmaker breaker model, anchor damage visuals (cracks, heated runes, sigil, ember rings), frozen lightning, siphon stream, red particles and the reality-crack sound.
**Architecture:** Same pipeline as the Mandible: generator emits models/blockstates; block-state levels (`DAMAGE` on the anchor, `CHARGE` on the breaker) driven from the breaker tick; a block entity renderer for the stream and lightning; client particles; one sound event.
**Tech Stack:** Node 24, Java 21, Forge 1.21.11-61.1.0.
**Spec:** `docs/superpowers/specs/2026-10-05-anchor-breaker-unmaker-design.md`

## Global Constraints
- ≤ 200 elements per model, coords −16…32; no new dependencies; no Co-Authored-By; CLAUDE.md not committed.
- Hot red `[255,80,40]`, dim `[120,40,30]`, ember `[255,170,60]`, bolt `[215,225,255]`.
- Bolt sets at 25/50/75 %, reach 33/51/69 px from the black hole centre (8, 13.5, 8).
- Sound plays on level increase to 2, 3, 4 and on anchor destruction; never on load.

## Review Focus
1. Breaker removed mid-break → anchor DAMAGE 0, ember off (unless a breacher… impossible: one block on top).
2. Server restart → DAMAGE/CHARGE recomputed, no sound replay on load.
3. Lightning stable (same jag every frame, seeded per block position).
4. Breaker with no fuel → progress paused, visuals hold, no sound spam.
5. Anchor destroyed → final sound once, no orphan renderer.

---
### Task 1: Generator (unmaker.mjs, damage.mjs, build.mjs, tests)
- [ ] Failing tests: crack births in (0,1] and each crack's births increase along it; breaker models ≤200, bounds; no breaker voxel overlaps the linked anchor; damage paint level 0 equals plain paint.
- [ ] Implement; run tests green; `node tools/anchor/build.mjs`; commit.

### Task 2: Sound + anchor DAMAGE state + renderer palette
- [ ] `ModSounds.REALITY_CRACK`, `sounds.json`, register in mod; anchor `DAMAGE` property, `setDamage`, neighbor reset, EMBER on damage, rune particle colours; build; commit.

### Task 3: Breaker block, logic, renderer, particles
- [ ] `CHARGE` property, shape, serverTick level sync + sound, fuel-change sync; renderer (stream + frozen bolts); red/siphon particle types; build; commit.

### Task 4: Verify + docs
- [ ] Tests + regenerate + build + runClient log check; README/PRD; review; commit.
