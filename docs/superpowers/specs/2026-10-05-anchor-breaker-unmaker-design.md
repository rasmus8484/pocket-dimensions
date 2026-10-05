# Anchor Breaker — Unmaker (implementation spec)

Date: 2026-10-05 · Status: approved ("hell yeah now we talking"; sound: crack_near)
Concept: `design/world-anchor-concept.html`, round 14 "Unmaker" (build 39)

## 1. Goal
Replace the Anchor Breaker's redstone placeholder with the Unmaker and show the breaking on the anchor:
- **Breaker model:** clamps with gold turnbuckles down the anchor's corners (to world y 19), heavy
  shoulders, chamfered iron housing with lapis cells and red vents, inverted funnel over the seed,
  four red-glass charge coils (fill with progress) around a dark spire.
- **Anchor while being broken** (new `DAMAGE` 1..4 = progress quarters): ember rings, runes heated
  toward red, stone cracks from every direction growing with progress, an unmaking sigil of red
  glyphs around the plinth (world y 4, ring outside the flare).
- **Renderer:** siphon stream (red light) from the black hole up into the funnel; **frozen lightning**:
  a new set of 4 branching bolts at 25 / 50 / 75 %, reaching 33 / 51 / 69 px from the black hole,
  static (seeded by block position), staying while the breaker is attached.
- **Particles:** red motes from the black hole through the windows to the clamp feet; siphon motes
  rising into the funnel; rune particles red/orange while being broken.
- **Sound:** `pocketdimensions:reality_crack` (our synthesized `crack_near`, mono OGG) plays at the
  anchor each time a lightning set appears (25/50/75 %) and when the anchor is destroyed (100 %).

## 2. Architecture
- `tools/anchor/unmaker.mjs`: breaker shape/paint (from concept), `CHARGE_LEVELS`; `damage.mjs` helpers:
  crack birth map (same algorithm as concept) and `damagePaint(level)` for the anchor.
- `build.mjs`: anchor models `world_anchor_linked_d{1..4}_{lower,upper}` (sigil voxels added on
  the lower half), breaker models `anchor_breaker_c{0..4}` (coil fill), item model, generated
  blockstates (`world_anchor`: half × influence × damage × linked; `anchor_breaker`: charge 0..4).
- Anchor: `IntegerProperty DAMAGE` 0..4 + `setDamage`; upper-half neighborChanged resets damage when
  the block above is not an Anchor Breaker; renderer EMBER when influence>0 or damage>0;
  `animateTick` rune colours by damage.
- Breaker: `IntegerProperty CHARGE` 0..4; serverTick sets anchor DAMAGE and own CHARGE every 20 ticks
  and on change; plays the sound when the level rises to 2/3/4 and on destruction; `sendBlockUpdated`
  only when fuel presence changes; head shape; `animateTick` red motes + siphon motes.
- Renderer `AnchorBreakerBlockEntityRenderer`: stream + frozen lightning from `CHARGE`.
- `ModSounds` (sound event) + `sounds.json` + `sounds/reality_crack.ogg`; generator committed at
  `tools/sound/reality_crack.py`.

## 3. Out of scope
Exact sound tuning (user will iterate), anchor cracks on plinth/neck, breaker on inert anchor visuals.

## 4. Testing
Node tests (budgets, bounds, no overlap with anchor, crack births monotonic); build; in game: attach
breaker with low `breaker_duration_ticks`, watch cracks/coils/lightning sets/sound at quarters, anchor
destroyed with final sound, removing the breaker restores the anchor.
