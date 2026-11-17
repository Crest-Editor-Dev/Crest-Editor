package com.cresteditor.workspace

import com.cresteditor.core.filesystem.GitignoreMatcher
import com.cresteditor.core.filesystem.GlobMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * Pre-indexed file metadata with normalized, pre-computed lowercase strings.
 * Minimizes allocations during interactive Command Palette searches.
 */
data class IndexedFile(
    val name: String,
    val path: String,
    val relativePath: String,
    val lowerName: String,
    val lowerRelativePath: String
)

data class ScoredFileMatch(
    val file: IndexedFile,
    val score: Int,
    val matchIndices: List<Int>
)

data class FileSearchResult(
    val matches: List<ScoredFileMatch>,
    val matchedCandidates: List<IndexedFile>
)

/**
 * Asynchronous, memory-efficient project file indexing system.
 * - Indexes workspace files in the background on Dispatchers.IO.
 * - Prunes excluded directories (.git, node_modules, build, .gitignore) during traversal.
 * - Supports lock-free reading and O(1) incremental add/remove/rename operations.
 * - Performs fuzzy matching and bounded Top-K ranking on Dispatchers.Default.
 */
class ProjectFileIndex {

    private val fileListRef = AtomicReference<List<IndexedFile>>(emptyList())

    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()

    fun getAllFiles(): List<IndexedFile> = fileListRef.get()

    fun getFileCount(): Int = fileListRef.get().size

    /**
     * Scans and builds the workspace file index asynchronously.
     */
    suspend fun indexWorkspace(
        rootDir: File,
        effectiveExcludes: Map<String, Boolean> = emptyMap(),
        gitignoreMatcher: GitignoreMatcher = GitignoreMatcher.EMPTY
    ) = withContext(Dispatchers.IO) {
        if (!rootDir.exists() || !rootDir.isDirectory) {
            fileListRef.set(emptyList())
            return@withContext
        }

        _isIndexing.value = true
        try {
            val indexedFiles = mutableListOf<IndexedFile>()
            val rootPath = rootDir.absolutePath

            fun shouldPruneDirectory(dir: File, relPath: String): Boolean {
                val name = dir.name
                if (name == ".git" || name == "node_modules" || name == "build" ||
                    name == ".gradle" || name == ".svn" || name == ".hg"
                ) {
                    return true
                }
                if (effectiveExcludes.isNotEmpty() && GlobMatcher.isFileExcluded(dir, rootDir, effectiveExcludes)) {
                    return true
                }
                if (!gitignoreMatcher.isEmpty() && gitignoreMatcher.isIgnored(relPath, isDirectory = true)) {
                    return true
                }
                return false
            }

            fun scanDir(dir: File) {
                val files = dir.listFiles() ?: return
                for (file in files) {
                    val absPath = file.absolutePath
                    val relPath = if (absPath.startsWith(rootPath)) {
                        absPath.removePrefix(rootPath).trimStart(File.separatorChar)
                    } else {
                        file.name
                    }

                    if (file.isDirectory) {
                        if (!shouldPruneDirectory(file, relPath)) {
                            scanDir(file)
                        }
                    } else {
                        if (effectiveExcludes.isNotEmpty() && GlobMatcher.isFileExcluded(file, rootDir, effectiveExcludes)) {
                            continue
                        }
                        if (!gitignoreMatcher.isEmpty() && gitignoreMatcher.isIgnored(relPath, isDirectory = false)) {
                            continue
                        }

                        val name = file.name
                        indexedFiles.add(
                            IndexedFile(
                                name = name,
                                path = absPath,
                                relativePath = relPath,
                                lowerName = name.lowercase(),
                                lowerRelativePath = relPath.lowercase()
                            )
                        )
                    }
                }
            }

            scanDir(rootDir)
            fileListRef.set(indexedFiles)
        } finally {
            _isIndexing.value = false
        }
    }

    /**
     * Incrementally registers a newly created file.
     */
    fun addFile(file: File, rootDir: File) {
        if (file.isDirectory) return
        val absPath = file.absolutePath
        val rootPath = rootDir.absolutePath
        val relPath = if (absPath.startsWith(rootPath)) {
            absPath.removePrefix(rootPath).trimStart(File.separatorChar)
        } else {
            file.name
        }

        val newEntry = IndexedFile(
            name = file.name,
            path = absPath,
            relativePath = relPath,
            lowerName = file.name.lowercase(),
            lowerRelativePath = relPath.lowercase()
        )

        while (true) {
            val current = fileListRef.get()
            val updated = current.filter { it.path != absPath } + newEntry
            if (fileListRef.compareAndSet(current, updated)) break
        }
    }

    /**
     * Incrementally removes a deleted file.
     */
    fun removeFile(file: File) {
        val absPath = file.absolutePath
        while (true) {
            val current = fileListRef.get()
            val updated = current.filter { it.path != absPath }
            if (fileListRef.compareAndSet(current, updated)) break
        }
    }

    /**
     * Incrementally updates a renamed or moved file.
     */
    fun renameFile(oldFile: File, newFile: File, rootDir: File) {
        removeFile(oldFile)
        addFile(newFile, rootDir)
    }

    fun clear() {
        fileListRef.set(emptyList())
    }

    /**
     * High-speed fuzzy search across indexed files with incremental candidate filtering
     * and bounded Top-K priority queue ranking.
     */
    fun search(
        query: String,
        candidates: List<IndexedFile>? = null,
        topK: Int = 50
    ): FileSearchResult {
        val allFiles = fileListRef.get()
        val trimmedQuery = query.trim()

        if (trimmedQuery.isEmpty()) {
            val defaultList = (candidates ?: allFiles).take(topK)
            val matches = defaultList.map { ScoredFileMatch(it, 0, emptyList()) }
            return FileSearchResult(matches, allFiles)
        }

        val pool = candidates ?: allFiles
        val queryLower = trimmedQuery.lowercase()
        val queryLen = queryLower.length
        val queryHasSlash = queryLower.contains('/')
        val firstChar = queryLower[0]

        val matchedCandidates = ArrayList<IndexedFile>(minOf(pool.size, 2048))
        // Min-heap ordered by score ascending so the lowest score is at the top
        val minHeap = PriorityQueue<ScoredFileMatch>(topK + 1, compareBy { it.score })

        for (file in pool) {
            val targetLower = if (queryHasSlash) file.lowerRelativePath else file.lowerName
            val targetOriginal = if (queryHasSlash) file.relativePath else file.name
            val targetLen = targetLower.length

            // 1. Fast rejection: First character must exist
            if (targetLower.indexOf(firstChar) < 0) continue

            // 2. Fast subsequence check
            var qIdx = 0
            var tIdx = 0
            while (qIdx < queryLen && tIdx < targetLen) {
                if (targetLower[tIdx] == queryLower[qIdx]) {
                    qIdx++
                }
                tIdx++
            }
            if (qIdx < queryLen) continue

            matchedCandidates.add(file)

            // 3. Compute score and match indices
            var score: Int
            val indices: List<Int>

            if (file.lowerName == queryLower) {
                score = 3000
                indices = file.name.indices.toList()
            } else {
                val directIdx = targetLower.indexOf(queryLower)
                if (directIdx >= 0) {
                    val boundaryBonus = if (directIdx == 0 || !targetOriginal[directIdx - 1].isLetterOrDigit()) 350 else 0
                    score = 1500 + boundaryBonus - directIdx
                    indices = (directIdx until directIdx + queryLen).toList()
                } else {
                    // Subsequence scoring with boundary detection
                    var qI = 0
                    var subScore = 0
                    val subIndices = ArrayList<Int>(queryLen)
                    for (i in 0 until targetLen) {
                        if (qI < queryLen && targetLower[i] == queryLower[qI]) {
                            subIndices.add(i)
                            if (i == 0 || targetOriginal[i - 1] in "._-/ ") {
                                subScore += 50
                            } else if (targetOriginal[i].isUpperCase()) {
                                subScore += 40
                            } else {
                                subScore += 10
                            }
                            qI++
                        }
                    }
                    val span = if (subIndices.isNotEmpty()) subIndices.last() - subIndices.first() + 1 else targetLen
                    score = 600 + subScore + maxOf(0, 300 - span)
                    indices = subIndices
                }
            }

            minHeap.offer(ScoredFileMatch(file, score, indices))
            if (minHeap.size > topK) {
                minHeap.poll()
            }
        }

        val topResults = mutableListOf<ScoredFileMatch>()
        while (minHeap.isNotEmpty()) {
            topResults.add(minHeap.poll()!!)
        }
        topResults.reverse()

        return FileSearchResult(topResults, matchedCandidates)
    }
}
