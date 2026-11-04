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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.cresteditor.runtime.AlpineManager
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

class MainActivity : ComponentActivity() {

    private lateinit var alpineManager: AlpineManager
    private lateinit var settingsManager: SettingsManager
    private var webView: WebView? = null
    private var onToggleCommandPalette: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            if (event.keyCode == android.view.KeyEvent.KEYCODE_F1 ||
                (event.isCtrlPressed && event.isShiftPressed && event.keyCode == android.view.KeyEvent.KEYCODE_P)
            ) {
                onToggleCommandPalette?.invoke()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alpineManager = AlpineManager(this)
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
                val commandRegistry = remember { CommandRegistry() }
                var monacoActions by remember { mutableStateOf(emptyList<Pair<String, String>>()) }

                DisposableEffect(Unit) {
                    onToggleCommandPalette = { showCommandPalette = !showCommandPalette }
                    onDispose { onToggleCommandPalette = null }
                }

                LaunchedEffect(openTabs, activeTabId, drawerState.isOpen, showTerminal, showSettings, monacoActions) {
                    commandRegistry.clear()
                    commandRegistry.registerAll(
                        listOf(
                            // --- WORKBENCH COMMANDS ---
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
                                viewModel.setShowTerminal(!showTerminal)
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
                                viewModel.openCreateDialog(isFolder = false)
                            },
                            CommandItem(
                                id = "workbench.action.files.newFolder",
                                title = "File: New Folder",
                                category = "File"
                            ) {
                                viewModel.openCreateDialog(isFolder = true)
                            },
                            CommandItem(
                                id = "workbench.action.closeActiveEditor",
                                title = "View: Close Active Tab",
                                category = "View",
                                shortcut = "Ctrl+W"
                            ) {
                                if (activeTabId.isNotEmpty()) {
                                    viewModel.closeTab(activeTabId, webView)
                                }
                            },
                            CommandItem(
                                id = "workbench.action.reloadWindow",
                                title = "Developer: Reload Window",
                                category = "Developer"
                            ) {
                                viewModel.refreshWorkspace(this@MainActivity)
                            },

                            // --- COMMON EDITOR SHORTCUTS ---
                            CommandItem(
                                id = "editor.action.formatDocument",
                                title = "Editor: Format Document",
                                category = "Editor",
                                shortcut = "Shift+Alt+F"
                            ) {
                                MonacoBridge.triggerAction(webView, "editor.action.formatDocument")
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
                                id = "actions.find",
                                title = "Editor: Find",
                                category = "Editor",
                                shortcut = "Ctrl+F"
                            ) {
                                MonacoBridge.triggerAction(webView, "actions.find")
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

                    // Dynamically register all 150+ actions exported from Monaco!
                    monacoActions.forEach { (id, label) ->
                        commandRegistry.register(
                            CommandItem(
                                id = id,
                                title = "Editor: $label",
                                category = "Editor"
                            ) {
                                MonacoBridge.triggerAction(webView, id)
                            }
                        )
                    }
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

                val showCreateDialog by viewModel.showCreateDialog.collectAsState()
                val isFolderCreation by viewModel.isFolderCreation.collectAsState()
                val newNameInput by viewModel.newNameInput.collectAsState()
                val validationError by viewModel.validationError.collectAsState()
                val toastMessage by viewModel.toastMessage.collectAsState()

                val monacoBridge = remember {
                    MonacoBridge(
                        context = context.applicationContext,
                        onReadyCallback = {
                            MonacoBridge.applySettings(webView, settingsManager.getSettingsJsonObject())
                            MonacoBridge.requestSupportedActions(webView)
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
                        },
                        onActionsLoadedCallback = { actions ->
                            monacoActions = actions
                        },
                        onOpenCommandPaletteCallback = {
                            showCommandPalette = true
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

                // Creation Dialog
                if (showCreateDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            viewModel.closeCreateDialog()
                        },
                        title = {
                            Text(
                                text = if (isFolderCreation) "New Folder" else "New File",
                                color = CrestTextActive
                            )
                        },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = newNameInput,
                                    onValueChange = { valStr ->
                                        viewModel.updateNewNameInput(context, valStr)
                                    },
                                    label = { Text(if (isFolderCreation) "Folder Name" else "File Name") },
                                    singleLine = true,
                                    isError = validationError != null,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CrestAccentPrimary,
                                        focusedLabelColor = CrestAccentPrimary
                                    )
                                )
                                if (validationError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = validationError!!,
                                        color = CrestAccentRed,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.createNewItem(context, webView)
                                }
                            ) {
                                Text("Create", color = CrestAccentPrimary)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    viewModel.closeCreateDialog()
                                }
                            ) {
                                Text("Cancel", color = CrestTextSecondary)
                            }
                        },
                        containerColor = CrestSurfaceHeader
                    )
                }

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
                                onFileSelect = { item ->
                                    scope.launch {
                                        drawerState.close()
                                        if (!item.isDirectory) {
                                            viewModel.openFile(File(item.path), webView)
                                        }
                                    }
                                },
                                onNewFileClick = {
                                    viewModel.openCreateDialog(isFolder = false)
                                },
                                onNewFolderClick = {
                                    viewModel.openCreateDialog(isFolder = true)
                                },
                                onRefreshClick = {
                                    viewModel.refreshWorkspace(this@MainActivity)
                                },
                                onTerminalClick = {
                                    scope.launch {
                                        drawerState.close()
                                        viewModel.setShowTerminal(true)
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
                    Scaffold(
                        topBar = {
                            Column {
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
                                        showCommandPalette = true
                                    }
                                )
                                CrestTabBar(
                                    tabs = openTabs,
                                    activeTabId = activeTabId,
                                    onTabSelect = { tabId ->
                                        viewModel.openFile(File(tabId), webView)
                                    },
                                    onTabClose = { tabId ->
                                        viewModel.closeTab(tabId, webView)
                                    }
                                )
                            }
                        },
                        bottomBar = {
                            Column {
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
                                        if (showTerminal) {
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
                                        if (showTerminal) {
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
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (showTerminal) {
                                TerminalScreen(
                                    onClose = {
                                        viewModel.setShowTerminal(false)
                                    },
                                    initialPath = workspaceDir.absolutePath,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (showSettings) {
                                SettingsScreen(
                                    settingsManager = settingsManager,
                                    onClose = {
                                        viewModel.setShowSettings(false)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CrestEditorScreen(
                                    onWebViewCreated = { wv ->
                                        webView = wv
                                        monacoBridge.setWebView(wv)
                                        if (activeTabId.isNotEmpty()) {
                                            viewModel.openFile(File(activeTabId), wv)
                                        }
                                    },
                                    bridge = monacoBridge
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
                    }
                }

                if (showCommandPalette) {
                    VSCodeCommandPalette(
                        commandRegistry = commandRegistry,
                        onDismiss = { showCommandPalette = false },
                        onCommandExecute = { cmd ->
                            showCommandPalette = false
                            commandRegistry.execute(cmd)
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
    bridge: Any
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
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
            webView.destroy()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { webView }
    )
}
