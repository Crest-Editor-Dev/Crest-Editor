package com.cresteditor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cresteditor.core.settings.SettingDefinition
import com.cresteditor.core.settings.SettingType
import com.cresteditor.core.settings.SettingsManager
import org.json.JSONObject

// =============================================================================
// VS CODE THEME TOKENS (Direct 1:1 match with VS Code Preferences Editor)
// =============================================================================
private val VSCodeBg = Color(0xFF1E1E1E)                   // editor.background
private val VSCodeHeaderBg = Color(0xFF252526)             // titlebar / sideBar.background
private val VSCodeInputBg = Color(0xFF313131)              // settings.textInputBackground
private val VSCodeInputBorder = Color(0xFF3C3C3C)          // settings.textInputBorder
private val VSCodeFocusBorder = Color(0xFF007ACC)          // focusBorder
private val VSCodeModifiedIndicator = Color(0xFF007ACC)    // settings.modifiedItemIndicator
private val VSCodeForeground = Color(0xFFCCCCCC)           // editor.foreground
private val VSCodeHeaderForeground = Color(0xFFE7E7E7)     // settings.headerForeground
private val VSCodeDescription = Color(0xFF9D9D9D)          // descriptionForeground
private val VSCodeRowDivider = Color(0xFF282828)           // settings.rowBorder
private val VSCodeBadgeBg = Color(0xFF2D2D2D)              // badge.background
private val VSCodeBadgeFg = Color(0xFF858585)              // badge.foreground
private val VSCodeErrorFg = Color(0xFFF14C4C)              // errorForeground
private val VSCodeLinkFg = Color(0xFF3794FF)               // textLink.foreground

/**
 * Pixel-perfect VS Code Settings UI for CrestEditor.
 * 100% faithful to VS Code Desktop Preferences Editor on mobile / narrow screens:
 * - Search bar directly at the top with filter widget (@modified) and clear action
 * - NO horizontal TOC tabs (matches VS Code's narrow-width display: none for TOC)
 * - In-place anchored SelectBox DropdownMenu for Enum settings (NO center alert dialogs!)
 * - Settings grouped by Category Section Headers directly in the list
 * - Exact 3dp left blue vertical modified indicator bar (is-configured)
 * - Square VS Code checkboxes, flat select boxes, and border-highlighted inputs
 * - Setting action menu (Reset Setting, Copy Setting ID, Copy as JSON)
 * - Seamless "Open Settings (JSON)" editor mode
 */
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    BackHandler { onClose() }

    // State observation from SettingsManager
    val settingsState by settingsManager.settingsState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isJsonMode by remember { mutableStateOf(false) }

    // Top action menu & search filter menu
    var showTopMenu by remember { mutableStateOf(false) }
    var showSearchFilterMenu by remember { mutableStateOf(false) }
    var showResetAllConfirm by remember { mutableStateOf(false) }

    if (isJsonMode) {
        // VS Code settings.json Editor View
        VSCodeJsonEditorView(
            settingsManager = settingsManager,
            onCloseJson = { isJsonMode = false },
            onSaveSuccess = {
                Toast.makeText(context, "settings.json saved", Toast.LENGTH_SHORT).show()
                isJsonMode = false
            }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VSCodeBg)
    ) {
        // ---------------------------------------------------------------------
        // 1. VS Code Settings Header
        // ---------------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VSCodeHeaderBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to Editor",
                    tint = VSCodeForeground,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "Settings",
                color = VSCodeHeaderForeground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.weight(1f))

            // Open Settings (JSON) button - iconic VS Code "{}" file icon
            IconButton(
                onClick = { isJsonMode = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "Open Settings (JSON)",
                    tint = VSCodeForeground,
                    modifier = Modifier.size(18.dp)
                )
            }

            // More Actions (...)
            Box {
                IconButton(
                    onClick = { showTopMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Actions",
                        tint = VSCodeForeground,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showTopMenu,
                    onDismissRequest = { showTopMenu = false },
                    modifier = Modifier.background(VSCodeHeaderBg)
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Settings (JSON)", color = VSCodeForeground, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Code, null, tint = VSCodeForeground, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showTopMenu = false
                            isJsonMode = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Show Modified Only (@modified)", color = VSCodeForeground, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.FilterList, null, tint = VSCodeForeground, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showTopMenu = false
                            searchQuery = "@modified"
                        }
                    )
                    HorizontalDivider(color = VSCodeRowDivider)
                    DropdownMenuItem(
                        text = { Text("Reset All Settings", color = VSCodeErrorFg, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.RestartAlt, null, tint = VSCodeErrorFg, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showTopMenu = false
                            showResetAllConfirm = true
                        }
                    )
                }
            }
        }

        HorizontalDivider(color = VSCodeRowDivider, thickness = 1.dp)

        // ---------------------------------------------------------------------
        // 2. VS Code Search Box (Exact search-container with filter widget)
        // ---------------------------------------------------------------------
        var isSearchFocused by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(VSCodeInputBg, RoundedCornerShape(2.dp))
                    .border(
                        width = 1.dp,
                        color = if (isSearchFocused) VSCodeFocusBorder else VSCodeInputBorder,
                        shape = RoundedCornerShape(2.dp)
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isSearchFocused) VSCodeForeground else VSCodeDescription,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search settings",
                            color = VSCodeDescription,
                            fontSize = 13.sp
                        )
                    }

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isSearchFocused = it.isFocused },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = VSCodeHeaderForeground,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.SansSerif
                        ),
                        cursorBrush = SolidColor(VSCodeFocusBorder),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )
                }

                // Clear button
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = VSCodeDescription,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // VS Code Search Filter Widget (codicon-filter)
                Box {
                    IconButton(
                        onClick = { showSearchFilterMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter Settings",
                            tint = if (searchQuery.contains("@modified")) VSCodeFocusBorder else VSCodeDescription,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showSearchFilterMenu,
                        onDismissRequest = { showSearchFilterMenu = false },
                        modifier = Modifier.background(VSCodeHeaderBg)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Filter: @modified", color = VSCodeForeground, fontSize = 13.sp) },
                            onClick = {
                                showSearchFilterMenu = false
                                searchQuery = if (searchQuery.contains("@modified")) "" else "@modified"
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Filters", color = VSCodeDescription, fontSize = 13.sp) },
                            onClick = {
                                showSearchFilterMenu = false
                                searchQuery = ""
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = VSCodeRowDivider, thickness = 1.dp)

        // ---------------------------------------------------------------------
        // 3. Settings Tree List (Grouped by Category Section Headers, No Tabs!)
        // ---------------------------------------------------------------------
        val isModifiedFilter = searchQuery.contains("@modified", ignoreCase = true)
        val cleanQuery = searchQuery.replace("@modified", "", ignoreCase = true).trim()

        val groupedSettings = remember(cleanQuery, isModifiedFilter, settingsState) {
            val filtered = SettingsManager.SCHEMA.filter { def ->
                val matchesModified = !isModifiedFilter || settingsManager.isModified(def.key)
                val matchesSearch = cleanQuery.isEmpty() ||
                        def.title.contains(cleanQuery, ignoreCase = true) ||
                        def.key.contains(cleanQuery, ignoreCase = true) ||
                        def.category.contains(cleanQuery, ignoreCase = true) ||
                        def.description.contains(cleanQuery, ignoreCase = true)

                matchesModified && matchesSearch
            }

            // Group by category, keeping standard order
            filtered.groupBy { it.category }
        }

        if (groupedSettings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isModifiedFilter)
                        "No settings have been modified from defaults."
                    else
                        "No settings found matching '$searchQuery'.",
                    color = VSCodeDescription,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                groupedSettings.forEach { (category, settingsList) ->
                    // VS Code Settings Group Header (settings-group-title-label)
                    item(key = "header_$category") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(VSCodeBg)
                                .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 6.dp)
                        ) {
                            Text(
                                text = category,
                                color = VSCodeHeaderForeground,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Setting items under this category
                    items(settingsList, key = { it.key }) { def ->
                        val isModified = settingsManager.isModified(def.key)
                        val currentValue = settingsState[def.key] ?: def.defaultValue

                        VSCodeSettingRow(
                            definition = def,
                            currentValue = currentValue,
                            isModified = isModified,
                            onValueChange = { newVal ->
                                settingsManager.set(def.key, newVal)
                            },
                            onReset = {
                                settingsManager.reset(def.key)
                            }
                        )
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Reset All Confirmation Dialog
    // -------------------------------------------------------------------------
    if (showResetAllConfirm) {
        AlertDialog(
            onDismissRequest = { showResetAllConfirm = false },
            title = { Text("Reset all settings?", color = VSCodeHeaderForeground, fontSize = 15.sp) },
            text = {
                Text(
                    "This will restore all configuration in settings.json to their default values.",
                    color = VSCodeDescription,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsManager.resetAll()
                        showResetAllConfirm = false
                        Toast.makeText(context, "All settings restored to defaults", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reset All", color = VSCodeErrorFg, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllConfirm = false }) {
                    Text("Cancel", color = VSCodeForeground, fontSize = 13.sp)
                }
            },
            containerColor = VSCodeHeaderBg,
            shape = RoundedCornerShape(3.dp)
        )
    }
}

// =============================================================================
// INDIVIDUAL VS CODE SETTING ROW (setting-item)
// =============================================================================
@Composable
private fun VSCodeSettingRow(
    definition: SettingDefinition,
    currentValue: Any,
    isModified: Boolean,
    onValueChange: (Any) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    var showActionMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VSCodeBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            // -----------------------------------------------------------------
            // Left Modified Indicator Bar (Exact VS Code 3dp strip)
            // -----------------------------------------------------------------
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(64.dp)
                    .background(if (isModified) VSCodeModifiedIndicator else Color.Transparent)
            )

            Spacer(modifier = Modifier.width(11.dp))

            // -----------------------------------------------------------------
            // Main Setting Content (setting-item-contents)
            // -----------------------------------------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 14.dp)
            ) {
                // Title & Key + Gear Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category: Label (e.g. "Files: Auto Save")
                    Text(
                        text = definition.title,
                        color = VSCodeHeaderForeground,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // ID / Key badge
                    Text(
                        text = definition.key,
                        color = VSCodeBadgeFg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Gear / Context Actions
                    Box {
                        IconButton(
                            onClick = { showActionMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isModified) Icons.Default.SettingsBackupRestore else Icons.Default.MoreHoriz,
                                contentDescription = if (isModified) "Reset Setting" else "More",
                                tint = if (isModified) VSCodeModifiedIndicator else VSCodeBadgeFg,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showActionMenu,
                            onDismissRequest = { showActionMenu = false },
                            modifier = Modifier.background(VSCodeHeaderBg)
                        ) {
                            if (isModified) {
                                DropdownMenuItem(
                                    text = { Text("Reset Setting", color = VSCodeForeground, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null, tint = VSCodeForeground, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        showActionMenu = false
                                        onReset()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Copy Setting ID", color = VSCodeForeground, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = VSCodeForeground, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showActionMenu = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("setting-id", definition.key))
                                    Toast.makeText(context, "Copied: ${definition.key}", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy as JSON", color = VSCodeForeground, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Code, null, tint = VSCodeForeground, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showActionMenu = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val jsonSnippet = "\"${definition.key}\": ${if (currentValue is String) "\"$currentValue\"" else currentValue}"
                                    clipboard.setPrimaryClip(ClipData.newPlainText("setting-json", jsonSnippet))
                                    Toast.makeText(context, "Copied JSON snippet", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Description
                Text(
                    text = definition.description,
                    color = VSCodeDescription,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Control Widgets based on Type
                when (definition.type) {
                    SettingType.BOOLEAN -> {
                        // VS Code Checkbox (Square dark box with checkmark)
                        val isChecked = currentValue as? Boolean ?: false
                        Row(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onValueChange(!isChecked) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(
                                        if (isChecked) VSCodeFocusBorder else VSCodeInputBg,
                                        RoundedCornerShape(2.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isChecked) VSCodeFocusBorder else VSCodeInputBorder,
                                        shape = RoundedCornerShape(2.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChecked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = if (isChecked) "Enabled" else "Disabled",
                                color = if (isChecked) VSCodeForeground else VSCodeDescription,
                                fontSize = 13.sp
                            )
                        }
                    }

                    SettingType.ENUM -> {
                        // VS Code In-Place Anchored SelectBox Dropdown (Exact VS Code desktop behavior!)
                        var isDropdownExpanded by remember { mutableStateOf(false) }

                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .background(VSCodeInputBg, RoundedCornerShape(2.dp))
                                    .border(
                                        width = 1.dp,
                                        color = if (isDropdownExpanded) VSCodeFocusBorder else VSCodeInputBorder,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .clickable { isDropdownExpanded = !isDropdownExpanded }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = currentValue.toString(),
                                    color = VSCodeForeground,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Dropdown",
                                    tint = if (isDropdownExpanded) VSCodeFocusBorder else VSCodeDescription,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false },
                                modifier = Modifier
                                    .background(VSCodeInputBg)
                                    .border(1.dp, VSCodeInputBorder, RoundedCornerShape(2.dp))
                                    .heightIn(max = 240.dp)
                            ) {
                                definition.options.forEach { opt ->
                                    val isSelected = opt == currentValue.toString()
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = opt,
                                                    color = if (isSelected) VSCodeFocusBorder else VSCodeForeground,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    fontSize = 13.sp
                                                )
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = VSCodeFocusBorder,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            onValueChange(opt)
                                            isDropdownExpanded = false
                                        },
                                        modifier = Modifier.background(
                                            if (isSelected) VSCodeFocusBorder.copy(alpha = 0.12f) else Color.Transparent
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    SettingType.INT -> {
                        // VS Code Number Input with mini stepper
                        val currentNum = (currentValue as? Number)?.toInt() ?: (definition.defaultValue as Int)
                        val min = definition.min ?: 0
                        val max = definition.max ?: 99999
                        val step = if (definition.key.contains("Delay")) 500 else 1

                        var isEditing by remember { mutableStateOf(false) }
                        var textInput by remember(currentNum) { mutableStateOf(currentNum.toString()) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(32.dp)
                                    .background(VSCodeInputBg, RoundedCornerShape(2.dp))
                                    .border(
                                        width = 1.dp,
                                        color = if (isEditing) VSCodeFocusBorder else VSCodeInputBorder,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .padding(start = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = textInput,
                                    onValueChange = { newVal ->
                                        textInput = newVal
                                        val parsed = newVal.toIntOrNull()
                                        if (parsed != null && parsed in min..max) {
                                            onValueChange(parsed)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusChanged { isEditing = it.isFocused },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = TextStyle(
                                        color = VSCodeHeaderForeground,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    cursorBrush = SolidColor(VSCodeFocusBorder)
                                )

                                // Mini Stepper Buttons
                                IconButton(
                                    onClick = {
                                        if (currentNum - step >= min) onValueChange(currentNum - step)
                                    },
                                    enabled = currentNum > min,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Decrease",
                                        tint = if (currentNum > min) VSCodeForeground else VSCodeDescription,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (currentNum + step <= max) onValueChange(currentNum + step)
                                    },
                                    enabled = currentNum < max,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase",
                                        tint = if (currentNum < max) VSCodeForeground else VSCodeDescription,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = if (definition.key.contains("Delay")) "ms" else if (definition.key.contains("FontSize")) "px" else "",
                                color = VSCodeDescription,
                                fontSize = 12.sp
                            )
                        }
                    }

                    SettingType.STRING -> {
                        // VS Code Text Input
                        var isTextFocused by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(VSCodeInputBg, RoundedCornerShape(2.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (isTextFocused) VSCodeFocusBorder else VSCodeInputBorder,
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = currentValue.toString(),
                                onValueChange = { onValueChange(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isTextFocused = it.isFocused },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = VSCodeHeaderForeground,
                                    fontSize = 13.sp
                                ),
                                cursorBrush = SolidColor(VSCodeFocusBorder)
                            )
                        }
                    }
                }
            }
        }

        // Row Separator (VS Code row divider)
        HorizontalDivider(color = VSCodeRowDivider, thickness = 1.dp)
    }
}

// =============================================================================
// VS CODE settings.json CODE EDITOR VIEW ("Open Settings (JSON)")
// =============================================================================
@Composable
private fun VSCodeJsonEditorView(
    settingsManager: SettingsManager,
    onCloseJson: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    var rawText by remember { mutableStateOf(settingsManager.getRawJson()) }
    var parseError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VSCodeBg)
    ) {
        // Tab Bar for settings.json
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VSCodeHeaderBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCloseJson,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to UI",
                    tint = VSCodeForeground,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Tab badge
            Row(
                modifier = Modifier
                    .background(VSCodeBg, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = Color(0xFFCBCB41), // JSON yellow icon
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "settings.json",
                    color = VSCodeHeaderForeground,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Prettify / Format JSON
            TextButton(
                onClick = {
                    try {
                        val parsed = JSONObject(rawText)
                        rawText = parsed.toString(2)
                        parseError = null
                    } catch (e: Exception) {
                        parseError = "Syntax Error: ${e.message}"
                    }
                }
            ) {
                Text("Prettify", color = VSCodeLinkFg, fontSize = 12.sp)
            }

            // Save JSON
            TextButton(
                onClick = {
                    val res = settingsManager.setRawJson(rawText)
                    if (res.isSuccess) {
                        onSaveSuccess()
                    } else {
                        parseError = res.exceptionOrNull()?.message ?: "Invalid JSON format"
                    }
                }
            ) {
                Text("Save", color = VSCodeFocusBorder, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        HorizontalDivider(color = VSCodeRowDivider, thickness = 1.dp)

        // Error Banner if invalid
        if (parseError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VSCodeErrorFg.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = VSCodeErrorFg,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = parseError ?: "",
                    color = VSCodeErrorFg,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Monospace Code Editor Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            BasicTextField(
                value = rawText,
                onValueChange = {
                    rawText = it
                    parseError = null
                },
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(
                    color = VSCodeHeaderForeground,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                ),
                cursorBrush = SolidColor(VSCodeFocusBorder)
            )
        }
    }
}
