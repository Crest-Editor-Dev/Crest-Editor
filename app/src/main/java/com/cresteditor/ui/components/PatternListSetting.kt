package com.cresteditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================================
// VS CODE PATTERN LIST THEME TOKENS
// =============================================================================
private val RowBg = Color(0xFF252526)
private val RowBorder = Color(0xFF383838)
private val FocusBorder = Color(0xFF007ACC)
private val TextFg = Color(0xFFCCCCCC)
private val TextActive = Color(0xFFFFFFFF)
private val DeleteFg = Color(0xFF858585)
private val DeleteHover = Color(0xFFF14C4C)
private val ButtonBg = Color(0xFF333333)
private val ButtonBorder = Color(0xFF444444)

/**
 * VS Code Pattern List Widget for files.exclude and search.exclude settings.
 * Renders an editable key-value list of glob patterns with:
 * - Checkbox to enable/disable specific patterns
 * - Delete button to remove pattern
 * - Inline "Add Pattern" input row
 */
@Composable
fun PatternListSetting(
    patterns: Map<String, Boolean>,
    onTogglePattern: (pattern: String, enabled: Boolean) -> Unit,
    onAddPattern: (pattern: String) -> Unit,
    onRemovePattern: (pattern: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isAdding by remember { mutableStateOf(false) }
    var newPatternText by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // List of existing patterns
        patterns.forEach { (pattern, enabled) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(RowBg, RoundedCornerShape(3.dp))
                    .border(1.dp, RowBorder, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox to toggle enabled/disabled
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(if (enabled) FocusBorder else Color(0xFF1E1E1E), RoundedCornerShape(2.dp))
                        .border(1.dp, if (enabled) FocusBorder else RowBorder, RoundedCornerShape(2.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTogglePattern(pattern, !enabled)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (enabled) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Pattern text
                Text(
                    text = pattern,
                    color = if (enabled) TextActive else TextFg,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )

                // Remove button
                IconButton(
                    onClick = { onRemovePattern(pattern) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove pattern",
                        tint = DeleteFg,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Inline Add Pattern Input or Add Button
        if (isAdding) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(RowBg, RoundedCornerShape(3.dp))
                    .border(1.dp, FocusBorder, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = newPatternText,
                    onValueChange = { newPatternText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextActive,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(FocusBorder),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val trimmed = newPatternText.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddPattern(trimmed)
                            newPatternText = ""
                            isAdding = false
                        }
                    })
                )

                IconButton(
                    onClick = {
                        val trimmed = newPatternText.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddPattern(trimmed)
                            newPatternText = ""
                            isAdding = false
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint = FocusBorder,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = {
                        newPatternText = ""
                        isAdding = false
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = DeleteFg,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            // "+ Add Pattern" Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(ButtonBg)
                    .border(1.dp, ButtonBorder, RoundedCornerShape(3.dp))
                    .clickable { isAdding = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = TextActive,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Add Pattern",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextActive
                    )
                }
            }
        }
    }
}
