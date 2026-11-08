package com.cresteditor.runtime

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

/**
 * BroadcastReceiver to handle URL and file opening requests from the PRoot terminal
 * (invoked via `crest-open`, `xdg-open`, `termux-open`).
 */
class CrestOpenReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CrestOpenReceiver"

        fun openUrl(context: Context, uri: Uri, contentType: String? = null) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (contentType != null) {
                        setDataAndType(uri, contentType)
                    }
                }
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "No application found to open URL: $uri", e)
                Toast.makeText(context, "No app found to open link", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open URL: $uri", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.data ?: run {
            Log.e(TAG, "Called without intent data")
            return
        }

        Log.d(TAG, "Received open request for URI: $data (action: ${intent.action})")
        val contentType = intent.getStringExtra("content-type")
        openUrl(context, data, contentType)
    }
}
