package com.cresteditor.core.commands

/**
 * Standard VS Code Quick Access Modes identified by prefix.
 */
enum class QuickAccessMode(val prefix: String, val placeholder: String) {
    FILES("", "Search files by name (append : to go to line)"),
    COMMANDS(">", "Type the name of a command to run"),
    GOTO_LINE(":", "Type a line number to navigate to"),
    HELP("?", "Type '?' to get help on the actions you can take from here"),
    VIEW("view ", "Type the name of a view to open");

    companion object {
        fun resolve(query: String): Pair<QuickAccessMode, String> {
            val trimmedLeading = query.trimStart()
            return when {
                trimmedLeading.startsWith("view ") -> QuickAccessMode.VIEW to trimmedLeading.removePrefix("view ")
                trimmedLeading.startsWith(">") -> QuickAccessMode.COMMANDS to trimmedLeading.removePrefix(">")
                trimmedLeading.startsWith(":") -> QuickAccessMode.GOTO_LINE to trimmedLeading.removePrefix(":")
                trimmedLeading.startsWith("?") -> QuickAccessMode.HELP to trimmedLeading.removePrefix("?")
                else -> QuickAccessMode.FILES to query
            }
        }
    }
}

enum class QuickPickIcon {
    FILE,
    COMMAND,
    GOTO_LINE,
    VIEW,
    HELP
}

sealed interface QuickPickEntry {
    data class Header(val title: String) : QuickPickEntry

    data class Item(
        val id: String,
        val label: String,
        val description: String? = null,
        val detail: String? = null,
        val shortcut: String? = null,
        val icon: QuickPickIcon = QuickPickIcon.COMMAND,
        val labelMatchIndices: List<Int> = emptyList(),
        val isModified: Boolean = false,
        val onExecute: () -> Unit
    ) : QuickPickEntry
}

/**
 * High-performance Fuzzy Scorer inspired by VS Code fuzzyScorer.ts.
 * Computes exact, boundary, and subsequence matches with index tracking.
 */
object QuickAccessFuzzyScorer {

    data class ScoreResult(
        val score: Int,
        val matchIndices: List<Int>
    )

    fun score(target: String, query: String): ScoreResult? {
        if (query.isEmpty()) return ScoreResult(0, emptyList())
        val targetLower = target.lowercase()
        val queryLower = query.lowercase()

        // 1. Exact match bonus
        if (targetLower == queryLower) {
            return ScoreResult(2000, target.indices.toList())
        }

        // 2. Direct substring match bonus
        val directIndex = targetLower.indexOf(queryLower)
        if (directIndex >= 0) {
            val indices = (directIndex until directIndex + queryLower.length).toList()
            val boundaryBonus = if (directIndex == 0 || !target[directIndex - 1].isLetterOrDigit()) 300 else 0
            val score = 1200 + boundaryBonus - directIndex
            return ScoreResult(score, indices)
        }

        // 3. Subsequence fuzzy match with word boundary bonus
        var qIdx = 0
        var score = 0
        val indices = ArrayList<Int>(queryLower.length)

        for (tIdx in targetLower.indices) {
            if (qIdx < queryLower.length && targetLower[tIdx] == queryLower[qIdx]) {
                indices.add(tIdx)
                // Bonus for match at start of word or after separator
                if (tIdx == 0 || target[tIdx - 1] in "._-/ ") {
                    score += 50
                } else if (target[tIdx].isUpperCase()) {
                    score += 40
                } else {
                    score += 10
                }
                qIdx++
            }
        }

        if (qIdx == queryLower.length) {
            // Closeness of matches bonus
            val span = indices.last() - indices.first() + 1
            score += (500 - span).coerceAtLeast(0)
            return ScoreResult(score, indices)
        }

        return null
    }
}
