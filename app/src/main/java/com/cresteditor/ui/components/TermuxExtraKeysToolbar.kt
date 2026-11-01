package com.cresteditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Termux Signature Colors
private val TermuxToolbarBg = Color(0xFF000000)
private val TermuxDividerColor = Color(0xFF1E1E1E)
private val TermuxKeyActiveBg = Color(0xFF333333)
private val TermuxKeyPressedBg = Color(0xFF282828)
private val TermuxKeyTextActive = Color(0xFFEF5350) // Termux red_400
private val TermuxKeyTextDefault = Color(0xFFFFFFFF)

/**
 * Exact Termux-style 2-row extra keys toolbar.
 * Flat edge-to-edge black slab, 7 equal-width columns per row, auto-repeat on hold for arrows/nav keys,
 * and tap-to-activate / long-press-to-lock for CTRL & ALT keys.
 */
@Composable
fun TermuxExtraKeysToolbar(
    isCtrlActive: Boolean,
    isCtrlLocked: Boolean = false,
    isAltActive: Boolean,
    isAltLocked: Boolean = false,
    onToggleCtrl: () -> Unit,
    onLockCtrl: () -> Unit = {},
    onToggleAlt: () -> Unit,
    onLockAlt: () -> Unit = {},
    onToggleKeyboard: () -> Unit = {},
    onKeyClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermuxToolbarBg)
    ) {
        // Subtle top border divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(TermuxDividerColor)
        )

        // Row 1 (7 keys): ESC, { }, = +, HOME, ↑, END, PGUP
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TermuxKeyCell(label = "ESC", modifier = Modifier.weight(1f), onClick = { onKeyClick("\u001b") })
            TermuxDivider()
            TermuxKeyCell(label = "{ }", modifier = Modifier.weight(1f), onClick = { onKeyClick("{}") })
            TermuxDivider()
            TermuxKeyCell(label = "= +", modifier = Modifier.weight(1f), onClick = { onKeyClick("=") })
            TermuxDivider()
            TermuxKeyCell(label = "HOME", modifier = Modifier.weight(1f), onClick = { onKeyClick("\u001b[1~") })
            TermuxDivider()
            TermuxKeyCell(label = "↑", isRepeatable = true, modifier = Modifier.weight(1f), onClick = { onKeyClick("\u001b[A") })
            TermuxDivider()
            TermuxKeyCell(label = "END", modifier = Modifier.weight(1f), onClick = { onKeyClick("\u001b[4~") })
            TermuxDivider()
            TermuxKeyCell(label = "PGUP", isRepeatable = true, modifier = Modifier.weight(1f), onClick = { onKeyClick("\u001b[5~") })
        }

        // Horizontal divider between Row 1 and Row 2
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(TermuxDividerColor)
        )

        // Row 2 (7 keys): TAB, CTRL, ALT, ←, ↓, →, PGDN
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TermuxKeyCell(label = "TAB", modifier = Modifier.weight(1f), onClick = { onKeyClick("\t") })
            TermuxDivider()
            TermuxKeyCell(
                label = "CTRL",
                isActive = isCtrlActive,
                isLocked = isCtrlLocked,
                modifier = Modifier.weight(1f),
                onClick = onToggleCtrl,
                onLongClick = onLockCtrl
            )
            TermuxDivider()
            TermuxKeyCell(
                label = "ALT",
                isActive = isAltActive,
                isLocked = isAltLocked,
                modifier = Modifier.weight(1f),
                onClick = onToggleAlt,
                onLongClick = onLockAlt
            )
            TermuxDivider()
            TermuxKeyCell(label = "←", isRepeatable = true, modifier = Modifier.weight(1f), onClick = { onKeyClick("\u001b[D") })
            TermuxDivider()
            TermuxKeyCell(label = "↓", isRepeatable = true, modifier = Modifier.weight(1f), onClick = { onKeyClick("\u001b[B") })
            TermuxDivider()
            TermuxKeyCell(label = "→", isRepeatable = true, modifier = Modifier.weight(1f), onClick = { onKeyClick("\u001b[C") })
            TermuxDivider()
            TermuxKeyCell(label = "PGDN", isRepeatable = true, modifier = Modifier.weight(1f), onClick = { onKeyClick("\u001b[6~") })
        }
    }
}

/**
 * Single flat key cell matching Termux ExtraKeysView button.
 * Supports auto-repeat on hold for navigation keys, and tap / long-press lock for modifier keys.
 */
@Composable
private fun TermuxKeyCell(
    label: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isLocked: Boolean = false,
    isRepeatable: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val isArrow = label in listOf("↑", "↓", "←", "→")
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    var isPressed by remember { mutableStateOf(false) }

    // Auto-repeat on hold (Termux DEFAULT_LONG_PRESS_REPEAT_DELAY = 80ms)
    LaunchedEffect(isPressed) {
        if (isPressed && isRepeatable) {
            currentOnClick()
            delay(400) // ViewConfiguration.getLongPressTimeout()
            while (isPressed) {
                currentOnClick()
                delay(80) // Termux repeat delay
            }
        }
    }

    val cellModifier = when {
        isRepeatable -> {
            modifier
                .fillMaxHeight()
                .background(
                    if (isActive) TermuxKeyActiveBg
                    else if (isPressed) TermuxKeyPressedBg
                    else Color.Transparent,
                    RectangleShape
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        try {
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                        } finally {
                            isPressed = false
                        }
                    }
                }
        }
        onLongClick != null -> {
            modifier
                .fillMaxHeight()
                .background(
                    if (isActive) TermuxKeyActiveBg
                    else if (isPressed) TermuxKeyPressedBg
                    else Color.Transparent,
                    RectangleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { currentOnClick() },
                        onLongPress = { currentOnLongClick?.invoke() }
                    )
                }
        }
        else -> {
            modifier
                .fillMaxHeight()
                .background(
                    if (isActive) TermuxKeyActiveBg else Color.Transparent,
                    RectangleShape
                )
                .clickable(onClick = onClick)
        }
    }

    Box(
        modifier = cellModifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isActive) TermuxKeyTextActive else TermuxKeyTextDefault,
            fontSize = if (isArrow) 16.sp else 13.5.sp,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (isArrow) 0.sp else 0.5.sp,
            maxLines = 1
        )

        // Termux-style locked indicator: crisp accent bar at bottom
        if (isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .align(Alignment.BottomCenter)
                    .background(TermuxKeyTextActive)
            )
        }
    }
}

/**
 * Hairline divider between keys.
 */
@Composable
private fun TermuxDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(0.5.dp)
            .background(TermuxDividerColor)
    )
}
