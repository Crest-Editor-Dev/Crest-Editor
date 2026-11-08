package com.cresteditor.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import org.tukaani.xz.XZInputStream

class RootfsManager(private val context: Context) {

    companion object {
        private const val TAG = "RootfsManager"
        private val setupMutex = Any()
        const val ROOTFS_DOWNLOAD_URL = "https://github.com/Crest-Editor-Dev/Crest-Rootfs/releases/download/latest/void-aarch64-rootfs.tar.xz"
        const val EXPECTED_ROOTFS_SHA256 = "cd51d38c80a7a86a58891260f52a107bb68b7e862488daa4d877df6ba5106441"
    }

    val filesDir: File get() = context.filesDir
    val rootfsDir: File get() {
        val modernDir = File(filesDir, "rootfs")
        val legacyDir = File(filesDir, "alpine")
        return if (modernDir.exists()) modernDir else if (legacyDir.exists()) legacyDir else modernDir
    }

    // Backwards-compatibility alias
    val alpineDir: File get() = rootfsDir

    val isInstalled: Boolean get() = rootfsDir.exists() && validateRootfs(rootfsDir)

    fun getArchName(): String = "arm64"

    /**
     * Validates that the rootfs contains a legitimate Void Linux installation with
     * /etc/os-release, /usr/bin/xbps-install, and glibc runtime libraries.
     */
    fun validateRootfs(dir: File): Boolean {
        try {
            val osRelease = File(dir, "etc/os-release")
            if (!osRelease.exists() || !osRelease.readText().contains("void", ignoreCase = true)) {
                Log.w(TAG, "Validation failed: etc/os-release missing or does not identify as Void Linux")
                return false
            }

            val xbps = File(dir, "usr/bin/xbps-install")
            if (!xbps.exists() || !xbps.isFile) {
                Log.w(TAG, "Validation failed: usr/bin/xbps-install missing")
                return false
            }

            val libc = File(dir, "usr/lib/libc.so.6")
            val ldLinux = File(dir, "usr/lib/ld-linux-aarch64.so.1")
            if (!libc.exists() || !ldLinux.exists()) {
                Log.w(TAG, "Validation failed: glibc libraries missing (libc.so.6 or ld-linux-aarch64.so.1)")
                return false
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error validating rootfs at ${dir.absolutePath}: ${e.message}", e)
            return false
        }
    }

    /**
     * Sets up the Void Linux RootFS environment:
     * - Preserves any existing, functional rootfs unless replacement succeeds completely.
     * - Downloads archive with redirect handling and .part detection.
     * - Verifies SHA-256 integrity before unpacking.
     * - Unpacks into a staging directory and verifies validation markers.
     * - Atomically swaps staging to the target rootfs directory.
     */
    fun setupRootfsEnvironment(onProgress: (String) -> Unit = {}): Boolean = synchronized(setupMutex) {
        try {
            if (!filesDir.exists()) filesDir.mkdirs()

            val arch = getArchName()
            Log.d(TAG, "Setting up RootFS environment for architecture: $arch")

            // 1. Copy environment setup scripts from assets
            onProgress("Preparing environment scripts...")
            copyAssetFile("alpine/init-sandbox.sh", File(filesDir, "init-sandbox.sh"))
            copyAssetFile("alpine/init-alpine.sh", File(filesDir, "init-alpine.sh"))
            copyAssetFile("alpine/setup-alpine.sh", File(filesDir, "setup-alpine.sh"))
            makeExecutable(File(filesDir, "init-sandbox.sh"))
            makeExecutable(File(filesDir, "init-alpine.sh"))
            makeExecutable(File(filesDir, "setup-alpine.sh"))

            // 2. Set up PRoot native binaries
            onProgress("Setting up PRoot execution engine...")
            copyNativeBinaries(arch)

            // 3. Check if already installed and validated
            if (isInstalled) {
                fixPermissionsAndLinks()
                ensureMountPointsExist()
                ensureLegacySymlink()
                onProgress("Void Linux environment active! ✔")
                return true
            }

            // 4. Download and stage new RootFS
            val archiveFile = File(filesDir, "void-aarch64-rootfs.tar.xz")
            val stagingDir = File(filesDir, "rootfs_staging")

            try {
                // Step A: Download
                downloadRootfs(archiveFile, onProgress)

                // Step B: SHA-256 Verification
                onProgress("Verifying RootFS SHA-256 integrity...")
                val verified = verifySha256(archiveFile, EXPECTED_ROOTFS_SHA256)
                if (!verified) {
                    archiveFile.delete()
                    throw IOException("RootFS SHA-256 checksum verification failed!")
                }
                onProgress("SHA-256 verified successfully! ✔")

                // Step C: Extract to staging directory
                if (stagingDir.exists()) {
                    stagingDir.deleteRecursively()
                }
                stagingDir.mkdirs()

                onProgress("Extracting Void Linux RootFS to staging...")
                extractTar(archiveFile, stagingDir)
                onProgress("Extraction completed! ✔")

                // Step D: Validate extracted rootfs in staging
                onProgress("Validating extracted Void Linux RootFS (/etc/os-release, xbps, glibc)...")
                if (!validateRootfs(stagingDir)) {
                    throw IOException("Extracted RootFS failed validation: missing os-release, xbps-install, or glibc.")
                }
                onProgress("RootFS validation passed! ✔")

                // Step E: Atomic Activation (Swap staging -> rootfs)
                onProgress("Activating Void Linux RootFS...")
                val targetDir = File(filesDir, "rootfs")
                val backupDir = File(filesDir, "rootfs_old")
                if (backupDir.exists()) backupDir.deleteRecursively()

                if (targetDir.exists()) {
                    if (!targetDir.renameTo(backupDir)) {
                        targetDir.deleteRecursively()
                    }
                }

                val activated = if (stagingDir.renameTo(targetDir)) {
                    true
                } else {
                    // Fallback to recursive copy if cross-filesystem or rename fails
                    stagingDir.copyRecursively(targetDir, overwrite = true)
                    stagingDir.deleteRecursively()
                    true
                }

                if (!activated || !validateRootfs(targetDir)) {
                    // Restore backup if activation failed
                    if (backupDir.exists()) {
                        targetDir.deleteRecursively()
                        backupDir.renameTo(targetDir)
                    }
                    throw IOException("Failed to activate RootFS into target directory.")
                }

                // Clean up backup and archive
                if (backupDir.exists()) backupDir.deleteRecursively()
                archiveFile.delete()

                ensureLegacySymlink()
                fixPermissionsAndLinks()
                ensureMountPointsExist()

                onProgress("Void Linux RootFS initialized successfully! 🚀")
                return true

            } catch (e: Exception) {
                stagingDir.deleteRecursively()
                Log.e(TAG, "RootFS staging / installation failed: ${e.message}", e)
                onProgress("Error: ${e.message}")
                return false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup Linux environment", e)
            onProgress("Setup Error: ${e.message}")
            return false
        }
    }

    // Backwards-compatibility method alias
    fun setupAlpineEnvironment(onProgress: (String) -> Unit = {}): Boolean = setupRootfsEnvironment(onProgress)

    /**
     * Downloads the RootFS archive with redirect following, .part buffering, and size verification.
     */
    private fun downloadRootfs(targetFile: File, onProgress: (String) -> Unit) {
        val parentDir = targetFile.parentFile ?: filesDir
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        val partFile = File(parentDir, "${targetFile.name}.part")

        // 1. If target file already exists and valid, skip download
        if (targetFile.exists() && targetFile.length() > 1000000) {
            onProgress("Checking existing archive integrity...")
            if (verifySha256(targetFile, EXPECTED_ROOTFS_SHA256)) {
                Log.d(TAG, "Existing archive ${targetFile.absolutePath} is valid, skipping download.")
                onProgress("Archive already present and verified! ✔")
                return
            } else {
                Log.w(TAG, "Existing archive SHA-256 mismatch, removing invalid file.")
                targetFile.delete()
            }
        }

        // 2. Check local candidates
        val localCandidates = listOf(
            File("/sdcard/Download/void-aarch64-rootfs.tar.xz"),
            File("/storage/emulated/0/Download/void-aarch64-rootfs.tar.xz")
        )
        val localMatch = localCandidates.firstOrNull { it.exists() && it.length() > 1000000 }
        if (localMatch != null && localMatch != targetFile) {
            onProgress("Using local RootFS archive: ${localMatch.name}")
            localMatch.copyTo(targetFile, overwrite = true)
            return
        }

        // 3. Connect and follow redirects
        onProgress("Connecting to Void Linux server...")

        var currentUrl = ROOTFS_DOWNLOAD_URL
        var connection: HttpURLConnection? = null
        var redirectCount = 0
        val maxRedirects = 6
        var totalLength = -1L

        try {
            while (redirectCount < maxRedirects) {
                val urlObj = URL(currentUrl)
                connection = (urlObj.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 30000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "CrestEditor-RootfsDownloader/1.0")
                }

                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) {
                        throw IOException("HTTP redirect without Location header (code $responseCode)")
                    }
                    currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        URL(urlObj, location).toString()
                    }
                    connection.disconnect()
                    connection = null
                    redirectCount++
                } else if (responseCode in 200..299) {
                    break
                } else {
                    throw IOException("HTTP download failed with code $responseCode: ${connection.responseMessage}")
                }
            }

            if (redirectCount >= maxRedirects || connection == null) {
                throw IOException("Too many HTTP redirects ($redirectCount)")
            }

            totalLength = connection.contentLengthLong
            val totalMbStr = if (totalLength > 0) String.format("%.1f", totalLength / 1048576.0) else "24.0"

            var downloadedBytes = 0L
            val buffer = ByteArray(65536)
            var lastReportTime = 0L

            Log.d(TAG, "Starting stream download: url=$currentUrl target=${targetFile.absolutePath} part=${partFile.absolutePath} totalLength=$totalLength")
            onProgress("Download started: void-aarch64-rootfs.tar.xz (~$totalMbStr MB)")

            connection.inputStream.use { input ->
                FileOutputStream(partFile, false).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 200 || downloadedBytes == totalLength) {
                            lastReportTime = now
                            val currentMb = String.format("%.1f", downloadedBytes / 1048576.0)
                            val percent = if (totalLength > 0) (downloadedBytes * 100 / totalLength).toInt() else 0
                            onProgress("Downloading Void RootFS: $percent% ($currentMb / $totalMbStr MB)")
                        }
                    }
                    output.flush()
                }
            }

            // Verify complete download stream
            if (totalLength > 0 && downloadedBytes != totalLength) {
                throw IOException("Incomplete download: expected $totalLength bytes, received $downloadedBytes bytes")
            }

            // Verify .part file exists and has non-zero size before finalization
            if (!partFile.exists()) {
                throw IOException("Finalization failed: .part file does not exist at ${partFile.absolutePath}")
            }
            val partLength = partFile.length()
            if (partLength <= 0L) {
                throw IOException("Finalization failed: .part file is empty (0 bytes) at ${partFile.absolutePath}")
            }
            if (totalLength > 0 && partLength != totalLength) {
                throw IOException("Finalization failed: .part file size ($partLength bytes) does not match expected ($totalLength bytes) at ${partFile.absolutePath}")
            }

            Log.d(TAG, "Download complete. Finalizing archive: source=${partFile.absolutePath} ($partLength bytes) -> target=${targetFile.absolutePath}")
            onProgress("Finalizing downloaded archive (${String.format("%.1f", partLength / 1048576.0)} MB)...")

            // Promote .part to target file
            if (targetFile.exists()) {
                targetFile.delete()
            }

            var promoted = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Files.move(partFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    promoted = true
                } catch (e: Exception) {
                    Log.w(TAG, "Atomic move failed, falling back to standard replace move: ${e.message}")
                    try {
                        Files.move(partFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        promoted = true
                    } catch (e2: Exception) {
                        Log.w(TAG, "Standard Files.move failed: ${e2.message}")
                    }
                }
            }

            if (!promoted) {
                if (partFile.renameTo(targetFile)) {
                    promoted = true
                } else {
                    Log.w(TAG, "File.renameTo failed, attempting copyTo fallback")
                    if (partFile.exists()) {
                        partFile.copyTo(targetFile, overwrite = true)
                        if (targetFile.exists() && targetFile.length() == partLength) {
                            partFile.delete()
                            promoted = true
                        }
                    }
                }
            }

            if (!promoted || !targetFile.exists() || targetFile.length() != partLength) {
                throw IOException("Failed to promote .part file (${partFile.absolutePath}, $partLength bytes) to final archive (${targetFile.absolutePath})")
            }

            Log.d(TAG, "Archive finalized successfully: target=${targetFile.absolutePath} (size=${targetFile.length()} bytes)")
            onProgress("Download completed successfully! (${String.format("%.1f", downloadedBytes / 1048576.0)} MB) ✔")

        } catch (e: Exception) {
            Log.e(TAG, "downloadRootfs failed: ${e.message} (partFile=${partFile.absolutePath}, exists=${partFile.exists()}, size=${if (partFile.exists()) partFile.length() else -1})", e)
            // Only clean up partial file if download was incomplete/aborted during transfer
            if (partFile.exists() && totalLength > 0 && partFile.length() < totalLength) {
                Log.w(TAG, "Cleaning up incomplete partial download (${partFile.length()} / $totalLength bytes)")
                partFile.delete()
            }
            throw e
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {}
        }
    }

    /**
     * Computes the SHA-256 hash of a file and compares it to the expected checksum.
     */
    private fun verifySha256(file: File, expectedHash: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buf = ByteArray(65536)
            while (true) {
                val read = fis.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        val matches = actualHash.equals(expectedHash.trim(), ignoreCase = true)
        if (!matches) {
            Log.e(TAG, "SHA256 mismatch! Expected: $expectedHash, Got: $actualHash")
        }
        return matches
    }

    private fun getDecompressedStream(file: File): InputStream {
        val fis = file.inputStream().buffered()
        fis.mark(16)
        val header = ByteArray(6)
        val read = fis.read(header)
        fis.reset()

        // XZ magic bytes: 0xFD, '7', 'z', 'X', 'Z', 0x00
        val isXz = read >= 6 &&
                (header[0].toInt() and 0xFF) == 0xFD &&
                header[1] == '7'.code.toByte() &&
                header[2] == 'z'.code.toByte() &&
                header[3] == 'X'.code.toByte() &&
                header[4] == 'Z'.code.toByte() &&
                header[5] == 0.toByte()

        // GZIP magic bytes: 0x1F, 0x8B
        val isGzip = read >= 2 &&
                (header[0].toInt() and 0xFF) == 0x1F &&
                (header[1].toInt() and 0xFF) == 0x8B

        return when {
            isXz -> XZInputStream(fis)
            isGzip -> GZIPInputStream(fis)
            else -> fis
        }
    }

    /**
     * Pure Kotlin Tar extractor with XZ, GZIP, GNU LongName/LongLink, and proper Hardlink/Symlink handling.
     */
    private fun extractTar(tarFile: File, destDir: File) {
        getDecompressedStream(tarFile).use { tarStream ->
            val buffer = ByteArray(512)
            var nextLongName: String? = null
            var nextLongLink: String? = null

            while (true) {
                val read = readFully(tarStream, buffer, 0, 512)
                if (read < 512) break

                // Two consecutive 512-byte zero blocks signal EOF in Tar
                if (buffer.all { it == 0.toByte() }) break

                // Type flag at offset 156
                val typeFlag = buffer[156].toInt().toChar()

                // File size in octal (offset 124, length 12)
                val sizeStr = String(buffer, 124, 12, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                val fileSize = sizeStr.toLongOrNull(8) ?: 0L

                // Handle GNU Long Name / Long Link extensions
                if (typeFlag == 'L') {
                    val nameBuf = ByteArray(fileSize.toInt())
                    readFully(tarStream, nameBuf, 0, nameBuf.size)
                    nextLongName = String(nameBuf, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                    skipPadding(tarStream, fileSize)
                    continue
                } else if (typeFlag == 'K') {
                    val linkBuf = ByteArray(fileSize.toInt())
                    readFully(tarStream, linkBuf, 0, linkBuf.size)
                    nextLongLink = String(linkBuf, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                    skipPadding(tarStream, fileSize)
                    continue
                } else if (typeFlag == 'x' || typeFlag == 'g') { // PAX Extended headers
                    skipPayload(tarStream, fileSize)
                    skipPadding(tarStream, fileSize)
                    continue
                }

                // Header filename (offset 0, length 100)
                val headerNameBytes = buffer.copyOfRange(0, 100)
                var name = nextLongName ?: String(headerNameBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                nextLongName = null

                if (name.isEmpty()) continue

                // Prefix (offset 345, length 155) for long paths if not overridden by GNU LongName
                if (buffer[345] != 0.toByte() && !name.contains("/")) {
                    val prefixBytes = buffer.copyOfRange(345, 500)
                    val prefix = String(prefixBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                    if (prefix.isNotEmpty()) {
                        name = "$prefix/$name"
                    }
                }

                // File mode (offset 100, length 8)
                val modeStr = String(buffer, 100, 8, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                val mode = modeStr.toIntOrNull(8) ?: 0

                val targetFile = File(destDir, name)

                when (typeFlag) {
                    '5' -> { // Directory
                        targetFile.mkdirs()
                    }
                    '1' -> { // Hard link: Attempt real filesystem link; if kernel blocks (Android EPERM), clone existing target
                        val headerLinkBytes = buffer.copyOfRange(157, 257)
                        val linkTarget = nextLongLink ?: String(headerLinkBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                        nextLongLink = null

                        val existingFile = File(destDir, linkTarget)
                        targetFile.parentFile?.mkdirs()

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                Files.deleteIfExists(targetFile.toPath())
                                Files.createLink(targetFile.toPath(), existingFile.toPath())
                            } catch (e: Exception) {
                                if (existingFile.exists()) {
                                    existingFile.copyTo(targetFile, overwrite = true)
                                    if ((mode and 73) != 0 || name.contains("bin/") || name.contains("sbin/") || name.contains("lib/")) {
                                        makeExecutable(targetFile)
                                    }
                                } else {
                                    Log.w(TAG, "Hardlink target missing for $name -> $linkTarget: ${e.message}")
                                }
                            }
                        } else {
                            if (existingFile.exists()) {
                                existingFile.copyTo(targetFile, overwrite = true)
                            }
                        }
                    }
                    '2' -> { // Symbolic link
                        val headerLinkBytes = buffer.copyOfRange(157, 257)
                        val linkTarget = nextLongLink ?: String(headerLinkBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                        nextLongLink = null

                        targetFile.parentFile?.mkdirs()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                Files.deleteIfExists(targetFile.toPath())
                                Files.createSymbolicLink(targetFile.toPath(), Paths.get(linkTarget))
                            } catch (e: Exception) {
                                Log.w(TAG, "Symlink creation skipped for $name -> $linkTarget: ${e.message}")
                            }
                        }
                    }
                    else -> { // Regular file ('0' or '\0')
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { fos ->
                            var remaining = fileSize
                            val copyBuf = ByteArray(8192)
                            while (remaining > 0) {
                                val toRead = Math.min(remaining, copyBuf.size.toLong()).toInt()
                                val bytesRead = tarStream.read(copyBuf, 0, toRead)
                                if (bytesRead <= 0) break
                                fos.write(copyBuf, 0, bytesRead)
                                remaining -= bytesRead
                            }
                        }
                        skipPadding(tarStream, fileSize)

                        // Set permissions for executables
                        if ((mode and 73) != 0 || name.contains("bin/") || name.contains("sbin/") || name.contains("lib/")) {
                            makeExecutable(targetFile)
                        } else {
                            targetFile.setReadable(true, false)
                        }
                    }
                }
            }
        }
    }

    private fun skipPadding(stream: InputStream, fileSize: Long) {
        val remainder = (fileSize % 512).toInt()
        if (remainder > 0) {
            val padBuf = ByteArray(512 - remainder)
            readFully(stream, padBuf, 0, padBuf.size)
        }
    }

    private fun skipPayload(stream: InputStream, fileSize: Long) {
        var remaining = fileSize
        val copyBuf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = Math.min(remaining, copyBuf.size.toLong()).toInt()
            val bytesRead = stream.read(copyBuf, 0, toRead)
            if (bytesRead <= 0) break
            remaining -= bytesRead
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(buffer, offset + totalRead, length - totalRead)
            if (read <= 0) break
            totalRead += read
        }
        return totalRead
    }

    fun fixPermissionsAndLinks() {
        try {
            val targetDirs = listOf(
                File(rootfsDir, "bin"),
                File(rootfsDir, "sbin"),
                File(rootfsDir, "lib"),
                File(rootfsDir, "lib64"),
                File(rootfsDir, "usr/bin"),
                File(rootfsDir, "usr/sbin"),
                File(rootfsDir, "usr/lib"),
                File(rootfsDir, "libexec")
            )

            for (dir in targetDirs) {
                if (dir.exists()) {
                    dir.walkTopDown().forEach { file ->
                        try {
                            file.setReadable(true, false)
                            file.setExecutable(true, false)
                        } catch (_: Exception) {}
                    }
                }
            }

            // Ensure glibc ld-linux dynamic linker is executable
            val ldLinux = File(rootfsDir, "usr/lib/ld-linux-aarch64.so.1")
            if (ldLinux.exists()) {
                makeExecutable(ldLinux)
            }
        } catch (e: Exception) {
            Log.w(TAG, "fixPermissionsAndLinks error: ${e.message}")
        }
    }

    // Backwards-compatibility alias
    fun fixAlpinePermissionsAndLinks() = fixPermissionsAndLinks()

    fun ensureLegacySymlink(target: File = rootfsDir) {
        try {
            val legacyDir = File(filesDir, "alpine")
            if (!legacyDir.exists() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.createSymbolicLink(legacyDir.toPath(), target.toPath())
            }
        } catch (_: Exception) {}
    }

    private fun copyNativeBinaries(arch: String) {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val nativeLibsDir = "alpine/$arch/libs"
        val nativeLibs = arrayOf("libproot-xed.so", "libproot.so", "libtalloc.so")

        for (libName in nativeLibs) {
            val nativeFile = File(nativeDir, libName)
            val outFile = File(filesDir, libName)
            if (nativeFile.exists()) {
                try {
                    if (!outFile.exists() || outFile.length() != nativeFile.length()) {
                        nativeFile.copyTo(outFile, overwrite = true)
                    }
                    makeExecutable(outFile)
                } catch (e: Exception) {
                    Log.w(TAG, "Native copy fallback for $libName: ${e.message}")
                }
            } else {
                try {
                    copyAssetFile("$nativeLibsDir/$libName", outFile)
                    makeExecutable(outFile)
                    Log.d(TAG, "Copied native library $libName to ${outFile.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "Asset copy skipped for $libName: ${e.message}")
                }
            }
        }

        // Create libtalloc.so.2 link for Android dynamic linker
        try {
            val talloc2 = File(filesDir, "libtalloc.so.2")
            val talloc1 = File(filesDir, "libtalloc.so")
            if (talloc1.exists()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try { Files.deleteIfExists(talloc2.toPath()) } catch (_: Exception) {}
                    try {
                        Files.createSymbolicLink(talloc2.toPath(), Paths.get("libtalloc.so"))
                    } catch (_: Exception) {
                        talloc1.copyTo(talloc2, overwrite = true)
                    }
                } else {
                    talloc1.copyTo(talloc2, overwrite = true)
                }
                makeExecutable(talloc2)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create libtalloc.so.2 link: ${e.message}")
        }
    }

    private fun copyAssetFile(assetPath: String, outFile: File) {
        if (outFile.exists()) outFile.delete()
        val stream = try {
            context.assets.open(assetPath)
        } catch (_: Exception) {
            val fallback = if (assetPath.startsWith("rootfs/")) assetPath.replace("rootfs/", "alpine/") else assetPath
            context.assets.open(fallback)
        }
        stream.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun makeExecutable(file: File) {
        file.setExecutable(true, false)
        file.setReadable(true, false)
        file.setWritable(true, true)
    }

    fun createTerminalSession(
        client: com.termux.terminal.TerminalSessionClient,
        initialPath: String? = null,
        preferredShell: String? = null
    ): com.termux.terminal.TerminalSession {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val filesPath = filesDir.absolutePath
        
        val prootBinInNative = File(nativeDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
            ?: File(nativeDir, "libproot.so").takeIf { it.exists() }?.absolutePath

        var prootBinInFiles = File(filesDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
            ?: File(filesDir, "libproot.so").takeIf { it.exists() }?.absolutePath

        if (prootBinInNative == null && prootBinInFiles == null) {
            copyNativeBinaries("arm64")
            prootBinInFiles = File(filesDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
                ?: File(filesDir, "libproot.so").takeIf { it.exists() }?.absolutePath
        }

        val prootBin = prootBinInNative ?: prootBinInFiles ?: File(filesDir, "libproot-xed.so").absolutePath
        val initSandboxScript = File(filesDir, "init-sandbox.sh").absolutePath

        val publicDir = File(filesDir, "public")
        if (!publicDir.exists()) publicDir.mkdirs()

        val workingDir = if (!initialPath.isNullOrBlank() && File(initialPath).exists()) {
            File(initialPath)
        } else {
            publicDir
        }

        val effectiveShell = preferredShell?.takeIf { it.isNotBlank() } ?: runCatching {
            val settingsFile = File(filesDir, "settings.json")
            if (settingsFile.exists()) {
                val json = org.json.JSONObject(settingsFile.readText())
                json.optString("terminal.integrated.shell", null)
            } else null
        }.getOrNull()

        val env = mutableListOf(
            "PREFIX=$filesPath",
            "NATIVE_DIR=$nativeDir",
            "HOME=/public",
            "TERM=xterm-256color",
            "PROOT=$prootBin",
            "LD_LIBRARY_PATH=$filesPath:$nativeDir",
            "INITIAL_CWD=${normalizePath(workingDir.absolutePath)}"
        )
        if (!effectiveShell.isNullOrBlank()) {
            env.add("PREFERRED_SHELL=$effectiveShell")
        }
        val envList = env.toTypedArray()

        val shellPath = "/system/bin/sh"
        val args = arrayOf("/system/bin/sh", initSandboxScript)

        return com.termux.terminal.TerminalSession(
            shellPath,
            workingDir.absolutePath,
            args,
            envList,
            10000,
            client
        )
    }

    // Backwards-compatibility alias
    fun createAlpineTerminalSession(
        client: com.termux.terminal.TerminalSessionClient,
        initialPath: String? = null,
        preferredShell: String? = null
    ) = createTerminalSession(client, initialPath, preferredShell)

    fun hasBinary(binaryName: String): Boolean {
        if (!isInstalled) return false
        val usrBin = File(rootfsDir, "usr/bin/$binaryName")
        val bin = File(rootfsDir, "bin/$binaryName")
        return usrBin.exists() || bin.exists()
    }

    // Backwards-compatibility alias
    fun hasAlpineBinary(binaryName: String): Boolean = hasBinary(binaryName)

    fun ensureMountPointsExist(sysMounts: List<String> = emptyList()) {
        val baseMounts = listOf("sdcard", "storage", "mnt", "data", "files", "tmp", "dev", "proc", "sys", "dev/shm", "tmp/shm")
        for (m in baseMounts) {
            val d = File(rootfsDir, m)
            if (!d.exists()) {
                try { d.mkdirs() } catch (_: Exception) {}
            }
        }
        for (m in sysMounts) {
            if (m.startsWith("/")) {
                val rel = m.removePrefix("/")
                val d = File(rootfsDir, rel)
                if (!d.exists()) {
                    try { d.mkdirs() } catch (_: Exception) {}
                }
            }
        }
    }

    fun createProcessBuilder(commandInRootfs: List<String>): ProcessBuilder {
        fixPermissionsAndLinks()
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val filesPath = filesDir.absolutePath

        val prootBinInNative = File(nativeDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
            ?: File(nativeDir, "libproot.so").takeIf { it.exists() }?.absolutePath

        var prootBinInFiles = File(filesDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
            ?: File(filesDir, "libproot.so").takeIf { it.exists() }?.absolutePath

        if (prootBinInNative == null && prootBinInFiles == null) {
            copyNativeBinaries(getArchName())
            prootBinInFiles = File(filesDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
                ?: File(filesDir, "libproot.so").takeIf { it.exists() }?.absolutePath
        }

        val prootBin = prootBinInNative ?: prootBinInFiles ?: File(filesDir, "libproot-xed.so").absolutePath
        val prootLoader = File(nativeDir, "libproot.so").takeIf { it.exists() }?.absolutePath
            ?: File(filesDir, "libproot.so").absolutePath
        val prootLoader32 = File(nativeDir, "libproot32.so").takeIf { it.exists() }?.absolutePath
            ?: File(filesDir, "libproot32.so").absolutePath

        val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"

        val prootExec = if (prootBin.startsWith("/data/app/")) {
            listOf(prootBin)
        } else {
            listOf(linker, prootBin)
        }

        val rawSysMounts = mutableListOf<String>()
        val sysMountArgs = mutableListOf<String>()
        val systemPaths = arrayOf(
            "/apex", "/odm", "/product", "/system", "/system_ext", "/vendor",
            "/proc", "/sys", "/dev", "/linkerconfig/ld.config.txt"
        )
        for (mnt in systemPaths) {
            if (File(mnt).exists()) {
                rawSysMounts.add(mnt)
                sysMountArgs.add("-b")
                sysMountArgs.add(mnt)
            }
        }

        ensureMountPointsExist(rawSysMounts)

        val cmd = mutableListOf<String>()
        cmd.addAll(prootExec)
        cmd.addAll(listOf(
            "--kill-on-exit",
            "-b", "/sdcard",
            "-b", "/storage",
            "-b", "/mnt",
            "-b", "/data",
            "-b", filesPath,
            "-b", "$filesPath:/files",
            "-b", nativeDir,
            "-b", "${rootfsDir.absolutePath}/tmp/shm:/dev/shm"
        ))
        cmd.addAll(sysMountArgs)
        cmd.addAll(listOf(
            "-r", rootfsDir.absolutePath,
            "-0",
            "--link2symlink",
            "--sysvipc",
            "-L"
        ))

        cmd.addAll(commandInRootfs)

        val pb = ProcessBuilder(cmd)
        val env = pb.environment()
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        env["PREFIX"] = filesPath
        env["NATIVE_DIR"] = nativeDir
        env["HOME"] = "/root"
        env["TERM"] = "xterm-256color"
        env["PROOT_TMP_DIR"] = "$filesPath/tmp"
        env["PROOT_LOADER"] = prootLoader
        env["PROOT_LOADER32"] = prootLoader32
        env["PROOT"] = prootBin
        env["LD_LIBRARY_PATH"] = "$filesPath:$nativeDir"

        return pb
    }

    // Backwards-compatibility alias
    fun createAlpineProcessBuilder(commandInAlpine: List<String>): ProcessBuilder = createProcessBuilder(commandInAlpine)

    fun normalizePath(path: String): String {
        return if (path.startsWith("/storage/emulated/0")) {
            path.replace("/storage/emulated/0", "/sdcard")
        } else {
            path
        }
    }

    // Backwards-compatibility alias
    fun normalizePathForAlpine(path: String): String = normalizePath(path)
}

// Global backwards-compatibility typealias
typealias AlpineManager = RootfsManager
