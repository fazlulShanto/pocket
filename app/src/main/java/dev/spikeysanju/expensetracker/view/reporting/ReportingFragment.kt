package dev.spikeysanju.expensetracker.view.reporting

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import dagger.hilt.android.AndroidEntryPoint
import dev.spikeysanju.expensetracker.R
import dev.spikeysanju.expensetracker.databinding.FragmentReportingBinding
import dev.spikeysanju.expensetracker.databinding.ItemReportingCategoryLegendBinding
import dev.spikeysanju.expensetracker.databinding.ItemReportingMonthlyCategoryBinding
import dev.spikeysanju.expensetracker.model.Transaction
import dev.spikeysanju.expensetracker.utils.CategorySpending
import dev.spikeysanju.expensetracker.utils.MonthlyReportingCard
import dev.spikeysanju.expensetracker.utils.ReportingPeriodSummary
import dev.spikeysanju.expensetracker.utils.SupportedCurrency
import dev.spikeysanju.expensetracker.utils.buildReportingOverview
import dev.spikeysanju.expensetracker.utils.viewState.ViewState
import dev.spikeysanju.expensetracker.view.base.BaseFragment
import dev.spikeysanju.expensetracker.view.main.viewmodel.TransactionViewModel
import formatCurrencyAmount
import hide
import kotlinx.coroutines.flow.collect
import show
import snack
import kotlin.math.abs
import kotlin.math.roundToInt

@AndroidEntryPoint
class ReportingFragment : BaseFragment<FragmentReportingBinding, TransactionViewModel>() {
	override val viewModel: TransactionViewModel by activityViewModels()
	private var latestTransactions: List<Transaction> = emptyList()
	private var selectedCurrency: SupportedCurrency = SupportedCurrency.DEFAULT
	private val categoryPalette by lazy {
		listOf(
			ContextCompat.getColor(requireContext(), R.color.income),
			ContextCompat.getColor(requireContext(), R.color.blue_500),
			ContextCompat.getColor(requireContext(), R.color.reporting_chart_yellow),
			ContextCompat.getColor(requireContext(), R.color.reporting_chart_teal),
			ContextCompat.getColor(requireContext(), R.color.reporting_chart_orange)
		)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initCharts()
		observeTransaction()
		observeSelectedCurrency()
		viewModel.getAllTransaction(DEFAULT_FILTER)
	}

	private fun initCharts() = with(binding) {
		configureAllTimeBarChart(allTimeBarChart)
		configureAllTimeCategoriesChart(allTimeCategoriesChart)
	}

	private fun observeTransaction() = lifecycleScope.launchWhenStarted {
		viewModel.uiState.collect { uiState ->
			when (uiState) {
				is ViewState.Loading -> {
				}
				is ViewState.Success -> {
					latestTransactions = uiState.transaction
					renderReporting(uiState.transaction)
				}
				is ViewState.Error -> binding.root.snack(string = R.string.text_error)
				is ViewState.Empty -> {
					latestTransactions = emptyList()
					renderReporting(emptyList())
				}
			}
		}
	}

	private fun observeSelectedCurrency() = lifecycleScope.launchWhenStarted {
		viewModel.selectedCurrency.collect { currency ->
			selectedCurrency = currency
			renderReporting(latestTransactions)
		}
	}

	private fun renderReporting(transactions: List<Transaction>) {
		val reportingOverview = buildReportingOverview(transactions)
		renderAllTimeCard(reportingOverview.allTime)
		renderAllTimeCategories(reportingOverview.allTime.topExpenseCategories)
		renderMonthlyBreakdown(reportingOverview.latestMonth)
	}

	private fun renderAllTimeCard(summary: ReportingPeriodSummary) = with(binding) {
		allTimeIncomeValue.text = formatCurrencyAmount(summary.totalIncome, selectedCurrency)
		allTimeExpenseValue.text = formatCurrencyAmount(summary.totalExpense, selectedCurrency)
		allTimeSavingsValue.text = formatCurrencyAmount(summary.remaining, selectedCurrency)

		val incomeMagnitude = chartMagnitude(summary.totalIncome)
		val useIncomeReference = incomeMagnitude > 0f
		val chartEntries = if (useIncomeReference) {
			listOf(
				BarEntry(0f, chartFillMagnitude(summary.totalExpense, summary.totalIncome)),
				BarEntry(1f, chartFillMagnitude(summary.remaining, summary.totalIncome))
			)
		} else {
			listOf(
				BarEntry(0f, chartMagnitude(summary.totalExpense)),
				BarEntry(1f, chartMagnitude(summary.remaining))
			)
		}

		if (useIncomeReference) {
			allTimeBarChart.axisLeft.axisMaximum = 1f
			allTimeBarChart.axisLeft.axisMinimum = 0f
		} else {
			val positiveMax = chartEntries.maxOfOrNull { it.y } ?: 0f
			val padding = positiveMax.let {
				if (it == 0f) 1f else it * 0.18f
			}
			allTimeBarChart.axisLeft.axisMaximum = if (positiveMax == 0f) padding else positiveMax + padding
			allTimeBarChart.axisLeft.axisMinimum = 0f
		}

		allTimeBarChart.data = BarData(
			BarDataSet(chartEntries, "").apply {
				colors = listOf(
					ContextCompat.getColor(requireContext(), R.color.expense),
					ContextCompat.getColor(requireContext(), R.color.blue_500)
				)
				setBarShadowColor(ContextCompat.getColor(requireContext(), R.color.reporting_chart_track))
				setDrawValues(false)
				highLightAlpha = 0
			}
		).apply {
			barWidth = 0.78f
		}
		allTimeBarChart.notifyDataSetChanged()
		allTimeBarChart.fitScreen()
		allTimeBarChart.invalidate()
	}

	private fun renderAllTimeCategories(categories: List<CategorySpending>) = with(binding) {
		if (categories.isEmpty()) {
			allTimeCategoriesContent.hide()
			allTimeCategoriesEmptyState.show()
			allTimeCategoriesChart.clear()
			return
		}

		allTimeCategoriesEmptyState.hide()
		allTimeCategoriesContent.show()
		allTimeCategoriesLegendContainer.removeAllViews()

		val chartColors = mutableListOf<Int>()
		val chartEntries = categories.mapIndexed { index, category ->
			val color = resolveCategoryColor(index)
			chartColors += color
			allTimeCategoriesLegendContainer.addView(createLegendRow(category, color))
			PieEntry(category.amount.toFloat(), category.tag)
		}

		allTimeCategoriesChart.setHoleColor(ContextCompat.getColor(requireContext(), R.color.surface))
		allTimeCategoriesChart.data = PieData(
			PieDataSet(chartEntries, "").apply {
				colors = chartColors
				sliceSpace = 3f
				selectionShift = 0f
				setDrawValues(false)
			}
		)
		allTimeCategoriesChart.highlightValues(null)
		allTimeCategoriesChart.invalidate()
	}

	private fun renderMonthlyBreakdown(monthlyCard: MonthlyReportingCard?) = with(binding) {
		if (monthlyCard == null) {
			monthlyBreakdownContent.hide()
			monthlyBreakdownEmptyState.show()
			return
		}

		monthlyBreakdownEmptyState.hide()
		monthlyBreakdownContent.show()
		monthlyBreakdownMonth.text = monthlyCard.monthLabel
		monthlyIncomeValue.text = formatCurrencyAmount(monthlyCard.summary.totalIncome, selectedCurrency)
		monthlyExpenseValue.text = formatCurrencyAmount(monthlyCard.summary.totalExpense, selectedCurrency)
		monthlySavingsValue.text = formatCurrencyAmount(monthlyCard.summary.remaining, selectedCurrency)
		renderMonthlyExpenseCategories(
			categories = monthlyCard.summary.topExpenseCategories,
			totalExpense = monthlyCard.summary.totalExpense
		)
	}

	private fun renderMonthlyExpenseCategories(
		categories: List<CategorySpending>,
		totalExpense: Double
	) = with(binding) {
		monthlyCategoryContainer.removeAllViews()
		if (categories.isEmpty()) {
			monthlyCategoryEmptyState.show()
			return
		}

		monthlyCategoryEmptyState.hide()
		categories.forEachIndexed { index, category ->
			monthlyCategoryContainer.addView(
				createMonthlyCategoryRow(
					category = category,
					totalExpense = totalExpense,
					color = resolveCategoryColor(index)
				)
			)
		}
	}

	private fun createLegendRow(category: CategorySpending, color: Int): View {
		val legendBinding = ItemReportingCategoryLegendBinding.inflate(
			LayoutInflater.from(requireContext()),
			binding.allTimeCategoriesLegendContainer,
			false
		)

		return legendBinding.apply {
			legendLabel.text = category.tag
			legendAmount.text = formatCurrencyAmount(category.amount, selectedCurrency)
			legendSwatch.background = GradientDrawable().apply {
				shape = GradientDrawable.RECTANGLE
				cornerRadius = resources.getDimension(R.dimen.dimen_4)
				setColor(color)
			}
		}.root
	}

	private fun createMonthlyCategoryRow(
		category: CategorySpending,
		totalExpense: Double,
		color: Int
	): View {
		val rowBinding = ItemReportingMonthlyCategoryBinding.inflate(
			LayoutInflater.from(requireContext()),
			binding.monthlyCategoryContainer,
			false
		)
		val progress = if (totalExpense <= 0.0 || category.amount <= 0.0) {
			0
		} else {
			((category.amount / totalExpense) * CATEGORY_PROGRESS_MAX)
				.roundToInt()
				.coerceIn(1, CATEGORY_PROGRESS_MAX)
		}

		return rowBinding.apply {
			monthlyCategoryName.text = category.tag
			monthlyCategoryAmount.text = formatCurrencyAmount(category.amount, selectedCurrency)
			monthlyCategoryAmount.setTextColor(color)
			monthlyCategoryProgress.trackColor = ColorUtils.setAlphaComponent(color, CATEGORY_TRACK_ALPHA)
			monthlyCategoryProgress.setIndicatorColor(color)
			monthlyCategoryProgress.setProgressCompat(progress, false)
		}.root
	}

	private fun resolveCategoryColor(index: Int): Int {
		return categoryPalette[index % categoryPalette.size]
	}

	private fun chartMagnitude(value: Double): Float {
		return abs(value).toFloat()
	}

	private fun chartFillMagnitude(value: Double, reference: Double): Float {
		val referenceMagnitude = abs(reference)
		if (referenceMagnitude <= 0.0) {
			return 0f
		}

		return (abs(value) / referenceMagnitude)
			.toFloat()
			.coerceIn(0f, 1f)
	}

	private fun configureAllTimeBarChart(chart: BarChart) = with(chart) {
		renderer = RoundedBarChartRenderer(
			chart = this,
			animator = animator,
			viewPortHandler = viewPortHandler,
			cornerRadiusDp = 14f
		)
		description.isEnabled = false
		legend.isEnabled = false
		setDrawGridBackground(false)
		setDrawBorders(false)
		setDrawBarShadow(true)
		setScaleEnabled(false)
		setPinchZoom(false)
		setTouchEnabled(false)
		setDoubleTapToZoomEnabled(false)
		setHighlightPerTapEnabled(false)
		setHighlightPerDragEnabled(false)
		setMinOffset(0f)
		setFitBars(true)
		isHighlightFullBarEnabled = false
		setNoDataText("")
		setDrawValueAboveBar(true)
		setMaxVisibleValueCount(2)
		setPinchZoom(false)
		setScaleYEnabled(false)
		setScaleXEnabled(false)
		setViewPortOffsets(
			resources.getDimension(R.dimen.dimen_8),
			resources.getDimension(R.dimen.dimen_24),
			resources.getDimension(R.dimen.dimen_8),
			resources.getDimension(R.dimen.dimen_8)
		)
		setDrawMarkers(false)
		setDrawBarShadow(true)
		setDrawGridBackground(false)
		setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
		setClipValuesToContent(true)
		setDrawBorders(false)
		setKeepPositionOnRotation(false)
		setVisibleXRangeMaximum(2f)
		setVisibleXRangeMinimum(2f)
		isAutoScaleMinMaxEnabled = false
		isDoubleTapToZoomEnabled = false
		setDragEnabled(false)
		axisRight.isEnabled = false
		xAxis.apply {
			position = XAxis.XAxisPosition.BOTTOM
			setDrawAxisLine(false)
			setDrawGridLines(false)
			setDrawLabels(false)
			axisMinimum = -0.55f
			axisMaximum = 1.55f
			granularity = 1f
		}
		axisLeft.apply {
			setDrawAxisLine(false)
			setDrawGridLines(false)
			setDrawLabels(false)
			setDrawZeroLine(false)
		}
	}

	private fun configureAllTimeCategoriesChart(chart: PieChart) = with(chart) {
		description.isEnabled = false
		legend.isEnabled = false
		setUsePercentValues(false)
		setDrawEntryLabels(false)
		setTouchEnabled(false)
		isRotationEnabled = false
		setHighlightPerTapEnabled(false)
		setTransparentCircleAlpha(0)
		transparentCircleRadius = 0f
		holeRadius = 64f
		setHoleColor(ContextCompat.getColor(context, R.color.surface))
		setNoDataText("")
		setMinOffset(0f)
		setExtraOffsets(0f, 0f, 0f, 0f)
	}

	override fun getViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?
	) = FragmentReportingBinding.inflate(inflater, container, false)

	private companion object {
		const val DEFAULT_FILTER = "Overall"
		const val CATEGORY_PROGRESS_MAX = 100
		const val CATEGORY_TRACK_ALPHA = 52
	}
}
