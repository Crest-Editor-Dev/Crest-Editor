package com.cresteditor.ui.components.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.cresteditor.core.model.FileTypeRegistry
import com.cresteditor.icons.Codicons
import com.cresteditor.workspace.WorkspaceManager
import java.io.File

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
fun InlineTreeInputRow(
    initialText: String = "",
    isFolder: Boolean,
    level: Int,
    targetDir: File?,
    isRename: Boolean = false,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier
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
                        tint = DrawerTheme.ActivityBarInactive,
                        modifier = Modifier.size(17.dp)
                    )
                } else {
                    val iconData = FileTypeRegistry.getFileIcon(textFieldValue.text)
                    Icon(
                        imageVector = iconData.first,
                        contentDescription = null,
                        tint = iconData.second,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            val borderColor = if (validationError != null) DrawerTheme.ErrorBorder else DrawerTheme.FocusBorder
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .background(DrawerTheme.InputBg, RoundedCornerShape(2.dp))
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
                    cursorBrush = SolidColor(DrawerTheme.FocusBorder),
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
                        .background(DrawerTheme.ErrorTooltipBg, RoundedCornerShape(2.dp))
                        .border(1.dp, DrawerTheme.ErrorBorder, RoundedCornerShape(2.dp))
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
