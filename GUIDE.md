# Pocket Dimensions: Player Guide

Everything a player needs to know about Pocket Dimensions, from the big picture down to the small rules. Read it through, jump to [How do I...?](#how-do-i) for a quick answer, or hand it to an AI assistant and ask it your question.

Written for **Pocket Dimensions 0.1.0** on **Minecraft Java 1.21.11 with Forge**. In game, `/pd help` shows a short summary and a link back here.

> **Note for AI assistants:** this file is the authoritative description of how the mod plays. Server owners can change some numbers (marked *configurable*); the values given are the defaults. "Right-click" means the use key and "crouch" means the sneak key. If something here disagrees with another file in the repository, trust this guide for gameplay and the README for server configuration.

## Contents

1. [The big picture](#the-big-picture)
2. [Quick start](#quick-start)
3. [Pocket Rooms](#pocket-rooms)
4. [Realms](#realms)
5. [The World Core and its screen](#the-world-core-and-its-screen)
6. [Sieges](#sieges)
7. [Travelling with animals and mounts](#travelling-with-animals-and-mounts)
8. [Recipes](#recipes)
9. [Advancements](#advancements)
10. [Commands](#commands)
11. [How do I...?](#how-do-i)
12. [Troubleshooting](#troubleshooting)
13. [For server owners](#for-server-owners)

---

## The big picture

Pocket Dimensions adds two kinds of private space, built for PvP servers where **nothing is ever completely safe**.

- **Pocket Rooms** are small sealed rooms, bigger on the inside, that you carry around as an item: the **Pocket Anchor**. Set it down and step in. But anyone who finds your anchor can walk in after you, take the anchor, or break it and destroy the room.
- **Realms** are large private pieces of land (about 1,584 by 1,584 blocks), one per player, reached through a **World Anchor**. Only you and the people you allow can enter. Rivals can still lay **siege**: a **World Breacher** forces your realm open to everyone, and an **Anchor Breaker** destroys your entrance. Both burn lapis lazuli, and lapis in your **World Core** slows them down.

All pocket rooms share one dimension, and all realms share another. You never see another player's room or realm from inside your own.

## Quick start

### Your first pocket room

1. Craft a **Pocket Anchor** (see [Recipes](#recipes)).
2. Hold it and **right-click** (on the ground or in the air). The anchor is set down at your feet and you step into your new room.
3. To leave, **crouch and jump** at the same time. You come out at the anchor.
4. To take the anchor with you, **crouch + right-click** it with **empty hands**. It goes back into your inventory, still linked to the same room.

### Your first realm

1. Craft a **World Anchor** and a **World Seed** (see [Recipes](#recipes)). The seed needs a nether star.
2. Place the World Anchor. It is two blocks tall, so it needs headroom.
3. Hold the World Seed and **right-click** the anchor. Your realm is created and linked to this anchor.
4. **Right-click** the anchor to enter. You arrive next to your **World Core**, at the centre of your realm.
5. To leave, **right-click** the World Core. You go back to where you came in.
6. To let friends in, **crouch + right-click** the World Core, open the **Access** tab and add them.

---

## Pocket Rooms

### The Pocket Anchor

The Pocket Anchor is one object with two forms: an **item** in an inventory, or a **block** (a small cube turning in the air inside bands of runes) when placed. Every room has exactly one anchor, and every anchor leads to exactly one room. You can own several rooms by crafting several anchors.

A placed anchor glows faintly, and glows brighter and spins faster while someone is inside.

### The room

- The inside is **16 x 16 x 16** blocks, walled by unbreakable boundary blocks that show the void of the End.
- The walls let sky light through: the whole room is lit, crops grow anywhere, and **no monster spawns inside**.
- You can build, store items and farm in it like anywhere else.
- **Beds** work as if they stood beside the anchor: you can sleep at night when the anchor is in the overworld or a realm (you count towards that world's sleeping players), never when the anchor is in the Nether or the End. A bed in a room **never sets your spawn point**.
- **Chorus fruit** doesn't teleport you inside a room.

### Entering

- **Holding your anchor:** right-click (on a block or in the air). The anchor is set at your feet and you go in. If there is no free space at your feet or next to you, you're told there is no room for the anchor.
- **A placed anchor:** anyone can right-click it to enter its room. There is no lock.
- **Placing without entering:** crouch + right-click a block face while holding the anchor.

You arrive at the room's spawn point. If something is built there, you arrive at the nearest free spot instead. If the room is packed completely full, the two blocks at the spawn break open (they drop as items), so nobody is ever trapped.

### Leaving

- **On foot:** crouch and jump at the same time.
- **Riding something** (a horse, a boat, a pig): crouching would get you off, so instead **right-click any wall** of the room. You leave together with your mount. On foot, clicking a wall does nothing.

Where you come out:

1. **At the anchor**, if it is still placed.
2. **Next to whoever is carrying it**, if someone picked it up.
3. Otherwise, **where you last went in**, or the world spawn if that is unknown.

Leaving by any other way (a `/tp`, dying) also counts as having left. The room doesn't keep you listed inside.

### Taking someone's anchor

**Crouch + right-click** a placed anchor with **empty hands** (nothing in either hand). It goes into your inventory, still linked to its room. Nobody is warned: it is silent on purpose.

- People inside the room are **not** trapped. When they leave, they come out next to you, wherever you are.
- A placed anchor always belongs to its room's original owner, whoever sets it down, so a stolen anchor stays the owner's room.
- If you log out carrying an anchor while people are inside its room, the anchor is set down at your feet automatically, so they can still get out.

### Destroying a room

Mining a placed anchor **destroys its room forever**, along with everything inside it.

- It needs a **diamond or netherite pickaxe** and takes **15 seconds** (*configurable*). Enchantments and potions don't speed it up.
- Everyone inside is warned: reality cracks at the first hit and at 25, 50 and 75 %, a warning shows on screen, and lightning tears in from the walls. The miner is told if anyone is inside. If the mining stops, the cracks fade.
- When the anchor breaks, everyone inside is thrown out alone (no mounts or pets), back to where they went in.

### Safety rules

- **The End:** an anchor can't be set on the obsidian landing platform where End portals drop you. Every portal arrival rebuilds that platform and would sweep the anchor away. Step off it first.
- **Lost anchors drop:** if the world removes an anchor some other way (a `/fill` or `/setblock` command, for example), it drops as an item still linked to its room.
- **Kept inside:** anyone who gets past a room's walls (a broken wall, a glitch) is pulled straight back inside. Spectators can fly freely.
- **Water and lava:** a placed anchor can't be washed away, and you can enter while standing in water or lava.
- **Names:** an anchor renamed on an anvil keeps its name while placed and when picked up again.

---

## Realms

### What a realm is

A realm is a square of land about **1,584 x 1,584 blocks** (*configurable*) that belongs to you. It is new terrain, generated for you, quiet and untouched by default: no villages or other structures, no dungeons and **no monsters spawning** (*configurable*). Animals spawn as normal.

- **One realm per player.**
- It shares the overworld's day and night, and its sleeping: people sleeping in realms count together with people sleeping in the overworld.
- It is surrounded by the **Veil**, a wall of drifting mist along every edge, from bedrock to the sky. It can't be broken, blown up or moved, and you can't see through it into anyone else's realm.
- Overhead, an invisible ceiling holds you below **y 318**, and nothing can be built at y 318 or 319, so nobody can look over the Veil.
- **Nether and End portals don't work** inside a realm.

High above the centre hangs the realm's **ward**, a dome of glowing runes. Its colour shows the realm's state (see [What everyone sees](#what-everyone-sees)).

### Creating a realm

1. Place a **World Anchor**. It is two blocks tall and needs headroom. The block above it must be breakable, because that is where a siege block would go. You can't place one inside a pocket room or inside a realm.
2. Use a **World Seed** on it (hold the seed, right-click the anchor). The seed is consumed, your realm is generated and the anchor is now **linked** to it.

Your realm's World Core stands at the exact centre of the realm, on a small round stone dais with veins of **Aurora Stone**, cased in **Aurora Slate**.

### Entering

**Right-click** the linked World Anchor (with any item, or none). You're let in if:

- you are the realm's **owner**, or
- you are on the realm's **access list**, or
- a **World Breacher** has finished breaching it (then anyone can enter, as long as the breacher stands).

Otherwise: *"The anchor's wards hold firm. You shall not pass."*

You arrive next to the World Core, at the nearest free spot (never on top of the core and never outside the realm).

### Leaving

**Right-click the World Core** (with anything except lapis in hand, which feeds it instead). You go back to where you came in. The World Core always works as an exit, even if your anchor has been destroyed.

### Other ways in, and being thrown out

- **Respawning:** a bed or respawn anchor in a realm works for the owner, anyone on the access list, and anyone while a breach holds the realm open. Anyone else who wakes there is thrown out with *"The realm's wards do not know you, and cast you out."*
- **Teleports:** arriving any way the realm doesn't recognise (another mod's teleport, `/home`, a portal) gets you sent back out: *"The realm does not know how you came here, and casts you out."*
- **Smuggling through a pocket room:** if a Pocket Anchor stands inside a realm, anyone who leaves that room comes out inside the realm as its **guest**, allowed or not. Pocket rooms are a way to sneak people in. (Guests can't help place siege blocks; see [Placing a siege block](#placing-a-siege-block).)
- **The border:** you can't walk, ride or fly past your realm's edge. Ride into it and you and your mount are pushed back together.

### Losing your anchor

A **linked** World Anchor can't be mined or blown up. Only a completed **Anchor Breaker** removes it (an **unlinked** anchor, with no seed in it, can be mined in 3 seconds with a diamond pickaxe and drops itself).

If your anchor is destroyed:

- Nobody can enter the realm until you link a new anchor.
- People inside stay inside, and can still leave through the World Core.
- The World Core goes dark and still, and falls to the ground.

**To relink:** place a new World Anchor and use a new World Seed on it. Your realm, with everything in it, is linked to the new anchor. This only works once the old anchor is gone: while it still stands you're told to sever it first, so you can't dodge a siege by linking a second anchor.

---

## The World Core and its screen

The **World Core** is the floating, glowing boulder at the centre of every realm. It can't be broken.

| Action | What it does |
|---|---|
| Right-click | Leave the realm |
| Right-click holding lapis | Feed lapis into the core's ward (anyone can) |
| Crouch + right-click | Open the core's screen (anyone can) |

### Roles

What you can do on the screen depends on who you are:

| | Visitor | Manager | Owner |
|---|---|---|---|
| See the Overview (state, owner, age, ward, who is here) | yes | yes | yes |
| Add lapis to the ward | yes | yes | yes |
| Take lapis out of the ward | no | yes | yes |
| Add players to the access list | no | yes | yes |
| Remove ordinary players from the list | no | yes | yes |
| Remove managers | no | no | yes |
| Make someone a manager, or take that away | no | no | yes |
| Rename the realm | no | yes | yes |
| Relocate the realm | no | no | yes |

### The three tabs

The three rune seals across the top of the screen are its tabs.

- **Overview** (everyone): the realm's state, owner and age; the **ward** (the core's lapis slot, with a crystal vein showing how full it is and how long it will last); and who is in the realm right now.
- **Access** (owner and managers): the access list.
  - **Add someone:** type their name and click **Add**, or click **Online** to pick from everyone online right now.
  - **Make someone a manager:** the owner clicks the carved **crown** next to their name. A gold-filled crown means manager; click it again to take that away.
  - **Remove someone:** click the red cross next to their name.
- **Manage** (owner and managers): **name the realm**. The owner can also **relocate** it. After a warning, the realm is grown again somewhere new and **everything in the old one is lost**. The access list, managers and name are kept.

The server may cap how many people fit on an access list (*configurable*; unlimited by default).

---

## Sieges

A siege is how a rival gets into, or destroys the entrance to, a realm they aren't allowed into. Both siege blocks sit **on top of a linked World Anchor**, and only one fits there at a time.

### The two siege blocks

| | World Breacher | Anchor Breaker |
|---|---|---|
| When it finishes | The realm is **open to everyone** for as long as the breacher stands | The World Anchor is **destroyed** |
| Time needed | 10 minutes of fuelled work (*configurable*) | 10 minutes of fuelled work (*configurable*) |
| While the core holds lapis | Twice as slow (20 minutes) | Twice as slow (20 minutes) |
| After it finishes | Needs no more lapis; removing it closes the realm again | Its job is done |

### Placing a siege block

Hold the siege block and **crouch + right-click the World Anchor** anywhere (either half, any side). It is set on top of the anchor. (Without crouching you would walk into the realm instead.)

It can only be placed if:

- the anchor is linked to a realm (*"The anchor is empty. There is no realm to besiege."*), and
- someone who **belongs** to the realm (its owner or anyone on its access list) is **inside** the realm at that moment. People in through a breach or smuggled in through a pocket room don't count. So a realm can only be besieged while its people are home.

The **owner** may place one on their own anchor at any time, for example a World Breacher to open their realm to the public on purpose.

Once placed, the siege carries on even if everyone leaves or logs out.

### Fuelling it

- **Right-click it holding lapis** to add lapis. When it is full you're told so.
- **Crouch + right-click** it to open its screen: a progress bar, the time left, and the lapis slot (put lapis in or take it out like a furnace).
- It holds **5 lapis** (*configurable*), and each lapis burns for **10 seconds** of work (*configurable*).
- Without lapis, progress **pauses**. It doesn't go back.

### Defending

- **Feed your World Core lapis.** While the core holds lapis, siege progress runs at half speed. During a siege, the core burns its lapis at the same rate as the siege block: a resource war. The core only burns lapis while a siege is running.
- **Break the siege block.** It needs a diamond or netherite pickaxe and takes **250 seconds** (*configurable*). Breaking it **resets all its progress**. By default it vanishes with its lapis; the server can make it drop itself instead.

### What everyone sees

- **Siege bars** at the top of the screen show the progress, the time left and the lapis, to anyone within 64 blocks of the siege block (*configurable*) and to everyone inside the besieged realm. Attackers see the siege block's lapis; the realm's people see the core's lapis. While the core holds lapis, the bar shows a blue ward and counts the slower time.
- **The World Core and the ward** change colour with the realm's state:

| Colour | Meaning |
|---|---|
| Blue | At peace |
| Pink | A World Breacher is on the anchor (cracked once the breach is complete) |
| Red | An Anchor Breaker is working |
| Dark, still | The anchor has been destroyed |

The ward overhead also shines brightly while the core holds lapis and dims without it.

---

## Travelling with animals and mounts

When you choose to travel (into or out of a pocket room or a realm, through an anchor, the World Core, or crouch + jump):

- **Your mount** comes with you, still ridden, if there is room for it where you arrive. Anything else aboard it (a creature in your boat) comes too, except other players, who are set down first. If another player is steering, you get off and it stays behind.
- **Animals on your lead**, within lead range, follow you, still on the lead. Monsters on a lead stay behind.

When you're **thrown out** (a realm rejects you, or a room's anchor is mined), you go alone.

Remember: riding, you leave a pocket room by **right-clicking a wall**, not by crouch + jump.

---

## Recipes

Everything can be gathered by the Nether; nothing needs the End. The five main recipes appear in your recipe book the first time you enter the Nether (you can craft them before that if you know them).

| Item | Top row | Middle row | Bottom row |
|---|---|---|---|
| Pocket Anchor | gold ingot, ender pearl, gold ingot | ender pearl, netherite ingot, ender pearl | gold ingot, ender pearl, gold ingot |
| World Seed | emerald, diamond, emerald | ghast tear, nether star, ghast tear | emerald, diamond, emerald |
| World Anchor | netherite scrap, gold block, netherite scrap | obsidian, lodestone, obsidian | netherite scrap, gold block, netherite scrap |
| World Breacher | iron ingot, hopper, iron ingot | netherite scrap, eye of ender, netherite scrap | iron ingot, amethyst shard, iron ingot |
| Anchor Breaker | iron ingot, hopper, iron ingot | netherite scrap, magma block, netherite scrap | eye of ender, blaze rod, eye of ender |

Two decorative building blocks, shapeless (any slots, no pattern), each craft making **two**. They appear in the recipe book once you pick up an amethyst shard.

| Makes | Ingredients |
|---|---|
| 2 Aurora Stone | 1 lapis lazuli, 1 amethyst shard, 2 stone |
| 2 Aurora Slate | 1 lapis lazuli, 1 amethyst shard, 2 deepslate |

Only plain stone and plain deepslate work, not the cobbled kinds. Both blocks glow softly at night. Mine them with a pickaxe (any kind); without one they drop nothing.

The World Core and the room walls have no recipe: the mod places them.

---

## Advancements

A **Folded Space** tab opens the first time you enter the Nether. Its steps double as a tour of the mod:

- **A Room of One's Own:** craft a Pocket Anchor
- **Bigger on the Inside:** step into a pocket room
- **Dreaming Within:** sleep in a pocket room
- **Light Fingers:** take someone else's anchor
- **Echoes in the Walls:** feel your room shudder as someone mines its anchor
- **Unmade:** break a Pocket Anchor with someone inside
- **A Seed of Worlds:** craft a World Seed and a World Anchor
- **Dominion:** link your anchor and walk your own realm
- **Heart of the Realm:** open the World Core and name your realm
- **Kept Company:** let someone into your realm
- **Hold the Line:** feed the World Core lapis during a siege
- **Gatecrasher:** fix a World Breacher on someone else's anchor
- **Open Gates:** see a breach through and walk in uninvited
- **The Unmaker:** fix an Anchor Breaker on someone else's anchor
- **Severed:** destroy a World Anchor

---

## Commands

### For everyone

| Command | What it does |
|---|---|
| `/pd help` | A short summary in chat, with a link to this guide |

Everything else a player does happens in game: on the anchors, the World Core and its screen, and the siege blocks.

### For operators (permission level 2)

Most of these act on the block you're **looking at**.

| Command | What it does |
|---|---|
| `/pd allow <player>` | Look at a World Anchor or World Core: add the player to that realm's access list |
| `/pd allow <player> man` | The same, and make them a manager |
| `/pd deny <player>` | Take the player off the access list |
| `/pd deny <player> man` | Take away only their manager status (they stay on the list) |
| `/pd owner <player>` | Look at a World Anchor or World Core: give the realm to that player (the old owner stays on as a manager; someone who already owns a realm can't be given a second). Look at a placed Pocket Anchor: record its room as theirs |
| `/pd disown <player>` | Take the player's realm away: everyone in it is sent out, its core is removed, its anchor unlinked, and the land is never used again. They can grow a new realm with a new seed |
| `/pd regenCore` | Rebuild the World Core of the realm you're standing in, or of the World Anchor you're looking at |
| `/pd regenRoom` | Rebuild the walls of the pocket room you're standing in, or of the Pocket Anchor you're looking at. Nothing inside is touched |
| `/pd regenAnchor <player>` | Put the player's World Anchor back, linked, where it last stood (whatever is there now is replaced) |
| `/pd test mineAnchor [seconds]` | From inside a pocket room: an invisible miner mines its anchor so you can see the warnings alone. **The room is destroyed at the end** |
| `/pd test wardTime [seconds]` | In a realm: measure how long the ward takes to draw on your computer |
| `/pd test veilTime [seconds]` | Measure how long the Veil's fog takes to draw on your computer |

A player can be named or given by UUID.

---

## How do I...?

**...get out of a pocket room?** Crouch and jump at the same time.

**...get out of a pocket room while riding a horse, pig or boat?** Right-click any wall of the room. Your mount comes with you.

**...pick my Pocket Anchor back up?** Crouch + right-click it with nothing in either hand.

**...place a Pocket Anchor without going in?** Hold it, crouch, and right-click the block where you want it.

**...let a friend into my pocket room?** Just show them the placed anchor: anyone can right-click it to enter.

**...keep my pocket room safe?** You can't, completely: that's the point. Hide the anchor well, or carry it with you. People inside a room whose anchor you carry come out next to you.

**...create a realm?** Place a World Anchor, then right-click it holding a World Seed.

**...let a friend into my realm?** Crouch + right-click your World Core, open the **Access** tab, and type their name and click **Add** (or click **Online** and pick them from the list).

**...make someone a manager of my World Core?** On the core's **Access** tab, click the crown next to their name so it fills with gold. Only the owner can do this. An operator can also run `/pd allow <player> man` while looking at your anchor or core.

**...take someone off my access list?** Click the red cross next to their name on the **Access** tab.

**...name my realm?** The **Manage** tab of the World Core's screen.

**...leave my realm?** Right-click the World Core.

**...set my spawn in my realm?** Sleep in a bed there. It works for the owner and anyone on the access list.

**...move my realm somewhere new?** The owner can **relocate** it from the **Manage** tab. Everything in the old realm is lost; the access list, managers and name are kept.

**...move my realm's entrance?** Your entrance is the World Anchor. Once the old one is gone, place a new anchor and use a new World Seed on it.

**...besiege someone's realm?** Wait until one of its people is inside, then hold a World Breacher (to open it) or an Anchor Breaker (to destroy its anchor), crouch + right-click their World Anchor, and feed it lapis.

**...defend against a siege?** Keep lapis in your World Core (it halves the siege's speed and burns while the siege runs), and break the siege block with a diamond pickaxe (250 seconds; it resets the siege).

**...open my realm to the public?** As the owner, place a World Breacher on your own anchor and let it finish. Break it to close the realm again.

**...see how a siege is going?** Watch the siege bar at the top of your screen, or crouch + right-click the siege block for its screen.

---

## Troubleshooting

**"There is no room here for the anchor to rest."** The anchor needs a free spot at your feet or right next to you. Step somewhere more open.

**"The landing stone is swept bare by every arrival..."** You're on the End's obsidian landing platform. Step off it, then place the anchor.

**My Pocket Anchor disappeared.** Someone may have taken it (silently) or mined it (which destroys the room). If the world removed it some other way, it dropped nearby as an item.

**I can't get out of my pocket room.** On foot, crouch and jump at the same time. Riding, right-click a wall instead. If you're stuck anyway, an operator can `/tp` you out; that is safe.

**"The anchor's wards hold firm. You shall not pass."** You're not on that realm's access list, and it isn't breached. Ask its owner or a manager to add you.

**"The realm does not know how you came here, and casts you out."** You arrived in a realm some way it doesn't recognise (a teleport command or another mod). Use the World Anchor.

**"The realm's wards do not know you, and cast you out."** You're not allowed in that realm, for example after respawning at a bed there once you'd been removed from its access list.

**"The anchor is empty. There is no realm to besiege."** That World Anchor has no realm linked to it.

**My siege block won't go on the anchor.** Crouch while you right-click. Check that the anchor is linked, that no siege block is already on it, and that someone who belongs to the realm is inside it right now.

**My World Seed won't link my new anchor.** Your old anchor still stands somewhere. A realm can only have one anchor; the old one must be gone first.

**I can't see the Veil's smoke, or it's slow on my computer.** Each player can turn it off or make it cheaper in `config/pocketdimensions-client.toml` (`veil_fog`, `veil_fog_steps`).

**My World Core or room walls got broken in creative mode.** An operator can run `/pd regenCore` or `/pd regenRoom`.

---

## For server owners

Timings, realm size, mob and structure rules, mining times and fuel limits are all configurable. See the [Configuration section of the README](README.md#configuration) for every setting.
