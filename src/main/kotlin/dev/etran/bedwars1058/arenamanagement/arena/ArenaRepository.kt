package dev.etran.bedwars1058.arenamanagement.arena

import dev.etran.bedwars1058.arenamanagement.ArenaManagementPlugin
import dev.etran.bedwars1058.arenamanagement.util.NaturalOrder
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.TreeSet

/**
 * Builds the picture of every map on the server by combining:
 *  - BedWars1058's loaded arenas (= enabled),
 *  - its `Arenas/<name>.yml` files (= set up; carries the `group`),
 *  - world folders with a level.dat that have no arena file (= not set up yet).
 */
class ArenaRepository(private val plugin: ArenaManagementPlugin) {

    class Snapshot(val arenas: List<ArenaInfo>, val groups: List<String>) {
        private val byName = arenas.associateBy { it.name.lowercase() }

        fun find(name: String): ArenaInfo? = byName[name.lowercase()]
        fun hasGroup(name: String): Boolean = groups.any { it.equals(name, ignoreCase = true) }
        fun inGroup(group: String): List<ArenaInfo> = arenas.filter { it.group.equals(group, ignoreCase = true) && it.state != ArenaState.UNCONFIGURED }
        fun count(state: ArenaState): Int = arenas.count { it.state == state }
        val unassignedCount: Int get() = arenas.count { it.unassigned }
    }

    private var cached: Snapshot? = null
    private var cachedAt = 0L

    fun snapshot(force: Boolean = false): Snapshot {
        val now = System.currentTimeMillis()
        val current = cached
        if (!force && current != null && now - cachedAt < plugin.settings.cacheMillis) return current
        val fresh = build()
        cached = fresh
        cachedAt = now
        return fresh
    }

    fun invalidate() {
        cached = null
    }

    private fun build(): Snapshot {
        val settings = plugin.settings
        val defaultGroup = settings.defaultGroup
        val live = plugin.hook.liveArenas().associateBy { it.name.lowercase() }
        val result = LinkedHashMap<String, ArenaInfo>()
        val groups = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
        groups.add(defaultGroup)

        val bwFolder = plugin.hook.bedWarsPlugin?.dataFolder

        // 1) set-up arenas: Arenas/<name>.yml
        if (bwFolder != null) {
            File(bwFolder, "Arenas").listFiles { f -> f.isFile && f.name.endsWith(".yml", ignoreCase = true) }?.forEach { file ->
                val name = file.nameWithoutExtension
                val liveArena = live[name.lowercase()]
                val group = liveArena?.group
                    ?: runCatching { YamlConfiguration.loadConfiguration(file).getString("group") }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: defaultGroup
                val state = if (liveArena != null) ArenaState.ENABLED else ArenaState.DISABLED
                result[name.lowercase()] = ArenaInfo(name, group, state, group.equals(defaultGroup, ignoreCase = true))
                groups.add(group)
            }
        }

        // 2) loaded arenas whose file we could not see (different folder layout, etc.)
        for ((key, arena) in live) {
            if (key in result) continue
            result[key] = ArenaInfo(arena.name, arena.group, ArenaState.ENABLED, arena.group.equals(defaultGroup, ignoreCase = true))
            groups.add(arena.group)
        }

        // 3) world folders that were never set up
        val excluded = settings.excludedWorlds
        Bukkit.getWorldContainer().listFiles { f -> f.isDirectory && File(f, "level.dat").isFile }?.forEach { dir ->
            val key = dir.name.lowercase()
            if (key in result || key in excluded) return@forEach
            result[key] = ArenaInfo(dir.name, "", ArenaState.UNCONFIGURED, false)
        }

        // 4) groups BedWars1058 knows about even if no arena uses them yet
        if (bwFolder != null) {
            val configFile = File(bwFolder, "config.yml")
            if (configFile.isFile) {
                runCatching { YamlConfiguration.loadConfiguration(configFile).getStringList("arenaGroups") }
                    .getOrNull()?.filter { it.isNotBlank() }?.forEach { groups.add(it) }
            }
        }

        val sorted = result.values.sortedWith(compareBy(NaturalOrder) { it.name })
        return Snapshot(sorted, groups.toList())
    }
}
