package dev.etran.bedwars1058.arenamanagement.gui

import dev.etran.bedwars1058.arenamanagement.ArenaManagementPlugin
import dev.etran.bedwars1058.arenamanagement.arena.ArenaState
import dev.etran.bedwars1058.arenamanagement.arena.Filter
import dev.etran.bedwars1058.arenamanagement.arena.GroupChoice
import org.bukkit.entity.Player

/** Filters narrow what the manager list shows (and what "select all matching" selects). */
class FilterMenu(plugin: ArenaManagementPlugin, player: Player) : Menu(plugin, player) {
    override val rows = 4
    private val session = plugin.sessions.get(player)

    override fun title() = "&8Filters"

    private fun toggleState(state: ArenaState) {
        val current = session.filter
        session.filter = current.copy(states = if (state in current.states) current.states - state else current.states + state)
        refresh()
    }

    override fun render() {
        val filter = session.filter
        val snapshot = plugin.repository.snapshot()
        val matching = snapshot.arenas.count { filter.matches(it) }

        set(4, icon("info", "&fActive filters", filter.describe() + listOf("", "&7Matching maps: &f$matching&7/&f${snapshot.arenas.size}")))

        set(
            10,
            icon(
                "letter", "&bFirst letter",
                listOf(if (filter.letters.isEmpty()) "&7Any letter" else "&7Letters: &f" + filter.letters.sorted().joinToString(" "), "", "&eClick to pick letters"),
                glow = filter.letters.isNotEmpty()
            ),
            { go(LetterMenu(plugin, player)) }
        )

        for ((slot, state) in listOf(12 to ArenaState.ENABLED, 13 to ArenaState.DISABLED, 14 to ArenaState.UNCONFIGURED)) {
            val on = state in filter.states
            val key = when (state) {
                ArenaState.ENABLED -> "arena-enabled"
                ArenaState.DISABLED -> "arena-disabled"
                ArenaState.UNCONFIGURED -> "arena-unconfigured"
            }
            set(
                slot,
                icon(key, state.color + state.label, listOf("&7Maps: &f${snapshot.count(state)}", if (on) "&a✔ Filtering on this" else "&8Not filtering", "", "&eClick to toggle"), glow = on),
                { toggleState(state) }
            )
        }

        val groupText = when {
            filter.unassignedOnly -> "Unassigned only"
            filter.group != null -> filter.group
            else -> "Any group"
        }
        set(16, icon("groups", "&bGroup", listOf("&7Showing: &f$groupText", "", "&eClick to choose"), glow = filter.unassignedOnly || filter.group != null), {
            go(
                GroupPickerMenu(
                    plugin, player, "Filter by group", includeAll = true, includeUnassigned = true, allowCreate = false,
                    onPick = { choice ->
                        session.filter = when (choice) {
                            GroupChoice.All -> filter.copy(group = null, unassignedOnly = false)
                            GroupChoice.Unassigned -> filter.copy(group = null, unassignedOnly = true)
                            is GroupChoice.Named -> filter.copy(group = choice.name, unassignedOnly = false)
                        }
                        session.page = 0
                        go(FilterMenu(plugin, player))
                    },
                    onBack = { go(FilterMenu(plugin, player)) }
                )
            )
        })

        set(21, icon("cancel", "&cReset all filters"), {
            session.filter = Filter()
            session.page = 0
            refresh()
        })
        set(23, icon("confirm", "&aShow results", listOf("&7Back to the map list.")), {
            session.page = 0
            go(ArenaBrowserMenu(plugin, player))
        })
    }
}

/** Pick any number of letters; maps starting with a picked letter are kept. '#' = digits/other. */
class LetterMenu(plugin: ArenaManagementPlugin, player: Player) : Menu(plugin, player) {
    override val rows = 4
    private val session = plugin.sessions.get(player)

    override fun title() = "&8Filter by letter"

    override fun render() {
        val snapshot = plugin.repository.snapshot()
        val counts = snapshot.arenas.groupingBy { Filter.letterKey(it.name) }.eachCount()
        val letters = ('A'..'Z').toList() + '#'
        letters.forEachIndexed { index, letter ->
            val on = letter in session.filter.letters
            val count = counts[letter] ?: 0
            set(
                index,
                icon("letter", "&e$letter", listOf("&7Maps: &f$count", if (on) "&a✔ Selected" else "&8Not selected", "", "&eClick to toggle"), glow = on),
                {
                    val current = session.filter
                    session.filter = current.copy(letters = if (letter in current.letters) current.letters - letter else current.letters + letter)
                    session.page = 0
                    refresh()
                }
            )
        }
        set(30, icon("cancel", "&cClear letters"), {
            session.filter = session.filter.copy(letters = emptySet())
            refresh()
        })
        set(32, icon("back", "&aDone"), { go(FilterMenu(plugin, player)) })
    }
}
