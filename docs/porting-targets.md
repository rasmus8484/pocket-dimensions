# Porting targets after 0.1.0

Pocket Dimensions 0.1.0 is built for **Forge 61.x on Minecraft 1.21.11** (the version our own server runs). After that release, the plan is to port to where modded players are.

## Targets

| Loader | 1.20.1 | 1.21.1 | 1.21.11 |
|---|:---:|:---:|:---:|
| Forge | yes | | yes (0.1.0) |
| NeoForge | yes | yes | yes |
| Fabric | yes | yes | yes |

Seven builds in all, counting the current one.

## Why these: the numbers

Counted from the Modrinth API on 2026-10-09. A project that supports several loaders counts once under each. "NeoForge" on 1.20.1 is NeoForge's first release, a fork of Forge 1.20.1 that still loads most Forge mods.

**Mods listing the version and loader**

| Version | Forge | NeoForge | Fabric |
|---|---:|---:|---:|
| 1.18.2 | 5,973 | 790 | 5,389 |
| 1.19.2 | 8,088 | 1,022 | 7,472 |
| 1.19.4 | 5,503 | 925 | 6,260 |
| **1.20.1** | **25,134** | 5,069 | 17,636 |
| 1.20.4 | 5,973 | 4,276 | 10,060 |
| 1.20.6 | 4,556 | 3,743 | 7,932 |
| **1.21.1** | 5,380 | **21,215** | 19,456 |
| 1.21.4 | 4,307 | 9,382 | 13,620 |
| 1.21.5 | 4,264 | 7,640 | 12,646 |
| 1.21.8 | 4,139 | 9,098 | 14,189 |
| 1.21.10 | 4,171 | 7,232 | 13,530 |
| **1.21.11** | 4,383 | 8,061 | 17,788 |
| 26.1 | 3,290 | 6,138 | 10,698 |
| 26.1.2 | 3,321 | 7,883 | 12,923 |
| 26.2 | 3,311 | 6,593 | 12,726 |
| 26.3 | 1,993 | 3,857 | 6,918 |

**Modpacks listing the version and loader** (closer to where players actually play)

| Version | Forge | NeoForge | Fabric |
|---|---:|---:|---:|
| 1.18.2 | 169 | 0 | 269 |
| 1.19.2 | 364 | 1 | 629 |
| **1.20.1** | **3,029** | 263 | 4,299 |
| 1.20.4 | 13 | 41 | 1,201 |
| **1.21.1** | 25 | **2,146** | 3,051 |
| 1.21.4 | 13 | 75 | 1,828 |
| 1.21.8 | 7 | 66 | 1,508 |
| 1.21.10 | 10 | 46 | 1,571 |
| **1.21.11** | 19 | 82 | 2,853 |
| 26.1.2 | 7 | 66 | 1,325 |
| 26.2 | 3 | 54 | 1,456 |
| 26.3 | 2 | 11 | 427 |

What this says:

- **Forge 1.20.1** is still the biggest Forge target by far (25k mods, 3k packs).
- **NeoForge 1.21.1** is where modern content modpacks went (21k mods, 2.1k packs; All the Mods 10 is the best-known example).
- **Fabric** is strong on every version and leads on the newest ones.
- After 1.20.1, Forge's community largely moved to NeoForge: Forge on 1.21.11 has only 19 modpacks on Modrinth.
- 1.18.2 and 1.19.2 have faded; not worth targeting.
- After 1.21.11 Minecraft moved to year-based numbers (26.1, 26.2, 26.3); 26.x is the next line to watch.

These are Modrinth numbers only. CurseForge is bigger and leans further towards Forge 1.20.1, but it has no open API to count with. Modpack Index, which combines CurseForge, Modrinth and FTB, puts 1.20.1 first (47.6k mods), then 1.12.2 (26.8k), then 1.21.1 (18.6k).

Sources: [Modrinth API](https://api.modrinth.com/v2/search), [Modpack Index stats](https://www.modpackindex.com/stats), [Forge vs Fabric vs NeoForge 2026](https://tech-insider.org/forge-vs-fabric-vs-neoforge-2026/), [NeoForged 2024 retrospective](https://neoforged.net/news/2024-retrospection/).

## What each port involves

The mod is written against 1.21.11. Two Minecraft changes since 1.21.1 matter most when going back:

- **Block entity rendering** was rebuilt around 1.21.4 (render states, `submit` instead of `render`, `SubmitNodeCollector` instead of `MultiBufferSource`). Every renderer is on the new system: the World Anchor, the World Core, the Tumbling Cube, the room walls, the siege effects.
- **Block entity saving** moved to `ValueInput` / `ValueOutput` in 1.21.6 (before: `CompoundTag` plus `HolderLookup.Provider`). Every block entity's save and load code uses the new form.

So, roughly from cheapest to hardest:

| Port | What changes |
|---|---|
| NeoForge 1.21.11 | Same Minecraft: only the loader layer (event bus, networking, registration, config, menus). The cheapest step. |
| Fabric 1.21.11 | Same Minecraft, but Fabric has no Forge-style events: hooks become Fabric API callbacks or mixins; config needs a library or our own file. |
| NeoForge 1.21.1 | The loader layer, plus the renderers rewritten to the old system and the save code to `CompoundTag`. |
| Fabric 1.21.1 | As NeoForge 1.21.1, with the Fabric hooks on top. |
| Forge / NeoForge 1.20.1 | As 1.21.1, plus the API changes between 1.20.1 and 1.21.1 (item components replaced item NBT in 1.20.5, registry and resource-location changes, networking). The biggest job. |
| Fabric 1.20.1 | The same, on Fabric. |

To keep seven builds manageable, split the code into a shared core (rules, managers, the pure tested classes, most gameplay) and thin per-loader, per-version layers. The pure classes (`RealmRules`, `TravelRules`, `SiegeBarArt`, `RoomShell`, `SafeSpot` ...) already have no Minecraft or loader dependencies and carry over as they are. Multi-loader templates (Architectury, or the MultiLoader-Template) are built for exactly this.
