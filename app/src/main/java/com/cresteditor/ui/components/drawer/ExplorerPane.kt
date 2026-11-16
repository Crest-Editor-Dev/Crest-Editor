package com.cresteditor.ui.components.drawer

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cresteditor.core.model.FileTypeRegistry
import com.cresteditor.core.platform.ClipboardHelper
import com.cresteditor.icons.Codicons
import com.cresteditor.ui.components.FileTreeItem
import com.cresteditor.ui.components.VSCodeDropdownMenu
import com.cresteditor.ui.components.VSCodeMenuItem
import com.cresteditor.ui.components.VSCodeMenuSeparator
import java.io.File

sealed interface InlineOpState {
    data class Create(val isFolder: Boolean, val parentPath: String? = null) : InlineOpState
    data class Rename(val item: FileTreeItem) : InlineOpState
}

sealed interface ExplorerRowItem {
    data class Existing(val item: FileTreeItem) : ExplorerRowItem
    data class InlineCreate(val isFolder: Boolean, val level: Int, val parentPath: String?) : ExplorerRowItem
    data class InlineRename(val item: FileTreeItem) : ExplorerRowItem
}

/**
 * VS Code Primary Explorer Pane.
 * Handles:
 * - Upper Explorer section title.
 * - Collapsible root workspace project header with action buttons (New File, New Folder, Refresh, Collapse All).
 * - Full interactive tree list with VS Code Seti icons and indent guide lines.
 * - Inline file / folder creation and rename rows.
 * - Context menus for individual tree items and empty workspace background.
 */
@Composable
fun ExplorerPane(
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
    modifier: Modifier = Modifier
) {
    var isProjectCollapsed by remember { mutableStateOf(false) }
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }
    var activeFolderTreePath by remember { mutableStateOf<String?>(null) }
    var contextMenuItem by remember { mutableStateOf<FileTreeItem?>(null) }
    var showEmptyAreaMenu by remember { mutableStateOf(false) }
    var inlineOpState by remember { mutableStateOf<InlineOpState?>(null) }
    val density = LocalDensity.current
    var touchOffset by remember { mutableStateOf(DpOffset.Zero) }
    var emptyAreaOffset by remember { mutableStateOf(DpOffset.Zero) }
    val context = LocalContext.current

    fun startInlineCreation(isFolder: Boolean, parentPath: String? = null) {
        if (parentPath != null) {
            expandedPaths = expandedPaths + parentPath
            activeFolderTreePath = parentPath
        }
        inlineOpState = InlineOpState.Create(isFolder, parentPath)
    }

    fun startInlineRename(item: FileTreeItem) {
        inlineOpState = InlineOpState.Rename(item)
    }

    // Sync active tree with active file tab if no tree was explicitly focused yet
    LaunchedEffect(activeTabId, fileList) {
        if (activeFolderTreePath == null && activeTabId.isNotEmpty()) {
            val parentPath = activeTabId.substringBeforeLast(File.separatorChar, "")
            if (parentPath.isNotEmpty() && expandedPaths.contains(parentPath) && fileList.any { it.path == parentPath && it.isDirectory }) {
                activeFolderTreePath = parentPath
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DrawerTheme.SideBarBg)
    ) {
        // ---------------------------------------------------------
        // 1. Explorer Header (Clean VS Code title bar)
        // ---------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EXPLORER",
                color = DrawerTheme.SideBarTitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        // ---------------------------------------------------------
        // 2. Collapsible Root Project Accordion Bar (with Action Buttons)
        // ---------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(DrawerTheme.SectionHeaderBg)
                .padding(start = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { isProjectCollapsed = !isProjectCollapsed },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isProjectCollapsed) Codicons.ChevronRight else Codicons.ChevronDown,
                    contentDescription = null,
                    tint = DrawerTheme.SectionHeaderFg,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = projectName.uppercase(),
                    color = DrawerTheme.SectionHeaderFg,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Action Buttons on Root Workspace Bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                // New File
                IconButton(
                    onClick = { startInlineCreation(isFolder = false, parentPath = null) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Codicons.NewFile,
                        contentDescription = "New File",
                        tint = DrawerTheme.TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // New Folder
                IconButton(
                    onClick = { startInlineCreation(isFolder = true, parentPath = null) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Codicons.NewFolder,
                        contentDescription = "New Folder",
                        tint = DrawerTheme.TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Refresh
                IconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Codicons.Refresh,
                        contentDescription = "Refresh Explorer",
                        tint = DrawerTheme.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Collapse All
                IconButton(
                    onClick = {
                        expandedPaths = emptySet()
                        activeFolderTreePath = null
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Codicons.CollapseAll,
                        contentDescription = "Collapse Folders",
                        tint = DrawerTheme.TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // ---------------------------------------------------------
        // 3. File Tree List
        // ---------------------------------------------------------
        if (!isProjectCollapsed) {
            val visibleFiles = remember(fileList, expandedPaths) {
                if (fileList.isEmpty()) {
                    emptyList()
                } else if (expandedPaths.isEmpty()) {
                    fileList.filter { it.level == 0 }
                } else {
                    val result = ArrayList<FileTreeItem>(fileList.size.coerceAtMost(256))
                    var skipLevel = Int.MAX_VALUE
                    for (item in fileList) {
                        if (item.level <= skipLevel) {
                            skipLevel = Int.MAX_VALUE
                            result.add(item)
                            if (item.isDirectory && !expandedPaths.contains(item.path)) {
                                skipLevel = item.level
                            }
                        }
                    }
                    result
                }
            }

            val activeFolderItem = remember(activeFolderTreePath, visibleFiles, expandedPaths) {
                if (activeFolderTreePath == null) null
                else {
                    val folder = visibleFiles.find { it.path == activeFolderTreePath && it.isDirectory }
                    if (folder != null && expandedPaths.contains(folder.path)) folder else null
                }
            }

            val activeFolderPrefix = remember(activeFolderItem) {
                activeFolderItem?.let {
                    if (it.path.endsWith(File.separator)) it.path else "${it.path}${File.separator}"
                }
            }

            val explorerRowItems = remember(visibleFiles, inlineOpState) {
                val result = mutableListOf<ExplorerRowItem>()
                val op = inlineOpState
                if (op == null) {
                    visibleFiles.mapTo(result) { ExplorerRowItem.Existing(it) }
                } else {
                    when (op) {
                        is InlineOpState.Rename -> {
                            for (item in visibleFiles) {
                                if (item.path == op.item.path) {
                                    result.add(ExplorerRowItem.InlineRename(op.item))
                                } else {
                                    result.add(ExplorerRowItem.Existing(item))
                                }
                            }
                        }
                        is InlineOpState.Create -> {
                            val insertIndex: Int
                            val childLevel: Int
                            if (op.parentPath == null) {
                                childLevel = 0
                                if (op.isFolder) {
                                    insertIndex = 0
                                } else {
                                    val firstRootFileIdx = visibleFiles.indexOfFirst { it.level == 0 && !it.isDirectory }
                                    insertIndex = if (firstRootFileIdx != -1) firstRootFileIdx else visibleFiles.size
                                }
                            } else {
                                val parentIdx = visibleFiles.indexOfFirst { it.path == op.parentPath }
                                if (parentIdx != -1) {
                                    val parentItem = visibleFiles[parentIdx]
                                    childLevel = parentItem.level + 1
                                    val parentPrefix = "${op.parentPath}${File.separator}"
                                    if (op.isFolder) {
                                        insertIndex = parentIdx + 1
                                    } else {
                                        val firstChildFileIdx = visibleFiles.indexOfFirst {
                                            it.level == childLevel && !it.isDirectory && it.path.startsWith(parentPrefix)
                                        }
                                        if (firstChildFileIdx != -1) {
                                            insertIndex = firstChildFileIdx
                                        } else {
                                            var lastDescendant = parentIdx
                                            for (i in (parentIdx + 1) until visibleFiles.size) {
                                                if (visibleFiles[i].path.startsWith(parentPrefix)) {
                                                    lastDescendant = i
                                                } else {
                                                    break
                                                }
                                            }
                                            insertIndex = lastDescendant + 1
                                        }
                                    }
                                } else {
                                    childLevel = 0
                                    insertIndex = 0
                                }
                            }
                            val clampedIdx = insertIndex.coerceIn(0, visibleFiles.size)
                            for (i in 0 until clampedIdx) {
                                result.add(ExplorerRowItem.Existing(visibleFiles[i]))
                            }
                            result.add(ExplorerRowItem.InlineCreate(isFolder = op.isFolder, level = childLevel, parentPath = op.parentPath))
                            for (i in clampedIdx until visibleFiles.size) {
                                result.add(ExplorerRowItem.Existing(visibleFiles[i]))
                            }
                        }
                    }
                }
                result
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 2.dp)
            ) {
                items(explorerRowItems, key = { row ->
                    when (row) {
                        is ExplorerRowItem.Existing -> row.item.path
                        is ExplorerRowItem.InlineCreate -> "__inline_create__"
                        is ExplorerRowItem.InlineRename -> "__inline_rename_${row.item.path}__"
                    }
                }) { row ->
                    when (row) {
                        is ExplorerRowItem.InlineCreate -> {
                            val targetDir = if (row.parentPath != null) File(row.parentPath) else workspaceDir
                            InlineTreeInputRow(
                                initialText = "",
                                isFolder = row.isFolder,
                                level = row.level,
                                targetDir = targetDir,
                                isRename = false,
                                onCommit = { name ->
                                    onCreateItem(targetDir, name, row.isFolder)
                                    inlineOpState = null
                                },
                                onCancel = {
                                    inlineOpState = null
                                }
                            )
                        }
                        is ExplorerRowItem.InlineRename -> {
                            val targetDir = File(row.item.path).parentFile
                            InlineTreeInputRow(
                                initialText = row.item.name,
                                isFolder = row.item.isDirectory,
                                level = row.item.level,
                                targetDir = targetDir,
                                isRename = true,
                                onCommit = { newName ->
                                    onRenameItem(row.item, newName)
                                    inlineOpState = null
                                },
                                onCancel = {
                                    inlineOpState = null
                                }
                            )
                        }
                        is ExplorerRowItem.Existing -> {
                            val item = row.item
                            val isFolder = item.isDirectory
                            val isExpanded = expandedPaths.contains(item.path)
                            val isActive = !isFolder && item.path == activeTabId

                            val isInsideActiveTree = activeFolderPrefix != null && item.path.startsWith(activeFolderPrefix)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .background(if (isActive) DrawerTheme.ListSelectionBg else Color.Transparent)
                                    .pointerInput(item.path, isExpanded, inlineOpState) {
                                        detectTapGestures(
                                            onTap = {
                                                if (inlineOpState != null) {
                                                    inlineOpState = null
                                                    return@detectTapGestures
                                                }
                                                if (isFolder) {
                                                    if (expandedPaths.contains(item.path)) {
                                                        expandedPaths = expandedPaths - item.path
                                                        if (activeFolderTreePath == item.path || (activeFolderTreePath != null && activeFolderTreePath!!.startsWith("${item.path}${File.separator}"))) {
                                                            val parentPath = item.path.substringBeforeLast(File.separatorChar, "")
                                                            val isParentExpanded = parentPath.isNotEmpty() && expandedPaths.contains(parentPath)
                                                            activeFolderTreePath = if (isParentExpanded) parentPath else null
                                                        }
                                                    } else {
                                                        expandedPaths = expandedPaths + item.path
                                                        activeFolderTreePath = item.path
                                                    }
                                                } else {
                                                    onFileSelect(item)
                                                    val parentPath = item.path.substringBeforeLast(File.separatorChar, "")
                                                    if (parentPath.isNotEmpty() && expandedPaths.contains(parentPath)) {
                                                        activeFolderTreePath = parentPath
                                                    }
                                                }
                                            },
                                            onLongPress = { offset ->
                                                if (inlineOpState != null) {
                                                    inlineOpState = null
                                                }
                                                touchOffset = DpOffset(
                                                    x = with(density) { offset.x.toDp() },
                                                    y = with(density) { offset.y.toDp() }
                                                )
                                                contextMenuItem = item
                                            }
                                        )
                                    }
                            ) {
                                // 1. VS Code Active Indent Guide Line
                                if (isInsideActiveTree && activeFolderItem != null) {
                                    val guideX = (activeFolderItem.level * 14 + 17).dp
                                    Box(
                                        modifier = Modifier
                                            .padding(start = guideX)
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .background(DrawerTheme.TreeIndentGuideActive)
                                    )
                                }

                                // 2. Tree Item Content Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = (item.level * 14 + 8).dp, end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(18.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isFolder) {
                                            Icon(
                                                imageVector = if (isExpanded) Codicons.ChevronDown else Codicons.ChevronRight,
                                                contentDescription = null,
                                                tint = DrawerTheme.ActivityBarInactive,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        } else {
                                            val iconData = FileTypeRegistry.getFileIcon(item.name)
                                            Icon(
                                                imageVector = iconData.first,
                                                contentDescription = null,
                                                tint = iconData.second,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Name
                                    Text(
                                        text = item.name,
                                        color = if (isActive) DrawerTheme.TextActive else DrawerTheme.TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    // Mobile touch context menu action
                                    IconButton(
                                        onClick = {
                                            touchOffset = DpOffset(
                                                x = 170.dp,
                                                y = 28.dp
                                            )
                                            contextMenuItem = item
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More actions",
                                            tint = if (isActive) DrawerTheme.TextActive.copy(alpha = 0.7f) else DrawerTheme.ActivityBarInactive.copy(alpha = 0.45f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Empty / Blank Area of Explorer (VS Code Root Context Menu)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 400.dp)
                            .pointerInput(inlineOpState) {
                                detectTapGestures(
                                    onTap = {
                                        if (inlineOpState != null) {
                                            inlineOpState = null
                                        }
                                    },
                                    onLongPress = { offset ->
                                        if (inlineOpState != null) {
                                            inlineOpState = null
                                        }
                                        emptyAreaOffset = DpOffset(
                                            x = with(density) { offset.x.toDp() },
                                            y = with(density) { offset.y.toDp() }
                                        )
                                        showEmptyAreaMenu = true
                                    }
                                )
                            }
                    ) {
                        if (showEmptyAreaMenu) {
                            VSCodeDropdownMenu(
                                expanded = true,
                                offset = emptyAreaOffset,
                                onDismissRequest = { showEmptyAreaMenu = false }
                            ) {
                                VSCodeMenuItem(
                                    text = "New File...",
                                    onClick = {
                                        showEmptyAreaMenu = false
                                        startInlineCreation(isFolder = false, parentPath = null)
                                    }
                                )
                                VSCodeMenuItem(
                                    text = "New Folder...",
                                    onClick = {
                                        showEmptyAreaMenu = false
                                        startInlineCreation(isFolder = true, parentPath = null)
                                    }
                                )
                                VSCodeMenuSeparator()
                                VSCodeMenuItem(
                                    text = "Paste",
                                    shortcut = "Ctrl+V",
                                    enabled = false,
                                    onClick = { showEmptyAreaMenu = false }
                                )
                                VSCodeMenuSeparator()
                                VSCodeMenuItem(
                                    text = "Copy Path",
                                    onClick = {
                                        showEmptyAreaMenu = false
                                        ClipboardHelper.copyText(context, workspaceDir?.absolutePath ?: "", "path")
                                        Toast.makeText(context, "Workspace path copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                VSCodeMenuSeparator()
                                VSCodeMenuItem(
                                    text = "Open in Integrated Terminal",
                                    onClick = {
                                        showEmptyAreaMenu = false
                                        onTerminalClick()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // VS Code Standard Context Menu for Item
            if (contextMenuItem != null) {
                val item = contextMenuItem!!
                VSCodeDropdownMenu(
                    expanded = true,
                    offset = touchOffset,
                    onDismissRequest = { contextMenuItem = null }
                ) {
                    if (item.isDirectory) {
                        VSCodeMenuItem(
                            text = "New File...",
                            onClick = {
                                contextMenuItem = null
                                startInlineCreation(isFolder = false, parentPath = item.path)
                            }
                        )
                        VSCodeMenuItem(
                            text = "New Folder...",
                            onClick = {
                                contextMenuItem = null
                                startInlineCreation(isFolder = true, parentPath = item.path)
                            }
                        )
                        VSCodeMenuSeparator()
                    }

                    VSCodeMenuItem(
                        text = "Cut",
                        shortcut = "Ctrl+X",
                        enabled = false,
                        onClick = { contextMenuItem = null }
                    )
                    VSCodeMenuItem(
                        text = "Copy",
                        shortcut = "Ctrl+C",
                        enabled = false,
                        onClick = { contextMenuItem = null }
                    )
                    VSCodeMenuItem(
                        text = "Paste",
                        shortcut = "Ctrl+V",
                        enabled = false,
                        onClick = { contextMenuItem = null }
                    )

                    VSCodeMenuSeparator()

                    VSCodeMenuItem(
                        text = "Copy Path",
                        onClick = {
                            contextMenuItem = null
                            ClipboardHelper.copyText(context, item.path, "path")
                            Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                    VSCodeMenuItem(
                        text = "Copy Relative Path",
                        onClick = {
                            contextMenuItem = null
                            val relPath = if (workspaceDir != null) {
                                try {
                                    File(item.path).relativeTo(workspaceDir).path
                                } catch (_: Exception) {
                                    item.name
                                }
                            } else item.name
                            ClipboardHelper.copyText(context, relPath, "relative_path")
                            Toast.makeText(context, "Relative path copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )

                    VSCodeMenuSeparator()

                    VSCodeMenuItem(
                        text = "Rename...",
                        shortcut = "F2",
                        onClick = {
                            contextMenuItem = null
                            startInlineRename(item)
                        }
                    )
                    VSCodeMenuItem(
                        text = "Delete",
                        shortcut = "Del",
                        isDestructive = true,
                        onClick = {
                            contextMenuItem = null
                            onDeleteClick(item)
                        }
                    )
                }
            }
        }
    }
}
