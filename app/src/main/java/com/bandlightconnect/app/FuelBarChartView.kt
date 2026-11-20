package com.bandlightconnect.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import java.util.Locale

class FuelBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bars: List<ChartBarData> = emptyList()
    private var selectedIndex: Int = -1
    private var emptyMessage: String = "No data recorded in this period"
    var onBarSelected: ((ChartBarData) -> Unit)? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E62D6")
        style = Paint.Style.FILL
    }

    private val selectedBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BB86FC")
        style = Paint.Style.FILL
    }

    private val emptyBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A2A")
        strokeWidth = dp(1f)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
    }

    private val selectedLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
        isFakeBoldText = true
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB74D")
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
        isFakeBoldText = true
    }

    private val emptyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#777777")
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
    }

    private val barRect = RectF()

    fun setStyle(normalColorHex: String, selectedColorHex: String, emptyMsg: String) {
        barPaint.color = Color.parseColor(normalColorHex)
        selectedBarPaint.color = Color.parseColor(selectedColorHex)
        emptyMessage = emptyMsg
        invalidate()
    }

    fun setData(data: List<ChartBarData>) {
        bars = data
        selectedIndex = data.indexOfLast { it.displayValue > 0f }.takeIf { it >= 0 } ?: (data.size - 1)
        invalidate()
        if (selectedIndex in bars.indices) {
            onBarSelected?.invoke(bars[selectedIndex])
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padTop = dp(24f)
        val padBottom = dp(26f)
        val padSide = dp(8f)
        val chartHeight = (height - padTop - padBottom).coerceAtLeast(1f)
        val chartWidth = (width - padSide * 2).coerceAtLeast(1f)
        val baseY = padTop + chartHeight

        canvas.drawLine(padSide, padTop, width - padSide, padTop, gridPaint)
        canvas.drawLine(padSide, padTop + chartHeight / 2f, width - padSide, padTop + chartHeight / 2f, gridPaint)
        canvas.drawLine(padSide, baseY, width - padSide, baseY, gridPaint)

        val maxVal = bars.maxOfOrNull { it.displayValue } ?: 0f
        if (bars.isEmpty() || maxVal <= 0f) {
            canvas.drawText(emptyMessage, width / 2f, padTop + chartHeight / 2f, emptyTextPaint)
            if (bars.isEmpty()) return
        }

        val slotWidth = chartWidth / bars.size
        val barWidth = (slotWidth * 0.56f).coerceAtMost(dp(28f))
        val corner = dp(5f)

        for (i in bars.indices) {
            val item = bars[i]
            val centerX = padSide + slotWidth * i + slotWidth / 2f
            val isSelected = i == selectedIndex

            val barHeight = if (maxVal > 0f && item.displayValue > 0f) {
                ((item.displayValue / maxVal) * chartHeight).coerceAtLeast(dp(6f))
            } else {
                dp(4f)
            }

            barRect.set(
                centerX - barWidth / 2f,
                baseY - barHeight,
                centerX + barWidth / 2f,
                baseY
            )

            val paint = when {
                item.displayValue <= 0f -> emptyBarPaint
                isSelected -> selectedBarPaint
                else -> barPaint
            }
            canvas.drawRoundRect(barRect, corner, corner, paint)

            if (item.displayValue > 0f && (isSelected || bars.size <= 8)) {
                val valText = if (item.displayValue >= 100f) {
                    String.format(Locale.US, "%.0f", item.displayValue)
                } else {
                    String.format(Locale.US, "%.1f", item.displayValue)
                }
                valuePaint.color = if (isSelected) Color.parseColor("#FFB74D") else Color.parseColor("#BBBBBB")
                canvas.drawText(valText, centerX, barRect.top - dp(5f), valuePaint)
            }

            canvas.drawText(
                item.label,
                centerX,
                baseY + dp(16f),
                if (isSelected) selectedLabelPaint else labelPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bars.isEmpty()) return super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
            val padSide = dp(8f)
            val chartWidth = (width - padSide * 2).coerceAtLeast(1f)
            val slotWidth = chartWidth / bars.size
            val idx = ((event.x - padSide) / slotWidth).toInt().coerceIn(0, bars.size - 1)
            if (idx != selectedIndex) {
                selectedIndex = idx
                invalidate()
                onBarSelected?.invoke(bars[selectedIndex])
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}