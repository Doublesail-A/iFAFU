package cn.ifafu.ifafu.schedule

import cn.ifafu.ifafu.entity.NewCourse
import cn.ifafu.ifafu.entity.SyllabusSetting
import cn.ifafu.ifafu.entity.Exam
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ScheduleEventsTest {
    private fun date(day: Int, hour: Int = 0, minute: Int = 0) = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.SEPTEMBER, day, hour, minute)
    }.time
    @Test fun weeksAndPeriodBreaksProduceActualTimesWithoutInventingMissingWeeks() {
        val setting = SyllabusSetting().apply { beginTime = listOf(0, 800, 850, 955); nodeLength = 45; weekCnt = 20 }
        val math = NewCourse(name = "高等数学D", classroom = "创104", teacher = "王老师",
            account = "private-account", year = "2026-2027", term = "1",
            weekday = Calendar.MONDAY, beginNode = 1, nodeLength = 2, weeks = sortedSetOf(1, 3))
        val lessons = ScheduleEvents.courses(listOf(math, math.copy(name = "[调课]高等数学D")), date(7), setting) { 0xff6750a4.toInt() }
        assertEquals(2, lessons.size)
        assertEquals(date(7, 8).time, lessons[0].start)
        assertEquals(date(7, 9, 35).time, lessons[0].end)
        assertEquals(date(21, 8).time, lessons[1].start)
        assertEquals(date(7, 7, 45).time, lessons[0].reminderAt)
        assertFalse(lessons[0].uid.contains("private-account"))
        assertEquals(lessons[0].uid, ScheduleEvents.courses(listOf(math), date(7), setting) { 1 }[0].uid)
        assertTrue(lessons[0].description.contains("王老师"))
    }

    @Test fun invalidPeriodsAreSkippedAndExamsUseThirtyMinuteLead() {
        val invalid = NewCourse(name = "无时间课程", weekday = 2, beginNode = 0, nodeLength = 2, weeks = sortedSetOf(1))
        assertTrue(ScheduleEvents.courses(listOf(invalid), date(7), SyllabusSetting()) { 1 }.isEmpty())
        val exam = ScheduleEvents.exams(listOf(Exam(name = "数学", startTime = date(8, 10).time, address = "创101", seatNumber = "23"))).single()
        assertEquals(date(8, 9, 30).time, exam.reminderAt)
        assertEquals("创101", exam.location)
        assertTrue(exam.description.contains("23"))
    }

    @Test fun calendarIsEscapedAndFoldedWithoutCorruptingUtf8() {
        val event = ScheduleEvent("stable", "math", "数学,实验;A", "创104\n北楼",
            "教师\\" + "张".repeat(70), date(7, 8).time, date(7, 9, 35).time, 0xff6750a4.toInt())
        val result = CalendarExport.ics(listOf(event), stamp = 0)
        assertTrue(result.endsWith("END:VCALENDAR\r\n"))
        assertTrue(result.contains("SUMMARY:数学\\,实验\\;A"))
        assertTrue(result.contains("LOCATION:创104\\n北楼"))
        assertTrue(result.contains("UID:stable@ifafu.local"))
        assertTrue(result.contains("X-APPLE-CALENDAR-COLOR:#6750A4"))
        assertTrue(result.split("\r\n").all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        assertTrue(result.replace("\r\n ", "").contains("教师\\\\"))
        assertFalse(result.contains("\uFFFD"))
    }

    @Test fun exportUsesUtcAndSeparateCourseCalendarsKeepTheirColor() {
        val event = ScheduleEvent("one", "math", "数学", "104", "", date(7, 8).time, date(7, 9).time, 0xff6750a4.toInt())
        val two = event.copy(uid = "two", subject = "lab", title = "实验", color = 0xff009688.toInt())
        val bytes = ByteArrayOutputStream()
        CalendarExport.zip(listOf(event, two), bytes)
        val files = linkedMapOf<String, String>()
        ZipInputStream(bytes.toByteArray().inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                files[entry.name] = input.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(2, files.keys.count { it.endsWith(".ics") })
        assertTrue(files.values.any { it.contains("X-APPLE-CALENDAR-COLOR:#009688") })
        val sourceTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            val fixed = event.copy(start = 1788739200000L)
            assertTrue(CalendarExport.ics(listOf(fixed)).contains("DTSTART:20260907T000000Z"))
        } finally { TimeZone.setDefault(sourceTimeZone) }
    }
}
