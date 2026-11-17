package com.cresteditor.runtime

import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import java.io.File
import kotlin.concurrent.thread

object TerminalSessionManager {
    private const val TAG = "TerminalSessionManager"

    var activeSession: TerminalSession? = null
        private set
    var activeClient: NativeTerminalClient? = null
        private set

    var onModifiersChanged: ((ctrl: Boolean, alt: Boolean) -> Unit)? = null
        set(value) {
            field = value
            activeClient?.onModifiersChanged = value
        }

    private var isInitializing = false

    fun getOrCreateSession(
        context: Context,
        initialPath: String?,
        onStatusUpdate: (String) -> Unit,
        onSessionReady: (TerminalSession) -> Unit
    ) {
        val existingSession = activeSession
        if (existingSession != null && existingSession.isRunning) {
            Log.d(TAG, "Reattaching to existing active TerminalSession")
            onStatusUpdate("Native Terminal Active")
            onSessionReady(existingSession)
            return
        }

        if (isInitializing) {
            Log.d(TAG, "Session initialization already in progress")
            return
        }

        isInitializing = true
        onStatusUpdate("Initializing Native Environment...")

        thread {
            try {
                val bootstrapManager = NativeBootstrapManager(context)
                val ready = bootstrapManager.setupEnvironment { status ->
                    onStatusUpdate(status)
                }

                if (ready) {
                    val client = NativeTerminalClient(context, onSessionFinishedCallback = {
                        activeSession = null
                        activeClient = null
                        onStatusUpdate("Terminal Process Terminated")
                    }).apply {
                        onModifiersChanged = this@TerminalSessionManager.onModifiersChanged
                    }
                    val session = bootstrapManager.createTerminalSession(client, initialPath)

                    activeClient = client
                    activeSession = session
                    isInitializing = false

                    onStatusUpdate("Native Terminal Active")
                    onSessionReady(session)
                } else {
                    isInitializing = false
                    onStatusUpdate("Native Setup Failed")
                }
            } catch (e: Exception) {
                isInitializing = false
                Log.e(TAG, "Error initializing terminal session", e)
                onStatusUpdate("Error: ${e.localizedMessage}")
            }
        }
    }

    fun restartSession(
        context: Context,
        initialPath: String?,
        onStatusUpdate: (String) -> Unit,
        onSessionReady: (TerminalSession) -> Unit
    ) {
        activeSession?.finishIfRunning()
        activeSession = null
        activeClient = null
        if (isInitializing) {
            Log.d(TAG, "Initialization already in progress, ignoring duplicate restart")
            return
        }
        getOrCreateSession(context, initialPath, onStatusUpdate, onSessionReady)
    }

    fun terminateSession() {
        activeSession?.finishIfRunning()
        activeSession = null
        activeClient = null
        isInitializing = false
    }
}
