package com.cresteditor.ui.components.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cresteditor.icons.Codicons
import com.cresteditor.ui.components.SideBarView

/**
 * Individual Activity Bar Icon with Left Indicator Line.
 */
@Composable
fun ActivityBarItem(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Iconic VS Code 2dp vertical active indicator on the far left edge
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .background(DrawerTheme.ActivityBarIndicator)
                    .align(Alignment.CenterStart)
            )
        }

        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) DrawerTheme.ActivityBarFg else DrawerTheme.ActivityBarInactive,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * VS Code Activity Bar (48dp dock on left side).
 */
@Composable
fun ActivityBar(
    activeView: SideBarView,
    onViewSelect: (SideBarView) -> Unit,
    onTerminalClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(DrawerTheme.ActivityBarBg)
            .border(
                width = 1.dp,
                color = DrawerTheme.SideBarBorder,
                shape = RectangleShape
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Explorer Item
        ActivityBarItem(
            icon = Codicons.Files,
            contentDescription = "Explorer",
            isSelected = activeView == SideBarView.EXPLORER,
            onClick = { onViewSelect(SideBarView.EXPLORER) }
        )

        // Search Item
        ActivityBarItem(
            icon = Codicons.Search,
            contentDescription = "Search",
            isSelected = activeView == SideBarView.SEARCH,
            onClick = { onViewSelect(SideBarView.SEARCH) }
        )

        // Source Control (Git) Item
        ActivityBarItem(
            icon = Codicons.SourceControl,
            contentDescription = "Source Control",
            isSelected = activeView == SideBarView.SOURCE_CONTROL,
            onClick = { onViewSelect(SideBarView.SOURCE_CONTROL) }
        )

        // Terminal Launcher
        ActivityBarItem(
            icon = Codicons.Terminal,
            contentDescription = "Terminal",
            isSelected = false,
            onClick = onTerminalClick
        )

        Spacer(modifier = Modifier.weight(1f))

        // Settings (Gear) at bottom
        ActivityBarItem(
            icon = Codicons.SettingsGear,
            contentDescription = "Settings",
            isSelected = false,
            onClick = onSettingsClick
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}
