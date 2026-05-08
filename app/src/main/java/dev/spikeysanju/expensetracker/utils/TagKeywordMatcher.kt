package dev.spikeysanju.expensetracker.utils

import dev.spikeysanju.expensetracker.model.Tag
import java.util.Locale

object TagKeywordMatcher {
    fun findBestMatch(tags: List<Tag>, title: String): Tag? {
        val normalizedTitle = normalize(title)
        if (normalizedTitle.isBlank()) {
            return null
        }

        return tags
            .mapNotNull { tag ->
                val matchingKeyword = extractKeywords(tag.keyword)
                    .filter { keyword -> containsKeyword(normalizedTitle, keyword) }
                    .maxByOrNull { keyword -> keyword.length }

                if (matchingKeyword == null) {
                    null
                } else {
                    tag to matchingKeyword.length
                }
            }
            .maxByOrNull { (_, keywordLength) -> keywordLength }
            ?.first
    }

    private fun extractKeywords(rawKeywords: String): List<String> {
        return rawKeywords
            .split(',', ';', '\n')
            .map(::normalize)
            .filter { keyword -> keyword.isNotBlank() }
            .distinct()
    }

    private fun containsKeyword(normalizedTitle: String, normalizedKeyword: String): Boolean {
        return " $normalizedTitle ".contains(" $normalizedKeyword ")
    }

    private fun normalize(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}