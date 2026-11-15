package com.cresteditor.ui.components.drawer

import androidx.compose.ui.graphics.Color

/**
 * VS Code Theme Constants for Activity Bar & Primary Side Bar.
 */
internal object DrawerTheme {
    val ActivityBarBg = Color(0xFF181818)          // activityBar.background
    val ActivityBarFg = Color(0xFFFFFFFF)          // activityBar.foreground
    val ActivityBarInactive = Color(0xFF858585)    // activityBar.inactiveForeground
    val ActivityBarIndicator = Color(0xFFFFFFFF)   // activityBar.activeBorder (white strip)

    val SideBarBg = Color(0xFF252526)              // sideBar.background
    val SideBarTitle = Color(0xFFBBBBBB)           // sideBarTitle.foreground
    val SideBarBorder = Color(0xFF1E1E1E)          // sideBar.border
    val SectionHeaderBg = Color(0x33808080)        // sideBarSectionHeader.background
    val SectionHeaderFg = Color(0xFFCCCCCC)        // sideBarSectionHeader.foreground

    val ListSelectionBg = Color(0xFF04395E)        // list.activeSelectionBackground
    val ListHoverBg = Color(0xFF2A2D2E)            // list.hoverBackground
    val TextPrimary = Color(0xFFCCCCCC)            // editor.foreground
    val TextActive = Color(0xFFFFFFFF)
    val InputBg = Color(0xFF313131)
    val InputBorder = Color(0xFF3C3C3C)
    val FocusBorder = Color(0xFF007ACC)
    val TreeIndentGuideActive = Color(0xFF6E6E6E)  // tree.indentGuidesStroke
    val ErrorBorder = Color(0xFFBE1100)
    val ErrorTooltipBg = Color(0xFF5A1D1D)
}
