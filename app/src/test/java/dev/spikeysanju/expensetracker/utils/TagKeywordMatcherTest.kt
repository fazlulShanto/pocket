package dev.spikeysanju.expensetracker.utils

import dev.spikeysanju.expensetracker.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagKeywordMatcherTest {
    private val tags = listOf(
        Tag(
            tagName = "Food",
            tagType = "Expense",
            iconName = "food",
            keyword = "chicken, lunch, groceries"
        ),
        Tag(
            tagName = "Work",
            tagType = "Income",
            iconName = "income",
            keyword = "salary, invoice"
        ),
        Tag(
            tagName = "Personal",
            tagType = "Expense",
            iconName = "personal",
            keyword = ""
        )
    )

    @Test
    fun `matches title keyword and returns the configured tag`() {
        val match = TagKeywordMatcher.findBestMatch(
            tags = tags,
            title = "Chicken biryani"
        )

        assertEquals("Food", match?.tagName)
        assertEquals("Expense", match?.tagType)
    }

    @Test
    fun `supports keyword matches inside longer titles`() {
        val match = TagKeywordMatcher.findBestMatch(
            tags = tags,
            title = "Monthly salary for April"
        )

        assertEquals("Work", match?.tagName)
        assertEquals("Income", match?.tagType)
    }

    @Test
    fun `returns null when no keyword matches`() {
        val match = TagKeywordMatcher.findBestMatch(
            tags = tags,
            title = "Weekend trip"
        )

        assertNull(match)
    }
}