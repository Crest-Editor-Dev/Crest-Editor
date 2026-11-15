package com.cresteditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// =============================================================================
// VS CODE / ZED MODAL TOKENS (Compact, High-Contrast Slate)
// =============================================================================
private val DialogBackground = Color(0xFF252526)      // editorWidget.background
private val DialogBorder = Color(0xFF383838)          // editorWidget.border
private val DialogTitle = Color(0xFFFFFFFF)           // foreground active
private val DialogMessage = Color(0xFFCCCCCC)         // foreground muted
private val ButtonPrimaryBg = Color(0xFF007ACC)       // button.background
private val ButtonPrimaryPressed = Color(0xFF0062A3)
private val ButtonDestructiveBg = Color(0xFFF14C4C)   // error / delete
private val ButtonDestructivePressed = Color(0xFFD03A3A)
private val ButtonSecondaryBg = Color(0xFF333333)     // button.secondaryBackground
private val ButtonSecondaryBorder = Color(0xFF444444)
private val ButtonSecondaryPressed = Color(0xFF3D3D3D)
private val ButtonSecondaryText = Color(0xFFCCCCCC)

/**
 * Unified VS Code / Zed Styled Modal Dialog for Crest-Editor.
 * Ensures strict design consistency across all dialogs:
 * - 6dp rounded corners with 1px #383838 border
 * - Compact typography (15sp title, 13sp body)
 * - Standardized primary, destructive, and secondary action buttons
 */
@Composable
fun VSCodeDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmButtonText: String = "OK",
    dismissButtonText: String? = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DialogBackground)
                .border(1.dp, DialogBorder, RoundedCornerShape(6.dp))
                .then(modifier)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DialogTitle
                )

                // Message or Custom Content
                if (message != null) {
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = DialogMessage
                    )
                }

                content?.invoke(this)

                // Button Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!dismissButtonText.isNullOrBlank()) {
                        VSCodeActionButton(
                            text = dismissButtonText,
                            onClick = {
                                onDismiss?.invoke() ?: onDismissRequest()
                            },
                            isPrimary = false
                        )
                    }

                    VSCodeActionButton(
                        text = confirmButtonText,
                        onClick = onConfirm,
                        isPrimary = true,
                        isDestructive = isDestructive
                    )
                }
            }
        }
    }
}

/**
 * Compact VS Code Styled Flat Action Button.
 */
@Composable
fun VSCodeActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    isDestructive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val backgroundColor = when {
        isDestructive && isPressed -> ButtonDestructivePressed
        isDestructive -> ButtonDestructiveBg
        isPrimary && isPressed -> ButtonPrimaryPressed
        isPrimary -> ButtonPrimaryBg
        isPressed -> ButtonSecondaryPressed
        else -> ButtonSecondaryBg
    }

    val textColor = if (isPrimary || isDestructive) Color.White else ButtonSecondaryText
    val borderColor = if (isPrimary || isDestructive) Color.Transparent else ButtonSecondaryBorder

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
