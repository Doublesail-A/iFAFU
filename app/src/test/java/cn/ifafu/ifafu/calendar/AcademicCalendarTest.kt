package cn.ifafu.ifafu.calendar

import cn.ifafu.ifafu.entity.Holiday
import cn.ifafu.ifafu.entity.NewCourse
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AcademicCalendarTest {
    private fun course(day: Int, weeks: Set<Int> = (1..20).toSet()) = NewCourse(id = 123, name = "数学", classroom = "A101", teacher = "教师", weeks = weeks.toSortedSet(), weekday = day, beginNode = 1, nodeLength = 2, year = "2026-2027", term = "1", account = "fixture")
    private fun calendar(holidays: List<Holiday>) = AcademicCalendar("FAFU", "2026-2027", "1", "2026-08-30", holidays)
    private fun dates(courses: List<NewCourse>, first: String = "2026-08-30") = courses.flatMap { c -> c.weeks.map { LocalDate.parse(first).plusDays((it - 1) * 7L + c.weekday - 1).toString() } }

    @Test fun originalHolidayRecordsExcludeClosuresAndPreserveBothMakeups() {
        val calendar = calendar(listOf(Holiday(name = "假期", from = "2026-10-01", days = 7, changes = mapOf("2026-10-06" to "2026-09-20", "2026-10-07" to "2026-10-10"))))
        val input = (2..6).map { course(it) }
        val output = CalendarCourses.apply(input, calendar)
        for (day in 1..7) assertFalse(dates(output).contains("2026-10-0" + day))
        assertTrue(dates(output).contains("2026-09-20"))
        assertTrue(dates(output).contains("2026-10-10"))
        val moved = output.filter { it.name.startsWith("[调课]") }
        assertEquals(2, moved.size)
        moved.forEach {
            assertEquals("fixture", it.account); assertEquals("2026-2027", it.year)
            assertEquals("1", it.term); assertEquals("A101", it.classroom); assertEquals(123, it.id)
        }
        input.forEach { assertEquals(20, it.weeks.size) }
        assertEquals(output, CalendarCourses.apply(input, calendar))
    }

    @Test fun everyOccurrenceMovesInsteadOfOnlyTheFirstOne() {
        val c = calendar(listOf(Holiday(name = "调课", from = "2026-09-01", days = 0, changes = mapOf("2026-09-01" to "2026-09-05", "2026-09-08" to "2026-09-12", "2025-09-01" to "2025-09-05"))))
        val input = course(3, setOf(1,2))
        assertEquals(listOf("2026-09-05", "2026-09-12"), dates(CalendarCourses.apply(listOf(input), c)).sorted())
        assertEquals(setOf(1,2), input.weeks)
    }

    @Test fun allEmptyCoursesRemovedButManualOverridesKept() {
        val c = calendar(listOf(Holiday(name = "停课", from = "2026-08-31", days = 1)))
        val output = CalendarCourses.apply(listOf(course(2,setOf(1)),course(3,setOf(1)),course(2,setOf(1)).copy(local = true)), c)
        assertEquals(2, output.size)
        assertTrue(output.any { it.local })
    }

    @Test fun teachingSystemAlreadyMovedLessonIsNotDeleted() {
        val c = calendar(listOf(Holiday(name = "停课", from = "2026-10-01", days = 7, changes = mapOf("2026-10-07" to "2026-10-10"))))
        val alreadyMoved = course(7, setOf(6))
        assertEquals(listOf("2026-10-10"), dates(CalendarCourses.apply(listOf(alreadyMoved), c)))
    }
}
