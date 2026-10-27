package dev.etran.bedwars1058.arenamanagement.session

import dev.etran.bedwars1058.arenamanagement.arena.ArenaInfo
import dev.etran.bedwars1058.arenamanagement.arena.ArenaRepository
import dev.etran.bedwars1058.arenamanagement.arena.Filter
import org.bukkit.entity.Player
import java.util.UUID

/** Per-player menu state. The selection and filters survive moving between menus. */
class Session {
    private val selected = LinkedHashSet<String>()
    var filter = Filter()
    var page = 0

    val selectedCount: Int get() = selected.size

    fun isSelected(name: String): Boolean = name.lowercase() in selected

    fun toggle(name: String) {
        val key = name.lowercase()
        if (!selected.remove(key)) selected.add(key)
    }

    fun select(arenas: Collection<ArenaInfo>) {
        arenas.forEach { selected.add(it.name.lowercase()) }
    }

    fun deselect(arenas: Collection<ArenaInfo>) {
        arenas.forEach { selected.remove(it.name.lowercase()) }
    }

    fun clear() = selected.clear()

    /** Selected arenas that still exist (maps deleted since being selected are dropped). */
    fun resolve(snapshot: ArenaRepository.Snapshot): List<ArenaInfo> {
        selected.retainAll { snapshot.find(it) != null }
        return snapshot.arenas.filter { it.name.lowercase() in selected }
    }
}

class SessionManager {
    private val sessions = HashMap<UUID, Session>()

    fun get(player: Player): Session = sessions.getOrPut(player.uniqueId) { Session() }

    fun drop(player: Player) {
        sessions.remove(player.uniqueId)
    }
}
