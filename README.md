# BedWars1058-ArenaManagement

Menu-driven arena management for [BedWars1058](https://www.spigotmc.org/resources/bedwars1058.50942/).
Replaces the command-dropdown workflow with chest menus, adds bulk operations, and replaces the
`/bw gui` slot configuration with a paged, group-aware join menu.

- Kotlin 2.2.21, Gradle (Kotlin DSL), JVM 8 bytecode
- Group: `dev.etran` - package `dev.etran.bedwars1058.arenamanagement`
- One jar for Minecraft 1.8 through the newest versions (see "Compatibility")

## Build

```
./gradlew build
```

The jar is `build/libs/BedWars1058-ArenaManagement-<version>.jar` (Kotlin's stdlib is shaded and
relocated inside it, because 1.8 - 1.15 servers cannot download libraries). Drop it in `plugins/`.
Requires BedWars1058 (hard dependency).

## What it overrides

BedWars1058 is not modified. The plugin cancels these commands before BedWars1058 sees them:

| Command | Result |
|---|---|
| `/bw setupArena` | Map manager menu |
| `/bw arenaGroup` | Group manager menu (`/bw arenaGroup list` prints our grouped list) |
| `/bw arenaList` | Grouped, colour-coded list in chat |
| `/bw gui [group]` | The join menu, titled with the group |

Any of them **with arguments** (e.g. `/bw setupArena MyMap`, `/bw arenaGroup create X`) still runs
plain BedWars1058, so scripts and muscle memory keep working. Each override can be switched off in
`config.yml` (`override:`). Map manager / arena list need `bw.setup`; the group manager needs `bw.groups` (both configurable). Aliases `bw`, `bedwars` and `bedwars1058` are all handled.

## Map manager (`/bw setupArena`)

Every map, alphabetical (natural order: `map2` before `map10`), 45 per page.

- **Left-click** - set the map up. If it's enabled it is disabled first, then BedWars1058's setup starts.
- **Right-click** - options for that one map: set up, change group (`arenaGroup set`), clone
  (`cloneArena`), enable, disable, delete (`delArena`, with confirmation), add to selection.
- **Shift-click** - add/remove from the selection.

Top row: previous / page / next, **Commands** (prints the command list), **Arena list**,
**Filters**, **Select this page**, **Bulk actions**, **Groups**.

Item colours: green = enabled, red = disabled, grey = a world folder that was never set up.

### Bulk operations

The selection follows you between menus and pages. Build it by any mix of:

- shift-click individual maps
- *Select this page* (one click toggles the whole page; do it on several pages)
- *Select all matching* - every map matching the current filters, across all pages
- *Select every map*
- *Select unassigned* - all set-up maps still in the default group
- Filters: first letter (any combination, A-Z and `#`), state (enabled / disabled / not set up),
  group (any group, or "unassigned only")

Then, from the Bulk menu: **move to group** (existing group or create a new one on the spot),
**enable**, **disable**, **clone** (type a suffix, `Map` becomes `Map-copy`), **delete** (confirmation
lists the maps). Maps an action can't apply to (e.g. enabling an already-enabled map) are skipped
and reported.

Bulk jobs run a few commands at a time (`bulk:` in config) so loading many worlds doesn't freeze the
server, and when a job finishes the plugin re-reads the arenas and reports `N/M confirmed`, so a
failed BedWars1058 command is visible instead of silently assumed to have worked.

## Group manager (`/bw arenaGroup`)

- Create group (type the name in chat), list groups, browse unassigned maps
- Left-click a group: move the current selection into it
- Right-click a group: browse its maps
- Shift-right-click a group: remove it (confirmation; the default group can't be removed)

## Join menu (`/bw gui [group]`)

Configured entirely in this plugin's `config.yml` under `gui:` - BedWars1058's own `arena-gui`
slot settings are no longer used.

- Default layout matches BedWars1058: a 7-wide block inside a 1-slot empty border (28 maps on a 9x6).
- The chest **grows** from 3 rows to 6 as maps are added (`rows: AUTO`; set `rows: 6` to always be 9x6).
- Past one full chest it **pages**, with Previous / Page / Next in the bottom row.
- The **title is the arena group** (`gui.title`, `{group}`), or `title-all` for plain `/bw gui`.
- Set `gui.slots: [10, 11, ...]` to choose exact slots, like BedWars1058's `use-slots`.
- Same capabilities as the BedWars1058 menu: per-state item + enchant glow (waiting / starting /
  playing), `show-playing`, an item for unused slots, configurable name/lore with
  `the same placeholders as BedWars1058 (`{name} {map_name} {on} {max} {status} {group}`)`, live-refreshing player counts.
- Left-click joins, right-click spectates (like BedWars1058).

## Compatibility notes (1.8 -> latest)

Compiled against the 1.8.8 API; version differences are handled at runtime:

- Items are config "specs" with fallbacks: `LIME_CONCRETE|STAINED_CLAY:5` is lime concrete on 1.13+
  and green stained clay on 1.8-1.12. Edit any `items:` entry the same way.
- Menus are matched by `InventoryHolder`, never by title, and never touch `InventoryView`
  (which changed from class to interface in 1.21).
- Inventory titles are trimmed to 32 characters on 1.8-1.13, where longer titles throw.
- No sounds, no direct enchantment/material constants.
- New-style version numbers (26.x) are treated as "newer than everything".

## Behaviour taken from the BedWars1058 source

These were checked against the BedWars1058 source (not just assumed):

- **Everything runs as the player.** All its management commands reject console, so bulk jobs run as
  the player who clicked (and stop if they log out).
- **`arenaGroup set <arena> <group>`** is the real assign command. The group must already exist in
  `arenaGroups` and is matched case-sensitively, so the plugin creates missing groups first and
  uses each group's exact spelling.
- **The default group can't be assigned by command**, so it is not offered as a target. A map whose
  yml names a group that isn't in `arenaGroups` is treated as default by BedWars1058 (and here).
- **`delArena` needs to be typed twice within 2 seconds.** The plugin shows its own confirmation
  screen, then sends it twice (disabling the arena first).
- **`setupArena` rejects capital letters.** Maps with capitals show a "rename the folder" message,
  and clone names must be lowercase.
- **`/bw gui` with no argument means group `default` = every arena.** `a+b` selects several groups.
  Restarting arenas are hidden, `{group}` lore lines are hidden for the default group, left-click joins
  waiting/starting arenas, right-click spectates running ones, and the optional skipped-slot item
  runs `bw join random` - all as in BedWars1058's own menu.
- `/bw gui` is left to BedWars1058 while you're inside an arena (it ignores it there too).

## BedWars1058-MapLoader compatibility

MapLoader lists "parked" arenas in BedWars1058 with no world until someone joins, and wakes them through
command and join hooks. So this plugin:

- sends every BedWars1058 command as if the player typed it (fires `PlayerCommandPreprocessEvent` first),
  which is what lets MapLoader disable or wake parked arenas instead of BedWars1058 throwing a
  NullPointerException on an arena with no world;
- joins from the `/bw gui` menu with `bw join <arena>`, not by calling the API directly (MapLoader cancels
  the direct join and joins the player itself, which used to print a bogus "can't join" message);
- treats parked arenas as normal enabled arenas (shown as "parked"), and also lists maps that exist only in
  the MapLoader library (`storage.path`, folders or zips) as "not set up" when they have no arena config.

Without MapLoader, an arena with no loaded world is reported instead of being touched.

## Developer note
This plugin is coded with AI assistance and architected completely by me. Regardless, use at your own risk.

## License
This plugin is under the MIT License, check the [License](LICENSE) file for more details.