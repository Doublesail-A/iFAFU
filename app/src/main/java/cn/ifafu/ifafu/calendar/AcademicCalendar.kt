package cn.ifafu.ifafu.calendar

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
    val closed: Map<String, String>,
    /** Original teaching date -> replacement date; never inferred from a workday. */
    val moves: Map<String, String>,
    val deferredHolidays: Set<String> = emptySet(),
    val uncertainDates: Set<String> = emptySet(),
    val source: String = "",
    val fetchedAt: Long = 0L,
    val notice: String? = null,
)

object CalendarCourses {
    fun apply(courses: List<NewCourse>, calendar: AcademicCalendar): List<NewCourse> {
        val opening = LocalDate.parse(calendar.firstWeek)
        val sunday = opening.minusDays((opening.dayOfWeek.value % 7).toLong())
        val targets = calendar.moves.values.toSet()
        val result = ArrayList<NewCourse>()
        for (course in courses) {
            if (course.weekday !in 1..7) continue
            val kept = TreeSet<Int>()
            for (week in course.weeks) {
                if (week !in 1..60) continue
                val date = sunday.plusDays((week - 1) * 7L + course.weekday - 1)
                val iso = date.toString()
                // A manually added one-off lesson is an explicit user override.
                if (course.local) { kept.add(week); continue }
                val replacement = calendar.moves[iso]
                if (replacement != null) {
                    val destination = LocalDate.parse(replacement)
                    val days = ChronoUnit.DAYS.between(sunday, destination)
                    val newWeek = Math.floorDiv(days, 7L).toInt() + 1
                    if (newWeek in 1..60 && replacement !in calendar.uncertainDates) {
                        result.add(course.copy(
                            name = "[调课]" + course.name.removePrefix("[调课]"),
                            weeks = sortedSetOf(newWeek),
                            weekday = destination.dayOfWeek.value % 7 + 1,
                        ))
                    }
                } else if (iso !in calendar.closed && iso !in targets && iso !in calendar.uncertainDates) {
                    kept.add(week)
                }
            }
            if (kept.isNotEmpty()) result.add(course.copy(weeks = kept))
        }
        return result
    }
}
