package com.stupidtree.hitax.data.repository

import com.stupidtree.hitax.data.model.timetable.EventItem
import net.fortuna.ical4j.model.component.VEvent
import java.sql.Timestamp
import java.util.UUID

object IcsImportEventMapper {
    fun map(event: VEvent, timetableId: String): EventItem? {
        val startDate = event.startDate?.date ?: return null
        val endDate = event.endDate?.date ?: return null

        val item = EventItem()
        item.id = UUID.randomUUID().toString()
        item.timetableId = timetableId
        item.name = event.summary?.value ?: "未知课程"

        val locationStr = event.location?.value ?: ""
        val parts = locationStr.split(" ", limit = 2)
        item.place = parts.getOrNull(0) ?: ""
        item.teacher = parts.getOrNull(1) ?: ""

        item.from = Timestamp(startDate.time)
        item.to = Timestamp(endDate.time)
        item.fromNumber = 0
        item.lastNumber = 0
        item.type = EventItem.TYPE.OTHER
        item.color = -1
        return item
    }
}
