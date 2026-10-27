package dev.etran.bedwars1058.arenamanagement.arena

/** Where a map is in its life: loaded by BedWars1058, set up but off, or never set up. */
enum class ArenaState(val label: String, val color: String) {
    ENABLED("Enabled", "&a"),
    DISABLED("Disabled", "&c"),
    UNCONFIGURED("Not set up", "&7")
}

/**
 * @property unassigned true for a set-up arena that is still in the default group. Maps that were
 * never set up are NOT unassigned (they can not be grouped or enabled until set up).
 */
data class ArenaInfo(
    val name: String,
    val group: String,
    val state: ArenaState,
    val unassigned: Boolean
)

/** A running arena as seen by BedWars1058 (only enabled arenas exist here). */
data class LiveArena(
    val name: String,
    val displayName: String,
    val group: String,
    val status: String,
    val players: Int,
    val maxPlayers: Int
)

/** Filters for the manager list. Empty / null fields mean "don't filter on this". */
data class Filter(
    val letters: Set<Char> = emptySet(),
    val states: Set<ArenaState> = emptySet(),
    val group: String? = null,
    val unassignedOnly: Boolean = false
) {
    val isActive: Boolean
        get() = letters.isNotEmpty() || states.isNotEmpty() || group != null || unassignedOnly

    fun matches(arena: ArenaInfo): Boolean {
        if (letters.isNotEmpty() && letterKey(arena.name) !in letters) return false
        if (states.isNotEmpty() && arena.state !in states) return false
        if (unassignedOnly && !arena.unassigned) return false
        if (group != null && !arena.group.equals(group, ignoreCase = true)) return false
        return true
    }

    fun describe(): List<String> {
        if (!isActive) return listOf("&7No filters active")
        val lines = ArrayList<String>()
        if (letters.isNotEmpty()) lines += "&7Letters: &f" + letters.sorted().joinToString(" ")
        if (states.isNotEmpty()) lines += "&7State: &f" + states.joinToString(", ") { it.label }
        if (unassignedOnly) lines += "&7Group: &fUnassigned only"
        if (group != null) lines += "&7Group: &f$group"
        return lines
    }

    companion object {
        /** A-Z for names starting with a letter, '#' for everything else. */
        fun letterKey(name: String): Char {
            val c = name.firstOrNull()?.uppercaseChar() ?: return '#'
            return if (c in 'A'..'Z') c else '#'
        }
    }
}

/** What a group picker returns. */
sealed class GroupChoice {
    object All : GroupChoice()
    object Unassigned : GroupChoice()
    data class Named(val name: String) : GroupChoice()
}
