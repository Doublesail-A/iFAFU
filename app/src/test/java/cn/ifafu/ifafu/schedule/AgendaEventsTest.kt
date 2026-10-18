package cn.ifafu.ifafu.schedule

import cn.ifafu.ifafu.entity.AgendaItem
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AgendaEventsTest {
    private fun item() = AgendaItem(id = "fixed", account = "fixture", title = "读书会", date = "2026-10-03",
        startMinute = 14 * 60 + 10, endMinute = 15 * 60 + 20, location = "图书馆", notes = "带上笔记", color = 0xff92d8ce.toInt())
    @Test fun datedAgendaIsIndependentOfHolidaysAndPreservesUserFields() {
        val zone = ZoneId.of("Asia/Shanghai")
        val event = AgendaEvents.event(item(), zone)
        assertEquals(LocalDateTime.of(2026,10,3,14,10).atZone(zone).toInstant().toEpochMilli(), event.start)
        assertEquals("读书会", event.title); assertEquals("图书馆", event.location)
        assertEquals("带上笔记", event.description); assertEquals(item().color, event.color)
        assertEquals("agenda", event.kind); assertEquals(15 * 60000L, event.start - event.reminderAt)
        assertEquals(event.uid, AgendaEvents.event(item().copy(title = "新标题"), zone).uid)
    }
    @Test fun daylightSavingDoesNotMoveAnAfternoonCivilTime() {
        val zone = ZoneId.of("America/New_York")
        val event = AgendaEvents.event(item().copy(date = "2026-03-08"), zone)
        assertEquals(14, java.time.Instant.ofEpochMilli(event.start).atZone(zone).hour)
        assertEquals(10, java.time.Instant.ofEpochMilli(event.start).atZone(zone).minute)
    }
    @Test fun invalidDatesTitlesAndRangesCannotBecomeEvents() {
        listOf(item().copy(title = " "), item().copy(date = "2026-02-30"), item().copy(startMinute = -1),
            item().copy(endMinute = 1441), item().copy(endMinute = item().startMinute)).forEach {
            try { AgendaEvents.event(it); fail("Invalid agenda accepted") } catch (_: RuntimeException) { }
        }
    }
}
