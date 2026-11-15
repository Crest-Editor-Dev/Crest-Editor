package com.cresteditor.ui.components.drawer

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cresteditor.core.model.FileTypeRegistry
import com.cresteditor.ui.components.FileTreeItem
import kotlin.math.abs

/**
 * VS Code Search Pane.
 * Handles substring search across workspace files, search results count,
 * and keyboard dismissal on list scrolling and outside gestures.
 */
@Composable
fun SearchPane(
    fileList: List<FileTreeItem>,
    onFileSelect: (FileTreeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
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

    LaunchedEffect(searchListState.isScrollInProgress) {
        if (searchListState.isScrollInProgress) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
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
        Text(
            text = "SEARCH",
            color = DrawerTheme.SideBarTitle,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(DrawerTheme.InputBg, RoundedCornerShape(2.dp))
                .border(
                    width = 1.dp,
                    color = if (isFocused) DrawerTheme.FocusBorder else DrawerTheme.InputBorder,
                    shape = RoundedCornerShape(2.dp)
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (isFocused) DrawerTheme.TextActive else DrawerTheme.ActivityBarInactive,
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
                        .onFocusChanged { isFocused = it.isFocused },
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

        Spacer(modifier = Modifier.height(14.dp))

        val filteredFiles = remember(searchQuery, fileList) {
            if (searchQuery.isBlank()) emptyList()
            else fileList.filter { !it.isDirectory && it.name.contains(searchQuery, ignoreCase = true) }
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
                    items(filteredFiles) { item ->
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
