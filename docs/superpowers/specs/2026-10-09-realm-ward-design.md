# The Realm Ward: Veil, ceiling and sigil

Status: design, awaiting review (2026-10-09)

## Why

Realms are now 99 x 99 chunks, and players run mods like Distant Horizons that draw terrain far beyond the vanilla view
distance. Nothing currently stops a player at their border from seeing into a neighbour's realm, and Distant Horizons
(on the client or the server) keeps and shows that terrain. Hiding what is sent was considered and set aside: Distant
Horizons' server side sends terrain through its own channel, and a player's own Distant Horizons cache remembers realms
they visited. Instead, the realm gets a visible enclosure that every renderer agrees on, and the limit is explained by
the lore: **the realm's ward**, the same ward the World Core is fed with lapis, made visible.

## What the player experiences

- **The Veil.** A wall of slowly drifting mist stands along every edge of the realm, from bedrock to the top of the
  world. Faint runes are woven into it. Near the ground it is pale mist; low down it darkens; toward the top it takes
  on the realm's sky and fog colours, so from far away (as Distant Horizons shows it) it reads as haze on the horizon.
- **The ceiling.** Players can build and fly as normal, up to a ceiling just below the top of the world. There the
  ward holds them, as the border does at the sides.
- **The sigil.** A great circle of runes hangs high over the realm, centred on the World Core. The Core's beam rises
  into its centre. Rings of runes turn slowly; a square band of runes runs along the top of the Veil; spokes join the
  centre to the band, and pulses travel outward along them. By day it is faint, like the moon in daylight; at night it
  glows.
- **The ward shows the siege.** The sigil (and the Veil's runes, where practical) take the beam's colours: calm blue at
  peace, pink while a World Breacher works, cracks through it once the breach is complete, red while an Anchor Breaker
  works, red and broken when the anchor is lost. It burns bright while the Core has lapis, and dims when it has none.
- **Touching the ward.** Walking into the Veil or flying into the ceiling sends a ripple of runes out from the point
  of contact, with a soft chime.

## Part A: the Veil and the ceiling (server and world generation)

### Heights

The realm dimension keeps its vanilla height (y -64 to 319). The sigil is drawn by our own code, so it needs no room
in the world and can hang above the top of it.

| Height | Meaning |
|---|---|
| y 340 | The sigil's plane (drawn, above the world's top) |
| y -64 to 319 | The Veil (its top surface is at y 320) |
| y 318 | The ceiling: players' feet are held at or below it |
| y 318 and 319 | No blocks may be placed here in a realm |
| y 192 | Clouds (unchanged), below the sigil |

A player's eyes are 1.62 blocks above their feet, so at the ceiling they are at y 319.62, below the Veil's top
surface: nobody can look over it. The two refused rows stop anyone standing on a block above the ceiling (feet at
y 319), which the ceiling would push down into that block. Building reaches y 317, two blocks short of vanilla.

### The Veil block

New block `pocketdimensions:veil` (no item in the creative tab; ops can still `/setblock` it):

- Unbreakable and blast-proof like the Boundary block (`strength(-1, 3600000)`), `PushReaction.BLOCK`, no drops, in the
  `wither_immune` and `dragon_immune` tags, not flammable, mobs never spawn on it, endermen can't take it.
- A full opaque cube, so it blocks the view, and it blocks movement, mobs, water and lava physically.
- Blockstate `band` 0..7: which height band it is in (48 blocks per band, y -64 at band 0). Each band has its own
  colour; the textures are animated (a slowly scrolling mist with faint runes), made with the texture tools in
  `tools/`. Colours run from a dark indigo underground, through pale mist around ground level, to the realm's fog
  colour `#c0d8ff` and sky colour `#78a7ff` in the top bands.
- It should look softly lit even in the dark, without giving off real light (a light source in every Veil block
  would make generation and lighting slow). Use a full-bright model if 1.21.11's block models allow it (to verify:
  the `light_emission` field on model elements); otherwise bake brightness into the texture.

### Where the Veil goes

The plots are a fixed grid (`RealmManager`: plot origin = index position x `cellChunks()`, side = `sideChunks()`).
The Veil is the **ring of block columns just outside each plot**: x = minX - 1 and x = maxX (max is exclusive), for
z from minZ - 1 to maxZ inclusive, and the same with x and z swapped. Every grid cell gets its ring, used or not, so
the generator never needs to know which plots are taken.

- Placed by `RealmChunkGenerator` while a chunk generates (after the surface is built), from a pure layout function:
  `VeilLayout.isVeilColumn(blockX, blockZ, sideBlocks, cellBlocks)` and `VeilLayout.band(y)`.
- Requires a gap between plots, so `realm_padding_chunks` gets a minimum of 1 (it is 0 today). With a gap, two
  neighbours' Veils both sit in the gap and never inside a plot.
- The playable area is unchanged: the Veil stands just outside the existing bounds, so the border check and the Veil
  agree.

### The ceiling

- **Server**: in the existing realm `PlayerTickEvent`, every tick (a single comparison, unlike the lazy border check),
  a player whose feet are above y 318 is put back at y 318 with no upward speed, using `connection.teleport` and the
  same mount handling as the border.
- **Client**: the local player is also clamped at y 318 on the client, so flying into the ceiling feels like hitting
  something rather than being pulled back by the server.
- **Building**: placing a block at y 318 or 319 in the realm dimension is refused (`BlockEvent.EntityPlaceEvent`,
  cancelled), so nobody can stand above the ceiling.
- The heights are constants in code (`RealmWard.CEILING_Y`, `RealmWard.NO_BUILD_Y`); the client needs no sync.

### What changes for existing worlds

New worlds get all of this from the start. Existing realms keep their already-generated edge chunks without a Veil
(only newly generated chunks get it); this mod has not been released, so the test world starts over rather than being
retrofitted. The dimension's height does not change, so nothing else in existing worlds is affected.

## Part B: the sigil (client rendering)

### What the client is told

New packet `WardSyncS2C`, sent to a player when they arrive in a realm, and to everyone in that realm when the ward's
state changes (siege state, breach open, the Core gaining or running out of lapis):

`coreX, coreY, coreZ, minX, minZ, maxX, maxZ, siege (0..3), breachOpen, warded (the Core has lapis)`

Visitors see the ward of the realm they are in. Outside the realm dimension the client draws nothing.

### Layout

Pure and tested (`SigilLayout`): from the Core's position and the plot bounds it produces the list of elements to draw
at y 340:

- an **inner seal** around the beam's top (radius about 24 blocks, large runes);
- **three or four rings** centred on the Core, the outermost inside the nearest Veil (the Core can sit up to 8 chunks
  off the plot's centre, so the rings are sized to the nearest edge);
- a **square band** of runes just inside the top of the Veil;
- **eight spokes** from the seal to the band, made of short line segments.

Runes are about 16 blocks across; the whole sigil is in the low thousands of quads. New, larger rune textures (with
glow versions) are generated in the same style as the anchor's runes. Rings turn slowly, alternating direction; pulses
run outward along the spokes.

### How it is drawn: the pulled dome

A rune hundreds of blocks overhead would be lost in fog (vanilla fogs by the larger of horizontal and vertical
distance) and cut off by the far plane at low render distances. So each element is drawn the way the sun is:

1. Each element (a rune quad, or a short line segment) has its true position.
2. It is scaled about the camera by one factor `k = min(1, R / d)`, where `d` is the camera's distance to the
   element's centre and `R = min(96, 0.9 x fog start)`. Scaling a whole element uniformly about the camera leaves its
   image on screen exactly the same, texture included; the scaling only moves it inside the fog-free zone.
3. Elements are drawn **after the sky and before the terrain**, without depth testing or writing, translucent and
   full-bright. Everything else (terrain, Distant Horizons' terrain, entities, clouds, water) is drawn over it. That is
   also physically right: everything a player can be near is below the ceiling, so it really is in front of the sigil.
4. Anything tied to distance (fading toward the far edges) is computed from the true distance, so the sigil looks the
   same at every render distance and setting.

The **beam** above the vanilla beam's reach (the Core's renderer draws up to 256 blocks above the Core, and it is
fogged high up) is continued by the sigil renderer as pulled segments up to y 340, so the beam visibly meets the seal.

### Colour and state

| Ward state | Sigil |
|---|---|
| Normal | Anchor cyan `#8CEBFF` |
| Breaching | Pink `#FF5ADC` spreading from the centre |
| Breach complete | Cracks: segments of the rings missing or flickering |
| Breaking | Red `#FF5028` |
| Anchor lost | Dim red, rings broken apart, no pulses |

Brightness: full while `warded`, about half without. Day and night: brightness scales with how dark the sky is (the
star brightness), never below about a quarter by day.

### The hook

Forge 1.21.11 has no event for drawing into the world (no `RenderLevelStageEvent`) and none for the sky. The sigil
needs a **mixin** into the world renderer (1.21.11 builds its frame from passes; the inject point is after the sky
pass, before the main terrain pass). This is the mod's first mixin, so Part B starts with a small spike: set up
mixins in the build, inject a single test quad at that point, and check it in the dev client, before the real
renderer is written.

### Shaders

No shader loader exists for Forge 1.21.11 (Iris supports NeoForge and Fabric; Oculus stops at 1.20.1), so shader
packs can't be tested on this build. The sigil is drawn as world geometry with standard render types, the kind shader
packs keep (beacon beams, end portals, the Pocket Anchor's runes), not as part of the sky, which they often replace.
Working with shader packs is a requirement for the NeoForge and Fabric ports, to be tested there; if a shader loader
needs a different inject point, only the hook moves.

## Part C: touching the ward

- **Veil**: the server notices a player pressing into the Veil (their box touching a Veil block, at most once every
  half second per player) and sends a ring of rune particles spreading across the Veil's face from that point, with a
  soft chime. Everyone nearby sees and hears it.
- **Ceiling**: when the ceiling holds a player, the server sends `WardRippleS2C(x, z)` to the realm's players; the
  sigil draws an expanding ring of light from that point, and the chime plays at the player.
- New sound `pocketdimensions:ward_chime` (synthesised like `reality_crack`, in `tools/sound/`).

## Testing

Pure classes with JUnit tests, written first:

- `VeilLayout`: columns just outside a plot are Veil, including the corners; columns inside a plot never are; cells
  with no realm still get their ring; band boundaries for y.
- `RealmWard` rules: feet above y 318 are clamped, at or below are untouched; placing at y 318 or 319 is refused, at y 317 allowed.
- `SigilLayout`: every element lies inside the plot; the rings fit inside the nearest Veil for a Core at the centre
  and at the 8-chunk extreme; element counts stay within budget.
- `CameraPull`: `k` is 1 within `R`, shrinks beyond it; a scaled element projects to the same screen position as the
  unscaled one (the invariance the whole approach rests on).

In the dev client: the Veil at a realm edge (up close, at night, from the Core with a high render distance); flying
into the ceiling; the sigil at 2, 12 and 32 chunks render distance; each siege state; the Core with and without lapis;
both ripples. Added to `docs/friend-test.md` afterwards.

## Out of scope

- Hiding chunks from players (the chunk filter) and the void gap: the Veil makes them unnecessary.
- Tuning how Distant Horizons colours the Veil from far away (`DhApiBlockColorOverrideEvent`): possible later.
- Adding the Veil to realm edges that were generated before this change.

## Order of work

Each part gets its own implementation plan and is playable on its own:

1. **Part A**: taller dimension, Veil block and generation, ceiling.
2. **Part B**: mixin spike, then sync packet, layout, pulled renderer, textures, beam extension.
3. **Part C**: ripples and chime.

Docs to update as the parts land: `README.md`, `PRD.md`, `pocket-worlds.md`, `CHANGELOG.md`, `CLAUDE.md`.
