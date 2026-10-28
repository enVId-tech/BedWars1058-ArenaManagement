package dev.etran.bedwars1058.arenamanagement.gui

import dev.etran.bedwars1058.arenamanagement.ArenaManagementPlugin
import dev.etran.bedwars1058.arenamanagement.util.Compat
import dev.etran.bedwars1058.arenamanagement.util.ItemBuilder
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * Base class for every chest menu. The menu itself is the inventory's holder, which is how clicks
 * are matched back to it (titles are never compared, so titles can contain anything).
 */
abstract class Menu(protected val plugin: ArenaManagementPlugin, val player: Player) : InventoryHolder {
    protected abstract val rows: Int
    protected abstract fun title(): String
    protected abstract fun render()

    private var inv: Inventory? = null
    private val actions = HashMap<Int, (ClickType) -> Unit>()

    override fun getInventory(): Inventory = inv ?: throw IllegalStateException("Menu has not been opened yet")

    fun open() {
        val created = Bukkit.createInventory(this, rows * 9, Compat.inventoryTitle(title()))
        inv = created
        render()
        player.openInventory(created)
        onOpened()
    }

    /** Redraws in place (no flicker, title unchanged). */
    open fun refresh() {
        val current = inv ?: return
        current.clear()
        actions.clear()
        render()
    }

    open fun onOpened() {}
    open fun onClosed() {}

    protected fun set(slot: Int, item: ItemStack, action: ((ClickType) -> Unit)? = null) {
        val current = inv ?: return
        if (slot < 0 || slot >= current.size) return
        current.setItem(slot, item)
        if (action != null) actions[slot] = action
    }

    internal fun handleClick(slot: Int, type: ClickType) {
        actions[slot]?.invoke(type)
    }

    /** Builds an item from the `items.<key>` spec in config.yml. */
    protected fun icon(key: String, name: String, lore: List<String> = emptyList(), glow: Boolean = false, amount: Int = 1): ItemStack =
        ItemBuilder(plugin.settings.item(key)).name(name).lore(lore).glow(glow).amount(amount).build()

    /** Opens another menu on the next tick (opening inside a click handler is fragile). */
    protected fun go(menu: Menu) {
        plugin.later(1L) { if (player.isOnline) menu.open() }
    }

    protected fun msg(text: String) = plugin.msg(player, text)

    /** Left/right click both count as a plain click. */
    protected fun isPlain(click: ClickType) = click == ClickType.LEFT || click == ClickType.RIGHT
    protected fun isShift(click: ClickType) = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT
}

class MenuListener(private val plugin: ArenaManagementPlugin) : Listener {

    // NOTE: only InventoryEvent#getInventory (the TOP inventory) is used here. Touching
    // getView() would not link across the 1.21 InventoryView class -> interface change.
    @EventHandler(priority = EventPriority.HIGH)
    fun onClick(event: InventoryClickEvent) {
        val menu = event.inventory.holder as? Menu ?: return
        event.isCancelled = true // blocks taking items, shift-clicking in, number keys, double-click collect
        if (event.click == ClickType.DOUBLE_CLICK) return
        val slot = event.rawSlot
        if (slot < 0 || slot >= event.inventory.size) return
        try {
            menu.handleClick(slot, event.click)
        } catch (t: Throwable) {
            plugin.logger.warning("Menu click failed: ${t.javaClass.simpleName}: ${t.message}")
            t.printStackTrace()
            plugin.msg(menu.player, "&cSomething went wrong - see the console.")
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is Menu) event.isCancelled = true
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        (event.inventory.holder as? Menu)?.onClosed()
    }
}
