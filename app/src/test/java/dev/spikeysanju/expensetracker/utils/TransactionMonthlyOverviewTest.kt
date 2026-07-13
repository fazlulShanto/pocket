package dev.spikeysanju.expensetracker.utils

import dev.spikeysanju.expensetracker.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TransactionMonthlyOverviewTest {

    @Test
    fun `buildReportingOverview returns all-time top 5 categories and monthly breakdowns`() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)

        try {
            val overview = buildReportingOverview(
                listOf(
                    transaction("Salary", 3500.0, "Income", "Work", "05/04/2026"),
                    transaction("Campaign", 250.0, "Expense", "Marketing", "08/04/2026"),
                    transaction("Concert", 200.0, "Expense", "Entertainment", "12/04/2026"),
                    transaction("Clinic", 150.0, "Expense", "Healthcare", "15/04/2026"),
                    transaction("Snacks", 50.0, "Expense", "Food", "18/04/2026"),
                    transaction("Salary", 4000.0, "Income", "Work", "05/05/2026"),
                    transaction("Rent", 1200.0, "Expense", "Housing", "02/05/2026"),
                    transaction("Groceries", 300.0, "Expense", "Food", "03/05/2026"),
                    transaction("Bills", 180.0, "Expense", "Utilities", "04/05/2026"),
                    transaction("Taxi", 100.0, "Expense", "Travel", "06/05/2026")
                )
            )

            assertEquals(7500.0, overview.allTime.totalIncome, 0.0)
            assertEquals(2430.0, overview.allTime.totalExpense, 0.0)
            assertEquals(5070.0, overview.allTime.remaining, 0.0)
            assertEquals(
                listOf("Housing", "Food", "Marketing", "Entertainment", "Utilities"),
                overview.allTime.topExpenseCategories.map { it.tag }
            )

            assertEquals(listOf("May 2026", "April 2026"), overview.monthlyCards.map { it.monthLabel })

            val latestMonth = overview.monthlyCards.first()
            requireNotNull(latestMonth)
            assertEquals("May 2026", latestMonth.monthLabel)
            assertEquals(4000.0, latestMonth.summary.totalIncome, 0.0)
            assertEquals(1780.0, latestMonth.summary.totalExpense, 0.0)
            assertEquals(2220.0, latestMonth.summary.remaining, 0.0)
            assertEquals(
                listOf("Housing", "Food", "Utilities"),
                latestMonth.summary.topExpenseCategories.map { it.tag }
            )
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `buildReportingOverview keeps May and June when July has no transactions`() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)

        try {
            val overview = buildReportingOverview(
                listOf(
                    transaction("May groceries", 500.0, "Expense", "Food", "10/05/2026"),
                    transaction("June groceries", 970.0, "Expense", "Food", "12/06/2026")
                )
            )

            assertEquals(listOf("June 2026", "May 2026"), overview.monthlyCards.map { it.monthLabel })
            assertEquals(listOf(970.0, 500.0), overview.monthlyCards.map { it.summary.totalExpense })
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `buildReportingOverview returns empty monthly history when there are no transactions`() {
        val overview = buildReportingOverview(emptyList())

        assertEquals(0.0, overview.allTime.totalIncome, 0.0)
        assertEquals(0.0, overview.allTime.totalExpense, 0.0)
        assertEquals(0.0, overview.allTime.remaining, 0.0)
        assertEquals(emptyList<CategorySpending>(), overview.allTime.topExpenseCategories)
        assertEquals(emptyList<MonthlyReportingCard>(), overview.monthlyCards)
    }

    private fun transaction(
        title: String,
        amount: Double,
        type: String,
        tag: String,
        date: String
    ) = Transaction(
        title = title,
        amount = amount,
        transactionType = type,
        tag = tag,
        date = date,
        note = ""
    )
}
