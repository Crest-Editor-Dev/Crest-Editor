package com.cresteditor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cresteditor.core.commands.CommandItem
import com.cresteditor.core.commands.CommandRegistry
import com.cresteditor.core.settings.SettingsManager
import com.cresteditor.editor.MonacoBridge
import com.cresteditor.runtime.RootfsManager
import com.cresteditor.runtime.TerminalSessionManager
import com.cresteditor.ui.MainViewModel
import com.cresteditor.ui.TerminalScreen
import com.cresteditor.ui.SettingsScreen
import com.cresteditor.ui.components.*
import com.cresteditor.ui.components.VSCodeCommandPalette
import com.cresteditor.ui.theme.*
import com.cresteditor.workspace.WorkspaceManager
import kotlinx.coroutines.launch
import java.io.File

enum class ActiveFocus { NONE, MONACO, TERMINAL }

class MainActivity : ComponentActivity() {

    private lateinit var rootfsManager: RootfsManager
    private lateinit var settingsManager: SettingsManager
    private var webView: WebView? = null
    private var onToggleCommandPalette: ((initialQuery: String) -> Unit)? = null

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            if (event.keyCode == android.view.KeyEvent.KEYCODE_F1 ||
                (event.isCtrlPressed && event.isShiftPressed && event.keyCode == android.view.KeyEvent.KEYCODE_P)
            ) {
                onToggleCommandPalette?.invoke(">")
                return true
            }
            if (event.isCtrlPressed && !event.isShiftPressed && event.keyCode == android.view.KeyEvent.KEYCODE_P) {
                onToggleCommandPalette?.invoke("")
                return true
            }
            if (event.isCtrlPressed && event.keyCode == android.view.KeyEvent.KEYCODE_G) {
                onToggleCommandPalette?.invoke(":")
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.let {
            it.stopLoading()
            it.destroy()
        }
        webView = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        rootfsManager = RootfsManager(this)
        settingsManager = SettingsManager(applicationContext)

        setContent {
            CrestTheme {
                val context = LocalContext.current
                val viewModel: MainViewModel = viewModel()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                val fileTreeItems by viewModel.fileTreeItems.collectAsState()
                val openTabs by viewModel.openTabs.collectAsState()
                val activeTabId by viewModel.activeTabId.collectAsState()
                val activeLanguage by viewModel.activeLanguage.collectAsState()
                val showTerminal by viewModel.showTerminal.collectAsState()
                val showSettings by viewModel.showSettings.collectAsState()

                var showCommandPalette by remember { mutableStateOf(false) }
                var commandPaletteInitialQuery by remember { mutableStateOf(">") }
                var currentLineCount by remember { mutableStateOf(1) }
                val commandRegistry = remember { CommandRegistry() }

                DisposableEffect(Unit) {
                    onToggleCommandPalette = { prefix ->
                        commandPaletteInitialQuery = prefix
                        MonacoBridge.getLineCount(webView) { count ->
                            currentLineCount = count
                        }
                        showCommandPalette = true
                    }
                    onDispose { onToggleCommandPalette = null }
                }

                LaunchedEffect(openTabs, activeTabId, drawerState.isOpen, showTerminal, showSettings) {
                    commandRegistry.clear()
                    commandRegistry.registerAll(
                        listOf(
                            // -------------------------------------------------
                            // 1. FILE COMMANDS
                            // -------------------------------------------------
                            CommandItem(
                                id = "workbench.action.files.save",
                                title = "File: Save",
                                category = "File",
                                shortcut = "Ctrl+S"
                            ) {
                                viewModel.requestSave(context, webView)
                            },
                            CommandItem(
                                id = "workbench.action.files.newFile",
                                title = "File: New File",
                                category = "File"
                            ) {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open()
                                }
                            },
                            CommandItem(
                                id = "workbench.action.files.newFolder",
                                title = "File: New Folder",
                                category = "File"
                            ) {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open()
                                }
                            },
                            CommandItem(
                                id = "workbench.action.closeActiveEditor",
                                title = "File: Close Active Tab",
                                category = "File",
                                shortcut = "Ctrl+W"
                            ) {
                                val currentTab = viewModel.activeTabId.value
                                if (currentTab.isNotEmpty()) {
                                    viewModel.closeTab(currentTab, webView)
                                }
                            },

                            // -------------------------------------------------
                            // 2. VIEW / WORKBENCH COMMANDS
                            // -------------------------------------------------
                            CommandItem(
                                id = "workbench.action.toggleSidebar",
                                title = "View: Toggle Primary Side Bar (Explorer)",
                                category = "View",
                                shortcut = "Ctrl+B"
                            ) {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            },
                            CommandItem(
                                id = "workbench.action.terminal.toggleTerminal",
                                title = "View: Toggle Terminal",
                                category = "View",
                                shortcut = "Ctrl+`"
                            ) {
                                viewModel.setShowTerminal(!viewModel.showTerminal.value)
                            },
                            CommandItem(
                                id = "workbench.action.openSettings",
                                title = "Preferences: Open Settings (UI)",
                                category = "Preferences",
                                shortcut = "Ctrl+,"
                            ) {
                                viewModel.setShowSettings(true)
                            },
                            CommandItem(
                                id = "workbench.action.reloadWindow",
                                title = "Developer: Reload Window",
                                category = "Developer"
                            ) {
                                viewModel.refreshWorkspace(this@MainActivity)
                            },

                            // -------------------------------------------------
                            // 3. CODE FORMATTING & COMMENTS
                            // -------------------------------------------------
                            CommandItem(
                                id = "editor.action.formatDocument",
                                title = "Editor: Format Document",
                                category = "Editor",
                                shortcut = "Shift+Alt+F"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.formatDocument")
                            },
                            CommandItem(
                                id = "editor.action.formatSelection",
                                title = "Editor: Format Selection",
                                category = "Editor"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.formatSelection")
                            },
                            CommandItem(
                                id = "editor.action.commentLine",
                                title = "Editor: Toggle Line Comment",
                                category = "Editor",
                                shortcut = "Ctrl+/"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.commentLine")
                            },
                            CommandItem(
                                id = "editor.action.blockComment",
                                title = "Editor: Toggle Block Comment",
                                category = "Editor",
                                shortcut = "Shift+Alt+A"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.blockComment")
                            },
                            CommandItem(
                                id = "editor.action.indentLines",
                                title = "Editor: Indent Line",
                                category = "Editor",
                                shortcut = "Ctrl+]"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.indentLines")
                            },
                            CommandItem(
                                id = "editor.action.outdentLines",
                                title = "Editor: Outdent Line",
                                category = "Editor",
                                shortcut = "Ctrl+["
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.outdentLines")
                            },

                            // -------------------------------------------------
                            // 4. LINE EDITING & MANIPULATION
                            // -------------------------------------------------
                            CommandItem(
                                id = "editor.action.copyLinesDownAction",
                                title = "Editor: Duplicate Line Down",
                                category = "Editor",
                                shortcut = "Shift+Alt+Down"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.copyLinesDownAction")
                            },
                            CommandItem(
                                id = "editor.action.copyLinesUpAction",
                                title = "Editor: Duplicate Line Up",
                                category = "Editor",
                                shortcut = "Shift+Alt+Up"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.copyLinesUpAction")
                            },
                            CommandItem(
                                id = "editor.action.moveLinesDownAction",
                                title = "Editor: Move Line Down",
                                category = "Editor",
                                shortcut = "Alt+Down"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.moveLinesDownAction")
                            },
                            CommandItem(
                                id = "editor.action.moveLinesUpAction",
                                title = "Editor: Move Line Up",
                                category = "Editor",
                                shortcut = "Alt+Up"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.moveLinesUpAction")
                            },
                            CommandItem(
                                id = "editor.action.deleteLines",
                                title = "Editor: Delete Line",
                                category = "Editor",
                                shortcut = "Ctrl+Shift+K"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.deleteLines")
                            },
                            CommandItem(
                                id = "editor.action.insertLineAfter",
                                title = "Editor: Insert Line Below",
                                category = "Editor",
                                shortcut = "Ctrl+Enter"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.insertLineAfter")
                            },
                            CommandItem(
                                id = "editor.action.insertLineBefore",
                                title = "Editor: Insert Line Above",
                                category = "Editor",
                                shortcut = "Ctrl+Shift+Enter"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.insertLineBefore")
                            },
                            CommandItem(
                                id = "editor.action.joinLines",
                                title = "Editor: Join Lines",
                                category = "Editor"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.joinLines")
                            },
                            CommandItem(
                                id = "editor.action.removeDuplicateLines",
                                title = "Editor: Delete Duplicate Lines",
                                category = "Editor"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.removeDuplicateLines")
                            },
                            CommandItem(
                                id = "editor.action.trimTrailingWhitespace",
                                title = "Editor: Trim Trailing Whitespace",
                                category = "Editor"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.trimTrailingWhitespace")
                            },
                            CommandItem(
                                id = "editor.action.sortLinesAscending",
                                title = "Editor: Sort Lines Ascending",
                                category = "Editor"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.sortLinesAscending")
                            },
                            CommandItem(
                                id = "editor.action.sortLinesDescending",
                                title = "Editor: Sort Lines Descending",
                                category = "Editor"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.sortLinesDescending")
                            },

                            // -------------------------------------------------
                            // 5. TEXT & CASE TRANSFORMATIONS
                            // -------------------------------------------------
                            CommandItem(
                                id = "editor.action.transformToUppercase",
                                title = "Editor: Transform to Uppercase",
                                category = "Editor"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.transformToUppercase")
                            },
                            CommandItem(
                                id = "editor.action.transformToLowercase",
                                title = "Editor: Transform to Lowercase",
                                category = "Editor"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.transformToLowercase")
                            },
                            CommandItem(
                                id = "editor.action.transformToTitlecase",
                                title = "Editor: Transform to Title Case",
                                category = "Editor"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.transformToTitlecase")
                            },
                            CommandItem(
                                id = "editor.action.transformToCamelcase",
                                title = "Editor: Transform to Camel Case",
                                category = "Editor"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.transformToCamelcase")
                            },
                            CommandItem(
                                id = "editor.action.transformToSnakecase",
                                title = "Editor: Transform to Snake Case",
                                category = "Editor"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.transformToSnakecase")
                            },
                            CommandItem(
                                id = "editor.action.transformToKebabcase",
                                title = "Editor: Transform to Kebab Case",
                                category = "Editor"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.transformToKebabcase")
                            },

                            // -------------------------------------------------
                            // 6. SEARCH, NAVIGATION, MULTI-CURSOR & FOLDING
                            // -------------------------------------------------
                            CommandItem(
                                id = "actions.find",
                                title = "Editor: Find",
                                category = "Editor",
                                shortcut = "Ctrl+F"
                            ) {
                                MonacoBridge.triggerAction(webView, "actions.find")
                            },
                            CommandItem(
                                id = "editor.action.selectAll",
                                title = "Editor: Select All",
                                category = "Editor",
                                shortcut = "Ctrl+A"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.selectAll")
                            },
                            CommandItem(
                                id = "editor.action.addSelectionToNextFindMatch",
                                title = "Editor: Add Selection to Next Match",
                                category = "Editor",
                                shortcut = "Ctrl+D"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.addSelectionToNextFindMatch")
                            },
                            CommandItem(
                                id = "editor.action.changeAll",
                                title = "Editor: Change All Occurrences",
                                category = "Editor",
                                shortcut = "Ctrl+F2"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.changeAll")
                            },
                            CommandItem(
                                id = "workbench.action.quickOpen",
                                title = "File: Go to File...",
                                category = "File",
                                shortcut = "Ctrl+P"
                            ) {
                                onToggleCommandPalette?.invoke("")
                            },
                            CommandItem(
                                id = "editor.action.gotoLine",
                                title = "Editor: Go to Line...",
                                category = "Editor",
                                shortcut = "Ctrl+G"
                            ) {
                                onToggleCommandPalette?.invoke(":")
                            },
                            CommandItem(
                                id = "editor.action.jumpToBracket",
                                title = "Editor: Go to Matching Bracket",
                                category = "Editor",
                                shortcut = "Ctrl+Shift+\\"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.jumpToBracket")
                            },
                            CommandItem(
                                id = "editor.action.foldAll",
                                title = "Editor: Fold All",
                                category = "Editor",
                                shortcut = "Ctrl+K Ctrl+0"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.foldAll")
                            },
                            CommandItem(
                                id = "editor.action.unfoldAll",
                                title = "Editor: Unfold All",
                                category = "Editor",
                                shortcut = "Ctrl+K Ctrl+J"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.unfoldAll")
                            },
                            CommandItem(
                                id = "undo",
                                title = "Editor: Undo",
                                category = "Editor",
                                shortcut = "Ctrl+Z"
                            ) {
                                MonacoBridge.undo(webView)
                            },
                            CommandItem(
                                id = "redo",
                                title = "Editor: Redo",
                                category = "Editor",
                                shortcut = "Ctrl+Y"
                            ) {
                                MonacoBridge.redo(webView)
                            }
                        )
                    )
                }

                var isCtrlActive by remember { mutableStateOf(false) }
                var isCtrlLocked by remember { mutableStateOf(false) }
                var isAltActive by remember { mutableStateOf(false) }
                var isAltLocked by remember { mutableStateOf(false) }

                DisposableEffect(Unit) {
                    TerminalSessionManager.onModifiersChanged = { ctrl, alt ->
                        isCtrlActive = ctrl
                        isAltActive = alt
                        if (!ctrl) isCtrlLocked = false
                        if (!alt) isAltLocked = false
                    }
                    onDispose {
                        TerminalSessionManager.onModifiersChanged = null
                    }
                }

                val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
                val deleteTargetItem by viewModel.deleteTargetItem.collectAsState()
                val toastMessage by viewModel.toastMessage.collectAsState()

                val monacoBridge = remember {
                    MonacoBridge(
                        context = context.applicationContext,
                        onReadyCallback = {
                            MonacoBridge.applySettings(webView, settingsManager.getSettingsJsonObject())
                            val activePath = viewModel.activeTabId.value
                            if (activePath.isNotEmpty()) {
                                viewModel.openFile(File(activePath), webView)
                            } else {
                                val openTabs = viewModel.openTabs.value
                                if (openTabs.isNotEmpty()) {
                                    viewModel.openFile(File(openTabs.first().id), webView)
                                }
                            }
                        },
                        onContentChangedCallback = { _ ->
                            viewModel.markActiveTabModified(true)
                        },
                        onSaveRequestedCallback = { content ->
                            val activePath = viewModel.activeTabId.value
                            if (activePath.isNotEmpty()) {
                                viewModel.saveContent(context, activePath, content)
                            }
                        }
                    )
                }

                val workspaceDir = remember { WorkspaceManager.getWorkspaceDir(context) }

                LaunchedEffect(Unit) {
                    viewModel.loadWorkspace(context, webView)
                }

                DisposableEffect(settingsManager, webView) {
                    val listener: (String, Any) -> Unit = { _, _ ->
                        MonacoBridge.applySettings(webView, settingsManager.getSettingsJsonObject())
                    }
                    settingsManager.addChangeListener(listener)
                    onDispose {
                        settingsManager.removeChangeListener(listener)
                    }
                }

                LaunchedEffect(toastMessage) {
                    toastMessage?.let { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        viewModel.clearToastMessage()
                    }
                }

                // Delete Confirmation Dialog
                if (showDeleteDialog && deleteTargetItem != null) {
                    val isDir = deleteTargetItem!!.isDirectory
                    AlertDialog(
                        onDismissRequest = {
                            viewModel.closeDeleteDialog()
                        },
                        title = {
                            Text(
                                text = if (isDir) "Delete Folder" else "Delete File",
                                color = CrestTextActive
                            )
                        },
                        text = {
                            Text(
                                text = "Are you sure you want to delete '${deleteTargetItem!!.name}'? This cannot be undone.",
                                color = CrestTextSecondary
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.confirmDelete(context, webView)
                                }
                            ) {
                                Text("Delete", color = androidx.compose.ui.graphics.Color(0xFFF14C4C))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    viewModel.closeDeleteDialog()
                                }
                            ) {
                                Text("Cancel", color = CrestTextSecondary)
                            }
                        },
                        containerColor = CrestSurfaceHeader
                    )
                }

                var activeFocus by remember { mutableStateOf(ActiveFocus.NONE) }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = drawerState.isOpen,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            drawerShape = androidx.compose.ui.graphics.RectangleShape
                        ) {
                            CrestFileDrawer(
                                projectName = workspaceDir.name,
                                fileList = fileTreeItems,
                                activeTabId = activeTabId,
                                workspaceDir = workspaceDir,
                                onFileSelect = { item ->
                                    scope.launch {
                                        drawerState.close()
                                        if (!item.isDirectory) {
                                            activeFocus = ActiveFocus.MONACO
                                            viewModel.openFile(File(item.path), webView)
                                        }
                                    }
                                },
                                onCreateItem = { parentDir, name, isFolder ->
                                    viewModel.createNewItemDirect(parentDir, name, isFolder, webView, this@MainActivity)
                                },
                                onRenameItem = { item, newName ->
                                    viewModel.renameItemDirect(item, newName, webView, this@MainActivity)
                                },
                                onRefreshClick = {
                                    viewModel.refreshWorkspace(this@MainActivity)
                                },
                                onDeleteClick = { item ->
                                    viewModel.openDeleteDialog(item)
                                },
                                onTerminalClick = {
                                    scope.launch {
                                        drawerState.close()
                                        viewModel.setShowTerminal(true)
                                        activeFocus = ActiveFocus.TERMINAL
                                    }
                                },
                                onSettingsClick = {
                                    scope.launch {
                                        drawerState.close()
                                        viewModel.setShowSettings(true)
                                    }
                                },
                                onCloseDrawer = {
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CrestBackground)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                        ) {
                                CrestTopBar(
                                    projectName = "workspace / " + (openTabs.find { it.id == activeTabId }?.title ?: ""),
                                    onMenuClick = {
                                        scope.launch {
                                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                        }
                                    },
                                    onSaveClick = {
                                        viewModel.requestSave(context, webView)
                                    },
                                    onUndoClick = {
                                        MonacoBridge.undo(webView)
                                    },
                                    onRedoClick = {
                                        MonacoBridge.redo(webView)
                                    },
                                    onSettingsClick = {
                                        viewModel.setShowSettings(true)
                                    },
                                    onSearchClick = {
                                        onToggleCommandPalette?.invoke("")
                                    }
                                )
                                CrestTabBar(
                                    tabs = openTabs,
                                    activeTabId = activeTabId,
                                    onTabSelect = { tabId ->
                                        activeFocus = ActiveFocus.MONACO
                                        viewModel.openFile(File(tabId), webView)
                                    },
                                    onTabClose = { tabId ->
                                        viewModel.closeTab(tabId, webView)
                                    }
                                )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val totalContainerHeight = maxHeight

                                WorkspaceLayout(
                                    showTerminal = showTerminal,
                                    terminalFocused = activeFocus == ActiveFocus.TERMINAL,
                                    modifier = Modifier.fillMaxSize(),
                                    editor = {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            CrestEditorScreen(
                                                onWebViewCreated = { wv ->
                                                    webView = wv
                                                    monacoBridge.setWebView(wv)
                                                    if (activeTabId.isNotEmpty()) {
                                                        viewModel.openFile(File(activeTabId), wv)
                                                    }
                                                },
                                                bridge = monacoBridge,
                                                onEditorFocus = { activeFocus = ActiveFocus.MONACO }
                                            )

                                            if (!showTerminal && showSettings) {
                                                SettingsScreen(
                                                    settingsManager = settingsManager,
                                                    onClose = {
                                                        viewModel.setShowSettings(false)
                                                    },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }

                                if (!showTerminal && !showSettings && openTabs.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(CrestBackground),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Code,
                                                contentDescription = null,
                                                tint = CrestTextSecondary,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "No Open Files",
                                                color = CrestTextSecondary,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Select a file from the menu drawer to start editing",
                                                color = CrestTextSecondary.copy(alpha = 0.7f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                        }
                                    },
                                    terminal = {
                                        TerminalScreen(
                                            onClose = {
                                                viewModel.setShowTerminal(false)
                                                activeFocus = ActiveFocus.MONACO
                                            },
                                            initialPath = workspaceDir.absolutePath,
                                            totalContainerHeight = totalContainerHeight,
                                            onTerminalFocus = { activeFocus = ActiveFocus.TERMINAL }
                                        )
                                    },
                                    bottomBar = {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                TermuxExtraKeysToolbar(
                                    isCtrlActive = isCtrlActive,
                                    isCtrlLocked = isCtrlLocked,
                                    isAltActive = isAltActive,
                                    isAltLocked = isAltLocked,
                                    onToggleCtrl = {
                                        if (isCtrlActive) {
                                            isCtrlActive = false
                                            isCtrlLocked = false
                                        } else {
                                            isCtrlActive = true
                                            isCtrlLocked = false
                                        }
                                        TerminalSessionManager.activeClient?.isCtrlActive = isCtrlActive
                                        TerminalSessionManager.activeClient?.isCtrlLocked = isCtrlLocked
                                    },
                                    onLockCtrl = {
                                        isCtrlActive = true
                                        isCtrlLocked = true
                                        TerminalSessionManager.activeClient?.isCtrlActive = true
                                        TerminalSessionManager.activeClient?.isCtrlLocked = true
                                    },
                                    onToggleAlt = {
                                        if (isAltActive) {
                                            isAltActive = false
                                            isAltLocked = false
                                        } else {
                                            isAltActive = true
                                            isAltLocked = false
                                        }
                                        TerminalSessionManager.activeClient?.isAltActive = isAltActive
                                        TerminalSessionManager.activeClient?.isAltLocked = isAltLocked
                                    },
                                    onLockAlt = {
                                        isAltActive = true
                                        isAltLocked = true
                                        TerminalSessionManager.activeClient?.isAltActive = true
                                        TerminalSessionManager.activeClient?.isAltLocked = true
                                    },
                                    onToggleKeyboard = {
                                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                        if (activeFocus == ActiveFocus.TERMINAL) {
                                            TerminalSessionManager.activeClient?.terminalView?.let { view ->
                                                view.requestFocus()
                                                imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                                            }
                                        } else {
                                            webView?.let { wv ->
                                                wv.requestFocus()
                                                imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                                            }
                                        }
                                    },
                                    onKeyClick = { key ->
                                        if (activeFocus == ActiveFocus.TERMINAL) {
                                            TerminalSessionManager.activeSession?.write(key)
                                        } else {
                                            when (key) {
                                                "\u001b" -> MonacoBridge.handleKey(webView, "ESC")
                                                "\t" -> MonacoBridge.handleKey(webView, "TAB")
                                                "\u001b[1~" -> MonacoBridge.handleKey(webView, "HOME")
                                                "\u001b[4~" -> MonacoBridge.handleKey(webView, "END")
                                                "\u001b[5~" -> MonacoBridge.handleKey(webView, "PGUP")
                                                "\u001b[6~" -> MonacoBridge.handleKey(webView, "PGDN")
                                                "\u001b[A" -> MonacoBridge.handleKey(webView, "UP")
                                                "\u001b[B" -> MonacoBridge.handleKey(webView, "DOWN")
                                                "\u001b[C" -> MonacoBridge.handleKey(webView, "RIGHT")
                                                "\u001b[D" -> MonacoBridge.handleKey(webView, "LEFT")
                                                else -> MonacoBridge.typeText(webView, key)
                                            }
                                        }
                                        // Deactivate single-tap (non-locked) modifiers after key action
                                        if (isCtrlActive && !isCtrlLocked) {
                                            isCtrlActive = false
                                            TerminalSessionManager.activeClient?.isCtrlActive = false
                                        }
                                        if (isAltActive && !isAltLocked) {
                                            isAltActive = false
                                            TerminalSessionManager.activeClient?.isAltActive = false
                                        }
                                    }
                                )
                                CrestStatusBar(
                                    language = activeLanguage
                                )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (showCommandPalette) {
                    VSCodeCommandPalette(
                        initialQuery = commandPaletteInitialQuery,
                        commandRegistry = commandRegistry,
                        fileTreeItems = fileTreeItems,
                        openTabs = openTabs,
                        activeTabId = activeTabId,
                        currentLineCount = currentLineCount,
                        onDismiss = { showCommandPalette = false },
                        onCommandExecute = { cmd ->
                            showCommandPalette = false
                            commandRegistry.execute(cmd)
                        },
                        onOpenFile = { path, targetLine, targetCol ->
                            showCommandPalette = false
                            viewModel.openFile(File(path), webView)
                            if (targetLine != null) {
                                webView?.postDelayed({
                                    MonacoBridge.revealLine(webView, targetLine, targetCol ?: 1)
                                }, 150)
                            }
                        },
                        onNavigateLine = { line, col ->
                            showCommandPalette = false
                            MonacoBridge.revealLine(webView, line, col)
                        },
                        onOpenView = { viewId ->
                            showCommandPalette = false
                            when (viewId) {
                                "workbench.view.explorer" -> {
                                    scope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                }
                                "workbench.view.terminal" -> {
                                    viewModel.setShowTerminal(!viewModel.showTerminal.value)
                                }
                                "workbench.view.settings" -> {
                                    viewModel.setShowSettings(true)
                                }
                                "workbench.view.reload" -> {
                                    viewModel.refreshWorkspace(this@MainActivity)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun CrestEditorScreen(
    onWebViewCreated: (WebView) -> Unit,
    bridge: Any,
    onEditorFocus: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentOnEditorFocus by rememberUpdatedState(onEditorFocus)
    val webView = remember {
        WebView(context).apply {
            WebView.setWebContentsDebuggingEnabled(true)
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
            }

            addJavascriptInterface(bridge, "CrestAndroidBridge")

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        Log.d("CrestWebView", "[JS Console] ${it.sourceId()}:${it.lineNumber()} -> ${it.message()}")
                    }
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("CrestWebView", "WebView page finished loading: $url")
                }
            }

            setOnTouchListener { v, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    currentOnEditorFocus()
                    if (!v.hasFocus()) {
                        v.requestFocus()
                    }
                }
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }

            loadUrl("file:///android_asset/editor/index.html")
            onWebViewCreated(this)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { webView }
    )
}

/**
 * Lays out editor, terminal and bottom bar (extra keys + status bar) in one pass.
 * Insets are read only in the measure phase, so keyboard animation never recomposes.
 *
 * Editor focused  -> terminal stays at its normal spot (keyboard covers it, tall part stays visible).
 * Terminal focused -> terminal sits right above the extra keys bar, which sits right above the keyboard.
 */
@Composable
fun WorkspaceLayout(
    showTerminal: Boolean,
    terminalFocused: Boolean,
    modifier: Modifier = Modifier,
    editor: @Composable () -> Unit,
    terminal: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val ime = WindowInsets.ime
    val nav = WindowInsets.navigationBars
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.layoutId("editor")) { editor() }
            if (showTerminal) Box(Modifier.layoutId("terminal")) { terminal() }
            Box(Modifier.layoutId("bar")) { bottomBar() }
        }
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val loose = Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = h)

        val imePx = ime.getBottom(this)
        val navPx = nav.getBottom(this)
        val keyboardOpen = imePx > navPx

        val bar = measurables.first { it.layoutId == "bar" }.measure(loose)
        val term = measurables.firstOrNull { it.layoutId == "terminal" }?.measure(loose)

        val barY = h - (if (keyboardOpen) imePx else navPx) - bar.height
        val normalTop = h - navPx - bar.height // bar top when keyboard is closed

        val termY = term?.let {
            if (terminalFocused && keyboardOpen) barY - it.height
            else normalTop - it.height
        }
        val editorH = (if (termY == null) barY else minOf(barY, termY)).coerceAtLeast(0)
        val ed = measurables.first { it.layoutId == "editor" }
            .measure(Constraints.fixed(w, editorH))

        layout(w, h) {
            ed.place(0, 0)
            if (term != null && termY != null) term.place(0, termY)
            bar.place(0, barY)
        }
    }
}
