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

A Pocket Anchor links to a 16x16x16 sealed room inside the pocket dimension, surrounded by unbreakable boundary blocks. It is one object with two forms — an item in your inventory and a block when placed — and every room has exactly one.

**How to use:**
1. Get a **Pocket Anchor** (creative tab: Pocket Dimensions)
2. **Right-click** to enter your room — the mod allocates a room, places the anchor at your feet, and teleports you in
3. **Crouch + right-click** a block face to place the anchor without entering
4. **Crouch + jump** inside the room to exit back to the anchor (or next to whoever is carrying it, if it was stolen)
5. Anyone can **right-click** your placed Pocket Anchor to enter your room
6. Anyone can **crouch + right-click** your anchor to steal it (folds it back into item form)
7. If the anchor is mined and destroyed, the room is permanently deleted and all occupants are ejected

**Look:** the placed anchor is the Tumbling Cube. A head-sized cube hovers in the block, with gold corner knobs, set-back basalt edges, and a window on each side looking into end portal light. A glowing rune is drawn on its top and bottom. It turns slowly on several axes at once inside three bands of runes, whose axes swing round too, so together they sweep out a sphere. While anyone is in the room, the bands brighten and everything turns half again as fast. The anchor gives off light 6, or 9 while the room is occupied. The hitbox is a still, head-sized box around the cube.

**Safe arrival:** you arrive at the room's spawn, or if blocks have been built there, at the nearest spot with room to stand. If the room is packed completely full, the two blocks at the spawn are broken open and drop as items, so nobody can be trapped.

**Disconnect safety:** If a player logs off while holding a stolen Pocket Anchor and others are still inside that room, the mod auto-places an anchor at their feet so occupants aren't trapped.

Chorus fruit teleportation is blocked inside pocket rooms.

### Realms

Each player can own one realm — a region of overworld-like terrain (no structures, no natural hostile spawns) inside the shared realm dimension.

**How to use:**
1. Place a **World Anchor** (two blocks tall — needs headroom). It can't be used from inside a pocket room or the realm itself
2. Use a **World Seed** on the anchor — the seed crumbles into the anchor and condenses a dimensional tunnel to your realm
3. **Right-click** the linked anchor to enter your realm — the owner always has access, players on the realm's access list too, and anyone else only after a successful breach
4. **Right-click** the **World Core** (indestructible block at your realm's center) to exit back to where you entered
5. As the owner, **crouch + right-click** the World Core to open its GUI — shows realm info (owner, age, siege status), a lapis fuel slot for defense, an Exit Realm button, and the **realm access list**

**Safe arrival:** you arrive next to the World Core, or if a slope or something built there is in the way, at the nearest spot around the core with room to stand (never on the core itself, never outside the realm).

**The World Core's screen** (crouch + right-click the core): a slab of the core's own rock, its words carved in and its runes glowing in the siege colour. Three rune seals across the top:
- **Overview** (everyone): the realm's state, owner and age, the **ward** (the lapis slot, with a crystal vein showing how full it is and how long it lasts), and who is in the realm right now. Anyone can add lapis to the ward; only the owner and managers can take it out.
- **Access** (owner and managers): add players by name or pick them from everyone online; each player in the list has a carved **crown** (the owner fills it with gold to make them a **manager**) and a red cross to remove them. Managers can remove ordinary players but not other managers.
- **Manage** (owner and managers): name the realm. The owner can also **relocate** it: after a warning, the realm is grown again in a fresh place and everything in the old one is lost (the access list, managers and name are kept).

**Access list:** In the World Core GUI the owner can type a player name and click **Add** to let them through the anchor, or click **x** next to a name to remove them. The list size is capped by `access.max_allowed_players` (0 = unlimited).

The World Anchor is indestructible by normal mining — it can only be removed through the siege system. Players inside a realm are confined to their region boundaries. Portals are blocked. If the anchor is destroyed, the World Core falls dark and inert and players inside can still exit via the World Core, but nobody can re-enter until the owner links a new anchor.

**Relinking:** Use a World Seed on a new World Anchor to rekey your realm's entry point. The old anchor must be gone first.

**Ownership transfer:** Admins can run `/pd owner <player|uuid>` while looking at a World Anchor to transfer the realm to another player.

### Siege System

Two siege blocks can be placed on top of a World Anchor (on its upper half — **crouch** while placing so you don't enter the realm instead). Only one block fits there, so an anchor can host one siege block at a time. Both are fueled by lapis lazuli:

**World Breacher** (`world_breacher`, the "Mandible": an iron head whose mandibles hook under the anchor's gold band)
- Right-click with lapis to fuel it, or **crouch + right-click** to open its GUI
- GUI shows progress bar, status, ETA, and a lapis fuel slot (insert/remove like a furnace)
- Progresses over 24,000 ticks (1 Minecraft day) while fueled
- When complete, any player can use the anchor to enter the realm (as long as the breacher remains fueled), and a pink beacon beam rises from the breacher's eye while it stays fueled
- While attached, the anchor visibly falls under its influence: its black hole turns ember, its runes turn pink from the top down as the breach progresses (all pink when complete), and pink motes drain from the mandibles into the black hole
- If fuel runs out after breach, access reverts to owner-only
- Destroying the breacher resets all progress

**Anchor Breaker** (`anchor_breaker`, the "Unmaker": clamps with gold turnbuckles grip the anchor's corners, a chamfered iron housing with lapis cells, an inverted funnel over the seed and four red charge coils around a dark spire)
- Right-click with lapis to fuel it, or **crouch + right-click** to open its GUI
- GUI shows progress bar, status, ETA, and a lapis fuel slot (insert/remove like a furnace)
- Progresses over 24,000 ticks while fueled
- When complete, permanently destroys the World Anchor
- While attached, it siphons the anchor's own power against it: a red stream rises from the black hole into the funnel and red motes pour out through the windows to the clamp feet (while fueled); the coils fill, the black hole turns ember, the runes heat to red, stone cracks spread and a sigil of red glyphs burns around the base as progress grows
- At 25 / 50 / 75 % a new set of silent lightning bolts bursts out of the black hole and stays frozen in the air, each with a distant thunder-like "reality crack"; the crack sounds a last time when the anchor is destroyed
- Removing the breaker closes the cracks and cools the anchor
- The Anchor Breaker breaks when the anchor disappears
- Destroying the breaker resets all progress

**Defense:** The realm owner can insert lapis into the World Core to slow siege progress by 3x (`core_slow_factor`). While a siege block is progressing, one attacker lapis and one defender lapis are consumed every `core_fuel_burn_ticks`, creating a resource war. Defender lapis is only used while a siege is actually running.

**The World Core (the Geode Heart):** a two-block boulder of weathered stone floating at the realm's centre, split open on four sides around a crystal-lined hollow that holds the realm's black hole (the same one as the anchor's). A shaft is bored straight down through it; the beacon rises from the black hole up the shaft, runes climb the beam in a slow double helix, crystal shards circle the black hole and drift down the shaft, faceted aurora crystals are driven through its crown and a layer of aurora crystal hangs beneath it, slowly shifting colour. A rune tablet below each opening marks where it answers you.

**Beacon indicator:** the core's colours show the realm's state from anywhere inside it:
- **Blue** — no active siege
- **Pink** — World Breacher is present on the anchor (the crystal, runes and beam turn pink, the black hole burns ember)
- **Red** — Anchor Breaker is active and fueled (everything runs hot red, the black hole burns ember)
- **Dark** — the anchor has been destroyed: the black hole collapses, the beam goes out, nothing glows or moves, and the boulder falls to the ground

Both siege blocks require placement directly on top of a World Anchor and break if the anchor is removed. There are no loot tables yet, so siege blocks drop nothing when broken or mined.

**Siege bars:** Players within `siege_bossbar_range` blocks of an active siege block, and everyone inside the besieged realm, see the siege on a themed bar at the top of the screen: its progress, the time left and the lapis. Attackers and anyone outside the realm see **Rift Eye** (a slice of the anchor's rift in gold, counting the siege block's lapis); the realm's people see **Aurora Stones** (the World Core's rock with aurora crystals and its black hole beneath, counting the core's own lapis). While the core holds lapis, a veined blue ward covers the progress line and the time counts the slowed pace.

**Cross-dimension awareness:** Siege blocks and the World Core force-load each other's chunks across dimensions, so boss bars are visible to players inside the realm during an active siege and the beacon color stays accurate regardless of which dimension players are in.

### Recipes

Everything can be gathered once you reach the Nether; nothing needs the End. The mod's items are powerful, so each costs netherite or a nether star.

| Item | Top | Middle | Bottom |
|------|-----|--------|--------|
| Pocket Anchor | gold ingot, ender pearl, gold ingot | ender pearl, netherite ingot, ender pearl | gold ingot, ender pearl, gold ingot |
| World Seed | emerald, diamond, emerald | ghast tear, nether star, ghast tear | emerald, diamond, emerald |
| World Anchor | netherite scrap, gold block, netherite scrap | obsidian, lodestone, obsidian | netherite scrap, gold block, netherite scrap |
| World Breacher | iron ingot, hopper, iron ingot | netherite scrap, eye of ender, netherite scrap | iron ingot, amethyst shard, iron ingot |
| Anchor Breaker | iron ingot, hopper, iron ingot | netherite scrap, magma block, netherite scrap | eye of ender, blaze rod, eye of ender |

All five appear in the recipe book the first time you enter the Nether. They can be crafted before that if you know them.

The World Core and Boundary Block have no recipe: the realm places its core, and the mod builds the room walls.

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
| `siege.siege_bossbar_range` | 64 | Radius in blocks for seeing siege boss bars |
| `access.max_allowed_players` | 0 | Max players on a realm's access list (0 = unlimited) |

Per-world server settings live in `serverconfig/pocketdimensions-server.toml` inside the world folder. Forge sends them to every player who joins, so mining times and fuel limits match on both sides:

| Setting | Default | Description |
|---------|---------|-------------|
| `mining.pocket_anchor_mine_seconds` | 9.4 | Seconds to mine a Pocket Anchor (still needs a diamond-tier pickaxe) |
| `mining.world_breacher_mine_seconds` | 250 | Seconds to mine a World Breacher (needs a diamond-tier pickaxe) |
| `mining.anchor_breaker_mine_seconds` | 250 | Seconds to mine an Anchor Breaker (needs a diamond-tier pickaxe) |
| `mining.siege_blocks_drop` | false | Whether a mined World Breacher or Anchor Breaker drops itself and its lapis (false: destroyed with its fuel) |
| `fuel.world_breacher_max_lapis` | 5 | Lapis a World Breacher can hold (1-64) |
| `fuel.anchor_breaker_max_lapis` | 5 | Lapis an Anchor Breaker can hold (1-64) |

Mining times are fixed: enchantments, Haste and mining fatigue don't change them. 0 means the block breaks instantly.

---

## Planned Features

These are not yet implemented:

**Anti-Exploit (Phase 5)**
- Wither protection for Pocket Anchors and siege blocks (TNT/creepers already can't break them; pistons can't move any of the mod's blocks)
- Ender pearl and chorus fruit blocking across realm boundaries
- Command teleport restrictions for non-admins in realms
- Hopper/dispenser interaction prevention with anchors

**Content & Polish (Phase 6)**
- Crafting recipes for all items and blocks (currently creative-only)
- Loot tables and block drop tables
- Advancements and progression milestones
- Custom textures and models (only the World Anchor has them so far; everything else uses vanilla placeholder textures)
- Visual/audio feedback during siege progression
- Warning particles and sounds when anchors are being destroyed

**Gameplay Refinements**
- World Breacher placement gating (require realm owner to be inside)
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
