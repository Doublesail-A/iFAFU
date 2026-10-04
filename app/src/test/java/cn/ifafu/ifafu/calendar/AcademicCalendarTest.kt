package cn.ifafu.ifafu.calendar

import cn.ifafu.ifafu.entity.NewCourse
import cn.ifafu.ifafu.entity.User
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class AcademicCalendarTest {
    private fun published(name: String, year: String, term: String) =
        SchoolCalendarParser.parse(javaClass.classLoader!!.getResource("calendars/$name.txt")!!.readText(), year, term)

    @Test fun currentSemesterUsesOfficialNationalDayAndBothMakeups() {
        val c = published("c2011a432573", "2026-2027", "1")
        assertEquals("2026-08-30", c.firstWeek)
        for (day in 1..7) assertEquals("国庆节", c.closed["2026-10-0$day"])
        assertFalse(c.closed.containsKey("2026-10-08"))
        assertEquals("2026-09-20", c.moves["2026-10-06"])
        assertEquals("2026-10-10", c.moves["2026-10-07"])
        assertEquals(setOf("元旦"), c.deferredHolidays)
        assertFalse(c.closed.containsKey("2026-10-26")) // freshman training is not a university-wide closure
        assertEquals("校运会", c.closed["2026-11-25"])
        assertEquals("寒假", c.closed["2027-01-21"])
    }

    @Test fun differentAcademicYearsAndWrappedWeekdaysAreParsedWithoutOverrides() {
        val previous = published("c2011a412601", "2025-2026", "1")
        assertEquals("2025-08-31", previous.firstWeek)
        assertTrue(previous.closed.containsKey("2025-10-08"))
        assertEquals("2025-09-28", previous.moves["2025-10-07"])
        assertEquals("2025-10-11", previous.moves["2025-10-08"])
        assertEquals(setOf("元旦"), previous.deferredHolidays)
        assertEquals("寒假", previous.closed["2026-03-03"])
        val older = published("c2011a389470", "2024-2025", "1")
        assertEquals("2024-09-01", older.firstWeek)
        assertEquals(3, older.moves.size)
        assertEquals("2024-09-14", older.moves["2024-09-16"])
        assertEquals("2024-10-12", older.moves["2024-10-07"])
    }

    @Test fun springSemesterCorrectsFirstWeekAndDoesNotInventUnpublishedDates() {
        val c = published("c2011a432574", "2026-2027", "2")
        assertEquals("2027-02-21", c.firstWeek)
        assertEquals(setOf("清明节", "劳动节", "端午节"), c.deferredHolidays)
        assertFalse(c.closed.containsKey("2027-05-01"))
        assertEquals("暑假", c.closed["2027-07-01"])
    }

    @Test fun termMatchingHandlesArabicChineseAndWhitespace() {
        assertTrue(SchoolCalendarParser.matchesTerm("校历（ 2026-2027 学年第 1 学期）", "2026-2027", "1"))
        assertTrue(SchoolCalendarParser.matchesTerm("2026-2027学年第二学期校历", "2026-2027", "2"))
        assertFalse(SchoolCalendarParser.matchesTerm("2026-2027学年第1学期", "2025-2026", "1"))
    }

    @Test fun unseenFutureYearAndCrossYearRangesNeedNoCodeChange() {
        val c = SchoolCalendarParser.parse("8月29日：学生上课\n国庆节:10月1日至7日放假\n12月31日至1月2日元旦放假", "2033-2034", "1")
        assertEquals("2033-08-28", c.firstWeek)
        assertEquals("元旦", c.closed["2034-01-02"])
        assertEquals("国庆节", c.closed["2033-10-01"])
    }

    @Test(expected = java.time.DateTimeException::class)
    fun malformedRangeIsRejectedInsteadOfSilentlyUsingNormalLessons() {
        SchoolCalendarParser.parse("9月1日：学生上课\n国庆节:10月1日至40日放假", "2026-2027", "1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unrecognizedMakeupIsRejected() {
        SchoolCalendarParser.parse("9月1日：学生上课\n国庆节:10月1日至7日放假\n10月10日补上10月7日的课程", "2026-2027", "1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun partiallyUnrecognizedHolidayCannotBeMarkedAsVerified() {
        SchoolCalendarParser.parse("9月1日：学生上课\n国庆节:十月一日至七日放假\n寒假时间:1月21日至2月20日", "2026-2027", "1")
    }

    private fun course(weekday: Int, weeks: Set<Int> = (1..20).toSet(), name: String = "高等数学") = NewCourse(
        id = 123, name = name, classroom = "创104", teacher = "教师", weeks = weeks.toSortedSet(),
        weekday = weekday, beginNode = 1, nodeLength = 2, year = "2026-2027", term = "1", account = "fixture")

    private fun dates(courses: List<NewCourse>, opening: String): List<String> {
        val sunday = LocalDate.parse(opening)
        return courses.flatMap { c -> c.weeks.map { sunday.plusDays((it - 1) * 7L + c.weekday - 1).toString() } }
    }

    @Test fun nationalDayHasNoCoursesAndMakeupPreservesContentAndMetadata() {
        val calendar = published("c2011a432573", "2026-2027", "1")
        val input = (1..7).map { course(it) }
        val adjusted = CalendarCourses.apply(input, calendar)
        val dates = dates(adjusted, calendar.firstWeek)
        for (day in 1..7) assertFalse(dates.contains("2026-10-0$day"))
        assertEquals(1, dates.count { it == "2026-09-20" })
        assertEquals(1, dates.count { it == "2026-10-10" })
        val moved = adjusted.filter { it.name.startsWith("[调课]") }
        assertEquals(2, moved.size)
        moved.forEach {
            assertEquals("fixture", it.account); assertEquals("2026-2027", it.year)
            assertEquals("1", it.term); assertEquals("创104", it.classroom); assertEquals(123, it.id)
        }
        input.forEach { assertEquals(20, it.weeks.size) }
        assertEquals(adjusted, CalendarCourses.apply(input, calendar))
    }

    @Test fun everyOccurrenceCanMoveAndOffTermRulesCannotCorruptWeeks() {
        val c = AcademicCalendar(User.FAFU, "2026-2027", "1", "2026-08-30", emptyMap(),
            mapOf("2026-09-01" to "2026-09-05", "2026-09-08" to "2026-09-12", "2025-09-01" to "2025-09-05"))
        val input = course(3, setOf(1,2))
        val output = CalendarCourses.apply(listOf(input), c)
        assertEquals(listOf("2026-09-05", "2026-09-12"), dates(output, c.firstWeek).sorted())
        assertEquals(setOf(1,2), input.weeks)
    }

    @Test fun allEmptyCoursesAreRemovedAndManualOverridesAreRetained() {
        val c = AcademicCalendar(User.FAFU, "2026-2027", "1", "2026-08-30",
            mapOf("2026-08-31" to "停课"), emptyMap())
        val output = CalendarCourses.apply(listOf(course(2, setOf(1)), course(3, setOf(1)), course(2,setOf(1)).copy(local = true)), c)
        assertEquals(2, output.size)
        assertTrue(output.any { it.local })
    }

    @Test fun unpublishedMakeupDatesAreExcludedFromAllOccurrences() {
        val c = AcademicCalendar(User.FAFU, "2026-2027", "1", "2026-08-30", emptyMap(), emptyMap(),
            uncertainDates = setOf("2026-09-05"))
        assertTrue(CalendarCourses.apply(listOf(course(7, setOf(1))), c).isEmpty())
    }
}
