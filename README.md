# Pocket Dimensions

A Minecraft Java 1.21.11 Forge mod for PvP sandbox servers, adding personal storage rooms and player-owned world regions with a lapis-fueled siege system.

This mod is heavily written by **Claude** (Anthropic's AI coding assistant), with design direction and testing by the project owner.

## What Is This?

Pocket Dimensions adds two spatial systems to Minecraft, both designed around PvP risk:

- **Pocket Rooms**: small sealed rooms inside a shared void dimension. Anyone can enter through your anchor, steal it, or destroy it. Nothing is truly safe.
- **Realms**: persistent player-owned regions inside a shared overworld-like dimension. Protected by an indestructible WorldCore, but vulnerable to siege through lapis-fueled attack blocks.

The mod uses only two shared dimensions (`pocketdimensions:pocket` and `pocketdimensions:realm`) rather than creating dimensions per player, so it scales with active players and loaded chunks.

---

## Current Features

### Pocket Rooms

A Pocket Anchor links to a 16x16x16 sealed room inside the pocket dimension, surrounded by unbreakable boundary blocks. It is one object with two forms (an item in your inventory and a block when placed), and every room has exactly one.

**How to use:**
1. Craft a **Pocket Anchor** (see Recipes), or take one from the Pocket Dimensions creative tab
2. **Right-click** to enter your room: the mod allocates a room, places the anchor at your feet, and teleports you in
3. **Crouch + right-click** a block face to place the anchor without entering
4. **Crouch + jump** inside the room to exit back to the anchor (or next to whoever is carrying it, if it was stolen)
5. Anyone can **right-click** your placed Pocket Anchor to enter your room
6. Anyone can **crouch + right-click** your anchor to steal it (folds it back into item form)
7. If the anchor is mined (a diamond-tier pickaxe, 15 seconds by default), the room is permanently deleted and all occupants are ejected

**Look:** the placed anchor is the Tumbling Cube. A head-sized cube hovers in the block, with gold corner knobs, set-back basalt edges, and a window on each side looking into end portal light. A glowing rune is drawn on its top and bottom. It turns slowly on several axes at once inside three bands of runes, whose axes swing round too, so together they sweep out a sphere. While anyone is in the room, the bands brighten and everything turns half again as fast. The anchor gives off light 6, or 9 while the room is occupied. The hitbox is a still, head-sized box around the cube.

**Inside:** the room is the Tumbling Cube seen from within, only far bigger: every wall, the floor and the ceiling are windows into the end portal's void, ringed by netherite edges with gold corners. Everything is full-bright and nothing casts a shadow on the walls.

**Safe arrival:** you arrive at the room's spawn, or if blocks have been built there, at the nearest spot with room to stand. If the room is packed completely full, the two blocks at the spawn are broken open and drop as items, so nobody can be trapped.

**Whose anchor:** a placed anchor always belongs to its room's owner, whoever puts it down, so a stolen anchor stays yours.

**Being mined:** the people inside feel it. Reality cracks (a sound) at the first hit and at 25 / 50 / 75 %, a warning shows on their screen, red motes shake off the walls, and the Anchor Breaker's lightning tears in from every wall, reaching further each quarter. Outside, the cube bleeds red motes, whether anyone is home or not; the miner is told if someone is inside. Stop mining and the cracks are gone.

**Living there:** the walls let the sky's light through, so crops grow anywhere and no monster spawns inside. Beds work like a bed beside the anchor would: at night in the overworld or a realm (sleepers count towards that world's "players sleeping"), never with the anchor in the Nether or the End. They never set your spawn.

**Liquids:** a placed anchor can't be washed away, and you can enter while standing in water or lava (the anchor goes at your feet).

**Disconnect safety:** If a player logs off while holding a stolen Pocket Anchor and others are still inside that room, the mod auto-places an anchor at their feet so occupants aren't trapped.

Chorus fruit teleportation is blocked inside pocket rooms.

### Realms

Each player can own one realm: a region of overworld-like terrain inside the shared realm dimension. By default it is quiet and untouched (no structures, no dungeons, no monsters spawning); server owners can change that (see Configuration).

**How to use:**
1. Place a **World Anchor** (two blocks tall, so it needs headroom; the block above that must be breakable, where a siege block would go). It can't be used from inside a pocket room or the realm itself
2. Use a **World Seed** on the anchor: the seed crumbles into the anchor and condenses a dimensional tunnel to your realm
3. **Right-click** the linked anchor to enter your realm: the owner always has access, players on the realm's access list too, and anyone else only after a successful breach
4. **Right-click** the **World Core** (indestructible block at your realm's center) to exit back to where you entered
5. **Crouch + right-click** the World Core to open its screen (anyone can; what you may do there depends on who you are, see below)

**Safe arrival:** you arrive next to the World Core, or if a slope or something built there is in the way, at the nearest spot around the core with room to stand (never on the core itself, never outside the realm).

**The World Core's screen** (crouch + right-click the core): a slab of the core's own rock, its words carved in and its runes glowing in the siege colour. Three rune seals across the top:
- **Overview** (everyone): the realm's state, owner and age, the **ward** (the lapis slot, with a crystal vein showing how full it is and how long it lasts), and who is in the realm right now. Anyone can add lapis to the ward; only the owner and managers can take it out.
- **Access** (owner and managers): add players by name or pick them from everyone online; each player in the list has a carved **crown** (the owner fills it with gold to make them a **manager**) and a red cross to remove them. Managers can remove ordinary players but not other managers.
- **Manage** (owner and managers): name the realm. The owner can also **relocate** it: after a warning, the realm is grown again in a fresh place and everything in the old one is lost (the access list, managers and name are kept).

**Access list:** capped by `access.max_allowed_players` (0 = unlimited).

**Day and sleep:** the realm shares the overworld's day, and its sleep count: realm sleepers count with the overworld's players.

A linked World Anchor is indestructible by normal mining: it can only be removed through the siege system. An unlinked one (no World Seed in it) can be mined with a diamond-tier pickaxe in 3 seconds (`world_anchor_mine_seconds`) and drops itself. Players inside a realm are confined to their region boundaries. Portals are blocked. Anyone arriving some other way (another mod's teleport, `/home`) without having come in through an anchor is sent back out. If the anchor is destroyed, the World Core falls dark and inert and players inside can still exit via the World Core, but nobody can re-enter until the owner links a new anchor.

**Relinking:** Use a World Seed on a new World Anchor to rekey your realm's entry point. The old anchor must be gone first.

**Ownership transfer:** Admins can run `/pd owner <player|uuid>` while looking at a World Anchor or World Core (either half) to transfer the realm to another player (the old owner stays on the access list as a manager; the new owner leaves it; someone who already owns a realm can't be given a second), or at a placed Pocket Anchor to record its room as theirs.

**Access by command:** looking at a World Anchor or World Core, admins can run `/pd allow <player>` to put someone on the realm's access list and `/pd deny <player>` to take them off. Add `man` to make them a manager (`/pd allow <player> man`) or take only the manager status away (`/pd deny <player> man`).

**Taking a realm away:** `/pd disown <player>` takes a player's realm from them: everyone inside is sent back out, the World Core is removed, their anchor is unlinked (it stays, and takes a new World Seed) and the old plot is never handed out again. They can grow a new realm with a new seed.

**A broken core:** `/pd regenCore` rebuilds the World Core where it stood, for the realm you're standing in or the World Anchor you're looking at (say, after it was broken by accident in creative).

**A lost anchor:** `/pd regenAnchor <player>` puts their World Anchor back, linked, where it last stood (after an Anchor Breaker took it, or if it was broken in creative). Whatever is in those two blocks now is replaced. The owner can also link any new anchor with a World Seed, as always.

**Testing tools** (ops): `/pd test mineAnchor [seconds]`, run from inside a pocket room, sets an invisible miner on that room's anchor (taking `pocket_anchor_mine_seconds` unless given) so the warnings and cracks can be seen without a second player. It finishes the job: the room is lost.

### Siege System

Two siege blocks can be placed on top of a linked World Anchor (never an empty one; on its upper half; **crouch** while placing so you don't enter the realm instead). Only one block fits there, so an anchor can host one siege block at a time. Both are fueled by lapis lazuli:

**World Breacher** (`world_breacher`, the "Mandible": an iron head whose mandibles hook under the anchor's gold band)
- Can only be placed while someone who belongs to the realm (its owner or anyone on its access list) is inside it; the owner can place one on their own anchor at any time (to open the realm to the public, say)
- Right-click with lapis to fuel it, or **crouch + right-click** to open its GUI
- GUI shows progress bar, status, ETA, and a lapis fuel slot (insert/remove like a furnace)
- Progresses over 24,000 ticks (1 Minecraft day) while fueled
- When complete, any player can use the anchor to enter the realm for as long as the breacher stands (it needs no more lapis), and a pink beacon beam rises from the breacher's eye
- While attached, the anchor visibly falls under its influence: its black hole turns ember, its runes turn pink from the top down as the breach progresses (all pink when complete), and pink motes drain from the mandibles into the black hole
- If fuel runs out after breach, access reverts to owner-only
- Destroying the breacher resets all progress

**Anchor Breaker** (`anchor_breaker`, the "Unmaker": clamps with gold turnbuckles grip the anchor's corners, a chamfered iron housing with lapis cells, an inverted funnel over the seed and four red charge coils around a dark spire)
- Like the breacher, can only be placed while someone who belongs to the realm is inside it, or by the realm's owner
- Right-click with lapis to fuel it, or **crouch + right-click** to open its GUI
- GUI shows progress bar, status, ETA, and a lapis fuel slot (insert/remove like a furnace)
- Progresses over 24,000 ticks while fueled
- When complete, permanently destroys the World Anchor
- While attached, it siphons the anchor's own power against it: a red stream rises from the black hole into the funnel and red motes pour out through the windows to the clamp feet (while fueled); the coils fill, the black hole turns ember, the runes heat to red, stone cracks spread and a sigil of red glyphs burns around the base as progress grows
- At 25 / 50 / 75 % a new set of silent lightning bolts bursts out of the black hole and stays frozen in the air, each with a distant thunder-like "reality crack"; the crack sounds a last time when the anchor is destroyed
- Removing the breaker closes the cracks and cools the anchor
- The Anchor Breaker breaks when the anchor disappears
- Destroying the breaker resets all progress

**Defense:** Anyone can put lapis into the World Core (only the owner and managers can take it out); while it holds lapis, siege progress is slowed by 3x (`core_slow_factor`). While a siege block is progressing, each lapis burns for `core_fuel_burn_ticks` (10 seconds by default) on each side, timed from when it starts burning, creating a resource war. The siege bar drains the burning lapis smoothly, so you can see how much of it is left. Defender lapis is only used while a siege is actually running.

**The World Core (the Geode Heart):** a two-block boulder of weathered stone floating at the realm's centre, split open on four sides around a crystal-lined hollow that holds the realm's black hole (the same one as the anchor's). A shaft is bored straight down through it; the beacon rises from the black hole up the shaft, runes climb the beam in a slow double helix, crystal shards circle the black hole and drift down the shaft, faceted aurora crystals are driven through its crown and a layer of aurora crystal hangs beneath it, slowly shifting colour. A rune tablet below each opening marks where it answers you.

**Beacon indicator:** the core's colours show the realm's state from anywhere inside it:
- **Blue**: no active siege
- **Pink**: World Breacher is present on the anchor (the crystal, runes and beam turn pink, the black hole burns ember)
- **Red**: Anchor Breaker is active and fueled (everything runs hot red, the black hole burns ember)
- **Dark**: the anchor has been destroyed: the black hole collapses, the beam goes out, nothing glows or moves, and the boulder falls to the ground

Both siege blocks require placement directly on top of a World Anchor and break if the anchor is removed. Mining one takes a diamond-tier pickaxe and a fixed time (250 seconds by default); it vanishes with its lapis unless the server turns on `siege_blocks_drop`.

**Siege bars:** Players within `siege_bossbar_range` blocks of an active siege block, and everyone inside the besieged realm, see the siege on a themed bar at the top of the screen: its progress, the time left and the lapis. Attackers and anyone outside the realm see **Rift Eye** (a slice of the anchor's rift in gold, counting the siege block's lapis); the realm's people see **Aurora Stones** (the World Core's rock with aurora crystals and its black hole beneath, counting the core's own lapis). While the core holds lapis, a veined blue ward covers the progress line and the time counts the slowed pace.

**Cross-dimension awareness:** Siege blocks and the World Core force-load each other's chunks across dimensions, so the siege bars reach players inside the realm during an active siege and the beacon color stays accurate regardless of which dimension players are in.

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

### Advancements

A **Folded Space** tab opens the first time you enter the Nether. Its steps double as hints: crafting an anchor, stepping into a room, sleeping there, growing a realm, naming it, letting a friend in, and both sides of a siege, with a few hidden ones for thieves and burglars.

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
| `siege.core_fuel_burn_ticks` | 200 | How long each lapis burns while a siege runs |
| `siege.siege_bossbar_range` | 64 | Radius in blocks for seeing siege boss bars |
| `access.max_allowed_players` | 0 | Max players on a realm's access list (0 = unlimited) |

Per-world server settings live in `serverconfig/pocketdimensions-server.toml` inside the world folder. Forge sends them to every player who joins, so mining times and fuel limits match on both sides:

| Setting | Default | Description |
|---------|---------|-------------|
| `mining.pocket_anchor_mine_seconds` | 15 | Seconds to mine a Pocket Anchor (still needs a diamond-tier pickaxe) |
| `mining.world_breacher_mine_seconds` | 250 | Seconds to mine a World Breacher (needs a diamond-tier pickaxe) |
| `mining.anchor_breaker_mine_seconds` | 250 | Seconds to mine an Anchor Breaker (needs a diamond-tier pickaxe) |
| `mining.siege_blocks_drop` | false | Whether a mined World Breacher or Anchor Breaker drops itself and its lapis (false: destroyed with its fuel) |
| `fuel.world_breacher_max_lapis` | 5 | Lapis a World Breacher can hold (1-64) |
| `fuel.anchor_breaker_max_lapis` | 5 | Lapis an Anchor Breaker can hold (1-64) |
| `realm.structures.generate_structures` | false | Whether structures generate in realms; exceptions in `structure_whitelist` / `structure_blacklist` |
| `realm.features.generate_features` | true | Whether features (ores, trees, lakes, dungeons, ...) generate; exceptions in `feature_whitelist` / `feature_blacklist` (default blacklist: dungeons) |
| `realm.mobs.spawn_monsters` | false | Whether monsters spawn naturally in realms |
| `realm.mobs.spawn_friendly_mobs` | true | Whether every other kind spawns naturally (animals, bats, fish, ...) |
| `realm.mobs.categories.<category>` | "group" | Per mob category: "group" follows the switch above, "true" / "false" overrides it |
| `realm.mobs.exceptions.mob_whitelist` / `mob_blacklist` | [] | Single mobs or tags that always / never spawn naturally |

Mining times are fixed: enchantments, Haste and mining fatigue don't change them. 0 means the block breaks instantly.

In every whitelist / blacklist, the blacklist wins; entries are ids (`minecraft:igloo`, or just `igloo`) or tags (`#minecraft:village`). The config file itself has examples next to each list. Realm generation changes apply to land generated afterwards (structures after a restart).

---

## Planned Features

- A check for item duplication around chunk unloading (a precaution; nothing is known)
- More `/pd test` tools for testing alone

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
