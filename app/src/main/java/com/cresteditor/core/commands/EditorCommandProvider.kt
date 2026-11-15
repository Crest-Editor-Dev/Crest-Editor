package com.cresteditor.core.commands

import android.content.Context
import android.webkit.WebView
import androidx.compose.material3.DrawerState
import com.cresteditor.editor.MonacoBridge
import com.cresteditor.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Factory provider for registering built-in workbench, file, and editor commands.
 */
object EditorCommandProvider {

    fun buildCommands(
        context: Context,
        viewModel: MainViewModel,
        getWebView: () -> WebView?,
        drawerState: DrawerState,
        coroutineScope: CoroutineScope,
        onToggleCommandPalette: (prefix: String) -> Unit,
        onReloadWindow: () -> Unit
    ): List<CommandItem> {
        return listOf(
            // -----------------------------------------------------------------
            // 1. FILE COMMANDS
            // -----------------------------------------------------------------
            CommandItem(
                id = "workbench.action.files.save",
                title = "File: Save",
                category = "File",
                shortcut = "Ctrl+S"
            ) {
                viewModel.requestSave(context, getWebView())
            },
            CommandItem(
                id = "workbench.action.files.newFile",
                title = "File: New File",
                category = "File"
            ) {
                coroutineScope.launch {
                    if (drawerState.isClosed) drawerState.open()
                }
            },
            CommandItem(
                id = "workbench.action.files.newFolder",
                title = "File: New Folder",
                category = "File"
            ) {
                coroutineScope.launch {
                    if (drawerState.isClosed) drawerState.open()
                }
            },
            CommandItem(
                id = "workbench.action.closeActiveEditor",
                title = "File: Close Active Tab",
                category = "File",
                shortcut = "Ctrl+W"
            ) {
                val currentTab = viewModel.activeTabId.value
                if (currentTab.isNotEmpty()) {
                    viewModel.closeTab(currentTab, getWebView())
                }
            },

            // -----------------------------------------------------------------
            // 2. VIEW / WORKBENCH COMMANDS
            // -----------------------------------------------------------------
            CommandItem(
                id = "workbench.action.toggleSidebar",
                title = "View: Toggle Primary Side Bar (Explorer)",
                category = "View",
                shortcut = "Ctrl+B"
            ) {
                coroutineScope.launch {
                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                }
            },
            CommandItem(
                id = "workbench.action.terminal.toggleTerminal",
                title = "View: Toggle Terminal",
                category = "View",
                shortcut = "Ctrl+`"
            ) {
                viewModel.setShowTerminal(!viewModel.showTerminal.value)
            },
            CommandItem(
                id = "workbench.action.openSettings",
                title = "Preferences: Open Settings (UI)",
                category = "Preferences",
                shortcut = "Ctrl+,"
            ) {
                viewModel.setShowSettings(true)
            },
            CommandItem(
                id = "workbench.action.reloadWindow",
                title = "Developer: Reload Window",
                category = "Developer"
            ) {
                onReloadWindow()
            },

            // -----------------------------------------------------------------
            // 3. CODE FORMATTING & COMMENTS
            // -----------------------------------------------------------------
            CommandItem(
                id = "editor.action.formatDocument",
                title = "Editor: Format Document",
                category = "Editor",
                shortcut = "Shift+Alt+F"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.formatDocument")
            },
            CommandItem(
                id = "editor.action.formatSelection",
                title = "Editor: Format Selection",
                category = "Editor"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.formatSelection")
            },
            CommandItem(
                id = "editor.action.commentLine",
                title = "Editor: Toggle Line Comment",
                category = "Editor",
                shortcut = "Ctrl+/"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.commentLine")
            },
            CommandItem(
                id = "editor.action.blockComment",
                title = "Editor: Toggle Block Comment",
                category = "Editor",
                shortcut = "Shift+Alt+A"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.blockComment")
            },
            CommandItem(
                id = "editor.action.indentLines",
                title = "Editor: Indent Line",
                category = "Editor",
                shortcut = "Ctrl+]"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.indentLines")
            },
            CommandItem(
                id = "editor.action.outdentLines",
                title = "Editor: Outdent Line",
                category = "Editor",
                shortcut = "Ctrl+["
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.outdentLines")
            },

            // -----------------------------------------------------------------
            // 4. LINE EDITING & MANIPULATION
            // -----------------------------------------------------------------
            CommandItem(
                id = "editor.action.copyLinesDownAction",
                title = "Editor: Duplicate Line Down",
                category = "Editor",
                shortcut = "Shift+Alt+Down"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.copyLinesDownAction")
            },
            CommandItem(
                id = "editor.action.copyLinesUpAction",
                title = "Editor: Duplicate Line Up",
                category = "Editor",
                shortcut = "Shift+Alt+Up"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.copyLinesUpAction")
            },
            CommandItem(
                id = "editor.action.moveLinesDownAction",
                title = "Editor: Move Line Down",
                category = "Editor",
                shortcut = "Alt+Down"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.moveLinesDownAction")
            },
            CommandItem(
                id = "editor.action.moveLinesUpAction",
                title = "Editor: Move Line Up",
                category = "Editor",
                shortcut = "Alt+Up"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.moveLinesUpAction")
            },
            CommandItem(
                id = "editor.action.deleteLines",
                title = "Editor: Delete Line",
                category = "Editor",
                shortcut = "Ctrl+Shift+K"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.deleteLines")
            },
            CommandItem(
                id = "editor.action.insertLineAfter",
                title = "Editor: Insert Line Below",
                category = "Editor",
                shortcut = "Ctrl+Enter"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.insertLineAfter")
            },
            CommandItem(
                id = "editor.action.insertLineBefore",
                title = "Editor: Insert Line Above",
                category = "Editor",
                shortcut = "Ctrl+Shift+Enter"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.insertLineBefore")
            },
            CommandItem(
                id = "editor.action.joinLines",
                title = "Editor: Join Lines",
                category = "Editor"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.joinLines")
            },
            CommandItem(
                id = "editor.action.removeDuplicateLines",
                title = "Editor: Delete Duplicate Lines",
                category = "Editor"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.removeDuplicateLines")
            },
            CommandItem(
                id = "editor.action.trimTrailingWhitespace",
                title = "Editor: Trim Trailing Whitespace",
                category = "Editor"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.trimTrailingWhitespace")
            },
            CommandItem(
                id = "editor.action.sortLinesAscending",
                title = "Editor: Sort Lines Ascending",
                category = "Editor"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.sortLinesAscending")
            },
            CommandItem(
                id = "editor.action.sortLinesDescending",
                title = "Editor: Sort Lines Descending",
                category = "Editor"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.sortLinesDescending")
            },

            // -----------------------------------------------------------------
            // 5. TEXT & CASE TRANSFORMATIONS
            // -----------------------------------------------------------------
            CommandItem(
                id = "editor.action.transformToUppercase",
                title = "Editor: Transform to Uppercase",
                category = "Editor"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.transformToUppercase")
            },
            CommandItem(
                id = "editor.action.transformToLowercase",
                title = "Editor: Transform to Lowercase",
                category = "Editor"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.transformToLowercase")
            },
            CommandItem(
                id = "editor.action.transformToTitlecase",
                title = "Editor: Transform to Title Case",
                category = "Editor"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.transformToTitlecase")
            },
            CommandItem(
                id = "editor.action.transformToCamelcase",
                title = "Editor: Transform to Camel Case",
                category = "Editor"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.transformToCamelcase")
            },
            CommandItem(
                id = "editor.action.transformToSnakecase",
                title = "Editor: Transform to Snake Case",
                category = "Editor"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.transformToSnakecase")
            },
            CommandItem(
                id = "editor.action.transformToKebabcase",
                title = "Editor: Transform to Kebab Case",
                category = "Editor"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.transformToKebabcase")
            },

            // -----------------------------------------------------------------
            // 6. SEARCH, NAVIGATION, MULTI-CURSOR & FOLDING
            // -----------------------------------------------------------------
            CommandItem(
                id = "actions.find",
                title = "Editor: Find",
                category = "Editor",
                shortcut = "Ctrl+F"
            ) {
                MonacoBridge.triggerAction(getWebView(), "actions.find")
            },
            CommandItem(
                id = "editor.action.selectAll",
                title = "Editor: Select All",
                category = "Editor",
                shortcut = "Ctrl+A"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.selectAll")
            },
            CommandItem(
                id = "editor.action.addSelectionToNextFindMatch",
                title = "Editor: Add Selection to Next Match",
                category = "Editor",
                shortcut = "Ctrl+D"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.addSelectionToNextFindMatch")
            },
            CommandItem(
                id = "editor.action.changeAll",
                title = "Editor: Change All Occurrences",
                category = "Editor",
                shortcut = "Ctrl+F2"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.changeAll")
            },
            CommandItem(
                id = "workbench.action.quickOpen",
                title = "File: Go to File...",
                category = "File",
                shortcut = "Ctrl+P"
            ) {
                onToggleCommandPalette("")
            },
            CommandItem(
                id = "editor.action.gotoLine",
                title = "Editor: Go to Line...",
                category = "Editor",
                shortcut = "Ctrl+G"
            ) {
                onToggleCommandPalette(":")
            },
            CommandItem(
                id = "editor.action.jumpToBracket",
                title = "Editor: Go to Matching Bracket",
                category = "Editor",
                shortcut = "Ctrl+Shift+\\"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.action.jumpToBracket")
            },
            CommandItem(
                id = "editor.action.foldAll",
                title = "Editor: Fold All",
                category = "Editor",
                shortcut = "Ctrl+K Ctrl+0"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.foldAll")
            },
            CommandItem(
                id = "editor.action.unfoldAll",
                title = "Editor: Unfold All",
                category = "Editor",
                shortcut = "Ctrl+K Ctrl+J"
            ) {
                MonacoBridge.triggerAction(getWebView(), "editor.unfoldAll")
            },
            CommandItem(
                id = "undo",
                title = "Editor: Undo",
                category = "Editor",
                shortcut = "Ctrl+Z"
            ) {
                MonacoBridge.undo(getWebView())
            },
            CommandItem(
                id = "redo",
                title = "Editor: Redo",
                category = "Editor",
                shortcut = "Ctrl+Y"
            ) {
                MonacoBridge.redo(getWebView())
            }
        )
    }
}
