package dev.etran.bedwars1058.arenamanagement.arena

import dev.etran.bedwars1058.arenamanagement.ArenaManagementPlugin
import dev.etran.bedwars1058.arenamanagement.util.NaturalOrder
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerCommandPreprocessEvent

/**
 * Every management action, expressed as BedWars1058 commands (templates from config.yml). The
 * single-arena and bulk menus both go through here, so behaviour is identical for 1 map or 100.
 */
class ArenaOps(private val plugin: ArenaManagementPlugin) {
    private val repo get() = plugin.repository
    private val settings get() = plugin.settings

    private val validGroupName = Regex("[A-Za-z0-9_\\-]{1,32}")

    /** BedWars1058 refuses to set up maps with capital letters (and '+' is its group separator). */
    private val validArenaName = Regex("[a-z0-9_\\-]{1,32}")

    fun isValidName(name: String): Boolean = validGroupName.matches(name)
    fun isValidArenaName(name: String): Boolean = validArenaName.matches(name)

    private fun template(key: String, vararg values: Pair<String, String>): String {
        var command = settings.command(key)
        for ((placeholder, value) in values) command = command.replace("{$placeholder}", value)
        return command.trim().removePrefix("/")
    }

    /**
     * Runs a command exactly as if the player had typed it: the PlayerCommandPreprocessEvent is fired first,
     * so plugins that hook commands (BedWars1058-MapLoader waking a parked arena on `bw join` / `bw disableArena`,
     * permission plugins, ...) see it. Bukkit.dispatchCommand alone skips that event.
     * Returns false if BedWars1058 threw an exception.
     */
    fun runAsPlayer(player: Player, command: String): Boolean = try {
        val event = PlayerCommandPreprocessEvent(player, "/$command")
        Bukkit.getPluginManager().callEvent(event)
        if (!event.isCancelled) Bukkit.dispatchCommand(player, event.message.removePrefix("/"))
        true
    } catch (t: Throwable) {
        val cause = generateSequence<Throwable>(t) { it.cause }.last()
        plugin.logger.warning("BedWars1058 threw while running '/$command': ${cause.javaClass.simpleName}: ${cause.message}")
        plugin.msg(player, "&cBedWars1058 hit an internal error running &e/$command&c. See the console for details.")
        false
    }

    /**
     * An arena BedWars1058 lists but has no world for. With BedWars1058-MapLoader that is a normal "parked" arena
     * (its command hook handles disabling it). Without it BedWars1058 throws a NullPointerException when disabling.
     */
    private fun cannotHandle(info: ArenaInfo): Boolean = !info.worldLoaded && plugin.mapLoader == null

    private fun brokenMessage(player: Player, name: String) {
        plugin.msg(
            player,
            "&e$name&c is loaded in BedWars1058 but its world is not (the world folder is probably missing or failed to load), " +
                "so BedWars1058 can't disable or set it up. Fix or restore the world, then restart or reload the server."
        )
    }

    private fun submit(
        player: Player,
        title: String,
        commands: List<String>,
        expected: Int,
        verify: ((ArenaRepository.Snapshot) -> Int)?
    ) {
        plugin.runner.submit(BulkRunner.Job(player, title, commands, expected, verify))
    }

    // ---------------------------------------------------------------- setup

    /** Left-click behaviour: disable the arena if it is on, then start BedWars1058's setup. */
    fun setup(player: Player, name: String) {
        val info = repo.snapshot(force = true).find(name)
        if (info == null) {
            plugin.msg(player, "&cNo map named &e$name&c.")
            return
        }
        if (info.name != info.name.lowercase()) {
            plugin.msg(player, "&cBedWars1058 can't set up &e${info.name}&c: map names must be lowercase. Rename the world folder to &a${info.name.lowercase()}&c.")
            return
        }
        if (cannotHandle(info)) {
            brokenMessage(player, info.name)
            return
        }
        player.closeInventory()
        if (info.state == ArenaState.ENABLED) {
            plugin.msg(player, "&e${info.name}&7 is enabled - disabling it first, then starting setup...")
            if (!runAsPlayer(player, template("disable", "arena" to info.name))) return
            repo.invalidate()
            plugin.later(settings.setupDelayTicks) {
                if (player.isOnline) runAsPlayer(player, template("setup", "arena" to info.name))
            }
        } else {
            runAsPlayer(player, template("setup", "arena" to info.name))
        }
    }

    // ---------------------------------------------------------------- bulk-capable actions

    fun setGroup(player: Player, names: Collection<String>, group: String) {
        if (group.equals(settings.defaultGroup, ignoreCase = true)) {
            plugin.msg(player, "&cBedWars1058 can't assign the &e${settings.defaultGroup}&c group with a command - it is the group every un-grouped map already has. Pick or create another group.")
            return
        }
        if (!isValidName(group)) {
            plugin.msg(player, "&cGroup names may only contain letters, digits, _ and - (max 32).")
            return
        }
        val snapshot = repo.snapshot(force = true)
        val infos = names.mapNotNull { snapshot.find(it) }
        val targets = infos.filter { it.state != ArenaState.UNCONFIGURED }
        val skipped = names.size - targets.size
        if (skipped > 0) plugin.msg(player, "&7Skipping &e$skipped&7 map(s) that are not set up yet (set them up first).")
        if (targets.isEmpty()) return

        // BedWars1058 only accepts groups that exist, and compares names case-sensitively.
        val existing = snapshot.canonicalGroup(group)
        val finalGroup = existing ?: group
        val commands = ArrayList<String>()
        if (existing == null) commands += template("create-group", "group" to finalGroup)
        targets.forEach { commands += template("set-group", "arena" to it.name, "group" to finalGroup) }

        submit(player, "Moving ${targets.size} map(s) to group $finalGroup", commands, targets.size) { snap ->
            targets.count { snap.find(it.name)?.group.equals(finalGroup, ignoreCase = true) }
        }
    }

    fun enable(player: Player, names: Collection<String>) {
        val snapshot = repo.snapshot(force = true)
        val infos = names.mapNotNull { snapshot.find(it) }
        val targets = infos.filter { it.state == ArenaState.DISABLED }
        reportSkipped(player, infos, targets, "enable", "already enabled or not set up")
        if (targets.isEmpty()) return
        submit(player, "Enabling ${targets.size} map(s)", targets.map { template("enable", "arena" to it.name) }, targets.size) { snap ->
            targets.count { snap.find(it.name)?.state == ArenaState.ENABLED }
        }
    }

    fun disable(player: Player, names: Collection<String>) {
        val snapshot = repo.snapshot(force = true)
        val infos = names.mapNotNull { snapshot.find(it) }
        val targets = infos.filter { it.state == ArenaState.ENABLED && !cannotHandle(it) }
        infos.filter { cannotHandle(it) }.forEach { brokenMessage(player, it.name) }
        reportSkipped(player, infos.filter { !cannotHandle(it) }, targets, "disable", "already disabled or not set up")
        if (targets.isEmpty()) return
        submit(player, "Disabling ${targets.size} map(s)", targets.map { template("disable", "arena" to it.name) }, targets.size) { snap ->
            targets.count { snap.find(it.name)?.state != ArenaState.ENABLED }
        }
    }

    fun delete(player: Player, names: Collection<String>) {
        val snapshot = repo.snapshot(force = true)
        val infos = names.mapNotNull { snapshot.find(it) }
        val targets = infos.filter { it.state != ArenaState.UNCONFIGURED && !cannotHandle(it) }
        infos.filter { cannotHandle(it) }.forEach { brokenMessage(player, it.name) }
        reportSkipped(player, infos.filter { !cannotHandle(it) }, targets, "delete", "not set up, nothing to delete")
        if (targets.isEmpty()) return
        val commands = ArrayList<String>()
        for (target in targets) {
            // BedWars1058 refuses to delete a loaded arena, so switch it off first.
            if (target.state == ArenaState.ENABLED) commands += template("disable", "arena" to target.name)
            // delArena only deletes when it is typed a second time within 2 seconds.
            commands += template("delete", "arena" to target.name)
            commands += template("delete", "arena" to target.name)
        }
        submit(player, "Deleting ${targets.size} map(s)", commands, targets.size) { snap ->
            targets.count { val s = snap.find(it.name)?.state; s == null || s == ArenaState.UNCONFIGURED }
        }
    }

    /** Clones one map to [newName]. */
    fun clone(player: Player, source: String, newName: String) {
        cloneMany(player, listOf(source)) { newName }
    }

    /** Clones every map in [names], naming each copy `<name><suffix>`. */
    fun cloneWithSuffix(player: Player, names: Collection<String>, suffix: String) {
        cloneMany(player, names) { it + suffix }
    }

    private fun cloneMany(player: Player, names: Collection<String>, newNameFor: (String) -> String) {
        val snapshot = repo.snapshot(force = true)
        val pairs = ArrayList<Pair<ArenaInfo, String>>()
        val usedNames = HashSet<String>()
        for (info in names.mapNotNull { snapshot.find(it) }) {
            if (info.state == ArenaState.UNCONFIGURED) {
                plugin.msg(player, "&7Skipping &e${info.name}&7: not set up yet.")
                continue
            }
            val target = newNameFor(info.name)
            when {
                !isValidArenaName(target) -> plugin.msg(player, "&7Skipping &e${info.name}&7: &e$target&7 is not a valid map name (lowercase letters, digits, _ and - only).")
                snapshot.find(target) != null || !usedNames.add(target.lowercase()) -> plugin.msg(player, "&7Skipping &e${info.name}&7: a map named &e$target&7 already exists.")
                else -> pairs += info to target
            }
        }
        if (pairs.isEmpty()) return
        submit(player, "Cloning ${pairs.size} map(s)", pairs.map { (info, target) -> template("clone", "arena" to info.name, "new" to target) }, pairs.size) { snap ->
            pairs.count { (_, target) -> snap.find(target) != null }
        }
    }

    private fun reportSkipped(player: Player, all: List<ArenaInfo>, targets: List<ArenaInfo>, verb: String, why: String) {
        val skipped = all.size - targets.size
        if (skipped > 0) plugin.msg(player, "&7Not trying to $verb &e$skipped&7 map(s): $why.")
    }

    // ---------------------------------------------------------------- groups

    fun createGroup(player: Player, group: String) {
        if (!isValidName(group)) {
            plugin.msg(player, "&cGroup names may only contain letters, digits, _ and - (max 32).")
            return
        }
        if (repo.snapshot(force = true).hasGroup(group)) {
            plugin.msg(player, "&cThe group &e$group&c already exists.")
            return
        }
        runAsPlayer(player, template("create-group", "group" to group))
        repo.invalidate()
    }

    fun removeGroup(player: Player, group: String) {
        if (group.equals(settings.defaultGroup, ignoreCase = true)) {
            plugin.msg(player, "&cThe default group can not be removed.")
            return
        }
        runAsPlayer(player, template("remove-group", "group" to (repo.snapshot().canonicalGroup(group) ?: group)))
        repo.invalidate()
    }

    // ---------------------------------------------------------------- chat output

    /** Joins through `bw join <arena>`, so BedWars1058's own checks (and MapLoader's wake-up) apply. */
    fun join(player: Player, arena: String) {
        runAsPlayer(player, template("join", "arena" to arena))
    }

    fun joinRandom(player: Player) {
        runAsPlayer(player, template("join-random"))
    }

    fun sendArenaList(player: Player) {
        val snapshot = repo.snapshot(force = true)
        plugin.msg(
            player,
            "&7Maps: &f${snapshot.arenas.size} &8| &aEnabled ${snapshot.count(ArenaState.ENABLED)} &8| " +
                "&cDisabled ${snapshot.count(ArenaState.DISABLED)} &8| &7Not set up ${snapshot.count(ArenaState.UNCONFIGURED)} &8| " +
                "&eUnassigned ${snapshot.unassignedCount}"
        )
        val byGroup = snapshot.arenas.filter { it.state != ArenaState.UNCONFIGURED }.groupBy { it.group.lowercase() }
        for (group in snapshot.groups.sortedWith(NaturalOrder)) {
            val members = byGroup[group.lowercase()] ?: continue
            player.sendMessage(color("&e$group &8(${members.size}): " + members.joinToString("&7, ") { "${it.state.color}${it.name}" }))
        }
        val notSetUp = snapshot.arenas.filter { it.state == ArenaState.UNCONFIGURED }
        if (notSetUp.isNotEmpty()) {
            player.sendMessage(color("&7Not set up &8(${notSetUp.size}): &7" + notSetUp.joinToString(", ") { it.name }))
        }
    }

    fun sendGroupList(player: Player) {
        val snapshot = repo.snapshot(force = true)
        plugin.msg(player, "&7Groups &8(${snapshot.groups.size})&7:")
        for (group in snapshot.groups) {
            val members = snapshot.inGroup(group)
            val enabled = members.count { it.state == ArenaState.ENABLED }
            player.sendMessage(color("  &e$group &8- &f${members.size} map(s)&7, &a$enabled enabled"))
        }
    }

    fun sendCommandHelp(player: Player) {
        val lines = listOf(
            "&bArena management commands",
            "&e/bw setupArena &7- map manager (left-click set up, right-click options)",
            "&e/bw arenaGroup &7- group manager (create, remove, list, assign)",
            "&e/bw arenaList &7- list every map by group and state",
            "&e/bw gui [group] &7- the join menu players see",
            "&7Typing these with arguments (e.g. &e/bw setupArena MyMap&7) runs plain BedWars1058."
        )
        lines.forEach { player.sendMessage(color(it)) }
    }

    private fun color(text: String) = dev.etran.bedwars1058.arenamanagement.util.Text.color(text)
}
