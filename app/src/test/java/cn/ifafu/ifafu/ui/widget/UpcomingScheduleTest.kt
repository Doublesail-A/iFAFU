package cn.ifafu.ifafu.ui.widget

import cn.ifafu.ifafu.schedule.ScheduleEvent
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class UpcomingScheduleTest {
    private fun time(day: Int, hour: Int = 0, minute: Int = 0) = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.OCTOBER, day, hour, minute)
    }.timeInMillis
    private fun event(id: String, time: Long, kind: String = "course") =
        ScheduleEvent(id, "", id, "创104", "", time, time + 3600000, 0xffa7c2e1.toInt(), kind)

    @Test fun todayMergesCoursesAndExamsAndExcludesTomorrowAndStartedItems() {
        val now = time(9, 12)
        val events = listOf(event("past", time(9, 11)), event("third", time(9, 16)),
            event("lesson", time(9, 15)), event("exam", time(9, 14), "exam"),
            event("tomorrow", time(10, 8)), event("unknown", time(9, 13), "test"))
        assertEquals(listOf("exam", "lesson"), UpcomingSchedule.today(events, now).map { it.uid })
        assertEquals(listOf("lesson", "third"), UpcomingSchedule.today(events, time(9, 14)).map { it.uid })
        assertTrue(UpcomingSchedule.today(events, time(9, 16)).isEmpty())
        assertEquals("tomorrow", UpcomingSchedule.next(events, time(9, 16))?.uid)
    }
    @Test fun midnightAndYearRolloverCannotShowTheFollowingDay() {
        val now = time(9, 23, 59)
        assertTrue(UpcomingSchedule.today(listOf(event("midnight", time(10))), now).isEmpty())
        assertEquals(listOf("morning"), UpcomingSchedule.today(listOf(event("morning", time(10, 8))), time(10)).map { it.uid })
        val december = Calendar.getInstance().apply { clear(); set(2026, 11, 31, 23, 59) }.timeInMillis
        val january = Calendar.getInstance().apply { clear(); set(2027, 0, 1, 8, 0) }.timeInMillis
        assertTrue(UpcomingSchedule.today(listOf(event("January", january)), december).isEmpty())
    }
    @Test fun examWinsSameTimeAndResultDoesNotDependOnInputOrder() {
        val now = time(9, 12)
        val events = listOf(event("lesson", now + 2000), event("exam", now + 2000, "exam"))
        assertEquals("exam", UpcomingSchedule.today(events, now).first().uid)
        assertEquals(UpcomingSchedule.today(events, now), UpcomingSchedule.today(events.reversed(), now))
    }
    @Test fun durationUsesHoursAndMinutesAndPreservesExamLabels() {
        assertEquals("31分钟后上课", UpcomingSchedule.countdownText(31, "course"))
        assertEquals("1小时47分后上课", UpcomingSchedule.countdownText(107, "course"))
        assertEquals("2小时后考试", UpcomingSchedule.countdownText(120, "exam"))
        assertEquals("2小时21分\n后上课", UpcomingSchedule.countdownText(141, "course", true))
        assertEquals("即将上课", UpcomingSchedule.countdownText(0, "course"))
    }
    @Test fun countdownRoundsUpAndRefreshesAtTheNextLabelChange() {
        val now = time(9, 12)
        val events = listOf(event("lesson", now + 61001))
        assertEquals(2L, UpcomingSchedule.minutesUntil(events.first(), now))
        assertEquals(1L, UpcomingSchedule.minutesUntil(events.first(), now + 1001))
        assertEquals(now + 1001, UpcomingSchedule.nextRefresh(events, now))
        assertEquals(now + 60000, UpcomingSchedule.nextRefresh(listOf(event("later", now + 3600000)), now))
        assertEquals(time(10) + 1000, UpcomingSchedule.nextRefresh(listOf(event("tomorrow", time(10, 8))), now))
        assertEquals(time(10) + 1000, UpcomingSchedule.nextRefresh(emptyList(), now))
    }
}
