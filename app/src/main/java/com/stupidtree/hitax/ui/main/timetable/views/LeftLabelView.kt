package com.stupidtree.hitax.ui.main.timetable.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.stupidtree.hitax.R
import com.stupidtree.hitax.data.model.timetable.TimeInDay

class LeftLabelView : View {
    constructor(context: Context?) : super(context) {}

    constructor(context: Context?, attrs: AttributeSet) : super(context, attrs) {
        typedTimeTableView(attrs, 0)
    }

    constructor(context: Context?, attrs: AttributeSet, defStyleAttr: Int) : super(
            context,
            attrs,
            defStyleAttr
    ) {
        typedTimeTableView(attrs, defStyleAttr)
    }

    private fun typedTimeTableView(attrs: AttributeSet, defStyleAttr: Int) {
        val a = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.LeftLabelView,
                defStyleAttr,
                0
        )
        val n = a.indexCount
        for (i in 0 until n) {
            when (val attr = a.getIndex(i)) {
                R.styleable.LeftLabelView_timeLabelSize -> labelSize = a.getDimensionPixelSize(
                        attr, TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, 8f, resources.displayMetrics
                ).toInt()
                )
                R.styleable.LeftLabelView_timeLabelColor -> labelColor =
                        a.getColor(attr, Color.BLACK)
            }
        }
        a.recycle()
        if (labelSize == 0) {
            labelSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics
            ).toInt()
        }
    }

    private val mLabelPaint = Paint()
    var labelColor = 0
    var labelSize = 0
    enum class LabelMode { TIME, PERIOD }
    private var labelMode: LabelMode = LabelMode.TIME
    private val startDate = TimeInDay(8, 0)
    private val temp = TimeInDay(8, 0)
    var sectionHeight = 180

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mLabelPaint.color = resolveLabelColor()
        mLabelPaint.textSize = labelSize.toFloat()
        mLabelPaint.textAlign = Paint.Align.CENTER
        val centerX = width / 2f
        if (labelMode == LabelMode.PERIOD) {
            val periods = TimeTableView.timetableStructure
            for (i in periods.indices) {
                val from = periods[i].from
                val minutes = startDate.getDistanceInMinutes(from).coerceAtLeast(0)
                val top = (minutes / 60f * sectionHeight)
                val line1Y = top + labelSize
                val line2Y = top + labelSize * 2
                val line3Y = top + labelSize * 3
                canvas.drawText("第", centerX, line1Y, mLabelPaint)
                canvas.drawText("${i + 1}", centerX, line2Y, mLabelPaint)
                canvas.drawText("节", centerX, line3Y, mLabelPaint)
            }
        } else {
            for (i in startDate.hour..23) {
                temp.hour = i
                val top = ((startDate.getDistanceInMinutes(temp)/60f) * sectionHeight)
                canvas.drawText(
                        temp.toString(), centerX, (top + labelSize), mLabelPaint)
            }
        }
    }

    fun setStartDate(hour: Int, minute: Int) {
        startDate.hour = hour
        startDate.minute = minute
        invalidate()
    }

    fun setLabelMode(mode: LabelMode) {
        labelMode = mode
        invalidate()
    }

    private fun resolveLabelColor(): Int {
        if (labelColor != 0) return labelColor
        val typedValue = TypedValue()
        val resolved = context.theme.resolveAttribute(R.attr.textColorSecondary, typedValue, true)
        return if (resolved) typedValue.data else Color.BLACK
    }
}
