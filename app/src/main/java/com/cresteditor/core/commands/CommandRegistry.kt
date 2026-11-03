package com.cresteditor.core.commands

/**
 * VS Code Command representation.
 */
data class CommandItem(
    val id: String,
    val title: String,            // e.g. "Preferences: Open Settings (UI)"
    val category: String,         // e.g. "Preferences", "View", "File", "Editor"
    val shortcut: String? = null, // e.g. "Ctrl+,", "Ctrl+S", "Ctrl+`"
    val action: () -> Unit
)

sealed interface CommandPaletteEntry {
    data class Header(val title: String) : CommandPaletteEntry
    data class Item(
        val command: CommandItem,
        val matchIndices: List<Int> = emptyList()
    ) : CommandPaletteEntry
}

class CommandRegistry {
    private val commands = mutableListOf<CommandItem>()
    private val recentCommandIds = mutableListOf<String>()

    fun register(command: CommandItem) {
        commands.removeAll { it.id == command.id }
        commands.add(command)
    }

    fun registerAll(list: List<CommandItem>) {
        list.forEach { register(it) }
    }

    fun clear() {
        commands.clear()
    }

    fun execute(command: CommandItem) {
        recentCommandIds.remove(command.id)
        recentCommandIds.add(0, command.id)
        if (recentCommandIds.size > 8) {
            recentCommandIds.removeAt(recentCommandIds.size - 1)
        }
        command.action()
    }

    /**
     * Filters and groups commands like VS Code:
     * - If query is blank: shows "recently used" first, then "other commands".
     * - If query is present: fuzzy matches on title and category, highlighting matched chars.
     */
    fun getPaletteEntries(query: String): List<CommandPaletteEntry> {
        val trimmed = query.trim().removePrefix(">").trim()
        val result = mutableListOf<CommandPaletteEntry>()

        if (trimmed.isEmpty()) {
            val recentList = recentCommandIds.mapNotNull { id ->
                commands.find { it.id == id }
            }
            if (recentList.isNotEmpty()) {
                result.add(CommandPaletteEntry.Header("recently used"))
                recentList.forEach { cmd ->
                    result.add(CommandPaletteEntry.Item(command = cmd))
                }
            }

            val otherList = commands.filter { cmd -> !recentCommandIds.contains(cmd.id) }
                .sortedBy { it.title }
            if (otherList.isNotEmpty()) {
                if (recentList.isNotEmpty()) {
                    result.add(CommandPaletteEntry.Header("other commands"))
                }
                otherList.forEach { cmd ->
                    result.add(CommandPaletteEntry.Item(command = cmd))
                }
            }
            return result
        }

        // Fuzzy filter query
        data class ScoredMatch(
            val command: CommandItem,
            val score: Int,
            val matchIndices: List<Int>
        )

        val matches = mutableListOf<ScoredMatch>()
        for (cmd in commands) {
            val titleLower = cmd.title.lowercase()
            val queryLower = trimmed.lowercase()

            // 1. Direct substring check
            val directIndex = titleLower.indexOf(queryLower)
            if (directIndex >= 0) {
                val indices = (directIndex until directIndex + queryLower.length).toList()
                // Higher score for starting at boundary or start of word
                val score = 1000 - directIndex
                matches.add(ScoredMatch(cmd, score, indices))
                continue
            }

            // 2. Fuzzy sequential character matching
            var qIdx = 0
            val indices = mutableListOf<Int>()
            for (tIdx in titleLower.indices) {
                if (qIdx < queryLower.length && titleLower[tIdx] == queryLower[qIdx]) {
                    indices.add(tIdx)
                    qIdx++
                }
            }

            if (qIdx == queryLower.length) {
                // All characters matched sequentially
                val score = 500 - (indices.last() - indices.first())
                matches.add(ScoredMatch(cmd, score, indices))
            }
        }

        matches.sortByDescending { it.score }
        matches.forEach { match ->
            result.add(CommandPaletteEntry.Item(command = match.command, matchIndices = match.matchIndices))
        }

        return result
    }
}
