package cn.ifafu.ifafu.calendar

import cn.ifafu.ifafu.entity.Holiday
import cn.ifafu.ifafu.entity.NewCourse
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.TreeSet

@androidx.annotation.Keep
data class AcademicCalendar(
    val school: String,
    val year: String,
    val term: String,
    val firstWeek: String,
    val holidays: List<Holiday>,
    val source: String = "",
)

/** Uses the original from/days/changes records without changing stored courses. */
object CalendarCourses {
    fun apply(courses: List<NewCourse>, calendar: AcademicCalendar): List<NewCourse> {
        val opening = LocalDate.parse(calendar.firstWeek)
        val sunday = opening.minusDays((opening.dayOfWeek.value % 7).toLong())
        val closed = calendar.holidays.flatMap { holiday ->
            val from = LocalDate.parse(holiday.from)
            (0 until holiday.days).map { from.plusDays(it.toLong()).toString() }
        }.toSet()
        val moves = calendar.holidays.flatMap { it.changes.entries }.associate { it.toPair() }
        val result = ArrayList<NewCourse>()
        for (course in courses) {
            if (course.weekday !in 1..7) continue
            val kept = TreeSet<Int>()
            for (week in course.weeks) {
                if (week !in 1..60) continue
                val iso = sunday.plusDays((week - 1) * 7L + course.weekday - 1).toString()
                if (course.local) { kept.add(week); continue }
                val replacement = moves[iso]
                if (replacement != null) {
                    val destination = LocalDate.parse(replacement)
                    val newWeek = Math.floorDiv(ChronoUnit.DAYS.between(sunday, destination), 7L).toInt() + 1
                    if (newWeek in 1..60) result.add(course.copy(
                        name = "[调课]" + course.name.removePrefix("[调课]"),
                        weeks = sortedSetOf(newWeek),
                        weekday = destination.dayOfWeek.value % 7 + 1,
                    ))
                } else if (iso !in closed) kept.add(week)
            }
            if (kept.isNotEmpty()) result.add(course.copy(weeks = kept))
        }
        return result
    }
}
