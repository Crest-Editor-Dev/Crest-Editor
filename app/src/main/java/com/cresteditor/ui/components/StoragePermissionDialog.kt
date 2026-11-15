package com.cresteditor.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cresteditor.ui.theme.CrestAccentPrimary
import com.cresteditor.ui.theme.CrestSurface
import com.cresteditor.ui.theme.CrestTextActive
import com.cresteditor.ui.theme.CrestTextPrimary
import com.cresteditor.ui.theme.CrestTextSecondary

/**
 * Dialog shown on app launch if storage permissions have not yet been granted.
 * Enables users to grant 'All Files Access' to ensure /sdcard is accessible
 * inside Monaco and the integrated PRoot terminal.
 */
@Composable
fun StoragePermissionDialog(
    onDismissRequest: () -> Unit,
    onGrantPermission: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(12.dp),
        containerColor = CrestSurface,
        titleContentColor = CrestTextActive,
        textContentColor = CrestTextPrimary,
        icon = {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = CrestAccentPrimary
            )
        },
        title = {
            Text(
                text = "Storage Access Required",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = CrestTextActive
            )
        },
        text = {
            Text(
                text = "Crest-Editor needs access to device storage to view and edit project files, and allow the terminal to access /sdcard.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = CrestTextPrimary
            )
        },
        confirmButton = {
            Button(
                onClick = onGrantPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrestAccentPrimary,
                    contentColor = CrestTextActive
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Grant Permission", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = CrestTextSecondary
                )
            ) {
                Text(text = "Not Now")
            }
        }
    )
}
