package com.cresteditor.ui.components.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cresteditor.core.filesystem.GlobMatcher
import com.cresteditor.core.model.FileTypeRegistry
import com.cresteditor.core.settings.SettingsManager
import com.cresteditor.ui.components.FileTreeItem
import kotlin.math.abs

/**
 * VS Code Search Pane.
 * Supports:
 * - Substring and glob search across workspace files
 * - Toggle search details ("files to include", "files to exclude")
 * - "Use Exclude Settings and Ignore Files" toggle button (inherits files.exclude & search.exclude)
 * - Keyboard dismissal on scrolling and outer tap gestures
 */
@Composable
fun SearchPane(
    fileList: List<FileTreeItem>,
    onFileSelect: (FileTreeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }

    var showDetails by remember { mutableStateOf(false) }
    var filesToInclude by remember { mutableStateOf("") }
    var filesToExclude by remember { mutableStateOf("") }
    var useExcludeSettings by remember { mutableStateOf(settingsManager.isSearchUseIgnoreFiles()) }

    var isSearching by remember { mutableStateOf(false) }
    var filteredFiles by remember { mutableStateOf<List<FileTreeItem>>(emptyList()) }
    var searchTrigger by remember { mutableLongStateOf(0L) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val searchListState = rememberLazyListState()
    val searchNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (abs(available.y) > 1f || abs(available.x) > 1f) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
                return Offset.Zero
            }
        }
    }

    val effectiveExcludes = remember(useExcludeSettings) {
        if (useExcludeSettings) settingsManager.getEffectiveSearchExcludes() else emptyMap()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
            .nestedScroll(searchNestedScrollConnection)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
    ) {
        // -------------------------------------------------------------
        // Header (Title + Toggle Details ellipsis)
        // -------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SEARCH",
                color = DrawerTheme.SideBarTitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            IconButton(
                onClick = { showDetails = !showDetails },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Toggle Search Details",
                    tint = if (showDetails) DrawerTheme.FocusBorder else DrawerTheme.ActivityBarInactive,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // -------------------------------------------------------------
        // 1. Primary Query Input Box
        // -------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(DrawerTheme.InputBg, RoundedCornerShape(2.dp))
                .border(
                    width = 1.dp,
                    color = if (isSearchFocused) DrawerTheme.FocusBorder else DrawerTheme.InputBorder,
                    shape = RoundedCornerShape(2.dp)
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (isSearchFocused) DrawerTheme.TextActive else DrawerTheme.ActivityBarInactive,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search in files",
                        color = DrawerTheme.ActivityBarInactive,
                        fontSize = 12.5.sp
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        searchTrigger = 0L
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isSearchFocused = it.isFocused },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = DrawerTheme.TextActive,
                        fontSize = 12.5.sp
                    ),
                    cursorBrush = SolidColor(DrawerTheme.FocusBorder),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        searchTrigger = System.currentTimeMillis()
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                )
            }
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = {
                        searchQuery = ""
                        filteredFiles = emptyList()
                        isSearching = false
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = DrawerTheme.ActivityBarInactive,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 2. Expandable Search Details (Include/Exclude globs + Toggle)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = showDetails,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Files to include
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "files to include",
                        color = DrawerTheme.ActivityBarInactive,
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(DrawerTheme.InputBg, RoundedCornerShape(2.dp))
                            .border(1.dp, DrawerTheme.InputBorder, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = filesToInclude,
                            onValueChange = { filesToInclude = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(color = DrawerTheme.TextActive, fontSize = 11.5.sp),
                            cursorBrush = SolidColor(DrawerTheme.FocusBorder)
                        )
                    }
                }

                // Files to exclude + Use Exclude Settings toggle icon
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "files to exclude",
                        color = DrawerTheme.ActivityBarInactive,
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(DrawerTheme.InputBg, RoundedCornerShape(2.dp))
                            .border(1.dp, DrawerTheme.InputBorder, RoundedCornerShape(2.dp))
                            .padding(start = 6.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = filesToExclude,
                            onValueChange = { filesToExclude = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(color = DrawerTheme.TextActive, fontSize = 11.5.sp),
                            cursorBrush = SolidColor(DrawerTheme.FocusBorder)
                        )

                        // ⚙️ "Use Exclude Settings and Ignore Files" toggle button
                        IconButton(
                            onClick = {
                                useExcludeSettings = !useExcludeSettings
                                settingsManager.set(SettingsManager.KEY_SEARCH_USE_IGNORE_FILES, useExcludeSettings)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Use Exclude Settings and Ignore Files",
                                tint = if (useExcludeSettings) DrawerTheme.FocusBorder else DrawerTheme.ActivityBarInactive,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 3. Debounced Asynchronous Search (Off UI Thread)
        // -------------------------------------------------------------
        LaunchedEffect(searchQuery, filesToInclude, filesToExclude, useExcludeSettings, fileList, searchTrigger) {
            val query = searchQuery.trim()
            if (query.isEmpty()) {
                filteredFiles = emptyList()
                isSearching = false
                return@LaunchedEffect
            }

            isSearching = true
            // Debounce typing by 150ms (VS Code delayer style).
            // If explicitly triggered via IME search button, run immediately.
            if (searchTrigger == 0L) {
                delay(150)
            }

            val activeExcludes = if (useExcludeSettings) {
                effectiveExcludes.filter { it.value }.keys.map { GlobMatcher.globToRegex(it) }
            } else {
                emptyList()
            }

            val trimmedInclude = filesToInclude.trim()
            val includeRegex = if (trimmedInclude.isNotEmpty()) GlobMatcher.globToRegex(trimmedInclude) else null

            val trimmedExclude = filesToExclude.trim()
            val excludeRegex = if (trimmedExclude.isNotEmpty()) GlobMatcher.globToRegex(trimmedExclude) else null

            val results = withContext(Dispatchers.Default) {
                val queryLower = query.lowercase()
                val queryHasSlash = queryLower.contains('/')
                val matches = ArrayList<FileTreeItem>()

                for (item in fileList) {
                    if (item.isDirectory) continue

                    // 1. Fast substring search on filename (and path only if query contains '/')
                    val matchesName = item.name.contains(queryLower, ignoreCase = true)
                    val matchesPath = if (!matchesName && queryHasSlash) {
                        item.path.contains(queryLower, ignoreCase = true)
                    } else {
                        false
                    }
                    if (!matchesName && !matchesPath) continue

                    val path = item.path

                    // 2. Exclude settings (files.exclude & search.exclude)
                    if (activeExcludes.isNotEmpty()) {
                        var excluded = false
                        for (patternRegex in activeExcludes) {
                            if (patternRegex.containsMatchIn(path)) {
                                excluded = true
                                break
                            }
                        }
                        if (excluded) continue
                    }

                    // 3. User filesToExclude pattern
                    if (excludeRegex != null && excludeRegex.containsMatchIn(path)) {
                        continue
                    }

                    // 4. User filesToInclude pattern
                    if (includeRegex != null && !includeRegex.containsMatchIn(path)) {
                        continue
                    }

                    matches.add(item)
                    if (matches.size >= 1000) break // Cap results to prevent UI memory pressure
                }
                matches.distinctBy { it.path }
            }

            filteredFiles = results
            isSearching = false
        }

        // Animated progress line (matches VS Code search progress bar)
        if (isSearching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(top = 4.dp),
                color = DrawerTheme.FocusBorder,
                trackColor = Color.Transparent
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (searchQuery.isNotBlank()) {
            Text(
                text = "${filteredFiles.size} results in files",
                color = DrawerTheme.ActivityBarInactive,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (filteredFiles.isEmpty() && !isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No files found matching '$searchQuery'.",
                        color = DrawerTheme.ActivityBarInactive,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    state = searchListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredFiles,
                        key = { it.path },
                        contentType = { "file_search_result" }
                    ) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onFileSelect(item)
                                }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val iconData = remember(item.name) { FileTypeRegistry.getFileIcon(item.name) }
                            Icon(
                                imageVector = iconData.first,
                                contentDescription = null,
                                tint = iconData.second,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.name,
                                color = DrawerTheme.TextPrimary,
                                fontSize = 13.5.sp,
                                maxLines = 1
                            )
                            val parentFolder = remember(item.path) {
                                val file = java.io.File(item.path)
                                file.parentFile?.name ?: ""
                            }
                            if (parentFolder.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = parentFolder,
                                    color = DrawerTheme.ActivityBarInactive,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    }
            )
        }
    }
}
