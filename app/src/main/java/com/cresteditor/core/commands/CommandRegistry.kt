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

/**
 * Central registry for editor and workbench commands.
 * Tracks registered commands and recently executed command history.
 */
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

    fun getCommands(): List<CommandItem> = commands.toList()
    fun getRecentCommandIds(): List<String> = recentCommandIds.toList()

    fun execute(command: CommandItem) {
        recentCommandIds.remove(command.id)
        recentCommandIds.add(0, command.id)
        if (recentCommandIds.size > 8) {
            recentCommandIds.removeAt(recentCommandIds.size - 1)
        }
        command.action()
    }
}
