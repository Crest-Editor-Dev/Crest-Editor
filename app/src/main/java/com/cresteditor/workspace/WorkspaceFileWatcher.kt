package com.cresteditor.workspace

import android.os.Build
import android.os.FileObserver
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

/**
 * VS Code-style recursive File Watcher for Crest-Editor workspace.
 * Replicates VS Code's explorer auto-reload architecture:
 * - Uses native Linux inotify (android.os.FileObserver) on workspace & all subfolders
 * - Exact 500ms debounce delay (EXPLORER_FILE_CHANGES_REACT_DELAY) matching VS Code
 * - Batches rapid file creation/deletion (e.g., git, npm, terminal scripts) into a single reload
 */
class WorkspaceFileWatcher(
    private val rootDir: File,
    private val coroutineScope: CoroutineScope,
    private val onFileModified: ((File) -> Unit)? = null,
    private val onFileDeleted: ((File) -> Unit)? = null,
    private val onWorkspaceChanged: () -> Unit
) {
    private val TAG = "WorkspaceFileWatcher"
    private val observers = mutableListOf<FileObserver>()
    private var debounceJob: Job? = null
    private var isWatching = false

    companion object {
        // Exact VS Code EXPLORER_FILE_CHANGES_REACT_DELAY
        private const val DEBOUNCE_DELAY_MS = 500L
        private const val WATCH_FLAGS = FileObserver.CREATE or
                FileObserver.DELETE or
                FileObserver.MOVED_TO or
                FileObserver.MOVED_FROM or
                FileObserver.CLOSE_WRITE or
                FileObserver.DELETE_SELF
    }

    fun start() {
        if (isWatching) return
        isWatching = true
        registerObserversRecursively(rootDir)
    }

    fun stop() {
        isWatching = false
        debounceJob?.cancel()
        debounceJob = null
        synchronized(observers) {
            observers.forEach { runCatching { it.stopWatching() } }
            observers.clear()
        }
    }

    private fun registerObserversRecursively(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return

        createObserver(dir)?.let {
            it.startWatching()
            synchronized(observers) {
                observers.add(it)
            }
        }

        dir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
            registerObserversRecursively(subDir)
        }
    }

    @Suppress("DEPRECATION")
    private fun createObserver(dir: File): FileObserver? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                object : FileObserver(dir, WATCH_FLAGS) {
                    override fun onEvent(event: Int, path: String?) {
                        handleFileEvent(event, path, dir)
                    }
                }
            } else {
                object : FileObserver(dir.absolutePath, WATCH_FLAGS) {
                    override fun onEvent(event: Int, path: String?) {
                        handleFileEvent(event, path, dir)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to watch ${dir.absolutePath}: ${e.message}")
            null
        }
    }

    private fun handleFileEvent(event: Int, path: String?, parentDir: File) {
        if (path == null) return

        val targetFile = File(parentDir, path)

        // If a new directory was created, attach a watcher to it
        if ((event and FileObserver.CREATE) != 0) {
            if (targetFile.isDirectory) {
                createObserver(targetFile)?.let {
                    it.startWatching()
                    synchronized(observers) {
                        observers.add(it)
                    }
                }
            }
        }

        // Notify file modification if an open file was written to or moved/replaced into place
        if ((event and (FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO)) != 0) {
            if (!targetFile.isDirectory) {
                coroutineScope.launch(Dispatchers.Main) {
                    if (isWatching) {
                        onFileModified?.invoke(targetFile)
                    }
                }
            }
        }

        // Notify file deletion/move away (rm or mv)
        if ((event and (FileObserver.DELETE or FileObserver.DELETE_SELF or FileObserver.MOVED_FROM)) != 0) {
            if (!targetFile.isDirectory) {
                coroutineScope.launch(Dispatchers.Main) {
                    if (isWatching) {
                        onFileDeleted?.invoke(targetFile)
                    }
                }
            }
        }

        // Debounce: batch multiple rapid changes into a single refresh (VS Code style)
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(DEBOUNCE_DELAY_MS)
            if (isWatching) {
                withContext(Dispatchers.Main) {
                    onWorkspaceChanged()
                }
            }
        }
    }
}
