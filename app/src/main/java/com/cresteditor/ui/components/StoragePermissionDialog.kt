package com.cresteditor.ui.components

import androidx.compose.runtime.Composable

/**
 * Storage Permission Dialog styled with the unified VS Code modal theme.
 * Prompts user for all-files access to enable /sdcard browsing in editor & terminal.
 */
@Composable
fun StoragePermissionDialog(
    onDismissRequest: () -> Unit,
    onGrantPermission: () -> Unit
) {
    VSCodeDialog(
        title = "Storage Access Required",
        message = "Crest-Editor needs access to device storage to view and edit project files, and allow the terminal to access /sdcard.",
        confirmButtonText = "Grant Permission",
        dismissButtonText = "Not Now",
        onConfirm = onGrantPermission,
        onDismiss = onDismissRequest,
        onDismissRequest = onDismissRequest
    )
}
