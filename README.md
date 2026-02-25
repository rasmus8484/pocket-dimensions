# Pocket Dimensions – Forge 1.21.x PvP Sandbox Mod

This repo documents a Forge mod for **Minecraft Java 1.21.x (Java 21)** designed for a **sandbox PvP server**.

The mod adds two spatial systems:

1. **Pocket Rooms** – sealed, chunk-aligned, teleport-only rooms tied to items, with PvP intrusion and permanent-loss mechanics.
2. **Pocket Worlds (Realms)** – persistent, player-bound regions inside a shared realm dimension, accessed through anchors and governed by a siege system.

Design goals:

- **No indefinite safety** (players can’t hide forever).
- **Physical risk** (entry points exist in the world and can be contested).
- **Server-authoritative** (no client trust; all validation server-side).
- **Scales with active players** (no per-item/per-player dimensions).
- **Strict isolation** (no crossing into other players’ realms).

---

## Documentation files

- **Pocket Rooms:** see `pocket-rooms.md`
  - Room structure & allocation (3×3 chunk plots)
  - Pocket Anchor lifecycle (enter / theft / destruction)
  - Exit rules, failsafes, and disconnect behavior

- **Pocket Worlds (Realms):** see `pocket-worlds.md`
  - WorldAnchor + WorldSeed creation and **rekey**
  - WorldCore (permanent core + exit)
  - Realm dimension constraints (no portals, no structures, no natural hostile spawns)
  - Siege system (WorldBreacher) + defender slowdown via WorldCore fueling

---

## Global technical rules (applies to everything)

### Dimension strategy (performance)
- Exactly **two** dimensions are used:
  - `pocketdimensions:pocket`
  - `pocketdimensions:realm`
- No “dimension per item” and no “dimension per player”.
- Total content is partitioned by **regions inside shared dimensions**.

Performance scales with:
- Active players
- Loaded chunks

Not with:
- Total number of rooms/realms created (when unloaded)

### Security / anti-exploit (minimum requirements)
Must prevent or handle:
- Piston movement of anchors/breach blocks (if relevant)
- Explosion relocation / duplication edge cases
- UUID/NBT tampering (items must not be authoritative)
- Chunk unload duplication / state desync
- Any teleport bypass into чужі regions (pearls, chorus, modded teleports)
- Portal creation/activation in the realm dimension
- Untrusted client-side triggers (all checks on server)

### Access validation model
Access is validated **only when interacting with the entry point** (anchor use / block interaction),
not by constantly “ticking” access state every game tick.

(Progress ticking for sieges is still time-based, but only while the relevant chunk is loaded;
see `pocket-worlds.md`.)

---

## Naming recap
- **Pocket Item** → creates/links a **Pocket Room**
- **Pocket Anchor** → the placed block representing an active pocket entry point
- **WorldSeed** → creates or rekeys a realm binding
- **WorldAnchor** → realm entry point block (placeable in any dimension)
- **WorldBreacher** → siege add-on block placed atop a WorldAnchor
- **WorldCore** → indestructible structure at center of a realm region
