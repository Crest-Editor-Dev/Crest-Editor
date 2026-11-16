package com.cresteditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.cresteditor.core.commands.*
import com.cresteditor.core.filesystem.GlobMatcher
import com.cresteditor.core.settings.SettingsManager

private val VSCodeQuickInputBg = Color(0xFF252526)
private val VSCodeQuickInputBorder = Color(0xFF3C3C3C)
private val VSCodeInputBoxBg = Color(0xFF313131)
private val VSCodeFocusBorder = Color(0xFF007ACC)
private val VSCodeListSelectionBg = Color(0xFF04395E)
private val VSCodeCategoryHeader = Color(0xFF3794FF)
private val VSCodeTextPrimary = Color(0xFFCCCCCC)
private val VSCodeTextActive = Color(0xFFFFFFFF)
private val VSCodeShortcut = Color(0xFF858585)
private val VSCodeHighlight = Color(0xFF3794FF)
private val VSCodeDescription = Color(0xFF969696)
private val VSCodeDirtyDot = Color(0xFFE2C08D)

/**
 * Pixel-Perfect Multi-Mode VS Code Quick Open / Command Palette Modal for Crest-Editor.
 * Supports:
 * - Empty / Query: File Search (Quick Open) + Open Editors (Ctrl+P)
 * - `>`: Show and Run Commands (F1 / Ctrl+Shift+P)
 * - `:`: Go to Line:Col (Ctrl+G)
 * - `?`: Help / Show all Quick Access providers
 * - `view `: Open View
 */
@Composable
fun VSCodeCommandPalette(
    initialQuery: String = ">",
    commandRegistry: CommandRegistry,
    fileTreeItems: List<FileTreeItem> = emptyList(),
    openTabs: List<CrestTabItem> = emptyList(),
    activeTabId: String = "",
    currentLineCount: Int = 1,
    effectiveExcludes: Map<String, Boolean>? = null,
    onDismiss: () -> Unit,
    onCommandExecute: (CommandItem) -> Unit,
    onOpenFile: ((path: String, line: Int?, col: Int?) -> Unit)? = null,
    onNavigateLine: ((line: Int, col: Int) -> Unit)? = null,
    onOpenView: ((viewId: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val resolvedExcludes = remember(effectiveExcludes) {
        effectiveExcludes ?: run {
            val sm = SettingsManager(context)
            if (sm.isSearchUseIgnoreFiles()) sm.getEffectiveSearchExcludes() else emptyMap()
        }
    }

    val activeExcludeRegexes = remember(resolvedExcludes) {
        if (resolvedExcludes.isNotEmpty()) {
            resolvedExcludes.filter { it.value }.keys.map { GlobMatcher.globToRegex(it) }
        } else {
            emptyList()
        }
    }

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialQuery,
                selection = TextRange(initialQuery.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }

    val rawQuery = textFieldValue.text
    val (mode, queryParam) = remember(rawQuery) {
        QuickAccessMode.resolve(rawQuery)
    }

    // Generate entries based on current mode and query parameter
    val entries = remember(rawQuery, mode, queryParam, commandRegistry, fileTreeItems, openTabs, currentLineCount, activeExcludeRegexes) {
        buildQuickPickEntries(
            mode = mode,
            param = queryParam,
            commandRegistry = commandRegistry,
            fileTreeItems = fileTreeItems,
            openTabs = openTabs,
            activeTabId = activeTabId,
            currentLineCount = currentLineCount,
            activeExcludeRegexes = activeExcludeRegexes,
            onCommandExecute = onCommandExecute,
            onOpenFile = onOpenFile,
            onNavigateLine = onNavigateLine,
            onOpenView = onOpenView,
            onSwitchMode = { newPrefix ->
                textFieldValue = TextFieldValue(
                    text = newPrefix,
                    selection = TextRange(newPrefix.length)
                )
            }
        )
    }

    // Filter to selectable items only for keyboard arrow navigation
    val selectableItems = remember(entries) {
        entries.filterIsInstance<QuickPickEntry.Item>()
    }

    var selectedIndex by remember(entries) { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            keyboardController?.hide()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Scroll to active index on user navigation
    LaunchedEffect(selectedIndex) {
        if (selectedIndex > 0 && selectableItems.isNotEmpty() && selectedIndex in selectableItems.indices) {
            val targetEntry = selectableItems[selectedIndex]
            val visualIndex = entries.indexOf(targetEntry)
            if (visualIndex >= 0) {
                listState.animateScrollToItem(visualIndex)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setDimAmount(0f)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 560.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consume click inside modal */ }
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(6.dp),
                        spotColor = Color(0x80000000),
                        ambientColor = Color(0x80000000)
                    )
                    .background(VSCodeQuickInputBg, RoundedCornerShape(6.dp))
                    .border(1.dp, VSCodeQuickInputBorder, RoundedCornerShape(6.dp))
            ) {
                // =============================================================
                // 1. INPUT BOX (VS Code QuickInput styling)
                // =============================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                        .height(34.dp)
                        .background(VSCodeInputBoxBg, RoundedCornerShape(3.dp))
                        .border(1.dp, VSCodeFocusBorder, RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Mode Indicator Icon / Glyph
                    when (mode) {
                        QuickAccessMode.COMMANDS -> {
                            Text(
                                text = ">",
                                color = VSCodeCategoryHeader,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        QuickAccessMode.GOTO_LINE -> {
                            Text(
                                text = ":",
                                color = VSCodeCategoryHeader,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        QuickAccessMode.HELP -> {
                            Text(
                                text = "?",
                                color = VSCodeCategoryHeader,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        QuickAccessMode.VIEW -> {
                            Text(
                                text = "view",
                                color = VSCodeCategoryHeader,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        QuickAccessMode.FILES -> {
                            // Exact VS Code & Zed behavior: no leading icon, keeping the input completely clean
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        if (rawQuery.isEmpty()) {
                            Text(
                                text = mode.placeholder,
                                color = VSCodeShortcut,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = {
                                textFieldValue = it
                                selectedIndex = 0
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionDown -> {
                                                if (selectableItems.isNotEmpty()) {
                                                    selectedIndex = (selectedIndex + 1).coerceAtMost(selectableItems.size - 1)
                                                }
                                                true
                                            }
                                            Key.DirectionUp -> {
                                                if (selectableItems.isNotEmpty()) {
                                                    selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                                }
                                                true
                                            }
                                            Key.Enter -> {
                                                if (selectableItems.isNotEmpty() && selectedIndex in selectableItems.indices) {
                                                    val item = selectableItems[selectedIndex]
                                                    item.onExecute()
                                                }
                                                true
                                            }
                                            Key.Escape -> {
                                                onDismiss()
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = VSCodeTextActive,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Default
                            ),
                            cursorBrush = SolidColor(VSCodeFocusBorder)
                        )
                    }

                    if (rawQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                textFieldValue = TextFieldValue("", selection = TextRange.Zero)
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = VSCodeShortcut,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // Separator line under input
                Divider(color = VSCodeQuickInputBorder, thickness = 1.dp)

                // =============================================================
                // 2. RESULTS LIST
                // =============================================================
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (mode) {
                                QuickAccessMode.COMMANDS -> "No matching commands found"
                                QuickAccessMode.FILES -> "No matching files found"
                                QuickAccessMode.GOTO_LINE -> "Invalid line number"
                                QuickAccessMode.VIEW -> "No matching views found"
                                QuickAccessMode.HELP -> "No help entries available"
                            },
                            color = VSCodeShortcut,
                            fontSize = 12.5.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        itemsIndexed(
                            items = entries,
                            key = { index, entry ->
                                when (entry) {
                                    is QuickPickEntry.Header -> "header_${entry.title}_$index"
                                    is QuickPickEntry.Item -> "item_${entry.id}_$index"
                                }
                            }
                        ) { _, entry ->
                            when (entry) {
                                is QuickPickEntry.Header -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = entry.title,
                                            color = VSCodeCategoryHeader,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Divider(
                                            color = Color(0x33808080),
                                            modifier = Modifier.weight(1f),
                                            thickness = 1.dp
                                        )
                                    }
                                }

                                is QuickPickEntry.Item -> {
                                    val isSelected = selectableItems.getOrNull(selectedIndex) == entry

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(34.dp)
                                            .background(
                                                if (isSelected) VSCodeListSelectionBg else Color.Transparent,
                                                RoundedCornerShape(3.dp)
                                            )
                                            .clickable {
                                                entry.onExecute()
                                            }
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Mode-specific Leading Icon
                                        when (entry.icon) {
                                            QuickPickIcon.FILE -> {
                                                val iconData = getFileIcon(entry.label)
                                                Icon(
                                                    imageVector = iconData.first,
                                                    contentDescription = null,
                                                    tint = iconData.second,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .padding(end = 6.dp)
                                                )
                                            }
                                            QuickPickIcon.COMMAND -> {
                                                Icon(
                                                    imageVector = Icons.Default.Code,
                                                    contentDescription = null,
                                                    tint = if (isSelected) VSCodeTextActive else VSCodeShortcut,
                                                    modifier = Modifier
                                                        .size(15.dp)
                                                        .padding(end = 6.dp)
                                                )
                                            }
                                            QuickPickIcon.GOTO_LINE -> {
                                                Text(
                                                    text = "#",
                                                    color = VSCodeHighlight,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(end = 6.dp)
                                                )
                                            }
                                            QuickPickIcon.VIEW -> {
                                                Icon(
                                                    imageVector = Icons.Default.ViewQuilt,
                                                    contentDescription = null,
                                                    tint = if (isSelected) VSCodeTextActive else VSCodeShortcut,
                                                    modifier = Modifier
                                                        .size(15.dp)
                                                        .padding(end = 6.dp)
                                                )
                                            }
                                            QuickPickIcon.HELP -> {
                                                Icon(
                                                    imageVector = Icons.Default.HelpOutline,
                                                    contentDescription = null,
                                                    tint = if (isSelected) VSCodeTextActive else VSCodeShortcut,
                                                    modifier = Modifier
                                                        .size(15.dp)
                                                        .padding(end = 6.dp)
                                                )
                                            }
                                        }

                                        // Primary Label with Fuzzy Highlight
                                        val labelText = formatHighlightedText(
                                            text = entry.label,
                                            matchIndices = entry.labelMatchIndices,
                                            isSelected = isSelected
                                        )

                                        Text(
                                            text = labelText,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Optional Secondary Description (e.g. folder path or category)
                                        entry.description?.let { desc ->
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = desc,
                                                color = if (isSelected) VSCodeTextActive.copy(alpha = 0.8f) else VSCodeDescription,
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        // Dirty dot indicator if modified
                                        if (entry.isModified) {
                                            Text(
                                                text = "●",
                                                color = VSCodeDirtyDot,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                        }

                                        // Keyboard Shortcut or Mode badge
                                        entry.shortcut?.let { shortcut ->
                                            Text(
                                                text = shortcut,
                                                color = if (isSelected) VSCodeTextActive else VSCodeShortcut,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Builds the entries for the active Quick Access Mode.
 */
private fun buildQuickPickEntries(
    mode: QuickAccessMode,
    param: String,
    commandRegistry: CommandRegistry,
    fileTreeItems: List<FileTreeItem>,
    openTabs: List<CrestTabItem>,
    activeTabId: String,
    currentLineCount: Int,
    activeExcludeRegexes: List<Regex> = emptyList(),
    onCommandExecute: (CommandItem) -> Unit,
    onOpenFile: ((path: String, line: Int?, col: Int?) -> Unit)?,
    onNavigateLine: ((line: Int, col: Int) -> Unit)?,
    onOpenView: ((viewId: String) -> Unit)?,
    onSwitchMode: (prefix: String) -> Unit
): List<QuickPickEntry> {
    val result = mutableListOf<QuickPickEntry>()

    when (mode) {
        QuickAccessMode.COMMANDS -> {
            val query = param.trim()
            val commands = commandRegistry.getCommands()
            val recentIds = commandRegistry.getRecentCommandIds()

            if (query.isEmpty()) {
                val recentList = recentIds.mapNotNull { id -> commands.find { it.id == id } }
                if (recentList.isNotEmpty()) {
                    result.add(QuickPickEntry.Header("recently used"))
                    recentList.forEach { cmd ->
                        result.add(
                            QuickPickEntry.Item(
                                id = cmd.id,
                                label = cmd.title,
                                description = cmd.category,
                                shortcut = cmd.shortcut,
                                icon = QuickPickIcon.COMMAND,
                                onExecute = { onCommandExecute(cmd) }
                            )
                        )
                    }
                }

                val otherList = commands.filter { cmd -> !recentIds.contains(cmd.id) }.sortedBy { it.title }
                if (otherList.isNotEmpty()) {
                    if (recentList.isNotEmpty()) {
                        result.add(QuickPickEntry.Header("other commands"))
                    }
                    otherList.forEach { cmd ->
                        result.add(
                            QuickPickEntry.Item(
                                id = cmd.id,
                                label = cmd.title,
                                description = cmd.category,
                                shortcut = cmd.shortcut,
                                icon = QuickPickIcon.COMMAND,
                                onExecute = { onCommandExecute(cmd) }
                            )
                        )
                    }
                }
            } else {
                data class ScoredCommand(val cmd: CommandItem, val score: Int, val matchIndices: List<Int>)
                val scored = mutableListOf<ScoredCommand>()

                for (cmd in commands) {
                    val match = QuickAccessFuzzyScorer.score(cmd.title, query)
                    if (match != null) {
                        scored.add(ScoredCommand(cmd, match.score, match.matchIndices))
                    } else {
                        // Also try category + title match
                        val combined = "${cmd.category}: ${cmd.title}"
                        val combMatch = QuickAccessFuzzyScorer.score(combined, query)
                        if (combMatch != null) {
                            scored.add(ScoredCommand(cmd, combMatch.score - 100, emptyList()))
                        }
                    }
                }

                scored.sortByDescending { it.score }
                scored.forEach { sc ->
                    result.add(
                        QuickPickEntry.Item(
                            id = sc.cmd.id,
                            label = sc.cmd.title,
                            description = sc.cmd.category,
                            shortcut = sc.cmd.shortcut,
                            icon = QuickPickIcon.COMMAND,
                            labelMatchIndices = sc.matchIndices,
                            onExecute = { onCommandExecute(sc.cmd) }
                        )
                    )
                }
            }
        }

        QuickAccessMode.GOTO_LINE -> {
            val text = param.trim()
            val maxLine = currentLineCount.coerceAtLeast(1)

            if (text.isEmpty()) {
                result.add(
                    QuickPickEntry.Item(
                        id = "goto_line_prompt",
                        label = "Type a line number between 1 and $maxLine to navigate to.",
                        description = "Current buffer has $maxLine lines",
                        icon = QuickPickIcon.GOTO_LINE,
                        onExecute = {}
                    )
                )
            } else {
                // Support format of `line` or `line,col` or `line:col`
                val parts = text.split(',', ':')
                val parsedLine = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val parsedCol = parts.getOrNull(1)?.trim()?.toIntOrNull()

                if (parsedLine != null && parsedLine > 0) {
                    val targetLine = parsedLine.coerceIn(1, maxLine)
                    val targetCol = (parsedCol ?: 1).coerceAtLeast(1)
                    result.add(
                        QuickPickEntry.Item(
                            id = "goto_line_$targetLine",
                            label = "Go to line $targetLine, column $targetCol",
                            description = "Press Enter to navigate",
                            icon = QuickPickIcon.GOTO_LINE,
                            onExecute = {
                                onNavigateLine?.invoke(targetLine, targetCol)
                            }
                        )
                    )
                } else {
                    result.add(
                        QuickPickEntry.Item(
                            id = "goto_line_invalid",
                            label = "Invalid line number: $text",
                            description = "Type a number from 1 to $maxLine",
                            icon = QuickPickIcon.GOTO_LINE,
                            onExecute = {}
                        )
                    )
                }
            }
        }

        QuickAccessMode.HELP -> {
            val helpProviders = listOf(
                Triple(">", "Show and Run Commands", "F1 / Ctrl+Shift+P"),
                Triple(":", "Go to Line / Column", "Ctrl+G"),
                Triple("view ", "Open View (Explorer, Terminal, Settings)", ""),
                Triple("?", "Show all Quick Access Providers", "")
            )

            val filter = param.trim().lowercase()
            result.add(QuickPickEntry.Header("quick access providers"))

            helpProviders.forEach { (prefix, desc, shortcut) ->
                if (filter.isEmpty() || desc.lowercase().contains(filter) || prefix.contains(filter)) {
                    result.add(
                        QuickPickEntry.Item(
                            id = "help_$prefix",
                            label = prefix,
                            description = desc,
                            shortcut = shortcut.ifEmpty { null },
                            icon = QuickPickIcon.HELP,
                            onExecute = {
                                onSwitchMode(prefix)
                            }
                        )
                    )
                }
            }
        }

        QuickAccessMode.VIEW -> {
            val query = param.trim()
            val viewItems = listOf(
                Pair("workbench.view.explorer", "Explorer: Toggle Primary Side Bar"),
                Pair("workbench.view.terminal", "Terminal: Toggle Terminal"),
                Pair("workbench.view.settings", "Preferences: Open Settings (UI)"),
                Pair("workbench.view.reload", "Developer: Reload Window")
            )

            result.add(QuickPickEntry.Header("views"))
            for ((id, title) in viewItems) {
                if (query.isEmpty()) {
                    result.add(
                        QuickPickEntry.Item(
                            id = id,
                            label = title,
                            icon = QuickPickIcon.VIEW,
                            onExecute = { onOpenView?.invoke(id) }
                        )
                    )
                } else {
                    val match = QuickAccessFuzzyScorer.score(title, query)
                    if (match != null) {
                        result.add(
                            QuickPickEntry.Item(
                                id = id,
                                label = title,
                                icon = QuickPickIcon.VIEW,
                                labelMatchIndices = match.matchIndices,
                                onExecute = { onOpenView?.invoke(id) }
                            )
                        )
                    }
                }
            }
        }

        QuickAccessMode.FILES -> {
            // Check for line / column suffix (e.g. filename.js:45 or filename.js:45,10)
            val lineSuffixMatch = Regex("^(.*?)(?::(\\d+)(?:[,:](\\d+))?)?$").matchEntire(param)
            val fileQuery = lineSuffixMatch?.groupValues?.getOrNull(1)?.trim() ?: param.trim()
            val targetLine = lineSuffixMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
            val targetCol = lineSuffixMatch?.groupValues?.getOrNull(3)?.toIntOrNull()
            val openTabIds = openTabs.map { it.id }.toSet()

            if (fileQuery.isEmpty()) {
                // 1. Show open editors first (instant O(openTabs))
                if (openTabs.isNotEmpty()) {
                    result.add(QuickPickEntry.Header("open editors"))
                    openTabs.forEach { tab ->
                        result.add(
                            QuickPickEntry.Item(
                                id = "tab_${tab.id}",
                                label = tab.title,
                                description = tab.id.substringAfterLast('/', ""),
                                shortcut = "OPEN",
                                isModified = tab.isModified,
                                icon = QuickPickIcon.FILE,
                                onExecute = {
                                    onOpenFile?.invoke(tab.id, targetLine, targetCol)
                                }
                            )
                        )
                    }
                }

                // 2. Followed by workspace files (with early exit after 40 items)
                val workspaceFiles = mutableListOf<FileTreeItem>()
                for (item in fileTreeItems) {
                    if (item.isDirectory) continue
                    val path = item.path
                    if (openTabIds.contains(path)) continue

                    if (activeExcludeRegexes.isNotEmpty()) {
                        var isExcluded = false
                        for (regex in activeExcludeRegexes) {
                            if (regex.containsMatchIn(path)) {
                                isExcluded = true
                                break
                            }
                        }
                        if (isExcluded) continue
                    }

                    workspaceFiles.add(item)
                    if (workspaceFiles.size >= 40) break // Early break avoids scanning tens of thousands of files!
                }

                if (workspaceFiles.isNotEmpty()) {
                    if (openTabs.isNotEmpty()) {
                        result.add(QuickPickEntry.Header("workspace files"))
                    }
                    workspaceFiles.forEach { file ->
                        result.add(
                            QuickPickEntry.Item(
                                id = "file_${file.path}",
                                label = file.name,
                                description = file.path,
                                icon = QuickPickIcon.FILE,
                                onExecute = {
                                    onOpenFile?.invoke(file.path, targetLine, targetCol)
                                }
                            )
                        )
                    }
                }
            } else {
                val queryLower = fileQuery.lowercase()
                val queryHasSlash = queryLower.contains('/')

                data class ScoredFile(
                    val file: FileTreeItem,
                    val isOpen: Boolean,
                    val isModified: Boolean,
                    val score: Int,
                    val labelIndices: List<Int>
                )
                val scored = mutableListOf<ScoredFile>()

                for (file in fileTreeItems) {
                    if (file.isDirectory) continue
                    val nameLower = file.name.lowercase()

                    // Fast character matching check: skip 99% of files in primitive loops
                    val matchesDirectly = nameLower.contains(queryLower) || (queryHasSlash && file.path.lowercase().contains(queryLower))
                    if (!matchesDirectly) {
                        var qI = 0
                        for (cI in nameLower.indices) {
                            if (qI < queryLower.length && nameLower[cI] == queryLower[qI]) {
                                qI++
                            }
                        }
                        if (qI < queryLower.length) continue
                    }

                    val path = file.path
                    val isOpen = openTabIds.contains(path)

                    // Only run exclude regexes on files that matched the query!
                    if (!isOpen && activeExcludeRegexes.isNotEmpty()) {
                        var isExcluded = false
                        for (regex in activeExcludeRegexes) {
                            if (regex.containsMatchIn(path)) {
                                isExcluded = true
                                break
                            }
                        }
                        if (isExcluded) continue
                    }

                    val openTab = if (isOpen) openTabs.find { it.id == path } else null
                    val isModified = openTab?.isModified == true

                    val nameMatch = QuickAccessFuzzyScorer.score(file.name, fileQuery)
                    if (nameMatch != null) {
                        val finalScore = nameMatch.score + (if (isOpen) 200 else 0)
                        scored.add(ScoredFile(file, isOpen, isModified, finalScore, nameMatch.matchIndices))
                    } else {
                        val pathMatch = QuickAccessFuzzyScorer.score(file.path, fileQuery)
                        if (pathMatch != null) {
                            val finalScore = pathMatch.score - 100 + (if (isOpen) 200 else 0)
                            scored.add(ScoredFile(file, isOpen, isModified, finalScore, emptyList()))
                        }
                    }

                    if (scored.size >= 150) break // Cap search results to avoid massive sorts
                }

                scored.sortByDescending { it.score }
                scored.take(50).forEach { sf ->
                    result.add(
                        QuickPickEntry.Item(
                            id = "search_file_${sf.file.path}",
                            label = sf.file.name,
                            description = sf.file.path,
                            shortcut = if (sf.isOpen) "OPEN" else null,
                            isModified = sf.isModified,
                            icon = QuickPickIcon.FILE,
                            labelMatchIndices = sf.labelIndices,
                            onExecute = {
                                onOpenFile?.invoke(sf.file.path, targetLine, targetCol)
                            }
                        )
                    )
                }
            }
        }
    }

    return result
}

/**
 * Builds an AnnotatedString highlighting fuzzy-matched characters in VS Code blue.
 */
private fun formatHighlightedText(
    text: String,
    matchIndices: List<Int>,
    isSelected: Boolean
): AnnotatedString {
    if (matchIndices.isEmpty()) {
        return buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = if (isSelected) VSCodeTextActive else VSCodeTextPrimary,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
            ) {
                append(text)
            }
        }
    }

    val matchSet = matchIndices.toSet()
    return buildAnnotatedString {
        for (i in text.indices) {
            if (matchSet.contains(i)) {
                withStyle(
                    SpanStyle(
                        color = VSCodeHighlight,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(text[i])
                }
            } else {
                withStyle(
                    SpanStyle(
                        color = if (isSelected) VSCodeTextActive else VSCodeTextPrimary,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                ) {
                    append(text[i])
                }
            }
        }
    }
}
