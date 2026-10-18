package cn.ifafu.ifafu.ui.widget

import cn.ifafu.ifafu.schedule.ScheduleEvent
import cn.ifafu.ifafu.schedule.TodaySchedule
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class UpcomingScheduleTest {
    private fun time(day: Int, hour: Int = 0, minute: Int = 0) = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.OCTOBER, day, hour, minute)
    }.timeInMillis
    private fun event(id: String, start: Long, kind: String = "course") =
        ScheduleEvent(id, "", id, "创104", "", start, start + 3600000, 0xffa7c2e1.toInt(), kind)
    @Test fun homeAndWidgetShareSelectionLabelsAndWarningsThroughTheWholeClass() {
        val start = time(9, 14)
        val events = listOf(event("lesson", start), event("next", time(9, 16)))
        for (offset in listOf(-900001L, -900000L, -1L, 0L, 599999L, 600000L, 3599999L, 3600000L)) {
            val now = start + offset
            assertEquals(TodaySchedule.today(events, now), UpcomingSchedule.today(events, now))
            events.forEach {
                assertEquals(TodaySchedule.label(it, now), UpcomingSchedule.label(it, now))
                assertEquals(TodaySchedule.urgent(it, now), UpcomingSchedule.urgent(it, now))
            }
        }
        assertEquals("1小时后下课", UpcomingSchedule.label(events.first(), start))
        assertEquals("50分钟后下课", UpcomingSchedule.label(events.first(), start + 600000))
        assertEquals("lesson", UpcomingSchedule.today(events, start + 3599999).first().uid)
        assertEquals("next", UpcomingSchedule.today(events, start + 3600000).first().uid)
    }
    @Test fun todayMergesOngoingCoursesExamsAndAgendaButNotTomorrowOrTestEvents() {
        val now = time(9, 14, 30)
        val events = listOf(event("past", time(9, 12)), event("ongoing", time(9, 14)),
            event("agenda", time(9, 15), "agenda"), event("tomorrow", time(10, 8)), event("test", time(9, 14), "test"))
        assertEquals(listOf("ongoing", "agenda"), UpcomingSchedule.today(events, now).map { it.uid })
        assertEquals("30分钟后下课", UpcomingSchedule.label(events[1], now))
        assertEquals("30分钟后开始", UpcomingSchedule.label(events[2], now))
        assertEquals("30分钟后结束", UpcomingSchedule.label(events[2], time(9, 15, 30)))
    }
    @Test fun midnightAndYearRolloverStayWithinToday() {
        assertTrue(UpcomingSchedule.today(listOf(event("tomorrow", time(10, 8))), time(9, 23, 59)).isEmpty())
        val december = Calendar.getInstance().apply { clear(); set(2026, 11, 31, 23, 59) }.timeInMillis
        val january = Calendar.getInstance().apply { clear(); set(2027, 0, 1, 8, 0) }.timeInMillis
        assertTrue(UpcomingSchedule.today(listOf(event("January", january)), december).isEmpty())
    }
    @Test fun examWinsTiesAndStaysUntilEndLikeTheApp() {
        val start = time(9, 14)
        val events = listOf(event("course", start), event("exam", start, "exam"))
        assertEquals("exam", UpcomingSchedule.today(events, start).first().uid)
        assertEquals("1小时后考试结束", UpcomingSchedule.label(events[1], start))
        assertEquals(UpcomingSchedule.today(events, start), UpcomingSchedule.today(events.reversed(), start))
    }
    @Test fun longLabelsKeepTheSameActionAsHome() {
        assertEquals("1小时47分后上课", UpcomingSchedule.countdownText(107, "course"))
        assertEquals("23小时20分后考试", UpcomingSchedule.countdownText(1400, "exam"))
        assertEquals("即将上课", UpcomingSchedule.countdownText(0, "course"))
    }
    @Test fun warningsAndSleepingTransitionsIncludeTheEndOfTheClass() {
        val start = time(9, 14); val course = event("lesson", start)
        assertFalse(UpcomingSchedule.urgent(course, start - 900001))
        assertTrue(UpcomingSchedule.urgent(course, start - 900000))
        assertFalse(UpcomingSchedule.urgent(course, start))
        assertEquals(start - 900000, UpcomingSchedule.nextTransition(listOf(course), time(9, 12)))
        assertEquals(course.end, UpcomingSchedule.nextTransition(listOf(course), start + 600000))
        assertEquals(time(10) + 1000, UpcomingSchedule.nextTransition(listOf(course), course.end))
    }
    @Test fun bothCountdownPhasesRefreshAtActualMinuteAndStructuralBoundaries() {
        val now = time(9, 12); val course = event("lesson", now + 61001)
        assertEquals(now + 1001, UpcomingSchedule.nextRefresh(listOf(course), now))
        assertEquals(2L, UpcomingSchedule.minutesUntil(course, now))
        assertEquals(course.start, UpcomingSchedule.nextRefresh(listOf(course), course.start - 1))
        assertEquals(course.start + 60000, UpcomingSchedule.nextRefresh(listOf(course), course.start + 1))
        assertEquals(time(10) + 1000, UpcomingSchedule.nextRefresh(emptyList(), now))
    }
}
