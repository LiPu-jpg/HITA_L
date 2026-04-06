package com.stupidtree.hitax.data.repository

import com.stupidtree.hitax.data.model.timetable.EventItem
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.sql.Timestamp

class IcsImportEventMapperTest {
    @Test
    fun map_preservesExactTimesAndClearsPeriodMetadata() {
        val start = DateTime("20260407T131000")
        val end = DateTime("20260407T144000")
        val event = VEvent(start, end, "线性代数")
        event.properties.add(Location("正心12 李老师"))

        val mapped = IcsImportEventMapper.map(event, "tt-1")

        assertNotNull(mapped)
        mapped ?: return
        assertEquals("tt-1", mapped.timetableId)
        assertEquals("线性代数", mapped.name)
        assertEquals("正心12", mapped.place)
        assertEquals("李老师", mapped.teacher)
        assertEquals(Timestamp(start.time), mapped.from)
        assertEquals(Timestamp(end.time), mapped.to)
        assertEquals(EventItem.TYPE.OTHER, mapped.type)
        assertEquals(0, mapped.fromNumber)
        assertEquals(0, mapped.lastNumber)
    }
}
