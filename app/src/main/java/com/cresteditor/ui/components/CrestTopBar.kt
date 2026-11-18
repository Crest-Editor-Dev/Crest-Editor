package com.cresteditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cresteditor.ui.theme.*

@Composable
fun CrestTopBar(
    projectName: String = "Crest Project",
    onMenuClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onRunClick: () -> Unit = {},
    onUndoClick: () -> Unit = {},
    onRedoClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(CrestSurfaceHeader)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val isWideScreen = totalWidth >= 600.dp

        // =====================================================================
        // 1. LEFT CONTROLS: Menu (Drawer) + VS Code Nav (Undo / Redo)
        // =====================================================================
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open File Explorer",
                    tint = CrestTextPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Undo button (VS Code navigation back position)
            IconButton(
                onClick = onUndoClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = CrestTextPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }

            // Redo button (VS Code navigation forward position)
            IconButton(
                onClick = onRedoClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = CrestTextPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        // =====================================================================
        // 2. CENTER: Responsive VS Code Command Center Search Bar
        // Math:
        // - Small Screen (<600dp): takes remaining space after left items (~106dp)
        //   leaving right margin, capped at 380dp.
        // - Large Screen (>=600dp): clamped to ~38% of screen (280dp-520dp) and
        //   strictly centered in the top bar (never occupies full screen width).
        // =====================================================================
        val searchWidth = if (isWideScreen) {
            (totalWidth * 0.38f).coerceIn(280.dp, 520.dp)
        } else {
            (totalWidth - 106.dp - 14.dp).coerceIn(160.dp, 380.dp)
        }

        val searchModifier = if (isWideScreen) {
            Modifier
                .align(Alignment.Center)
                .width(searchWidth)
        } else {
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 106.dp)
                .width(searchWidth)
        }

        Row(
            modifier = searchModifier
                .height(30.dp)
                .background(
                    color = Color(0x1F282828),
                    shape = RoundedCornerShape(6.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0x2EFFFFFF),
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable { onSearchClick() }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = CrestTextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = projectName,
                color = CrestTextActive,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
