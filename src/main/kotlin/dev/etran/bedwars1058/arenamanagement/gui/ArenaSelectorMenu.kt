package dev.etran.bedwars1058.arenamanagement.gui

import dev.etran.bedwars1058.arenamanagement.ArenaManagementPlugin
import dev.etran.bedwars1058.arenamanagement.arena.LiveArena
import dev.etran.bedwars1058.arenamanagement.util.ItemBuilder
import dev.etran.bedwars1058.arenamanagement.util.NaturalOrder
import dev.etran.bedwars1058.arenamanagement.util.Text
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType

/**
 * `/bw gui [group]` - the "Click to join" menu players use. Replaces BedWars1058's own arena-gui
 * slot settings.
 *
 * Layout (when `gui.slots` is empty): a 7-wide block of arenas inside a one-slot empty border,
 * exactly like BedWars1058's default (28 maps on a 9x6 chest). The chest grows from 3 rows up to
 * 6 as maps are added; beyond one full chest it pages, with Previous / Next in the bottom row.
 * When `gui.slots` is set, exactly those slots are used instead.
 */
class ArenaSelectorMenu(
    plugin: ArenaManagementPlugin,
    player: Player,
    private val group: String?,
    private var page: Int = 0
) : Menu(plugin, player) {

    private val settings = plugin.settings
    private var arenas: List<LiveArena> = load()
    private val layoutRows: Int
    private val slots: List<Int>

    init {
        val custom = settings.guiCustomSlots
        if (custom.isNotEmpty()) {
            layoutRows = if (settings.guiFixedRows > 0) settings.guiFixedRows else 6
            slots = custom.filter { it in 0 until layoutRows * 9 }.distinct()
        } else {
            val interiorRowsNeeded = maxOf(1, (arenas.size + 6) / 7)
            layoutRows = if (settings.guiFixedRows > 0) settings.guiFixedRows.coerceAtLeast(3) else (interiorRowsNeeded + 2).coerceIn(settings.guiRowsMin, 6)
            slots = (1 until layoutRows - 1).flatMap { row -> (1..7).map { column -> row * 9 + column } }
        }
    }

    override val rows: Int get() = layoutRows

    private val perPage: Int get() = maxOf(1, slots.size)
    private val pageCount: Int get() = maxOf(1, (arenas.size + perPage - 1) / perPage)

    /** "default" (what `/bw gui` uses with no argument) means every group, like BedWars1058. */
    private val groupFilter: List<String>? = group
        ?.takeUnless { it.equals(settings.defaultGroup, ignoreCase = true) }
        ?.split('+')
        ?.filter { it.isNotBlank() }

    private fun load(): List<LiveArena> =
        plugin.hook.liveArenas()
            // BedWars1058 never lists arenas that are restarting.
            .filter { it.status == "waiting" || it.status == "starting" || it.status == "playing" }
            .filter { arena -> groupFilter == null || groupFilter.any { it.equals(arena.group, ignoreCase = true) } }
            .filter { settings.guiShowPlaying || it.status != "playing" }
            .sortedWith(compareBy(NaturalOrder) { it.name })

    override fun title(): String {
        val base = if (groupFilter == null) settings.guiTitleAll else Text.fill(settings.guiTitle, "group" to groupFilter.joinToString("+"))
        val suffix = if (pageCount > 1) Text.fill(settings.guiTitlePageSuffix, "page" to (page + 1).toString(), "pages" to pageCount.toString()) else ""
        return base + suffix
    }

    override fun onOpened() {
        plugin.liveSelectors.add(this)
    }

    override fun onClosed() {
        plugin.liveSelectors.remove(this)
    }

    /** Called by the refresh task: re-reads live state and redraws without reopening. */
    fun live() {
        arenas = load()
        page = page.coerceIn(0, pageCount - 1)
        refresh()
    }

    override fun render() {
        page = page.coerceIn(0, pageCount - 1)
        val pageArenas = arenas.drop(page * perPage).take(perPage)

        val skipped = settings.guiSkippedItem
        slots.forEachIndexed { index, slot ->
            val arena = pageArenas.getOrNull(index)
            if (arena != null) {
                set(slot, arenaItem(arena)) { click -> onArenaClick(arena, click) }
            } else if (skipped.isNotBlank()) {
                set(slot, ItemBuilder(skipped).name(settings.guiSkippedName).build()) { plugin.ops.joinRandom(player) }
            }
        }

        if (pageCount > 1) {
            val bottom = (layoutRows - 1) * 9
            if (page > 0 && bottom + 3 !in slots) {
                set(bottom + 3, icon("prev", "&aPrevious page", listOf("&7Page $page of $pageCount"))) { turn(-1) }
            }
            if (bottom + 4 !in slots) set(bottom + 4, icon("page", "&7Page &f${page + 1}&7/&f$pageCount"))
            if (page < pageCount - 1 && bottom + 5 !in slots) {
                set(bottom + 5, icon("next", "&aNext page", listOf("&7Page ${page + 2} of $pageCount"))) { turn(1) }
            }
        }
    }

    /** Same as BedWars1058's menu: left-click joins a waiting/starting arena, right-click spectates a running one. */
    private fun onArenaClick(arena: LiveArena, click: ClickType) {
        when (click) {
            ClickType.LEFT -> {
                // `bw join` (not IArena#addPlayer directly) so BedWars1058 gives its normal feedback and
                // BedWars1058-MapLoader can wake a parked arena and join the player when it is ready.
                player.closeInventory()
                plugin.ops.join(player, arena.name)
            }
            ClickType.RIGHT -> {
                if (plugin.hook.spectate(player, arena.name)) player.closeInventory()
                else msg("&cYou can only spectate arenas that are running a game.")
            }
            else -> {}
        }
    }

    /** The title shows the page, so a page turn opens a fresh menu instead of redrawing. */
    private fun turn(delta: Int) {
        go(ArenaSelectorMenu(plugin, player, group, (page + delta).coerceIn(0, pageCount - 1)))
    }

    private fun arenaItem(arena: LiveArena) = ItemBuilder(settings.guiItemSpec(arena.status))
        .name(fill(settings.guiName, arena))
        .lore(
            settings.guiLore
                // BedWars1058 hides lines mentioning {group} for arenas in the default group.
                .filterNot { it.contains("{group}") && arena.group.equals(settings.defaultGroup, ignoreCase = true) }
                .map { fill(it, arena) }
        )
        .glow(settings.guiItemEnchanted(arena.status))
        .amount(1)
        .build()

    private fun fill(template: String, arena: LiveArena) = Text.fill(
        template,
        "name" to arena.displayName,
        "map_name" to arena.name,
        "displayName" to arena.displayName,
        "on" to arena.players.toString(),
        "group" to arena.group,
        "players" to arena.players.toString(),
        "max" to arena.maxPlayers.toString(),
        "status" to settings.guiStatusName(arena.status)
    )
}
