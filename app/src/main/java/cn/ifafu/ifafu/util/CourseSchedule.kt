package cn.ifafu.ifafu.util

import cn.ifafu.ifafu.entity.NewCourse
import cn.ifafu.ifafu.entity.SyllabusSetting
import java.util.Calendar
import java.util.Date

object CourseSchedule {
    /** Theme from the next lesson that has not started, including future days and weeks. */
    fun nextCourse(courses: List<NewCourse>, openingDate: Date?, setting: SyllabusSetting,
                   now: Long = System.currentTimeMillis()): NewCourse? {
        if (openingDate == null) return null
        val sunday = Calendar.getInstance().apply {
            time = openingDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))
        }
        return courses.asSequence().flatMap { course ->
            course.weeks.asSequence().filter { it >= 1 }.map { week ->
                val start = (sunday.clone() as Calendar).apply {
                    add(Calendar.WEEK_OF_YEAR, week - 1)
                    add(Calendar.DAY_OF_YEAR, course.weekday - Calendar.SUNDAY)
                    add(Calendar.MINUTE, startMinutes(course.beginNode, setting))
                }.timeInMillis
                course to start
            }
        }.filter { (_, start) -> start >= now }
            .minByOrNull { (_, start) -> start }?.first
    }

    private fun startMinutes(node: Int, setting: SyllabusSetting): Int {
        val time = setting.beginTime.getOrNull(node) ?: return 0
        return time / 100 * 60 + time % 100
    }
}
