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
| PR-011 | TODO | BoundaryBlock right-click exit | Design doc specifies right-click boundary wall as exit trigger; not implemented |
| PR-012 | PARTIAL | Anchor break warning | Miner gets an action-bar warning when breaking an occupied anchor; no particles/sounds, occupants not warned |
| PR-013 | DONE | Chorus fruit blocking | Chorus fruit teleport cancelled inside pocket rooms |

---

## RL — Realms

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| RL-001 | DONE | Realm allocation | Chunk-aligned plots; config-driven radius/padding; dry-land search for WorldCore |
| RL-002 | DONE | WorldAnchor + WorldSeed linking | WorldSeed binds anchor to realm; rekey supported (old anchor must be gone) |
| RL-003 | DONE | Realm terrain generation | RealmChunkGenerator: overworld-like noise, no structures, legacy_random_source |
| RL-004 | DONE | WorldCore placement | Searches for dry land near plot center; clears column above; sets owner UUID |
| RL-005 | DONE | Realm entry | Right-click linked anchor; owner and access-list players always, others only if breached+fueled |
| RL-006 | DONE | Realm exit via WorldCore | Right-click WorldCore; queued teleport to entry location or world spawn |
| RL-007 | DONE | Border enforcement | Chunk-change + 200-tick timer checks; connection.teleport() snap-back |
| RL-008 | DONE | Portal blocking | EntityTravelToDimensionEvent cancelled for all non-queued exits from realm |
| RL-009 | DONE | Hostile mob spawn blocking | MobSpawnEvent.FinalizeSpawn cancelled for natural Monster spawns in realm |
| RL-010 | DONE | Ownership transfer | `/pd owner <player|uuid>` command transfers realm, anchor, and core |
| RL-011 | DONE | Realm relinking | WorldSeed on new anchor rekeys realm; old anchor must be gone first |
| RL-012 | DONE | Login restoration | PlayerLoggedInEvent restores runtime bounds or ejects player if no info |
| RL-013 | DONE | Sleep time advancement | SleepFinishedTimeEvent advances overworld day time from realm |
| RL-014 | TODO | Passive mob spawn control | Config option to suppress passive spawns in realm dimension |
| RL-015 | DONE | Realm access list | Owner adds/removes players in WorldCore GUI; custom packets (ModNetworking); `max_allowed_players` cap |
| RL-016 | DONE | Two-block World Anchor | Lower/upper halves (DOUBLE_BLOCK_HALF); BE on lower half; custom model + textures |

---

## SG — Siege

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| SG-001 | DONE | World Breacher | `world_breacher` block; after breach_duration_ticks, opens realm access to anyone while fueled |
| SG-002 | DONE | Anchor Breaker | `anchor_breaker` block; after breaker_duration_ticks, permanently destroys WorldAnchor |
| SG-003 | DONE | Lapis fuel system | Both siege blocks consume lapis; progress pauses when fuel exhausted |
| SG-004 | DONE | Config-driven durations | breach/breaker duration, core_slow_factor, core_fuel_burn_ticks in config |
| SG-005 | DONE | WorldCore defensive fuel | Owner inserts lapis into WorldCore; slows attacker progress by core_slow_factor |
| SG-006 | DONE | Dynamic beacon colour | WorldCore beam: blue=normal, pink=breacher present, red=breaker active+fueled or anchor destroyed |
| SG-007 | DONE | WorldAnchor indestructible | Hardness -1; only removable by AnchorBreaker via level.setBlock() (upper half follows) |
| SG-008 | TODO | Breacher placement gating | Design: World Breacher placement requires realm owner inside realm; not enforced |
| SG-009 | DONE | One siege block per anchor | Enforced by geometry: siege blocks must sit directly on the anchor's upper half, which has room for one |
| SG-010 | DONE | Siege boss bars | Progress boss bars within `siege_bossbar_range`; cross-dimension chunk force-loading keeps both sides ticking |
| SG-011 | DONE | Siege visuals on the World Anchor | While a siege block is active, World Breacher done: anchor `INFLUENCE` 0..4 drives EMBER rings, a top-down pink rune gradient with breach progress, pink/gold rune particles and drain particles. Anchor Breaker (Unmaker) done: anchor `DAMAGE` 0..4 drives EMBER rings, cracks, heated runes and the sigil; breaker `CHARGE` 0..4 fills the coils and adds frozen lightning sets at 25/50/75 %, with siphon stream, red motes and the `reality_crack` sound |

---

## AE — Anti-Exploit

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| AE-001 | DONE | Piston protection | Handled by vanilla: pistons never move hardness -1 blocks or blocks with block entities (covers every mod block) |
| AE-002 | PARTIAL | Explosion protection | Blast resistance ≥1200 stops TNT/creepers; the Wither can still break Pocket Anchors and siege blocks (not in `wither_immune`) |
| AE-003 | TODO | Ender pearl blocking | Block ender pearl teleportation across realm boundaries |
| AE-004 | PARTIAL | Chorus fruit blocking | Blocked in pocket rooms (PR-013); not yet in realm dimension |
| AE-005 | TODO | Command teleport restriction | Block /tp and similar for non-admins in realm dimension |
| AE-006 | TODO | Teleport bypass prevention | Catch modded teleports, /back, /home, etc. in realm |
| AE-007 | TODO | Chunk unload duplication | Prevent item/block duplication via chunk boundary exploits |
| AE-008 | TODO | Hopper/dispenser anchor interaction | Prevent automation from extracting/placing anchors |

---

## CP — Content & Polish

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| CP-001 | TODO | Crafting recipes | All blocks/items currently creative-only; need survival crafting path |
| CP-002 | TODO | Loot tables | No loot tables exist: siege blocks drop nothing when mined or when their anchor is removed |
| CP-003 | TODO | Advancements | Progression milestones (first room, first realm, first siege, etc.) |
| CP-004 | PARTIAL | Custom textures | World Anchor uses generated Runebound Monolith textures (`tools/anchor/`); all other blocks use vanilla placeholders |
| CP-005 | PARTIAL | Mining/tool tags | pickaxe.json and needs_diamond_tool.json only list pocket_anchor; siege blocks (hardness 50) mine slowly with any tool |
| CP-006 | TODO | Anchor break warning FX | Particles and sounds when anchor is being mined/destroyed |
| CP-007 | PARTIAL | Custom block models | World Anchor (Runebound), World Breacher (Mandible) and Anchor Breaker (Unmaker): generated models (`tools/anchor/`), renderers and particles; other blocks use cube_all |
| CP-008 | TODO | Siege progress visual feedback | Particles, sounds, or block state changes during siege progression |
| CP-009 | TODO | In-game documentation | Tooltips, guide book, or advancement hints explaining mechanics |

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
| 5 — Anti-exploit | AE-001 through AE-008 | PARTIAL |
| 6 — Content | CP-001 through CP-009 | TODO |

---

## Design-vs-Code Mismatches

These are features described in design docs that differ from current implementation:

| PRD ID | Mismatch |
|--------|----------|
| PR-007 | pocket-rooms.md says netherite-tier tool; code requires diamond-tier pickaxe |
| PR-011 | Docs describe BoundaryBlock right-click exit; not implemented |
| PR-012 | Docs say miner and occupants are warned with particles/sounds; code only sends the miner an action-bar message |
| PR-008 | Docs fall back to nearest valid / last known position; code tries feet + 8 neighbours and places no anchor if all are blocked |
| — | pocket-rooms.md says boundary blocks emit light 15; code sets no light level |
| RL-002 | Docs allow relinking freely; code requires old anchor gone first |
| SG-008 | Docs require owner inside realm for breacher placement; not enforced |
