# Product Requirements Document — Pocket Dimensions

Reference ID format: `{SYSTEM}-{NNN}`
Status: `DONE` | `PARTIAL` | `TODO`

---

## PR — Pocket Rooms

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| PR-001 | DONE | Room allocation & generation | 20x20x20 boundary shell, 3x3 chunk plots, PocketRoomManager SavedData |
| PR-002 | DONE | Pocket Anchor binding | Single object with item + placed forms; pocket_id UUID stored in item data and block entity; server resolves coords via PocketRoomManager |
| PR-003 | DONE | Anchor placement | Crouch+right-click block face places anchor without entering; anchor leaves inventory |
| PR-004 | DONE | Room entry via anchor | Right-click anchor teleports player in; no ownership restriction |
| PR-005 | DONE | Room exit | Crouch + upward movement detected server-side; teleport to anchor location |
| PR-006 | DONE | Anchor theft | Crouch+right-click anchor folds it back into item form in thief's inventory |
| PR-007 | DONE | Anchor destruction | Diamond-tier pickaxe required; mining anchor force-ejects occupants, destroys room permanently, nothing drops |
| PR-008 | DONE | Disconnect handling | Player logs off holding stolen item while others inside: anchor auto-placed at feet |
| PR-009 | DONE | Multi-player occupancy | Multiple players can be in the same room simultaneously |
| PR-010 | DONE | Exit to thief location | Exiting player appears next to the online player carrying the anchor; falls back to entry location / spawn |
| PR-012 | PARTIAL | Anchor break warning | Miner gets an action-bar warning when breaking an occupied anchor; no particles/sounds, occupants not warned |
| PR-013 | DONE | Chorus fruit blocking | Chorus fruit teleport cancelled inside pocket rooms |
| PR-014 | DONE | Safe arrival | Entering a room searches for the nearest free spot (SpawnSearch/SafeSpot) so blocks built over the spawn never trap you |
| PR-015 | DONE | Occupied anchor | Server sets `OCCUPIED` from online occupants; the Tumbling Cube glows brighter (light 9 vs 6) while someone is inside |
| PR-016 | DONE | Lit rooms | The shell lets sky light through (`getLightBlock` 0, `propagatesSkylightDown`), so every spot in a room has light 15: crops plant and grow anywhere, hostile mobs never spawn naturally. Rooms made before this keep their old darkness until relit |
| PR-017 | DONE | Liquid-proof anchor | The Pocket Anchor is forced solid, so flowing water or lava can't wash it away (its 10 px cube is too small to count as solid on its own, and fluids destroy non-solid blocks). Entering refuses, keeping the item, when there is no spot at your feet or beside you for the anchor |

---

## RL — Realms

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| RL-001 | DONE | Realm allocation | Chunk-aligned plots; config-driven radius/padding; dry-land search for WorldCore |
| RL-002 | DONE | WorldAnchor + WorldSeed linking | WorldSeed binds anchor to realm; rekey only once the linked anchor is destroyed (by design: no dodging a siege) |
| RL-003 | DONE | Realm terrain generation | RealmChunkGenerator: overworld-like noise, no structures, legacy_random_source |
| RL-004 | DONE | WorldCore placement | Searches for dry land near plot center; clears column above; sets owner UUID |
| RL-005 | DONE | Realm entry | Right-click linked anchor; owner and access-list players always, others only if breached+fueled |
| RL-006 | DONE | Realm exit via WorldCore | Right-click WorldCore; queued teleport to entry location or world spawn |
| RL-007 | DONE | Border enforcement | Chunk-change + 200-tick timer checks; connection.teleport() snap-back |
| RL-008 | DONE | Portal blocking | EntityTravelToDimensionEvent cancelled for all non-queued exits from realm |
| RL-009 | DONE | Hostile mob spawn blocking | MobSpawnEvent.FinalizeSpawn cancelled for natural Monster spawns in realm |
| RL-010 | DONE | Ownership transfer | `/pd owner <player|uuid>` command transfers realm, anchor, and core |
| RL-011 | DONE | Realm relinking | WorldSeed on new anchor rekeys realm; refused while the old anchor stands |
| RL-012 | DONE | Login restoration | PlayerLoggedInEvent restores runtime bounds or ejects player if no info |
| RL-013 | DONE | Sleep time advancement | SleepFinishedTimeEvent advances overworld day time from realm |
| RL-014 | TODO | Passive mob spawn control | Config option to suppress passive spawns in realm dimension |
| RL-015 | DONE | Realm access list | Owner adds/removes players in WorldCore GUI; custom packets (ModNetworking); `max_allowed_players` cap |
| RL-016 | DONE | Two-block World Anchor | Lower/upper halves (DOUBLE_BLOCK_HALF); BE on lower half; custom model + textures |
| RL-017 | DONE | Safe arrival in realms | Realm entry searches around the World Core for free space within the plot, so you never land inside blocks |
| RL-018 | DONE | World Core screen | Carved-stone screen with Overview / Access / Manage; roles owner, manager, visitor (RealmRules); managers (crown), realm names, online-player picker; visitors see Overview only and can add but never take lapis |
| RL-019 | DONE | Realm relocation | Owner-only, behind a warning: the realm is regenerated in a new plot; everything in the old one is lost; access list, managers and name are kept |
| RL-020 | DONE | Pocket room smuggling | Leaving a pocket room clears your realm record; stepping out inside a realm makes you its guest whoever you are, so a pocket anchor inside a realm smuggles people in (or lets them sneak in through someone else's room) |

---

## SG — Siege

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| SG-001 | DONE | World Breacher | `world_breacher` block; after breach_duration_ticks, opens realm access to anyone while fueled |
| SG-002 | DONE | Anchor Breaker | `anchor_breaker` block; after breaker_duration_ticks, permanently destroys WorldAnchor |
| SG-003 | DONE | Lapis fuel system | Both siege blocks consume lapis; progress pauses when fuel exhausted |
| SG-004 | DONE | Config-driven durations | breach/breaker duration, core_slow_factor, core_fuel_burn_ticks in config |
| SG-005 | DONE | WorldCore defensive fuel | Owner inserts lapis into WorldCore; slows attacker progress by core_slow_factor |
| SG-006 | DONE | Dynamic beacon colour | WorldCore beam: blue=normal, pink=breacher present, red=breaker active+fueled; anchor destroyed = core inert (no beam) |
| SG-007 | DONE | WorldAnchor indestructible | Hardness -1; only removable by AnchorBreaker via level.setBlock() (upper half follows) |
| SG-008 | DONE | Breacher placement gating | A World Breacher can only be placed while the realm's owner or someone on its access list is inside the realm (breach visitors and smuggled players don't count) |
| SG-009 | DONE | One siege block per anchor | Enforced by geometry: siege blocks must sit directly on the anchor's upper half, which has room for one |
| SG-010 | DONE | Siege bars | Themed bars within `siege_bossbar_range` (Rift Eye outside the realm, Aurora Stones inside), drawn by a HUD layer from `SiegeBarS2C`; cross-dimension chunk force-loading keeps both sides ticking |
| SG-011 | DONE | Siege visuals on the World Anchor | While a siege block is active, World Breacher done: anchor `INFLUENCE` 0..4 drives EMBER rings, a top-down pink rune gradient with breach progress, pink/gold rune particles and drain particles. Anchor Breaker (Unmaker) done: anchor `DAMAGE` 0..4 drives EMBER rings, cracks, heated runes and the sigil; breaker `CHARGE` 0..4 fills the coils and adds frozen lightning sets at 25/50/75 %, with siphon stream, red motes and the `reality_crack` sound |
| SG-012 | DONE | Server config | Per-world `serverconfig/pocketdimensions-server.toml`, synced to clients: mining times for the Pocket Anchor and both siege blocks, siege lapis caps (1-64), `siege_blocks_drop` |
| SG-013 | DONE | Siege block mining | World Breacher and Anchor Breaker need a diamond-tier pickaxe and take a fixed, configured time; they vanish when mined unless `siege_blocks_drop` is on |

---

## AE — Anti-Exploit

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| AE-001 | DONE | Piston protection | Handled by vanilla: pistons never move hardness -1 blocks or blocks with block entities (covers every mod block) |
| AE-002 | DONE | Explosion protection | Blast resistance ≥1200 stops TNT/creepers. The unbreakable blocks (boundary, World Anchor, World Core) are in `wither_immune` and `dragon_immune`. The siege blocks are `wither_immune` too, so a Wither can't clear a siege in seconds. The Pocket Anchor stays breakable by the Wither (it is meant to be destroyable) |
| AE-003 | DONE | Ender pearl blocking | Covered by border enforcement (RL-007): landing outside your realm's bounds pulls you back in |
| AE-004 | DONE | Chorus fruit blocking | Cancelled in pocket rooms (PR-013); in realms, border enforcement pulls you back |
| AE-005 | DONE | Command teleport restriction | Out of the realm: cross-dimension teleports are cancelled (only the World Core and Pocket Anchors let you leave). Into it, by any other route: you need a record of entering through an anchor and must still be allowed in (owner, access list or an open breach), or you are sent back out. Leaving clears the record. Pocket rooms are the deliberate exception (RL-020) |
| AE-006 | DONE | Teleport bypass prevention | Same arrival check as AE-005 for /back, /home and modded teleports; the border's pull-back lands you on a free spot |
| AE-007 | TODO | Chunk unload duplication | Prevent item/block duplication via chunk boundary exploits |
| AE-008 | DONE | Hopper/dispenser anchor interaction | Nothing to block: no mod block entity is a `Container` or exposes an item handler, so hoppers (and hopper minecarts) can't move lapis in or out of the World Core or siege blocks; dispensers only drop the anchor items, never place them; pistons can't push blocks with block entities |
| AE-009 | DONE | Unbesiegeable anchor placement | A World Anchor can't be placed where the siege spot above it (two above its foot) is unbreakable (hardness below zero: the Nether's bedrock ceiling, modded walls) or outside the world |

---

## CP — Content & Polish

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| CP-001 | DONE | Crafting recipes | Pocket Anchor, World Seed, World Anchor, World Breacher, Anchor Breaker; nothing from the End. All five unlock on first entering the Nether |
| CP-002 | DONE | Drops | By design: siege blocks vanish unless `siege_blocks_drop` (then they drop themselves and their lapis, in code, no loot tables); a mined Pocket Anchor destroys its room and drops nothing; World Anchor and World Core are indestructible |
| CP-003 | TODO | Advancements | Progression milestones (first room, first realm, first siege, etc.); only a hidden recipe-unlock advancement exists |
| CP-004 | DONE | Custom textures | Every block and item has its own design (`tools/anchor/`); the pocket room walls borrow vanilla's gold and netherite blocks around the end-portal void |
| CP-005 | DONE | Mining/tool tags | Pocket Anchor, World Breacher and Anchor Breaker in `mineable/pickaxe` and `needs_diamond_tool`; mining times from the server config |
| CP-006 | TODO | Anchor break warning FX | Particles and sounds when anchor is being mined/destroyed |
| CP-007 | PARTIAL | Custom block models | World Anchor (Runebound), World Breacher (Mandible), Anchor Breaker (Unmaker), World Core (Geode Heart, two blocks, model per siege state), Pocket Anchor (Tumbling Cube: renderer-drawn cube, end portal windows, three rune bands, OCCUPIED state) and the World Seed item (Starseed sprite): generated models (`tools/anchor/`), renderers and particles; the BoundaryBlock is the inside of the Tumbling Cube: void faces (end-portal effect from one RoomVoidRenderer per room), netherite edges, gold corners, all full-bright |
| CP-008 | DONE | Siege progress visual feedback | Anchor influence/damage states, particles and sound (SG-011), core beacon colour and siege states (SG-006), themed siege bars (SG-010) |
| CP-009 | PARTIAL | In-game documentation | Item tooltips: a short summary on every mod item, the full rules while Shift is held (lang keys `tooltip.pocketdimensions.<item>.N` / `.more.N`, read by `client/ItemTooltips`). A guide book or advancement hints could follow |

---

## Phase Map

| Phase | PRD IDs | Status |
|-------|---------|--------|
| 1 — Scaffold | (infrastructure) | DONE |
| 2 — Pocket Rooms | PR-001 through PR-009 | DONE |
| 3 — Realms | RL-001 through RL-013 | DONE |
| 4 — Siege | SG-001 through SG-007, SG-009, SG-010 | DONE |
| 4.5 — GUIs | (WorldCore + siege block screens) | DONE |
| 4.6 — Access list & anchor model | RL-015, RL-016 | DONE |
| 4.7 — Visual rework, safe arrival, config | CP-007, SG-010 to SG-013, PR-014, PR-015, RL-017 to RL-019 | DONE |
| 5 — Anti-exploit | AE-001 through AE-008 | PARTIAL |
| 6 — Content | CP-001 through CP-009 | PARTIAL (CP-003, CP-006, CP-009 left) |

---

## Design-vs-Code Mismatches

These are features described in design docs that differ from current implementation:

| PRD ID | Mismatch |
|--------|----------|
| PR-012 | Docs say miner and occupants are warned with particles/sounds; code only sends the miner an action-bar message |
