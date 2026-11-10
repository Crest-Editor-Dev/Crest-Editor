package com.cresteditor.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.cresteditor.icons.Codicons
import com.cresteditor.ui.theme.*
import com.cresteditor.workspace.WorkspaceManager
import java.io.File

// =============================================================================
// VS CODE THEME CONSTANTS (Activity Bar & Primary Side Bar)
// =============================================================================
private val VSCodeActivityBarBg = Color(0xFF181818)          // activityBar.background (Modern dark)
private val VSCodeActivityBarFg = Color(0xFFFFFFFF)          // activityBar.foreground
private val VSCodeActivityBarInactive = Color(0xFF858585)    // activityBar.inactiveForeground
private val VSCodeActivityBarIndicator = Color(0xFFFFFFFF)   // activityBar.activeBorder (white strip)

private val VSCodeSideBarBg = Color(0xFF252526)              // sideBar.background
private val VSCodeSideBarTitle = Color(0xFFBBBBBB)           // sideBarTitle.foreground
private val VSCodeSideBarBorder = Color(0xFF1E1E1E)          // sideBar.border
private val VSCodeSectionHeaderBg = Color(0x33808080)        // sideBarSectionHeader.background
private val VSCodeSectionHeaderFg = Color(0xFFCCCCCC)        // sideBarSectionHeader.foreground

private val VSCodeListSelectionBg = Color(0xFF04395E)        // list.activeSelectionBackground
private val VSCodeListHoverBg = Color(0xFF2A2D2E)            // list.hoverBackground
private val VSCodeTextPrimary = Color(0xFFCCCCCC)            // editor.foreground
private val VSCodeTextActive = Color(0xFFFFFFFF)
private val VSCodeInputBg = Color(0xFF313131)
private val VSCodeInputBorder = Color(0xFF3C3C3C)
private val VSCodeFocusBorder = Color(0xFF007ACC)
private val VSCodeTreeIndentGuideActive = Color(0xFF6E6E6E) // tree.indentGuidesStroke (#585858 / #6E6E6E)

// File Extension Icon Colors
private val ColorFolder = Color(0xFFDCDCAA)
private val ColorKotlin = Color(0xFFFFA657)
private val ColorJavaScript = Color(0xFFF7DF1E)
private val ColorTypeScript = Color(0xFF3178C6)
private val ColorJson = Color(0xFFCBCB41)
private val ColorHtml = Color(0xFFE34C26)
private val ColorCss = Color(0xFF388BFD)
private val ColorMarkdown = Color(0xFF519ABA)
private val ColorXml = Color(0xFF4EC9B0)

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
 * Pixel-perfect VS Code Left Side Bar for Crest-Editor.
 * Features:
 * 1. Activity Bar (48dp left strip):
 *    - Explorer, Search, Source Control (Git), Terminal, Settings (Gear)
 *    - Active white vertical indicator bar on left edge
 * 2. Primary Side Bar (~250dp pane):
 *    - Uppercase section header ("EXPLORER") with action icons (New File, New Folder, Refresh, Collapse All)
 *    - Collapsible Workspace root folder header ("▼ WORKSPACE_NAME")
 *    - Tree view with collapsible folders (▶ / ▼) and file extension-colored icons
 *    - Active open file highlighted in VS Code blue selection background
 *    - In-place Search view & Source Control view
 */
sealed interface InlineOpState {
    data class Create(val isFolder: Boolean, val parentPath: String? = null) : InlineOpState
    data class Rename(val item: FileTreeItem) : InlineOpState
}

sealed interface ExplorerRowItem {
    data class Existing(val item: FileTreeItem) : ExplorerRowItem
    data class InlineCreate(val isFolder: Boolean, val level: Int, val parentPath: String?) : ExplorerRowItem
    data class InlineRename(val item: FileTreeItem) : ExplorerRowItem
}

@OptIn(ExperimentalFoundationApi::class)
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
    var isProjectCollapsed by remember { mutableStateOf(false) }
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }
    var activeFolderTreePath by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var contextMenuItem by remember { mutableStateOf<FileTreeItem?>(null) }
    var showEmptyAreaMenu by remember { mutableStateOf(false) }
    var inlineOpState by remember { mutableStateOf<InlineOpState?>(null) }
    val density = LocalDensity.current
    var touchOffset by remember { mutableStateOf(DpOffset.Zero) }
    var emptyAreaOffset by remember { mutableStateOf(DpOffset.Zero) }
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }

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
            val parentPath = java.io.File(activeTabId).parent
            if (parentPath != null && expandedPaths.contains(parentPath) && fileList.any { it.path == parentPath && it.isDirectory }) {
                activeFolderTreePath = parentPath
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(VSCodeSideBarBg)
    ) {
        // =====================================================================
        // 1. ACTIVITY BAR (48dp Width - Exact VS Code Left Dock)
        // =====================================================================
        Column(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .background(VSCodeActivityBarBg)
                .border(
                    width = 1.dp,
                    color = VSCodeSideBarBorder,
                    shape = androidx.compose.ui.graphics.RectangleShape
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Explorer Item
            ActivityBarItem(
                icon = Codicons.Files,
                contentDescription = "Explorer",
                isSelected = activeView == SideBarView.EXPLORER,
                onClick = { activeView = SideBarView.EXPLORER }
            )

            // Search Item
            ActivityBarItem(
                icon = Codicons.Search,
                contentDescription = "Search",
                isSelected = activeView == SideBarView.SEARCH,
                onClick = { activeView = SideBarView.SEARCH }
            )

            // Source Control (Git) Item
            ActivityBarItem(
                icon = Codicons.SourceControl,
                contentDescription = "Source Control",
                isSelected = activeView == SideBarView.SOURCE_CONTROL,
                onClick = { activeView = SideBarView.SOURCE_CONTROL }
            )

            // Terminal Launcher
            ActivityBarItem(
                icon = Codicons.Terminal,
                contentDescription = "Terminal",
                isSelected = false,
                onClick = onTerminalClick
            )

            Spacer(modifier = Modifier.weight(1f))

            // Settings (Gear) at bottom
            ActivityBarItem(
                icon = Codicons.SettingsGear,
                contentDescription = "Settings",
                isSelected = false,
                onClick = onSettingsClick
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // =====================================================================
        // 2. PRIMARY SIDE BAR PANE (~262dp Width)
        // =====================================================================
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(VSCodeSideBarBg)
        ) {
            when (activeView) {
                SideBarView.EXPLORER -> {
                    // ---------------------------------------------------------
                    // Explorer Header (Clean VS Code title bar)
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
                            color = VSCodeSideBarTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // ---------------------------------------------------------
                    // Collapsible Root Project Accordion Bar (with Action Buttons)
                    // ---------------------------------------------------------
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(VSCodeSectionHeaderBg)
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
                                tint = VSCodeSectionHeaderFg,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = projectName.uppercase(),
                                color = VSCodeSectionHeaderFg,
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
                                    tint = VSCodeTextPrimary,
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
                                    tint = VSCodeTextPrimary,
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
                                    tint = VSCodeTextPrimary,
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
                                    tint = VSCodeTextPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // File Tree List
                    // ---------------------------------------------------------
                    if (!isProjectCollapsed) {
                        val allDirPaths = remember(fileList) {
                            fileList.filter { it.isDirectory }.map { it.path }.toSet()
                        }

                        // By default, nested directories are collapsed.
                        // Root items (level == 0) are visible. Nested items require all ancestor directories to be in expandedPaths.
                        val visibleFiles = remember(fileList, expandedPaths, allDirPaths) {
                            fileList.filter { item ->
                                if (item.level == 0) {
                                    true
                                } else {
                                    var currentParent = java.io.File(item.path).parent
                                    var isVisible = true
                                    while (currentParent != null && allDirPaths.contains(currentParent)) {
                                        if (!expandedPaths.contains(currentParent)) {
                                            isVisible = false
                                            break
                                        }
                                        currentParent = java.io.File(currentParent).parent
                                    }
                                    isVisible
                                }
                            }
                        }

                        // Resolve currently active / latest open folder tree item
                        val activeFolderItem = remember(activeFolderTreePath, fileList, expandedPaths) {
                            if (activeFolderTreePath == null) null
                            else {
                                val folder = fileList.find { it.path == activeFolderTreePath && it.isDirectory }
                                if (folder != null && expandedPaths.contains(folder.path)) folder else null
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
                                                val parentPrefix = "${op.parentPath}${java.io.File.separator}"
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

                                        // VS Code Indent Guide: only show guide for descendants of the latest open folder tree
                                        val isInsideActiveTree = remember(activeFolderItem, item.path) {
                                            if (activeFolderItem == null) false
                                            else {
                                                val prefix = if (activeFolderItem.path.endsWith(java.io.File.separator)) {
                                                    activeFolderItem.path
                                                } else {
                                                    "${activeFolderItem.path}${java.io.File.separator}"
                                                }
                                                item.path.startsWith(prefix)
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp)
                                                .background(if (isActive) VSCodeListSelectionBg else Color.Transparent)
                                                .pointerInput(item.path, isExpanded, inlineOpState) {
                                                    detectTapGestures(
                                                        onTap = {
                                                            if (inlineOpState != null) {
                                                                inlineOpState = null
                                                                return@detectTapGestures
                                                            }
                                                            if (isFolder) {
                                                                if (expandedPaths.contains(item.path)) {
                                                                    // Collapsing folder: remove from expandedPaths
                                                                    expandedPaths = expandedPaths - item.path
                                                                    if (activeFolderTreePath == item.path || (activeFolderTreePath != null && activeFolderTreePath!!.startsWith("${item.path}${java.io.File.separator}"))) {
                                                                        val parentPath = java.io.File(item.path).parent
                                                                        val isParentExpanded = parentPath != null && expandedPaths.contains(parentPath)
                                                                        activeFolderTreePath = if (isParentExpanded) parentPath else null
                                                                    }
                                                                } else {
                                                                    // Expanding folder: add only this folder to expandedPaths
                                                                    expandedPaths = expandedPaths + item.path
                                                                    activeFolderTreePath = item.path
                                                                }
                                                            } else {
                                                                onFileSelect(item)
                                                                // When selecting a file, activate its parent folder tree guide
                                                                val parentPath = java.io.File(item.path).parent
                                                                if (parentPath != null && expandedPaths.contains(parentPath)) {
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
                                            // Exact 1dp vertical line aligned under parent folder's chevron center
                                            if (isInsideActiveTree && activeFolderItem != null) {
                                                val guideX = (activeFolderItem.level * 14 + 17).dp
                                                Box(
                                                    modifier = Modifier
                                                        .padding(start = guideX)
                                                        .width(1.dp)
                                                        .fillMaxHeight()
                                                        .background(VSCodeTreeIndentGuideActive)
                                                )
                                            }

                                            // 2. Tree Item Content Row
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(start = (item.level * 14 + 8).dp, end = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Exact VS Code Seti Style:
                                                // Sibling folder arrow and file icon start at the EXACT same position!
                                                // Children inside a folder are indented ahead by level * 14.dp
                                                Box(
                                                    modifier = Modifier.size(18.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isFolder) {
                                                        Icon(
                                                            imageVector = if (isExpanded) Codicons.ChevronDown else Codicons.ChevronRight,
                                                            contentDescription = null,
                                                            tint = VSCodeActivityBarInactive,
                                                            modifier = Modifier.size(17.dp)
                                                        )
                                                    } else {
                                                        val iconData = getFileIcon(item.name)
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
                                                    color = if (isActive) VSCodeTextActive else VSCodeTextPrimary,
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
                                                        tint = if (isActive) VSCodeTextActive.copy(alpha = 0.7f) else VSCodeActivityBarInactive.copy(alpha = 0.45f),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }

                                            // VS Code Standard Context Menu (No icons/emojis, consistent common component)
                                            if (contextMenuItem == item) {
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
                                                            clipboard?.setPrimaryClip(ClipData.newPlainText("path", item.path))
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
                                                            clipboard?.setPrimaryClip(ClipData.newPlainText("relative_path", relPath))
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
                            }

                            // 3. Empty / Blank Area of Explorer (VS Code Root Context Menu)
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
                                                    clipboard?.setPrimaryClip(ClipData.newPlainText("path", workspaceDir?.absolutePath ?: ""))
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
                    }
                }

                SideBarView.SEARCH -> {
                    // ---------------------------------------------------------
                    // Search Pane View
                    // ---------------------------------------------------------
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "SEARCH",
                            color = VSCodeSideBarTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        var isFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(VSCodeInputBg, RoundedCornerShape(2.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (isFocused) VSCodeFocusBorder else VSCodeInputBorder,
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = VSCodeActivityBarInactive,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search in files",
                                        color = VSCodeActivityBarInactive,
                                        fontSize = 12.5.sp
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isFocused = it.isFocused },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = VSCodeTextActive,
                                        fontSize = 12.5.sp
                                    ),
                                    cursorBrush = SolidColor(VSCodeFocusBorder)
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = VSCodeActivityBarInactive,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val filteredFiles = remember(searchQuery, fileList) {
                            if (searchQuery.isBlank()) emptyList()
                            else fileList.filter { !it.isDirectory && it.name.contains(searchQuery, ignoreCase = true) }
                        }

                        if (searchQuery.isNotBlank()) {
                            Text(
                                text = "${filteredFiles.size} results in files",
                                color = VSCodeActivityBarInactive,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredFiles) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onFileSelect(item) }
                                            .padding(vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val iconData = getFileIcon(item.name)
                                        Icon(
                                            imageVector = iconData.first,
                                            contentDescription = null,
                                            tint = iconData.second,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.name,
                                            color = VSCodeTextPrimary,
                                            fontSize = 13.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                SideBarView.SOURCE_CONTROL -> {
                    // ---------------------------------------------------------
                    // Source Control (Git) View
                    // ---------------------------------------------------------
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "SOURCE CONTROL",
                            color = VSCodeSideBarTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .background(VSCodeSectionHeaderBg)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = VSCodeSectionHeaderFg,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CHANGES",
                                color = VSCodeSectionHeaderFg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Workspace Git repository synced.",
                            color = VSCodeActivityBarInactive,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Activity Bar Icon with Left Indicator Line.
 */
@Composable
private fun ActivityBarItem(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Iconic VS Code 2dp vertical active indicator on the far left edge
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .background(VSCodeActivityBarIndicator)
                    .align(Alignment.CenterStart)
            )
        }

        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) VSCodeActivityBarFg else VSCodeActivityBarInactive,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Helper to get extension-based file icons and colors (Seti theme style).
 */
internal fun getFileIcon(filename: String): Pair<ImageVector, Color> {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts" -> Pair(Icons.Default.Code, ColorKotlin)
        "js", "jsx", "mjs" -> Pair(Icons.Default.Javascript, ColorJavaScript)
        "ts", "tsx" -> Pair(Icons.Default.Code, ColorTypeScript)
        "json" -> Pair(Icons.Default.DataObject, ColorJson)
        "html", "htm" -> Pair(Icons.Default.Html, ColorHtml)
        "css", "scss", "less" -> Pair(Icons.Default.Css, ColorCss)
        "md", "markdown" -> Pair(Icons.Default.Description, ColorMarkdown)
        "xml" -> Pair(Icons.Default.Code, ColorXml)
        "sh", "bash" -> Pair(Icons.Default.Terminal, ColorKotlin)
        else -> Pair(Icons.Default.InsertDriveFile, VSCodeTextPrimary)
    }
}

/**
 * Exact VS Code In-Place Tree Row for File / Folder Creation and Rename.
 * Features:
 * - Dynamic Seti file icon based on real-time typed extension (Chevron arrow for folders)
 * - 1px #007ACC focus border (#BE1100 on validation error)
 * - Auto-focus with software keyboard (IME Done = Commit)
 * - Pre-selected name excluding extension on rename
 * - VS Code Floating Error Tooltip (#5A1D1D) hovering over items below
 * - Clean UI with NO touch checkmark / cancel buttons
 */
@Composable
private fun InlineTreeInputRow(
    initialText: String = "",
    isFolder: Boolean,
    level: Int,
    targetDir: File?,
    isRename: Boolean = false,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit
) {
    val initialSelection = remember(initialText, isFolder) {
        if (initialText.isEmpty()) {
            TextRange(0, 0)
        } else if (isFolder) {
            TextRange(0, initialText.length)
        } else {
            val dotIndex = initialText.lastIndexOf('.')
            if (dotIndex > 0) {
                TextRange(0, dotIndex)
            } else {
                TextRange(0, initialText.length)
            }
        }
    }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = initialText, selection = initialSelection))
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = (level * 14 + 8).dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(18.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isFolder) {
                    Icon(
                        imageVector = Codicons.ChevronRight,
                        contentDescription = null,
                        tint = VSCodeActivityBarInactive,
                        modifier = Modifier.size(17.dp)
                    )
                } else {
                    val iconData = getFileIcon(textFieldValue.text)
                    Icon(
                        imageVector = iconData.first,
                        contentDescription = null,
                        tint = iconData.second,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            val borderColor = if (validationError != null) Color(0xFFBE1100) else Color(0xFF007ACC)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .background(Color(0xFF313131), RoundedCornerShape(2.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        val trimmed = newValue.text.trim()
                        validationError = if (trimmed.isNotEmpty() && targetDir != null) {
                            if (isRename && trimmed == initialText) {
                                null
                            } else {
                                WorkspaceManager.validateName(targetDir, trimmed)
                            }
                        } else null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Default
                    ),
                    cursorBrush = SolidColor(Color(0xFF007ACC)),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val trimmed = textFieldValue.text.trim()
                            if (trimmed.isEmpty() || (isRename && trimmed == initialText)) {
                                onCancel()
                            } else {
                                val err = if (isRename && trimmed == initialText) null
                                else targetDir?.let { WorkspaceManager.validateName(it, trimmed) }
                                if (err != null) {
                                    validationError = err
                                } else {
                                    onCommit(trimmed)
                                }
                            }
                        }
                    )
                )
            }
        }

        // Floating VS Code Validation Error Tooltip (Hovers over rows below without pushing them)
        if (validationError != null) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(
                    x = with(density) { (level * 14 + 32).dp.roundToPx() },
                    y = with(density) { 30.dp.roundToPx() }
                ),
                onDismissRequest = {}
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .background(Color(0xFF5A1D1D), RoundedCornerShape(2.dp))
                        .border(1.dp, Color(0xFFBE1100), RoundedCornerShape(2.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = validationError!!,
                        color = Color(0xFFCCCCCC),
                        fontSize = 11.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
