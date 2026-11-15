package com.cresteditor.runtime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Universal IPC Bridge Server connecting Linux/PRoot containers to Android.
 *
 * Runs a lightweight localhost TCP listener on 127.0.0.1 to provide:
 * 1. URL Opening (xdg-open, x-www-browser, wslview, crest-open) via Android Intent.
 * 2. Clipboard Integration (termux-clipboard-get/set, xclip, wl-copy, crest-clipboard).
 * 3. In-app Toasts and system feedback.
 */
object TerminalBridgeServer {

    private const val TAG = "TerminalBridgeServer"
    private const val PREFERRED_PORT = 18924

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var serverThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var activePort: Int = PREFERRED_PORT
        private set

    @Synchronized
    fun start(context: Context, filesDir: File, rootfsDir: File? = null) {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
            Log.d(TAG, "TerminalBridgeServer already running on port $activePort")
            writePortFiles(filesDir, rootfsDir, activePort)
            return
        }

        try {
            // Try preferred port first; fall back to OS-assigned ephemeral port (0)
            serverSocket = try {
                ServerSocket(PREFERRED_PORT, 50, InetAddress.getByName("127.0.0.1"))
            } catch (_: Exception) {
                ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            }

            activePort = serverSocket!!.localPort
            isRunning = true

            writePortFiles(filesDir, rootfsDir, activePort)
            Log.i(TAG, "TerminalBridgeServer successfully started on 127.0.0.1:$activePort")

            val sSocket = serverSocket!!
            val appContext = context.applicationContext

            serverThread = thread(name = "CrestBridgeServerThread", isDaemon = true) {
                while (isRunning && !sSocket.isClosed) {
                    try {
                        val clientSocket = sSocket.accept()
                        thread(isDaemon = true) {
                            handleClient(appContext, clientSocket)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.w(TAG, "Server socket accept exception: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TerminalBridgeServer: ${e.message}", e)
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverThread = null
        Log.i(TAG, "TerminalBridgeServer stopped")
    }

    private fun handleClient(context: Context, socket: Socket) {
        try {
            socket.soTimeout = 5000 // 5 seconds timeout
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

            val firstLine = reader.readLine() ?: return
            val trimmed = firstLine.trim()

            when {
                trimmed.startsWith("OPEN ") -> {
                    val url = trimmed.removePrefix("OPEN ").trim()
                    handleOpenUrl(context, url)
                    writer.write("OK\n")
                    writer.flush()
                }

                trimmed.startsWith("CLIP_SET ") -> {
                    val initialText = trimmed.removePrefix("CLIP_SET ")
                    val fullText = StringBuilder(initialText)
                    while (reader.ready()) {
                        val line = reader.readLine() ?: break
                        fullText.append("\n").append(line)
                    }
                    handleClipboardSet(context, fullText.toString())
                    writer.write("OK\n")
                    writer.flush()
                }

                trimmed == "CLIP_GET" -> {
                    val clipText = handleClipboardGet(context)
                    writer.write(clipText)
                    writer.write("\n")
                    writer.flush()
                }

                trimmed.startsWith("TOAST ") -> {
                    val msg = trimmed.removePrefix("TOAST ").trim()
                    mainHandler.post {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    writer.write("OK\n")
                    writer.flush()
                }

                else -> {
                    writer.write("ERR Unknown command\n")
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling bridge request: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleOpenUrl(context: Context, rawUrl: String) {
        var url = rawUrl
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("mailto:") && !url.startsWith("file://")) {
            url = "https://$url"
        }
        mainHandler.post {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot open browser for $url: ${e.message}")
                Toast.makeText(context, "Cannot open URL: $url", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleClipboardSet(context: Context, text: String) {
        mainHandler.post {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Crest", text))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy to clipboard: ${e.message}")
            }
        }
    }

    private fun handleClipboardGet(context: Context): String {
        val latch = CountDownLatch(1)
        var result = ""
        mainHandler.post {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                result = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read clipboard: ${e.message}")
            } finally {
                latch.countDown()
            }
        }
        latch.await(1500, TimeUnit.MILLISECONDS)
        return result
    }

    private fun writePortFiles(filesDir: File, rootfsDir: File?, port: Int) {
        val targets = mutableListOf(
            File(filesDir, "crest-bridge.port"),
            File(filesDir, "tmp/crest-bridge.port")
        )
        if (rootfsDir != null && rootfsDir.exists()) {
            targets.add(File(rootfsDir, "tmp/crest-bridge.port"))
            targets.add(File(rootfsDir, "files/crest-bridge.port"))
        }

        for (target in targets) {
            try {
                target.parentFile?.mkdirs()
                target.writeText(port.toString())
            } catch (_: Exception) {}
        }
    }

    /**
     * Installs crest-open and crest-clipboard scripts plus universal compatibility
     * symlinks into rootfs /usr/local/bin and filesDir/bin.
     */
    fun ensureBridgeScriptsInstalled(rootfsDir: File, filesDir: File) {
        val binDirs = listOf(
            File(rootfsDir, "usr/local/bin"),
            File(filesDir, "bin")
        )

        val crestOpenContent = """
            #!/bin/bash
            # Crest Editor System Bridge - Open URL in Android Browser
            PORT=${'$'}(cat /files/crest-bridge.port 2>/dev/null || cat "${'$'}PREFIX/crest-bridge.port" 2>/dev/null || cat /tmp/crest-bridge.port 2>/dev/null || echo "18924")
            TARGET="${'$'}{1:-}"

            if [ -z "${'$'}TARGET" ]; then
                echo "Usage: crest-open <url-or-path>"
                exit 1
            fi

            exec 3<>/dev/tcp/127.0.0.1/${'$'}PORT 2>/dev/null || {
                echo "[CrestBridge] Error: Crest Bridge Server is not responding on 127.0.0.1:${'$'}PORT" >&2
                exit 1
            }

            echo "OPEN ${'$'}TARGET" >&3
            read -r RESPONSE <&3 2>/dev/null || true
            exec 3<&-
            exec 3>&-
        """.trimIndent() + "\n"

        val crestClipboardContent = """
            #!/bin/bash
            # Crest Editor System Bridge - Android Clipboard Integration
            PORT=${'$'}(cat /files/crest-bridge.port 2>/dev/null || cat "${'$'}PREFIX/crest-bridge.port" 2>/dev/null || cat /tmp/crest-bridge.port 2>/dev/null || echo "18924")
            CMD_NAME=${'$'}(basename "${'$'}0")

            # Check if invoked as a get operation
            for arg in "${'$'}@"; do
                case "${'$'}arg" in
                    -o|-out|--output|--out|get)
                        exec 3<>/dev/tcp/127.0.0.1/${'$'}PORT 2>/dev/null || exit 1
                        echo "CLIP_GET" >&3
                        cat <&3
                        exec 3<&-
                        exec 3>&-
                        exit 0
                        ;;
                esac
            done

            if [ "${'$'}CMD_NAME" = "termux-clipboard-get" ] || [ "${'$'}CMD_NAME" = "wl-paste" ]; then
                exec 3<>/dev/tcp/127.0.0.1/${'$'}PORT 2>/dev/null || exit 1
                echo "CLIP_GET" >&3
                cat <&3
                exec 3<&-
                exec 3>&-
                exit 0
            fi

            # Set operation (termux-clipboard-set, wl-copy, xclip, xsel, crest-clipboard set)
            ARGS=()
            for arg in "${'$'}@"; do
                case "${'$'}arg" in
                    -*|clipboard|primary|secondary|set)
                        # Skip all option flags and keywords
                        ;;
                    *)
                        ARGS+=("${'$'}arg")
                        ;;
                esac
            done

            if [ ${'$'}{#ARGS[@]} -gt 0 ]; then
                CONTENT="${'$'}{ARGS[*]}"
            else
                CONTENT="${'$'}(cat)"
            fi

            exec 3<>/dev/tcp/127.0.0.1/${'$'}PORT 2>/dev/null || exit 1
            printf "CLIP_SET %s\n" "${'$'}CONTENT" >&3
            read -r RESPONSE <&3 2>/dev/null || true
            exec 3<&-
            exec 3>&-
        """.trimIndent() + "\n"

        val openSymlinks = listOf(
            "xdg-open", "x-www-browser", "www-browser", "wslview", "open", "termux-open", "termux-open-url"
        )
        val clipSymlinks = listOf(
            "termux-clipboard-set", "termux-clipboard-get", "xclip", "wl-copy", "wl-paste", "xsel"
        )

        for (binDir in binDirs) {
            try {
                if (!binDir.exists()) binDir.mkdirs()

                val openFile = File(binDir, "crest-open")
                openFile.writeText(crestOpenContent)
                openFile.setExecutable(true, false)

                val clipFile = File(binDir, "crest-clipboard")
                clipFile.writeText(crestClipboardContent)
                clipFile.setExecutable(true, false)

                for (link in openSymlinks) {
                    createSymlinkOrCopy(File(binDir, link), "crest-open", openFile)
                }

                for (link in clipSymlinks) {
                    createSymlinkOrCopy(File(binDir, link), "crest-clipboard", clipFile)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to deploy bridge scripts into ${binDir.absolutePath}: ${e.message}")
            }
        }
    }

    private fun createSymlinkOrCopy(linkFile: File, targetName: String, fallbackFile: File) {
        try {
            if (linkFile.exists() || Files.isSymbolicLink(linkFile.toPath())) {
                Files.deleteIfExists(linkFile.toPath())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.createSymbolicLink(linkFile.toPath(), Paths.get(targetName))
            } else {
                fallbackFile.copyTo(linkFile, overwrite = true)
            }
            linkFile.setExecutable(true, false)
        } catch (_: Exception) {
            try {
                fallbackFile.copyTo(linkFile, overwrite = true)
                linkFile.setExecutable(true, false)
            } catch (_: Exception) {}
        }
    }
}
