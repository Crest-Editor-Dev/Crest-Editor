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
import com.cresteditor.workspace.IndexedFile
import com.cresteditor.workspace.ProjectFileIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    projectFileIndex: ProjectFileIndex? = null,
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

    // Async file search states
    var previousFileQuery by remember { mutableStateOf("") }
    var candidatePool by remember { mutableStateOf<List<IndexedFile>?>(null) }
    var asyncFileEntries by remember { mutableStateOf<List<QuickPickEntry>>(emptyList()) }

    // Run file search asynchronously on Dispatchers.Default with intelligent debounce and cancellation
    LaunchedEffect(rawQuery, mode, queryParam, projectFileIndex, fileTreeItems, openTabs, activeExcludeRegexes) {
        if (mode != QuickAccessMode.FILES) {
            candidatePool = null
            previousFileQuery = ""
            return@LaunchedEffect
        }

        val lineSuffixMatch = Regex("^(.*?)(?::(\\d+)(?:[,:](\\d+))?)?$").matchEntire(queryParam)
        val fileQuery = lineSuffixMatch?.groupValues?.getOrNull(1)?.trim() ?: queryParam.trim()
        val targetLine = lineSuffixMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
        val targetCol = lineSuffixMatch?.groupValues?.getOrNull(3)?.toIntOrNull()
        val openTabIds = openTabs.map { it.id }.toSet()

        if (fileQuery.isEmpty()) {
            candidatePool = null
            previousFileQuery = ""
            val initialResults = mutableListOf<QuickPickEntry>()
            if (openTabs.isNotEmpty()) {
                initialResults.add(QuickPickEntry.Header("open editors"))
                openTabs.forEach { tab ->
                    initialResults.add(
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

            val defaultFiles: List<Pair<String, String>> = if (projectFileIndex != null) {
                projectFileIndex.getAllFiles().filter { !openTabIds.contains(it.path) }.take(40).map { it.name to it.path }
            } else {
                val list = mutableListOf<Pair<String, String>>()
                for (item in fileTreeItems) {
                    if (item.isDirectory || openTabIds.contains(item.path)) continue
                    if (activeExcludeRegexes.any { it.containsMatchIn(item.path) }) continue
                    list.add(item.name to item.path)
                    if (list.size >= 40) break
                }
                list
            }

            if (defaultFiles.isNotEmpty()) {
                if (openTabs.isNotEmpty()) {
                    initialResults.add(QuickPickEntry.Header("workspace files"))
                }
                defaultFiles.forEach { (name, path) ->
                    initialResults.add(
                        QuickPickEntry.Item(
                            id = "file_$path",
                            label = name,
                            description = path,
                            icon = QuickPickIcon.FILE,
                            onExecute = {
                                onOpenFile?.invoke(path, targetLine, targetCol)
                            }
                        )
                    )
                }
            }

            asyncFileEntries = initialResults
        } else {
            // 40ms intelligent debounce: rapid keystrokes cancel previous jobs immediately
            delay(40)

            val results = withContext(Dispatchers.Default) {
                val searchResultsList = mutableListOf<QuickPickEntry>()

                val currentCandidates = if (previousFileQuery.isNotEmpty() &&
                    fileQuery.startsWith(previousFileQuery) &&
                    candidatePool != null
                ) {
                    candidatePool
                } else {
                    null
                }

                if (projectFileIndex != null) {
                    val searchResult = projectFileIndex.search(fileQuery, currentCandidates, topK = 50)
                    candidatePool = searchResult.matchedCandidates
                    previousFileQuery = fileQuery

                    for (m in searchResult.matches) {
                        val file = m.file
                        val isOpen = openTabIds.contains(file.path)
                        val openTab = if (isOpen) openTabs.find { it.id == file.path } else null
                        searchResultsList.add(
                            QuickPickEntry.Item(
                                id = "search_file_${file.path}",
                                label = file.name,
                                description = file.relativePath.ifEmpty { file.path },
                                shortcut = if (isOpen) "OPEN" else null,
                                isModified = openTab?.isModified == true,
                                icon = QuickPickIcon.FILE,
                                labelMatchIndices = m.matchIndices,
                                onExecute = {
                                    onOpenFile?.invoke(file.path, targetLine, targetCol)
                                }
                            )
                        )
                    }
                } else {
                    candidatePool = null
                    previousFileQuery = fileQuery
                    val fallback = performFallbackFileSearch(
                        fileQuery = fileQuery,
                        fileTreeItems = fileTreeItems,
                        openTabs = openTabs,
                        openTabIds = openTabIds,
                        activeExcludeRegexes = activeExcludeRegexes,
                        targetLine = targetLine,
                        targetCol = targetCol,
                        onOpenFile = onOpenFile
                    )
                    searchResultsList.addAll(fallback)
                }

                searchResultsList
            }

            asyncFileEntries = results
        }
    }

    // Static / light modes (commands, view, goto line, help) run synchronously in remember
    val nonFileEntries = remember(rawQuery, mode, queryParam, commandRegistry, openTabs, currentLineCount) {
        if (mode != QuickAccessMode.FILES) {
            buildQuickPickEntries(
                mode = mode,
                param = queryParam,
                commandRegistry = commandRegistry,
                fileTreeItems = emptyList(),
                openTabs = openTabs,
                activeTabId = activeTabId,
                currentLineCount = currentLineCount,
                activeExcludeRegexes = emptyList(),
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
        } else {
            emptyList()
        }
    }

    val entries = if (mode == QuickAccessMode.FILES) asyncFileEntries else nonFileEntries

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
            val lineSuffixMatch = Regex("^(.*?)(?::(\\d+)(?:[,:](\\d+))?)?$").matchEntire(param)
            val fileQuery = lineSuffixMatch?.groupValues?.getOrNull(1)?.trim() ?: param.trim()
            val targetLine = lineSuffixMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
            val targetCol = lineSuffixMatch?.groupValues?.getOrNull(3)?.toIntOrNull()
            val openTabIds = openTabs.map { it.id }.toSet()

            return performFallbackFileSearch(
                fileQuery = fileQuery,
                fileTreeItems = fileTreeItems,
                openTabs = openTabs,
                openTabIds = openTabIds,
                activeExcludeRegexes = activeExcludeRegexes,
                targetLine = targetLine,
                targetCol = targetCol,
                onOpenFile = onOpenFile
            )
        }
    }

    return result
}

/**
 * Fallback file search with bounded Top-K priority ranking for when ProjectFileIndex is not available.
 */
private fun performFallbackFileSearch(
    fileQuery: String,
    fileTreeItems: List<FileTreeItem>,
    openTabs: List<CrestTabItem>,
    openTabIds: Set<String>,
    activeExcludeRegexes: List<Regex>,
    targetLine: Int?,
    targetCol: Int?,
    onOpenFile: ((path: String, line: Int?, col: Int?) -> Unit)?
): List<QuickPickEntry> {
    val result = mutableListOf<QuickPickEntry>()

    if (fileQuery.isEmpty()) {
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

        val workspaceFiles = mutableListOf<FileTreeItem>()
        for (item in fileTreeItems) {
            if (item.isDirectory) continue
            val path = item.path
            if (openTabIds.contains(path)) continue

            if (activeExcludeRegexes.isNotEmpty()) {
                if (activeExcludeRegexes.any { it.containsMatchIn(path) }) continue
            }

            workspaceFiles.add(item)
            if (workspaceFiles.size >= 40) break
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
        return result
    }

    val queryLower = fileQuery.lowercase()
    val queryHasSlash = queryLower.contains('/')
    val firstChar = queryLower[0]
    val queryLen = queryLower.length

    data class ScoredFallbackFile(
        val file: FileTreeItem,
        val isOpen: Boolean,
        val isModified: Boolean,
        val score: Int,
        val labelIndices: List<Int>
    )

    val minHeap = java.util.PriorityQueue<ScoredFallbackFile>(51, compareBy { it.score })

    for (file in fileTreeItems) {
        if (file.isDirectory) continue
        val nameLower = file.name.lowercase()
        val pathLower = if (queryHasSlash) file.path.lowercase() else ""
        val targetLower = if (queryHasSlash) pathLower else nameLower

        if (targetLower.indexOf(firstChar) < 0) continue

        var qI = 0
        var tI = 0
        val tLen = targetLower.length
        while (qI < queryLen && tI < tLen) {
            if (targetLower[tI] == queryLower[qI]) qI++
            tI++
        }
        if (qI < queryLen) continue

        val path = file.path
        val isOpen = openTabIds.contains(path)

        if (!isOpen && activeExcludeRegexes.isNotEmpty()) {
            if (activeExcludeRegexes.any { it.containsMatchIn(path) }) continue
        }

        val openTab = if (isOpen) openTabs.find { it.id == path } else null
        val isModified = openTab?.isModified == true

        val nameMatch = QuickAccessFuzzyScorer.score(file.name, fileQuery)
        val score: Int
        val indices: List<Int>
        if (nameMatch != null) {
            score = nameMatch.score + (if (isOpen) 200 else 0)
            indices = nameMatch.matchIndices
        } else {
            val pathMatch = QuickAccessFuzzyScorer.score(file.path, fileQuery)
            if (pathMatch != null) {
                score = pathMatch.score - 100 + (if (isOpen) 200 else 0)
                indices = emptyList()
            } else {
                continue
            }
        }

        minHeap.offer(ScoredFallbackFile(file, isOpen, isModified, score, indices))
        if (minHeap.size > 50) {
            minHeap.poll()
        }
    }

    val top = mutableListOf<ScoredFallbackFile>()
    while (minHeap.isNotEmpty()) {
        top.add(minHeap.poll()!!)
    }
    top.reverse()

    return top.map { sf ->
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
    }
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
