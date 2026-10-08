# Pocket Worlds (Realms)

Related:
- Project overview and global rules: `README.md`
- Pocket Rooms system: `pocket-rooms.md`

---

## 1. Realm dimension

All player realms exist as regions inside a single shared dimension:

- `pocketdimensions:realm`

No realm is its own dimension.

---

## 2. Realm region ownership

Realm areas are bound to **Player UUID**, not to the key item.

Server stores authoritative mapping:

- `player_uuid -> RealmData (region coords, size tier, timestamps, etc.)`

Players must not be able to cross into other players' regions.

---

## 3. Realm dimension rules

A realm is meant to be a safe, untouched place for a base: never found by a village, raided from an outpost or dug into by a dungeon's spawner. Server owners decide what else they want in the `[realm]` section of the per-world server config (`serverconfig/pocketdimensions-server.toml`). Every part has a switch, then exceptions: the whitelist lets things through whatever the switch says, the blacklist keeps things out whatever else says (it beats the whitelist). Entries are ids (`minecraft:igloo`) or tags (`#minecraft:village`).

### World generation

- Overworld-style terrain from the realm's own noise settings
- **Structures**: `generate_structures` (default **false**) plus `structure_whitelist` / `structure_blacklist`. Structures that are off don't show up in `/locate` or on explorer maps
- **Features** (ores, trees, lakes, dungeons, geodes, fossils, ...): `generate_features` (default **true**) plus `feature_whitelist` / `feature_blacklist`; the blacklist starts with dungeons (`minecraft:monster_room`, `minecraft:monster_room_deep`)
- Changes apply to land generated afterwards; structure changes need a restart

### Mob spawning

- Natural spawning only (in the dark, or when new land generates): **spawner blocks always work**, and so do eggs and commands
- `spawn_monsters` (default **false**) and `spawn_friendly_mobs` (default **true**, everything that isn't a monster: animals, bats, fish, squid, axolotls)
- Per category (`monster`, `creature`, `ambient`, `axolotls`, `underground_water_creature`, `water_creature`, `water_ambient`): `"group"` follows the switch above, `"true"` / `"false"` decides for that category
- Then `mob_whitelist` / `mob_blacklist` for single mobs or tags
- A jockey's mount isn't judged on its own: if the rider may not spawn, neither does the ride

### Portals

All portals are disallowed in the realm dimension:

- Nether portals (creation + activation blocked)
- End portals blocked
- End gateways blocked
- Any portal-based dimension change blocked

Entry and exit must be via anchor/core teleport mechanics only.

### Day and sleep

- The realm shares the overworld's day: same time, same nights.
- It shares the overworld's sleep count too. Realm sleepers count with the overworld's players (and with everyone in pocket rooms anchored in either), so "1/5 players sleeping" is one count, and the realm can't skip the overworld's night on its own.

### Border enforcement

Players in the realm must not cross into other players' regions via:

- Walking / flying / elytra
- Ender pearls
- Chorus fruit
- Teleport commands (unless admin)
- Modded teleport items or mechanics

Server enforces hard region boundaries (implementation choice), but the rule is absolute.

---

# WorldAnchor + WorldSeed (creation and rekey)

## 4. WorldAnchor placement

- Placeable in **any dimension**
- No placement requirements (no structures or biomes)
- No linking structures
- The anchor itself is the realm entry point
- Two blocks tall (lower + upper half); needs one free block above to place
- The block above that (where a siege block would go) must be breakable and inside the world, so no anchor can hide under the Nether's bedrock ceiling or an unbreakable modded block
- It refuses to open from inside a pocket room or from inside the realm dimension

## 5. WorldSeed behavior (create or rekey)

Using a WorldSeed on a WorldAnchor:

- If the player has **no realm yet**:
  - Allocate a realm region and generate it
  - Bind this anchor as their entry point

- If the player **already has a realm**:
  - **Rekey**: relink the existing realm to this new anchor, but only once the old anchor has been destroyed
  - While the old anchor still stands, the rekey is refused ("Sever it first")

Anchors are replaceable once lost; realms persist. An owner can never move their entry point away from a siege by linking a second anchor.

---

# WorldCore (permanent structure)

## 6. WorldCore properties

Each realm has a permanent indestructible **WorldCore** at the center of the realm region.

The WorldCore:

- Is never destroyed
- Defines region center and supports border enforcement
- Determines realm spawn radius (player enters near core)
- Provides a reliable **exit** even if the WorldAnchor is destroyed

Exit behavior:

- Player interacts with WorldCore (or defined trigger) to exit
- Exit target is the player's last realm entry location
- If invalid or unavailable, fallback to a safe default

If the WorldAnchor is destroyed:

- Players inside stay inside
- They can still leave using the WorldCore
- They cannot re-enter until the owner rekeys a new anchor

---

# Siege system: World Breacher + Anchor Breaker

The current repository contains two siege blocks tied to a WorldAnchor:

- **World Breacher** opens anchor access after a successful breach
- **Anchor Breaker** destroys the anchor after a successful breaker cycle

## 7. World Breacher placement rules

World Breacher is a malicious add-on structure that must be placed **on top of** a WorldAnchor.

Placement is allowed only if:

- Someone who belongs to the realm (its owner or anyone on its access list) is **inside** it **at the moment of placement**. Players in through a breach or smuggled through a pocket room don't count.

After placement:

- The siege can continue even if they leave or disconnect

Only one breacher per anchor: siege blocks sit directly on the anchor's upper half, which has room for one (breacher or breaker).

---

## 8. Fuel (lapis) and breacher progress

Fuel: **Lapis Lazuli**

Base breach duration:

- **1 Minecraft day (20 minutes)** of uninterrupted progress

Progress advances only while all are true:

- World Breacher exists
- WorldAnchor exists
- Breacher has lapis fuel
- **Breacher chunk is loaded**

If fuel runs out:

- Progress **pauses** (no decay)

If the breacher is destroyed:

- Progress **resets to 0%**

If the anchor is destroyed:

- The breacher is destroyed
- Progress resets
- Realm entry becomes impossible until the owner rekeys a new anchor

Destruction requirements:

- The WorldAnchor **cannot be mined** at all (hardness -1); only a completed Anchor Breaker removes it
- World Breacher and Anchor Breaker can be mined (hardness 50, slow with any tool); breaking takes a long time and is interruptible
- No loot tables exist yet, so broken siege blocks drop nothing

---

## 9. Post-breach access model (checked only on use)

Access is evaluated only when a player attempts to use the WorldAnchor.

Default state:

- Only the owner can use their WorldAnchor to enter

Players on the realm's **access list** (see section 13) can also always enter.

After successful breach (100%):

- The WorldAnchor becomes accessible to **anyone** as long as:
  - The World Breacher still exists
  - The World Breacher currently contains at least 1 lapis fuel

If the breacher has no fuel:

- Access immediately reverts to owner-only

This supports both:

- Forced raids (attacker places the breacher)
- Voluntary public access (owner places the breacher themself)

No continuous "open/closed ticking" logic - only interaction-time checks.

---

## 10. Anchor Breaker

Anchor Breaker is a separate siege block placed on top of a WorldAnchor.

Rules:

- It uses lapis as fuel
- It progresses only while correctly placed and fueled
- It can be slowed by WorldCore defensive fuel in the same way as the World Breacher
- On completion, it destroys the WorldAnchor

Anchor destruction should not eject players from the realm.

---

# Defensive mechanic: WorldCore fueling

## 11. Fuel the WorldCore to slow breaches

The realm owner can fuel the WorldCore with **lapis** as a defensive measure.

Effect:

- While the WorldCore has fuel, breach progress speed is slowed by **3x** (progress rate becomes 1/3 normal)

Rules:

- Only the realm owner may insert fuel into the WorldCore
- Fuel is consumed **only while an active breach attempt is running**
- If fuel runs out mid-breach, progress speed immediately returns to normal
- No stacking beyond 3x slowdown

This creates a resource-vs-resource siege loop:

- Attackers spend lapis to push progress
- Defenders spend lapis to delay progress

---

## 12. Anchor destruction without ejection

If the WorldAnchor is destroyed:

- Players inside are **not ejected**
- They can exit via WorldCore
- They cannot re-enter until the owner rekeys a new anchor

---

# Access list

## 13. Realm access list

The realm owner manages a list of players who may always enter through the WorldAnchor, independent of any siege.

- Managed in the WorldCore GUI (owner only): type a player name and click **Add**, or click **x** to remove
- Only players who have joined the server before can be added
- Size capped by config `access.max_allowed_players` (0 = unlimited)
- Checked only at interaction time, like all other access rules
