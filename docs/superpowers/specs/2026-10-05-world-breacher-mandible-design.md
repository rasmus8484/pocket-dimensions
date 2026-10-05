# World Breacher — Mandible (implementation spec)

Date: 2026-10-05
Status: approved design ("I like it, make it real")
Concept source: `design/world-anchor-concept.html`, round 12 · III "Mandible"

## 1. Goal

Replace the World Breacher's netherite placeholder with the Mandible, and make the World Anchor
show the siege:

- **Breacher model:** chamfered dark-iron head above the anchor's seed (gold brow, lapis cells,
  magenta glyph band), four thick mandibles running down the anchor's corner edges and hooking
  under its gold band, gold-tipped fangs, an eye crystal on top (dim while breaching, bright when
  complete).
- **Beacon beam** rises out of the eye, and only while the breach is complete **and** the breacher
  has fuel (today it ignores fuel).
- **Anchor runes are influenced:** a smooth blue→pink gradient from the top down that advances with
  breach progress; all pink when the breach is complete.
- **Anchor black hole** switches to the EMBER palette while a breacher is attached.
- **Rune particles:** blue + pink while breaching, pink + gold once complete.
- **Drain particles:** pink motes from the mandible hooks, curving over each window into the black hole.
- **Bug fix:** post-breach access checks `breacher.getFuel() > 0` (legacy counter only); use `hasFuel()`.

The anchor itself is never visibly damaged: no anchor element is removed; breacher voxels that
would overlap anchor voxels are dropped by the generator.

## 2. Architecture

### 2.1 Generator (`tools/anchor/`)
- `mandible.mjs` — the breacher's `shape`/`paint` (from the concept), world y 20..47, plus
  `influenceAmount(y, level)`.
- `build.mjs` additions:
  - Linked anchor models per influence level 0..4: `world_anchor_linked_{lower,upper}` (level 0,
    unchanged names) and `world_anchor_linked_i{1..4}_{lower,upper}`. Glow/flow pixels are mixed
    toward magenta by `influenceAmount`.
  - Breacher models `world_breacher_{breaching,complete}` (block-local y = world y − 32; elements
    may reach down to −12). Voxels inside the anchor's solid shape are skipped.
  - Item model `item/world_breacher` (breaching model, scaled).
  - Generated blockstates: `world_anchor.json` (half × linked × influence = 20 variants) and
    `world_breacher.json` (complete false/true).
  - Particle JSONs `rune_pink`, `rune_gold` (rune sprites) and `drain` (vanilla `minecraft:glow`).
- `influenceAmount(y, level)`: 0 → 0; 4 → 1; 1..3 → pink front at `30 − 30·level/4` px, smoothstep
  over 10 px below it.

### 2.2 World Anchor
- New `IntegerProperty INFLUENCE` 0..4 (0 = no breacher). Helper `setInfluence(level, lowerPos, n)`
  updates both halves.
- UPPER half `neighborChanged`: if the block above is not a World Breacher and influence > 0 →
  reset to 0.
- Renderer: `palette = influence > 0 ? EMBER : GLOW`.
- `animateTick`: particle type by influence — 0 cyan only; 1..3 cyan or pink; 4 pink or gold.
- Access fix: `hasFuel()` instead of `getFuel() > 0`.

### 2.3 World Breacher
- New `BooleanProperty COMPLETE`; models switch by it. Shape: own-block head box `(2,0,2,14,12,14)`.
- `serverTick`, every 20 ticks and on completion: compute level = complete ? 4 : 1 + min(2,
  ⌊3·progress/duration⌋); set anchor influence and own `COMPLETE` if changed. Every 40 ticks
  `sendBlockUpdated` so clients see fuel/progress.
- Renderer: beam when `isBreachComplete() && hasFuel()`, starting 12 px up (out of the eye).
- `animateTick` (client): 3 drain particles per call from random hooks when on a linked anchor.

### 2.4 Particles
- Types `rune_pink`, `rune_gold`, `drain` (`SimpleParticleType`).
- `RuneParticle` takes its colour from the provider (cyan / pink `#ff5adc` / gold `#ffcd5f`).
- `DrainParticle`: starts at the hook, target = start + (xd,yd,zd) (the core); random face axis
  picks the control point (start on that axis, core y + 5 px, core on the other axis); quadratic
  path over its 60–80 tick life; full bright, pink, fades in the last 20 %.

## 3. Out of scope
Anchor Breaker visuals (and EMBER for it), siege sounds, inert-anchor + breacher combination
(cannot occur: breacher requires a linked realm only for function, but placement is allowed; it
then shows the influence gradient on the inert model's runes — accepted, no special case).

## 4. Testing
- `node --test tools/anchor/test/*.test.mjs`: budgets, bounds, no breacher/anchor overlap,
  `influenceAmount` endpoints and monotonicity.
- `./gradlew build`; log free of missing model/texture/particle warnings.
- In game: place breacher on a linked anchor → ember rings, top runes pink, drain motes, eye dim;
  `/data merge` or wait (test config: `breach_duration_ticks` low) → progress gradient moves down,
  completion → all pink, eye bright, beam; empty fuel → beam off, access closed; refuel → beam on;
  remove breacher → anchor back to blue and cyan rings.
