package com.cresteditor.runtime

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import com.cresteditor.core.logger.AppLogger
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * NativeBootstrapManager manages the Termux-based userland inside Crest-Editor (`com.crestd`).
 *
 * It operates completely native without PRoot ptrace overhead:
 * 1. Pinned to targetSdk 28 (untrusted_app_27 domain) to allow direct execve.
 * 2. In-place 10-byte translation from com.termux / com.zdroid to com.crestd.
 * 3. Symlink restoration from SYMLINKS.txt using android.system.Os.symlink.
 * 4. Injects libtermux-exec.so via LD_PRELOAD to rewrite shebangs on the fly.
 */
class NativeBootstrapManager(private val context: Context) {

    companion object {
        private const val TAG = "NativeBootstrapManager"
        private const val BOOTSTRAP_URL =
            "https://github.com/termux/termux-packages/releases/latest/download/bootstrap-aarch64.zip"
        private const val PACKAGE_NAME_CRESTD = "com.crestd"
        private const val SYMLINKS_ENTRY = "SYMLINKS.txt"
        private const val SYMLINKS_DELIM = "←"
        private val setupMutex = Any()
    }

    val filesDir: File get() = context.filesDir
    val prefixDir: File get() = File(filesDir, "usr")
    val homeDir: File get() = File(filesDir, "home")
    val binDir: File get() = File(prefixDir, "bin")
    val tmpDir: File get() = File(prefixDir, "tmp")
    val bashBin: File get() = File(binDir, "bash")
    val libTermuxExec: File get() = File(prefixDir, "lib/libtermux-exec.so")

    val isInstalled: Boolean
        get() = bashBin.exists() && bashBin.canExecute() && libTermuxExec.exists()

    fun hasBinary(binaryName: String): Boolean {
        if (!isInstalled) return false
        return File(binDir, binaryName).exists()
    }

    /**
     * Prepares the native bootstrap environment.
     * Downloads and extracts the Termux userland if not already present.
     */
    fun setupEnvironment(onStatusUpdate: (String) -> Unit): Boolean {
        synchronized(setupMutex) {
            if (isInstalled) {
                AppLogger.d(TAG, "Native bootstrap already installed at ${prefixDir.absolutePath}")
                TerminalBridgeServer.start(context, filesDir, prefixDir)
                TerminalBridgeServer.ensureBridgeScriptsInstalled(prefixDir, filesDir)
                return true
            }

            try {
                if (!homeDir.exists()) homeDir.mkdirs()
                if (!tmpDir.exists()) tmpDir.mkdirs()

                onStatusUpdate("Downloading Native Termux Bootstrap...")
                val zipFile = File(filesDir, "bootstrap-aarch64.zip")
                downloadBootstrap(BOOTSTRAP_URL, zipFile, onStatusUpdate)

                onStatusUpdate("Extracting Bootstrap Environment...")
                val stagingDir = File(filesDir, "usr.staging")
                if (stagingDir.exists()) stagingDir.deleteRecursively()
                stagingDir.mkdirs()

                extractZip(zipFile, stagingDir, onStatusUpdate)

                onStatusUpdate("Patching binary package paths...")
                patchBinariesInDirectory(stagingDir)

                onStatusUpdate("Finalizing installation...")
                if (prefixDir.exists()) prefixDir.deleteRecursively()
                if (!stagingDir.renameTo(prefixDir)) {
                    // Fallback to copy if atomic rename fails
                    stagingDir.copyRecursively(prefixDir, overwrite = true)
                    stagingDir.deleteRecursively()
                }

                zipFile.delete()

                // Ensure bash and core utilities have executable permissions
                ensureExecutablePermissions(prefixDir)

                TerminalBridgeServer.start(context, filesDir, prefixDir)
                TerminalBridgeServer.ensureBridgeScriptsInstalled(prefixDir, filesDir)

                AppLogger.d(TAG, "Native bootstrap successfully installed!")
                onStatusUpdate("Native Terminal Ready")
                return true
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to setup native bootstrap: ${e.message}")
                onStatusUpdate("Setup Failed: ${e.message}")
                return false
            }
        }
    }

    /**
     * Creates an active TerminalSession attached to native bash.
     */
    fun createTerminalSession(
        client: TerminalSessionClient,
        initialPath: String? = null
    ): TerminalSession {
        val cwd = if (!initialPath.isNullOrBlank() && File(initialPath).exists()) {
            initialPath
        } else {
            homeDir.absolutePath
        }

        val env = arrayOf(
            "HOME=${homeDir.absolutePath}",
            "PREFIX=${prefixDir.absolutePath}",
            "TERMUX__HOME=${homeDir.absolutePath}",
            "TERMUX__PREFIX=${prefixDir.absolutePath}",
            "TERMUX_APP__PACKAGE_NAME=$PACKAGE_NAME_CRESTD",
            "PATH=${binDir.absolutePath}:${binDir.absolutePath}/applets",
            "LD_PRELOAD=${libTermuxExec.absolutePath}",
            "LD_LIBRARY_PATH=${prefixDir.absolutePath}/lib",
            "LANG=en_US.UTF-8",
            "LC_ALL=en_US.UTF-8",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "TMPDIR=${tmpDir.absolutePath}",
            "SHELL=${bashBin.absolutePath}",
            "CREST_BRIDGE_PORT=${TerminalBridgeServer.activePort}"
        )

        val args = arrayOf(bashBin.absolutePath, "-l")

        return TerminalSession(
            bashBin.absolutePath,
            cwd,
            args,
            env,
            10000,
            client
        )
    }

    private fun downloadBootstrap(
        sourceUrl: String,
        destination: File,
        onStatusUpdate: (String) -> Unit
    ) {
        var currentUrl = sourceUrl
        var connection: HttpURLConnection
        var redirects = 0

        while (true) {
            val url = URL(currentUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "Crest-Bootstrap-Installer")
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode in 300..399) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                if (newUrl == null || redirects++ >= 5) {
                    throw IOException("Too many redirects or missing Location header from $currentUrl")
                }
                currentUrl = newUrl
            } else if (responseCode == HttpURLConnection.HTTP_OK) {
                break
            } else {
                connection.disconnect()
                throw IOException("Server returned HTTP $responseCode: ${connection.responseMessage}")
            }
        }

        val totalBytes = connection.contentLengthLong
        var downloadedBytes = 0L

        connection.inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var lastPercent = -1

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            val mb = downloadedBytes / (1024 * 1024)
                            val totalMb = totalBytes / (1024 * 1024)
                            onStatusUpdate("Downloading Bootstrap: $percent% ($mb/$totalMb MB)")
                        }
                    }
                }
            }
        }
        connection.disconnect()
    }

    private fun extractZip(
        zipFile: File,
        targetDir: File,
        onStatusUpdate: (String) -> Unit
    ) {
        val symlinks = mutableListOf<Pair<String, String>>()

        FileInputStream(zipFile).use { fis ->
            ZipInputStream(BufferedInputStream(fis)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: break
                    val entryName = currentEntry.name

                    if (entryName == SYMLINKS_ENTRY) {
                        val reader = BufferedReader(InputStreamReader(zis))
                        reader.forEachLine { line ->
                            val parts = line.split(SYMLINKS_DELIM)
                            if (parts.size == 2) {
                                val target = parts[0]
                                val linkRel = parts[1].removePrefix("./")
                                symlinks.add(target to linkRel)
                            }
                        }
                        zis.closeEntry()
                        continue
                    }

                    val outFile = File(targetDir, entryName)
                    if (currentEntry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                }
            }
        }

        // Replay symlinks via native Os.symlink
        onStatusUpdate("Replaying Symlinks...")
        for ((target, linkRel) in symlinks) {
            val linkFile = File(targetDir, linkRel)
            linkFile.parentFile?.mkdirs()
            if (linkFile.exists()) linkFile.delete()
            try {
                Os.symlink(target, linkFile.absolutePath)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Symlink failed for $target -> ${linkFile.absolutePath}: ${e.message}")
            }
        }
    }

    /**
     * In-place 10-byte binary translation:
     * "com.zdroid" (10 bytes) -> "com.crestd" (10 bytes)
     * "com.termux" (10 bytes) -> "com.crestd" (10 bytes)
     */
    private fun patchBinariesInDirectory(dir: File) {
        val zdroidBytes = "com.zdroid".toByteArray(Charsets.US_ASCII)
        val termuxBytes = "com.termux".toByteArray(Charsets.US_ASCII)
        val crestdBytes = PACKAGE_NAME_CRESTD.toByteArray(Charsets.US_ASCII)

        dir.walkTopDown().forEach { file ->
            if (file.isFile && !file.isSymbolicLink()) {
                val isBinOrLib = file.absolutePath.contains("/bin/") ||
                        file.absolutePath.contains("/lib/") ||
                        file.absolutePath.contains("/etc/")

                if (isBinOrLib && file.length() < 30 * 1024 * 1024) { // Under 30MB
                    replaceBytesInFile(file, zdroidBytes, crestdBytes)
                    replaceBytesInFile(file, termuxBytes, crestdBytes)
                }
            }
        }
    }

    private fun replaceBytesInFile(file: File, target: ByteArray, replacement: ByteArray) {
        try {
            val bytes = file.readBytes()
            var modified = false
            var i = 0
            while (i <= bytes.size - target.size) {
                var match = true
                for (j in target.indices) {
                    if (bytes[i + j] != target[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    System.arraycopy(replacement, 0, bytes, i, replacement.size)
                    modified = true
                    i += target.size
                } else {
                    i++
                }
            }
            if (modified) {
                file.writeBytes(bytes)
            }
        } catch (_: Exception) {
            // Ignore unreadable/protected files
        }
    }

    private fun ensureExecutablePermissions(dir: File) {
        val execDirs = listOf(
            File(dir, "bin"),
            File(dir, "libexec"),
            File(dir, "lib")
        )
        for (d in execDirs) {
            d.walkTopDown().forEach { file ->
                if (file.isFile) {
                    file.setExecutable(true, false)
                    file.setReadable(true, false)
                }
            }
        }
    }

    private fun File.isSymbolicLink(): Boolean {
        return try {
            val canon = if (parent == null) this else File(parentFile?.canonicalFile, name)
            canon.canonicalPath != canon.absolutePath
        } catch (_: Exception) {
            false
        }
    }
}
