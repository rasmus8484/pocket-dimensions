# Changelog

All notable changes to Pocket Dimensions. Versions follow [Semantic Versioning](https://semver.org/).

## [0.1.0] - unreleased

The first release, for Minecraft Java 1.21.11 with Forge 61.1.0 or later. Server and clients both need the mod.

### Pocket Rooms

- **Pocket Anchor**: one object with two forms (an item and a placed block), each tied to its own sealed 16×16×16 room in the shared `pocketdimensions:pocket` dimension.
- **Using it**:
  - right-click to enter (the anchor is left at your feet)
  - crouch + right-click a block to set it down without entering
  - crouch + jump inside to leave
- **No indefinite safety**:
  - anyone can walk into a placed anchor's room
  - anyone can steal it with crouch + right-click, silently
  - anyone can mine it with a diamond-tier pickaxe (15 seconds), which deletes the room and throws everyone inside out
- **Being mined**: the people inside are warned. Reality cracks (a sound) at the first hit and at 25 / 50 / 75 %, lightning tears in from the walls, and red motes shake loose. It all stops if the miner stops.
- **Theft and logout**: a stolen anchor stays its owner's. Leaving the room while the anchor is carried brings you out next to whoever holds it. Logging out with a stolen anchor while others are inside sets it down at your feet, so nobody is trapped.
- **Living there**:
  - the walls let the sky's light through, so crops grow and no monsters spawn
  - beds work as a bed beside the anchor would (never with the anchor in the Nether or the End) and share that world's sleep count
  - you arrive on a free spot even in a packed room
- **The Tumbling Cube**: the placed anchor is a small cube turning inside bands of runes, faster and brighter while someone is home. Inside, the room is that cube seen from within: walls of end portal void.

### Realms

- **World Anchor + World Seed**: link an anchor to your own realm, a plot of overworld-like land in the shared `pocketdimensions:realm` dimension. One realm per player; it shares the overworld's day and sleep count.
- **Quiet and untouched by default**: no structures, no dungeons, no monsters spawning. Server owners can switch structures, features and natural spawns per kind, with whitelists and blacklists.
- **The World Core (the Geode Heart)**: an indestructible floating boulder at the realm's centre.
  - right-click it to go home
  - crouch + right-click to open its carved-stone screen with Overview, Access and Manage tabs
  - its colours show the realm's state from anywhere inside
- **Roles**:
  - the owner can do everything
  - managers (crowned by the owner) can edit the access list and name the realm
  - visitors can only give lapis
- **Realm management**: the access list (by name or from everyone online) and naming the realm. The owner can also relocate it: the realm grows again somewhere new and the old one is lost.
- **Borders and arrival checks**:
  - you can't walk, fly, pearl or chorus out of your plot
  - portals are blocked
  - anyone arriving by another mod's teleport without having come through an anchor is sent back out
- **An empty (unlinked) anchor** can be mined with a diamond-tier pickaxe in 3 seconds and drops itself; a linked one can't be mined at all.

### Sieges

- **World Breacher**: burns lapis to tear a realm open. When complete, anyone may enter for as long as it stands.
- **Anchor Breaker**: burns lapis to destroy the anchor for good. The realm survives and can be relinked with a new anchor.
- **Placement**: both sit on a linked anchor, one at a time. Anyone but the owner can only place one while someone who belongs to the realm is inside.
- **Length**: 10 minutes of fuelled progress by default. While the World Core holds lapis (the ward), sieges run half as fast, and the core burns its own lapis to do it.
- **Siege bars**: themed bars replace the vanilla boss bar. They show progress, time left and both sides' lapis, update the moment lapis changes, and drain the burning lapis smoothly.
- **Visible in the world**:
  - the anchor falls under the siege block's influence (pink runes, or cracks, heated runes and frozen lightning)
  - the World Core turns pink or red
  - the realm hears its anchor crack
- **Mining siege blocks**: a diamond-tier pickaxe, 250 seconds by default. Mined siege blocks vanish with their lapis unless the server turns drops on.

### Travelling with company

- **Your mount**: going into or out of a pocket room or a realm by choice brings your mount (still ridden) and everything aboard it except other players.
- **Your lead**: animals on your lead come along too; monsters on a lead stay behind.
- **Riding out of a pocket room**: right-click any of its walls.
- **Thrown out, you go alone.**

### Admin commands (ops)

| Command | What it does |
|---|---|
| `/pd owner <player\|uuid>` | Hand over the realm or room you're looking at (World Anchor, World Core or Pocket Anchor) |
| `/pd allow <player> [man]` / `/pd deny <player> [man]` | Edit a realm's access list and managers |
| `/pd disown <player>` | Take a realm away |
| `/pd regenCore` | Rebuild a broken World Core where it stood |
| `/pd regenAnchor <player>` | Put a lost World Anchor back where it stood |
| `/pd test mineAnchor [seconds]` | Mine the anchor of the room you're in, for testing alone |

### Also

- **Recipes** for all five items, using netherite or a nether star and nothing from the End. They are unlocked in the recipe book on first entering the Nether.
- **The Folded Space advancement tab**, with a few hidden steps for thieves and burglars.
- **Tooltips** on every item, with the full rules on Shift.
- **Configuration**: siege timing and lapis in `config/pocketdimensions-common.toml`; mining times, fuel caps and realm generation per world in `serverconfig/pocketdimensions-server.toml`.
- **Protected blocks**:
  - pistons can't move the mod's blocks, and explosions can't break them
  - the Wither and the Ender Dragon can't break World Anchors, World Cores, room walls or siege blocks (a Pocket Anchor stays breakable, as it's meant to be)
  - hoppers can't take lapis out, and dispensers can't place anchors

[0.1.0]: https://github.com/rasmus8484/pocket-dimensions/releases/tag/v0.1.0
