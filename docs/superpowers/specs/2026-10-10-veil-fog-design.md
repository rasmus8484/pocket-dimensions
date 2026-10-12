# The Veil Fog: falling smoke along the realm's edge

Status: design, awaiting review (2026-10-10)

Replaces the look of the Veil from the Realm Ward spec (`2026-10-09-realm-ward-design.md`, Part A, "The Veil block").
The Veil block, its placement, the ceiling and the sigil are unchanged. Part C (ripples and chime) stays on hold.

## Why

The Veil today is a wall of animated mist blocks. Up close it reads as blocks with a texture, never as a ward. The
studies (scratchpad page "Veil Mist Studies", style "Veil Fog, game version", approved 2026-10-10) settled on a
waterfall of glowing smoke anchored to the wall: thickest against the blocks so the wall has no surface, thinning out
over about twenty blocks, pouring down a few blocks a second, with real depth up close. That look needs per-pixel work
with the depth buffer, which blocks and particles cannot give, so it is drawn as one full-screen pass.

## What the player experiences

- Along every edge of the realm, a wall of glowing smoke pours slowly down from high above, curling and changing as it
  falls. Against the blocks it is thick enough to hide them; it thins out toward the player over about twenty blocks.
- Up close the smoke has depth: puffs at different distances slide past each other as the player moves.
- Walking into it, the view goes milky; stepping back, it settles into a falling wall again.
- It is calm blue-white by day and deep indigo-violet at night, and stays whole up to the end of the render distance (covering the Veil blocks' straight edge), fading out over the next 64 blocks; close-range fog (water, lava, rain) thins it; it never takes on the fog's colour (no red smoke at sunset; changed after the second and third in-game looks).
- The pattern does not visibly repeat along the wall.
- The smoke covers the Veil all the way up and only thins out over its last 16 blocks below the top of the world
  (changed after the first in-game look, 2026-10-10).
- With the fog turned off (client config), the Veil is a soft plain wall of mist blocks.

## Decisions taken

| Question | Decision | Why |
|---|---|---|
| The Veil blocks | Keep them as a plain backdrop in one deep violet (#7672b0) all the way up, darker than the smoke so gaps in it read as deeper smoke, lit like ordinary blocks (no glow, so they darken with the smoke at night), with no animation, drifting mist or rune glints (changed after the first and fourth in-game looks) | The fog covers them up close; if the fog is off or fails, the wall is still a soft wall and never a view into the next plot |
| Where the pass runs | Once, at the end of `LevelRenderer.renderLevel` | After any shader mod's final pass (Iris and its Oculus ports overwrite the screen at the end of `renderLevel`), so the fog is never wiped; one path for every setup, nothing to detect |
| Shader mod detection | None | One drawing point works with and without shader packs; less to break on updates |
| Player settings | Client config `veil_fog` (on/off) and `veil_fog_steps` (12, range 4 to 16) | Weak PCs can halve the near-wall cost; off falls back to the backdrop blocks |
| Half resolution | Not now | Needs a depth-aware upscale pass; added only if the friend test asks for it |

### Accepted trade-offs of drawing at the end

- Particles and rain write no depth, so in front of the wall they are fogged as if they stood at the wall. Near the
  wall the difference is small.
- Part C's ripple runes (on hold) would sit under the fog's face. When Part C resumes, its runes can be drawn after
  the fog pass.
- With a shader pack, the fog is drawn over the pack's finished image: it is not lit or shadowed by the pack, but it
  is intact.

## How it works

### The pass

- **Hook**: a mixin injection at `RETURN` of `LevelRenderer.renderLevel` (added to the existing `LevelRendererMixin`).
  Vanilla's frame graph has run, any shader mod's final pass has run (Iris calls it inside `renderLevel`), and the hand
  is drawn later by `GameRenderer`, so the hand is never fogged.
- **When**: only in the realm dimension, only once the client has the plot's bounds (`WardSyncS2C`: minX, minZ, maxX,
  maxZ), only if `veil_fog` is on. Skipped where the ward sigil is skipped: camera in lava or powder snow, blindness or
  darkness.
- **What it draws**: one full-screen triangle into the main render target, no depth attachment. The fragment shader
  samples the main target's depth texture and the noise atlas and writes `vec4(light, transmittance)`. Blending
  `ONE, SRC_ALPHA` gives `scene * T + L` without copying the screen.
- **Pipeline**: our own `RenderPipeline` (`pocketkingdoms:pipeline/veil_fog`), shaders
  `assets/pocketkingdoms/shaders/core/veil_fog.vsh` and `.fsh`, one uniform block `VeilFog` written per frame.
- **Depth**: Iris uses Minecraft's main depth texture as its scene depth (`depthtex0`, verified in Iris's 1.21.11
  source), so reading the main target's depth is valid with and without a shader pack. The hand sits at almost zero
  depth, so even if it were in the depth buffer it would get no visible fog.

### Inputs (uniform block `VeilFog`, written each frame)

| Input | Notes |
|---|---|
| Inverse of projection x view (rotation only) | Rebuilds each pixel's view direction and distance from depth |
| Camera position relative to the plot's min corner | Floats stay under about 1,600: precise even for plots millions of blocks out |
| Plot size (x and z) | From the bounds; the walls are the plot's four edges |
| Time in seconds | Game time plus partial tick, wrapped every hour (one unseen jump per hour, keeps float precision) |
| Night factor | From the level's sky darkening: 0 by day, 1 at night |
| Vanilla fog start and end (the `Fog` block) | So far-off smoke thins out with vanilla's fog; its colour is not used |
| World bottom and height | For the top fade |
| Step count | From `veil_fog_steps` |

### The shader (ported from the approved study)

- Rebuilds the pixel's position from depth. Sky pixels count as 64 blocks past the end of the render distance.
- Per pixel, at most two walls: the x wall and the z wall the view runs toward. Each is treated like the study's wall,
  with x the distance from the wall's face measured into the realm:
  - density `SIGMA * exp(-max(x - face - (b - 0.4) * 5, -3) / H)`, H = 2.4, SIGMA = 0.8, out to XMAX = 24 blocks;
  - the near zone (x < 10): `steps` steps with interleaved-gradient jitter, path capped at 48 blocks, each step two
    atlas reads;
  - the rest of a long stretch along the wall and the thin smoke from x 10 to 24: the closed form (the integral of
    `e^(-x/H)`), with colour and the vanilla fog fade taken at the half-amount point;
  - pieces are combined front to back, and so are the two walls.
- The smoke: two noise fields falling at 3.2 and 2.0 blocks a second in the study, all motion at 0.48 of that in game (after two in-game looks), multiplied; a sideways curl; tile sizes that
  never line up (19.4 and 12 blocks across, 28 and 19.1 tall); slow sine bends that travel with each field; a broad sine
  for thicker and thinner stretches of wall. Contrast as in the study.
- Thin right around the camera: `mix(0.55, 1, smoothstep(0, 3, t))`.
- Fades out only over the last 16 blocks below the top of the world (y 304 to 320 in the realm).
- Colours: the study's deep and lit colours for night and day, darkened slightly with height. The smoke stays whole
  up to the render distance's end and fades over the next 64 blocks (sky pixels count as that far); vanilla's close-range
  (environmental) fog fades its opacity; it is never tinted toward the fog colour.

### The noise atlas

- `client/veil/VeilNoise` (plain Java, tested): 32 x 32 x 32 periodic value noise, 3 octaves, base period 4, two
  fields (seeds 71 and 83), each normalised to 0 to 255. The same noise as the study.
- The game's renderer has no 3D textures, so the atlas is a 2D `RGBA8` texture, 1088 x 32: the 32 slices along the
  depth axis side by side, each 34 pixels wide (one copied edge pixel each side, so filtering never blends into the
  next slice). The vertical (falling) axis wraps with `REPEAT`.
- One pixel of slice k holds `(a[k], a[k+1], b[k], b[k+1])` (k + 1 wrapping to 0): one read gives a field at two
  neighbouring slices, and the shader blends them by the fraction between. Two reads per step, smooth in all three
  directions.
- Built once when the client first needs it (about 50 ms), uploaded with `writeToTexture`, kept for the session.

### Cost

- Pixels whose view never comes within 24 blocks of a wall: a few dozen arithmetic operations.
- Pixels whose view passes within 10 blocks of a wall: `steps` x (2 texture reads + about 30 operations) for each wall
  the view passes near, plus the closed forms. At 12 steps, about 24 reads: in the range of a light shader-pack fog.
- No vertices, no CPU work beyond one uniform upload per frame.

### Measuring it

`/pd test veilTime [seconds]` (alongside `wardTime`): the player's client times the fog pass on the GPU with timer
queries (`CommandEncoder.timerQueryBegin` / `timerQueryEnd`) for the given seconds (default 10) and reports in chat:
average, 95th percentile and worst GPU milliseconds, and the share of a 60 fps frame. The friend test uses it with the
Veil in view.

## Files

| File | Change |
|---|---|
| `client/veil/VeilNoise.java` | New: the noise and the atlas layout (pure, tested) |
| `client/veil/VeilFogMath.java` | New: Java twins of the closed form, the half-amount point and the wall clipping (pure, tested) |
| `client/veil/VeilFogRenderer.java` | New: the pipeline, atlas texture, uniform block, the draw, the timer |
| `client/veil/VeilFogTimer.java` | New: GPU timings for `/pd test veilTime` (pure statistics, tested) |
| `PocketKingdomsClientConfig.java` | New: client config `[veil] veil_fog`, `veil_fog_steps`; registered as `ModConfig.Type.CLIENT` |
| `mixin/LevelRendererMixin.java` | Add the `RETURN` injection on `renderLevel` |
| `command/PocketKingdomsCommand.java` | `/pd test veilTime [seconds]` |
| `assets/pocketkingdoms/shaders/core/veil_fog.{vsh,fsh}` | New shaders |
| `tools/anchor/veil.mjs` | The band textures become plain, still: no animation, no drifting mist, no glints |
| `CLAUDE.md`, `README.md`, `PRD.md` | Document the fog, the config and the command |

## Error handling

- If the pipeline fails to compile or the atlas fails to upload, the fog is turned off for the session, one warning
  goes to the log, and the backdrop blocks remain.
- No bounds yet (just arrived, sync pending): no fog that frame.
- Resizing the window needs nothing: the pass reads whatever main target exists that frame.

## Testing

Unit tests (JUnit, `src/test`):

- `VeilNoise`: values stay in 0 to 255; the noise wraps (slice 0 continues slice 31, column 0 continues column 31);
  each slice's copied edge pixels equal the opposite edge; pixel k's second channel equals slice k + 1's first,
  including k = 31 to 0; the same seed gives the same atlas.
- `VeilFogMath`: the closed-form amount matches summing 10,000 small steps of `e^(-x/H)` (within 0.1 %), for views
  toward, away from and along the wall; the half-amount point really splits the amount in two; wall clipping returns
  an empty range for views that never reach x < 10.
- `VeilFogTimer`: average, 95th percentile and worst from a known list.

By eye, in the dev client (no shader mod, then the Oculus Community Port with one common pack, e.g. Complementary or
BSL):

- At the wall, ten blocks out, along the wall, from the Core, from high above: matches the study.
- Strafing at the wall shows depth.
- Walking a long way along the wall: no visible repeat.
- Day and night; in rain; in water near the wall; held item never fogged.
- With the shader pack: the fog shows and is not wiped.
- `veil_fog = false`: plain backdrop wall. `veil_fog_steps = 6`: still reads right.
- `/pd test veilTime` reports sensible numbers.

## Not in this change

- Half resolution.
- Part C (ripples and chime) and any change to the sigil.
- Tinting the fog with the siege colours.
- Tuning how Distant Horizons draws the far Veil.
