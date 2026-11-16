package com.cresteditor.core.filesystem

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance Glob Matcher modeled after VS Code's glob.ts engine.
 * Supports:
 * - wildcards like **/.git and **/node_modules
 * - extension patterns like *.log
 * - prefix and nested path globs like build or src
 * Uses a thread-safe ConcurrentHashMap cache for compiled regular expressions.
 */
object GlobMatcher {

    private val regexCache = ConcurrentHashMap<String, Regex>()

    /**
     * Converts a standard VS Code glob pattern to a compiled Kotlin Regex.
     */
    fun globToRegex(glob: String): Regex {
        return regexCache.getOrPut(glob) {
            val sb = StringBuilder()
            var i = 0
            val normalized = glob.replace('\\', '/')
            val n = normalized.length

            if (normalized.startsWith("**/")) {
                sb.append("(?:^|.*/)")
                i = 3
            } else if (normalized.startsWith("/")) {
                sb.append("^")
                i = 1
            } else if (!normalized.contains("/")) {
                // If no slash, matches anywhere in the path hierarchy (e.g. *.log or .git)
                sb.append("(?:^|.*/)")
            } else {
                sb.append("^")
            }

            while (i < n) {
                val c = normalized[i]
                when (c) {
                    '*' -> {
                        if (i + 1 < n && normalized[i + 1] == '*') {
                            if (i + 2 < n && normalized[i + 2] == '/') {
                                sb.append("(?:.*/)?")
                                i += 3
                                continue
                            } else {
                                sb.append(".*")
                                i += 2
                                continue
                            }
                        } else {
                            sb.append("[^/]*")
                        }
                    }
                    '?' -> sb.append("[^/]")
                    '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']' -> {
                        sb.append('\\').append(c)
                    }
                    '/' -> sb.append('/')
                    else -> sb.append(c)
                }
                i++
            }
            // Allow matching directory contents as well if matching directory node
            sb.append("(?:/.*)?$")
            Regex(sb.toString(), RegexOption.IGNORE_CASE)
        }
    }

    /**
     * Tests if a given path matches a specific glob pattern.
     */
    fun matches(pattern: String, path: String): Boolean {
        val normalized = path.replace('\\', '/').trimEnd('/')
        return globToRegex(pattern).containsMatchIn(normalized)
    }

    /**
     * Tests whether a path is excluded by any active pattern in the provided pattern map.
     */
    fun isExcluded(path: String, excludePatterns: Map<String, Boolean>): Boolean {
        if (excludePatterns.isEmpty()) return false
        val normalized = path.replace('\\', '/').trimEnd('/')
        for ((pattern, isEnabled) in excludePatterns) {
            if (isEnabled && matches(pattern, normalized)) {
                return true
            }
        }
        return false
    }

    /**
     * Helper to test a File object against the exclude patterns, relativizing against rootDir if possible.
     */
    fun isFileExcluded(file: File, rootDir: File? = null, excludePatterns: Map<String, Boolean>): Boolean {
        if (excludePatterns.isEmpty()) return false
        val relPath = if (rootDir != null && file.absolutePath.startsWith(rootDir.absolutePath)) {
            file.relativeTo(rootDir).path
        } else {
            file.name
        }
        return isExcluded(relPath, excludePatterns) || isExcluded(file.name, excludePatterns)
    }
}
