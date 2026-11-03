package com.cresteditor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(CrestSurfaceHeader)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open File Explorer",
                tint = CrestTextPrimary
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .padding(horizontal = 4.dp)
                .background(androidx.compose.ui.graphics.Color(0x1AFFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .clickable { onSearchClick() }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = CrestTextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = projectName,
                color = CrestTextActive,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onSaveClick) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = "Save File",
                tint = CrestTextPrimary
            )
        }

        IconButton(onClick = onRunClick) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Run / Preview",
                tint = CrestAccentGreen
            )
        }

        IconButton(onClick = onUndoClick) {
            Icon(
                imageVector = Icons.Default.Undo,
                contentDescription = "Undo",
                tint = CrestTextPrimary
            )
        }

        IconButton(onClick = onRedoClick) {
            Icon(
                imageVector = Icons.Default.Redo,
                contentDescription = "Redo",
                tint = CrestTextPrimary
            )
        }

        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = CrestTextPrimary
            )
        }

        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = CrestTextPrimary
            )
        }
    }
}
