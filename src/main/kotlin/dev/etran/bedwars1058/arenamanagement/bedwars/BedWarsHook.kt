package dev.etran.bedwars1058.arenamanagement.bedwars

import dev.etran.bedwars1058.arenamanagement.ArenaManagementPlugin
import dev.etran.bedwars1058.arenamanagement.arena.LiveArena
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method

/**
 * Read-only view of BedWars1058's loaded arenas.
 *
 * Done with reflection on purpose: the BedWars1058 API has shifted between releases, and this way
 * the plugin has no compile-time dependency on it and keeps working across versions. Every method
 * name is tried in a few spellings. If BedWars1058's API can't be reached, [isAvailable] is false
 * and the plugin says so loudly instead of silently showing every arena as disabled.
 */
class BedWarsHook(private val plugin: ArenaManagementPlugin) {
    val bedWarsPlugin: Plugin? = Bukkit.getPluginManager().getPlugin("BedWars1058")

    private var api: Any? = null

    val isAvailable: Boolean get() = api != null

    fun connect(): Boolean {
        if (bedWarsPlugin == null) return false
        try {
            val apiClass = Class.forName("com.andrei1058.bedwars.api.BedWars")
            @Suppress("UNCHECKED_CAST")
            api = Bukkit.getServicesManager().getRegistration(apiClass as Class<Any>)?.provider
        } catch (t: Throwable) {
            plugin.logger.warning("Could not reach the BedWars1058 API service: ${t.javaClass.simpleName}: ${t.message}")
        }
        if (api == null) {
            plugin.logger.warning("BedWars1058 API service not found - enabled/disabled state of arenas can not be detected.")
        }
        return true
    }

    /** All arenas BedWars1058 currently has loaded (i.e. enabled). */
    fun liveArenas(): List<LiveArena> {
        val bw = api ?: return emptyList()
        return try {
            val util = call(bw, "getArenaUtil") ?: return emptyList()
            val arenas = call(util, "getArenas") as? Collection<*> ?: return emptyList()
            arenas.mapNotNull { toLive(it ?: return@mapNotNull null) }
        } catch (t: Throwable) {
            plugin.logger.warning("Reading BedWars1058 arenas failed: ${t.javaClass.simpleName}: ${t.message}")
            emptyList()
        }
    }

    private fun toLive(arena: Any): LiveArena? {
        val name = (call(arena, "getArenaName") ?: call(arena, "getWorldName")) as? String ?: return null
        val display = (call(arena, "getDisplayName") as? String)?.takeIf { it.isNotBlank() } ?: name
        val group = (call(arena, "getGroup") as? String) ?: plugin.settings.defaultGroup
        val status = call(arena, "getStatus")?.toString()?.lowercase() ?: "waiting"
        val players = (call(arena, "getPlayers") as? Collection<*>)?.size ?: 0
        val max = (call(arena, "getMaxPlayers") as? Number)?.toInt() ?: 0
        return LiveArena(name, display, group, status, players, max)
    }

    private fun call(target: Any, name: String): Any? {
        val method: Method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 } ?: return null
        return try {
            method.isAccessible = true
            method.invoke(target)
        } catch (_: Throwable) {
            null
        }
    }
}
