package com.stupidtree.hitax.ui.main.timetable.views

import com.stupidtree.hitax.data.model.timetable.EventItem
import kotlin.math.max

object TimetableOverlapLayout {
    data class PositionedEvent(
        val event: EventItem,
        val columnIndex: Int,
        val columnCount: Int
    )

    private data class Cluster(
        val events: MutableList<EventItem> = mutableListOf(),
        var endTime: Long = Long.MIN_VALUE
    )

    private data class ActiveColumn(
        val columnIndex: Int,
        val endTime: Long
    )

    fun arrange(events: List<EventItem>): List<PositionedEvent> {
        if (events.isEmpty()) {
            return emptyList()
        }
        val positioned = mutableListOf<PositionedEvent>()
        events.groupBy { it.getDow() }
            .toSortedMap()
            .values
            .forEach { dayEvents ->
                val sortedEvents = dayEvents.sortedWith(
                    compareBy<EventItem>({ it.from.time }, { it.to.time }, { it.id })
                )
                splitIntoClusters(sortedEvents).forEach { cluster ->
                    positioned.addAll(positionCluster(cluster.events))
                }
            }
        return positioned
    }

    private fun splitIntoClusters(events: List<EventItem>): List<Cluster> {
        if (events.isEmpty()) {
            return emptyList()
        }
        val clusters = mutableListOf<Cluster>()
        var current: Cluster? = null
        for (event in events) {
            if (current == null || event.from.time >= current.endTime) {
                current = Cluster()
                clusters.add(current)
            }
            current.events.add(event)
            current.endTime = max(current.endTime, event.to.time)
        }
        return clusters
    }

    private fun positionCluster(events: List<EventItem>): List<PositionedEvent> {
        val positioned = mutableListOf<PositionedEvent>()
        val activeColumns = mutableListOf<ActiveColumn>()
        var maxColumns = 1

        for (event in events) {
            activeColumns.removeAll { it.endTime <= event.from.time }
            val usedColumns = activeColumns.map { it.columnIndex }.toSet()
            var columnIndex = 0
            while (usedColumns.contains(columnIndex)) {
                columnIndex++
            }
            activeColumns.add(ActiveColumn(columnIndex, event.to.time))
            activeColumns.sortBy { it.columnIndex }
            maxColumns = max(maxColumns, activeColumns.size)
            positioned.add(PositionedEvent(event, columnIndex, 1))
        }

        return positioned.map { it.copy(columnCount = maxColumns) }
    }
}
