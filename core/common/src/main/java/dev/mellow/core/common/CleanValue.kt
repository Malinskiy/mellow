package dev.mellow.core.common

import java.text.Normalizer

fun getCleanValue(value: String): String {
    if (value.isBlank()) return value
    val noDiacritics = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
    return noDiacritics.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
