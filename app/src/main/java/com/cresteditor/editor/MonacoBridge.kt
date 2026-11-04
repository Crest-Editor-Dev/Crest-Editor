package com.cresteditor.editor

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener

class MonacoBridge(
    private val context: android.content.Context? = null,
    private var onReadyCallback: (() -> Unit)? = null,
    private var onContentChangedCallback: ((versionId: Int) -> Unit)? = null,
    private var onSaveRequestedCallback: ((content: String) -> Unit)? = null
) {
    private val TAG = "MonacoBridge"
    private var webView: WebView? = null

    fun setWebView(wv: WebView?) {
        this.webView = wv
    }

    @JavascriptInterface
    fun showKeyboard() {
        webView?.post {
            webView?.requestFocus()
            val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(webView, 0)
            imm?.toggleSoftInput(android.view.inputmethod.InputMethodManager.SHOW_FORCED, 0)
        }
    }

    @JavascriptInterface
    fun hideKeyboard() {
        webView?.post {
            val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            webView?.let { wv ->
                imm?.hideSoftInputFromWindow(wv.windowToken, 0)
            }
        }
    }

    fun setCallbacks(
        onReady: (() -> Unit)? = null,
        onContentChanged: ((versionId: Int) -> Unit)? = null,
        onSaveRequested: ((content: String) -> Unit)? = null
    ) {
        this.onReadyCallback = onReady
        this.onContentChangedCallback = onContentChanged
        this.onSaveRequestedCallback = onSaveRequested
    }

    @JavascriptInterface
    fun getClipboardText(): String {
        return try {
            val ctx = context ?: return ""
            val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val item = clipboard?.primaryClip?.getItemAt(0)
            item?.coerceToText(ctx)?.toString() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error reading system clipboard: ${e.message}")
            ""
        }
    }

    @JavascriptInterface
    fun getDeviceInfo(): String {
        return "Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")"
    }

    @JavascriptInterface
    fun onEditorReady(infoJson: String) {
        Log.i(TAG, "Monaco Editor READY: $infoJson")
        webView?.post {
            onReadyCallback?.invoke()
        }
    }

    @JavascriptInterface
    fun onContentChanged(contentStats: String) {
        Log.d(TAG, "Monaco content changed: $contentStats")
        try {
            val json = JSONObject(contentStats)
            val versionId = json.optInt("versionId", 0)
            onContentChangedCallback?.invoke(versionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing contentStats: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onSaveRequested(content: String) {
        Log.i(TAG, "Monaco requested Save (Ctrl+S)")
        onSaveRequestedCallback?.invoke(content)
    }

    @JavascriptInterface
    fun logNative(message: String) {
        Log.i(TAG, "[Monaco JS]: $message")
    }

    companion object {
        fun openFile(webView: WebView?, path: String, content: String, languageId: String) {
            webView?.let { wv ->
                wv.post {
                    val jsCode = "if (window.CrestEditorAPI && window.CrestEditorAPI.openFile) { " +
                            "window.CrestEditorAPI.openFile(" +
                            JSONObject.quote(path) + ", " +
                            JSONObject.quote(content) + ", " +
                            JSONObject.quote(languageId) + "); }"
                    wv.evaluateJavascript(jsCode, null)
                }
            }
        }

        fun getContent(webView: WebView?, callback: (String) -> Unit) {
            webView?.let { wv ->
                wv.evaluateJavascript("window.CrestEditorAPI ? window.CrestEditorAPI.getValue() : ''") { result ->
                    // evaluateJavascript wraps string result in quotes or returns "null"
                    val decoded = try {
                        if (result == null || result == "null") {
                            null
                        } else {
                            JSONTokener(result).nextValue()
                        }
                    } catch (e: Exception) {
                        Log.e("MonacoBridge", "Failed to decode editor content", e)
                        null
                    }

                    if (decoded is String) {
                        callback(decoded)
                    } else {
                        Log.e("MonacoBridge", "Editor returned no valid string content")
                    }
                }
            }
        }

        fun clearEditor(webView: WebView?) {
            webView?.evaluateJavascript(
                "if (window.CrestEditorAPI && window.CrestEditorAPI.clearEditor) { window.CrestEditorAPI.clearEditor(); }",
                null
            )
        }

        fun undo(webView: WebView?) {
            webView?.evaluateJavascript("if (window.CrestEditorAPI && window.CrestEditorAPI.undo) { window.CrestEditorAPI.undo(); }", null)
        }

        fun redo(webView: WebView?) {
            webView?.evaluateJavascript("if (window.CrestEditorAPI && window.CrestEditorAPI.redo) { window.CrestEditorAPI.redo(); }", null)
        }

        fun triggerAction(webView: WebView?, actionId: String) {
            webView?.let { wv ->
                wv.post {
                    val js = "if (window.CrestEditorAPI && window.CrestEditorAPI.triggerAction) { " +
                            "  window.CrestEditorAPI.triggerAction(" + JSONObject.quote(actionId) + "); " +
                            "} else if (typeof monaco !== 'undefined') { " +
                            "  var ed = monaco.editor.getEditors()[0]; " +
                            "  if (ed) { ed.focus(); ed.trigger('commandPalette', " + JSONObject.quote(actionId) + ", null); } " +
                            "}"
                    wv.evaluateJavascript(js, null)
                }
            }
        }

        fun handleKey(webView: WebView?, key: String) {
            webView?.evaluateJavascript(
                "if (window.CrestEditorAPI && window.CrestEditorAPI.handleKey) { window.CrestEditorAPI.handleKey(" +
                        JSONObject.quote(key) + "); }", null
            )
        }

        fun typeText(webView: WebView?, text: String) {
            webView?.evaluateJavascript(
                "if (window.CrestEditorAPI && window.CrestEditorAPI.typeText) { window.CrestEditorAPI.typeText(" +
                        JSONObject.quote(text) + "); }", null
            )
        }

        fun applySettings(webView: WebView?, settingsJson: JSONObject) {
            webView?.let { wv ->
                wv.post {
                    val jsCode = "if (window.CrestEditorAPI && window.CrestEditorAPI.applySettings) { " +
                            "window.CrestEditorAPI.applySettings(" + settingsJson.toString() + "); }"
                    wv.evaluateJavascript(jsCode, null)
                }
            }
        }
    }
}
