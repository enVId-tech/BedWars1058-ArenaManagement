package dev.etran.bedwars1058.arenamanagement.util

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

object Items {
    /**
     * Resolves an item "spec" from config.
     *
     * A spec is one or more candidates separated by `|`, each `NAME` or `NAME:data`. The first
     * candidate that exists on the running server wins, so one config line works everywhere:
     *
     *     LIME_CONCRETE|STAINED_CLAY:5
     *
     * is lime concrete on 1.13+, and green stained clay on 1.8 - 1.12.
     */
    fun resolve(spec: String): ItemStack {
        for (candidate in spec.split('|')) {
            val trimmed = candidate.trim()
            if (trimmed.isEmpty()) continue
            val name = trimmed.substringBefore(':').trim()
            val data = trimmed.substringAfter(':', "").trim().toIntOrNull() ?: 0
            val material = Material.matchMaterial(name) ?: continue
            @Suppress("DEPRECATION")
            return if (data > 0 && Compat.isLegacyMaterials) ItemStack(material, 1, data.toShort()) else ItemStack(material)
        }
        return ItemStack(Material.STONE)
    }

    /**
     * Enchantment used for the "glowing" effect. Looked up reflectively because Enchantment's
     * shape and names changed (DURABILITY became UNBREAKING) and a direct reference would not
     * link on every version.
     */
    val glowEnchantment: Enchantment? by lazy {
        listOf("UNBREAKING", "DURABILITY", "LURE", "LUCK").firstNotNullOfOrNull { name ->
            try {
                Enchantment::class.java.getMethod("getByName", String::class.java).invoke(null, name) as? Enchantment
            } catch (_: Throwable) {
                null
            }
        }
    }
}

class ItemBuilder(spec: String) {
    private val stack: ItemStack = Items.resolve(spec)
    private var displayName: String? = null
    private var lore: List<String> = emptyList()
    private var glow = false

    fun name(value: String) = apply { displayName = value }
    fun lore(value: List<String>) = apply { lore = value }
    fun glow(value: Boolean = true) = apply { glow = value }
    fun amount(value: Int) = apply { stack.amount = value.coerceIn(1, 64) }

    fun build(): ItemStack {
        val meta = stack.itemMeta ?: return stack
        displayName?.let { meta.setDisplayName(Text.color(it)) }
        if (lore.isNotEmpty()) meta.lore = lore.map { Text.color(it) }
        if (glow) Items.glowEnchantment?.let { meta.addEnchant(it, 1, true) }
        // Hides enchant text, attributes, potion info, ... on every version.
        meta.addItemFlags(*ItemFlag.values())
        stack.itemMeta = meta
        return stack
    }
}
