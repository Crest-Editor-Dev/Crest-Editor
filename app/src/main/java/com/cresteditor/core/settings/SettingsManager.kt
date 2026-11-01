package com.cresteditor.core.settings

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * Definition of a single configurable setting item in CrestEditor.
 */
data class SettingDefinition(
    val key: String,
    val title: String,
    val category: String,
    val description: String,
    val type: SettingType,
    val defaultValue: Any,
    val options: List<String> = emptyList(),
    val min: Int? = null,
    val max: Int? = null
)

enum class SettingType {
    ENUM,
    BOOLEAN,
    INT,
    STRING
}

/**
 * VS Code-style Settings Manager for CrestEditor.
 * Stores settings in a persistent `settings.json` file inside the app storage.
 * Designed so that:
 * 1. UI can easily read/write settings.
 * 2. Future AI Agents and shell scripts can read/write the JSON file directly.
 * 3. Reactive updates via StateFlow and listeners notify Monaco & Terminal immediately.
 */
class SettingsManager(private val context: Context) {

    private val TAG = "CrestSettingsManager"
    private val settingsFile = File(context.filesDir, "settings.json")

    // Reactive state flow of all current settings
    private val _settingsState = MutableStateFlow<Map<String, Any>>(emptyMap())
    val settingsState: StateFlow<Map<String, Any>> = _settingsState.asStateFlow()

    // Listeners for setting changes
    private val changeListeners = mutableListOf<(key: String, value: Any) -> Unit>()

    companion object {
        // Categories
        const val CAT_COMMONLY_USED = "Commonly Used"
        const val CAT_FILES = "Files (Auto Save)"
        const val CAT_EDITOR = "Text Editor"
        const val CAT_TERMINAL = "Terminal"
        const val CAT_APPEARANCE = "Appearance"

        // Setting Keys
        const val KEY_FILES_AUTOSAVE = "files.autoSave"
        const val KEY_FILES_AUTOSAVE_DELAY = "files.autoSaveDelay"
        const val KEY_FILES_SHOW_HIDDEN = "files.showHiddenFiles"

        const val KEY_EDITOR_FONT_SIZE = "editor.fontSize"
        const val KEY_EDITOR_TAB_SIZE = "editor.tabSize"
        const val KEY_EDITOR_WORD_WRAP = "editor.wordWrap"
        const val KEY_EDITOR_MINIMAP = "editor.minimap.enabled"
        const val KEY_EDITOR_CURSOR_BLINKING = "editor.cursorBlinking"
        const val KEY_EDITOR_THEME = "editor.theme"
        const val KEY_EDITOR_LINE_NUMBERS = "editor.lineNumbers"

        const val KEY_TERMINAL_FONT_SIZE = "terminal.integrated.fontSize"
        const val KEY_TERMINAL_CURSOR_STYLE = "terminal.integrated.cursorStyle"

        val SCHEMA: List<SettingDefinition> = listOf(
            SettingDefinition(
                key = KEY_FILES_AUTOSAVE,
                title = "Files: Auto Save",
                category = CAT_FILES,
                description = "Controls auto save of dirty editors.\n• off: never auto save\n• afterDelay: save after configured delay\n• onFocusChange: save when editor loses focus\n• onWindowChange: save when switching apps or minimizing",
                type = SettingType.ENUM,
                defaultValue = "afterDelay",
                options = listOf("off", "afterDelay", "onFocusChange", "onWindowChange")
            ),
            SettingDefinition(
                key = KEY_FILES_AUTOSAVE_DELAY,
                title = "Files: Auto Save Delay",
                category = CAT_FILES,
                description = "Controls the delay in milliseconds after which a dirty editor is saved automatically when autoSave is set to 'afterDelay'.",
                type = SettingType.INT,
                defaultValue = 1000,
                min = 100,
                max = 10000
            ),
            SettingDefinition(
                key = KEY_FILES_SHOW_HIDDEN,
                title = "Files: Show Hidden Files",
                category = CAT_FILES,
                description = "Controls whether files starting with a dot (.) are shown in the file explorer drawer.",
                type = SettingType.BOOLEAN,
                defaultValue = false
            ),
            SettingDefinition(
                key = KEY_EDITOR_FONT_SIZE,
                title = "Editor: Font Size",
                category = CAT_EDITOR,
                description = "Controls the font size in pixels for the Monaco code editor.",
                type = SettingType.INT,
                defaultValue = 14,
                min = 8,
                max = 32
            ),
            SettingDefinition(
                key = KEY_EDITOR_TAB_SIZE,
                title = "Editor: Tab Size",
                category = CAT_EDITOR,
                description = "The number of spaces a tab is equal to in the code editor.",
                type = SettingType.INT,
                defaultValue = 4,
                min = 2,
                max = 8
            ),
            SettingDefinition(
                key = KEY_EDITOR_WORD_WRAP,
                title = "Editor: Word Wrap",
                category = CAT_EDITOR,
                description = "Controls how lines should wrap in the editor. Set to 'on' to prevent horizontal scrolling of long code lines.",
                type = SettingType.ENUM,
                defaultValue = "on",
                options = listOf("off", "on", "wordWrapColumn", "bounded")
            ),
            SettingDefinition(
                key = KEY_EDITOR_MINIMAP,
                title = "Editor: Minimap Enabled",
                category = CAT_EDITOR,
                description = "Controls whether the mini code preview map is shown on the right side of the editor.",
                type = SettingType.BOOLEAN,
                defaultValue = false
            ),
            SettingDefinition(
                key = KEY_EDITOR_CURSOR_BLINKING,
                title = "Editor: Cursor Blinking",
                category = CAT_EDITOR,
                description = "Controls the cursor animation style in the editor.",
                type = SettingType.ENUM,
                defaultValue = "smooth",
                options = listOf("smooth", "blink", "phase", "expand", "solid")
            ),
            SettingDefinition(
                key = KEY_EDITOR_LINE_NUMBERS,
                title = "Editor: Line Numbers",
                category = CAT_EDITOR,
                description = "Controls the display of line numbers on the left margin of the editor.",
                type = SettingType.ENUM,
                defaultValue = "on",
                options = listOf("on", "off", "relative")
            ),
            SettingDefinition(
                key = KEY_EDITOR_THEME,
                title = "Workbench: Color Theme",
                category = CAT_APPEARANCE,
                description = "Specifies the color theme used in the editor and workbench UI.",
                type = SettingType.ENUM,
                defaultValue = "vs-dark",
                options = listOf("vs-dark", "vs", "hc-black")
            ),
            SettingDefinition(
                key = KEY_TERMINAL_FONT_SIZE,
                title = "Terminal: Font Size",
                category = CAT_TERMINAL,
                description = "Controls the font size in pixels of the integrated terminal screen.",
                type = SettingType.INT,
                defaultValue = 13,
                min = 8,
                max = 28
            ),
            SettingDefinition(
                key = KEY_TERMINAL_CURSOR_STYLE,
                title = "Terminal: Cursor Style",
                category = CAT_TERMINAL,
                description = "Controls the cursor style in the integrated terminal emulator.",
                type = SettingType.ENUM,
                defaultValue = "block",
                options = listOf("block", "line", "underline")
            )
        )
    }

    init {
        loadSettings()
    }

    /**
     * Loads settings from settings.json or initializes defaults.
     */
    @Synchronized
    private fun loadSettings() {
        val map = mutableMapOf<String, Any>()

        // 1. Fill defaults first
        SCHEMA.forEach { def ->
            map[def.key] = def.defaultValue
        }

        // 2. Read from settings.json if exists
        if (settingsFile.exists()) {
            try {
                val jsonStr = settingsFile.readText()
                val json = JSONObject(jsonStr)
                SCHEMA.forEach { def ->
                    if (json.has(def.key)) {
                        when (def.type) {
                            SettingType.BOOLEAN -> map[def.key] = json.getBoolean(def.key)
                            SettingType.INT -> map[def.key] = json.getInt(def.key)
                            SettingType.ENUM, SettingType.STRING -> map[def.key] = json.getString(def.key)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading settings.json, recreating with defaults", e)
                persistJson(map)
            }
        } else {
            persistJson(map)
        }

        _settingsState.value = map
    }

    /**
     * Write settings map to settings.json
     */
    @Synchronized
    private fun persistJson(map: Map<String, Any>) {
        try {
            val json = JSONObject()
            map.forEach { (k, v) ->
                json.put(k, v)
            }
            settingsFile.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist settings.json", e)
        }
    }

    fun addChangeListener(listener: (key: String, value: Any) -> Unit) {
        changeListeners.add(listener)
    }

    fun removeChangeListener(listener: (key: String, value: Any) -> Unit) {
        changeListeners.remove(listener)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T {
        val current = _settingsState.value[key]
        if (current != null) {
            return current as T
        }
        val defaultVal = SCHEMA.find { it.key == key }?.defaultValue
        return (defaultVal as? T) ?: error("Unknown setting key: $key")
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getOrDefault(key: String, default: T): T {
        return (_settingsState.value[key] as? T) ?: default
    }

    fun set(key: String, value: Any) {
        val currentMap = _settingsState.value.toMutableMap()
        currentMap[key] = value
        _settingsState.value = currentMap
        persistJson(currentMap)

        changeListeners.forEach { listener ->
            try {
                listener(key, value)
            } catch (e: Exception) {
                Log.e(TAG, "Listener error on key: $key", e)
            }
        }
    }

    fun reset(key: String) {
        val defaultVal = SCHEMA.find { it.key == key }?.defaultValue ?: return
        set(key, defaultVal)
    }

    fun resetAll() {
        val map = mutableMapOf<String, Any>()
        SCHEMA.forEach { def ->
            map[def.key] = def.defaultValue
        }
        _settingsState.value = map
        persistJson(map)
        SCHEMA.forEach { def ->
            changeListeners.forEach { listener ->
                try { listener(def.key, def.defaultValue) } catch (_: Throwable) {}
            }
        }
    }

    fun isModified(key: String): Boolean {
        val current = _settingsState.value[key] ?: return false
        val defaultVal = SCHEMA.find { it.key == key }?.defaultValue ?: return false
        return current.toString() != defaultVal.toString()
    }

    fun getRawJson(): String {
        return if (settingsFile.exists()) {
            settingsFile.readText()
        } else {
            val json = JSONObject()
            _settingsState.value.forEach { (k, v) -> json.put(k, v) }
            json.toString(2)
        }
    }

    fun setRawJson(rawJson: String): Result<Unit> {
        return try {
            val parsed = JSONObject(rawJson)
            val newMap = _settingsState.value.toMutableMap()

            SCHEMA.forEach { def ->
                if (parsed.has(def.key)) {
                    when (def.type) {
                        SettingType.BOOLEAN -> newMap[def.key] = parsed.getBoolean(def.key)
                        SettingType.INT -> newMap[def.key] = parsed.getInt(def.key)
                        SettingType.ENUM, SettingType.STRING -> newMap[def.key] = parsed.getString(def.key)
                    }
                }
            }

            _settingsState.value = newMap
            settingsFile.writeText(parsed.toString(2))

            // Notify all
            newMap.forEach { (k, v) ->
                changeListeners.forEach { listener ->
                    try { listener(k, v) } catch (_: Throwable) {}
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Export JSON object for Monaco / Web bridge
    fun getSettingsJsonObject(): JSONObject {
        val json = JSONObject()
        _settingsState.value.forEach { (k, v) ->
            json.put(k, v)
        }
        return json
    }

    // Backward compatibility properties for any legacy components
    var showHiddenFiles: Boolean
        get() = getOrDefault(KEY_FILES_SHOW_HIDDEN, false)
        set(value) = set(KEY_FILES_SHOW_HIDDEN, value)

    var folderAnimationEnabled: Boolean = true
    var densityScale: Float = 1.0f
    var naturalSort: Boolean = true
    var bottomBarScale: Float = 1.0f
    var showThumbnails: Boolean = true
    var fileNameMaxLines: Int = 2
    var activePaneHighlightEnabled: Boolean = true
    var isRootEnabled: Boolean = false
    var folderAnimationStyle: String = "slide"
}
