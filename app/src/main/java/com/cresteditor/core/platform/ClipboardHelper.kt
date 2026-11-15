package com.cresteditor.core.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

/**
 * Centralized Android Clipboard utility for Crest Editor.
 * Standardizes clip data generation, text coercion, null-safety, and exception handling.
 */
object ClipboardHelper {

    private const val TAG = "ClipboardHelper"

    /**
     * Copies plain text to the primary system clipboard.
     *
     * @param context Context used to resolve CLIPBOARD_SERVICE
     * @param text Text payload to copy
     * @param label User-visible clip label (defaults to "Crest")
     * @return true if successfully copied, false otherwise
     */
    fun copyText(context: Context, text: String, label: String = "Crest"): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy text to clipboard: ${e.message}")
            false
        }
    }

    /**
     * Retrieves plain text from the primary system clipboard.
     * Uses [ClipData.Item.coerceToText] for safe string coercion across text, URI, and styled items.
     *
     * @param context Context used to resolve CLIPBOARD_SERVICE
     * @return Clipboard text content or empty string if empty or inaccessible
     */
    fun getText(context: Context): String {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return ""
            val item = clipboard.primaryClip?.getItemAt(0)
            item?.coerceToText(context)?.toString() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read text from clipboard: ${e.message}")
            ""
        }
    }
}
