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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
                    onValueChange = { searchQuery = it },
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
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                )
            }
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = {
                        searchQuery = ""
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

        Spacer(modifier = Modifier.height(14.dp))

        // -------------------------------------------------------------
        // 3. Search Results Filtering (Respecting Excludes)
        // -------------------------------------------------------------
        val filteredFiles = remember(searchQuery, fileList, useExcludeSettings, filesToInclude, filesToExclude) {
            if (searchQuery.isBlank()) emptyList()
            else {
                fileList.filter { item ->
                    if (item.isDirectory) return@filter false

                    // Substring match on query
                    val matchesQuery = item.name.contains(searchQuery, ignoreCase = true)
                    if (!matchesQuery) return@filter false

                    // Smart exclude filtering
                    if (useExcludeSettings && GlobMatcher.isExcluded(item.path, effectiveExcludes)) {
                        return@filter false
                    }

                    // User filesToExclude pattern
                    if (filesToExclude.isNotBlank() && GlobMatcher.matches(filesToExclude.trim(), item.path)) {
                        return@filter false
                    }

                    // User filesToInclude pattern
                    if (filesToInclude.isNotBlank() && !GlobMatcher.matches(filesToInclude.trim(), item.path)) {
                        return@filter false
                    }

                    true
                }
            }
        }

        if (searchQuery.isNotBlank()) {
            Text(
                text = "${filteredFiles.size} results in files",
                color = DrawerTheme.ActivityBarInactive,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { _, _ ->
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
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
                    items(filteredFiles, key = { it.path }) { item ->
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
                            val iconData = FileTypeRegistry.getFileIcon(item.name)
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
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { _, _ ->
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    }
            )
        }
    }
}
