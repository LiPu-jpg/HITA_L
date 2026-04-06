package com.stupidtree.hitax.ui.main.timetable.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.stupidtree.hitax.R
import com.stupidtree.hitax.data.model.timetable.EventItem
import com.stupidtree.hitax.ui.main.timetable.TimetableStyleSheet
import kotlin.math.roundToInt

class TimeTableBlockView constructor(
    context: Context,
    private val event: EventItem,
    var styleSheet: TimetableStyleSheet,
    private val columnIndex: Int = 0,
    private val columnCount: Int = 1
) : FrameLayout(context) {
    lateinit var card: View
    var title: TextView? = null
    var subtitle: TextView? = null
    var icon: ImageView? = null
    var onCardClickListener: OnCardClickListener? = null
    var onCardLongClickListener: OnCardLongClickListener? = null

    interface OnCardClickListener {
        fun onClick(v: View, ei: EventItem)
    }

    interface OnCardLongClickListener {
        fun onLongClick(v: View, ei: EventItem): Boolean
    }

    private fun getColor(color: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(color, typedValue, true)
        return typedValue.data
    }

    private fun initEventCard(context: Context) {
        inflate(context, R.layout.fragment_timetable_class_card, this)
        card = findViewById(R.id.card)
        title = findViewById(R.id.title)
        subtitle = findViewById(R.id.subtitle)
        icon = findViewById(R.id.icon)
        if (styleSheet.isFadeEnabled) {
            card.setBackgroundResource(R.drawable.spec_timetable_card_background_fade)
        } else {
            card.setBackgroundResource(R.drawable.spec_timetable_card_background)
        }
        if (styleSheet.isColorEnabled) {
            card.backgroundTintList = ColorStateList.valueOf(event.color)
        } else {
            card.backgroundTintList = ColorStateList.valueOf(getColor(R.attr.colorPrimary))
        }
        when (styleSheet.cardTitleColor) {
            "subject" -> if (styleSheet.isColorEnabled) {
                title?.setTextColor(event.color)
            } else title?.setTextColor(getColor(R.attr.colorPrimary))
            "white" -> title?.setTextColor(Color.WHITE)
            "black" -> title?.setTextColor(Color.BLACK)
            "primary" -> title?.setTextColor(getColor(R.attr.colorPrimary))
        }
        when (styleSheet.subTitleColor) {
            "subject" -> if (styleSheet.isColorEnabled) {
                subtitle?.setTextColor(event.color)
            } else subtitle?.setTextColor(getColor(R.attr.colorPrimary))
            "white" -> subtitle?.setTextColor(Color.WHITE)
            "black" -> subtitle?.setTextColor(Color.BLACK)
            "primary" -> subtitle?.setTextColor(getColor(R.attr.colorPrimary))
        }
        if (styleSheet.cardIconEnabled) {
            icon?.visibility = VISIBLE
            icon?.setColorFilter(Color.WHITE)
            when (styleSheet.iconColor) {
                "subject" -> if (styleSheet.isColorEnabled) {
                    icon?.setColorFilter(event.color)
                } else icon?.setColorFilter(getColor(R.attr.colorPrimary))
                "white" -> icon?.setColorFilter(Color.WHITE)
                "black" -> icon?.setColorFilter(Color.BLACK)
                "primary" -> icon?.setColorFilter(getColor(R.attr.colorPrimary))
            }
        } else {
            icon?.visibility = GONE
        }

        card.setOnClickListener { v -> onCardClickListener?.onClick(v, event) }
        card.setOnLongClickListener { v: View ->
            return@setOnLongClickListener onCardLongClickListener?.onLongClick(v, event) == true
        }
        title?.text = event.name
        subtitle?.text = if (TextUtils.isEmpty(event.place)) "" else event.place
        card.background.mutate().alpha = (255 * (styleSheet.cardOpacity.toFloat() / 100)).toInt()
        if (styleSheet.isBoldText) {
            title?.typeface = Typeface.DEFAULT_BOLD
            subtitle?.typeface = Typeface.DEFAULT_BOLD
        }
        title?.alpha = styleSheet.titleAlpha.toFloat() / 100
        subtitle?.alpha = styleSheet.subtitleAlpha.toFloat() / 100
        title?.gravity = styleSheet.titleGravity
        applyTextScaleForColumns()
        applyMarginScaleForColumns()
    }

    private fun applyTextScaleForColumns() {
        val scale = TimetableCardTextScale.forColumnCount(columnCount)
        if (scale == 1f) {
            return
        }
        title?.let {
            it.setTextSize(TypedValue.COMPLEX_UNIT_PX, it.textSize * scale)
        }
        subtitle?.let {
            it.setTextSize(TypedValue.COMPLEX_UNIT_PX, it.textSize * scale)
        }
    }

    private fun applyMarginScaleForColumns() {
        val scale = TimetableCardTextScale.marginScaleForColumnCount(columnCount)
        if (scale == 1f) {
            return
        }
        scaleMargins(icon, scale)
        scaleMargins(title, scale)
        scaleMargins(subtitle, scale)
    }

    private fun scaleMargins(view: View?, scale: Float) {
        val params = view?.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        params.leftMargin = (params.leftMargin * scale).roundToInt()
        params.topMargin = (params.topMargin * scale).roundToInt()
        params.rightMargin = (params.rightMargin * scale).roundToInt()
        params.bottomMargin = (params.bottomMargin * scale).roundToInt()
        view.layoutParams = params
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!this::card.isInitialized) {
            initEventCard(context)
        }
    }

    fun getDow(): Int {
        return event.getDow()
    }

    fun getDuration(): Int {
        return event.getDurationInMinutes()
    }

    fun getStartTime(): Long {
        return event.from.time
    }

    fun getColumnIndex(): Int {
        return columnIndex
    }

    fun getColumnCount(): Int {
        return columnCount
    }
}
