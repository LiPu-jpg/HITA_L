package com.stupidtree.hitax.ui.main.timetable.views

object TimetableCardTextScale {
    fun forColumnCount(columnCount: Int): Float {
        return if (columnCount > 1) 0.5f else 1f
    }

    fun marginScaleForColumnCount(columnCount: Int): Float {
        return if (columnCount > 1) 0.5f else 1f
    }
}
