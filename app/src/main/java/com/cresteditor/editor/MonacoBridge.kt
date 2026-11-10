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
        activeWebView = java.lang.ref.WeakReference(wv)
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
        private var activeWebView: java.lang.ref.WeakReference<WebView>? = null

        fun getActiveWebView(): WebView? = activeWebView?.get()

        fun openFile(webView: WebView?, path: String, content: String?, languageId: String, isModified: Boolean = false) {
            val target = webView ?: activeWebView?.get()
            target?.let { wv ->
                wv.post {
                    val contentArg = if (content != null) JSONObject.quote(content) else "null"
                    val jsCode = "if (window.CrestEditorAPI && window.CrestEditorAPI.openFile) { " +
                            "window.CrestEditorAPI.openFile(" +
                            JSONObject.quote(path) + ", " +
                            contentArg + ", " +
                            JSONObject.quote(languageId) + ", " +
                            isModified + "); }"
                    wv.evaluateJavascript(jsCode, null)
                }
            }
        }

        fun closeFile(webView: WebView?, path: String) {
            val target = webView ?: activeWebView?.get()
            target?.let { wv ->
                wv.post {
                    val jsCode = "if (window.CrestEditorAPI && window.CrestEditorAPI.closeFile) { " +
                            "window.CrestEditorAPI.closeFile(" +
                            JSONObject.quote(path) + "); }"
                    wv.evaluateJavascript(jsCode, null)
                }
            }
        }

        fun renameModel(webView: WebView?, oldPath: String, newPath: String, languageId: String) {
            val target = webView ?: activeWebView?.get()
            target?.let { wv ->
                wv.post {
                    val jsCode = "if (window.CrestEditorAPI && window.CrestEditorAPI.renameModel) { " +
                            "window.CrestEditorAPI.renameModel(" +
                            JSONObject.quote(oldPath) + ", " +
                            JSONObject.quote(newPath) + ", " +
                            JSONObject.quote(languageId) + "); }"
                    wv.evaluateJavascript(jsCode, null)
                }
            }
        }

        fun syncExternalContent(webView: WebView?, path: String, content: String) {
            val target = webView ?: activeWebView?.get()
            target?.let { wv ->
                wv.post {
                    val jsCode = "if (window.CrestEditorAPI && window.CrestEditorAPI.syncExternalContent) { " +
                            "window.CrestEditorAPI.syncExternalContent(" +
                            JSONObject.quote(path) + ", " +
                            JSONObject.quote(content) + "); }"
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
            val target = webView ?: activeWebView?.get()
            target?.let { wv ->
                wv.postDelayed({
                    wv.requestFocus()
                    wv.evaluateJavascript("if (window.CrestEditorAPI && window.CrestEditorAPI.undo) { window.CrestEditorAPI.undo(); }", null)
                }, 50)
            }
        }

        fun redo(webView: WebView?) {
            val target = webView ?: activeWebView?.get()
            target?.let { wv ->
                wv.postDelayed({
                    wv.requestFocus()
                    wv.evaluateJavascript("if (window.CrestEditorAPI && window.CrestEditorAPI.redo) { window.CrestEditorAPI.redo(); }", null)
                }, 50)
            }
        }

        fun triggerAction(webView: WebView?, actionId: String) {
            val target = webView ?: activeWebView?.get()
            Log.d("MonacoBridge", "triggerAction requested: actionId=$actionId, targetWebView=$target")
            if (target == null) {
                Log.e("MonacoBridge", "triggerAction FAILED: targetWebView is null!")
                return
            }
            target.postDelayed({
                target.requestFocus()
                val js = "(function() { " +
                        "  try { " +
                        "    if (window.CrestEditorAPI && typeof window.CrestEditorAPI.triggerAction === 'function') { " +
                        "      return window.CrestEditorAPI.triggerAction(" + JSONObject.quote(actionId) + "); " +
                        "    } else if (typeof monaco !== 'undefined') { " +
                        "      var ed = monaco.editor.getEditors()[0]; " +
                        "      if (ed) { " +
                        "        var a = ed.getAction(" + JSONObject.quote(actionId) + "); " +
                        "        if (a && typeof a._run === 'function') { a._run(); return 'action._run'; } " +
                        "        ed.focus(); ed.trigger('commandPalette', " + JSONObject.quote(actionId) + ", null); return 'ed.trigger'; " +
                        "      } " +
                        "    } " +
                        "  } catch(e) { return 'ERR:' + e; } " +
                        "  return 'NOT_FOUND'; " +
                        "})();"
                target.evaluateJavascript(js) { res ->
                    Log.d("MonacoBridge", "triggerAction JS result: $res for action: $actionId")
                }
            }, 50)
        }

        fun revealLine(webView: WebView?, lineNumber: Int, column: Int = 1) {
            val target = webView ?: activeWebView?.get()
            target?.let { wv ->
                wv.post {
                    val js = "(function() { " +
                            "  try { " +
                            "    var ed = typeof monaco !== 'undefined' ? monaco.editor.getEditors()[0] : null; " +
                            "    if (ed) { " +
                            "      var pos = { lineNumber: " + lineNumber + ", column: " + column + " }; " +
                            "      ed.setPosition(pos); " +
                            "      ed.revealPositionInCenter(pos, 0); " +
                            "      ed.focus(); " +
                            "      return 'OK'; " +
                            "    } " +
                            "  } catch(e) { return 'ERR:' + e; } " +
                            "  return 'NO_EDITOR'; " +
                            "})();"
                    wv.evaluateJavascript(js, null)
                }
            }
        }

        fun getLineCount(webView: WebView?, callback: (Int) -> Unit) {
            val target = webView ?: activeWebView?.get()
            if (target == null) {
                callback(1)
                return
            }
            target.evaluateJavascript("(function() { try { var ed = monaco.editor.getEditors()[0]; return ed ? ed.getModel().getLineCount() : 1; } catch(e) { return 1; } })()") { res ->
                val count = res?.toIntOrNull() ?: 1
                callback(count)
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
