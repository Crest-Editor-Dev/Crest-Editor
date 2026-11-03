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
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cresteditor.core.commands.CommandItem
import com.cresteditor.core.commands.CommandPaletteEntry
import com.cresteditor.core.commands.CommandRegistry

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

/**
 * Pixel-Perfect VS Code Quick Open / Command Palette Modal for Crest-Editor.
 */
@Composable
fun VSCodeCommandPalette(
    commandRegistry: CommandRegistry,
    onDismiss: () -> Unit,
    onCommandExecute: (CommandItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val entries = remember(searchQuery, commandRegistry) {
        commandRegistry.getPaletteEntries(searchQuery)
    }

    // Filter to selectable items only for keyboard arrow navigation
    val selectableItems = remember(entries) {
        entries.filterIsInstance<CommandPaletteEntry.Item>()
    }

    var selectedIndex by remember(entries) { mutableStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Scroll to active index on navigation
    LaunchedEffect(selectedIndex) {
        if (selectableItems.isNotEmpty() && selectedIndex in selectableItems.indices) {
            // Find visual index in full entries list
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x73000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 580.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consume click inside modal */ }
                    .background(VSCodeQuickInputBg, RoundedCornerShape(6.dp))
                    .border(1.dp, VSCodeQuickInputBorder, RoundedCornerShape(6.dp))
            ) {
                // =============================================================
                // 1. INPUT BOX (Exact VS Code QuickInput styling)
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
                    // Prefix ">" prompt
                    Text(
                        text = ">",
                        color = VSCodeCategoryHeader,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp)
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Type a command to run...",
                                color = VSCodeShortcut,
                                fontSize = 13.sp
                            )
                        }

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
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
                                                    val cmd = selectableItems[selectedIndex].command
                                                    onCommandExecute(cmd)
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

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
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
                            text = "No matching commands found",
                            color = VSCodeShortcut,
                            fontSize = 12.5.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        itemsIndexed(entries) { _, entry ->
                            when (entry) {
                                is CommandPaletteEntry.Header -> {
                                    // Category Header (e.g. "recently used", "other commands")
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

                                is CommandPaletteEntry.Item -> {
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
                                                onCommandExecute(entry.command)
                                            }
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Command Title with Fuzzy Highlight
                                        val titleText = formatHighlightedTitle(
                                            title = entry.command.title,
                                            matchIndices = entry.matchIndices,
                                            isSelected = isSelected
                                        )

                                        Text(
                                            text = titleText,
                                            modifier = Modifier.weight(1f),
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Keyboard Shortcut Badge
                                        entry.command.shortcut?.let { shortcut ->
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = shortcut,
                                                color = if (isSelected) VSCodeTextActive else VSCodeShortcut,
                                                fontSize = 11.5.sp,
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
 * Builds an AnnotatedString highlighting fuzzy-matched characters in VS Code blue.
 */
private fun formatHighlightedTitle(
    title: String,
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
                append(title)
            }
        }
    }

    val matchSet = matchIndices.toSet()
    return buildAnnotatedString {
        for (i in title.indices) {
            if (matchSet.contains(i)) {
                withStyle(
                    SpanStyle(
                        color = VSCodeHighlight,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(title[i])
                }
            } else {
                withStyle(
                    SpanStyle(
                        color = if (isSelected) VSCodeTextActive else VSCodeTextPrimary,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                ) {
                    append(title[i])
                }
            }
        }
    }
}
