# Siege Bars

The World Breacher and Anchor Breaker replace their vanilla boss bars with two themed bars, one for each side of the siege. Approved in `design/siege-bar-mock.html` (artifact https://claude.ai/artifact/Sg7fpd7ZVMeG112dYmdg91, version 14).

## Who sees which bar

The server's existing range rule decides who sees a siege at all:

- players within `siege_bossbar_range` of the siege block, in the same dimension;
- players inside the linked realm's plot.

The client picks the look from its own dimension:

| Viewer is in | Bar | Fuel line shows |
|---|---|---|
| the realm dimension | **Aurora Stones** (the core's side) | the World Core's lapis, out of 64 |
| anywhere else | **Rift Eye** (the anchor's side) | the siege block's lapis, out of its configured max |

## Layout (256 x 56 GUI px, drawn at GUI scale, centred, stacked below any vanilla boss bars)

From top to bottom:

1. **Crest.**
2. **Banner:** carries the flavour text, "Breaching the Veil" or "Shattering the Anchor". The banner widens for a long title.
3. **The bar:**
   - the progress line, 5 px, left to right;
   - a divider;
   - the fuel line, 2 px.
   - The fuel line is slotted (one slot per lapis) while the max is 16 or less, and continuous above that.
   - Each end cap holds a lapis gem.
4. **Plaque:** shows the stats.
   - Progress percent.
   - Time left, or "Dormant".
   - Lapis count with a gem icon.
5. **Pendant.**

**Rift Eye**
- Banner and plaque are cut from the rift: a starry dark that twinkles slowly.
- Gold borders and gold rails, with pointed gold end caps.
- Faint runes at the banner's ends.
- A small rift in gold claws as the crest, and another as the pendant. Their rings turn from blue to the siege colour as progress grows.
- Gold rays fan out along the banner's crown.

**Aurora Stones**
- The core's weathered rock slab with a broken edge, lichen on the crown, a calcite line, and a dark face behind the text.
- Rock rails and rock knuckle ends.
- Three aurora crystals stand on the crown.
- Small runes beside the title glow in the siege colour.
- The black hole sits behind the plaque with its centre on the plaque's bottom edge, so its top half is hidden:
  - its lensed ring curves around the visible edge;
  - only the near half of a wide, tilted accretion disk shows, as an arc whose ends meet the bar's bottom;
  - the disk's light runs a short way along the edge.

## States

**Colours**
- The Breacher is pink.
- The Breaker is red-orange (`lo 170,28,14`, `mid 250,70,32`, `hi 255,118,62`).
- The progress line flows with a slow wave toward a bright head.

**Warded** (the World Core has lapis while the siege block is fuelled)
- A veined blue force field covers only the progress line.
- The progress keeps its siege colour underneath.
- The time turns ward-blue behind a small shield and counts the slowed time.

**Dormant** (the siege block has no lapis)
- Everything goes grey and still.
- The time reads "Dormant".

## Mechanics

**Server: the siege blocks stop using `ServerBossEvent`**
- Each siege block sends `SiegeBarS2C` packets to the same players the boss bar reached: on first sight, every 20 ticks, and a removal when a player leaves range or the bar ends.
- The packet carries:
  - the bar id;
  - the kind;
  - `progressTicks` and `durationTicks`;
  - the progress rate (0 while dormant, 1 normally, `core_slow_factor` while warded);
  - siege lapis and its max;
  - the core's lapis.
- The bar ends when the breach completes, when there is no progress and no fuel, and when the block is removed.

**Why not vanilla's boss bar?** Forge's `CustomizeGuiOverlayEvent.BossEventProgress` cannot be cancelled in 1.21.11: the hook then returns null and the overlay dereferences it. So vanilla boss events cannot be restyled from the event. Custom packets and a HUD layer avoid the problem entirely.

**Client: a GUI layer above `BOSS_OVERLAY`**
- It draws each known bar. Between packets, progress is extrapolated at the reported rate so the line moves smoothly.
- The layer reads how many vanilla boss bars are showing, and stacks ours below them.
- If a bar gets no update for 5 s it is dropped. All bars are cleared on logout.

**Rendering**
- `SiegeBarArt` is a pure Java port of the mock's pixel renderer. It uses no Minecraft classes, so it can be unit-tested.
- It draws into a 256 x 56 premultiplied RGBA buffer at about 30 fps per bar.
- The result is uploaded to a `DynamicTexture` and blitted with `GuiGraphics`.
- Text is drawn on top with Minecraft's font, at the positions the art leaves for it. The art takes the text widths as input.

## Out of scope

- Restyling other mods' boss bars or vanilla's.
- Sounds.
