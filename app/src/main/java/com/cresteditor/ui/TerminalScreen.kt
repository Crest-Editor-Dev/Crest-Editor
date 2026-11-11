package com.cresteditor.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cresteditor.icons.Codicons
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.cresteditor.runtime.NativeTerminalClient
import com.cresteditor.runtime.RootfsManager
import com.cresteditor.runtime.TerminalService
import com.cresteditor.runtime.TerminalSessionManager

/**
 * Exact VS Code Integrated Terminal Panel (Bottom Docked Overlay).
 * Features:
 * - Default 40% height matching VS Code (preferredHeight = 0.4)
 * - Draggable top splitter handle to resize panel smoothly
 * - Double-tap splitter or click Maximize (^) for full-screen view
 * - Clean VS Code Panel Header with active tab bar & action buttons
 * - Persistent Void Linux shell session in background
 */
@Composable
fun TerminalScreen(
    onClose: () -> Unit,
    initialPath: String? = null,
    totalContainerHeight: Dp = 600.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var statusText by remember { mutableStateOf("Initializing...") }
    val activityLogs = remember { mutableStateListOf<String>() }
    var currentSession by remember { mutableStateOf<TerminalSession?>(null) }
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    var clientRef by remember { mutableStateOf<NativeTerminalClient?>(null) }

    val rootfsManager = remember { RootfsManager(context) }

    // State for VS Code Splitter & Maximize
    var isMaximized by remember { mutableStateOf(false) }
    var panelHeightFraction by remember { mutableFloatStateOf(0.40f) } // Exact VS Code 40% default!
    val minHeight = 77.dp // Exact VS Code 77px min height
    val density = LocalDensity.current
    val totalHeightPx = with(density) { totalContainerHeight.toPx() }

    val currentPanelHeight = if (isMaximized) {
        totalContainerHeight
    } else {
        (totalContainerHeight * panelHeightFraction).coerceIn(minHeight, totalContainerHeight)
    }

    fun postStatus(status: String) {
        mainHandler.post {
            statusText = status
            val formatted = ">>> $status"
            if (activityLogs.isEmpty()) {
                activityLogs.add(formatted)
            } else {
                val lastIdx = activityLogs.lastIndex
                val last = activityLogs[lastIdx]
                if (status.startsWith("Downloading Void RootFS:") && last.startsWith(">>> Downloading Void RootFS:")) {
                    activityLogs[lastIdx] = formatted
                } else {
                    activityLogs.add(formatted)
                }
            }
        }
    }

    fun showKeyboard() {
        terminalViewRef?.let { view ->
            view.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        }
    }

    fun hideKeyboard() {
        keyboardController?.hide()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val view = terminalViewRef ?: (context as? android.app.Activity)?.currentFocus
        if (view != null) {
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    val handleClose = {
        hideKeyboard()
        onClose()
    }

    BackHandler {
        if (isMaximized) {
            isMaximized = false
        } else {
            handleClose()
        }
    }

    LaunchedEffect(Unit) {
        try {
            val serviceIntent = Intent(context, TerminalService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Protect against background service start exception on UI launch
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(currentPanelHeight)
            .background(Color(0xFF0F172A))
    ) {
        // =====================================================================
        // 1. VS Code Draggable Splitter Handle (Overlays top border)
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Color(0xFF1E1E1E))
                .pointerInput(totalHeightPx) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        // Dragging UP (-dragAmount) expands the bottom panel
                        // Dragging DOWN (+dragAmount) shrinks the bottom panel
                        val deltaFraction = -dragAmount / totalHeightPx
                        panelHeightFraction = (panelHeightFraction + deltaFraction).coerceIn(0.15f, 0.95f)
                        if (isMaximized) isMaximized = false
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            isMaximized = !isMaximized
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // 1px border line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF2B2B2B))
                    .align(Alignment.TopCenter)
            )
            // Subtle centered grab pill
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(3.dp)
                    .background(Color(0xFF5A5D5E), RoundedCornerShape(1.5.dp))
            )
        }

        // =====================================================================
        // 2. VS Code Panel Header Bar (30dp height)
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(Color(0xFF1E1E1E))
                .border(width = 1.dp, color = Color(0xFF2B2B2B))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TERMINAL Active Tab
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "TERMINAL",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (currentSession != null) Color(0xFF22C55E)
                                else Color(0xFFEAB308)
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .height(2.dp)
                        .background(Color(0xFF007ACC))
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (currentSession != null) "1: bash" else statusText,
                fontSize = 11.sp,
                color = Color(0xFF858585),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons
            IconButton(
                onClick = {
                    currentSession = null
                    activityLogs.clear()
                    postStatus("Restarting Void Linux Session...")
                    TerminalSessionManager.restartSession(
                        context = context,
                        initialPath = initialPath,
                        onStatusUpdate = { postStatus(it) },
                        onSessionReady = { session ->
                            mainHandler.post {
                                currentSession = session
                                clientRef = TerminalSessionManager.activeClient
                                terminalViewRef?.post {
                                    val client = TerminalSessionManager.activeClient
                                    if (client != null) {
                                        client.terminalView = terminalViewRef
                                        terminalViewRef?.setTerminalViewClient(client)
                                        terminalViewRef?.attachSession(session)
                                        terminalViewRef?.requestFocus()
                                        showKeyboard()
                                    }
                                }
                            }
                        }
                    )
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Codicons.Refresh,
                    contentDescription = "Restart Terminal",
                    tint = Color(0xFFC5C5C5),
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            IconButton(
                onClick = { currentSession?.write("clear\r") },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Codicons.ClearAll,
                    contentDescription = "Clear",
                    tint = Color(0xFFC5C5C5),
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            IconButton(
                onClick = { showKeyboard() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Show Keyboard",
                    tint = Color(0xFFC5C5C5),
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            IconButton(
                onClick = { isMaximized = !isMaximized },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isMaximized) Codicons.ChevronDown else Codicons.ChevronUp,
                    contentDescription = if (isMaximized) "Restore Panel" else "Maximize Panel",
                    tint = Color(0xFFC5C5C5),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            IconButton(
                onClick = handleClose,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Codicons.Close,
                    contentDescription = "Close Panel",
                    tint = Color(0xFFC5C5C5),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // =====================================================================
        // 3. Terminal View Body
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0F172A))
        ) {
            AndroidView(
                factory = { ctx ->
                    val view = TerminalView(ctx, null).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setTextSize(36)
                        isFocusable = true
                        isFocusableInTouchMode = true
                    }
                    terminalViewRef = view

                    TerminalSessionManager.getOrCreateSession(
                        context = ctx,
                        initialPath = initialPath,
                        onStatusUpdate = { status ->
                            postStatus(status)
                        },
                        onSessionReady = { session ->
                            mainHandler.post {
                                currentSession = session
                                val client = TerminalSessionManager.activeClient
                                clientRef = client
                                view.post {
                                    if (client == null) {
                                        android.util.Log.e(
                                            "TerminalScreen",
                                            "Cannot attach terminal session: TerminalViewClient is null"
                                        )
                                        postStatus("Terminal Client Initialization Failed")
                                        return@post
                                    }

                                    client.terminalView = view
                                    view.setTerminalViewClient(client)
                                    view.attachSession(session)

                                    view.requestFocus()
                                    showKeyboard()
                                }
                            }
                        }
                    )

                    view
                },
                modifier = Modifier.fillMaxSize()
            )

            if (currentSession == null) {
                val logScrollState = rememberScrollState()
                LaunchedEffect(activityLogs.size, activityLogs.lastOrNull()) {
                    logScrollState.animateScrollTo(logScrollState.maxValue)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(logScrollState)
                    ) {
                        Text(
                            text = "─── Void Linux Environment Boot & Setup ───",
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        activityLogs.forEach { line ->
                            val color = when {
                                line.contains("Error", ignoreCase = true) || line.contains("failed", ignoreCase = true) -> Color(0xFFEF4444)
                                line.contains("✔") || line.contains("🚀") || line.contains("passed", ignoreCase = true) || line.contains("successfully", ignoreCase = true) -> Color(0xFF22C55E)
                                line.contains("Downloading", ignoreCase = true) || line.contains("Download started", ignoreCase = true) -> Color(0xFF38BDF8)
                                line.contains("Verifying", ignoreCase = true) || line.contains("Validating", ignoreCase = true) -> Color(0xFFFBBF24)
                                else -> Color(0xFFCBD5E1)
                            }

                            Text(
                                text = line,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = color,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            hideKeyboard()
        }
    }
}
