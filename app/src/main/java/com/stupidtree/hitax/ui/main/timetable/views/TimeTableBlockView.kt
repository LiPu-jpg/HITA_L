package com.stupidtree.hitax.ui.main.timetable.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.stupidtree.hitax.R
import com.stupidtree.hitax.data.model.timetable.EventItem
import com.stupidtree.hitax.ui.main.timetable.TimetableStyleSheet
import kotlin.math.max
import kotlin.math.min

class TimeTableBlockView constructor(
    context: Context,
    var block: Any,
    var styleSheet: TimetableStyleSheet
) :
    FrameLayout(context) {
    lateinit var card: View
    var title: TextView? = null
    var subtitle: TextView? = null
    var icon: ImageView? = null
    var onCardClickListener: OnCardClickListener? = null
    var onCardLongClickListener: OnCardLongClickListener? = null
    var onDuplicateCardClickListener: OnDuplicateCardClickListener? = null
    var onDuplicateCardLongClickListener: OnDuplicateCardLongClickListener? = null

    interface OnCardClickListener {
        fun onClick(v: View, ei: EventItem)
    }

    interface OnCardLongClickListener {
        fun onLongClick(v: View, ei: EventItem): Boolean
    }

    interface OnDuplicateCardClickListener {
        fun onDuplicateClick(v: View, list: List<EventItem>)
    }

    private fun getColor(color: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(color, typedValue, true)
        return typedValue.data
    }

    private fun initEventCard(context: Context) {
        val ei = block as EventItem
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
            card.backgroundTintList = ColorStateList.valueOf(ei.color)
        } else {
            card.backgroundTintList = ColorStateList.valueOf(getColor(R.attr.colorPrimary))
        }
        when (styleSheet.cardTitleColor) {
            "subject" -> if (styleSheet.isColorEnabled) {
                title?.setTextColor(ei.color)
            } else title?.setTextColor(getColor(R.attr.colorPrimary))
            "white" -> title?.setTextColor(Color.WHITE)
            "black" -> title?.setTextColor(Color.BLACK)
            "primary" -> title?.setTextColor(getColor(R.attr.colorPrimary))
        }
        when (styleSheet.subTitleColor) {
            "subject" -> if (styleSheet.isColorEnabled) {
                subtitle?.setTextColor(ei.color)
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
                    icon?.setColorFilter(ei.color)
                } else icon?.setColorFilter(getColor(R.attr.colorPrimary))
                "white" -> icon?.setColorFilter(Color.WHITE)
                "black" -> icon?.setColorFilter(Color.BLACK)
                "primary" -> icon?.setColorFilter(getColor(R.attr.colorPrimary))
            }
        } else {
            icon?.visibility = GONE
        }

        card.setOnClickListener { v -> onCardClickListener?.onClick(v, ei) }
        card.setOnLongClickListener { v: View ->
            return@setOnLongClickListener onCardLongClickListener?.onLongClick(v, ei) == true
        }
        title?.text = ei.name
        subtitle?.text = if (TextUtils.isEmpty(ei.place)) "" else ei.place
        card.background.mutate().alpha = (255 * (styleSheet.cardOpacity.toFloat() / 100)).toInt()
        if (styleSheet.isBoldText) {
            title?.typeface = Typeface.DEFAULT_BOLD
            subtitle?.typeface = Typeface.DEFAULT_BOLD
        }
        title?.alpha = styleSheet.titleAlpha.toFloat() / 100
        subtitle?.alpha = styleSheet.subtitleAlpha.toFloat() / 100
        title?.gravity = styleSheet.titleGravity
    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (block is EventItem) {
            initEventCard(context)
        } else if (block is List<*>) {
            initDuplicateCard(context)
        }
    }


    @Suppress("UNCHECKED_CAST")
    private fun initDuplicateCard(context: Context) {
        val list: List<EventItem> = block as List<EventItem>
        // 创建一个垂直的 LinearLayout 来容纳多个课程块
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(container)
        
        // 为每个冲突的课程创建一个子块
        val count = list.size
        for (index in list.indices) {
            val ei = list[index]
            val childBlock = createChildEventCard(context, ei, index, count)
            container.addView(childBlock)
        }
        
        // 为整个容器也设置点击事件（兼容旧的点击方式）
        container.setOnClickListener { v ->
            onDuplicateCardClickListener?.onDuplicateClick(v, list)
        }
        container.setOnLongClickListener { v ->
            onDuplicateCardLongClickListener?.onDuplicateLongClick(v, list) == true
        }
    }
    
    /**
     * 创建冲突课程中的一个子块
     */
    private fun createChildEventCard(context: Context, ei: EventItem, index: Int, total: Int): View {
        val childView = FrameLayout(context)
        val layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        if (index < total - 1) {
            layoutParams.setMargins(0, 0, 0, 2) // 添加微小间距区分
        }
        childView.layoutParams = layoutParams
        
        // inflate 单个课程卡片布局（使用LayoutInflater因为布局使用了merge标签）
        // attachToRoot=true 后布局已自动添加到 childView 中
        LayoutInflater.from(context).inflate(R.layout.fragment_timetable_class_card, childView, true)
        val card = childView.findViewById<View>(R.id.card)
        val title = childView.findViewById<TextView>(R.id.title)
        val subtitle = childView.findViewById<TextView>(R.id.subtitle)
        val icon = childView.findViewById<ImageView>(R.id.icon)
        
        // 设置样式
        if (styleSheet.isFadeEnabled) {
            card.setBackgroundResource(R.drawable.spec_timetable_card_background_fade)
        } else {
            card.setBackgroundResource(R.drawable.spec_timetable_card_background)
        }
        
        if (styleSheet.isColorEnabled) {
            card.backgroundTintList = ColorStateList.valueOf(ei.color)
        } else {
            card.backgroundTintList = ColorStateList.valueOf(getColor(R.attr.colorPrimary))
        }
        
        when (styleSheet.cardTitleColor) {
            "subject" -> if (styleSheet.isColorEnabled) {
                title?.setTextColor(ei.color)
            } else title?.setTextColor(getColor(R.attr.colorPrimary))
            "white" -> title?.setTextColor(Color.WHITE)
            "black" -> title?.setTextColor(Color.BLACK)
            "primary" -> title?.setTextColor(getColor(R.attr.colorPrimary))
        }
        
        when (styleSheet.subTitleColor) {
            "subject" -> if (styleSheet.isColorEnabled) {
                subtitle?.setTextColor(ei.color)
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
                    icon?.setColorFilter(ei.color)
                } else icon?.setColorFilter(getColor(R.attr.colorPrimary))
                "white" -> icon?.setColorFilter(Color.WHITE)
                "black" -> icon?.setColorFilter(Color.BLACK)
                "primary" -> icon?.setColorFilter(getColor(R.attr.colorPrimary))
            }
        } else {
            icon?.visibility = GONE
        }
        
        // 点击事件 - 对于冲突课程中的子卡片，使用 onDuplicateCardClickListener 传递单个课程
        card.setOnClickListener { v ->
            // 尝试使用单个课程回调，如果没有设置则使用冲突课程回调
            if (onCardClickListener != null) {
                onCardClickListener?.onClick(v, ei)
            } else {
                onDuplicateCardClickListener?.onDuplicateClick(v, listOf(ei))
            }
        }
        card.setOnLongClickListener { v: View ->
            val result = if (onCardLongClickListener != null) {
                onCardLongClickListener?.onLongClick(v, ei)
            } else {
                onDuplicateCardLongClickListener?.onDuplicateLongClick(v, listOf(ei))
            }
            return@setOnLongClickListener result == true
        }
        
        title?.text = ei.name
        subtitle?.text = if (TextUtils.isEmpty(ei.place)) "" else ei.place
        card.background.mutate().alpha = (255 * (styleSheet.cardOpacity.toFloat() / 100)).toInt()
        
        if (styleSheet.isBoldText) {
            title?.typeface = Typeface.DEFAULT_BOLD
            subtitle?.typeface = Typeface.DEFAULT_BOLD
        }
        title?.alpha = styleSheet.titleAlpha.toFloat() / 100
        subtitle?.alpha = styleSheet.subtitleAlpha.toFloat() / 100
        title?.gravity = styleSheet.titleGravity
        
        // 设置文本大小适应小空间
        if (total > 2) {
            title?.textSize = 10f
            subtitle?.textSize = 8f
        }
        
        return childView
    }

    interface OnDuplicateCardLongClickListener {
        fun onDuplicateLongClick(v: View, list: List<EventItem>): Boolean
    }


    @Suppress("UNCHECKED_CAST")
    fun getDow(): Int {
        if (block is EventItem) {
            return (block as EventItem).getDow()
        } else if (block is List<*>) {
            return (block as List<EventItem>)[0].getDow()
        }
        return -1
    }

    @Suppress("UNCHECKED_CAST")
    fun getDuration(): Int {
        if (block is EventItem) {
            return (block as EventItem).getDurationInMinutes()
        } else if (block is List<*>) {
            // 对于冲突课程，返回第一个课程的时间段（因为它们是并行的）
            val list = block as List<EventItem>
            return list[0].getDurationInMinutes()
        }
        return -1
    }

    fun getStartTime(): Long {
        if (block is EventItem) {
            return (block as EventItem).from.time
        } else if (block is List<*>) {
            var minStart = Long.MAX_VALUE
            for (e in block as List<*>) {
                minStart = min(minStart, (e as EventItem).from.time)
            }
            return minStart
        }
        return -1
    }


}
