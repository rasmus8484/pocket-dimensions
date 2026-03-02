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

### World generation

- Custom terrain or flat baseline (configurable)
- **No structure generation** of any kind

### Mob spawning

- **No natural hostile mob spawning**
- (Optional) no natural passive mob spawning
- **Spawner blocks must still function normally**

### Portals

All portals are disallowed in the realm dimension:

- Nether portals (creation + activation blocked)
- End portals blocked
- End gateways blocked
- Any portal-based dimension change blocked

Entry and exit must be via anchor/core teleport mechanics only.

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

## 5. WorldSeed behavior (create or rekey)

Using a WorldSeed on a WorldAnchor:

- If the player has **no realm yet**:
  - Allocate a realm region and generate it
  - Bind this anchor as their entry point

- If the player **already has a realm**:
  - **Rekey**: relink the existing realm to this new anchor
  - Old anchor becomes invalid or unbound

Anchors are replaceable; realms persist.

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

- The realm owner is currently **inside** their realm **at the moment of placement**

After placement:

- The siege can continue even if the owner leaves or disconnects

Only one breacher per anchor.

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

- WorldAnchor, World Breacher, and Anchor Breaker are destroyable with **netherite-tier or higher** harvest level
- Breaking takes a long time and is interruptible

---

## 9. Post-breach access model (checked only on use)

Access is evaluated only when a player attempts to use the WorldAnchor.

Default state:

- Only the owner can use their WorldAnchor to enter

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
