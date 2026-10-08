# Pocket Rooms (Pocket Dimension)

Related:
- Project overview & global rules: `README.md`
- Realms system: `pocket-worlds.md` (separate system)

---

## 1. Dimension

Pocket rooms exist in a single shared dimension:

- `pocketdimensions:pocket`

No room is its own dimension.

---

## 2. Room structure

### Interior (free space)
- **16 x 16 x 16** air volume

### Boundary shell
Boundary is a **custom mod block** with:

- Looks like the **inside of the Tumbling Cube** you entered (bigger on the inside): every wall, the floor and the ceiling are windows into the end-portal void, ringed by netherite edges with gold corners. Full-bright, nothing casts a shadow on them. One renderer per room draws the void (from a block entity in one floor corner)
- The shell lets the sky's light through, so the whole room is fully lit: seeds can be planted and grow anywhere, and no hostile mob spawns inside
- The room is folded space inside the world around its anchor, so it sleeps with that world: beds work whenever a bed at the anchor would (night in the overworld or a realm, never in the Nether or the End) but never set your spawn, and sleepers count with that world's players (one shared "players sleeping" count; enough of them sleeping skips the night). The overworld and the realms share one day, so they share one count too. While the anchor is being carried, the room belongs to wherever its occupant came in from
- Unbreakable / extremely high hardness
- High blast resistance
- Not movable by pistons
- Fluid-proof (cannot be replaced by liquids)
- Not obtainable in survival (unless intentionally exposed later)

Shell thickness:
- Walls: **2 blocks** thick (all sides)
- Floor: **2 blocks** thick
- Ceiling: **2 blocks** thick

Total outer size:
- **20 x 20 x 20**

No door, hatch, portal, or deliberate gap exists.
**The only entry/exit is teleportation.**

---

## 3. Allocation model (3x3 chunk plot)

Each pocket room is assigned a **3x3 chunk region** (48x48 blocks).

- The sealed 20x20x20 room sits in the middle of the plot (offset 14 blocks from the plot corner).
- The remaining space around it is a buffer to prevent overlap / interaction.

This is required because the room is larger than a single chunk footprint (it straddles chunk borders).

---

## 4. Pocket Anchor binding

The **Pocket Anchor** is a single object with two forms:

- **Item form**: carried in an inventory (`pocketdimensions:pocket_anchor` item)
- **Placed form**: the anchor block in the world (`pocketdimensions:pocket_anchor` block)

Each room has exactly one anchor, and it is always in one of the two forms.
Placing it, entering with it, or stealing it simply toggles between them.

Both forms carry the same identifier:

- `pocket_id` (UUID)

The server stores authoritative mapping:

- `pocket_id -> RoomData (plot coords, owner, etc.)`

The item is never trusted to provide coordinates.
The server resolves coordinates from `pocket_id`.

A blank anchor (no `pocket_id`) allocates a new room on first use.
If the linked room has been destroyed, a new room is allocated instead.

---

# Pocket Anchor (PvP intrusion + lifecycle)

The placed Pocket Anchor represents the active pocket entry point.

---

## 5. Entering with the anchor in item form

When a player right-clicks while holding the anchor (in the air or on a block):

- The anchor leaves the player's inventory
- It is **placed at the player's feet** (or an adjacent free spot)
- Player is teleported into the linked pocket room

A player never enters while still carrying the anchor: entering always leaves the placed anchor behind as the way back out.
- The anchor goes at the player's feet even under water or lava (the liquid is displaced), or beside them; if there is no spot at all, entering is refused and the item is kept
- Liquids can't wash a placed anchor away (it is forced solid)

The anchor stores:
- `pocket_id`
- Owner UUID: always the room's owner (who made it, or who `/pd owner` gave it to), whoever places it, so a stolen anchor stays its owner's

---

## 6. Anchor interactions

### A) Right-click (enter)
Right-clicking the Pocket Anchor teleports the player into the room.

- No ownership restriction
- No warning to players inside
- Enables direct invasion

### B) Crouch + right-click (silent theft)
Crouch-right-clicking the Pocket Anchor:

- Instantly folds the anchor back into item form in the thief's inventory (same `pocket_id`, same room)
- Removes the anchor block
- Sends **no warning** to players inside

This is a stealth theft mechanic.

### C) Mining the anchor (siege destruction)
Mining takes a fixed time (`pocket_anchor_mine_seconds`, 15 seconds by default) with a diamond-tier pickaxe or better.

While being mined:
- Outside: red motes are pulled out of the cube and reality cracks (a sound) at the first hit and at 25 / 50 / 75 %. Every anchor does this, occupied or not, so mining never gives away whether anyone is inside
- The miner is told at the first hit if someone is inside ("Voices echo from within")
- Inside: the same crack at the same volume, a warning on the action bar, motes shaken off the walls, and the Anchor Breaker's lightning, enlarged, tearing in from every wall: one set at 25 %, another at 50 %, another at 75 %
- When the mining stops, the cracks are gone (the next attempt starts over, as mining does)

If fully mined (broken):
- The player(s) inside are force-ejected
- The anchor is **permanently destroyed**
- The pocket room is **permanently deleted**
- All contents inside are **permanently lost**
- Nothing drops

This is intentional irreversible loss.

### D) Chorus fruit
Chorus fruit teleportation is cancelled inside pocket rooms.

Tool gating:
- Requires a **diamond-tier or better** pickaxe; the break time is set by `pocket_anchor_mine_seconds` in the server config

*Implementation: `event/AnchorMiningHandler` (left-click events, progress counted as the server counts it), pure `manager/AnchorMining` and `manager/RoomCracks`; the room's `RoomVoidBlockEntity` carries the crack count (synced, never saved) and `client/RoomVoidRenderer` draws them.*

---

## 7. Manual placement of anchor (pre-placement)

If a player is holding the anchor in item form and crouch-right-clicks a valid block face:

- The anchor is placed on that face
- It leaves the player's inventory
- Player remains outside

A blank anchor cannot be pre-placed; it must be used once to allocate its room.

Players can then right-click the anchor to enter.
Others may invade immediately.

---

# Exit mechanics

Players inside the pocket room exit via **crouch + jump** (server detected).

---

## 8. Normal exit (anchor exists, not stolen)

On normal exit:
- Player teleports to the anchor location
- The anchor does **not** drop as an item entity
- The placed anchor remains on the ground at the exit location
- Player must **crouch + right-click** to pick the anchor back up
- This creates a forced interaction window (PvP exposure)

(Effectively: after exit, the anchor stays in placed form until picked up.)

---

## 9. Exit when another player holds the item

If someone silently stole the anchor into their inventory,
and a player exits the pocket room:

- Exiting player appears in a safe open space **adjacent to the item holder**
- Server must search for valid clearance to avoid suffocation
- If no adjacent safe space exists, expand outward until found

This enables ambush scenarios and prevents "safe escape" if your anchor was stolen.

---

## 10. Exit failsafe (no valid anchor exit)

If the anchor location cannot be used as a safe exit for any reason:

- Player exits to the **location they entered from** (dimension + pos + rotation)

The server must store each player's pocket entry location for this failsafe.

---

# Disconnect / logoff handling

"Disconnect" includes manual logout, network disconnect, or crash.

---

## 11. Player logs off inside the pocket room
- The player remains logically inside the room.
- The Pocket Anchor persists.

---

## 12. Player disconnects while holding a stolen anchor and players are inside
If a player has stolen the anchor into inventory, and players remain inside,
then on logout/disconnect:

- A Pocket Anchor is automatically placed at the disconnecting player's feet
- The anchor leaves their inventory (it changes form, it is not duplicated)
- Players inside remain linked to the anchor

If the exact feet position is invalid:
- Place it at one of the 8 blocks around the feet
- If all of those are blocked too, no anchor is placed: it stays in the offline player's inventory, and anyone exiting the room meanwhile comes out where they entered it from (or at world spawn)

This prevents trapping occupants by logging off with the stolen item.

---

## 13. Multi-player occupancy
- Multiple players may be inside the same pocket room simultaneously.
- Theft/destruction rules apply to all occupants.
