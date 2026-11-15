package com.cresteditor.core.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Centralized Virtual Keyboard (IME) Controller for Crest Editor.
 * Replaces deprecated [InputMethodManager.toggleSoftInput] with modern
 * [WindowCompat] insets controllers while providing robust fallback across all API levels.
 */
object KeyboardHelper {

    /**
     * Requests the virtual keyboard to show for the target view.
     */
    fun show(view: View) {
        view.requestFocus()
        val activity = view.context.findActivity()
        if (activity != null) {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.show(WindowInsetsCompat.Type.ime())
        } else {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * Requests the virtual keyboard to hide.
     */
    fun hide(view: View) {
        val activity = view.context.findActivity()
        if (activity != null) {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.hide(WindowInsetsCompat.Type.ime())
        } else {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    /**
     * Toggles the virtual keyboard: hides if currently visible, shows if hidden.
     */
    fun toggle(view: View) {
        val insets = ViewCompat.getRootWindowInsets(view)
        val isVisible = insets?.isVisible(WindowInsetsCompat.Type.ime())
        if (isVisible == true) {
            hide(view)
        } else {
            show(view)
        }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
