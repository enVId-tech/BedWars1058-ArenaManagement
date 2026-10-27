package dev.etran.bedwars1058.arenamanagement.session

import dev.etran.bedwars1058.arenamanagement.ArenaManagementPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

/**
 * "Type the name in chat" prompts (clone name, new group, name suffix). The typed line is
 * swallowed, never broadcast, and is handed back on the main thread.
 */
class ChatPrompts(private val plugin: ArenaManagementPlugin) : Listener {
    private val pending = HashMap<UUID, (String) -> Unit>()

    fun ask(player: Player, question: String, onInput: (String) -> Unit) {
        player.closeInventory()
        synchronized(pending) { pending[player.uniqueId] = onInput }
        plugin.msg(player, question)
        plugin.msg(player, "&7Type &ecancel&7 to abort.")
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncPlayerChatEvent) {
        val callback = synchronized(pending) { pending.remove(event.player.uniqueId) } ?: return
        event.isCancelled = true
        val text = event.message.trim()
        val player = event.player
        plugin.later(0L) {
            if (text.equals("cancel", ignoreCase = true)) plugin.msg(player, "&7Cancelled.") else callback(text)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        synchronized(pending) { pending.remove(event.player.uniqueId) }
        plugin.sessions.drop(event.player)
    }
}
