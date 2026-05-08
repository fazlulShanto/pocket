package dev.spikeysanju.expensetracker.view.reporting

import android.graphics.Canvas
import android.graphics.RectF
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.buffer.BarBuffer
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.renderer.BarChartRenderer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler
import kotlin.math.ceil
import kotlin.math.min

class RoundedBarChartRenderer(
    chart: BarDataProvider,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler,
    private val cornerRadiusDp: Float = 12f
) : BarChartRenderer(chart, animator, viewPortHandler) {

    private val roundedBarRect = RectF()
    private val roundedShadowRect = RectF()

    override fun drawDataSet(c: Canvas, dataSet: IBarDataSet, index: Int) {
        val transformer = mChart.getTransformer(dataSet.axisDependency)

        mBarBorderPaint.color = dataSet.barBorderColor
        mBarBorderPaint.strokeWidth = Utils.convertDpToPixel(dataSet.barBorderWidth)

        val drawBorder = dataSet.barBorderWidth > 0f
        val phaseX = mAnimator.phaseX
        val phaseY = mAnimator.phaseY

        if (mChart.isDrawBarShadowEnabled) {
            mShadowPaint.color = dataSet.barShadowColor

            val barWidth = mChart.barData.barWidth
            val barWidthHalf = barWidth / 2f
            val entryCount = min(
                ceil(dataSet.entryCount * phaseX.toDouble()).toInt(),
                dataSet.entryCount
            )

            for (indexInSet in 0 until entryCount) {
                val entry = dataSet.getEntryForIndex(indexInSet)
                val x = entry.x

                roundedShadowRect.left = x - barWidthHalf
                roundedShadowRect.right = x + barWidthHalf
                transformer.rectValueToPixel(roundedShadowRect)

                if (!mViewPortHandler.isInBoundsLeft(roundedShadowRect.right)) {
                    continue
                }

                if (!mViewPortHandler.isInBoundsRight(roundedShadowRect.left)) {
                    break
                }

                roundedShadowRect.top = mViewPortHandler.contentTop()
                roundedShadowRect.bottom = mViewPortHandler.contentBottom()
                drawRoundedRect(c, roundedShadowRect, shadowOnly = true)
            }
        }

        val buffer: BarBuffer = mBarBuffers[index]
        buffer.setPhases(phaseX, phaseY)
        buffer.setDataSet(index)
        buffer.setInverted(mChart.isInverted(dataSet.axisDependency))
        buffer.setBarWidth(mChart.barData.barWidth)
        buffer.feed(dataSet)
        transformer.pointValuesToPixel(buffer.buffer)

        val isSingleColor = dataSet.colors.size == 1
        if (isSingleColor) {
            mRenderPaint.color = dataSet.getColor()
        }

        for (bufferIndex in 0 until buffer.size() step 4) {
            val left = buffer.buffer[bufferIndex]
            val top = buffer.buffer[bufferIndex + 1]
            val right = buffer.buffer[bufferIndex + 2]
            val bottom = buffer.buffer[bufferIndex + 3]

            if (!mViewPortHandler.isInBoundsLeft(right)) {
                continue
            }

            if (!mViewPortHandler.isInBoundsRight(left)) {
                break
            }

            if (!isSingleColor) {
                mRenderPaint.color = dataSet.getColor(bufferIndex / 4)
            }

            roundedBarRect.set(left, top, right, bottom)
            drawRoundedRect(c, roundedBarRect, shadowOnly = false)

            if (drawBorder) {
                val radius = resolveCornerRadius(roundedBarRect)
                c.drawRoundRect(roundedBarRect, radius, radius, mBarBorderPaint)
            }
        }
    }

    private fun drawRoundedRect(c: Canvas, rect: RectF, shadowOnly: Boolean) {
        val radius = resolveCornerRadius(rect)
        if (shadowOnly) {
            c.drawRoundRect(rect, radius, radius, mShadowPaint)
        } else {
            c.drawRoundRect(rect, radius, radius, mRenderPaint)
        }
    }

    private fun resolveCornerRadius(rect: RectF): Float {
        val requestedRadius = Utils.convertDpToPixel(cornerRadiusDp)
        return min(requestedRadius, min(rect.width(), rect.height()) / 2f)
    }
}