package dev.spikeysanju.expensetracker.view.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import dev.spikeysanju.expensetracker.R
import dev.spikeysanju.expensetracker.databinding.FragmentDashboardBinding
import dev.spikeysanju.expensetracker.databinding.ItemDashboardCategoryBinding
import dev.spikeysanju.expensetracker.model.Tag
import dev.spikeysanju.expensetracker.model.Transaction
import dev.spikeysanju.expensetracker.utils.CategorySpending
import dev.spikeysanju.expensetracker.utils.ExpenseTrend
import dev.spikeysanju.expensetracker.utils.MonthlyOverview
import dev.spikeysanju.expensetracker.utils.SupportedCurrency
import dev.spikeysanju.expensetracker.utils.TagCatalog
import dev.spikeysanju.expensetracker.utils.buildMonthlyOverview
import dev.spikeysanju.expensetracker.utils.viewState.ViewState
import dev.spikeysanju.expensetracker.view.adapter.TransactionAdapter
import dev.spikeysanju.expensetracker.view.base.BaseFragment
import dev.spikeysanju.expensetracker.view.main.viewmodel.TagViewModel
import dev.spikeysanju.expensetracker.view.main.viewmodel.TransactionViewModel
import formatCurrencyAmount
import hide
import kotlinx.coroutines.flow.collect
import show
import snack
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@AndroidEntryPoint
class DashboardFragment :
    BaseFragment<FragmentDashboardBinding, TransactionViewModel>() {
    private lateinit var transactionAdapter: TransactionAdapter
    private var latestTransactions: List<Transaction> = emptyList()
    private var availableTags: List<Tag> = emptyList()
    private var selectedCurrency: SupportedCurrency = SupportedCurrency.DEFAULT
    private val tagViewModel: TagViewModel by activityViewModels()
    override val viewModel: TransactionViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRV()
        initViews()
        observeTransaction()
		observeTags()
		observeSelectedCurrency()
        viewModel.getAllTransaction(DEFAULT_FILTER)
    }

    private fun setupRV() = with(binding) {
        transactionAdapter = TransactionAdapter()
        transactionRv.apply {
            adapter = transactionAdapter
            layoutManager = LinearLayoutManager(activity)
        }
    }

    private fun observeTransaction() = lifecycleScope.launchWhenStarted {
        viewModel.uiState.collect { uiState ->
            when (uiState) {
                is ViewState.Loading -> {
                }
                is ViewState.Success -> {
                    latestTransactions = uiState.transaction
                    renderDashboard(uiState.transaction)
                }
                is ViewState.Error -> {
                    binding.root.snack(
                        string = R.string.text_error
                    )
                }
                is ViewState.Empty -> {
                    latestTransactions = emptyList()
                    renderDashboard(emptyList())
                }
            }
        }
    }

	private fun observeSelectedCurrency() = lifecycleScope.launchWhenStarted {
		viewModel.selectedCurrency.collect { currency ->
			selectedCurrency = currency
			transactionAdapter.updateCurrency(currency)
			renderDashboard(latestTransactions)
		}
	}

    private fun observeTags() = lifecycleScope.launchWhenStarted {
        tagViewModel.tags.collect { tags ->
            availableTags = tags
            transactionAdapter.updateTagMetadata(tags)
            renderDashboard(latestTransactions)
        }
    }

    private fun renderDashboard(transactions: List<Transaction>) {
        val overview = buildMonthlyOverview(transactions)
        renderOverview(overview)
        renderCategories(overview.categorySpending, overview.totalExpense)
        renderRecentTransactions(transactions)
    }

    private fun renderOverview(overview: MonthlyOverview) = with(binding) {
        dashboardMonth.text = overview.monthLabel
        overviewTotalExpenseValue.text = formatCurrencyAmount(overview.totalExpense, selectedCurrency)
        overviewIncomeValue.text = formatCurrencyAmount(overview.totalIncome, selectedCurrency)
        overviewExpenseValue.text = formatCurrencyAmount(overview.totalExpense, selectedCurrency)
        overviewRemainingValue.text = formatCurrencyAmount(overview.remaining, selectedCurrency)
        overviewRemainingValue.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (overview.remaining < 0) R.color.expense else R.color.blue_500
            )
        )
        overviewComparisonChip.text = formatPercentChange(overview)
        overviewComparisonLabel.text = getString(
            R.string.text_dashboard_vs_last_month,
            formatCurrencyAmount(overview.previousMonthExpense, selectedCurrency)
        )

        when (overview.expenseTrend) {
            ExpenseTrend.UP -> {
                overviewComparisonChip.setBackgroundResource(R.drawable.bg_dashboard_trend_negative)
                overviewComparisonChip.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.expense)
                )
            }

            ExpenseTrend.DOWN -> {
                overviewComparisonChip.setBackgroundResource(R.drawable.bg_dashboard_trend_positive)
                overviewComparisonChip.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.income)
                )
            }

            ExpenseTrend.FLAT -> {
                overviewComparisonChip.setBackgroundResource(R.drawable.bg_dashboard_trend_neutral)
                overviewComparisonChip.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.blue_500)
                )
            }
        }
    }

    private fun renderCategories(
        categorySpending: List<CategorySpending>,
        totalExpense: Double
    ) = with(binding) {
        categoryContainer.removeAllViews()
        if (categorySpending.isEmpty()) {
            categoryEmptyState.show()
            return
        }

        categoryEmptyState.hide()
        categorySpending.forEach { category ->
            categoryContainer.addView(createCategoryRow(category, totalExpense))
        }
    }

    private fun renderRecentTransactions(transactions: List<Transaction>) = with(binding) {
        if (transactions.isEmpty()) {
            titleRecentTransaction.hide()
            transactionRv.hide()
        } else {
            titleRecentTransaction.show()
            transactionRv.show()
            transactionAdapter.updateCurrency(selectedCurrency)
            transactionAdapter.differ.submitList(transactions)
        }
    }

    private fun createCategoryRow(category: CategorySpending, totalExpense: Double): View {
        val rowBinding = ItemDashboardCategoryBinding.inflate(
            LayoutInflater.from(requireContext()),
            binding.categoryContainer,
            false
        )
        val visualStyle = resolveCategoryVisualStyle(category.tag)
        val accentColor = ContextCompat.getColor(requireContext(), visualStyle.accentColorRes)
        val progress = if (totalExpense <= 0.0 || category.amount <= 0.0) {
            0
        } else {
            ((category.amount / totalExpense) * CATEGORY_PROGRESS_MAX)
                .roundToInt()
                .coerceIn(1, CATEGORY_PROGRESS_MAX)
        }

        return rowBinding.apply {
            categoryName.text = category.tag
            categoryAmount.text = formatCurrencyAmount(category.amount, selectedCurrency)
            categoryAmount.setTextColor(accentColor)
            categoryIconContainer.setCardBackgroundColor(
                ColorUtils.setAlphaComponent(accentColor, CATEGORY_ICON_BG_ALPHA)
            )
            categoryIconView.setImageResource(visualStyle.iconRes)
            categoryIconView.imageTintList = ColorStateList.valueOf(accentColor)
            categoryProgress.max = CATEGORY_PROGRESS_MAX
            categoryProgress.trackColor =
                ColorUtils.setAlphaComponent(accentColor, CATEGORY_TRACK_ALPHA)
            categoryProgress.setIndicatorColor(accentColor)
            categoryProgress.setProgressCompat(progress, false)
        }.root
    }

    private fun resolveCategoryVisualStyle(tag: String): CategoryVisualStyle {
        val iconRes = availableTags.firstOrNull { availableTag ->
            availableTag.tagName.equals(tag, ignoreCase = true)
        }?.let { availableTag ->
            TagCatalog.resolveIconRes(availableTag.iconName)
        } ?: TagCatalog.resolveIconRes(TagCatalog.defaultIconNameForTag(tag))

        val accentColorRes = when (tag.trim().lowercase(Locale.ROOT)) {
            "housing" -> R.color.blue_500
            "transportation", "transport", "travel" -> CategoryVisualStyle(
                R.drawable.ic_transport,
                R.color.blue_500
            ).accentColorRes

            "food", "groceries" -> R.color.income
            "utilities" -> R.color.blue_500
            "insurance" -> R.color.expense
            "healthcare", "medical" -> R.color.expense
            "saving & debts", "savings", "debts" -> R.color.income
            "personal spending", "shopping" -> R.color.expense
            "entertainment" -> R.color.blue_500
            else -> R.color.blue_500
        }

        return CategoryVisualStyle(iconRes, accentColorRes)
    }

    private fun formatPercentChange(overview: MonthlyOverview): String {
        val percentFormatter = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 1
        }
        val formattedPercent = percentFormatter.format(abs(overview.expenseDeltaPercent))
        return when (overview.expenseTrend) {
            ExpenseTrend.UP -> "+$formattedPercent%"
            ExpenseTrend.DOWN -> "-$formattedPercent%"
            ExpenseTrend.FLAT -> "$formattedPercent%"
        }
    }

    private fun initViews() = with(binding) {
        transactionAdapter.setOnItemClickListener {
            val bundle = Bundle().apply {
                putSerializable("transaction", it)
            }
            findNavController().navigate(
                R.id.action_dashboardFragment_to_transactionDetailsFragment,
                bundle
            )
        }
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = FragmentDashboardBinding.inflate(inflater, container, false)

    private companion object {
        const val DEFAULT_FILTER = "Overall"
        const val CATEGORY_PROGRESS_MAX = 100
        const val CATEGORY_ICON_BG_ALPHA = 28
        const val CATEGORY_TRACK_ALPHA = 52
    }

    private data class CategoryVisualStyle(
        val iconRes: Int,
        val accentColorRes: Int
    )
}
