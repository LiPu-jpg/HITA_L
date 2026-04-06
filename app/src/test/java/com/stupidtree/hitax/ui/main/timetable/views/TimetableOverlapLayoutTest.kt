package com.stupidtree.hitax.ui.main.timetable.views

import com.stupidtree.hitax.data.model.timetable.EventItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.Timestamp
import java.util.Calendar

class TimetableOverlapLayoutTest {
    @Test
    fun arrange_keepsFullWidthForNonOverlappingEvents() {
        val first = event("first", day = 0, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0)
        val second = event("second", day = 0, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0)

        val arranged = TimetableOverlapLayout.arrange(listOf(first, second)).associateBy { it.event.id }

        assertEquals(1, arranged.getValue(first.id).columnCount)
        assertEquals(0, arranged.getValue(first.id).columnIndex)
        assertEquals(1, arranged.getValue(second.id).columnCount)
        assertEquals(0, arranged.getValue(second.id).columnIndex)
    }

    @Test
    fun arrange_splitsOverlappingEventsIntoColumns() {
        val first = event("first", day = 0, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0)
        val second = event("second", day = 0, startHour = 9, startMinute = 30, endHour = 10, endMinute = 30)

        val arranged = TimetableOverlapLayout.arrange(listOf(first, second)).associateBy { it.event.id }

        assertEquals(2, arranged.getValue(first.id).columnCount)
        assertEquals(2, arranged.getValue(second.id).columnCount)
        assertEquals(0, arranged.getValue(first.id).columnIndex)
        assertEquals(1, arranged.getValue(second.id).columnIndex)
    }

    @Test
    fun arrange_keepsClusterWidthAtPeakConcurrencyForOverlapChain() {
        val first = event("first", day = 0, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0)
        val second = event("second", day = 0, startHour = 9, startMinute = 30, endHour = 10, endMinute = 30)
        val third = event("third", day = 0, startHour = 10, startMinute = 0, endHour = 11, endMinute = 0)

        val arranged = TimetableOverlapLayout.arrange(listOf(first, second, third)).associateBy { it.event.id }

        assertEquals(2, arranged.getValue(first.id).columnCount)
        assertEquals(2, arranged.getValue(second.id).columnCount)
        assertEquals(2, arranged.getValue(third.id).columnCount)
        assertEquals(0, arranged.getValue(first.id).columnIndex)
        assertEquals(1, arranged.getValue(second.id).columnIndex)
        assertEquals(0, arranged.getValue(third.id).columnIndex)
    }

    private fun event(
        id: String,
        day: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ): EventItem {
        val event = EventItem()
        event.id = id
        event.from = timestamp(day, startHour, startMinute)
        event.to = timestamp(day, endHour, endMinute)
        return event
    }

    private fun timestamp(day: Int, hour: Int, minute: Int): Timestamp {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 6 + day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return Timestamp(calendar.timeInMillis)
    }
}
