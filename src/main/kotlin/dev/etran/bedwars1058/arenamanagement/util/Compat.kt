package dev.etran.bedwars1058.arenamanagement.util

import org.bukkit.Bukkit
import org.bukkit.ChatColor

/**
 * Everything that differs between Minecraft versions lives here or in [Items].
 *
 * Rules the rest of the plugin follows so one jar works from 1.8 to the newest versions:
 *  - never call InventoryClickEvent#getView / InventoryView (class -> interface in 1.21),
 *  - never reference Sound, Enchantment constants or flattened Material constants directly,
 *  - identify our inventories with an InventoryHolder, never by title.
 */
object Compat {
    /**
     * Minor version of a "1.x" server (8 for 1.8.8, 21 for 1.21.4).
     * New-style versions (26.1, 26.3, ...) report 99 so every "minor >= N" check stays true.
     */
    val minor: Int by lazy {
        val version = Bukkit.getBukkitVersion().substringBefore('-')
        val parts = version.split('.')
        val first = parts.getOrNull(0)?.toIntOrNull() ?: 1
        if (first == 1) parts.getOrNull(1)?.toIntOrNull() ?: 8 else 99
    }

    /** True on 1.8 - 1.12, where materials still use numeric "data" values. */
    val isLegacyMaterials: Boolean get() = minor < 13

    /** Inventory titles longer than 32 characters throw on 1.8 - 1.13. */
    private val maxTitleLength: Int get() = if (minor < 14) 32 else 256

    fun inventoryTitle(raw: String): String {
        var title = Text.color(raw)
        if (title.length > maxTitleLength) {
            title = title.substring(0, maxTitleLength)
            if (title.endsWith(ChatColor.COLOR_CHAR)) title = title.dropLast(1)
        }
        return title
    }
}

object Text {
    fun color(text: String): String = ChatColor.translateAlternateColorCodes('&', text)

    /** Replaces every {key} with its value. */
    fun fill(template: String, vararg values: Pair<String, String>): String {
        var out = template
        for ((key, value) in values) out = out.replace("{$key}", value)
        return out
    }
}

/** "map2" sorts before "map10"; otherwise plain case-insensitive alphabetical. */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ie = i
                while (ie < a.length && a[ie].isDigit()) ie++
                var je = j
                while (je < b.length && b[je].isDigit()) je++
                val na = a.substring(i, ie).trimStart('0')
                val nb = b.substring(j, je).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val cmp = na.compareTo(nb)
                if (cmp != 0) return cmp
                i = ie
                j = je
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
