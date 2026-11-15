package com.cresteditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.cresteditor.core.model.FileTypeRegistry
import com.cresteditor.ui.components.drawer.*
import java.io.File

enum class SideBarView {
    EXPLORER,
    SEARCH,
    SOURCE_CONTROL
}

data class FileTreeItem(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val level: Int = 0,
    val path: String = id
)

/**
 * Helper to get extension-based file icons and colors (Seti theme style).
 * Preserved for internal compatibility with VSCodeCommandPalette and drawer panes.
 */
internal fun getFileIcon(filename: String): Pair<ImageVector, Color> = FileTypeRegistry.getFileIcon(filename)

/**
 * Pixel-perfect VS Code Left Side Bar for Crest-Editor.
 * Modular container composing:
 * 1. Activity Bar (48dp dock on left)
 * 2. Primary Side Bar Pane (Explorer, Search, or Source Control)
 */
@Composable
fun CrestFileDrawer(
    projectName: String = "Crest Project",
    fileList: List<FileTreeItem> = emptyList(),
    activeTabId: String = "",
    workspaceDir: File? = null,
    onFileSelect: (FileTreeItem) -> Unit = {},
    onCreateItem: (parentDir: File?, name: String, isFolder: Boolean) -> Unit = { _, _, _ -> },
    onRenameItem: (item: FileTreeItem, newName: String) -> Unit = { _, _ -> },
    onRefreshClick: () -> Unit = {},
    onDeleteClick: (FileTreeItem) -> Unit = {},
    onTerminalClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onCloseDrawer: () -> Unit = {}
) {
    var activeView by remember { mutableStateOf(SideBarView.EXPLORER) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(DrawerTheme.SideBarBg)
    ) {
        // =====================================================================
        // 1. ACTIVITY BAR (56dp Width - Mobile Touch Friendly Left Dock)
        // =====================================================================
        ActivityBar(
            activeView = activeView,
            onViewSelect = { view ->
                focusManager.clearFocus()
                keyboardController?.hide()
                activeView = view
            },
            onTerminalClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                onTerminalClick()
            },
            onSettingsClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                onSettingsClick()
            }
        )

        // =====================================================================
        // 2. PRIMARY SIDE BAR PANE (~262dp Width)
        // =====================================================================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            when (activeView) {
                SideBarView.EXPLORER -> {
                    ExplorerPane(
                        projectName = projectName,
                        fileList = fileList,
                        activeTabId = activeTabId,
                        workspaceDir = workspaceDir,
                        onFileSelect = onFileSelect,
                        onCreateItem = onCreateItem,
                        onRenameItem = onRenameItem,
                        onRefreshClick = onRefreshClick,
                        onDeleteClick = onDeleteClick,
                        onTerminalClick = onTerminalClick
                    )
                }
                SideBarView.SEARCH -> {
                    SearchPane(
                        fileList = fileList,
                        onFileSelect = onFileSelect
                    )
                }
                SideBarView.SOURCE_CONTROL -> {
                    SourceControlPane()
                }
            }
        }
    }
}
