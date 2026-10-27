package dev.etran.bedwars1058.arenamanagement.gui

import dev.etran.bedwars1058.arenamanagement.ArenaManagementPlugin
import org.bukkit.entity.Player

/** Generic yes/no screen used before destructive actions. */
class ConfirmMenu(
    plugin: ArenaManagementPlugin,
    player: Player,
    private val heading: String,
    private val details: List<String>,
    private val onConfirm: () -> Unit,
    private val onCancel: () -> Unit
) : Menu(plugin, player) {
    override val rows = 3

    override fun title() = "&8Are you sure?"

    override fun render() {
        set(13, icon("info", heading, details))
        set(11, icon("confirm", "&a&lConfirm", listOf("&7Click to proceed")), { onConfirm() })
        set(15, icon("cancel", "&c&lCancel", listOf("&7Go back without changing anything")), { onCancel() })
    }
}
