package dev.spikeysanju.expensetracker.utils

import dev.spikeysanju.expensetracker.model.Transaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class CategorySpending(
    val tag: String,
    val amount: Double
)

enum class ExpenseTrend {
    UP,
    DOWN,
    FLAT
}

data class MonthlyOverview(
    val monthLabel: String,
    val totalIncome: Double,
    val totalExpense: Double,
    val remaining: Double,
    val previousMonthExpense: Double,
    val expenseDeltaAmount: Double,
    val expenseDeltaPercent: Double,
    val expenseTrend: ExpenseTrend,
    val categorySpending: List<CategorySpending>
)

data class ReportingPeriodSummary(
    val totalIncome: Double,
    val totalExpense: Double,
    val remaining: Double,
    val topExpenseCategories: List<CategorySpending>
)

data class MonthlyReportingCard(
    val monthLabel: String,
    val summary: ReportingPeriodSummary
)

data class ReportingOverview(
    val allTime: ReportingPeriodSummary,
    val latestMonth: MonthlyReportingCard?
)

private data class MonthKey(
    val year: Int,
    val month: Int
)

private data class DatedTransaction(
    val transaction: Transaction,
    val monthKey: MonthKey
)

fun buildMonthlyOverview(
    transactions: List<Transaction>,
    referenceCalendar: Calendar = Calendar.getInstance()
): MonthlyOverview {
    val monthFormatter = SimpleDateFormat(MONTH_LABEL_PATTERN, Locale.getDefault())
    val datedTransactions = parseTransactions(transactions)

    val currentMonth = MonthKey(
        year = referenceCalendar.get(Calendar.YEAR),
        month = referenceCalendar.get(Calendar.MONTH)
    )
    val previousMonthCalendar = (referenceCalendar.clone() as Calendar).apply {
        add(Calendar.MONTH, -1)
    }
    val previousMonth = MonthKey(
        year = previousMonthCalendar.get(Calendar.YEAR),
        month = previousMonthCalendar.get(Calendar.MONTH)
    )

    val currentTransactions = datedTransactions
        .filter { it.monthKey == currentMonth }
        .map { it.transaction }
    val previousTransactions = datedTransactions
        .filter { it.monthKey == previousMonth }
        .map { it.transaction }

    val totalIncome = currentTransactions
        .filter { it.transactionType == INCOME_TYPE }
        .sumOf { it.amount }
    val totalExpense = currentTransactions
        .filter { it.transactionType == EXPENSE_TYPE }
        .sumOf { it.amount }
    val previousMonthExpense = previousTransactions
        .filter { it.transactionType == EXPENSE_TYPE }
        .sumOf { it.amount }
    val expenseDeltaAmount = totalExpense - previousMonthExpense
    val expenseDeltaPercent = when {
        previousMonthExpense == 0.0 && totalExpense == 0.0 -> 0.0
        previousMonthExpense == 0.0 -> 100.0
        else -> (expenseDeltaAmount / previousMonthExpense) * 100.0
    }
    val expenseTrend = when {
        expenseDeltaAmount > 0 -> ExpenseTrend.UP
        expenseDeltaAmount < 0 -> ExpenseTrend.DOWN
        else -> ExpenseTrend.FLAT
    }
    val categorySpending = buildCategorySpending(currentTransactions)

    return MonthlyOverview(
        monthLabel = monthFormatter.format(referenceCalendar.time),
        totalIncome = totalIncome,
        totalExpense = totalExpense,
        remaining = totalIncome - totalExpense,
        previousMonthExpense = previousMonthExpense,
        expenseDeltaAmount = expenseDeltaAmount,
        expenseDeltaPercent = expenseDeltaPercent,
        expenseTrend = expenseTrend,
        categorySpending = categorySpending
    )
}

fun buildReportingOverview(transactions: List<Transaction>): ReportingOverview {
    val datedTransactions = parseTransactions(transactions)
    val monthlyCards = datedTransactions
        .groupBy { it.monthKey }
        .toList()
        .sortedWith(
            compareByDescending<Pair<MonthKey, List<DatedTransaction>>> { it.first.year }
                .thenByDescending { it.first.month }
        )
        .map { (monthKey, items) ->
            MonthlyReportingCard(
                monthLabel = formatMonthLabel(monthKey),
                summary = buildReportingPeriodSummary(items.map { it.transaction })
            )
        }

    return ReportingOverview(
        allTime = buildReportingPeriodSummary(
            transactions = datedTransactions.map { it.transaction },
            categoryLimit = TOP_ALL_TIME_EXPENSE_CATEGORY_LIMIT
        ),
        latestMonth = monthlyCards.firstOrNull()
    )
}

private fun parseTransactions(transactions: List<Transaction>): List<DatedTransaction> {
    val inputDateFormatter = SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).apply {
        isLenient = false
    }

    return transactions.mapNotNull { transaction ->
        val parsedDate = runCatching {
            inputDateFormatter.parse(transaction.date)
        }.getOrNull() ?: return@mapNotNull null

        val transactionCalendar = Calendar.getInstance().apply {
            time = parsedDate
        }
        DatedTransaction(
            transaction = transaction,
            monthKey = MonthKey(
                year = transactionCalendar.get(Calendar.YEAR),
                month = transactionCalendar.get(Calendar.MONTH)
            )
        )
    }
}

private fun buildReportingPeriodSummary(
    transactions: List<Transaction>,
    categoryLimit: Int = TOP_MONTHLY_EXPENSE_CATEGORY_LIMIT
): ReportingPeriodSummary {
    val totalIncome = transactions
        .filter { it.transactionType == INCOME_TYPE }
        .sumOf { it.amount }
    val totalExpense = transactions
        .filter { it.transactionType == EXPENSE_TYPE }
        .sumOf { it.amount }

    return ReportingPeriodSummary(
        totalIncome = totalIncome,
        totalExpense = totalExpense,
        remaining = totalIncome - totalExpense,
        topExpenseCategories = buildCategorySpending(transactions).take(categoryLimit)
    )
}

private fun buildCategorySpending(transactions: List<Transaction>): List<CategorySpending> {
    return transactions
        .filter { it.transactionType == EXPENSE_TYPE }
        .groupBy { it.tag }
        .mapValues { (_, items) -> items.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
        .map { (tag, amount) -> CategorySpending(tag = tag, amount = amount) }
}

private fun formatMonthLabel(monthKey: MonthKey): String {
    val monthFormatter = SimpleDateFormat(MONTH_LABEL_PATTERN, Locale.getDefault())
    val calendar = Calendar.getInstance().apply {
        clear()
        set(monthKey.year, monthKey.month, 1)
    }
    return monthFormatter.format(calendar.time)
}

private const val DATE_PATTERN = "dd/MM/yyyy"
private const val MONTH_LABEL_PATTERN = "MMMM yyyy"
private const val INCOME_TYPE = "Income"
private const val EXPENSE_TYPE = "Expense"
private const val TOP_MONTHLY_EXPENSE_CATEGORY_LIMIT = 3
private const val TOP_ALL_TIME_EXPENSE_CATEGORY_LIMIT = 5