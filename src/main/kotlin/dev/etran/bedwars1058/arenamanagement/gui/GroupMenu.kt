package dev.etran.bedwars1058.arenamanagement.gui

import dev.etran.bedwars1058.arenamanagement.ArenaManagementPlugin
import dev.etran.bedwars1058.arenamanagement.arena.ArenaState
import dev.etran.bedwars1058.arenamanagement.arena.Filter
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType

/**
 * `/bw arenaGroup` - every group, with create / list / remove / assign.
 *
 *   Left-click a group         move the current selection into it
 *   Right-click a group        browse its maps
 *   Shift-right-click a group  remove the group
 */
class GroupMenu(plugin: ArenaManagementPlugin, player: Player) : Menu(plugin, player) {
    override val rows = 6
    private val session = plugin.sessions.get(player)
    private val pageSize = 45
    private var page = 0

    override fun title() = "&8Arena Groups"

    override fun render() {
        val snapshot = plugin.repository.snapshot(force = true)
        val groups = snapshot.groups
        val pages = maxOf(1, (groups.size + pageSize - 1) / pageSize)
        page = page.coerceIn(0, pages - 1)
        val selected = session.resolve(snapshot)
        val movable = selected.filter { it.state != ArenaState.UNCONFIGURED }

        // ---- top row
        if (page > 0) set(0, icon("prev", "&aPrevious page")) { page--; refresh() }
        set(1, icon("page", "&7Page &f${page + 1}&7/&f$pages", listOf("&7Groups: &f${groups.size}")))
        if (page < pages - 1) set(2, icon("next", "&aNext page")) { page++; refresh() }

        set(3, icon("action-create", "&aCreate group", listOf("&7/bw arenaGroup create <name>", "", "&eClick, then type a name")), {
            plugin.prompts.ask(player, "&eType the name of the new group in chat:") { text ->
                plugin.ops.createGroup(player, text)
                plugin.later(10L) { if (player.isOnline) GroupMenu(plugin, player).open() }
            }
        })
        set(4, icon("commands", "&bList groups", listOf("&7/bw arenaGroup list", "", "&eClick to print in chat")), {
            player.closeInventory()
            plugin.ops.sendGroupList(player)
        })
        set(
            5,
            icon(
                "filter", "&eUnassigned maps",
                listOf("&7Set-up maps still in the default group.", "&7Maps: &f${snapshot.unassignedCount}", "", "&eClick to browse them", "&7(then select them and move them)")
            ),
            {
                session.filter = Filter(unassignedOnly = true)
                session.page = 0
                go(ArenaBrowserMenu(plugin, player))
            }
        )
        set(6, icon("bulk", "&bSelection: &f${selected.size}", listOf("&7Movable (set up): &f${movable.size}", "", "&eClick for selection tools")), {
            go(BulkMenu(plugin, player))
        })
        set(7, icon("arena-list", "&bAll maps", listOf("&7Open the map manager.", "", "&eClick to open")), {
            session.filter = Filter()
            session.page = 0
            go(ArenaBrowserMenu(plugin, player))
        })
        set(8, icon("close", "&cClose"), { player.closeInventory() })

        // ---- groups
        groups.drop(page * pageSize).take(pageSize).forEachIndexed { index, group ->
            val members = snapshot.inGroup(group)
            val isDefault = group.equals(plugin.settings.defaultGroup, ignoreCase = true)
            val lore = ArrayList<String>()
            lore += "&7Maps: &f${members.size}"
            lore += "&7Enabled: &a${members.count { it.state == ArenaState.ENABLED }}&7, disabled: &c${members.count { it.state == ArenaState.DISABLED }}"
            if (isDefault) lore += "&8(default group - maps here count as unassigned)"
            lore += ""
            lore += if (movable.isNotEmpty()) "&eLeft-click &7move &f${movable.size}&7 selected map(s) here" else "&8Left-click: select maps first to move them here"
            lore += "&eRight-click &7browse this group's maps"
            lore += if (isDefault) "&8Can not be removed" else "&eShift-right-click &7remove group"
            set(9 + index, icon("group", "&a$group", lore)) { click -> onGroupClick(group, click, movable.map { it.name }, isDefault) }
        }
    }

    private fun onGroupClick(group: String, click: ClickType, movableNames: List<String>, isDefault: Boolean) {
        when (click) {
            ClickType.LEFT -> {
                if (movableNames.isEmpty()) {
                    msg("&cSelect some set-up maps first (shift-click them in the map manager, or use the selection tools).")
                } else {
                    plugin.ops.setGroup(player, movableNames, group)
                    player.closeInventory()
                }
            }
            ClickType.RIGHT -> {
                session.filter = Filter(group = group)
                session.page = 0
                go(ArenaBrowserMenu(plugin, player))
            }
            ClickType.SHIFT_RIGHT -> {
                if (isDefault) {
                    msg("&cThe default group can not be removed.")
                    return
                }
                go(
                    ConfirmMenu(
                        plugin, player, "&cRemove group $group?", listOf("&7Maps in it are left as they are.", "&7(Behaviour follows BedWars1058's remove command.)"),
                        onConfirm = {
                            plugin.ops.removeGroup(player, group)
                            plugin.later(10L) { if (player.isOnline) GroupMenu(plugin, player).open() }
                        },
                        onCancel = { go(GroupMenu(plugin, player)) }
                    )
                )
            }
            else -> {}
        }
    }
}
