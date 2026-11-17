package com.cresteditor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cresteditor.core.commands.CommandItem
import com.cresteditor.core.commands.CommandRegistry
import com.cresteditor.core.commands.EditorCommandProvider
import com.cresteditor.core.permission.StoragePermissionHelper
import com.cresteditor.core.platform.KeyboardHelper
import com.cresteditor.core.settings.SettingsManager
import com.cresteditor.editor.MonacoBridge
import com.cresteditor.runtime.RootfsManager
import com.cresteditor.runtime.TerminalSessionManager
import com.cresteditor.ui.MainViewModel
import com.cresteditor.ui.TerminalScreen
import com.cresteditor.ui.SettingsScreen
import com.cresteditor.ui.components.*
import com.cresteditor.ui.components.StoragePermissionDialog
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
                var isTerminalMaximized by remember { mutableStateOf(false) }
                var commandPaletteInitialQuery by remember { mutableStateOf(">") }
                var currentLineCount by remember { mutableStateOf(1) }
                val commandRegistry = remember { CommandRegistry() }

                val lifecycleOwner = LocalLifecycleOwner.current
                var hasStoragePermission by remember {
                    mutableStateOf(StoragePermissionHelper.hasStoragePermission(context))
                }
                var showStoragePermissionDialog by remember {
                    mutableStateOf(!hasStoragePermission)
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            val granted = StoragePermissionHelper.hasStoragePermission(context)
                            val wasGranted = hasStoragePermission
                            hasStoragePermission = granted
                            if (granted) {
                                showStoragePermissionDialog = false
                                if (!wasGranted) {
                                    viewModel.refreshWorkspace(this@MainActivity)
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(showTerminal) {
                    if (!showTerminal) {
                        isTerminalMaximized = false
                    }
                }

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
                        EditorCommandProvider.buildCommands(
                            context = context,
                            viewModel = viewModel,
                            getWebView = { webView },
                            drawerState = drawerState,
                            coroutineScope = scope,
                            onToggleCommandPalette = { prefix -> onToggleCommandPalette?.invoke(prefix) },
                            onReloadWindow = { viewModel.refreshWorkspace(this@MainActivity) }
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
                    VSCodeDialog(
                        title = if (isDir) "Delete Folder" else "Delete File",
                        message = "Are you sure you want to delete '${deleteTargetItem!!.name}'? This cannot be undone.",
                        confirmButtonText = "Delete",
                        dismissButtonText = "Cancel",
                        isDestructive = true,
                        onConfirm = { viewModel.confirmDelete(context, webView) },
                        onDismiss = { viewModel.closeDeleteDialog() },
                        onDismissRequest = { viewModel.closeDeleteDialog() }
                    )
                }

                var activeFocus by remember { mutableStateOf(ActiveFocus.NONE) }

                Box(modifier = Modifier.fillMaxSize()) {
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
                                .background(CrestSurfaceHeader)
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
                                if (!showTerminal || !isTerminalMaximized) {
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
                                                backgroundColor = settingsManager.getThemeBackgroundColor(),
                                                onEditorFocus = { activeFocus = ActiveFocus.MONACO }
                                            )

                                            if (!showTerminal && openTabs.isEmpty()) {
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
                                                isTerminalMaximized = false
                                                viewModel.setShowTerminal(false)
                                                activeFocus = ActiveFocus.MONACO
                                            },
                                            initialPath = workspaceDir.absolutePath,
                                            totalContainerHeight = totalContainerHeight,
                                            isMaximized = isTerminalMaximized,
                                            onMaximizeChange = { isTerminalMaximized = it },
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
                                        val targetView = if (activeFocus == ActiveFocus.TERMINAL) {
                                            TerminalSessionManager.activeClient?.terminalView
                                        } else {
                                            webView
                                        }
                                        targetView?.let { view ->
                                            KeyboardHelper.toggle(view)
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

                androidx.compose.animation.AnimatedVisibility(
                    visible = showSettings,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it / 8 }),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { it / 8 })
                ) {
                    SettingsScreen(
                        settingsManager = settingsManager,
                        onClose = {
                            viewModel.setShowSettings(false)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (showCommandPalette) {
                    VSCodeCommandPalette(
                        initialQuery = commandPaletteInitialQuery,
                        commandRegistry = commandRegistry,
                        fileTreeItems = fileTreeItems,
                        projectFileIndex = viewModel.projectFileIndex,
                        openTabs = openTabs,
                        activeTabId = activeTabId,
                        currentLineCount = currentLineCount,
                        effectiveExcludes = remember(showCommandPalette) {
                            if (settingsManager.isSearchUseIgnoreFiles()) {
                                settingsManager.getEffectiveSearchExcludes()
                            } else {
                                emptyMap()
                            }
                        },
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

                if (showStoragePermissionDialog && !hasStoragePermission) {
                    StoragePermissionDialog(
                        onDismissRequest = {
                            showStoragePermissionDialog = false
                        },
                        onGrantPermission = {
                            StoragePermissionHelper.requestStoragePermission(context)
                        }
                    )
                }
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
    backgroundColor: Int = 0xFF1E1E1E.toInt(),
    onEditorFocus: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentOnEditorFocus by rememberUpdatedState(onEditorFocus)
    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(backgroundColor)
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
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(),
        factory = { webView }
    )
}

/**
 * Lays out editor, terminal and bottom bar (extra keys + status bar) in one pass.
 * Insets are read only in the measure phase, so keyboard animation never recomposes.
 *
 * During active keyboard sliding, the WebView measurement is frozen at resting height
 * so the browser engine never reflows intermediate frames, allowing the Extra Keys Bar
 * and Terminal to glide at a locked 60/120 FPS.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val imeTarget = WindowInsets.imeAnimationTarget
    val imeSource = WindowInsets.imeAnimationSource
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
        val targetPx = imeTarget.getBottom(this)
        val sourcePx = imeSource.getBottom(this)
        val isAnimating = targetPx != sourcePx
        val keyboardOpen = imePx > navPx

        val bar = measurables.first { it.layoutId == "bar" }.measure(loose)

        // Live animated positions (60/120 FPS smooth hardware tracking)
        val barY = h - (if (keyboardOpen) imePx else navPx) - bar.height
        val normalTop = h - navPx - bar.height // bar top when keyboard is closed

        // Settled target geometry to avoid re-measuring WebView during continuous animation frames:
        val targetKeyboardOpen = targetPx > navPx
        val targetBarY = h - (if (targetKeyboardOpen) targetPx else navPx) - bar.height

        val maxTermH = (if (terminalFocused && targetKeyboardOpen) targetBarY else normalTop).coerceAtLeast(0)
        val termConstraints = Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = maxTermH)
        val term = measurables.firstOrNull { it.layoutId == "terminal" }?.measure(termConstraints)

        val termY = term?.let {
            val y = if (terminalFocused && keyboardOpen) barY - it.height
            else normalTop - it.height
            y.coerceAtLeast(0)
        }

        val targetTermY = term?.let {
            val y = if (terminalFocused && targetKeyboardOpen) targetBarY - it.height
            else normalTop - it.height
            y.coerceAtLeast(0)
        }
        val settledEditorH = (if (targetTermY == null) targetBarY else minOf(targetBarY, targetTermY)).coerceAtLeast(0)

        // During keyboard sliding (isAnimating), freeze editor height at resting/settled height
        // so WebView isn't forced to perform DOM reflow on every frame:
        val editorH = if (isAnimating && targetPx > sourcePx) {
            // Opening: keep editor at resting height so extra keys bar glides smoothly over it at 120 FPS
            (normalTop - (if (!terminalFocused && term != null) term.height else 0)).coerceAtLeast(0)
        } else {
            // Settled or Closing: use target settled height
            settledEditorH
        }

        val ed = measurables.first { it.layoutId == "editor" }
            .measure(Constraints.fixed(w, editorH))

        layout(w, h) {
            ed.place(0, 0)
            if (term != null && termY != null) term.place(0, termY)
            bar.place(0, barY)
        }
    }
}
