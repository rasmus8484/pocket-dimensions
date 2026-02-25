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
- **16 × 16 × 16** air volume

### Boundary shell
Boundary is a **custom mod block** with:

- Pure white texture
- Emits light level **15** (glowstone strength)
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
- **20 × 20 × 20**

No door, hatch, portal, or deliberate gap exists.
**The only entry/exit is teleportation.**

---

## 3. Allocation model (3×3 chunk plot)

Each pocket room is assigned a **3×3 chunk region** (48×48 blocks).

- The **center chunk** contains the sealed 20×20×20 room.
- The surrounding 8 chunks are a buffer to prevent overlap / interaction.

This is required because the room is larger than a single chunk footprint.

---

## 4. Pocket Item binding

Each Pocket Item carries an identifier:

- `pocket_id` (UUID)

The server stores authoritative mapping:

- `pocket_id → RoomData (plot coords, owner, etc.)`

The item is never trusted to provide coordinates.
The server resolves coordinates from `pocket_id`.

---

# Pocket Anchor (PvP intrusion + lifecycle)

The Pocket Anchor is the placed block that represents the active pocket entry point.

---

## 5. Activating a Pocket Item (entering the room)

When a player uses a Pocket Item to enter:

- Pocket Item is **removed from inventory**
- A **Pocket Anchor** block entity is placed at the activation location
- Player is teleported into the linked pocket room

The anchor stores:
- `pocket_id`
- Owner UUID (for reference / logging)
- Timestamp (optional)

---

## 6. Anchor interactions

### A) Right-click (enter)
Right-clicking the Pocket Anchor teleports the player into the room.

- No ownership restriction
- No warning to players inside
- Enables direct invasion

### B) Crouch + right-click (silent theft)
Crouch-right-clicking the Pocket Anchor:

- Instantly converts the anchor into a Pocket Item in the thief’s inventory
- Removes the anchor block
- Sends **no warning** to players inside

This is a stealth theft mechanic.

### C) Mining the anchor (siege destruction)
Mining behaves as a high-hardness block.

While being mined:
- Both the miner and the players inside are warned via:
  - Particles
  - Sounds
  - (Optional) action bar warning

If fully mined (broken):
- The player(s) inside are force-ejected
- The Pocket Item is **permanently destroyed**
- The pocket room is **permanently deleted**
- All contents inside are **permanently lost**
- Nothing drops

This is intentional irreversible loss.

Tool gating:
- Requires **netherite-tier or higher** harvest level (long break time)

---

## 7. Manual placement of anchor (pre-placement)

If a player is holding a Pocket Item and crouch-right-clicks a valid block face:

- A Pocket Anchor is placed
- Pocket Item is removed from inventory
- Player remains outside

Players can then right-click the anchor to enter.
Others may invade immediately.

---

# Exit mechanics

Players inside the pocket room exit via **crouch + jump** (server detected).

---

## 8. Normal exit (anchor exists, not stolen)

On normal exit:
- Player teleports to the anchor location
- The Pocket Item does **not** drop as an item entity
- A **placed pickup anchor** remains on the ground at exit location
- Player must **crouch + right-click** to retrieve the Pocket Item
- This creates a forced interaction window (PvP exposure)

(Effectively: after exit, the item remains as a placed anchor block until picked up.)

---

## 9. Exit when another player holds the item

If someone silently stole the Pocket Item into their inventory,
and a player exits the pocket room:

- Exiting player appears in a safe open space **adjacent to the item holder**
- Server must search for valid clearance to avoid suffocation
- If no adjacent safe space exists, expand outward until found

This enables ambush scenarios and prevents “safe escape” if your anchor was stolen.

---

## 10. Exit failsafe (no valid anchor exit)

If the anchor location cannot be used as a safe exit for any reason:

- Player exits to the **location they entered from** (dimension + pos + rotation)

The server must store each player’s pocket entry location for this failsafe.

---

# Disconnect / logoff handling

“Disconnect” includes manual logout, network disconnect, or crash.

---

## 11. Player logs off inside the pocket room
- The player remains logically inside the room.
- The Pocket Anchor persists.

---

## 12. Player disconnects while holding stolen Pocket Item and players are inside
If a player has stolen the Pocket Item into inventory, and players remain inside,
then on logout/disconnect:

- A Pocket Anchor is automatically placed at the disconnecting player’s feet
- The Pocket Item is removed from their inventory
- Players inside remain linked to the anchor

If the exact feet position is invalid:
- Place at nearest valid block at/under that position
- Fallback to last known valid on-ground position

This prevents trapping occupants by logging off with the stolen item.

---

## 13. Multi-player occupancy
- Multiple players may be inside the same pocket room simultaneously.
- Theft/destruction rules apply to all occupants.
