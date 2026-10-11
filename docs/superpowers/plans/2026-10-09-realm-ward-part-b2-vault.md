# Realm Ward Part B2: the Vaulted Ward Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Replace the in-game sigil (flat rings, spiral arms) with the agreed Vaulted Ward (crown, three tiers with worlds and flowing outlines, band, conduit) over the flowing ward field, and fix the Part B review's bugs.

**Architecture:** Pure, tested classes compute everything: `Vault` (the layout and dome), `VaultFrame` (every rune's position for a moment in time), `WardField` (the field's opacity at a point and time, with its own value noise). `WardSigilRenderer` only pulls and draws what they produce. The reference implementation for every number is `design/ward-sigil-studies.html` (Vaulted Ward study, Ward field power), converted from seconds to game ticks / 20.

**Tech Stack:** Minecraft 1.21.11, Forge 61.1.0, Mixin, Java 21, JUnit 5, Node texture tools.

**Spec:** `docs/superpowers/specs/2026-10-09-realm-ward-design.md`, Part B, "Layout: the Vaulted Ward".

## Global Constraints

- No em dashes anywhere. No `Co-Authored-By` lines. Never run gradle while runServer / runClient run.
- No drawn lines or strokes: every shape is runes; the field is soft quads with per-corner opacity.
- Numbers as in the spec and the reference page: dome `382 - 58 * (r / OUTER)^1.5`; OUTER = half - 30; field peak `0.065 * (1 + 10 e^(-r/38))`, field filling the square to half - 15, streaming outward at 9 blocks/s.
- Sizes scale by `min(1, OUTER / 362)`; OUTER < 60 shows only crown and band.

## Review Focus

1. Relog inside a realm: the sigil must come back (WardSync forgets what it sent on logout and on server stop).
2. Blindness, Darkness, lava, powder snow: no sigil (vanilla skips the sky then); the pass sets its own fog.
3. The beam above vanilla's reach is visible from every side and not dashed.
4. Radius 50 realm: rune count and field cells stay within budget (runes < 6000, field vertices < 20000).
5. The built jar names the mixin config in its manifest.

---

### Task 1: WardField (pure)
Tests (`WardFieldTest`): strength 0.449 at r 20, 0.199 at r 60, 0.065 at r 320 (3 decimals), falling all the way; opacity 0 inside r 16 and outside the square; the wave streams outward: `wave(r, t) == wave(r + 9 dt, t + dt)` along any direction; opacity never above 0.5.

### Task 2: Vault + VaultFrame (pure)
Tests (`VaultTest`): radius 25: OUTER 362, three tiers at 0.36 / 0.62 / 0.88, dome heights; crown and band only below OUTER 60; scale 1 at radius 25 and below 1 for radius 10. Tests (`VaultFrameTest`): every rune inside the plot square; counts within budget at radius 25 and 50; no main-track rune within a world's radius of its centre; outline runes stay at least world radius + 5 from world centres; worlds are carried round with their tier; frozen (anchor lost) gives the same runes at any time; hidden ids are left out; an off-centre Core gets a conduit of runes from the beam to the crown, a centred one none.

### Task 3: Renderer, field texture, beam, fog, mixin
`tools/anchor/sigil.mjs` gains `fieldTexture()` (16 x 16 opaque white, test first), built to `textures/misc/ward_field.png`. `WardSigilRenderer` draws the field (grid of quads, per-corner opacity from `WardField`, pulled per quad), then glow and rune sprites per glyph from `VaultFrame`, then the beam to the crown (both windings, the line texture across its width). The mixin passes `addMainPass`'s fog slice; the pass sets it and is not added when vanilla would skip the sky. `WardView` builds a `Vault`; `SigilLayout` and its test go; `WardLook.pulse` goes.

### Task 4: Sync fixes
`WardSync` clears a player's sent state on logout and all maps on server stop.

### Task 5: Docs, jar check
README, pocket-worlds, CHANGELOG, PRD RL-022, CLAUDE.md for the new classes; build the jar and check its manifest for `MixinConfigs`.
