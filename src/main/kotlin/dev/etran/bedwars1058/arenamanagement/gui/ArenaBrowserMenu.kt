package dev.etran.bedwars1058.arenamanagement.gui

import dev.etran.bedwars1058.arenamanagement.ArenaManagementPlugin
import dev.etran.bedwars1058.arenamanagement.arena.ArenaInfo
import dev.etran.bedwars1058.arenamanagement.arena.ArenaState
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack

/**
 * `/bw setupArena` - every map, alphabetical, 45 per page.
 *
 *   Left-click   set the map up (turns it off first if it is on)
 *   Right-click  options for just this map
 *   Shift-click  add / remove the map from the selection
 *
 * Top row: back, page, next, commands, arena list, filters, select-this-page, bulk actions, groups.
 */
class ArenaBrowserMenu(plugin: ArenaManagementPlugin, player: Player) : Menu(plugin, player) {
    override val rows = 6
    private val session = plugin.sessions.get(player)
    private val pageSize = 45

    private fun visible(): List<ArenaInfo> = plugin.repository.snapshot().arenas.filter { session.filter.matches(it) }
    private fun pageCount(total: Int) = maxOf(1, (total + pageSize - 1) / pageSize)

    override fun title(): String {
        val pages = pageCount(visible().size)
        session.page = session.page.coerceIn(0, pages - 1)
        return "&8Arena Manager &7(${session.page + 1}/$pages)"
    }

    override fun render() {
        val snapshot = plugin.repository.snapshot()
        val list = visible()
        val pages = pageCount(list.size)
        session.page = session.page.coerceIn(0, pages - 1)
        val pageItems = list.drop(session.page * pageSize).take(pageSize)

        // ---- top row
        if (session.page > 0) {
            set(0, icon("prev", "&aPrevious page", listOf("&7Page ${session.page} of $pages"))) {
                session.page--
                go(ArenaBrowserMenu(plugin, player))
            }
        }
        set(1, icon("page", "&7Page &f${session.page + 1}&7/&f$pages", listOf("&7Showing &f${list.size}&7 of &f${snapshot.arenas.size}&7 maps")))
        if (session.page < pages - 1) {
            set(2, icon("next", "&aNext page", listOf("&7Page ${session.page + 2} of $pages"))) {
                session.page++
                go(ArenaBrowserMenu(plugin, player))
            }
        }
        set(3, icon("commands", "&bCommands", listOf("&7/bw setupArena", "&7/bw arenaGroup", "&7/bw arenaList", "&7/bw gui", "", "&eClick to print in chat")), {
            player.closeInventory()
            plugin.ops.sendCommandHelp(player)
        })
        set(
            4,
            icon(
                "arena-list", "&bArena list",
                listOf(
                    "&aEnabled: &f${snapshot.count(ArenaState.ENABLED)}",
                    "&cDisabled: &f${snapshot.count(ArenaState.DISABLED)}",
                    "&7Not set up: &f${snapshot.count(ArenaState.UNCONFIGURED)}",
                    "&eUnassigned: &f${snapshot.unassignedCount}",
                    "",
                    "&eClick to print in chat"
                )
            ),
            {
                player.closeInventory()
                plugin.ops.sendArenaList(player)
            }
        )
        set(5, icon("filter", "&bFilters", session.filter.describe() + listOf("", "&eClick to change filters"), glow = session.filter.isActive), {
            go(FilterMenu(plugin, player))
        })

        val pageAllSelected = pageItems.isNotEmpty() && pageItems.all { session.isSelected(it.name) }
        set(
            6,
            icon(
                "select-page", if (pageAllSelected) "&eDeselect this page" else "&aSelect this page",
                listOf("&7Toggles all &f${pageItems.size}&7 maps on this page.", "&7Selection carries across pages.", "", "&7Selected overall: &f${session.selectedCount}")
            ),
            {
                if (pageAllSelected) session.deselect(pageItems) else session.select(pageItems)
                refresh()
            }
        )
        set(7, icon("bulk", "&bSelection & bulk actions", listOf("&7Selected: &f${session.selectedCount}", "", "&eClick to open"), glow = session.selectedCount > 0), {
            go(BulkMenu(plugin, player))
        })
        set(8, icon("groups", "&bGroups", listOf("&7Create, remove, list and assign", "&7arena groups.", "", "&eClick to open")), {
            go(GroupMenu(plugin, player))
        })

        // ---- arenas
        pageItems.forEachIndexed { index, arena ->
            set(9 + index, arenaItem(arena)) { click -> onArenaClick(arena, click) }
        }
    }

    private fun arenaItem(arena: ArenaInfo): ItemStack {
        val selected = session.isSelected(arena.name)
        val key = when (arena.state) {
            ArenaState.ENABLED -> "arena-enabled"
            ArenaState.DISABLED -> "arena-disabled"
            ArenaState.UNCONFIGURED -> "arena-unconfigured"
        }
        val group = when {
            arena.state == ArenaState.UNCONFIGURED -> "&7-"
            arena.unassigned -> "&e${arena.group} &c(unassigned)"
            else -> "&f${arena.group}"
        }
        val lore = listOf(
            "&7State: ${arena.state.color}${arena.state.label}",
            "&7Group: $group",
            if (selected) "&b✔ Selected" else "&8Not selected",
            "",
            "&eLeft-click &7set up" + if (arena.state == ArenaState.ENABLED) " &8(disables it first)" else "",
            "&eRight-click &7options",
            "&eShift-click &7select / deselect"
        )
        return icon(key, (if (selected) "&b" else arena.state.color) + arena.name, lore, glow = selected)
    }

    private fun onArenaClick(arena: ArenaInfo, click: ClickType) {
        when {
            click == ClickType.LEFT -> plugin.ops.setup(player, arena.name)
            click == ClickType.RIGHT -> go(ArenaOptionsMenu(plugin, player, arena.name))
            isShift(click) || click == ClickType.MIDDLE -> {
                session.toggle(arena.name)
                refresh()
            }
        }
    }
}
