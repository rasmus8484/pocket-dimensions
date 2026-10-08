# Product Requirements Document: Pocket Dimensions

Reference ID format: `{SYSTEM}-{NNN}`
Status: `DONE` | `PARTIAL` | `TODO`

---

## PR: Pocket Rooms

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| PR-001 | DONE | Room allocation & generation | 20x20x20 boundary shell, 3x3 chunk plots, PocketRoomManager SavedData |
| PR-002 | DONE | Pocket Anchor binding | Single object with item + placed forms; pocket_id UUID stored in item data and block entity; server resolves coords via PocketRoomManager. A placed anchor always belongs to its room's owner, whoever places it |
| PR-003 | DONE | Anchor placement | Crouch+right-click block face places anchor without entering; anchor leaves inventory |
| PR-004 | DONE | Room entry via anchor | Right-click anchor teleports player in; no ownership restriction |
| PR-005 | DONE | Room exit | Crouch + upward movement detected server-side; teleport to anchor location |
| PR-006 | DONE | Anchor theft | Crouch+right-click anchor folds it back into item form in thief's inventory |
| PR-007 | DONE | Anchor destruction | Diamond-tier pickaxe required; mining anchor force-ejects occupants, destroys room permanently, nothing drops |
| PR-008 | DONE | Disconnect handling | Player logs off holding stolen item while others inside: anchor auto-placed at feet |
| PR-009 | DONE | Multi-player occupancy | Multiple players can be in the same room simultaneously |
| PR-010 | DONE | Exit to thief location | Exiting player appears next to the online player carrying the anchor; falls back to entry location / spawn |
| PR-012 | DONE | Anchor break warning | While a Pocket Anchor is mined: the miner is told at the first hit if anyone is inside; occupants get an action-bar warning and the reality-crack sound at the same volume as outside (`event/AnchorMiningHandler`, pure `manager/AnchorMining`) |
| PR-013 | DONE | Chorus fruit blocking | Chorus fruit teleport cancelled inside pocket rooms |
| PR-014 | DONE | Safe arrival | Entering a room searches for the nearest free spot (SpawnSearch/SafeSpot) so blocks built over the spawn never trap you |
| PR-015 | DONE | Occupied anchor | Server sets `OCCUPIED` from online occupants; the Tumbling Cube glows brighter (light 9 vs 6) while someone is inside |
| PR-016 | DONE | Lit rooms | The shell lets sky light through (`getLightBlock` 0, `propagatesSkylightDown`), so every spot in a room has light 15: crops plant and grow anywhere, hostile mobs never spawn naturally. Rooms made before this keep their old darkness until relit |
| PR-017 | DONE | Liquid-proof anchor | The Pocket Anchor is forced solid, so flowing water or lava can't wash it away (its 10 px cube is too small to count as solid on its own, and fluids destroy non-solid blocks). Entering refuses, keeping the item, when there is no spot at your feet or beside you for the anchor |
| PR-018 | DONE | Sleep where the anchor is | A pocket room is folded space inside the world around its anchor (`RoomHost`: the anchor's dimension, else where its occupant came in, else the overworld). Its beds follow that world's bed rule (night in the overworld or a realm; never in the Nether or End) and never set spawn. Its sleepers join that world's sleep pool: one "1/5 players sleeping" count with that world's players (the overworld and the realm share one pool, as they share one clock), and the night passes once enough of them have slept (`playersSleepingPercentage`). `event/PocketSleepHandler` + pure `manager/SleepPool` |

---

## RL: Realms

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| RL-001 | DONE | Realm allocation | Chunk-aligned plots; config-driven radius/padding; dry-land search for WorldCore |
| RL-002 | DONE | WorldAnchor + WorldSeed linking | WorldSeed binds anchor to realm; rekey only once the linked anchor is destroyed (by design: no dodging a siege) |
| RL-003 | DONE | Realm terrain generation | RealmChunkGenerator: overworld-like noise, legacy_random_source; structures and features per the server config (RL-014; none and no dungeons by default) |
| RL-004 | DONE | WorldCore placement | Searches for dry land near plot center; clears column above; sets owner UUID |
| RL-005 | DONE | Realm entry | Right-click linked anchor; owner and access-list players always, others only while a completed World Breacher stands |
| RL-006 | DONE | Realm exit via WorldCore | Right-click WorldCore; queued teleport to entry location or world spawn |
| RL-007 | DONE | Border enforcement | Chunk-change + 200-tick timer checks; connection.teleport() snap-back |
| RL-008 | DONE | Portal blocking | EntityTravelToDimensionEvent cancelled for all non-queued exits from realm |
| RL-009 | DONE | Hostile mob spawn blocking | No natural monster spawns by default; now part of the configurable spawn rules (RL-014, by mob category) |
| RL-010 | DONE | Ownership transfer | `/pd owner <player|uuid>`: looking at a World Anchor or World Core (either half) transfers realm, anchor and core; at a placed Pocket Anchor, the room's recorded owner and the anchor's owner. `/pd allow` / `/pd deny <player> [man]` edit the access list and managers the same way. Refused when the new owner already has a realm; `/pd disown <player>` takes a realm away (core removed, anchor unlinked, plot retired); `/pd regenCore` rebuilds a broken core in place |
| RL-011 | DONE | Realm relinking | WorldSeed on new anchor rekeys realm; refused while the old anchor stands |
| RL-012 | DONE | Login restoration | PlayerLoggedInEvent restores runtime bounds or ejects player if no info |
| RL-013 | DONE | Sleep time advancement | The realm shares the overworld's clock and its sleep pool: realm sleepers count with the overworld's players (`PocketSleepHandler`, `RoomHost.poolHost`), so the realm can't skip the overworld's night alone |
| RL-014 | DONE | Realm spawn and generation rules | Server config `[realm]`: `generate_structures` (false) and `generate_features` (true) with whitelist / blacklist (default feature blacklist: dungeons); `spawn_monsters` (false), `spawn_friendly_mobs` (true), per-category overrides (group / true / false), mob whitelist / blacklist (`[realm.mobs.exceptions]`). Ids or #tags; blacklist wins. Pure `worldgen/RealmGenRules`, `RealmWorldRules`, `RealmChunkGenerator` (filtered structure starts and feature decoration), `RealmEventHandler.onFinalizeSpawn` (natural and chunk-generation spawns, by category) |
| RL-015 | DONE | Realm access list | Owner and managers add/remove players on the World Core screen; custom packets (ModNetworking); `max_allowed_players` cap |
| RL-016 | DONE | Two-block World Anchor | Lower/upper halves (DOUBLE_BLOCK_HALF); BE on lower half; custom model + textures |
| RL-017 | DONE | Safe arrival in realms | Realm entry searches around the World Core for free space within the plot, so you never land inside blocks |
| RL-018 | DONE | World Core screen | Carved-stone screen with Overview / Access / Manage; roles owner, manager, visitor (RealmRules); managers (crown), realm names, online-player picker; visitors see Overview only and can add but never take lapis |
| RL-019 | DONE | Realm relocation | Owner-only, behind a warning: the realm is regenerated in a new plot; everything in the old one is lost; access list, managers and name are kept |
| RL-020 | DONE | Pocket room smuggling | Leaving a pocket room clears your realm record; stepping out inside a realm makes you its guest whoever you are, so a pocket anchor inside a realm smuggles people in (or lets them sneak in through someone else's room) |

---

## SG: Siege

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| SG-001 | DONE | World Breacher | `world_breacher` block; after breach_duration_ticks, opens realm access to anyone for as long as it stands (no fuel needed once complete) |
| SG-002 | DONE | Anchor Breaker | `anchor_breaker` block; after breaker_duration_ticks, permanently destroys WorldAnchor |
| SG-003 | DONE | Lapis fuel system | Both siege blocks consume lapis; progress pauses when fuel exhausted |
| SG-004 | DONE | Config-driven durations | breach/breaker duration, core_slow_factor, core_fuel_burn_ticks in config |
| SG-005 | DONE | WorldCore defensive fuel | Anyone may add lapis to the WorldCore (owner and managers can take it out); slows attacker progress by core_slow_factor |
| SG-006 | DONE | Dynamic beacon colour | WorldCore beam: blue=normal, pink=breacher present, red=breaker active+fueled; anchor destroyed = core inert (no beam) |
| SG-007 | DONE | WorldAnchor indestructible | Linked: hardness -1, only removable by AnchorBreaker via level.setBlock() (upper half follows). Unlinked: diamond-tier pickaxe, `world_anchor_mine_seconds` (3), drops itself |
| SG-008 | DONE | Siege placement gating | A World Breacher or Anchor Breaker can only be placed while the realm's owner or someone on its access list is inside the realm (breach visitors and smuggled players don't count). The realm's owner may place either on their own anchor at any time. `block/SiegePlacement`, pure `RealmRules.maySetSiege` |
| SG-009 | DONE | One siege block per anchor | Enforced by geometry: siege blocks must sit directly on the anchor's upper half, which has room for one |
| SG-010 | DONE | Siege bars | Themed bars within `siege_bossbar_range` (Rift Eye outside the realm, Aurora Stones inside), drawn by a HUD layer from `SiegeBarS2C`; cross-dimension chunk force-loading keeps both sides ticking |
| SG-011 | DONE | Siege visuals on the World Anchor | While a siege block is active, World Breacher done: anchor `INFLUENCE` 0..4 drives EMBER rings, a top-down pink rune gradient with breach progress, pink/gold rune particles and drain particles. Anchor Breaker (Unmaker) done: anchor `DAMAGE` 0..4 drives EMBER rings, cracks, heated runes and the sigil; breaker `CHARGE` 0..4 fills the coils and adds frozen lightning sets at 25/50/75 %, with siphon stream, red motes and the `reality_crack` sound |
| SG-012 | DONE | Server config | Per-world `serverconfig/pocketdimensions-server.toml`, synced to clients: mining times for the Pocket Anchor and both siege blocks, siege lapis caps (1-64), `siege_blocks_drop` |
| SG-013 | DONE | Siege block mining | World Breacher and Anchor Breaker need a diamond-tier pickaxe and take a fixed, configured time; they vanish when mined unless `siege_blocks_drop` is on |

---

## AE: Anti-Exploit

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

## CP: Content & Polish

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| CP-001 | DONE | Crafting recipes | Pocket Anchor, World Seed, World Anchor, World Breacher, Anchor Breaker; nothing from the End. All five unlock on first entering the Nether |
| CP-002 | DONE | Drops | By design: siege blocks vanish unless `siege_blocks_drop` (then they drop themselves and their lapis, in code, no loot tables); a mined Pocket Anchor destroys its room and drops nothing; a linked World Anchor and the World Core are indestructible (an unlinked anchor drops itself) |
| CP-003 | DONE | Advancements | Tab "Folded Space" (unlocks on entering the Nether): pocket rooms (A Room of One's Own, Bigger on the Inside, Dreaming Within, hidden Light Fingers / Echoes in the Walls / Unmade), realms (A Seed of Worlds, Dominion, Heart of the Realm, Kept Company, Hold the Line) and sieges (Gatecrasher, Open Gates, The Unmaker, Severed). Vanilla triggers plus one custom `pocketdimensions:event` trigger (`advancement/PocketEventTrigger`, events in `advancement/Milestones`); files in `data/pocketdimensions/advancement/main`, checked by `AdvancementsTest` |
| CP-004 | DONE | Custom textures | Every block and item has its own design (`tools/anchor/`); the pocket room walls borrow vanilla's gold and netherite blocks around the end-portal void |
| CP-005 | DONE | Mining/tool tags | Pocket Anchor, World Breacher and Anchor Breaker in `mineable/pickaxe` and `needs_diamond_tool`; mining times from the server config |
| CP-006 | DONE | Anchor break warning FX | Red motes and the crack sound at any anchor being mined; inside, the Anchor Breaker's lightning, enlarged, tears in from the walls at 25 / 50 / 75 % and vanishes when the mining stops (pure `manager/RoomCracks`, `client/RoomVoidRenderer`) |
| CP-007 | DONE | Custom block models | World Anchor (Runebound), World Breacher (Mandible), Anchor Breaker (Unmaker), World Core (Geode Heart, two blocks, model per siege state), Pocket Anchor (Tumbling Cube: renderer-drawn cube, end portal windows, three rune bands, OCCUPIED state) and the World Seed item (Starseed sprite): generated models (`tools/anchor/`), renderers and particles; the BoundaryBlock is the inside of the Tumbling Cube: void faces (end-portal effect from one RoomVoidRenderer per room), netherite edges, gold corners, all full-bright |
| CP-008 | DONE | Siege progress visual feedback | Anchor influence/damage states, particles and sound (SG-011), core beacon colour and siege states (SG-006), themed siege bars (SG-010) |
| CP-009 | DONE | In-game documentation | Item tooltips: a short summary on every mod item, the full rules while Shift is held (lang keys `tooltip.pocketdimensions.<item>.N` / `.more.N`, read by `client/ItemTooltips`). No guide book (decided); advancement hints come with CP-003 |
| CP-010 | PARTIAL | Testing commands | `/pd test <name>` (ops) for solo testing. `mineAnchor [seconds]`: an invisible miner breaks the anchor of the room you're in (`AnchorMiningHandler.startTestMiner`). More to come |

---

## Phase Map

| Phase | PRD IDs | Status |
|-------|---------|--------|
| 1: Scaffold | (infrastructure) | DONE |
| 2: Pocket Rooms | PR-001 through PR-009 | DONE |
| 3: Realms | RL-001 through RL-013 | DONE |
| 4: Siege | SG-001 through SG-007, SG-009, SG-010 | DONE |
| 4.5: GUIs | (WorldCore + siege block screens) | DONE |
| 4.6: Access list & anchor model | RL-015, RL-016 | DONE |
| 4.7: Visual rework, safe arrival, config | CP-007, SG-010 to SG-013, PR-014, PR-015, RL-017 to RL-019 | DONE |
| 5: Anti-exploit | AE-001 through AE-009 | PARTIAL (AE-007 left) |
| 6: Content | CP-001 through CP-010 | DONE (CP-010 grows as needed) |

---

## Design-vs-Code Mismatches

These are features described in design docs that differ from current implementation:

| PRD ID | Mismatch |
|--------|----------|
| (none) | Every documented feature matches the code |
