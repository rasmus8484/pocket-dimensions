# World Core screen: the carved slab

Approved mock: `design/world-core-gui-mock.html`, published at https://claude.ai/artifact/NVQzMKyEvWwrzPU9U6TS1n (version 4).

## Who sees what

| Role | Who | Tabs | Can |
|---|---|---|---|
| Owner | the realm's owner | Overview, Access, Manage | everything; crown and uncrown managers; relocate |
| Manager | an allowed player the owner crowned | Overview, Access, Manage | add players; remove ordinary players (not managers); rename |
| Visitor | anyone else who reaches the core | Overview | add lapis to the ward (never take it out) |

- Anyone can crouch-right-click the core to open it. Before, only the owner could.
- The server checks every action against the role; the client only hides what it can't do.
- Managers are a subset of the access list. Removing a player also uncrowns them.

## Data (RealmManager.RealmData, saved)

- `managers`: a set of player UUIDs.
- `name`: up to 24 characters, trimmed, with formatting codes (`§`) stripped. An empty name shows as "Realm of <owner>".

## Network

- `CoreActionC2S(corePos, action, text, target)`, with action ADD (by name or UUID), REMOVE, TOGGLE_MANAGER, RENAME or RELOCATE. Checked server side:
  - the sender has this core's menu open;
  - the sender's role allows the action.
- `CoreSyncS2C`, sent on open, after every action, and every 2 s while the menu is open. It carries:
  - role, realm name;
  - access list (UUID, name, manager flag);
  - players online and not yet allowed;
  - players in the realm now.

## Relocate (owner only)

1. Every player in the realm is sent back to where they entered.
2. The old core is removed.
3. The old plot is retired and never handed out again.
4. A new plot is allocated and generated, with a new core. The realm's age starts again.
5. The access list, managers and name are kept.
6. Stored realm entries for the realm's players are cleared, so anyone who logged out inside is sent home on login.

## Screen (fixed 252 × 270 GUI px)

- **Slab:** one generated texture, `textures/gui/core_slab.png`. Weathered Geode Heart rock with lichen and cracks, and a broken outline baked into its alpha.
- **Rune seals (tabs):** eye, key and crown, on the slab's crown. A rune inscription runs top and bottom, with light flowing through it.
- **Hierarchy:**
  1. the realm's name, double size, glowing;
  2. rune light (siege accent) for live state;
  3. gold-inlaid section headings;
  4. dark engraved text with a lit lower lip, labels a shade lighter.
- **Polished faces:** text sits on these. They're drawn from `textures/gui/core_polished.png`.
- **Overview:**
  - Status (state, owner, age, anchor, access count);
  - Ward: a crystal-lined hollow for the lapis slot, a crystal vein gauge and the time left;
  - In the realm now (faces);
  - the player inventory in carved pockets. The inventory slots exist on Overview only.
- **Access:**
  - an "Add a player" channel with Add and Online;
  - the "Who may enter" ledger. Each row has a face niche, a name, a carved crown (gold when a manager) and a red unbind cross.
  - The online picker is a darker hollow.
- **Manage:**
  - rename;
  - a "More to come" placeholder;
  - Relocate (owner only). It opens a cracked tablet warning with a 3-second countdown.
- **Siege accent:** blue at peace, pink while breaching, orange while breaking, dull grey once the anchor is lost.

## Out of scope

- Showing the realm name on boss bars.
- An "Exit realm" button on the screen. A plain right-click on the core still exits.
