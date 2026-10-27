package dev.etran.bedwars1058.arenamanagement

import dev.etran.bedwars1058.arenamanagement.util.Text

/** Typed, always-fresh reads of config.yml (so /arenamanagement reload needs no extra work). */
class Settings(private val plugin: ArenaManagementPlugin) {
    private val cfg get() = plugin.config

    val prefix: String get() = Text.color(cfg.getString("prefix") ?: "&8[&bArenaMgmt&8] ")

    // bedwars
    val defaultGroup: String get() = cfg.getString("bedwars.default-group") ?: "Default"
    val excludedWorlds: Set<String> get() = cfg.getStringList("bedwars.excluded-worlds").map { it.lowercase() }.toSet()
    val cacheMillis: Long get() = cfg.getLong("bedwars.cache-millis", 3000L)

    // commands
    fun command(key: String): String = cfg.getString("commands.$key") ?: ""

    // overrides
    val aliases: Set<String> get() = cfg.getStringList("override.aliases").map { it.lowercase() }.toSet()
    fun overrides(key: String): Boolean = cfg.getBoolean("override.$key", true)
    val permission: String get() = cfg.getString("override.permission") ?: "bw.setup"
    val groupPermission: String get() = cfg.getString("override.group-permission") ?: "bw.groups"

    // bulk
    val commandsPerStep: Int get() = cfg.getInt("bulk.commands-per-step", 1).coerceAtLeast(1)
    val ticksBetweenSteps: Long get() = cfg.getLong("bulk.ticks-between-steps", 3L).coerceAtLeast(1L)
    val verifyDelayTicks: Long get() = cfg.getLong("bulk.verify-delay-ticks", 60L).coerceAtLeast(1L)
    val setupDelayTicks: Long get() = cfg.getLong("bulk.setup-delay-ticks", 20L).coerceAtLeast(1L)
    val confirmDelete: Boolean get() = cfg.getBoolean("bulk.confirm-delete", true)

    // manager items
    fun item(key: String): String = cfg.getString("items.$key") ?: "STONE"

    // gui
    val guiTitle: String get() = cfg.getString("gui.title") ?: "&8{group}"
    val guiTitleAll: String get() = cfg.getString("gui.title-all") ?: "&8All Arenas"
    val guiTitlePageSuffix: String get() = cfg.getString("gui.title-page-suffix") ?: " &7({page}/{pages})"
    val guiRowsMin: Int get() = cfg.getInt("gui.rows-min", 3).coerceIn(3, 6)
    val guiCustomSlots: List<Int> get() = cfg.getIntegerList("gui.slots")
    val guiShowPlaying: Boolean get() = cfg.getBoolean("gui.show-playing", true)
    val guiSkippedItem: String get() = cfg.getString("gui.skipped-slot-item") ?: ""
    val guiSkippedName: String get() = cfg.getString("gui.skipped-slot-name") ?: " "
    val guiRefreshTicks: Long get() = cfg.getLong("gui.refresh-ticks", 20L).coerceAtLeast(5L)
    val guiName: String get() = cfg.getString("gui.name") ?: "&a{name}"
    val guiLore: List<String> get() = cfg.getStringList("gui.lore")

    /** 0 = AUTO, 1-6 = fixed height. */
    val guiFixedRows: Int
        get() = (cfg.getString("gui.rows") ?: "AUTO").trim().toIntOrNull()?.coerceIn(1, 6) ?: 0

    fun guiStatusName(status: String): String =
        cfg.getString("gui.status-names.${status.lowercase()}") ?: status

    fun guiItemSpec(status: String): String {
        val key = if (status.equals("restarting", true)) "playing" else status.lowercase()
        return cfg.getString("gui.items.$key.material") ?: cfg.getString("gui.items.waiting.material") ?: "LIME_CONCRETE|STAINED_CLAY:5"
    }

    fun guiItemEnchanted(status: String): Boolean {
        val key = if (status.equals("restarting", true)) "playing" else status.lowercase()
        return cfg.getBoolean("gui.items.$key.enchanted", false)
    }
}
