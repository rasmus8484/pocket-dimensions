# Product Requirements Document — Pocket Dimensions

Reference ID format: `{SYSTEM}-{NNN}`
Status: `DONE` | `PARTIAL` | `TODO`

---

## PR — Pocket Rooms

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| PR-001 | DONE | Room allocation & generation | 20x20x20 boundary shell, 3x3 chunk plots, PocketRoomManager SavedData |
| PR-002 | DONE | Pocket Item binding | pocket_id UUID stored in item NBT; server resolves coords via PocketRoomManager |
| PR-003 | DONE | Anchor placement | Crouch+right-click block face places anchor without entering; Pocket Item consumed |
| PR-004 | DONE | Room entry via anchor | Right-click anchor teleports player in; no ownership restriction |
| PR-005 | DONE | Room exit | Crouch + upward movement detected server-side; teleport to anchor location |
| PR-006 | DONE | Anchor theft | Crouch+right-click anchor converts it to Pocket Item in thief's inventory |
| PR-007 | DONE | Anchor destruction | Mining anchor force-ejects occupants, destroys room permanently, nothing drops |
| PR-008 | DONE | Disconnect handling | Player logs off holding stolen item while others inside: anchor auto-placed at feet |
| PR-009 | DONE | Multi-player occupancy | Multiple players can be in the same room simultaneously |
| PR-010 | TODO | Exit to thief location | Exiting player should appear adjacent to thief's position; search for valid clearance |
| PR-011 | TODO | BoundaryBlock right-click exit | Design doc specifies right-click boundary wall as exit trigger; not implemented |
| PR-012 | TODO | Anchor break warning FX | Particles/sounds warning occupants when anchor is being mined |

---

## RL — Realms

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| RL-001 | DONE | Realm allocation | Chunk-aligned plots; config-driven radius/padding; dry-land search for WorldCore |
| RL-002 | DONE | WorldAnchor + WorldSeed linking | WorldSeed binds anchor to realm; rekey supported (old anchor must be gone) |
| RL-003 | DONE | Realm terrain generation | RealmChunkGenerator: overworld-like noise, no structures, legacy_random_source |
| RL-004 | DONE | WorldCore placement | Searches for dry land near plot center; clears column above; sets owner UUID |
| RL-005 | DONE | Realm entry | Right-click linked anchor; owner always, others only if breached+fueled |
| RL-006 | DONE | Realm exit via WorldCore | Right-click WorldCore; queued teleport to entry location or world spawn |
| RL-007 | DONE | Border enforcement | Chunk-change + 200-tick timer checks; connection.teleport() snap-back |
| RL-008 | DONE | Portal blocking | EntityTravelToDimensionEvent cancelled for all non-queued exits from realm |
| RL-009 | DONE | Hostile mob spawn blocking | MobSpawnEvent.FinalizeSpawn cancelled for natural Monster spawns in realm |
| RL-010 | DONE | Ownership transfer | `/pd owner <player|uuid>` command transfers realm, anchor, and core |
| RL-011 | DONE | Realm relinking | WorldSeed on new anchor rekeys realm; old anchor must be gone first |
| RL-012 | DONE | Login restoration | PlayerLoggedInEvent restores runtime bounds or ejects player if no info |
| RL-013 | DONE | Sleep time advancement | SleepFinishedTimeEvent advances overworld day time from realm |
| RL-014 | TODO | Passive mob spawn control | Config option to suppress passive spawns in realm dimension |

---

## SG — Siege

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| SG-001 | DONE | World Breacher | `world_breacher` block; after breach_duration_ticks, opens realm access to anyone while fueled |
| SG-002 | DONE | Anchor Breaker | `anchor_breaker` block; after breaker_duration_ticks, permanently destroys WorldAnchor |
| SG-003 | DONE | Lapis fuel system | Both siege blocks consume lapis; progress pauses when fuel exhausted |
| SG-004 | DONE | Config-driven durations | breach/breaker duration, core_slow_factor, core_fuel_burn_ticks in config |
| SG-005 | DONE | WorldCore defensive fuel | Owner inserts lapis into WorldCore; slows attacker progress by core_slow_factor |
| SG-006 | DONE | Dynamic beacon colour | WorldCore beam: blue=normal, pink=breacher present, red=breaker active+fueled |
| SG-007 | DONE | WorldAnchor indestructible | Hardness -1; only removable by AnchorBreaker via level.setBlock() |
| SG-008 | TODO | Breacher placement gating | Design: World Breacher placement requires realm owner inside realm; not enforced |
| SG-009 | TODO | One siege block per anchor | Prevent stacking breacher + breaker on same anchor simultaneously |

---

## AE — Anti-Exploit

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| AE-001 | TODO | Piston protection | Prevent piston movement of anchors, siege blocks, boundary blocks, WorldCore |
| AE-002 | TODO | Explosion protection | Prevent explosions from destroying anchors and siege blocks |
| AE-003 | TODO | Ender pearl blocking | Block ender pearl teleportation across realm boundaries |
| AE-004 | TODO | Chorus fruit blocking | Block chorus fruit teleportation in realm dimension |
| AE-005 | TODO | Command teleport restriction | Block /tp and similar for non-admins in realm dimension |
| AE-006 | TODO | Teleport bypass prevention | Catch modded teleports, /back, /home, etc. in realm |
| AE-007 | TODO | Chunk unload duplication | Prevent item/block duplication via chunk boundary exploits |
| AE-008 | TODO | Hopper/dispenser anchor interaction | Prevent automation from extracting/placing anchors |

---

## CP — Content & Polish

| ID | Status | Feature | Notes |
|----|--------|---------|-------|
| CP-001 | TODO | Crafting recipes | All blocks/items currently creative-only; need survival crafting path |
| CP-002 | TODO | Loot tables | Block drop tables for siege blocks, anchors, etc. |
| CP-003 | TODO | Advancements | Progression milestones (first room, first realm, first siege, etc.) |
| CP-004 | TODO | Custom textures | All blocks use vanilla placeholder textures (netherite, iron, redstone, etc.) |
| CP-005 | PARTIAL | Mining/tool tags | pickaxe.json and needs_diamond_tool.json exist but may not cover all blocks |
| CP-006 | TODO | Anchor break warning FX | Particles and sounds when anchor is being mined/destroyed |
| CP-007 | TODO | Custom block models | All blocks use cube_all; no visual distinction beyond texture |
| CP-008 | TODO | Siege progress visual feedback | Particles, sounds, or block state changes during siege progression |
| CP-009 | TODO | In-game documentation | Tooltips, guide book, or advancement hints explaining mechanics |

---

## Phase Map

| Phase | PRD IDs | Status |
|-------|---------|--------|
| 1 — Scaffold | (infrastructure) | DONE |
| 2 — Pocket Rooms | PR-001 through PR-009 | DONE |
| 3 — Realms | RL-001 through RL-013 | DONE |
| 4 — Siege | SG-001 through SG-007 | DONE |
| 5 — Anti-exploit | AE-001 through AE-008 | TODO |
| 6 — Content | CP-001 through CP-009 | TODO |

---

## Design-vs-Code Mismatches

These are features described in design docs that differ from current implementation:

| PRD ID | Mismatch |
|--------|----------|
| PR-005 | Docs say 2-second hold to exit; code uses crouch + upward movement |
| PR-011 | Docs describe BoundaryBlock right-click exit; not implemented |
| RL-002 | Docs allow relinking freely; code requires old anchor gone first |
| RL-006 | Docs say shift+right-click to exit; code uses normal right-click |
| SG-008 | Docs require owner inside realm for breacher placement; not enforced |
