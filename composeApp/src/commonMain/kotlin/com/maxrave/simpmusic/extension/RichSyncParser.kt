package com.maxrave.simpmusic.extension

import com.maxrave.domain.extension.decodeHtmlEntities

/**
 * Represents a single word with its timing information for rich sync lyrics
 */
data class WordTiming(
    val text: String,
    val startTimeMs: Long,
)

/**
 * Represents a parsed rich sync line with all word timings
 */
data class ParsedRichSyncLine(
    val words: List<WordTiming>,
    val lineStartTimeMs: Long,
    val lineEndTimeMs: Long,
)

/**
 * Parses rich sync lyrics format into word-level timing data
 *
 * Expected format: <MM:SS.mm> word <MM:SS.mm> word ...
 * Example: <00:16.62> Và <00:16.64> em <00:16.68> nói
 *
 * @param words The rich sync string containing timestamps and words
 * @param lineStartTimeMs The start time of the line as a string
 * @param lineEndTimeMs The end time of the line as a string
 * @return ParsedRichSyncLine if parsing succeeds, null if parsing fails or input is invalid
 */
fun parseRichSyncWords(
    words: String,
    lineStartTimeMs: String,
    lineEndTimeMs: String,
): ParsedRichSyncLine? {
    // Handle edge cases
    if (words.isBlank()) {
        println("[parseRichSyncWords] Input is blank")
        return null
    }

    println("[parseRichSyncWords] Input preview: ${words.take(100)}")

    // Strategy: Find all timestamps first, then extract text between them
    // Regex to match timestamp only: <MM:SS.mm> or <MM:SS.mmm>
    val timestampRegex = Regex("""<(\d{2}):(\d{2})\.(\d{2,3})>""")

    val wordTimings = mutableListOf<WordTiming>()

    // Find all timestamp matches with their positions
    val timestamps = timestampRegex.findAll(words).toList()

    timestamps.forEachIndexed { index, match ->
        val (minutes, seconds, fraction) = match.destructured

        // Convert time to milliseconds
        // If 2 digits (centiseconds): multiply by 10 to get ms (e.g., 62 -> 620ms)
        // If 3 digits (milliseconds): use as-is (e.g., 620 -> 620ms)
        val fractionMs = fraction.toLongOrNull() ?: 0L
        val timeMs =
            (minutes.toLongOrNull() ?: 0L) * 60000L +
                (seconds.toLongOrNull() ?: 0L) * 1000L +
                if (fraction.length == 2) fractionMs * 10L else fractionMs

        // Extract text after this timestamp until the next timestamp (or end of string)
        val startPos = match.range.last + 1
        val endPos =
            if (index < timestamps.size - 1) {
                timestamps[index + 1].range.first
            } else {
                words.length
            }

        // Get the text between timestamps and trim it
        val textBetween = words.substring(startPos, endPos).trim()

        // Only add if there's actual text (not just whitespace or empty)
        if (textBetween.isNotBlank()) {
            wordTimings.add(WordTiming(text = decodeHtmlEntities(textBetween), startTimeMs = timeMs))
        }
    }

    // If no valid words were parsed, return null (fallback to LINE_SYNCED)
    if (wordTimings.isEmpty()) {
        println("[parseRichSyncWords] No words matched the regex")
        return null
    }

    val finalWordTimings = mergeSyllableTimings(wordTimings)

    println("[parseRichSyncWords] Successfully parsed ${finalWordTimings.size} words")

    // Parse line timing
    val lineStart = lineStartTimeMs.toLongOrNull() ?: 0L
    val lineEnd = lineEndTimeMs.toLongOrNull() ?: Long.MAX_VALUE

    return ParsedRichSyncLine(
        words = finalWordTimings,
        lineStartTimeMs = lineStart,
        lineEndTimeMs = lineEnd,
    )
}

private fun mergeSyllableTimings(timings: List<WordTiming>): List<WordTiming> {
    if (timings.isEmpty()) return timings
    val merged = mutableListOf<WordTiming>()
    var i = 0
    while (i < timings.size) {
        var current = timings[i]
        while (i + 1 < timings.size) {
            val next = timings[i + 1]
            if (shouldMergeAdjacentSyllables(current.text, next.text)) {
                current =
                    WordTiming(
                        text = current.text + next.text,
                        startTimeMs = current.startTimeMs,
                    )
                i++
            } else {
                break
            }
        }
        merged.add(current)
        i++
    }
    return merged
}

private fun shouldMergeAdjacentSyllables(curr: String, next: String): Boolean {
    val cLow = cleanWord(curr)
    val nLow = cleanWord(next)
    if (cLow.isEmpty() || nLow.isEmpty()) return false

    // 1. Hyphen
    if (curr.endsWith("-")) return true

    // 2. Single Latin letter that cannot be an English word
    if (nLow.length == 1 && nLow[0] in 'a'..'z' && nLow !in setOf("a", "i", "o")) return true
    if (cLow.length == 1 && cLow[0] in 'a'..'z' && cLow !in setOf("a", "i", "o")) return true

    // 3. Known syllable prefixes that are not standalone words
    if (cLow in setOf("brea", "camou", "wi", "lo", "fa", "pie", "pixe", "beau")) return true

    // 4. Known syllable suffixes that are not standalone words
    if (nLow in setOf("rea", "dy", "ce", "sh", "ve", "king", "flaged", "lete", "peat", "ture", "ted", "ces")) return true

    // 5. 'al' + 'ready' / 'rea' / 'dy'
    if (cLow == "a" && (nLow == "l" || nLow == "lready")) return true
    if (cLow == "al" && (nLow == "rea" || nLow == "ready")) return true
    if (cLow == "alrea" && nLow == "dy") return true

    // 6. 'de' + 'lete', 're' + 'peat'
    if ((cLow == "de" || cLow == "re") && (nLow == "lete" || nLow == "peat")) return true

    return false
}

private fun cleanWord(word: String): String =
    word.filter { it.isLetterOrDigit() || it == '\'' }.lowercase()
