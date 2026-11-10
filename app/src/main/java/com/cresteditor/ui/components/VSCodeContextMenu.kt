package com.cresteditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================================
// VS CODE CONTEXT MENU TOKENS (Mobile Adapted, Compact & Sleek)
// =============================================================================
private val MenuBackground = Color(0xFF1F1F1F)          // menu.background
private val MenuBorder = Color(0xFF383838)              // menu.border
private val MenuSeparator = Color(0xFF2E2E2E)           // menu.separatorBackground
private val MenuForeground = Color(0xFFCCCCCC)          // menu.foreground
private val MenuSelectionBg = Color(0xFF04395E)         // menu.selectionBackground
private val MenuDanger = Color(0xFFF14C4C)              // error / destructive
private val MenuAccent = Color(0xFF007ACC)              // primary accent / check

/**
 * Common VS Code Styled Popup / Dropdown Menu for Crest-Editor.
 * Features:
 * - Compact mobile width (no wide PC shortcuts clutter)
 * - Exact 1px dark border (#383838) and #1F1F1F slate background
 * - 6dp rounded corners with anchored positioning
 * - Auto height constraints to prevent screen overflow
 */
@Composable
fun VSCodeDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        modifier = modifier
            .widthIn(min = 160.dp, max = 220.dp)
            .heightIn(max = 320.dp)
            .background(MenuBackground, RoundedCornerShape(6.dp))
            .border(1.dp, MenuBorder, RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp),
        content = content
    )
}

/**
 * Individual Menu Item inside a VSCodeDropdownMenu.
 */
@Composable
fun VSCodeMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shortcut: String? = null,
    icon: ImageVector? = null,
    isDestructive: Boolean = false,
    isChecked: Boolean? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val effectiveFg = when {
        !enabled -> Color(0xFF666666)
        isDestructive -> MenuDanger
        else -> MenuForeground
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(horizontal = 4.dp)
            .background(
                if (isPressed && enabled) MenuSelectionBg else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Optional Checkmark for toggle items
        if (isChecked != null) {
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isChecked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MenuAccent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Optional Icon
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveFg,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Action Label
        Text(
            text = text,
            color = if (isPressed && enabled) Color.White else effectiveFg,
            fontSize = 13.sp,
            fontWeight = if (isPressed && enabled) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Optional Shortcut text (e.g. F2, Del, Ctrl+X)
        if (shortcut != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = shortcut,
                color = if (enabled) Color(0xFF858585) else Color(0xFF555555),
                fontSize = 11.5.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Clean 1px divider for separating action groups in the menu.
 */
@Composable
fun VSCodeMenuSeparator(
    modifier: Modifier = Modifier
) {
    HorizontalDivider(
        color = MenuSeparator,
        thickness = 1.dp,
        modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp)
    )
}
