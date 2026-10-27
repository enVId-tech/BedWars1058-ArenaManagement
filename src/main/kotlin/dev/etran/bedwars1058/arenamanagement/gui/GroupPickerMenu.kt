package dev.etran.bedwars1058.arenamanagement.gui

import dev.etran.bedwars1058.arenamanagement.ArenaManagementPlugin
import dev.etran.bedwars1058.arenamanagement.arena.GroupChoice
import org.bukkit.entity.Player

/**
 * Reusable "pick a group" screen: used for assigning maps to a group (can create a new group on
 * the spot) and for the group filter (can pick "all" or "unassigned").
 */
class GroupPickerMenu(
    plugin: ArenaManagementPlugin,
    player: Player,
    private val heading: String,
    private val includeAll: Boolean,
    private val includeUnassigned: Boolean,
    private val allowCreate: Boolean,
    private val onPick: (GroupChoice) -> Unit,
    private val onBack: () -> Unit
) : Menu(plugin, player) {
    override val rows = 6
    private var page = 0
    private val pageSize = 45

    override fun title() = "&8$heading"

    private class Entry(val name: String, val lore: List<String>, val choice: GroupChoice)

    private fun entries(): List<Entry> {
        val snapshot = plugin.repository.snapshot()
        val list = ArrayList<Entry>()
        if (includeAll) list += Entry("&fAll groups", listOf("&7No group filtering", "", "&eClick to choose"), GroupChoice.All)
        if (includeUnassigned) {
            list += Entry(
                "&eUnassigned maps",
                listOf("&7Set-up maps still in the default group", "&7Maps: &f${snapshot.unassignedCount}", "", "&eClick to choose"),
                GroupChoice.Unassigned
            )
        }
        for (group in snapshot.groups) {
            val members = snapshot.inGroup(group)
            list += Entry(
                "&a$group",
                listOf("&7Maps: &f${members.size}", "&7Enabled: &f${members.count { it.state.name == "ENABLED" }}", "", "&eClick to choose"),
                GroupChoice.Named(group)
            )
        }
        return list
    }

    override fun render() {
        val all = entries()
        val pages = maxOf(1, (all.size + pageSize - 1) / pageSize)
        page = page.coerceIn(0, pages - 1)
        all.drop(page * pageSize).take(pageSize).forEachIndexed { index, entry ->
            set(index, icon("group", entry.name, entry.lore), { onPick(entry.choice) })
        }

        if (page > 0) set(45, icon("prev", "&aPrevious page")) { page--; refresh() }
        if (page < pages - 1) set(53, icon("next", "&aNext page")) { page++; refresh() }
        set(49, icon("page", "&7Page &f${page + 1}&7/&f$pages"))
        set(48, icon("back", "&cBack"), { onBack() })

        if (allowCreate) {
            set(50, icon("action-create", "&aCreate a new group", listOf("&7Type a name in chat,", "&7then the maps move there.")), {
                plugin.prompts.ask(player, "&eType the name of the new group in chat:") { text ->
                    if (!plugin.ops.isValidName(text)) {
                        msg("&cGroup names may only contain letters, digits, _ and - (max 32).")
                    } else {
                        onPick(GroupChoice.Named(text))
                    }
                }
            })
        }
    }
}
