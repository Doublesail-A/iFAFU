package cn.ifafu.ifafu.ui.widget

import cn.ifafu.ifafu.schedule.ScheduleEvent
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class UpcomingScheduleTest {
    private fun event(id: String, time: Long, kind: String = "course") =
        ScheduleEvent(id, "", id, "创104", "", time, time + 3600000, 0xffa7c2e1.toInt(), kind)

    @Test fun nearestExamReplacesLaterCourseAndPastEventsNeverReappear() {
        val events = listOf(event("past", 900), event("lesson", 5000), event("exam", 3000, "exam"), event("test", 1100, "test"))
        assertEquals("exam", UpcomingSchedule.next(events, 1000)?.uid)
        assertEquals("lesson", UpcomingSchedule.next(events, 3000)?.uid)
        assertNull(UpcomingSchedule.next(events, 5000))
    }
    @Test fun examWinsSameTimeAndResultDoesNotDependOnInputOrder() {
        val events = listOf(event("lesson", 2000), event("exam", 2000, "exam"))
        assertEquals("exam", UpcomingSchedule.next(events, 1000)?.uid)
        assertEquals("exam", UpcomingSchedule.next(events.reversed(), 1000)?.uid)
    }
    @Test fun refreshAtStartOrMidnightWithoutMinutePolling() {
        val now = Calendar.getInstance().apply { clear(); set(2026, 9, 9, 12, 0) }.timeInMillis
        val later = now + 3600000
        assertEquals(later + 1000, UpcomingSchedule.nextRefresh(listOf(event("lesson", later)), now))
        val midnight = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.SECOND, 1) }.timeInMillis
        assertEquals(midnight, UpcomingSchedule.nextRefresh(listOf(event("after holiday", now + 3 * 86400000L)), now))
        assertEquals(midnight, UpcomingSchedule.nextRefresh(emptyList(), now))
    }
}
