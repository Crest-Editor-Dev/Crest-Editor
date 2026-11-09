package com.cresteditor.ui

import android.content.Context
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cresteditor.editor.MonacoBridge
import com.cresteditor.ui.components.CrestTabItem
import com.cresteditor.ui.components.FileTreeItem
import com.cresteditor.workspace.WorkspaceManager
import com.cresteditor.workspace.WorkspaceFileWatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MainViewModel : ViewModel() {

    private val _fileTreeItems = MutableStateFlow<List<FileTreeItem>>(emptyList())
    val fileTreeItems: StateFlow<List<FileTreeItem>> = _fileTreeItems.asStateFlow()

    private val _openTabs = MutableStateFlow<List<CrestTabItem>>(emptyList())
    val openTabs: StateFlow<List<CrestTabItem>> = _openTabs.asStateFlow()

    private val _activeTabId = MutableStateFlow("")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _activeLanguage = MutableStateFlow("javascript")
    val activeLanguage: StateFlow<String> = _activeLanguage.asStateFlow()

    private val _showTerminal = MutableStateFlow(false)
    val showTerminal: StateFlow<Boolean> = _showTerminal.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    // Dialog states
    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    private val _showRenameDialog = MutableStateFlow(false)
    val showRenameDialog: StateFlow<Boolean> = _showRenameDialog.asStateFlow()

    private val _renameTargetItem = MutableStateFlow<FileTreeItem?>(null)
    val renameTargetItem: StateFlow<FileTreeItem?> = _renameTargetItem.asStateFlow()

    private val _renameInput = MutableStateFlow("")
    val renameInput: StateFlow<String> = _renameInput.asStateFlow()

    private val _renameValidationError = MutableStateFlow<String?>(null)
    val renameValidationError: StateFlow<String?> = _renameValidationError.asStateFlow()

    private val _isFolderCreation = MutableStateFlow(false)
    val isFolderCreation: StateFlow<Boolean> = _isFolderCreation.asStateFlow()

    private val _newNameInput = MutableStateFlow("")
    val newNameInput: StateFlow<String> = _newNameInput.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    fun loadWorkspace(context: Context, webView: WebView? = null) {
        startFileWatcher(context)
        viewModelScope.launch {
            val workspaceDir = WorkspaceManager.getWorkspaceDir(context)
            val items = WorkspaceManager.listFilesRecursively(workspaceDir)
            _fileTreeItems.value = items

            if (_openTabs.value.isEmpty()) {
                val firstFile = items.firstOrNull { !it.isDirectory }
                firstFile?.let { item ->
                    openFile(File(item.path), webView)
                }
            }
        }
    }

    // Multi-layer self-save echo suppression & dirty sync caches
    private val inFlightSaves = ConcurrentHashMap.newKeySet<String>()
    private val lastSavedContentHashes = ConcurrentHashMap<String, Int>()
    private val lastSavedTimestamps = ConcurrentHashMap<String, Long>()

    private var fileWatcher: WorkspaceFileWatcher? = null

    fun startFileWatcher(context: Context) {
        if (fileWatcher != null) return
        val workspaceDir = WorkspaceManager.getWorkspaceDir(context)
        fileWatcher = WorkspaceFileWatcher(
            rootDir = workspaceDir,
            coroutineScope = viewModelScope,
            onFileModified = { file ->
                handleExternalFileModified(file)
            },
            onFileDeleted = { file ->
                handleExternalFileDeleted(file)
            },
            onWorkspaceChanged = {
                refreshWorkspace(context)
            }
        )
        fileWatcher?.start()
    }

    private fun handleExternalFileDeleted(file: File) {
        val path = file.absolutePath
        val dirPrefix = path + File.separator
        var marked = false
        _openTabs.value = _openTabs.value.map { tab ->
            if (tab.id == path || tab.id.startsWith(dirPrefix)) {
                marked = true
                tab.copy(isOrphaned = true)
            } else {
                tab
            }
        }
        if (marked) {
            _toastMessage.value = "${file.name} (deleted from disk)"
        }
    }

    private fun handleExternalFileModified(file: File) {
        val path = file.absolutePath
        val matchingTab = _openTabs.value.find { it.id == path } ?: return

        // 1. Origin tracking: Ignore if this file is actively being saved by Crest Editor
        if (inFlightSaves.contains(path)) return

        // 2. Dirty check: NEVER overwrite unsaved / dirty editor models
        if (matchingTab.isModified) {
            _toastMessage.value = "External changes detected in ${file.name}. Keeping unsaved changes."
            return
        }

        viewModelScope.launch {
            // 3. Read current disk content
            val readResult = WorkspaceManager.readFileContent(file)
            val diskContent = readResult.getOrNull() ?: return@launch

            // 4. Content validation (Multi-layer echo prevention)
            val diskContentHash = diskContent.hashCode()
            if (diskContentHash == lastSavedContentHashes[path]) {
                // Content identical to our last known/saved version
                return@launch
            }

            // 5. Update cache & synchronize with Monaco editor model
            lastSavedContentHashes[path] = diskContentHash
            lastSavedTimestamps[path] = file.lastModified()

            val webView = MonacoBridge.getActiveWebView()
            MonacoBridge.syncExternalContent(webView, path, diskContent)
        }
    }

    fun stopFileWatcher() {
        fileWatcher?.stop()
        fileWatcher = null
    }

    override fun onCleared() {
        super.onCleared()
        stopFileWatcher()
    }

    fun refreshWorkspace(context: Context) {
        viewModelScope.launch {
            val workspaceDir = WorkspaceManager.getWorkspaceDir(context)
            _fileTreeItems.value = WorkspaceManager.listFilesRecursively(workspaceDir)
        }
    }

    fun openFile(file: File, webView: WebView?) {
        if (file.isDirectory) return

        viewModelScope.launch {
            val path = file.absolutePath
            val language = WorkspaceManager.detectLanguage(file.name)
            val currentTabs = _openTabs.value
            val existingTab = currentTabs.find { it.id == path }

            if (existingTab != null) {
                // Tab is ALREADY OPEN: switch to in-memory model without reading or overwriting from disk
                _activeTabId.value = path
                _activeLanguage.value = language
                MonacoBridge.openFile(webView, path, null, language, existingTab.isModified)
                return@launch
            }

            // Fresh file open: read content from disk
            val readResult = WorkspaceManager.readFileContent(file)
            val content = readResult.getOrDefault("")

            // Cache baseline for self-save echo suppression and sync comparisons
            lastSavedContentHashes[path] = content.hashCode()
            lastSavedTimestamps[path] = file.lastModified()

            _openTabs.value = currentTabs + CrestTabItem(id = path, title = file.name, isModified = false)
            _activeTabId.value = path
            _activeLanguage.value = language

            MonacoBridge.openFile(webView, path, content, language, false)
        }
    }

    fun closeTab(tabId: String, webView: WebView?) {
        val currentTabs = _openTabs.value
        val newTabs = currentTabs.filterNot { it.id == tabId }
        _openTabs.value = newTabs
        lastSavedContentHashes.remove(tabId)
        lastSavedTimestamps.remove(tabId)
        inFlightSaves.remove(tabId)
        MonacoBridge.closeFile(webView, tabId)

        if (_activeTabId.value == tabId) {
            if (newTabs.isNotEmpty()) {
                val nextTabFile = File(newTabs.last().id)
                openFile(nextTabFile, webView)
            } else {
                _activeTabId.value = ""
                _activeLanguage.value = "-"
                MonacoBridge.clearEditor(webView)
            }
        }
    }

    fun markActiveTabModified(isModified: Boolean) {
        val currentActive = _activeTabId.value
        if (currentActive.isEmpty()) return

        _openTabs.value = _openTabs.value.map { tab ->
            if (tab.id == currentActive) {
                tab.copy(isModified = isModified)
            } else {
                tab
            }
        }
    }

    fun saveContent(context: Context, filePath: String, content: String) {
        inFlightSaves.add(filePath)
        viewModelScope.launch {
            val targetFile = File(filePath)
            val writeResult = WorkspaceManager.writeFileContent(targetFile, content)

            writeResult.onSuccess {
                lastSavedContentHashes[filePath] = content.hashCode()
                lastSavedTimestamps[filePath] = targetFile.lastModified()
                inFlightSaves.remove(filePath)

                _openTabs.value = _openTabs.value.map { tab ->
                    if (tab.id == filePath) tab.copy(isModified = false, isOrphaned = false) else tab
                }
            }.onFailure { ex ->
                inFlightSaves.remove(filePath)
                _toastMessage.value = "Save failed: ${ex.localizedMessage}"
            }
        }
    }

    fun requestSave(context: Context, webView: WebView?) {
        val currentActive = _activeTabId.value
        if (currentActive.isEmpty()) return

        MonacoBridge.getContent(webView) { content ->
            saveContent(context, currentActive, content)
        }
    }

    fun setShowTerminal(show: Boolean) {
        _showTerminal.value = show
    }

    fun setShowSettings(show: Boolean) {
        _showSettings.value = show
    }

    fun openCreateDialog(isFolder: Boolean) {
        _isFolderCreation.value = isFolder
        _newNameInput.value = ""
        _validationError.value = null
        _showCreateDialog.value = true
    }

    fun closeCreateDialog() {
        _showCreateDialog.value = false
        _newNameInput.value = ""
        _validationError.value = null
    }

    fun updateNewNameInput(context: Context, name: String) {
        _newNameInput.value = name
        val workspaceDir = WorkspaceManager.getWorkspaceDir(context)
        _validationError.value = WorkspaceManager.validateName(workspaceDir, name)
    }

    fun createNewItem(context: Context, webView: WebView?) {
        viewModelScope.launch {
            val workspaceDir = WorkspaceManager.getWorkspaceDir(context)
            val name = _newNameInput.value
            val err = WorkspaceManager.validateName(workspaceDir, name)

            if (err != null) {
                _validationError.value = err
                return@launch
            }

            val isFolder = _isFolderCreation.value
            val result = if (isFolder) {
                WorkspaceManager.createNewFolder(workspaceDir, name)
            } else {
                WorkspaceManager.createNewFile(workspaceDir, name)
            }

            result.onSuccess { createdFile ->
                closeCreateDialog()
                refreshWorkspace(context)
                if (!createdFile.isDirectory) {
                    openFile(createdFile, webView)
                }
            }.onFailure { ex ->
                _validationError.value = ex.message ?: "Creation failed."
            }
        }
    }

    fun openRenameDialog(item: FileTreeItem) {
        _renameTargetItem.value = item
        _renameInput.value = item.name
        _renameValidationError.value = null
        _showRenameDialog.value = true
    }

    fun closeRenameDialog() {
        _showRenameDialog.value = false
        _renameTargetItem.value = null
        _renameInput.value = ""
        _renameValidationError.value = null
    }

    fun updateRenameInput(newName: String) {
        _renameInput.value = newName
        val target = _renameTargetItem.value ?: return
        val parent = File(target.path).parentFile ?: return
        if (newName.trim() == target.name) {
            _renameValidationError.value = null
        } else {
            _renameValidationError.value = WorkspaceManager.validateName(parent, newName)
        }
    }

    fun executeRename(context: Context, webView: WebView?) {
        val target = _renameTargetItem.value ?: return
        val newName = _renameInput.value.trim()
        val oldFile = File(target.path)
        val parent = oldFile.parentFile ?: return

        if (newName == target.name) {
            closeRenameDialog()
            return
        }

        val err = WorkspaceManager.validateName(parent, newName)
        if (err != null) {
            _renameValidationError.value = err
            return
        }

        viewModelScope.launch {
            val result = WorkspaceManager.renameFile(oldFile, newName)
            result.onSuccess { newFile ->
                val oldPath = oldFile.absolutePath
                val newPath = newFile.absolutePath
                val newLanguage = WorkspaceManager.detectLanguage(newFile.name)

                // Update caches if tracked
                if (lastSavedContentHashes.containsKey(oldPath)) {
                    val hash = lastSavedContentHashes.remove(oldPath)
                    if (hash != null) lastSavedContentHashes[newPath] = hash
                }
                if (lastSavedTimestamps.containsKey(oldPath)) {
                    lastSavedTimestamps.remove(oldPath)
                    lastSavedTimestamps[newPath] = newFile.lastModified()
                }

                if (oldFile.isDirectory) {
                    val oldPrefix = oldFile.absolutePath + File.separator
                    val newPrefix = newFile.absolutePath + File.separator
                    _openTabs.value = _openTabs.value.map { tab ->
                        if (tab.id.startsWith(oldPrefix)) {
                            val rel = tab.id.removePrefix(oldPrefix)
                            val newChildPath = newPrefix + rel
                            val childLang = WorkspaceManager.detectLanguage(tab.title)
                            MonacoBridge.renameModel(webView, tab.id, newChildPath, childLang)
                            tab.copy(id = newChildPath)
                        } else {
                            tab
                        }
                    }
                    if (_activeTabId.value.startsWith(oldPrefix)) {
                        _activeTabId.value = newPrefix + _activeTabId.value.removePrefix(oldPrefix)
                    }
                } else {
                    // Update open tabs
                    _openTabs.value = _openTabs.value.map { tab ->
                        if (tab.id == oldPath) {
                            tab.copy(id = newPath, title = newFile.name, isOrphaned = false)
                        } else {
                            tab
                        }
                    }

                    // Update active tab if it was the renamed file
                    if (_activeTabId.value == oldPath) {
                        _activeTabId.value = newPath
                        _activeLanguage.value = newLanguage
                    }

                    // Update Monaco Editor Model URI & Content
                    MonacoBridge.renameModel(webView, oldPath, newPath, newLanguage)
                }

                closeRenameDialog()
                refreshWorkspace(context)
            }.onFailure { ex ->
                _renameValidationError.value = ex.message ?: "Rename failed."
            }
        }
    }
}
