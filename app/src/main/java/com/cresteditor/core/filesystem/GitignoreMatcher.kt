package com.cresteditor.core.filesystem

import java.io.File

/**
 * Lightweight, high-performance .gitignore parser and matcher.
 * Conforms to Git ignore specifications:
 * - Blank lines and '#' comment lines are ignored.
 * - '!' prefix negates the pattern.
 * - Trailing '/' matches directories only.
 * - Leading '/' anchors the match to the workspace root.
 * - Patterns without slashes match anywhere in the directory hierarchy.
 * - Wildcards '*' and '**' handled efficiently without heavy external libraries.
 */
class GitignoreMatcher(private val rules: List<Rule> = emptyList()) {

    data class Rule(
        val regex: Regex,
        val isNegated: Boolean,
        val directoryOnly: Boolean
    )

    /**
     * Checks if a relative path is ignored based on the parsed rules.
     * Evaluates in sequential order so that later rules (and '!negations') take precedence.
     */
    fun isIgnored(relativePath: String, isDirectory: Boolean = false): Boolean {
        if (rules.isEmpty()) return false
        val normalized = relativePath.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return false

        val hasSlash = normalized.contains('/')
        var ignored = false
        for (rule in rules) {
            // If the rule is directory-only, it only matches if the target is a directory
            // or if the path is a child inside a matching directory segment.
            if (rule.directoryOnly && !isDirectory && !hasSlash) {
                continue
            }
            if (rule.regex.containsMatchIn(normalized)) {
                ignored = !rule.isNegated
            }
        }
        return ignored
    }

    fun isEmpty(): Boolean = rules.isEmpty()

    companion object {
        val EMPTY = GitignoreMatcher(emptyList())

        fun fromDirectory(dir: File): GitignoreMatcher {
            val gitignore = File(dir, ".gitignore")
            return if (gitignore.exists() && gitignore.isFile) {
                fromFile(gitignore)
            } else {
                EMPTY
            }
        }

        fun fromFile(file: File): GitignoreMatcher {
            return try {
                parse(file.readText())
            } catch (e: Exception) {
                EMPTY
            }
        }

        fun parse(content: String): GitignoreMatcher {
            val rules = mutableListOf<Rule>()
            val lines = content.lines()

            for (rawLine in lines) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty() || trimmed.startsWith('#')) {
                    continue
                }

                var line = trimmed
                var isNegated = false
                if (line.startsWith('!')) {
                    isNegated = true
                    line = line.substring(1).trim()
                }

                if (line.isEmpty()) continue

                var directoryOnly = false
                if (line.endsWith('/')) {
                    directoryOnly = true
                    line = line.trimEnd('/')
                }

                if (line.isEmpty()) continue

                val regex = parsePatternToRegex(line)
                rules.add(Rule(regex, isNegated, directoryOnly))
            }

            return GitignoreMatcher(rules)
        }

        private fun parsePatternToRegex(pattern: String): Regex {
            val sb = StringBuilder()
            val normalized = pattern.replace('\\', '/')
            val n = normalized.length
            var i = 0

            val startsWithSlash = normalized.startsWith('/')
            val hasInternalSlash = normalized.indexOf('/') in 0 until (n - 1)

            if (startsWithSlash) {
                sb.append("^")
                i = 1
            } else if (!hasInternalSlash) {
                // No slash: matches anywhere in hierarchy
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

            sb.append("(?:/.*)?$")
            return Regex(sb.toString(), RegexOption.IGNORE_CASE)
        }
    }
}
