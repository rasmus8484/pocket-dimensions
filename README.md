# Pocket Dimensions

A Minecraft Java 1.21.11 Forge mod for PvP sandbox servers, adding personal storage rooms and player-owned world regions with a lapis-fueled siege system.

This mod is heavily written by **Claude** (Anthropic's AI coding assistant), with design direction and testing by the project owner.

## What Is This?

Pocket Dimensions adds two spatial systems to Minecraft, both designed around PvP risk:

- **Pocket Rooms** — small sealed rooms inside a shared void dimension. Anyone can enter through your anchor, steal it, or destroy it. Nothing is truly safe.
- **Realms** — persistent player-owned regions inside a shared overworld-like dimension. Protected by an indestructible WorldCore, but vulnerable to siege through lapis-fueled attack blocks.

The mod uses only two shared dimensions (`pocketdimensions:pocket` and `pocketdimensions:realm`) rather than creating dimensions per player, so it scales with active players and loaded chunks.

---

## Current Features

### Pocket Rooms

A Pocket Item links to a 16x16x16 sealed room inside the pocket dimension, surrounded by unbreakable boundary blocks.

**How to use:**
1. Get a **Pocket Item** (creative tab: Pocket Dimensions)
2. **Right-click** to enter your room — the mod allocates a room, places a Pocket Anchor, and teleports you in
3. **Crouch + right-click** a block face to place the anchor without entering
4. **Crouch + move upward** inside the room to exit back to the anchor
5. Anyone can **right-click** your placed Pocket Anchor to enter your room
6. Anyone can **crouch + right-click** your anchor to steal it (converts it back to a Pocket Item)
7. If the anchor is mined and destroyed, the room is permanently deleted and all occupants are ejected

**Disconnect safety:** If a player logs off while holding a stolen Pocket Item and others are still inside that room, the mod auto-places an anchor at their feet so occupants aren't trapped.

### Realms

Each player can own one realm — a region of overworld-like terrain (no structures, no natural hostile spawns) inside the shared realm dimension.

**How to use:**
1. Place a **World Anchor** anywhere in the overworld
2. Use a **World Seed** on the anchor — this allocates your realm and links the anchor as the entry point
3. **Right-click** the linked anchor to enter your realm (owner always has access)
4. **Right-click** the **World Core** (indestructible block at your realm's center) to exit back to where you entered
5. As the owner, **crouch + right-click** the World Core with lapis lazuli to add defensive fuel

The World Anchor is indestructible by normal mining — it can only be removed through the siege system. Players inside a realm are confined to their region boundaries. Portals are blocked. If the anchor is destroyed, players inside can still exit via the World Core but nobody can re-enter until the owner links a new anchor.

**Relinking:** Use a World Seed on a new World Anchor to rekey your realm's entry point. The old anchor must be gone first.

**Ownership transfer:** Admins can run `/pd owner <player|uuid>` while looking at a World Anchor to transfer the realm to another player.

### Siege System

Two siege blocks can be placed on top of a World Anchor, both fueled by lapis lazuli:

**World Breacher** (`world_breacher`, netherite block texture)
- Right-click with lapis to fuel it
- Progresses over 24,000 ticks (1 Minecraft day) while fueled
- When complete, any player can use the anchor to enter the realm (as long as the breacher remains fueled)
- If fuel runs out after breach, access reverts to owner-only
- Destroying the breacher resets all progress

**Anchor Breaker** (`anchor_breaker`, redstone block texture)
- Right-click with lapis to fuel it
- Progresses over 24,000 ticks while fueled
- When complete, permanently destroys the World Anchor
- The Anchor Breaker drops itself when the anchor disappears
- Destroying the breaker resets all progress

**Defense:** The realm owner can insert lapis into the World Core to slow siege progress by 3x. Both attacker and defender lapis are consumed over time, creating a resource war.

**Beacon indicator:** The World Core emits a beacon beam visible from the realm:
- **Blue** — no active siege
- **Pink** — World Breacher is present on the anchor
- **Red** — Anchor Breaker is active and fueled

Both siege blocks require placement directly on top of a World Anchor and will drop if the anchor is removed.

### Configuration

All timing values are configurable in `config/pocketdimensions-common.toml`:

| Setting | Default | Description |
|---------|---------|-------------|
| `realm.realm_radius_chunks` | 2 | Plot size (side = 2r-1 chunks) |
| `realm.realm_padding_chunks` | 1 | Gap between adjacent realms |
| `realm.max_spawn_search_chunks` | 16 | WorldCore dry-land search radius |
| `siege.breach_duration_ticks` | 24000 | World Breacher full-breach time |
| `siege.breaker_duration_ticks` | 24000 | Anchor Breaker anchor-destroy time |
| `siege.core_slow_factor` | 3 | Defense slowdown (progress every N ticks) |
| `siege.core_fuel_burn_ticks` | 200 | Ticks between each lapis consumed |

---

## Planned Features

These are not yet implemented:

**Anti-Exploit (Phase 5)**
- Piston protection for anchors, siege blocks, and boundary blocks
- Explosion protection for anchors and siege blocks
- Ender pearl and chorus fruit blocking across realm boundaries
- Command teleport restrictions for non-admins in realms
- Hopper/dispenser interaction prevention with anchors

**Content & Polish (Phase 6)**
- Crafting recipes for all items and blocks (currently creative-only)
- Loot tables and block drop tables
- Advancements and progression milestones
- Custom textures (all blocks currently use vanilla placeholder textures)
- Custom block models beyond the current cube_all placeholders
- Visual/audio feedback during siege progression
- Warning particles and sounds when anchors are being destroyed

**Gameplay Refinements**
- World Breacher placement gating (require realm owner to be inside)
- Prevention of stacking both siege blocks on the same anchor
- Optional passive mob spawn control in realms
- Anchor break warning effects for room occupants

---

## Build & Run

**Requirements:** Java 21, Forge 1.21.11-61.1.0

```bash
./gradlew build        # Build the mod JAR (output: build/libs/)
./gradlew runClient    # Run the Forge client
./gradlew runServer    # Run the Forge server
```

On Windows use `gradlew.bat` or `./` in Git Bash.

---

## Project Info

- **Mod ID:** `pocketdimensions`
- **Version:** 0.1.0
- **Minecraft:** 1.21.11
- **Forge:** 61.1.0+
- **Java:** 21
- **License:** MIT
