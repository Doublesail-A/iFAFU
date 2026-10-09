package cn.ifafu.ifafu.schedule

import cn.ifafu.ifafu.entity.NewCourse
import cn.ifafu.ifafu.entity.SyllabusSetting
import cn.ifafu.ifafu.entity.Exam
import cn.ifafu.ifafu.ui.timetable.CourseColorPalette
import java.security.MessageDigest
import java.util.Calendar
import java.util.Date

data class ScheduleEvent(
    val uid: String, val subject: String, val title: String, val location: String,
    val description: String, val start: Long, val end: Long, val color: Int,
    val kind: String = "course", val scope: String = "",
    val teacher: String = "", val week: Int = 0
) {
    val reminderAt: Long get() = start - (if (kind == "exam") 30 else 15) * 60_000L
}

object ScheduleEvents {
    fun key(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(32)

    fun courses(courses: List<NewCourse>, opening: Date?, setting: SyllabusSetting,
                color: (String) -> Int): List<ScheduleEvent> {
        if (opening == null) return emptyList()
        val sunday = Calendar.getInstance().apply {
            time = opening
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))
        }
        return courses.flatMap { course ->
            val first = setting.beginTime.getOrNull(course.beginNode)
            val last = setting.beginTime.getOrNull(course.endNode)
            if (course.weekday !in 1..7 || course.beginNode < 1 || course.nodeLength < 1 ||
                first == null || last == null) return@flatMap emptyList()
            val subject = CourseColorPalette.identity(course.name)
            val scope = key(course.account + "|" + course.year + "|" + course.term)
            course.weeks.filter { it in 1..setting.weekCnt }.mapNotNull { week ->
                val date = (sunday.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, (week - 1) * 7 + course.weekday - 1)
                }
                val start = (date.clone() as Calendar).apply { add(Calendar.MINUTE, first / 100 * 60 + first % 100) }.timeInMillis
                val end = (date.clone() as Calendar).apply { add(Calendar.MINUTE, last / 100 * 60 + last % 100 + setting.nodeLength) }.timeInMillis
                if (end <= start) return@mapNotNull null
                ScheduleEvent(key(scope + "|" + subject + "|" + start + "|" + course.classroom),
                    subject, course.name, course.classroom,
                    "教师：" + course.teacher + "\n第 " + week + " 周 · 第 " +
                        course.beginNode + "–" + course.endNode + " 节\n来自 iFAFU",
                    start, end, color(course.name), scope = scope, teacher = course.teacher, week = week)
            }
        }.distinctBy { it.uid }.sortedBy { it.start }
    }

    fun exams(exams: List<Exam>): List<ScheduleEvent> = exams.filter { it.startTime > 0 }.map {
        val scope = key(it.account + "|" + it.year + "|" + it.term)
        ScheduleEvent(key(scope + "|exam|" + it.name + "|" + it.startTime),
            CourseColorPalette.identity(it.name), it.name, it.address,
            "考试 · 座位：" + it.seatNumber, it.startTime, it.endTime,
            0xffba1a1a.toInt(), "exam", scope)
    }.distinctBy { it.uid }.sortedBy { it.start }
}
