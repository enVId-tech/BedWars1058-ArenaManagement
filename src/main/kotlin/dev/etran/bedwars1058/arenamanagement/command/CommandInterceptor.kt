package dev.etran.bedwars1058.arenamanagement.command

import dev.etran.bedwars1058.arenamanagement.ArenaManagementPlugin
import dev.etran.bedwars1058.arenamanagement.gui.ArenaBrowserMenu
import dev.etran.bedwars1058.arenamanagement.gui.ArenaSelectorMenu
import dev.etran.bedwars1058.arenamanagement.gui.GroupMenu
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

/**
 * Takes over four BedWars1058 sub-commands by cancelling the command before BedWars1058 sees it
 * (BedWars1058 itself is never modified):
 *
 *   /bw setupArena          -> map manager menu        (with an argument: plain BedWars1058)
 *   /bw arenaGroup          -> group manager menu      (with arguments:   plain BedWars1058,
 *                                                       except `list`, which prints our list)
 *   /bw arenaList           -> grouped, colour-coded list
 *   /bw gui [group]         -> the join menu
 *
 * Commands our menus run on the player's behalf go through Bukkit.dispatchCommand, which does not
 * fire this event, so there is no loop.
 */
class CommandInterceptor(private val plugin: ArenaManagementPlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val parts = event.message.trim().removePrefix("/").split(Regex("\\s+"))
        if (parts.size < 2) return

        // "bedwars1058:bw" style namespaced labels are accepted too.
        val label = parts[0].substringAfter(':').lowercase()
        if (label !in plugin.settings.aliases) return

        val sub = parts[1].lowercase()
        val args = parts.drop(2)
        val player = event.player
        val settings = plugin.settings

        when (sub) {
            "setuparena" -> {
                if (!settings.overrides("setup-arena") || args.isNotEmpty() || !canManage(player)) return
                event.isCancelled = true
                plugin.later(1L) { ArenaBrowserMenu(plugin, player).open() }
            }
            "arenagroup" -> {
                if (!settings.overrides("arena-group") || !player.hasPermission(settings.groupPermission)) return
                when {
                    args.isEmpty() -> {
                        event.isCancelled = true
                        plugin.later(1L) { GroupMenu(plugin, player).open() }
                    }
                    args[0].equals("list", ignoreCase = true) -> {
                        event.isCancelled = true
                        plugin.ops.sendGroupList(player)
                    }
                }
            }
            "arenalist" -> {
                if (!settings.overrides("arena-list") || !canManage(player)) return
                event.isCancelled = true
                plugin.ops.sendArenaList(player)
            }
            "gui" -> {
                // BedWars1058 itself ignores /bw gui while you are inside an arena.
                if (!settings.overrides("gui") || plugin.hook.isInArena(player)) return
                event.isCancelled = true
                openSelector(player, args.firstOrNull())
            }
        }
    }

    private fun canManage(player: Player) = player.hasPermission(plugin.settings.permission)

    /** Shared by `/bw gui` and `/arenamanagement gui`. */
    fun openSelector(player: Player, group: String?) {
        if (group != null) {
            // "a+b" selects several groups, like BedWars1058.
            val snapshot = plugin.repository.snapshot()
            val known = group.split('+').filter { it.isNotBlank() }.all { part ->
                snapshot.hasGroup(part) || plugin.hook.liveArenas().any { it.group.equals(part, ignoreCase = true) }
            }
            if (!known) {
                plugin.msg(player, "&cThere is no arena group called &e$group&c.")
                return
            }
        }
        plugin.later(1L) { ArenaSelectorMenu(plugin, player, group).open() }
    }
}
